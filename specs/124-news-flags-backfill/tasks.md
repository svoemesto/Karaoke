---

description: "Task list for feature 124-news-flags-backfill"
---

# Tasks: Backfill флагов публикаций готовых песен

**Input**: Design documents from `/specs/124-news-flags-backfill/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/api.md, quickstart.md

**Tests**: Тесты в CI отсутствуют (AGENTS.md «Тесты»). Валидация — ручная, по `quickstart.md`. Тестовых задач нет; каждая задача содержит implicitly проверку через запуск/lint.

**Organization**: Задачи сгруппированы по user story из spec.md (US1 P1 — backfill; US2 P2 — kill-switch защита от лавины при sync; US3 P3 — dry-run и отчёт). MVP = US1 + US2 (backfill + kill-switch — минимально жизнеспособный сценарий «привести флаги и не породить лавину»). US3 (dry-run/отчёт) добавляется поверх MVP.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Backend (Kotlin): `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`, `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/`
- Frontend (Vue 3): `webvue3/src/`
- Properties: `karaoke-app/.../KaraokeProperties.kt`
- Абсолютные пути от repository root (`/home/nsa/Karaoke/`)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка feature-ветки и проверка окружения. Проект уже существует — setup минимальный.

- [x] T001 Verify branch `124-news-flags-backfill` is current: `git branch --show-current` (должна быть `124-news-flags-backfill`, зарезервирована через `tools/reserve-branch-number.sh`)
- [x] T002 Verify karaoke-app container is running on LOCAL: `docker ps | grep karaoke-app` (если не запущен — попросить пользователя запустить, AGENTS.md «Запрещено» — агент не запускает контейнер)
- [x] T003 [P] Read existing `ApiController.doBackfillNewsAvailable` at `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:5561` как образец для нового endpoint'а (НЕ менять — образец)

**Checkpoint**: Ветка активна, контейнер запущен, образец изучен.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Базовая инфраструктура, обязательная для всех user stories. Для этой фичи — НЕТ отдельных foundational задач: backfill использует существующие `Song.saveToDb()`, `Song.loadListFromDb()`, `SNS.send()`, `KaraokeProperties.getBoolean()`. Никаких новых сущностей/миграций (см. data-model.md).

**⚠️ CRITICAL**: Фаза пуста — можно сразу переходить к User Story 1.

**Checkpoint**: Foundation ready — реализация user stories может начинаться.

---

## Phase 3: User Story 1 - Разовый backfill флагов готовых песен (Priority: P1) 🎯 MVP

**Goal**: Реализовать `SongReleaseAnnouncementService.backfillPublishFlags()` и endpoint `POST /api/utils/backfillpublishflags` в `ApiController`, который проходит по всем готовым песням (`id_status=6` + непустые `source_markers`) на LOCAL и выставляет флаги `player_readiness_flags` в complete-состояние через `Song.saveToDb()`. Прогресс репортится SSE-тостами.

**Independent Test**: `curl -s -X POST "http://localhost:8898/api/utils/backfillpublishflags" -d "target=local&dryRun=false"` → SSE-прогресс + финальный тост с отчётом; проверить тестовую песню в БД (`newsAvailableAnnounced=true`, `premiumAutoPublishState="COMPLETE"`); проверить `tbl_news` на LOCAL = 0 новых.

### Implementation for User Story 1

- [x] T004 [US1] Add `backfillPublishFlags` method to `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt` — по образцу существующего `backfillNewsAvailableFlag` (`SongReleaseAnnouncementService.kt:226`), но выставляет ПОЛНЫЙ complete-набор флагов (см. research.md Решение 4 и data-model.md «После backfill»): `newsAvailableAnnounced=true`, `newsPremiumPublishPending=false`, `newsPremiumTelegramSent=true`, `newsPremiumVkSent=true`, `premiumAutoPublishState="COMPLETE"`, `premiumAutoPublishLastError=""`, `premiumAttemptCount=0`. Грузит чанками по `CHUNK_SIZE=25` (существующая константа), фильтрует `isContentReady` (= `id_status>=6` + стемы + обложки + `sourceMarkersList.isNotEmpty()`), пропускает активные публикации (`telegramAutoPublishState`/`vkAutoPublishState` in `rendering`/`publishing`) и песни без маркеров. Каждый chunk: `Song.loadListFromDb` → для каждого кандидата `setReadinessFlag`/`setReadinessStringFlag` → `saveToDb()` (идемпотентно через `getDiff` early-return). Возвращает отчёт-объект (см. T006).
- [x] T005 [US1] Add `dryRun` parameter to `backfillPublishFlags` in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt` — при `dryRun=true` НЕ вызывает `saveToDb()`, только считает сколько БЫЛО БЫ исправлено (та же логика фильтрации + проверка текущего значения флага, но без записи). Возвращает отчёт с `dryRun=true`, `durationMs=0` (FR-013, SC-010).
- [x] T006 [US1] Define report data class in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt` (или рядом) — `data class BackfillReport(totalCandidates, fixedNewsAvailableAnnounced, fixedPremiumComplete, alreadyOk, skippedActivePublishing, skippedNoMarkers, durationMs, dryRun)` с `toJson()` методом (возвращает JSON-строку для `Message.body` SSE-тоста, см. contracts/api.md «Отчёт»).
- [x] T007 [US1] Add `POST /api/utils/backfillpublishflags` endpoint to `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` — по образцу `doBackfillNewsAvailable` (`ApiController.kt:5561`): `@PostMapping("/utils/backfillpublishflags")`, `@ResponseBody`, параметры `target=local` (default), `dryRun=false` (default). Запуск в `thread { ... }` (фон). Вызов `SongReleaseAnnouncementService.backfillPublishFlags(database=Connection.local(), storageService, storageApiClient, dryRun=dryRun)`. Прогресс-тосты каждые ~500 обработанных песен через `SNS.send(SseNotification.message(...))` с body «Обработано N/total...». Финальный тост с `head="Backfill флагов публикаций (local) — завершено"` и `body=<BackfillReport.toJson()>`. Тост ошибки при exception (по образцу `doBackfillNewsAvailable` catch-блок).
- [x] T008 [US1] Add progress reporting inside `backfillPublishFlags` in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt` — счётчик обработанных песен, каждый chunk проверяет `if (processedCount % 500 == 0) SNS.send(SseNotification.message(...))` с прогресс-текстом. Для dry-run прогресс НЕ репортится (быстро, без записи) — только финальный отчёт.

**Checkpoint**: User Story 1 полностью функционален. Тест: `curl .../backfillpublishflags -d "target=local&dryRun=false"` → SSE-тосты + тестовая песня в complete-состоянии + 0 новостей в `tbl_news` на LOCAL. MVP готов.

---

## Phase 4: User Story 2 - Защита от лавины новостей при sync (kill-switch) (Priority: P2) 🎯 MVP

**Goal**: Добавить kill-switch `newsAutoPublishKillSwitch` в `KaraokeProperties` (default=false) и проверку в `News.createAutoAnnouncement` — при `true` метод возвращает `null` без INSERT в `tbl_news`. Это блокирует обе точки создания auto-новостей (`detectAndAnnouncementService.detectAndAnnounceAvailability` из sync и `checkOnAirWindow` из scheduler'а) на PROD во время sync-окна после backfill.

**Independent Test**: Включить kill-switch на PROD: `curl -s -X POST ".../api/properties/setproperty" -d "key=newsAutoPublishKillSwitch&stringValue=true"`. Запустить sync LOCAL→PROD. Проверить `tbl_news` на PROD = 0 новых `source='auto'`. Снять kill-switch: `...&stringValue=false`.

### Implementation for User Story 2

- [x] T009 [US2] Add `newsAutoPublishKillSwitch` property to `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` — default `false`, описание KDoc: «Временный kill-switch, блокирует создание auto-новостей (`News.createAutoAnnouncement`) во время sync-окна после backfill флагов (specs/124-news-flags-backfill). Включается/снимается через `/api/properties/setproperty` без рестарта контейнера». Свойство читается через `KaraokeProperties.getBoolean("newsAutoPublishKillSwitch")`.
- [x] T010 [US2] Add kill-switch check to `News.createAutoAnnouncement` in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/News.kt:337` — первой строкой метода: `if (KaraokeProperties.getBoolean("newsAutoPublishKillSwitch")) return null`. KDoc: «Блокировка auto-новостей во время sync-окна (specs/124-news-flags-backfill FR-010/FR-011). При `newsAutoPublishKillSwitch=true` метод no-op — покрывает обе точки: `detectAndAnnouncementService.detectAndAnnounceAvailability` (sync, premium) и `checkOnAirWindow` (scheduler, air). Ручные новости (`News.createNew`, `source='manual'`) НЕ блокируются».
- [x] T011 [US2] Verify kill-switch does NOT block `News.createNew` (manual news) — визуально проверить, что `News.createNew` (`News.kt:312`) НЕ содержит проверки kill-switch (только `createAutoAnnouncement` имеет). Это гарантирует, что админ может создавать ручные новости во время sync-окна (INV-004).

**Checkpoint**: User Story 2 полностью функционален. MVP (US1 + US2) готов: backfill на LOCAL + kill-switch на PROD + sync = 0 лавины новостей.

---

## Phase 5: User Story 3 - Диагностика и отчёт о расхождениях (dry-run + отчёт) (Priority: P3)

**Goal**: Dry-run режим backfill (возвращает отчёт без записи) и финальный отчёт с разбивкой по категориям. Dry-run числа должны совпадать с числами реального backfill (SC-010).

**Independent Test**: `curl .../backfillpublishflags -d "target=local&dryRun=true"` → SSE-тост с отчётом `dryRun=true`. Затем `...&dryRun=false` → отчёт `dryRun=false`, числа `fixed*` совпадают. Повторный dry-run → `alreadyOk=N`, `fixed*=0`.

### Implementation for User Story 3

- [x] T012 [US3] Verify dry-run path in `backfillPublishFlags` (already implemented in T005) returns correct report — explicitly test: на тестовой песне с `newsAvailableAnnounced=false` dry-run показывает `fixedNewsAvailableAnnounced=1`, real backfill тоже `fixedNewsAvailableAnnounced=1`, повторный dry-run `fixedNewsAvailableAnnounced=0` (SC-010). Если расхождение — фикс в T005.
- [x] T013 [US3] Verify report JSON format matches `contracts/api.md` «Отчёт» — поля `totalCandidates`, `fixedNewsAvailableAnnounced`, `fixedPremiumComplete`, `alreadyOk`, `skippedActivePublishing`, `skippedNoMarkers`, `durationMs`, `dryRun`. Проверить, что `Message.body` в финальном SSE-тосте содержит валидный JSON (webvue3 парсит).
- [x] T014 [US3] Add UI button to webvue3 — кнопка «Backfill флагов публикаций» + чекбокс «Dry run» рядом с существующей кнопкой «Backfill флага «доступна»» (по образцу `doBackfillNewsAvailable` UI). Точное место — найти через `grep -rn "backfillnewsavailable" webvue3/src/` и разместить рядом. Вызов `POST /api/utils/backfillpublishflags` с `target=local`, `dryRun` из чекбокса. Прогресс и результат рендерятся через существующий SSE-listener (toast-уведомления, как у других утилит).

**Checkpoint**: User Story 3 полностью функционален. Dry-run + real backfill + UI — фича завершена.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Финализация — документация, per-feature doc, CI-gate.

- [x] T015 [P] Update `docs/architecture-notes.md` — добавить запись о PR `124-news-flags-backfill` в конец файла (по AGENTS.md «Как обновлять этот файл»).
- [x] T016 [P] Update or create `docs/features/news-publish-backfill.md` per-feature document — структура `## Что делает`, `## Зачем`, `## Как работает`, `## Инварианты / правила`, `## Известные ловушки`, `## Ссылки` (по AGENTS.md «Q: Как добавить per-feature документ»). Добавить запись в `docs/features/README.md` таблицу (если новой фиче нужен документ — фича затрагивает подсистему «новости/публикации»).
- [x] T017 Run lint checks locally (AGENTS.md «Q: Как проверить, что CI пройдёт локально?»): `./gradlew ktlintCheck`, `cd webvue3 && npm run lint:check`, `bash tools/check-kdoc-coverage.sh`, `bash tools/check-jsdoc-coverage.sh webvue3`. Все должны быть зелёными.
- [ ] T018 Commit changes (NO push, NO PR yet — это делает пользователь). Проверить `git status`, `git diff`. Закоммитить только intended files (НЕ `deploy/.env`, `node_modules`, `dist`).
- [ ] T019 Create PR via `gh pr create --base master` (только после явного запроса пользователя — AGENTS.md «Git»), дождаться CI 7/7 SUCCESS, затем `gh pr merge --merge --delete-branch`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — начать сразу.
- **Foundational (Phase 2)**: Пустая фаза — пропустить.
- **User Stories (Phase 3–5)**:
  - US1 (Phase 3) — core backfill, зависит только от Setup.
  - US2 (Phase 4) — kill-switch, зависит только от Setup; НЕ зависит от US1 (kill-switch можно реализовать до backfill, логически — это защита sync-окна, работает независимо от того, был backfill или нет).
  - US3 (Phase 5) — dry-run + отчёт + UI; US3 зависит от US1 (dry-run — это параметр `backfillPublishFlags`, UI кнопка вызывает endpoint из US1).
- **Polish (Phase 6)**: зависит от всех завершённых user stories.

### User Story Dependencies

- **US1 (P1)**: после Setup. Нет зависимостей от других stories. → MVP part 1.
- **US2 (P2)**: после Setup. Нет зависимостей от US1 (но логически следует за US1 — kill-switch включается после backfill, перед sync). → MVP part 2.
- **US3 (P3)**: после US1 (использует endpoint/метод из US1, добавляет dry-run параметр и UI).

### Within Each User Story

- T004 (backfillPublishFlags) → T005 (dryRun параметр) → T006 (report data class) → T007 (endpoint) → T008 (прогресс). Все в одном/смежных файлах, последовательное выполнение.
- T009 (KaraokeProperties) → T010 (News.createAutoAnnouncement) — последовательное.
- T012 (verify dry-run) → T013 (verify report) → T014 (UI button) — последовательное.

### Parallel Opportunities

- US1 и US2 могут разрабатываться параллельно (разные файлы: US1 — `SongReleaseAnnouncementService.kt`/`ApiController.kt`, US2 — `KaraokeProperties.kt`/`News.kt`).
- T015 (architecture-notes) и T016 (per-feature doc) — параллельны (разные файлы).
- T009 (KaraokeProperties) и T010 (News.kt) — параллельны (разные файлы, но логически последовательны — T010 зависит от существования свойства в T009 для `getBoolean`).

---

## Parallel Example: US1 + US2 (MVP)

```bash
# Запуск MVP параллельно (2 разработчика):

# Разработчик A — US1 (backfill):
Task: "T004 Add backfillPublishFlags method in SongReleaseAnnouncementService.kt"
Task: "T006 Define BackfillReport data class"
Task: "T007 Add POST /api/utils/backfillpublishflags endpoint in ApiController.kt"

# Разработчик B — US2 (kill-switch):
Task: "T009 Add newsAutoPublishKillSwitch property in KaraokeProperties.kt"
Task: "T010 Add kill-switch check to News.createAutoAnnouncement in News.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2)

1. Complete Phase 1: Setup (verify branch + container + образец).
2. Skip Phase 2: Foundational (пустая).
3. Complete Phase 3: US1 (backfill endpoint + метод) — ТЕСТ на LOCAL: backfill отработал, флаги в complete, 0 новостей.
4. Complete Phase 4: US2 (kill-switch) — ТЕСТ: включить kill-switch на PROD, sync, 0 новостей в `tbl_news`, снять kill-switch.
5. **STOP and VALIDATE**: MVP готов — backfill + sync с kill-switch = проблема пользователя решена.
6. Deploy/demo (по quickstart.md Шаги 0–4).

### Incremental Delivery

1. MVP (US1+US2): backfill + kill-switch — флаги приведены, лавина новостей блокирована.
2. Add US3: dry-run + отчёт + UI кнопка — админ может.preview изменений и запускать из UI без curl.
3. Polish: документация, per-feature doc, CI 7/7, PR.

### Parallel Team Strategy

- Разработчик A: US1 (backend Kotlin: `SongReleaseAnnouncementService.kt` + `ApiController.kt`).
- Разработчик B: US2 (backend Kotlin: `KaraokeProperties.kt` + `News.kt`).
- После MVP: один разработчик — US3 (dry-run + UI кнопка в `webvue3`).

---

## Notes

- [P] tasks = different files, no dependencies. В этой фиче параллельность ограничена (мало задач, файлы связанные).
- [Story] label maps task to specific user story.
- Каждая user story independently completable и testable (см. quickstart.md).
- Валидация — ручная, по quickstart.md (тестов в CI нет, AGENTS.md).
- Commit после каждой задачи или логической группы. НЕ пушить/НЕ создавать PR без явного запроса пользователя.
- CI-gate: ktlint, ESLint webvue3, KDoc, JSDoc, docs structure — все 7 проверок должны быть зелёными перед merge (AGENTS.md «CI-gate для master»).
- Ветка `124-news-flags-backfill` уже зарезервирована и создана (через `tools/reserve-branch-number.sh`).
- Контейнер `karaoke-app` на LOCAL запускает только пользователь (AGENTS.md «Запрещено») — агент не запускает/перезапускает.
- Kill-switch на PROD включается/снимается через `/api/properties/setproperty` (без деплоя, без rsync, без SSH-правок — AGENTS.md «Деплой»).