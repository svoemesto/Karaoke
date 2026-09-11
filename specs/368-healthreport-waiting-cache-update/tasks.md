# Tasks: HealthReport WAITING status + SSE re-compute on cache fill

**Input**: Design documents from `/specs/368-healthreport-waiting-cache-update/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Tests**: спецификация НЕ запрашивает test tasks (CI не падает на @Disabled, проверка пользователем — см. `quickstart.md`). Поэтому **нет** test tasks.

**Organization**: Tasks сгруппированы по user story (P1, P2, P3) для независимой реализации и тестирования.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Backend Kotlin**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/...`
- **Single project**: paths показаны ниже

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка изменений enum и сигнатур, не зависящих от user story.

- [ ] T001 Добавить значение `WAITING(color = "#FFCCFF")` в `HealthReportStatus.kt` — файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReportStatus.kt`
- [ ] T002 [P] Добавить параметр `onFillComplete: (() -> Unit)? = null` в `StorageMetadataCache.getFileExists` — файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt:114`
- [ ] T003 [P] Добавить параметр `onFillComplete: (() -> Unit)? = null` в `StorageMetadataCache.getFileExistsAsync` — файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt:397`
- [ ] T004 [P] Добавить SLF4J-категорию `infra.cache.storage.waiting` в `StorageMetadataCache.companion object` (новый logger) — файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Изменения сигнатур в `HealthReport.cachedFileExists` и `cachedFileExistsAsync`, пробрасывающие `onFillComplete` в `StorageMetadataCache`. Без этого US1/US2/US3 не работают.

**⚠️ CRITICAL**: US1/US2/US3 work can begin только после Phase 2.

- [ ] T005 Добавить параметр `onFillComplete: (() -> Unit)? = null` в `HealthReport.Companion.cachedFileExists` — файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:110`
- [ ] T006 Добавить параметр `onFillComplete: (() -> Unit)? = null` в `HealthReport.Companion.cachedFileExistsAsync` — файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:117`
- [ ] T006a Verify single-flight guard (FR-009): убедиться, что `StorageMetadataCache.upsert` использует `ON CONFLICT (source, bucket, file_name) DO UPDATE` (Pass 348) — два параллельных вызова на один cache key не создают две записи. Это покрывает FR-009 спеки без дополнительного кода. Файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt:310-335`

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 — Admin видит WAITING-индикатор вместо ошибки при первом открытии песни (Priority: P1) 🎯 MVP

**Goal**: При cache miss в `StorageMetadataCache` UI сразу отображает `WAITING` (`#FFCCFF`) для всех проверок `fileExists`/`fileIsActual`, не `ERROR`.

**Independent Test**: Очистить `StorageMetadataCache` (refresh-all endpoint), открыть страницу Songs с 20 песнями. Все `FILE_VIOLATION`-записи для песен, где файлы реально есть, должны отображаться с цветом `#FFCCFF` и `HealthReportStatus = WAITING`.

**Acceptance Scenarios** (из spec.md § User Story 1):
- AC1: HTTP-ответ ≤500 мс, все 3 location = WAITING
- AC2: После fill (через ~2 сек) — UI получает SSE, записи → OK без F5
- AC3: Реальные нарушения показываются как ERROR, остальные — WAITING

### Implementation

- [ ] T007 [US1] Изменить `actionsLocalStorage` (HealthReport.kt:571-...) — для каждого branch где `existsInLocalStore` определяется через `cachedFileExists`, при cache miss заменить потенциальный `ERROR` на `WAITING`. Файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt`
- [ ] T008 [US1] Изменить `actionsRemoteStorage` (HealthReport.kt:789-...) — аналогично для REMOTE branch. Использовать `cachedFileExistsAsync` с callback `recomputeAndBroadcast`. Файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt`
- [ ] T009 [US1] Обновить `cachedFileExistsAsync` вызов в `actionsRemoteStorage` (HealthReport.kt:963) — добавить `onFillComplete = { recomputeAndBroadcast(song.id, database, storageService, storageApiClient) }`. Файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt`

---

## Phase 4: User Story 2 — Race condition: cache fill завершается во время показа страницы (Priority: P2)

**Goal**: При фоновом заполнении кеша UI получает SSE `HEALTH_REPORTS` и обновляет записи с `WAITING → OK` без F5.

**Independent Test**: Открыть страницу Songs с 20 песнями, подождать 5 секунд. Все песни, у которых `StorageMetadataCache` заполнился, должны отображаться с `OK`-статусом.

**Acceptance Scenarios** (из spec.md § User Story 2):
- AC1: 20 песен на странице, WAITING → OK по мере fill (без F5)
- AC2: cache fill завершён успешно → SSE `HEALTH_REPORTS` для затронутых песен
- AC3: cache fill завершён с ошибкой → остаёмся в WAITING (НЕ возвращаемся к ERROR)

### Implementation

- [ ] T010 [US2] В `StorageMetadataCache.getFileExistsAsync` — после успешного `upsert` в `cacheFillerExecutor.submit { ... }` добавить вызов `onFillComplete?.invoke()`. Файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt:397-427`
- [ ] T011 [US2] В `StorageMetadataCache.getFileExists` (sync) — после успешного `upsert` добавить вызов `onFillComplete?.invoke()` перед `return value`. Файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt:114-129`
- [ ] T012 [P] [US2] Добавить SLF4J-логирование в категорию `infra.cache.storage.waiting` после `upsert` (для sync и async методов): `INFO cache:filled ... status=FILLED durationMs=X` или `WARN cache:fillFailed ... error=Y`. Файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt`

---

## Phase 5: User Story 3 — Backward compatibility: SSE не отправляется, если cache уже hit (Priority: P3)

**Goal**: На warm cache (≥80% hit rate) перезагрузка страницы Songs не порождает лишних SSE `HEALTH_REPORTS` событий.

**Independent Test**: После прогрева `StorageMetadataCache`, перезагрузить страницу Songs. Никаких лишних SSE-событий в Network panel.

**Acceptance Scenarios** (из spec.md § User Story 3):
- AC1: Warm cache → все записи OK синхронно ≤200 мс, ноль SSE
- AC2: Редактирование одной песни не вызывает лишних SSE для других

### Implementation

- [ ] T013 [US3] Verify в `StorageMetadataCache.getFileExistsAsync` — если `cached != null` (cache hit), `onFillComplete` НЕ вызывается. Уже выполнено по дизайну (см. plan.md § Phase 1 / Interface Contracts), но требует code review подтверждения. Файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt:397-427`
- [ ] T014 [US3] Verify в `StorageMetadataCache.getFileExists` (sync) — аналогично, `onFillComplete` НЕ вызывается на cache hit. Файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt:114-129`
- [ ] T015 [US3] Обновить KDoc для `cachedFileExists` и `cachedFileExistsAsync` — явно задокументировать "callback НЕ вызывается на cache hit" для будущих читателей кода. Файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:110, 117`

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Финальная проверка — backward compat, регрессии, логирование.

- [ ] T016 Code review — убедиться, что `IN_PROGRESS` (repair-loop) НЕ заменён на `WAITING`. Проверить все места где `healthReportStatus = IN_PROGRESS` (HealthReport.kt:393, 1006, 1085) — не должны измениться. Файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt`
- [ ] T017 Code review — убедиться, что 8 существующих вызовов `cachedFileExists` (HealthReport.kt:466, 484, 664, 736, 800, 1051, 1115) **компилируются без изменений** благодаря default `null` для `onFillComplete`. Файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt`
- [ ] T018 [P] Запустить `./gradlew :karaoke-app:compileKotlin` для проверки компиляции. Команда: `cd /home/nsa/Karaoke && GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin --parallel`
- [ ] T019 [P] Запустить `./gradlew :karaoke-app:ktlintCheck` для проверки стиля. Команда: `cd /home/nsa/Karaoke && GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck`
- [ ] T020 Пересобрать bootJar: `cd /home/nsa/Karaoke && GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar --parallel` (НЕ перезапускать контейнер — только сборка, по AGENTS.md § Машинно-специфичные исключения, nsa-i9).
- [ ] T021 Запустить локальный контейнер `karaoke-app` (по согласованию с владельцем, см. AGENTS.md § Машинно-специфичные исключения). Команда: `cd /home/nsa/Karaoke/deploy && bash do.sh start_app` (только после сборки из T020).
- [ ] T022 Проверить, что новый контейнер стартовал без ошибок: `docker logs --tail 100 karaoke-app 2>&1 | grep -iE 'error|exception|warn|fatal'` (должно быть пусто или только известные предупреждения).
- [ ] T023 Запустить Сценарий 1 из `quickstart.md` — cold start с пустым кешем → WAITING → SSE → OK. Замерить латентность HTTP-ответа (SC-001: ≤500 мс) и время прогрева через SSE (SC-002: ≤3 сек).
- [ ] T024 Запустить Сценарий 2 из `quickstart.md` — warm cache перезагрузка без лишних SSE (SC-003).
- [ ] T025 Запустить Сценарий 3 и 4 из `quickstart.md` — регрессии: `IN_PROGRESS` (repair-loop) и реальная `ERROR` (ручное удаление файла) показываются корректно.
- [ ] T026 Проверить `infra.cache.storage.waiting` логи: `docker logs karaoke-app --since 1m 2>&1 | grep 'infra.cache.storage.waiting' | wc -l` (должно быть >0 после cold start).
- [ ] T027 Создать commit в feature-ветке `368-healthreport-waiting-cache-update` (НЕ в master — см. AGENTS.md § Git — CI-gate для master). Перед commit запустить pre-commit checklist (см. AGENTS.md § Перед каждым git commit). Сообщение: `area: спека #368 — HealthReport WAITING status + SSE re-compute на cache fill`.
- [ ] T028 Создать PR через `gh pr create --base master`, дождаться CI 7/7 PASS.
- [ ] T029 После merge — выполнить `tools/tracker-implement-done.sh 80` (Pass 350 auto-hook) для автоматической публикации `report.md` + `mark-review` на OpenProject.

---

## Dependencies & Execution Order

### Story completion order

```
Phase 1 (Setup) ─────┐
                     │
Phase 2 (Foundation) │
                     │
                     ▼
Phase 3 (US1, P1) ───┬──► Phase 4 (US2, P2) ───► Phase 5 (US3, P3) ───► Phase 6 (Polish)
```

US1, US2, US3 — sequential dependencies, но внутри каждой фазы задачи могут выполняться параллельно (на разных файлах).

### Parallel opportunities

В Phase 1 (T002, T003, T004 — все про `StorageMetadataCache.kt`, sequential внутри файла).

В Phase 4 (T010, T011, T012 — `[P]` T012 можно делать параллельно с T010/T011 в разных местах файла, но sequential commit безопаснее).

### Independent test criteria per story

| Story | Independent test |
|-------|------------------|
| US1 (MVP) | Открыть Songs page на cold cache → цвет `#FFCCFF`, HTTP ≤500 мс |
| US2 | Подождать 5 сек на Songs page, проверить `WAITING → OK` через SSE |
| US3 | Прогреть кеш, перезагрузить Songs page, проверить отсутствие лишних SSE |

---

## Implementation Strategy

### MVP (User Story 1 only)

Минимально жизнеспособный продукт для спеки #368 — это **US1**: пользователь видит `WAITING` вместо ложных `ERROR`. Без US2 (SSE) US1 уже лучше текущего бага (нет ложных ERROR), но зависает в WAITING до F5. С US2 — полное решение.

**Стратегия**: реализовать US1 + US2 вместе (Phase 3 + Phase 4) — они образуют одну функциональную единицу. US3 (regression test) можно сделать позже, но это просто KDoc/code review — занимает ≤30 мин.

### Incremental delivery

1. **Phase 1 + 2**: foundation (1 час работы)
2. **Phase 3 + 4 + 5 + 6**: реализация US1-US3 + polish (3-4 часа работы + ~1 час на UI-верификацию)

Итого: ~5 часов работы.

---

## Format Validation

Все 29 задач следуют формату `- [ ] [TaskID] [P?] [Story?] Description with file path`:
- ✅ Checkbox в начале
- ✅ Sequential TaskID (T001-T029)
- ✅ [P] marker где применимо (только для truly parallel)
- ✅ [Story] label где требуется (только в Phase 3-5)
- ✅ Точные file paths в каждой задаче