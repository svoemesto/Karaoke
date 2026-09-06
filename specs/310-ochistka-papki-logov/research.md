# Research: 310 — Очистка папки логов

**Branch**: `310-ochistka-papki-logov` | **Date**: 2026-09-06
**Spec**: [specs/310-ochistka-papki-logov/spec.md](./spec.md)

## Решения (Decisions)

### D1. `LOG_RETENTION_DAYS` как `const val = 30` в `Constants.kt`

**Decision**: Добавить одну строку в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Constants.kt`:
```kotlin
const val LOG_RETENTION_DAYS = 30
```

**Rationale**:
- Конвенция файла: для простых статических значений (числа, строковые литералы) — `const val`. В файле есть и `val` (например, `WORKING_DATABASE` для Spring runtime init, `LETERS_VOWEL` для string interpolation), но для нового простого Int-литерала — `const val` (NON-NEGOTIABLE guard per AGENTS.md v2.2.0; урок спеки #309 v1 — попытка ввести `val LOG_RETENTION_DAYS = parseRetentionDays(env)` была откачена владельцем).
- Жёсткий порог (30 дней) — соответствует industry-standard default для log rotation. Если администратор захочет другой порог — отдельная спека с явным work package.

**Alternatives considered**:
- `val LOG_RETENTION_DAYS = System.getenv("LOG_RETENTION_DAYS")?.toIntOrNull() ?: 30` — отклонён (урок #309 v1, env-var конфигурируемости не было в work package).
- Отдельный файл `LogRetention.kt` для runtime config — отклонён (для простого Int-литерала не нужна изоляция).

### D2. Cleanup через `Files.list` + `Files.getLastModifiedTime` + `Files.delete`

**Decision**: Использовать Java NIO API. Не использовать legacy `java.io.File` (не поддерживает симлинки корректно).

**Rationale**:
- `Files.list(dir)` возвращает `Stream<Path>` — ленивая итерация, не грузит всё в память.
- `Files.getLastModifiedTime(p).toInstant()` — стандартный способ получить mtime.
- `Files.isRegularFile(p)` — корректная проверка для regular files (vs subdirs, symlinks, sockets).
- `Files.delete(p)` — `IOException` при ошибке (залочен, нет прав), ловится per-file.

**Alternatives considered**:
- `java.io.File.listFiles()` — устаревший API, не различает regular files от subdirs/symlinks.
- `Files.walkFileTree()` — overkill для одного уровня; рекурсия не нужна (cleanup только в `PATH_TO_LOGS`).

### D3. Per-file error isolation (fail-open)

**Decision**: Каждый файл обрабатывается в отдельном try/catch. Ошибка на одном файле (например, залочен другим процессом) не мешает удалению остальных.

**Rationale**:
- FR-4: «per-file IOException → пропустить файл, продолжить; счётчик removed отражает только успешные удаления».
- Outer try/catch на уровне всей функции — для непредвиденных исключений (например, если папка исчезла во время итерации).
- `println` в stderr (виден в `docker logs`) — owner выбрал это в work package (Q-1 «отдельный лог cleanup.log — допустимо отложить»).

**Alternatives considered**:
- Атомарный подход (всё или ничего) — отклонён: нарушил бы FR-4 fail-open, плюс слишком агрессивен для cleanup (один залоченный файл блокировал бы все остальные).

### D4. Clock skew защита: skip файлов с `mtime > Instant.now()`

**Decision**: Если `Files.getLastModifiedTime(p).toInstant().isAfter(Instant.now())` — файл НЕ удаляется (защита от clock skew, NFS-монтирования с чужим хостом).

**Rationale**:
- FR-3 явный: «Файлы с `mtime` в будущем (позже текущего момента) НЕ удаляются».
- Защищает от случайного стирания при рассинхронизации часов или NFS-монтировании с другим хостом (где mtime системных часов сервера может быть в прошлом).
- Реальная гонка между writeText и cleanup невозможна: только что записанный лог имеет mtime ≈ now, при пороге ≥ 1 дня он не попадёт под удаление.

**Alternatives considered**:
- Без защиты — отклонён (риск случайного удаления свежих файлов).
- Допуск ±N секунд skew — отклонён (избыточно сложно, race window минимален).

### D5. Boundary: strict `>` на KEEP (`mtime == threshold` удаляется)

**Decision**: KEEP-условие — `!mtime.isAfter(threshold)`, то есть файл с `mtime == threshold` удаляется. Это strict `>` — порог ровно N дней означает «старше N дней», не «N дней и старше».

**Rationale**:
- SC-2: «файл с mtime ровно 7 дней удаляется (сравнение `>`, не `>=`)».
- Семантика «retention=30» = «оставить ≤ 30 дней», то есть файл с mtime = now − 30 дней уже за порогом.

**Alternatives considered**:
- `>=` — отклонён (изменил бы семантику на «оставить < 30 дней»).

### D6. Триггер: после успешного `File.writeText` + `chmod 666`

**Decision**: Вызов cleanup внутри существующего try-блока в `KaraokeProcessWorker.run()` сразу после `runCommand(listOf("chmod", "666", logFileName))`.

**Rationale**:
- FR-3: «cleanup запускается автоматически при записи нового лога».
- Тот же try-блок — нет внешних зависимостей (cron, scheduled task, отдельный поток).
- Cleanup fail-open (FR-4) — внешний try/catch не нужен, т.к. cleanup сам по себе fail-open внутри.

**Alternatives considered**:
- Отдельный cron/daemon — отклонён (владелец выбрал «при записи нового лога» через Q-2 в #309; лишние зависимости).
- Scheduled task — отклонён (та же причина).
- Background thread — отклонён (overkill для in-process cleanup).

### D7. Только `Files.isRegularFile(p)` (FR-2)

**Decision**: В итерации по `Files.list(dir)` проверяем `Files.isRegularFile(p)`. Subdirs, symlinks, sockets — игнорируются.

**Rationale**:
- FR-2: «Только регулярные файлы логов».
- `isRegularFile` возвращает false для subdirs, symlinks (если не follow), sockets.
- Безопаснее, чем просто `Files.exists(p)` (которое вернёт true и для subdir).

**Alternatives considered**:
- `File.isFile()` (legacy API) — отклонён (не различает symlinks от regular files корректно).

## Best practices research

### Karaoke project conventions (brief.md + AGENTS.md)

- `Constants.kt` — `const val` для простых статических значений (DECIDED — D1).
- 5-step обязательная проверка после ЛЮБОГО изменения кода: compileKotlin / ktlintCheck / bootJar (DECIDED — plan.md Technical Context).
- Git workflow: feature-ветка `NNN-slug` + PR + merge (current: branch `310-ochistka-papki-logov`).
- OpenProject add-comment после каждого speckit-этапа (per workflow).
- Не коммитить секреты (`deploy/.env`, `*.key`, `*.pem`) — фича их не затрагивает.

### Java NIO patterns for log rotation

- Standard pattern: `Files.list` + `Files.getLastModifiedTime` + `Files.delete`.
- Fail-open: per-file try/catch.
- Stream API: use `.use { stream -> ... }` для auto-close.
- Logging: `println` в stderr для docker logs visibility (vs `LoggerFactory.getLogger` — но KaraokeProjectWorker уже использует println в этом блоке).

## Integration patterns

- `karaoke-app` build: `./gradlew :karaoke-app:bootJar --parallel` (per brief.md).
- Tests: `./gradlew :karaoke-app:test --tests "KaraokeProcessCleanupTest"` (selective run).
- Lint: `./gradlew :karaoke-app:ktlintCheck`.

## Не нужно (NOT NEEDED)

- ❌ Spring DI (фича не требует зависимостей; использует существующий `KaraokeProcessWorker.run()` flow).
- ❌ External configuration service (env var отклонён по D1).
- ❌ Logging framework (println в stderr достаточно для observability через docker logs).

## References

- Karaoke constitution: `/home/nsa/Karaoke/.specify/memory/constitution.md` v2.1.0
- Karaoke AGENTS.md: v2.2.0 (с speckit skill-driven workflow правилами)
- brief.md: `/home/nsa/Agents/shared/projects/karaoke/brief.md`
- Livedoc про WORKING_DATABASE gotcha: `/home/nsa/Karaoke/livedocs/architecture/karaoke-web-architecture-boundaries.md`
- Code review Кирилла по spec #309 v1 → 6 замечаний (env-var convention mismatch, etc.) — учтены в D1
- Spec #310: [specs/310-ochistka-papki-logov/spec.md](./spec.md)

## Done When

- [x] Все NEEDS CLARIFICATION резолвнуты (их не было — work package однозначен)
- [x] Все design decisions обоснованы
- [x] Alternatives considered для каждого решения
- [x] Best practices research собран
- [x] Интеграционные паттерны описаны
- [x] References приведены
