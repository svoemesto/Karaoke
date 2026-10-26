---

description: "Task list для фичи «Устранение утечки соединений с БД (BR-627)»"
---

# Tasks: Устранение утечки соединений с БД (BR-627)

**Input**: Design documents from `specs/645-fix-connection-leak/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/connection-lifecycle.md, quickstart.md

**Tests**: тестовые задачи включены — спека прямо требует проверок (FR-003…FR-006),
поэтому тесты — обязательная часть, а не опциональная.

**Organization**: задачи сгруппированы по user story спеки, чтобы каждую историю
можно было реализовать и проверить независимо.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей)
- **[Story]**: к какой user story относится задача
- Пути — точные, относительно корня репозитория

**Контракт**: FR-001…FR-012 и SC-001…SC-005 — из `spec.md`, не подлежат изменению.

---

## Phase 1: Setup (общая инфраструктура)

**Purpose**: подготовить ветку, окружение и обязательные гейты.

- [ ] T001 Зафиксировать рабочее окружение: `GRADLE_USER_HOME`/`DOCKER_CONFIG` от корня репозитория, `env -u DISPLAY` для backend-тестов, `./gradlew … --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true`
- [ ] T002 [P] Заclaim'ить OpenProject #410 (`bash tools/tracker.sh claim-issue 410`) — выполнено до первой строки кода
- [ ] T003 Собрать baseline «до правки»: зафиксировать список 4 сырых потоков (`grep -rnP '(?<![\w.])thread\s*\{|(?<![\w.])thread\s*\((?!\s*\))' --include='*.kt' karaoke-app/src/main karaoke-web/src/main`) и число файлов прикладного кода, обращающихся к БД (~160 из ~420 `.kt`)

---

## Phase 2: Foundational (блокирующие предусловия)

**Purpose**: канонический механизм закрытия соединений должен уметь закрывать
соединения **всех** БД, к которым пошёл одноразовый поток — иначе фикс US1
неполон (см. research D-2, D-5).

**⚠️ CRITICAL**: работа по user story начинается только после этой фазы.

- [ ] T004 Расширить `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/OneShotThread.kt`: аддитивный параметр `alsoClose: List<KaraokeConnection> = emptyList()` — дополнительные экземпляры БД, чьи thread-local соединения закрываются в том же `finally` (после `database.closeThreadConnection()`); KDoc на параметр и на гарантии (FR-001, research D-2)
- [ ] T005 [P] Добавить в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/OneShotThreadTest.kt` тест: при `alsoClose = listOf(secondDb)` закрываются **оба** соединения — и при успехе, и при исключении (FR-005)
- [ ] T006 Проверить, что все существующие вызовы `startOneShotThread` в `karaoke-app/src/main` и `karaoke-web/src/main` компилируются без изменений (обратная совместимость аддитивного параметра)

**Checkpoint**: канонический механизм готов — можно переводить фоновые потоки.

---

## Phase 3: User Story 1 — Оператор не теряет очередь и сайт из-за исчерпания соединений (Priority: P1) 🎯 MVP

**Goal**: ни один фоновый поток приложения, ходящий в БД, не оставляет
физическое соединение открытым после своей смерти.

**Independent Test**: добавить в любой файл прикладного кода с обращением к БД
сырой поток — после правки фикса such файл не должен содержать сырых потоков;
поведенчески — `closeThreadConnection()` вызывается при успехе и при исключении.

### Implementation for User Story 1

- [ ] T007 [US1] Перевести `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/ExportAlignmentDataset.kt:58` на `startOneShotThread(WORKING_DATABASE, "export-alignment-dataset") { … }`; убрать импорт `kotlin.concurrent.thread` (FR-001, FR-002)
- [ ] T008 [P] [US1] Перевести `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/StorageCacheWarm.kt:59` на `startOneShotThread(database, "cache-warm") { … }`; убрать импорт `kotlin.concurrent.thread` (FR-001, FR-002)
- [ ] T009 [P] [US1] Перевести `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/CacheEtagSizeBackfill.kt:75` на `startOneShotThread(Connection.local(), "cache-etag-size-backfill") { … }` (тот же экземпляр БД, что использует `loadRowsNeedingBackfill`); убрать импорт `kotlin.concurrent.thread` (FR-001, FR-002)
- [ ] T010 [US1] Перевести `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt:556` на `startOneShotThread(WORKING_DATABASE, "approve-sync-related", alsoClose = listOf(Connection.remote())) { … }` — тело ходит и в LOCAL, и в SERVER (research D-2, F-5); убрать импорт `kotlin.concurrent.thread` (FR-001, FR-002)
- [ ] T011 [P] [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:749` добавить `alsoClose = listOf(Connection.remote())` — поток очереди в ветке sync-batch ходит в SERVER (research F-6, D-2)
- [ ] T012 [US1] Проверить, что ни в одном файле прикладного кода не осталось вызовов `kotlin.concurrent.thread` (FR-002) — прогнать `grep -rnP '(?<![\w.])thread\s*\{|(?<![\w.])thread\s*\((?!\s*\))' --include='*.kt' karaoke-app/src/main karaoke-web/src/main` и убедиться, что совпадений нет
- [ ] T013 [US1] Собрать модуль: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin` с обязательными параметрами из T001

**Checkpoint**: US1 реализована — соединения одноразовых фоновых потоков закрываются детерминированно.

---

## Phase 4: User Story 2 — Новый фоновый поток не может снова проскочить мимо контракта (Priority: P1)

**Goal**: автоматическая проверка охватывает **все** файлы прикладного кода,
обращающиеся к БД (а не фиксированный перечень из двух), отличает нарушение от
допустимого случая и доказывает негативной фикстурой, что падает на внесённом
нарушении.

**Independent Test**: внести сырой поток в файл с обращением к БД — проверка
обязана упасть с указанием файла и строки; после замены на канонический способ —
пройти; сырой поток в файле без обращений к БД — нарушением не считается.

### Tests for User Story 2

- [ ] T014 [P] [US2] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/RawThreadDbGuard.kt` — детектор: снятие комментариев (`//`, `/* … */`, KDoc), маркеры обращения к БД (research D-3), поиск сырых потоков: `kotlin.concurrent.thread` в любой форме + `java.lang.Thread { … }` кроме `addShutdownHook(Thread { … })` (research D-4)
- [ ] T015 [US2] Переписать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/OneShotThreadWiringTest.kt`: обход **всех** `.kt` в `karaoke-app/src/main/kotlin` и `karaoke-web/src/main/kotlin`, фильтр «обращается к БД», проверка отсутствия сырых потоков; в сообщении об ошибке — путь файла и номер строки (FR-003, US2-сценарий 1)
- [ ] T016 [P] [US2] Негативная фикстура «до/после» (FR-006): копия **реального** DB-файла с внедрённым `thread {` во временном каталоге → детектор обязан найти нарушение и указать файл и строку; та же фикстура с `startOneShotThread` → нарушений нет
- [ ] T017 [P] [US2] Фикстура различения (FR-004): синтетический файл с сырым `thread {`, но **без** обращений к БД → нарушений нет; синтетический файл без БД и без сырого потока → нарушений нет
- [ ] T018 [US2] Зафиксировать в тесте ожидаемый охват: тест печатает число найденных DB-файлов (~160) и список 4 ранее неохваченных файлов — `ExportAlignmentDataset.kt`, `StorageCacheWarm.kt`, `CacheEtagSizeBackfill.kt`, `controllers/SongEditorController.kt` (SC-003)
- [ ] T019 [US2] Прогнать `./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.services.OneShotThreadWiringTest"` — тест зелёный на текущем коде (FR-003, FR-004, FR-006)

**Checkpoint**: US2 реализована — новый фоновой поток в файле с обращением к БД больше не проскочит незамеченным.

---

## Phase 5: User Story 3 — Разработчик понимает границы контракта соединения (Priority: P2)

**Goal**: документация в одном месте содержит правило закрытия по типам потока,
признанное ограничение «третьего типа», компенсирующую меру, признак
исчерпания и способ проверки числа соединений, а регламент запуска тестов
называет требуемый запас по лимиту соединений.

**Independent Test**: читатель документации о соединениях находит правило
закрытия, признанное ограничение и компенсирующую меру в одном месте, без
поиска по нескольким документам.

### Implementation for User Story 3

- [ ] T020 [US3] Обновить `knowledge/domains/persistence/domain.md`: контракт соединения — новая автоматическая проверка (динамический охват всех DB-файлов вместо перечня), параметр `alsoClose` во внутреннем API, ссылка на регламент запуска тестов (FR-007, FR-009, FR-012)
- [ ] T021 [P] [US3] Обновить `knowledge/system/infra/deploy-overview.md`: рядом с признаком исчерпания добавить способ проверки состояния — `SELECT count(*) FROM pg_stat_activity;` и сравнение с `max_connections`; НЕ предлагать «перезапустить БД» как единственный ответ (FR-008)
- [ ] T022 [P] [US3] Обновить `knowledge/system/infra/ci-tools.md`: подраздел «Прогон backend-тестов и лимит соединений БД» — тестовые потоки тоже оставляют соединения; требуемый запас по лимиту (`max_connections=300`, `DB_MAX_CONNECTIONS`); команда проверки (FR-009)
- [ ] T023 [P] [US3] Поправить `knowledge/adr/local-0009-thread-local-connection-lifecycle.md` (append-only, без переписывания): факт закрытия 4 мест + аддитивный параметр `alsoClose`; признанное ограничение «третьего типа» и компенсирующая мера остаются (FR-007, FR-012)
- [ ] T024 [US3] Создать per-feature документ `docs/features/fix-connection-leak.md` с 6 обязательными секциями (`## Что делает`, `## Зачем`, `## Как работает`, `## Инварианты`, `## Известные ловушки`, `## Ссылки`) — Constitution VI (FR-009), slug == имя файла, Status ∈ {active, deprecated, experimental}
- [ ] T025 [US3] Проверить, что читатель находит правило/ограничение/меру в одном месте — `knowledge/domains/persistence/domain.md` (SC-005)

**Checkpoint**: US3 реализована — границы контракта соединения видны из документации.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: сквозные проверки, обязательные по hard-gates проекта.

- [ ] T026 [P] Прогнать все `run:`-команды из `.github/workflows/lint.yml` (список извлекается парсингом воркфлоу, R-47) и зафиксировать результат в `specs/645-fix-connection-leak/report.md`
- [ ] T027 [P] Прогнать пять гейтов знаний: `bash tools/check-knowledge-structure.sh`, `bash tools/check-knowledge-cross-links.sh`, `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt`, `python3 tools/check-doc-references.py`, `python3 tools/check-ssot-impact.py` — без новых нарушений
- [ ] T028 Прогнать полный набор backend-тестов модуля `karaoke-app` с `env -u DISPLAY` и убедиться, что ни один тест не падает из-за исчерпания соединений БД (SC-002, SC-004)
- [ ] T029 Проверить `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$|\.p12$|\.pfx$'` → пусто (Constitution VIII.3)
- [ ] T030 Проверить чек-лист `kara-pre-commit` перед коммитом и `kara-post-edit` после правок кода
- [ ] T031 Написать `specs/645-fix-connection-leak/report.md`: что сделано по каждому FR, что не сделано и почему, «до/после» с командами и выводом, результаты проверок, принятые решения, что осталось на владельца
- [ ] T032 Закоммитить изменения в ветку `648-fix-connection-leak-impl` (без push), сообщения в стиле репозитория (`fix(scope): …`)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей
- **Foundational (Phase 2)**: зависит от Setup — блокирует все user story
- **US1 (Phase 3)**: зависит от Foundational
- **US2 (Phase 4)**: зависит от Foundational; проверка T015 должна находить нарушения, поэтому **запускать после T007–T012** (иначе тест будет падать на ещё не исправленном коде — это ожидаемо «до/после» состояние, фиксируется в отчёте)
- **US3 (Phase 5)**: зависит от Foundational; SSoT-правки отражают состояние кода, поэтому выполнять **после US1 и US2**
- **Polish (Phase 6)**: зависит от всех историй

### User Story Dependencies

- **US1 (P1)**: стартует после Foundational; самостоятельна
- **US2 (P1)**: стартует после Foundational; использует реальные файлы, исправленные в US1 (иначе позитивный прогон падает — что и подтверждает негативную фикстуру «до/после»)
- **US3 (P2)**: стартует после Foundational; документирует состояние US1/US2

### Parallel Opportunities

- T005, T008, T009, T011, T014, T016, T017, T021, T022, T023, T026, T027 — разные файлы, параллельны
- T007 и T010 — разные файлы, параллельны
- Внутри Phase 6 прогоны гейтов независимы

---

## Parallel Example: User Story 1

```bash
# Параллельно: два независимых перевода фоновых потоков
Task: "T008 Перевести StorageCacheWarm.kt на startOneShotThread"
Task: "T009 Перевести CacheEtagSizeBackfill.kt на startOneShotThread"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup
2. Phase 2: Foundational (T004–T006)
3. Phase 3: US1 (T007–T013)
4. **STOP и ПРОВЕРИТЬ**: `OneShotThreadTest` зелёный (закрытие при успехе и при исключении); `grep` не находит `kotlin.concurrent.thread`
5. US2 (расширенная проверка) — T014–T019
6. US3 (документация) — T020–T025
7. Phase 6: Polish — T026–T032

### Incremental Delivery

- Foundational → US1: значение — утечка в проде закрыта; проверяется независимо
- US1 → US2: значение — фикс не вернётся в новом файле; проверяется независимо
- US2 → US3: значение — границы контракта видны; проверяется независимо

---

## Notes

- [P] = разные файлы, нет зависимостей
- [Story] = трассировка к user story спеки
- Спека — контракт: FR-001…FR-012 и SC-001…SC-005 не изменяются; если требование
  невыполнимо — фиксируется в `report.md`, а не правится спека
- Коммит после каждой логической группы задач; push не выполняется
- `kara-pre-commit` — перед коммитом, `kara-post-edit` — после правок кода
