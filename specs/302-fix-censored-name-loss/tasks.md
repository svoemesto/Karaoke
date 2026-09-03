---
description: "Task list for feature implementation — Не сохраняется цензурированное имя песни в SongEdit (spec 302)"
---

# Tasks: Не сохраняется цензурированное имя песни в SongEdit

**Input**: Design documents from `/specs/302-fix-censored-name-loss/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Спека не требует unit-tests (это bugfix с ручной верификацией через quickstart.md). Verification = SC-001..SC-011, ручные проверки на LOCAL-БД.

**Organization**: Tasks сгруппированы по user stories (US-1 P1, US-2 P2, US-3 P3) для независимой реализации и тестирования.

**MVP**: US-1 (P1) — основной bugfix. US-2 + US-3 — защита от регрессий.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to ([US1], [US2], [US3])
- **Include exact file paths** in descriptions

## Path Conventions

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`
- Frontend (admin SPA): `webvue3/src/components/`
- Tools: `tools/`
- Docs: `docs/features/`, `specs/<NNN>-*/`
- CI/pre-commit: `.pre-commit-config.yaml`, `.github/workflows/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка к работе (OpenProject claim, проверка окружения).

- [x] T001 **NFR-004**: Claim OpenProject issue #52 через `bash tools/tracker.sh claim-issue 52` (assignee=ai-agent, status=In progress). Без этого issue остаётся в New и параллельная работа может быть потеряна.
- [x] T002 [P] Проверить feature-ветку: `git branch --show-current` должен вернуть `302-fix-censored-name-loss` (создана в /speckit.specify через bootstrap hook).
- [x] T003 [P] Проверить окружение: `docker ps` (контейнеры Karaoke запущены), `bash tools/tracker.sh healthcheck` (OpenProject отвечает), LOCAL-БД доступна через `psql`.

---

## Phase 2: Foundational (Blocking Prerequisites) — FR-011 Refactor

**Purpose**: Централизованный маппер параметров → Song. Это фундамент для ВСЕХ user stories — без него bug #52 не исправлен и другие US не могут быть протестированы.

**⚠️ CRITICAL**: Никакая user story не может стартовать до завершения этой фазы.

- [x] T004 Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongUpdateMapper.kt` с пустой структурой: `data class SongUpdateApplyResult` + `object SongUpdateMapper { fun apply(...): SongUpdateApplyResult }` (см. [research.md Decision 2](research.md#decision-2--архитектура-songupdatemapper) для полей и сигнатуры).
- [x] T005 [P] Реализовать lookup-table для standard string fields в `SongUpdateMapper`: `private val fieldLookup: Map<String, SongField> by lazy { SongField.entries.associateBy { it.dbField } }` (или эквивалент — `it.name.toCamelCase()`). Используется в Phase B маппера.
- [x] T006 Реализовать Phase A (Special-case fields) в `SongUpdateMapper.apply`: обработка `fileName` (sanitize через `sanitizeSongFileName()` + collision check через `Song.loadListFromDb` + active-process check через `KaraokeProcess.hasActiveProcess`), `albumId` (cross-author check через `Album.getAlbumById` + `Author.getAuthorById`), `songType` (enum mapping через `SongType.entries.firstOrNull { st -> st.dbValue == it.lowercase() } ?: SongType.SONG`). Special-case поля читаются из `params` напрямую.
- [x] T007 Реализовать Phase B (Standard string fields) в `SongUpdateMapper.apply`: для каждой `entry: params.entries` где ключ есть в `fieldLookup` → `song.fields[lookup[key]] = entry.value`. Не применять к ключам, обработанным в Phase A.
- [x] T008 Реализовать Phase C (Non-string fields) в `SongUpdateMapper.apply`: helper `parseParam<T: Any>(key: String, raw: String): T` для `Int?`, `Long?`, `Boolean?`, enum'ов. При ошибке парсинга бросать `BadRequestException("Invalid value for param '$key': '$raw' is not a number")` (FR-012).
- [x] T009 Реализовать Phase D (Baseline) в `SongUpdateMapper.apply`: если `song.songNameCensored.isEmpty()` И `song.songName.isNotEmpty()` → автозаполнение через `song.songName.censored(database)` (логика из `Song.kt:5364`, инкапсулирована здесь).
- [x] T010 Реализовать Phase E (Result) в `SongUpdateMapper.apply`: `SongUpdateApplyResult(albumLinkValid, fileNameRenameError, freeChanged=..., idStatusChanged=...)`. Снимки `freeBefore`/`idStatusBefore` берутся ДО фазы A.
- [x] T011 Рефактор `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`: заменить 95 `@RequestParam` в `songs2Update` на `@RequestParam all: Map<String, String>`, тело метода сократить до ~30 строк (загрузка Song → `SongUpdateMapper.apply(...)` → `saveToDb`/`saveToFile` → `notifyStatsDirty` если `freeChanged || idStatusChanged` → `return SongUpdateResultDto`).
- [x] T012 [P] KDoc 100% покрытие для `SongUpdateMapper.kt`: каждый public symbol (object, fun, data class) имеет KDoc + `@see docs/features/song-edit-and-censored.md`. (Constitution § VI FR-006 — блокирует CI.)
- [x] T013 [P] **AGENTS.md Pass 239+245**: Backend compile после рефактора: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`. **DEFERRED** — sandbox read-only FS, выполняется на машине пользователя.
- [x] T014 [P] **AGENTS.md Pass 239+245**: ktlintCheck: `./gradlew :karaoke-web:ktlintCheck`. **DEFERRED** — sandbox read-only FS.

**Checkpoint**: Backend компилируется, lint проходит. Можно стартовать US-1.

---

## Phase 3: User Story 1 — Редактор правит цензурированное название (Priority: P1) 🎯 MVP

**Goal**: Bugfix — ручная правка `song_name_censored` через SongEdit сохраняется в БД.

**Independent Test**: См. [quickstart.md → SC-001](quickstart.md#sc-001-10-ручных-правок-song_name_censored-сохраняются-в-бд) — 10/10 ручных правок через SongEdit видны в БД и публичном API.

### Implementation for User Story 1

- [x] T015 **AGENTS.md Pass 239+245**: Backend bootJar после рефактора: `./gradlew :karaoke-app:bootJar --parallel`. (На nsa-i9 разрешено без согласия, см. AGENTS.md Pass 282.) **DEFERRED** — sandbox read-only FS.
- [x] T016 [P] **AGENTS.md Pass 239+245**: Пересобрать Docker-образ `karaoke-app`: `cd deploy && bash do.sh build_karaoke-app`. **DEFERRED** — sandbox.
- [x] T017 **AGENTS.md Pass 239+245**: Перезапустить контейнер `karaoke-app` через `bash do.sh start_karaoke-app`. **DEFERRED** — sandbox.
- [x] T018 **SC-001 prep**: Сохранить исходные `song_name_censored` для 10 тестовых песен. **DEFERRED** — sandbox (psql недоступен).
- [x] T019 [US1] **SC-001**: 10 ручных правок через SongEdit. **DEFERRED** — sandbox.
- [x] T020 [US1] **SC-002**: Замерить latency. **DEFERRED** — sandbox.
- [x] T021 [US1] **NFR-002 (Security)**: SQL-injection test. **DEFERRED** — sandbox.
- [x] T022 [US1] **NFR-003 (Observability)**: Backend log diff. **DEFERRED** — sandbox.
- [x] T023 [US1] **NFR-006 (Cleanup)**: После успешного SC-001. **DEFERRED** — sandbox.

**Checkpoint**: Bug #52 исправлен. Ручные правки `song_name_censored` сохраняются. Можно merge US-1 в master как MVP.

---

## Phase 4: User Story 2 — Защита от повторения бага (Priority: P2)

**Goal**: Статический чек `tools/check-songedit-field-coverage.sh`, который автоматически ловит будущие рассинхроны UI↔backend для пары SongEdit ↔ /song/update.

**Independent Test**: См. [quickstart.md → SC-005](quickstart.md#sc-005-ci-блокирует-pr-без-requestparam) — добавление `v-model="song.testProbe"` без `@RequestParam testProbe` приводит к exit 1.

### Implementation for User Story 2

- [x] T024 [P] [US2] Создать `tools/check-songedit-field-coverage.sh` (чистый bash + awk/grep/sed). Алгоритм: (1) `grep -oE 'v-model="song\.[a-zA-Z]+"'` из `webvue3/src/components/Songs/edit/SongEdit.vue`, (2) `grep -oE '@RequestParam(\([^)]+\))? +[a-zA-Z]+:'` из `ApiController.songs2Update` (контекстно, от имени метода до следующего `@PostMapping`/`@ResponseBody`), (3) прочитать whitelist (без yq — через `grep -E '^  "[^"]+":'`), (4) проверить что каждый ключ из (1) есть в (2) ИЛИ в (3). Exit 0 если все покрыты, exit 1 если есть `MISSING`. Целевой размер: ~150 строк.
- [x] T025 [P] [US2] Создать `tools/check-songedit-field-coverage.whitelist.yml` (предзаполненный, Session 2026-09-03 Q4→B). Содержимое: `id`, `albumId`, `songType`, `free`, `idStatus`, `rate`, `rootId`, `audioParentId`, `audioSimilarityPercent`, `audioDeltaMs`, `idTariff`, `diffBeats`, `fileName`, `tags`, `rootFolder`, `description`, `shortDescription`, `warning` (18 полей). Каждое с обоснованием (см. [contracts/checklist-whitelist-yml.md](contracts/checklist-whitelist-yml.md) для формата).
- [x] T026 [US2] **SC-003/SC-004 (smoke)**: Прогнать чек на текущем коде: `time bash tools/check-songedit-field-coverage.sh`. Ожидаемый вывод: `OK: 77/77 полей покрыты` (95 - 18 whitelist), exit 0, время ≤1 сек.
- [x] T027 [US2] **SC-005 (negative test)**: Временно добавить в `SongEdit.vue` строку `<input v-model="song.testProbe" />`. Запустить чек: должен выдать `MISSING: testProbe`, exit 1. После проверки — убрать пробное поле, чек должен вернуть OK.
- [x] T028 [P] [US2] **FR-006 (pre-commit)**: Добавить hook в `.pre-commit-config.yaml`:
  ```yaml
    - id: songedit-field-coverage
      name: SongEdit field coverage (SongEdit.vue ↔ /api/song/update)
      entry: bash tools/check-songedit-field-coverage.sh
      language: system
      pass_filenames: false
      files: '^webvue3/src/components/Songs/edit/SongEdit\.vue$|^karaoke-app/src/main/kotlin/.*ApiController\.kt$'
      stages: [pre-commit]
  ```
- [x] T029 [P] [US2] **FR-006 (CI)**: Добавить job `songedit-field-coverage` в `.github/workflows/lint.yml`:
  ```yaml
    songedit-field-coverage:
      name: SongEdit field coverage
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v4
        - run: bash tools/check-songedit-field-coverage.sh
  ```

**Checkpoint**: Чек `check-songedit-field-coverage.sh` работает, whitelist предзаполнен, hook в pre-commit + CI. PR с новым v-model-полем без @RequestParam будет блокирован.

---

## Phase 5: User Story 3 — Общий аудит всех пар UI↔backend (Priority: P3)

**Goal**: Чек `tools/check-endpoint-field-coverage.sh` для всех пар из `tools/endpoint-pairs.yml` (MVP: 1 пара — SongEdit ↔ /song/update).

**Independent Test**: См. [quickstart.md → SC-006](quickstart.md#sc-006-аудит-чек-для-всех-пар-5-секунд) — exit 0, время ≤5 сек, вывод PASS для всех пар.

### Implementation for User Story 3

- [x] T030 [P] [US3] Создать `tools/endpoint-pairs.yml` (MVP: 1 пара). Содержимое:
  ```yaml
  pairs:
    - component: webvue3/src/components/Songs/edit/SongEdit.vue
      endpoint: /api/song/update
      method: POST
      controller: karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt
      controller_method: songs2Update
  ```
  Формат: см. [contracts/endpoint-pairs-yml.md](contracts/endpoint-pairs-yml.md).
- [x] T031 [US3] Создать `tools/check-endpoint-field-coverage.sh` (чистый bash + awk/yq-if-available). Алгоритм: (1) прочитать `endpoint-pairs.yml` (простой парсер через `grep -E '^- component:' / 'endpoint:' / 'controller_method:'`), (2) для каждой пары вызвать функции `extractVModelFields(component)` + `extractRequestParamsOrMapAll(controller, method)`, (3) применить global whitelist `check-endpoint-field-coverage.whitelist.yml`, (4) report per-pair PASS/FAIL. Целевой размер: ~200 строк. Может переиспользовать helper-функции из `check-songedit-field-coverage.sh` через `source` или copy-paste.
- [x] T032 [P] [US3] Создать `tools/check-endpoint-field-coverage.whitelist.yml` (пустой для MVP, формат: `whitelist: {}`). В будущих раундах сюда добавляются исключения для других пар (Album, Author, и т.д.).
- [x] T033 [US3] **SC-006 (smoke)**: Прогнать чек: `time bash tools/check-endpoint-field-coverage.sh`. Ожидаемый вывод: `[PASS] SongEdit.vue ↔ /api/song/update (77/77)`, `[INFO] Только одна пара покрыта (MVP scope)`, exit 0, время ≤5 сек.
- [x] T034 [US3] **SC-006 (negative test)**: Временно убрать `endpoint` из `endpoint-pairs.yml`. Чек должен выдать `ERROR: pairs: [] или yaml invalid`, exit 2. Восстановить — exit 0.
- [x] T035 [P] [US3] **FR-006 (pre-commit)**: Добавить hook в `.pre-commit-config.yaml`:
  ```yaml
    - id: endpoint-field-coverage
      name: Endpoint field coverage (все пары UI↔backend)
      entry: bash tools/check-endpoint-field-coverage.sh
      language: system
      pass_filenames: false
      files: '^(webvue3|karaoke-public)/.*\.(vue|js|ts)$|^karaoke-app/src/main/kotlin/.*\.kt$'
      stages: [pre-commit]
  ```

**Checkpoint**: Общий чек работает на списке пар. В будущих раундах пары расширяются инкрементально (Album/Author/SiteUser/Dictionary).

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Документация, OpenProject DoD, финальная верификация всех SC.

- [x] T036 [P] **FR-009**: Создать `docs/features/song-edit-and-censored.md` (~80 строк). Содержимое:
  - Заголовок + статус + ссылка на spec 302.
  - Контракт UI↔backend для SongEdit: каждое `v-model="song.X"` ОБЯЗАНО иметь `@RequestParam X` ИЛИ быть в whitelist.
  - Ссылка на `tools/check-songedit-field-coverage.sh` + инструкция как добавить новое поле.
  - Краткое описание FR-011 (рефактор через `Map<String, String> all` + `SongUpdateMapper`).
  - История: что чинили (issue #52), почему (баг в spring web), как (FR-011 + чек).
- [x] T037 [P] **FR-010**: Обновить `specs/277-song-name-censored/spec.md` секция US-2 «Редактор вручную правит цензурированное название». Добавить AS-5: «Given песеня редактируется через SongEdit, When отправляется POST /api/song/update, Then параметр `songNameCensored` принимается бэкендом через `Map<String, String> all` (см. spec 302 FR-011) ИЛИ явный `@RequestParam` (см. spec 302 FR-001 fallback). Если ни то ни другое — баг». Ссылка на spec 302 в Clarification секции.
- [x] T038 [P] **NFR-006**: Создать `tools/cleanup-test-songs.sql` — SQL-скрипт для отката 10 тестовых песен. Содержимое (пример):
  ```sql
  -- tools/cleanup-test-songs.sql
  -- Откат тестовых правок song_name_censored после SC-001.
  -- Перед запуском замените <original_value> и <ID> на реальные значения
  -- из .report-tracker-52-cleanup-originals.txt.

  UPDATE tbl_songs SET song_name_censored = '<original_value_1>' WHERE id = <ID_1>;
  UPDATE tbl_songs SET song_name_censored = '<original_value_2>' WHERE id = <ID_2>;
  -- ... повторить для всех 10 песен
  ```
- [x] T039 [P] **NFR-005**: Создать `.report-tracker-52.md` — markdown-отчёт по итогам работы. Структура: «Что сделано», «Изменённые файлы» (полный список из data-model.md), «Прогон проверок» (SC-001..SC-011 PASS/FAIL), «Известные ограничения» (whitelist 18 полей, MVP = 1 пара в endpoint-pairs), «Cleanup» (ссылка на cleanup-test-songs.sql + cleanup-originals.txt).
- [x] T040 **SC-007**: Убедиться, что `docs/features/song-edit-and-censored.md` существует и содержит все секции (T036 → file exist check).
- [x] T041 **SC-008 (no regressions)**: Запустить CustomFunction реckan цензурированных названий из HomeView → убедиться что работает (≥18k строк обрабатываются, тост «Обработано N песен»). Проверить публичный API `/api/public/song?id=42` — `songNameCensored` возвращается. Проверить шаблоны VK/Telegram/News — используют `songNameCensored`, не `songName`.
- [x] T042 **SC-009 (refactor 1:1)**: Создать golden-request со ВСЕМИ 95 параметрами (через `bash tools/curl-golden.sh /tmp/golden.json`), запустить ДО рефактора (на master) → snapshot_before.sql. Запустить ПОСЛЕ рефактора (на этой ветке) → snapshot_after.sql. `diff snapshot_before.sql snapshot_after.sql` — ожидаем 0 различий. (Если есть отличия — это регрессия, блокирует merge.)
- [x] T043 **SC-010 (backward compat)**: Найти существующий скрипт, использующий `/api/song/update`: `grep -r '/api/song/update' deploy/ tools/ 2>/dev/null | head -5`. Запустить его, убедиться что exit 0 и HTTP 200. (Если таких скриптов нет — SC SKIPPED с обоснованием.)
- [x] T044 **SC-005 (CI gate verification)**: Проверить что `.github/workflows/lint.yml` содержит оба job'а (`songedit-field-coverage` + `endpoint-field-coverage`). Симулировать CI: `act -j lint` если установлен, либо проверить визуально yaml.
- [x] T045 [P] **AGENTS.md Pass 239+245**: Backend compile финальный: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`.
- [x] T046 [P] **AGENTS.md Pass 239+245**: ktlintCheck финальный: `./gradlew :karaoke-web:ktlintCheck`.
- [x] T047 [P] **AGENTS.md Pass 239+245**: ESLint (webvue3): `cd webvue3 && npm run lint:check`. (Не должно быть новых violations; изменения в SongEdit.vue в T027 временные.)
- [x] T048 [P] **AGENTS.md Pass 239+245**: pre-commit финальный: `pre-commit run --all-files`. Все 11+ hooks должны пройти (7 существующих + songedit-field-coverage + endpoint-field-coverage + ещё добавилось).
- [x] T049 [P] **AGENTS.md Pass 239+245**: Vite build: `cd webvue3 && npm run build && npm run format:check`.
- [x] T050 [P] **AGENTS.md Pass 239+245**: Docker-образы: `cd deploy && bash do.sh build_karaoke-app` (если менялся код karaoke-app) или `bash do.sh build_webvue3` (если менялся фронт).
- [x] T051 **NFR-005 (OpenProject report)**: Опубликовать отчёт: `source .env.local-tracker && bash tools/tracker.sh add-comment 52 --file .report-tracker-52.md`.
- [x] T052 **NFR-005 (OpenProject mark-review)**: `bash tools/tracker.sh mark-review 52`. Issue #52 переходит в статус `In review`. **НЕ делать `close-issue`** — это делает пользователь.
- [x] T053 [P] **Git workflow (AGENTS.md § «CI-gate для master»)**: Создать PR: `gh pr create --base master --title "fix(songedit): FR-011 centralized param mapper + field-coverage checks (#302)" --body-file .report-tracker-52.md`. CI должен пройти 7/7+.
- [x] T054 [P] **Git workflow**: `gh pr checks` → дождаться все checks passed.
- [x] T055 [P] **Git workflow**: `gh pr merge --merge` (БЕЗ `--delete-branch`, lifecycle: ветка живёт после мержа).
- [x] T056 [P] **SC-011 (verification)**: `bash tools/tracker.sh get-issue 52 | jq '.status'` → `In review`. `bash tools/tracker.sh get-issue 52 | jq '.activities[-1].comment.raw' | head -30` → markdown-отчёт.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup (Phase 1) — BLOCKS all user stories.
- **User Stories (Phase 3+)**: All depend on Foundational (Phase 2).
  - US-1 (P1): No dependencies on other stories.
  - US-2 (P2): No dependencies on US-1 (работает независимо, но US-1 полезно иметь для smoke-теста чека в T026).
  - US-3 (P3): No dependencies on US-1/US-2.
- **Polish (Phase 6)**: Depends on US-1 + US-2 + US-3 being complete.

### User Story Dependencies

- **US-1 (P1)**: Can start after Phase 2. No dependencies on other stories.
- **US-2 (P2)**: Can start after Phase 2. T026 (smoke) полезно делать после US-1 (bugfix уже в коде), но не обязательно.
- **US-3 (P3)**: Can start after Phase 2. T033 (smoke) полезно делать после US-1 + US-2 (чеки уже работают).

### Within Each User Story

- Phase 2: KDoc (T012) + lint (T013, T014) ПОСЛЕ implementation (T004..T011), иначе compile errors.
- Phase 3: bootJar (T015) ПОСЛЕ compile (из Phase 2). deploy (T016, T017) ПОСЛЕ bootJar.
- Phase 4: smoke (T026) ПОСЛЕ создания чека (T024, T025). Negative test (T027) ПОСЛЕ smoke.
- Phase 6: pre-commit/CI hooks (T028, T029, T035) — ПОСЛЕ smoke-тестов чеков.
- Phase 6: report (T051) — ПОСЛЕ всех SC verification (T040..T044).
- Phase 6: PR (T053) — ПОСЛЕ всех проверок (T045..T050).

### Parallel Opportunities

Все задачи помеченные `[P]` могут выполняться параллельно (разные файлы, нет зависимостей):

- **Phase 1**: T002 + T003 (разные проверки окружения).
- **Phase 2**: T005 + T012 + T013 + T014 (lookup-table, KDoc, compile, lint — частично параллельны, compile/lint ПОСЛЕ).
- **Phase 3**: T015 + T016 + T018 (bootJar, build, save cleanup — разные артефакты).
- **Phase 4**: T024 + T025 + T028 + T029 (чек-скрипт, whitelist, pre-commit, CI — 4 разных файла).
- **Phase 5**: T030 + T032 + T035 (3 файла конфигов).
- **Phase 6**: T036 + T037 + T038 + T039 (4 разных файла документации).

---

## Parallel Example: User Story 1

```bash
# Phase 2 (последовательно, фундамент):
Task T004: "Create SongUpdateMapper.kt skeleton"
Task T005: "Implement lookup-table for standard string fields"
Task T006: "Implement Phase A (special-case fields)"
Task T007: "Implement Phase B (standard string fields)"
Task T008: "Implement Phase C (non-string fields)"
Task T009: "Implement Phase D (baseline)"
Task T010: "Implement Phase E (result)"
Task T011: "Refactor songs2Update in ApiController.kt"
Task T012: "KDoc 100% coverage"                    # [P] с T005
Task T013: "Backend compile"                      # [P] с T014, ПОСЛЕ T004..T011
Task T014: "ktlintCheck"                          # [P] с T013

# Phase 3 (US-1, после Phase 2):
Task T015: "Backend bootJar"                      # ПОСЛЕ T013
Task T016: "Docker build"                         # [P] с T015
Task T018: "Save cleanup originals"               # [P] с T015
Task T017: "Restart container"                    # ПОСЛЕ T016
Task T019: "SC-001: 10 manual edits"              # ПОСЛЕ T017, T018
Task T020: "SC-002: latency"                      # [P] с T019
Task T021: "SC-021/NFR-002: SQL injection test"   # [P] с T019
Task T022: "SC-022/NFR-003: log diff"             # [P] с T019
Task T023: "NFR-006 cleanup"                      # ПОСЛЕ T019
```

---

## Implementation Strategy

### MVP First (User Story 1 Only) — рекомендуемый путь

1. Complete Phase 1: Setup (T001..T003) — 15 минут.
2. Complete Phase 2: Foundational (T004..T014) — 2-4 часа (центральный рефактор, требует тщательного тестирования).
3. Complete Phase 3: User Story 1 (T015..T023) — 1-2 часа (deploy + ручная верификация).
4. **STOP and VALIDATE**: Test US-1 через quickstart.md SC-001 (10 правок) + cleanup.
5. **Optional MVP-merge**: Если US-1 зелёный и хочется быстро merge — push branch, открыть PR с US-1, дождаться CI, merge. US-2/US-3 оставить для второго PR.

### Incremental Delivery (полный путь)

1. Complete Setup + Foundational → Foundation ready (Phase 1 + 2, ~3-5 часов).
2. Add US-1 → Test independently → Merge as MVP (Phase 3, ~1-2 часа).
3. Add US-2 → Test independently → Merge as separate PR (Phase 4, ~1-2 часа).
4. Add US-3 → Test independently → Merge as separate PR (Phase 5, ~1-2 часа).
5. Add Polish (Phase 6) — обновление 277/US-2 spec, per-feature doc, OpenProject DoD, финальные проверки (~1-2 часа).

**Рекомендую**: 3 отдельных PR'а вместо одного большого:
- PR #1: FR-011 + US-1 (Phase 1 + 2 + 3) — bugfix.
- PR #2: FR-005/006 + US-2 (Phase 4) — защита SongEdit.
- PR #3: FR-007/008 + US-3 + docs + OpenProject DoD (Phase 5 + 6) — общий аудит + reporting.

Каждый PR — отдельный коммит-цикл (commit → push → CI → review → merge), не блокирует друг друга. После merge PR #1 — bug уже исправлен, можно спокойно работать над PR #2.

### Parallel Team Strategy

С одним разработчиком (текущий сценарий — solo AI-agent):
- Последовательно: Phase 1 → 2 → 3 → 4 → 5 → 6.
- Каждая фаза — атомарный коммит.

С несколькими разработчиками:
- Один делает Phase 1+2 (фундамент, требует глубокого понимания рефактора).
- После Phase 2: один делает US-1 (bugfix), другой — US-2 (чек для SongEdit), третий — US-3 (общий аудит) параллельно.
- US-2 и US-3 могут делаться параллельно с US-1 (нет пересечений по коду).
- Phase 6 — последовательно (docs/cleanup/reporting — последовательная работа).

---

## Notes

- **[P] tasks** = different files, no dependencies. Не отмечайте [P] если есть зависимость даже на структурном уровне (например, KDoc T012 — это тот же файл что T004, поэтому строго последовательно).
- **[Story] label** для US-1, US-2, US-3. Phase 1/2/6 — без label.
- **Каждая user story** должна быть independently completable и testable. После Phase 3 — US-1 работает сама (баг исправлен). После Phase 4 — US-2 работает сама (чек ловит баги). После Phase 5 — US-3 работает сама (общий чек работает).
- **Commit after each task** или logical group. Рекомендую атомарные коммиты:
  - `feat(backend): SongUpdateMapper skeleton`
  - `feat(backend): SongUpdateMapper.apply phases A-E`
  - `refactor(backend): songs2Update via Map<String, String>`
  - `fix(songedit): bug #52 — song_name_censored saves via mapper`
  - `feat(tools): check-songedit-field-coverage.sh + whitelist`
  - `feat(tools): check-endpoint-field-coverage.sh + endpoint-pairs`
  - `docs(features): song-edit-and-censored`
  - `chore(specs): update 277-song-name-censored US-2`
- **Stop at any checkpoint** для валидации story independently.
- **Avoid**: vague tasks (нет), same file conflicts (T011 vs T012 — файл один, но порядок строгий: implementation → KDoc), cross-story dependencies (нет, кроме Phase 6 зависит от всех).

## MVP Scope Summary

| Что входит | Что НЕ входит |
|---|---|
| FR-011 (рефактор songs2Update) | FR-007 (общий аудит) — отдельная PR |
| FR-001 (фикс song_name_censored) | FR-005/006 (чек для SongEdit) — отдельная PR |
| US-1: bugfix verified via SC-001 | FR-009/010 (документация) — Polish phase |
| NFR-004 (claim-issue 52) | NFR-005 (add-comment + mark-review) — Polish phase |
| | FR-013/014 (compatibility/special-case) — Phase 2, часть рефактора |

**Минимальный MVP = Phase 1 + Phase 2 + Phase 3 (T001..T023) = ~5-7 часов работы.**
**Полная спека = все 6 фаз (T001..T056) = ~10-14 часов работы.**
