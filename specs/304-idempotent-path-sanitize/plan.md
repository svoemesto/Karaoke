# Implementation Plan: Идемпотентная санитиризация путей и имён файлов и папок

**Branch**: `304-idempotent-path-sanitize` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/304-idempotent-path-sanitize/spec.md`

## Summary

Реализовать единый идемпотентный санитайзер путей и имён файлов/папок
(`SanitizePath` / `SanitizePathSegment`) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SanitizePath.kt`,
который заменяет «проблемные» символы (`!`, `?`, `\n`, `\r`, `\t`, `\0`,
`<`, `>`, `|`, `&`, `;`, `"`) на `_`, сохраняет legacy-mapping
(`'` → `` ` ``, `$` → `s`, `*` → `x`, `:` → `-`), сохраняет структурно
значимые символы (`(`, `)`, `[`, `]`, кириллицу и др.), пишет INFO-логи
через slf4j при фактических заменах и удовлетворяет формальному контракту
`sanitize(sanitize(s)) == sanitize(s)` (включая side-effect идемпотентность).

Существующие обёртки `rightFileNameSymbols`, `sanitizeSongFileName`,
`rightFileName` остаются как тонкие алиасы над новым ядром (FR-005),
сохраняя совместимость с 200+ вызывающих мест в `StemJobProcessing.kt`,
`KaraokeProcess.kt`, `model/Song.kt`, `controllers/*.kt`,
`karaoke-web/services/*.kt`.

## Technical Context

**Language/Version**: Kotlin 2.2.20 (JVM 17), Spring Boot 3.5.6

**Primary Dependencies**:
- `org.jetbrains.kotlin:kotlin-stdlib` (уже в karaoke-app)
- `org.slf4j:slf4j-api` 2.x (транзитивно через `spring-boot-starter`,
  уже используется в `VkIdTokenRefreshScheduler.kt`,
  `AutoOneClickSyncScheduler.kt`, `StatsCache.kt` и др.)
- `ch.qos.logback:logback-classic` (транзитивно через Spring Boot starter)
- `org.junit.jupiter:junit-jupiter` (testImplementation, через
  `spring-boot-starter-test`)

**Storage**: N/A — санитайзер является чистой функцией над `String`,
не работает с БД, MinIO или файловой системой напрямую.

**Testing**: JUnit 5 (`@Test`), Logback `ListAppender<ILoggingEvent>` для
верификации FR-014 (side-effect идемпотентность лог-записей). Тесты
пишутся как **unit-тесты** (без сети/БД) — `karaoke-app/src/test/kotlin/
com/svoemesto/karaokeapp/SanitizePathTest.kt` (новый файл).

**Target Platform**: JVM (admin-машина и прод-сервер). Модули:
`karaoke-app` (определение функций + обёртки), `karaoke-web` (только
вызывает через extension-import).

**Project Type**: Kotlin extension-function library (внутренний API,
не standalone публичный пакет).

**Performance Goals**: < 1 мс на вызов для типичного пути < 200 символов;
< 100 мс на пакет из 1000 файлов (что соответствует типичному импорту).
Санитайзер **не** должен быть узким местом пайплайна импорта
(ffmpeg/Demucs/melt занимают секунды/минуты на файл).

**Constraints**:
- 200+ существующих вызывающих мест (`StemJobProcessing.kt:38,68,75,96,118,125`,
  `KaraokeProcess.kt:1038-1789+`, `model/Song.kt:495-572, 1870-2708, 7541-7764`,
  `controllers/SongEditorController.kt:846`, `controllers/ApiController.kt:7855-7961`,
  `karaoke-web/services/EventsBuffer.kt:100-104`,
  `karaoke-web/controllers/MainController.kt:162`,
  `karaoke-web/controllers/PublicPlayerController.kt:459-565`,
  `KaraokeProcessWorker.kt:278`, `HealthReport.kt:2199`,
  `SongUpdateMapper.kt:227` — сигнатуры **не должны** меняться.
- Обратная совместимость с уже-санитайзенными именами на проде
  (FR-004 legacy-mapping сохраняется идемпотентно).
- slf4j logger получается через `LoggerFactory.getLogger(SanitizePath::class.java)`
  (как в существующем `VkIdTokenRefreshScheduler.kt:42`) — без DI,
  чтобы не ломать extension-function API (`String.rightFileName()`).

**Scale/Scope**:
- ~18k песен на проде (см. Constitution §II rationale).
- ~10k+ файловых путей проходят через санитайзер за жизненный цикл проекта.
- 1 новая пара файлов (исходник + тест), 0 изменений в 200+ вызывающих
  местах (только потенциально минимальные правки в `Extentions.kt`,
  если это будет решено в Phase 2 / `tasks.md`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

**Principle I — Self-contained pipeline (NON-NEGOTIABLE)**: ✅ PASS.
Санитайзер — чистая локальная функция, не требует внешних SaaS-зависимостей.
slf4j уже в classpath через Spring Boot starter.

**Principle II — Raw JDBC + diff (NON-NEGOTIABLE)**: ✅ N/A.
Санитайзер не работает с БД.

**Principle III — Sync registry**: ✅ N/A. Санитайзер не влияет на
синхронизацию LOCAL↔SERVER.

**Principle IV — Async queue + ProcessBuilder**: ✅ N/A. Санитайзер —
синхронная функция. **Важное уточнение**: `redirectErrorStream(false)`
ЗАПРЕЩЁН для ProcessBuilder — это правило касается только OS-процессов,
не нашего кода.

**Principle V — Two-frontend**: ✅ N/A. Санитайзер используется только
в backend (`karaoke-app` + `karaoke-web`), не в `webvue3` или
`karaoke-public`.

**Principle VI — Code Standards (NON-NEGOTIABLE)**: ✅ PASS, требует follow-up:
- FR-006 конституции: новый публичный API (`SanitizePath` / `SanitizePathSegment`
  + extension-functions) MUST сопровождаться KDoc с `@see docs/features/
  idempotent-path-sanitize.md`.
- FR-007 конституции: ktlint MUST проходить (через `./gradlew ktlintCheck`
  + pre-commit). Если правки в `Extentions.kt` нарушают существующий
  baseline — обновить baseline (`./gradlew ktlintGenerateBaseline`) и
  зафиксировать сокращение в `tools/baseline-stats.sh`.
- FR-009 конституции: per-feature документ MUST быть создан —
  `docs/features/idempotent-path-sanitize.md` (см. FR-010 спеки).

**Principle VII — Cross-Machine Setup**: ✅ PASS. Не затрагивает
`.git-blame-ignore-revs` или `.gitattributes`. Новые файлы — UTF-8 LF.

**Principle VIII — Secrets and git hygiene**: ✅ PASS. Санитайзер не
работает с секретами. Pre-commit `git ls-files | grep -iE '\.env$|do\.env$'`
` MUST быть пусто (как и сейчас).

**Overall verdict**: PASS. Все принципы удовлетворены. Phase 0 / Phase 1
могут выполняться.

## Project Structure

### Documentation (this feature)

```text
specs/304-idempotent-path-sanitize/
├── plan.md              # Этот файл
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── sanitizer-contract.md  # Phase 1 output
├── checklists/
│   └── requirements.md  # Уже создан в /speckit.specify
├── spec.md              # Уже создан в /speckit.specify
└── tasks.md             # Phase 2 output (/speckit.tasks — НЕ создаётся в /speckit.plan)
```

### Source Code (repository root)

**Structure Decision**: Single project с Kotlin extension-functions,
принадлежит модулю `karaoke-app` (где уже находится `Extentions.kt`).
Никаких новых модулей или директорий верхнего уровня.

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── Extentions.kt                 # СУЩЕСТВУЕТ — обновить (алиасы → новое ядро)
├── SanitizePath.kt               # НОВЫЙ — единое идемпотентное ядро
└── ...

karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/
├── SanitizePathTest.kt           # НОВЫЙ — unit-тесты идемпотентности (FR-009)
└── ...

docs/features/
└── idempotent-path-sanitize.md   # НОВЫЙ — per-feature документ (FR-010)
```

Модуль `karaoke-web` не требует новых файлов: он использует extension-functions
из `karaoke-app` через `import com.svoemesto.karaokeapp.rightFileName`
(см. `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/
EventsBuffer.kt:4`).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений. Все принципы Constitution удовлетворены.
Таблица пуста.

---

## Phase 0: Research — выводы

Детальный отчёт — в `research.md`. Краткое резюме решений:

| Решение | Обоснование |
|---------|-------------|
|Char-by-char mapping через lookup table (не regex)|Regex с unicode-классами рискует regression Cyrillic (`\b` ASCII-only — задокументировано в `Extentions.kt:222-224`). Lookup table детерминирован и прост в отладке.|
|Существующие обёртки как `actual fun` алиасы над `SanitizePath.kt` ядром|Сохраняет 200+ вызывающих мест без изменений (FR-005). Kotlin `typealias` для функций не поддерживается; `actual fun String.foo() = SanitizePath.segment(this)` — минимальный код.|
|slf4j `LoggerFactory.getLogger(SanitizePath::class.java)` (static logger)|Соответствует существующему паттерну в `VkIdTokenRefreshScheduler.kt:42`, `StatsCache.kt`. Не требует DI-рефакторинга extension-function API.|
|Logback `ListAppender<ILoggingEvent>` для тестов side-effect идемпотентности|Стандартный паттерн slf4j/logback тестирования. Не требует дополнительных зависимостей (logback уже в classpath через Spring Boot).|
|Per-feature документ `docs/features/idempotent-path-sanitize.md` (новый файл)|FR-009 конституции + FR-010 спеки. Не обновляем существующий (такого ещё нет), создаём новый.|
|Обновление `AGENTS.md` (TOP-10 ловушек) — отдельный пункт|FR-011 спеки. Делается в том же PR, что и код.|

## Phase 1: Design — выводы

Детальный отчёт — в `data-model.md` и `contracts/sanitizer-contract.md`.
Краткое резюме:

- **API**: два публичных символа — `String.sanitizePathSegment(): String`
  (для «голых» фрагментов) и `String.sanitizePath(): String` (для полных
  путей с `/`/`\` разделителями, сегменты санитайзятся независимо).
- **Таблица замен**: см. `data-model.md` — символ → `_`, legacy-mapping
  (`'` → `` ` ``, `$` → `s`, `*` → `x`, `:` → `-`).
- **Логирование**: см. `FR-014` спеки + `contracts/sanitizer-contract.md`.
- **Тесты**: 5 категорий (каждый символ таблицы, комбинации, Unicode,
  пустые/only-problematic, side-effect идемпотентность через ListAppender).

## Re-evaluation Constitution Check (post-design)

Все принципы Constitution по-прежнему удовлетворены (Phase 1 не добавил
новых зависимостей или интерфейсов, нарушающих governance).

**Verdict**: ✅ PASS. Спека готова к Phase 2 (`/speckit.tasks`).

---

## Open Questions для Phase 2 (tasks.md)

Следующие вопросы — **implementation-level**, не spec-level. Они
решаются в `/speckit.tasks`, не здесь:

1. Точная форма lookup table (Kotlin `when`? `Map<Char, Char>`? `if/else`?) —
   влияет только на читаемость кода.
2. Нужно ли `private val LOG = LoggerFactory.getLogger(...)` или вычислять
   каждый раз внутри функции (на горячем пути это может быть проблемой
   для производительности — но slf4j кеширует логгеры внутри, так что
   разница минимальна).
3. Нужно ли выносить таблицу замен в `companion object` (для тестируемости)
   или держать как `private const val` (для скорости).