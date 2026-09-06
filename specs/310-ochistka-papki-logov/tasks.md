# Tasks: 310 — Очистка папки логов

**Input**: Design documents from `/home/nsa/Karaoke/specs/310-ochistka-papki-logov/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Tests**: Spec FR-6 запрашивает unit tests (JUnit 5 + `@TempDir`). Тесты включены в задачи.

**Organization**: Tasks grouped by user story. Каждая история независимо тестируема.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2)
- Include exact file paths

## Path Conventions

- Backend-only Karaoke: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`
- Tests: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/`
- Livedoc: `livedocs/features/`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Никакой setup не требуется — feature использует существующий проект.

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Блокирующие prerequisite для US1 — добавление константы порога в `Constants.kt`. Без неё cleanup-функция не имеет порога.

- [X] T001 [P] Add `LOG_RETENTION_DAYS` constant to `Constants.kt` (FR-1). Insert after `PATH_TO_LOGS` declaration:
  ```kotlin
  // Спека 310: порог возраста логов в днях для автоочистки (см. KaraokeProcessWorker.cleanupOldLogs()).
  // Жёсткий порог (NON-NEGOTIABLE per AGENTS.md v2.2.0; урок спеки #309 v1).
  const val LOG_RETENTION_DAYS = 30
  ```
  Convention match: для простых Int-литералов — `const val` (в файле 63 const val + 6 val для технических причин).

## Phase 3: User Story 1 — Автоматическая очистка старых логов (P1, MVP)

**Goal**: При записи нового лога KaraokeProcess — в фоне удалить регулярные файлы старше 30 дней. Fail-open, защита от clock skew, без внешних зависимостей.

**Independent Test**: Запустить любой процесс KaraokeProcess → создать набор лог-файлов разного возраста → дождаться следующей записи → проверить, что старые удалены, свежие остались, новый записан.

### Tests for US1

- [X] T002 [P] [US1] Create test class `KaraokeProcessCleanupTest.kt` at `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/KaraokeProcessCleanupTest.kt`. Structure: package `com.svoemesto.karaokeapp`, imports (`org.junit.jupiter.api.{Test,io.TempDir}`, `java.nio.file.*`, `java.nio.file.attribute.FileTime`, `java.time.Instant`, `java.time.temporal.ChronoUnit`, `kotlin.test.{assertTrue,assertFalse}`). Helper `touch(p: Path, ageDays: Long)` создаёт файл и ставит `mtime = now - ageDays дней`. Uses `@TempDir lateinit var tempDir: Path`.

- [X] T003 [P] [US1] Add test method `cleanupRemovesOldFiles` covering SC-2. Touch 3 файла (5d, 20d, 60d) → call `KaraokeProcessWorker.cleanupOldLogsIn(tempDir, retentionDays = 30)` → assert 5d and 20d exist, 60d deleted. Reference: spec FR-6 + SC-2.

- [X] T004 [P] [US1] Add test method `cleanupIgnoresFutureFiles` covering FR-3. Touch файл с `mtime = now + 1h` → call cleanup → assert файл не удалён. Reference: spec FR-3 (clock skew защита).

- [X] T005 [P] [US1] Add test method `cleanupFailsOpenOnLockedFile` covering FR-4 (fail-open, per-file isolation). Создать файл, chmod сделать его read-only (`chmod 444` или эквивалент), вызвать cleanup → assert что cleanup не падает и возвращается успешно (ошибка per-file изолирована, остальные файлы удаляются). **Обязательно по spec FR-6 line 72: «Coverage SC-2, FR-3, FR-4 — обязательно».**

### Implementation for US1

- [X] T006 [P] [US1] Add imports to `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt` (после существующих импортов):
  ```kotlin
  import java.io.IOException
  import java.nio.file.Files
  import java.nio.file.Path
  import java.nio.file.Paths
  import java.time.temporal.ChronoUnit
  ```

- [X] T007 [US1] Add `cleanupOldLogs()` and `cleanupOldLogsIn(dir, retentionDays)` functions to `KaraokeProcessWorker.kt` companion object. Reference: research.md D2 (Files.list + getLastModifiedTime + delete), D3 (per-file try/catch), D4 (clock skew защита), D5 (boundary strict `>` на KEEP), D7 (Files.isRegularFile). Per Кирилл review M2 — **обязательно KDoc с `@see` на spec.md** для public API (Constitution VI FR-006). Signature:
  ```kotlin
  /**
   * Удаляет регулярные файлы из [PATH_TO_LOGS], чей mtime старше [LOG_RETENTION_DAYS] дней.
   * Fail-open (FR-4): per-file IOException пропускается; outer Exception логируется, не пробрасывается.
   * Вызывается после успешного [File.writeText] лога в [KaraokeProcessThread.run] (FR-3).
   *
   * @see specs/310-ochistka-papki-logov/spec.md
   */
  fun cleanupOldLogs() { ... }
  
  internal fun cleanupOldLogsIn(dir: Path, retentionDays: Int) { ... }
  ```

- [X] T008 [US1] Add cleanup call after `chmod` in `KaraokeProcessWorker.kt` (~line 287). В существующем try-блоке, после `runCommand(listOf("chmod", "666", logFileName))`, добавить:
  ```kotlin
  KaraokeProcessWorker.cleanupOldLogs()
  ```
  Cleanup fail-open внутри, отдельный try/catch на call site не нужен.

## Phase 4: User Story 2 — Сохранение свежести логов для отладки (P2)

**Goal**: Свежие логи (≤ 30 дней) сохраняются для диагностики; старые удаляются. Граница strict `>` (файл с mtime == threshold удаляется).

**Independent Test**: Запустить KaraokeProcess с набором логов разного возраста → проверить, что файл возрастом 14 дней остаётся, файл возрастом 31 день удаляется.

**Примечание**: Реализация US2 покрыта US1 (тот же cleanup-механизм). Отдельный код не нужен — только тест.

- [X] T009 [P] [US2] Add test method `cleanupKeepsFreshFilesForDebug` covering SC-2 boundary. Touch файл с `mtime = now − 14 дней` → call cleanup (retention=30) → assert файл остаётся. Touch файл с `mtime = now − 31 дней` → call cleanup → assert файл удаляется. **Обязательно по spec FR-6 line 72: «Coverage SC-2, FR-3, FR-4 — обязательно».**

## Phase 5: Livedoc (SC-5)

**Goal**: Документация фичи в `livedocs/features/310-ochistka-papki-logov.md` + строка в INDEX.

- [X] T010 [P] Create livedoc at `livedocs/features/310-ochistka-papki-logov.md` per spec SC-5 (line 89: «После имплементации в `livedocs/features/310-ochistka-papki-logov.md` появляется описание фичи…»). Frontmatter:
  ```markdown
  ---
  status: Active
  slug: 310-ochistka-papki-logov
  related:
    - ../../specs/310-ochistka-papki-logov/spec.md
  ---
  ```
  Body: что делает, поведение (триггер, условие удаления, граница strict `>`, fail-open, only-regular-files), acceptance (SC-1..SC-5), история.

- [X] T011 [P] Add row to `livedocs/features/README.md` INDEX. Insert after existing entries. Format как у соседних строк (см. 309 entry).

## Phase 6: Polish & Cross-Cutting Concerns

**Goal**: 5-step verification (brief.md NON-NEGOTIABLE), regression grep, OP add-comment для этапа implementation.

- [X] T012 Run 5-step verification (per Кирилл review M1 — ОБА модуля):
  ```bash
  ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel
  ./gradlew :karaoke-app:ktlintCheck :karaoke-web:ktlintCheck
  ./gradlew :karaoke-app:bootJar :karaoke-web:bootJar
  ```
  Все три должны вернуть BUILD SUCCESSFUL. ktlint может быть UP-TO-DATE без новых нарушений (KaraokeCodeStyle уже задаёт baseline).

- [X] T013 [P] Run unit tests:
  ```bash
  ./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.KaraokeProcessCleanupTest"
  ```
  Ожидаемый результат: **4 теста PASS** (cleanupRemovesOldFiles, cleanupIgnoresFutureFiles, cleanupFailsOpenOnLockedFile, cleanupKeepsFreshFilesForDebug), 0 failures. Build evidence: `TEST-com.svoemesto.karaokeapp.KaraokeProcessCleanupTest.xml` с `tests="4" failures="0"`.

- [X] T014 [P] Regression grep: убедиться, что `parseRetentionDays`, `LOG_RETENTION_DAYS = parseRetentionDays`, `System.getenv("LOG_RETENTION_DAYS")` — 0 occurrences в исходниках (урок #309 v1, должен быть только `const val LOG_RETENTION_DAYS = 30`).

- [X] T015 [P] OpenProject add-comment для Phase 5/6 (implementation stage). Файл: `/tmp/wp-310-implementation.md`. Markdown: что сделано, sha256 всех артефактов, build evidence (TEST-…xml), pre-task checksums в `Programmer/.worklog/`. Reference AGENTS.md v2.2.0 «обязательное обновление OpenProject после каждого speckit-этапа».

- [X] T016 [P] Send implementation report to Марк (reviewer) for фаза 9 (ревью имплементации). Build evidence + sha256 + convention match в существующем коде.

## Dependencies

Зависимости между story phases:
- Phase 2 (T001) → Phase 3 (T002-T008): T001 (Constants.kt) требуется до cleanupOldLogs() в T007
- Phase 3 (T002-T005 tests, T006-T008 impl) → Phase 4 (T009 test): T009 использует cleanupOldLogsIn, реализованный в T007
- Phase 3 → Phase 5 (T010-T011): livedoc описывает реализованную функциональность
- Phase 3 → Phase 6 (T012-T016): 5-step verification требует реализации

Story completion order:
1. US1 (P1) — must complete first (provides the cleanup function)
2. US2 (P2) — depends on US1 (uses cleanupOldLogsIn)
3. Livedoc — depends on both US1 and US2 (documents the feature)
4. Polish — depends on all implementation

## Parallel Execution Examples

**Per US1 (P1)**:
- T002-T005 (tests) can run in parallel with T006 (imports) — different files
- T007 (cleanup functions) MUST come after T001 (constant) and T006 (imports)
- T008 (cleanup call) MUST come after T007 (functions defined)

**Per US2 (P2)**:
- T009 (test) can run in parallel with T010-T011 (livedoc)

**Cross-phase parallelism**:
- T002-T005 (US1 tests) can run in parallel with T010-T011 (livedoc) and T012-T016 (verification prep)
- T009 (US2 test) can run in parallel with T012-T016

## Implementation Strategy

**MVP scope**: Phase 2 + Phase 3 (US1) — минимальная рабочая фича. После T008 KaraokeProcessWorker.cleanupOldLogs() работает и удаляет старые логи при каждой записи.

**Incremental delivery**:
1. Phase 2 (T001) — foundation
2. Phase 3 (T002-T008) — US1 implementation
3. Phase 4 (T009) — US2 test
4. Phase 5 (T010-T011) — documentation
5. Phase 6 (T012-T016) — verification

**Suggested commit boundaries** (per Karaoke git workflow — НЕ коммитим, only working tree):
- Один squash-коммит на всю фичу (по решению владельца в конце).
- Working tree до самого финала (per AGENTS.md «всё в working tree до самого финала»).

## Notes

- **Tests are REQUIRED** (per spec FR-6)
- **KDoc обязательно** для public API (Кирилл review M2, Constitution VI FR-006)
- **5-step оба модуля** (Кирилл review M1): ktlintCheck для karaoke-app + karaoke-web
- **Livedoc (SC-5)** обязательно перед mark-review
- **OpenProject add-comment (Phase 6 T015)** — NON-NEGOTIABLE per AGENTS.md v2.2.0
- **Mark-review (Phase 9)** через peer-mail → Марк с указанием sha256 артефактов + build evidence

## Done When

- [X] Все 16 tasks выполнены
- [X] 5-step verification (T012) пройден
- [X] Unit tests (T013) **4/4 PASS**
- [X] Regression grep (T014) чист
- [X] OP add-comment (T015) добавлен
- [X] Mark-review (T016) запрошен
- [X] Готово к фазе 9 (Марк ревью имплементации)
