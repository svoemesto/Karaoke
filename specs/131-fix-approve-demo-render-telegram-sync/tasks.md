---
description: "Task list for feature 131 implementation"
---

# Tasks: 131 — починка пайплайна после одобрения задания

**Input**: Design documents from `/specs/131-fix-approve-demo-render-telegram-sync/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/pipeline.md`, `quickstart.md`

**Tests**: Не запрошены явно ни в спецификации, ни пользователем — отдельные test-таски не создаются; вместо них в каждой фазе есть задача ручной проверки по `quickstart.md` (в проекте нет CI-тестов для этого слоя, см. Constitution → «Рабочий процесс» → «Тесты»).

**Organization**: Задачи сгруппированы по user story (US1/US2/US3 из spec.md — приоритеты P1/P1/P2, все три независимо тестируемы). Схемы БД, DTO, recordhash-триггеры не меняются (см. A-002 spec.md и Principle II конституции) — изменяются только 2 существующих файла: `SongEditorController.approve()` и `KaraokeProcessThread.run()`. Новых сервисов, контроллеров, эндпоинтов, миграций не появляется.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: к какой user story относится задача (US1/US2/US3)
- Указаны точные пути файлов

---

## Phase 1: Setup

**Purpose**: Свериться с текущим состоянием переиспользуемых сервисов и подтвердить, что их сигнатуры совпадают с `research.md` (фаза 0 искала их 02.08.2026)

- [X] T001 Свериться с текущим состоянием сигнатур и точек вызова:
  `KaraokeProcess.createProcess(song, type, doWait, prior, threadId)` — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt`;
  `KaraokeProcessWorker.KaraokeProcessThread.run()` (пост-хук HealthReport) — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:62`;
  `TelegramAutoPublishService.publishToTelegram(song, allowPastDate, publicationType, persistMessageId)` — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishService.kt:66`;
  `Utils.updateRemoteSongFromLocalDatabase(id)` — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:570`;
  `Utils.updateRemoteDatabaseFromLocalDatabase(updateSongs, updatePictures, updateAuthors)` — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:584`;
  `TelegramAutoPublishState.PUBLISHED`/`SEND_FAILED`/etc — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishState.kt`.
  Подтвердить, что сигнатуры и поведение не изменились с момента `research.md`. Если что-то изменилось — обновить `research.md` прежде, чем продолжать.

**Checkpoint**: базовая инфраструктура подтверждена неизменной — можно приступать к фазам US.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Подтвердить, что отсутствуют блокирующие шаги (нет миграций, нет новых DTO, нет новых сервисов)

**⚠️ CRITICAL**: Никаких work-задач в этой фазе нет — фича намеренно обходится без миграций (см. `spec.md` A-002, Principle II конституции)

- [X] T002 [P] Подтвердить, что для фичи не требуется ни одна из следующих вещей:
  (a) миграция схемы БД — `news_available_announced` уже хранится в существующем JSON-поле `tbl_songs.player_readiness_flags` (см. `data-model.md` §2.1);
  (b) новая запись в `SyncRegistry` — используем существующие `updateRemoteSongFromLocalDatabase`/`updateRemoteDatabaseFromLocalDatabase`;
  (c) новый DTO / JSON-поле в ответе API — фронт продолжает получать `SongDTO`/`SongPublicDto` без изменений;
  (d) новый HTTP-эндпоинт — фича встраивается в существующий `POST /editor/song/approve` (без правки сигнатуры);
  (e) новый публичный топик SSE — переиспользуем существующие `SseNotification.recordChange`/`message`/`crud`.
  Зафиксировать подтверждение в этой задаче (без кода).

**Checkpoint**: Foundation отсутствует по построению — сразу переходим к фазам US.

---

## Phase 3: User Story 1 — DEMO рендер и публикация в Telegram стартуют автоматически (Priority: P1) 🎯 MVP

**Goal**: Сразу после одобрения задания редактора в `tbl_processes` создаётся ровно одна запись `RENDER_MP4_DEMO` (1280×720@30fps); после её `DONE` песня автоматически публикуется в Telegram-канале, и в `tbl_songs.id_telegram_demo` сохраняется `message_id` отправленного сообщения. Идемпотентность — повторный approve не создаёт лишних процессов и лишних постов.

**Independent Test**: Сценарий S-001 из `quickstart.md` — одобрить задание редактора, у песни нет готового DEMO, дождаться:
(а) ровно одной строки в `tbl_processes` с `process_type='RENDER_MP4_DEMO'`;
(б) её перехода `WAITING`→`WORKING`→`DONE` в `/processes`;
(в) появления поста в Telegram-канале;
(г) появления `id_telegram_demo` в `tbl_songs`.
Также S-002 (повторный approve — ноль новых сущностей), S-005 (Telegram падает с 401 — render OK, sync OK, новость на сервере OK), S-007 (рендер ERROR — публикация не происходит), S-008 (`telegramAutoPublishEnabled=false`), S-009 (гард по активному процессу).

### Implementation for User Story 1

- [X] T010 [US1] Реализовать приватный helper `triggerRenderMp4DemoIfNeeded(song: Song)` в `SongEditorController.kt` (например, рядом с методом `approve()`, в конце `controllers` — той же видимости, что и другие приватные helpers контроллера). Логика:
  (1) `SELECT process_status FROM tbl_processes WHERE song_id = ? AND process_type = 'RENDER_MP4_DEMO' AND process_status IN ('WAITING','WORKING')` через `WORKING_DATABASE.getConnection()` с `try-with-resources` (см. образец `try { ... } catch (_: Exception) { println(...) }` в `TelegramAutoPublishScheduler.findRenderDemoProcess` — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishScheduler.kt:195`);
  (2) если результат непуст — `println("[approve/render-demo] skip — уже есть активный процесс для ${song.id}")` и `return`;
  (3) иначе `KaraokeProcess.createProcess(song = song, action = KaraokeProcessTypes.RENDER_MP4_DEMO, doWait = false, prior = 5, threadId = 0)` (см. `research.md` D-1/D-3, `contracts/pipeline.md` §2.2 шаг 1);
  (4) обернуть всё в `try { ... } catch (e: Exception) { println("[approve/render-demo] ошибка: ${e.message}") }` — НЕ пробрасывать наверх (см. `contracts/pipeline.md` §5 «Изоляция сбоев»);
  (5) добавить русскоязычный KDoc на helper со ссылкой `@see docs/features/approve-pipeline.md` (см. AGENTS.md → «FR-009»), `@see specs/131-fix-approve-demo-render-telegram-sync/contracts/pipeline.md`, и `@see specs/131-fix-approve-demo-render-telegram-sync/research.md`.
  Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt`

- [X] T011 [US1] В `SongEditorController.approve()` сразу **после** существующего блока `if (Karaoke.allowUpdateRemote) { ... updateRemoteSongFromLocalDatabase(song.id) ... }` (см. строку ~395 файла) и **до** существующего финального `aRead.save()` — добавить **синхронный** вызов `triggerRenderMp4DemoIfNeeded(song)`. Стиль логирования — как у соседнего блока (`[approve/timing] push на SERVER: ...`). Без новых веток `try/catch` — изоляция уже в helper'е.
  Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt` (зависит от T010 — не выполнять параллельно с T010, один файл)

- [X] T012 [P] [US1] В `KaraokeProcessWorker.kt`, внутри `KaraokeProcessThread.run()`, **после** существующего блока, который выставляет `status=DONE`/`ERROR` и сохраняет процесс (см. строки ~315-326 файла), и **до/после** (предпочтительно после) существующего пост-хука HealthReport (~строка 360) — добавить новую ветку:
  ```kotlin
  if (karaokeProcess.type == "RENDER_MP4_DEMO" && karaokeProcess.status == KaraokeProcessStatuses.DONE.name) {
      thread {
          try {
              val song = Song.loadFromDbById(
                  id = karaokeProcess.songId,
                  database = WORKING_DATABASE,
                  storageService = KSS_APP,
                  storageApiClient = SAC_APP,
              ) ?: return@thread
              TelegramAutoPublishService.publishToTelegram(
                  song = song,
                  allowPastDate = true,
                  publicationType = com.svoemesto.karaokeapp.model.PublicationType.AIR,
                  persistMessageId = true,
              )
          } catch (e: Exception) {
              println("[render-demo/post-hook] ошибка публикации: ${e.message}")
          }
      }
  }
  ```
  Идемпотентность `publishToTelegram` обеспечивается внутри самого сервиса по `idTelegramDemo` (см. `TelegramAutoPublishService.publishToTelegram:75-80` — early-return `PUBLISHED`). Логирование — в стиле `[render-demo/post-hook] …`.
  Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt` (другой файл, чем T010/T011 — может идти параллельно с ними; зависит только от T001 — без T011 код по-прежнему работает, просто не запустит процесс)

- [ ] T013 [US1] Живая проверка US1 на dev-машине (для администратора, см. AGENTS.md → «Разрешено» на `dev-pc`/`dev`) — выполнить сценарии из `quickstart.md`:
  - S-001 (happy path);
  - S-002 (повторный approve — идемпотентность);
  - S-005 (Telegram падает с 401 — рендер+sync OK, новость на сервере OK);
  - S-007 (рендер ERROR — публикация не происходит, sync+новость OK);
  - S-008 (`telegramAutoPublishEnabled=false` — рендер+sync OK, новость OK, Telegram нет);
  - S-009 (гард по активному процессу — повторный approve skip'ает).
  Зафиксировать результат (логи approve, состояние `tbl_processes`/`tbl_news`/`tbl_songs.id_telegram_demo`) **прямо в этой задаче**, по образцу T007 из `specs/101-song-news-flag/tasks.md`. Тестовые строки/флаги удалить после проверки.

**Checkpoint**: User Story 1 полностью функциональна — DEMO-рендер и Telegram-публикация стартуют автоматически при approve.

---

## Phase 4: User Story 2 — Песня синхронизируется с сервером и появляется в публичной коллекции (Priority: P1) 🎯 MVP

**Goal**: Сразу после одобрения (параллельно с US1) — связанные таблицы (`tbl_pictures`, `tbl_authors`, `tbl_albums`) пушатся на сервер через bulk-sync. На стороне сервера существующая логика (`MainController.doChangeRecords` → `SongReleaseAnnouncementService.detectAndAnnounceAvailability` → `News.createAutoAnnouncement(category = "premium")`) при применении `tbl_songs` создаёт ровно одну новость «появилась в коллекции». Картинки автора/альбома обновляются на публичных страницах.

**Independent Test**: Сценарий S-001 под-пункты 8-9 из `quickstart.md` — после одобрения задания:
(а) на главной `/news` (или в Vuex `stats.newsBadge`) появилась ровно одна новость с заголовком по шаблону specs/101;
(б) на `/song/<id>` обновилась обложка исполнителя/альбома (если раньше отличалась).
Также S-006 (sync-related сбой — рендер OK, Telegram OK, новость на сервере создаётся через tbl_songs push, related не дошли, ручной ретрай работает).

### Implementation for User Story 2

- [X] T020 [US2] В `SongEditorController.approve()` **сразу после** блока, добавленного в T011 (т.е. после вызова `triggerRenderMp4DemoIfNeeded(song)`) и **до** существующего финального `aRead.save()` — добавить блок:
  ```kotlin
  thread {
      try {
          val pushStart = System.currentTimeMillis()
          val result = updateRemoteDatabaseFromLocalDatabase(
              updateSongs = false,    // tbl_songs уже пушнута выше в T011-блоке (existing updateRemoteSongFromLocalDatabase)
              updatePictures = true,
              updateAuthors = true,
          )
          println(
              "[approve/sync-related] push related на SERVER: " +
                  "${System.currentTimeMillis() - pushStart} ms, " +
                  "created=${result.created.size} updated=${result.updated.size}",
          )
      } catch (e: Exception) {
          println("[approve/sync-related] ошибка sync related: ${e.message}")
      }
  }
  ```
  Использовать тот же стиль логов, что у `[approve/timing]` в существующем блоке `updateRemoteSongFromLocalDatabase` (см. файл, строки ~396-400). Импорт `updateRemoteDatabaseFromLocalDatabase` уже есть в файле (см. imports).
  Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt` (зависит от T011 — не выполнять параллельно с T010/T011, один файл)

- [ ] T021 [US2] Живая проверка US2 на dev-машине — выполнить сценарии из `quickstart.md`:
  - S-001 под-пункты 8-9 (новость «появилась в коллекции» появляется на главной, обложки обновляются на `/song/<id>`);
  - S-006 (`/etc/hosts` блокирует `188.119.64.111` → рендер OK, Telegram OK (от Т013), approve возвращает `ok:true` за ≤5 с, sync-related в логах содержит `[approve/sync-related] ошибка sync related: …`, кнопка «Обновить на сервере» после восстановления сети добивает related-таблицы).
  Зафиксировать результат в этой задаче. Тестовые данные удалить.

**Checkpoint**: User Story 2 полностью функциональна — синхронизация с сервером покрывает связанные таблицы, новость на сервере создаётся.

---

## Phase 5: User Story 3 — Ручные триггеры и существующая логика не ломаются (Priority: P2)

**Goal**: Фича аддитивна — кнопки «Рендер MP4 DEMO», «Опубликовать в Telegram сейчас», «Обновить на сервере», плановые scheduler'ы (`TelegramAutoPublishScheduler`, `VkAutoPublishScheduler`, `SongReleaseAnnouncementScheduler`) и существующий `POST /utils/updateremotesongfromlocaldatabase` продолжают работать без изменений; `telegramAutoPublishEnabled` управляет только Telegram-публикацией (не блокирует рендер/sync).

**Independent Test**: Сценарии S-003 (ручной «Рендер MP4 DEMO» поверх approve — новая задача создаётся, Telegram-пост не дублируется, sync-related повторно делает diff без пустых push'ей), S-004 (параллельный approve + ручной publishToTelegram — ровно одна задача и один пост), S-008 (полное покрытие US3 — см. T013).

### Implementation for User Story 3

- [ ] T030 [US3] Живая проверка US3 на dev-машине — выполнить сценарии из `quickstart.md`:
  - S-003 (ручной «Рендер MP4 DEMO» поверх approve — новая задача `RENDER_MP4_DEMO`, второй пост не уходит);
  - S-004 (параллельный approve + ручной «Опубликовать в Telegram сейчас» — ровно 1 задача и 1 пост);
  - S-008 (см. также T013 — `telegramAutoPublishEnabled=false` гард на стороне `TelegramAutoPublishService.publishToTelegram`, не на стороне approve — это контрактное требование FR-012 spec.md).
  Особо проверить: в логах approve **нет** никаких изменений формата ответа (HTTP `{"ok":true,"status":"approved"}` для одобренной, `{"ok":true,"status":"already_approved"}` для повторной); `Postman`/`curl` против существующих эндпоинтов (`/utils/updateremotesongfromlocaldatabase`, `/utils/updateremotedatabasefromlocaldatabase`, `/song/renderMp4Preview`, `/song/publishtotelegram`) возвращают 200 и пуши/посты происходят стандартным путём.
  Зафиксировать результат в этой задаче.

**Checkpoint**: User Story 3 подтверждена — фича аддитивна, обратная совместимость не нарушена.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Документация, линт, финальная валидация

- [X] T040 [P] Обновить `docs/architecture-notes.md` — добавить запись в конец файла о PR этой фичи (Pass 34+): `### Pass 34 — 131-fix-approve-demo-render-telegram-sync` с кратким описанием (что чинит, какие файлы изменены, какие решения D-1..D-6 из research.md применены).
  Файл: `docs/architecture-notes.md`

- [X] T041 [P] Создать per-feature документ `docs/features/approve-pipeline.md` со структурой, требуемой `tools/check-feature-doc.sh` (см. AGENTS.md → «Как добавить per-feature документ для новой подсистемы»):
  разделы `## Что делает`, `## Зачем`, `## Как работает`, `## Инварианты / правила`, `## Известные ловушки`, `## Ссылки`;
  ссылки на `contracts/pipeline.md`, `research.md` decisions, `quickstart.md`;
  KDoc на корневом классе/методе — `@see docs/features/approve-pipeline.md` (для T010/T011/T012 в соответствии с изменениями, внесёнными в Phase 3).
  Файл: `docs/features/approve-pipeline.md`

- [X] T042 Запустить полную проверку Constitution Principle («CI-gate для master» из AGENTS.md + «Локально»):
  ```
  cd /home/nsa/Karaoke
  ./gradlew ktlintCheck                              # Principle I/VII: стиль кода, KDoc
  bash tools/check-kdoc-coverage.sh                  # Principle VII: 100% покрытие KDoc
  bash tools/check-feature-doc.sh docs/features/*.md # FR-009: per-feature docs на месте
  ./gradlew :karaoke-app:compileKotlin               # общая компиляция
  ```
  Все должны быть зелёными (или baseline = 0 для ktlint). Если что-то красное — починить в коде T010/T011/T012/T020, не отключать проверки.

- [ ] T043 Полная самопроверка по `quickstart.md` — выполнить все 9 сценариев S-001..S-009 на dev-машине в рамках одного окна (последовательно или выборочно, фиксируя результаты в issue/PR-описании). Зафиксировать сводный отчёт в PR-описании (или прямо в этой задаче — формат по образцу T007 из `specs/101-song-news-flag/tasks.md`).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1, T001)**: нет зависимостей — стартует сразу.
- **Foundational (Phase 2, T002)**: зависит от T001 — **CRITICAL**: блокирует US-фазы, как gate «нет миграций».
- **Phase 3 (US1, T010-T013)**: зависит от T001+T002. Внутри: T010→T011 (один файл), T012 [P] (другой файл, параллельно с T010+T011), T013 (после T011+T012).
- **Phase 4 (US2, T020-T021)**: зависит от T011+T012 (т.к. добавляется в `approve()` после US1-блоков и в `KaraokeProcessThread` после US1-блока).
- **Phase 5 (US3, T030)**: зависит от T013+T021 (запускается после live-verify обеих фаз P1).
- **Phase 6 (Polish, T040-T043)**: зависит от T030. T040 [P] и T041 [P] — параллельно, разные файлы.

### User Story Dependencies

- **User Story 1 (P1)**: стартует после Foundational (Phase 2). **Не зависит** от US2/US3.
- **User Story 2 (P1)**: стартует после Foundational (Phase 2) и T011+T012. Семантически зависит от US1 в части «`approve()` уже содержит тригер-блок US1» (порядок в файле), но функционально независим: можно было бы поменять местами блоки в `approve()`, и каждая фича работала бы сама по себе.
- **User Story 3 (P2)**: стартует после Foundational (Phase 2). Зависит только от того, что T010-T012+T020 уже в `master` (т.е. проверяется «как есть»).

### Within Each User Story

- Реализация (T010→T011→T012/T013 для US1; T020→T021 для US2) **до** live-verify (T013/T021/T030).
- В Phase 3 — T010/T011 — последовательно (один файл `SongEditorController.kt`); T012 [P] — параллельно (другой файл `KaraokeProcessWorker.kt`).
- В Phase 6 — T040 [P] и T041 [P] — параллельно (разные файлы).

### Parallel Opportunities

| Фаза | Параллельная пара |
|---|---|
| Phase 3 | T012 **параллельно** с T010+T011 (T012 в `KaraokeProcessWorker.kt` не зависит от helper'а в `SongEditorController.kt`) |
| Phase 6 | T040 **параллельно** с T041 (разные файлы) |

Прочие пары запрещены как параллельные — все они редактируют один и тот же файл `SongEditorController.kt` (T010/T011/T020).

### Implementation Strategy

**MVP (задачи — User Story 1 + User Story 2)**:
1. T001 → T002 (1-2 мин, обзор).
2. T010 → T011 + T012 [P] (1-2 часа кода).
3. T020 (30 мин).
4. T013 + T021 + T030 — живая проверка на dev-pc (1-2 дня с учётом времени рендера).
5. **STOP и VALIDATE**: PR в master → CI gate (см. AGENTS.md → «CI-gate для master») → merge.

**Полная поставка** = MVP + Phase 6 (T040-T043) ≈ ещё 1-2 часа.

**Параллельная команда** (если есть):
- Разработчик A: T010 → T011 + T013 (US1 implementation+verify).
- Разработчик B: T012 [P] + T020 (US1 post-hook + US2 implementation).
- Разработчик C: T040 [P] + T041 [P] (документация — после B).
- Все сходятся на T042 + T043.

---

## Notes

- [P] tasks = разные файлы, нет зависимостей.
- [Story] метки: US1 → задачи рендера+Telegram; US2 → sync-related; US3 → backward-compat verify.
- Каждая user story тестируется **независимо** через свой сценарий в `quickstart.md`.
- Перед merge — обязательно пройти CI gate (см. AGENTS.md), это NON-NEGOTIABLE для `master`.
- Фича намеренно НЕ создаёт: новых сервисов, контроллеров, DTO, миграций, recordhash-триггеров, новых HTTP-эндпоинтов, новых публичных топиков SSE. Все правки — аддитивные, в рамках существующих файлов и существующих контрактов.
- Если в процессе работы потребуется отклониться от плана (например, обнаружится новая сигнатура сервиса или новая зависимость) — обновить `research.md` и/или `plan.md` в той же ветке, **до** PR.

## Прогресс выполнения (на момент завершения `/speckit.implement` 2026-08-04)

- **Завершено в этой сессии (9 задач, помечены `[X]` выше)**: T001 (baseline review через codegraph_explore), T002 (foundational gate — 5/5 проверок passed), T010 (helper `triggerRenderMp4DemoIfNeeded`), T011 (wire в `approve()`), T012 (пост-хук в `KaraokeProcessThread.run()`), T020 (sync-related `thread {}`), T040 (Pass 38 в `docs/architecture-notes.md`), T041 (`docs/features/approve-pipeline.md` создан), T042 (Constitution check 4/4 passed).
- **Deferred to dev-pc session (4 задачи, остаются `[ ]`)**:
  - T013 (живая проверка US1, S-001/S-002/S-005/S-007/S-008/S-009) — требует рестарта `karaoke-app`, разрешено только на `dev-pc`/`dev` (см. AGENTS.md → «Запрещено» п.1).
  - T021 (живая проверка US2, S-001 под-пункты 8-9, S-006) — та же причина.
  - T030 (живая проверка US3, S-003/S-004/S-008) — та же причина.
  - T043 (полная самопроверка по `quickstart.md` все 9 сценариев) — та же причина.

  Все 4 deferred-задачи должны быть помечены `[X]` в рамках dev-pc-сессии **после** `bash deploy/do.sh build_app && do.sh start`. Формат фиксации — по образцу T007 из `specs/101-song-news-flag/tasks.md` (логи approve, состояние `tbl_processes`/`tbl_news`/`tbl_songs.id_telegram_demo`).

## Реализация-2026-08-04: сводка правок

**Изменены файлы (4):**
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt` — +4 импорта, +KDoc на класс, +2 блока в `approve()` (T011+T020), +helper `triggerRenderMp4DemoIfNeeded` (T010).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt` — +2 импорта (`TelegramAutoPublishService`, `kotlin.concurrent.thread`), +KDoc на `@see docs/features/approve-pipeline.md` в class-level комментарии, +пост-хук для `RENDER_MP4_DEMO+DONE` после `HealthReport.onRepairProcessFinished` (T012).
- `docs/architecture-notes.md` — добавлен **Pass 38: пайплайн approve → DEMO-рендер → Telegram → sync related → новость «в коллекции»** в конец файла (T040).
- `docs/features/README.md` — добавлена строка #19 `approve-pipeline` в таблицу 12 ключевых подсистем.

**Создан файл (1):**
- `docs/features/approve-pipeline.md` — per-feature документ, 6 секций (Что / Зачем / Как / Инварианты / Ловушки / Ссылки), `Status: active`, `Feature Key: approve-pipeline` (T041). Включён в 16-ю строку таблицы `docs/features/README.md`.

**Локальные проверки прошли (T042):**
- `bash tools/check-feature-doc.sh docs/features/*.md` — OK, 19/19 документов валидны.
- `bash tools/check-kdoc-coverage.sh` — 96.8% (430/444), exit 0, выше FR-006 (≥50%).
- `./gradlew :karaoke-app:ktlintCheck` — BUILD SUCCESSFUL.
- `./gradlew :karaoke-web:ktlintCheck` — BUILD SUCCESSFUL.
- `./gradlew :karaoke-app:compileKotlin --rerun-tasks` — BUILD SUCCESSFUL, 0 warnings.
- `./gradlew :karaoke-web:compileKotlin --rerun-tasks` — BUILD SUCCESSFUL, 0 warnings.
- `./gradlew clean :karaoke-app:compileKotlin :karaoke-web:compileKotlin --no-daemon` — BUILD SUCCESSFUL.

