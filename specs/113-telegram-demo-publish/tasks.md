---
description: "Task list for Автопубликация демо-версий песен в Telegram-канал по расписанию"
---

# Tasks: Автопубликация демо-версий песен в Telegram-канал по расписанию

**Input**: Design documents from `/specs/113-telegram-demo-publish/`
- [plan.md](./plan.md) (required) — Technical Context, Constitution Check, Project Structure
- [spec.md](./spec.md) (required) — 3 User Stories (P1, P2, P2) + 16 FR + 8 SC
- [data-model.md](./data-model.md) — `TelegramAutoPublishState` enum + JSON-блоб `player_readiness_flags`
- [contracts/telegram-auto-publish.md](./contracts/telegram-auto-publish.md) — 5 контрактов
- [research.md](./research.md) — 8 Phase 0 решений
- [quickstart.md](./quickstart.md) — 11 ручных сценариев проверки

**Tests**: в CI тестов для этого модуля нет (Constitution, «Рабочий
процесс» → «Тесты»); проверка — вручную по `quickstart.md`.
Существующие `@Disabled`-тесты в `karaoke-app/src/test` не покрывают
ни Фазу 1, ни Фазу 2.

**Organization**: Tasks сгруппированы по user story. Каждая story
может быть реализована и протестирована независимо (после завершения
Phase 1 + Phase 2).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно запустить параллельно (разные файлы, нет
  зависимостей)
- **[Story]**: к какой user story относится (US1, US2, US3)
- Включать точные пути файлов в описания

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Минимальная инфраструктура, общая для всех историй —
конфигурация Telegram-параметров и базовые перечисления/типы
данных.

- [ ] T001 Добавить 4 новых ключа в `KaraokeProperties.kt` —
  `telegramAutoPublishEnabled` (Boolean, default `false`),
  `telegramAutoPublishChannelId` (String, default `""`),
  `telegramAutoPublishWindowMinutes` (Long, default `5`),
  `telegramAutoPublishMaxFileSizeMb` (Long, default `50`).
  Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`
  (раздел с `telegramBotToken`/`telegramPollingEnabled`).
- [ ] T002 [P] Создать enum `TelegramAutoPublishState` (6 значений:
  `SCHEDULED`, `RENDERING`, `PUBLISHING`, `PUBLISHED`, `SEND_FAILED`,
  `CANCELLED`) с компаньоном `fromCode(code: String?)` в новом
  файле `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishState.kt`.
  KDoc с `@see docs/features/telegram-auto-publish.md`.
- [ ] T003 [P] Создать data class `TelegramAutoPublishResult`
  (поля: `state: TelegramAutoPublishState`,
  `messageId: String? = null`, `error: String? = null`) в новом
  файле `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishResult.kt`.
  KDoc с `@see docs/features/telegram-auto-publish.md`.

**Checkpoint**: Setup завершён — можно приступать к Foundational.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core-инфраструктура, которая ДОЛЖНА быть готова до
начала любой user story: расширение `TelegramApiClient.sendVideo`
(retry-обёртка с прокси-fallback'ом) и state-аксессоры
в `Song.kt` (для хранения `telegramAutoPublishState` в уже
существующем JSON-блобе `player_readiness_flags`).

**⚠️ CRITICAL**: Никакая user story не может стартовать до
завершения этой фазы.

- [ ] T004 Расширить `TelegramApiClient.kt` — добавить
  `fun sendVideo(channelId: String, videoFile: File, caption:
  String, maxFileSizeBytes: Long, maxAttempts: Int = 3,
  backoffScheduleMs: List<Long> = listOf(30_000L, 120_000L, 300_000L)):
  TelegramAutoPublishResult` (вызывает `POST /bot{token}/sendVideo`
  multipart, парсит JSON-ответ, реализует retry-цикл per FR-010,
  переиспользует существующий прокси-fallback из `send(request)`,
  на каждой попытке перед отправкой проверяет
  `videoFile.length() <= maxFileSizeBytes`). Возвращает
  `TelegramAutoPublishResult(state=PUBLISHED, messageId=...)` при
  успехе или `TelegramAutoPublishResult(state=SEND_FAILED,
  error=...)` при исчерпании ретраев. Файл:
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramApiClient.kt`.
  KDoc с `@see docs/features/telegram-auto-publish.md`.
- [ ] T005 [P] Добавить в `Song.kt` Kotlin-property accessors
  для новых ключей JSON-блоба `player_readiness_flags`:
  `telegramAutoPublishState: String get/set` (через
  `readinessFlag("telegramAutoPublishState")` / `setReadinessFlag(...)`),
  `telegramAutoPublishLastAttemptAt: String get/set` (ISO-8601
  timestamp), `telegramAutoPublishLastError: String get/set`.
  Использовать тот же приватный helper, что уже используется
  для `newsAvailableAnnounced` и других readiness-флагов (см.
  `specs/101-song-news-flag/data-model.md`). Файл:
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`
  (рядом с уже существующими `idTelegramDemo` getter'ами, ~строка 1031).
- [ ] T006 [P] Добавить в `Song.kt` derived property
  `effectiveTelegramAutoPublishState: TelegramAutoPublishState`
  (см. точный псевдокод в
  `data-model.md` — секция «Производное правило чтения»):
  `if (idTelegramDemo.isNotEmpty()) → PUBLISHED`;
  `if (state == "cancelled") → CANCELLED`;
  `if (dateTimePublish == null || dateTimePublish < Date())
  → SCHEDULED` (с пометкой «опоздавшая» в UI);
  иначе `TelegramAutoPublishState.fromCode(state) ?: SCHEDULED`.
  Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`.

**Checkpoint**: Foundation готова — можно стартовать user stories.

---

## Phase 3: User Story 1 - Бот сам публикует демо-версию песни в Telegram по расписанию (Priority: P1) 🎯 MVP

**Goal**: Администратор указывает у песни дату/время публикации;
после наступления этой даты/времени бот автоматически
отправляет в Telegram-канал демо-MP4 с подписью, без ручных
действий в Telegram-UI. `message_id` записывается в
`Settings.idTelegramDemo`. Повторные срабатывания —
no-op (idempotent). «Опоздавшая» дата/время — skip.
Кнопка «Опубликовать сейчас» в `webvue3` для ручного
триггера того же пути.

**Independent Test** (quickstart.md, Steps 1–7 + 9):
- Довести тестовую песню до полной готовности;
- Установить `date`/`time` на 5 минут вперёд;
- Дождаться тика (5–10 минут) — пост с демо-MP4 появился
  в Telegram-канале;
- `Settings.idTelegramDemo` заполнен;
- Повторный тик — повторного поста НЕТ;
- Прошлая `date`/`time` — пост НЕ создаётся;
- Кнопка «Опубликовать сейчас» в `webvue3` — пост создаётся
  даже для прошлой `date`/`time`;
- Кнопка скрыта для уже опубликованной.

### Implementation for User Story 1

- [ ] T007 Создать `TelegramAutoPublishService.kt` —
  бизнес-логика публикации. Метод
  `fun publishToTelegram(song: Song): TelegramAutoPublishResult`,
  который:
  1. Проверяет `song.idTelegramDemo.isNotEmpty()` (FR-008) →
     возврат `Result(state=PUBLISHED, messageId=song.idTelegramDemo)`
     без действий;
  2. Проверяет `song.dateTimePublish` против `Date()` (FR-001,
     Q1 clarify) → если в прошлом, возврат
     `Result(state=SCHEDULED, error="dateTimePublish < now() —
     'опоздавшая'")` без действий;
  3. Проверяет `song.isContentReady` (FR-011) → если нет,
     возврат `Result(state=SCHEDULED, error="not content-ready:
     <missing flags>")` без действий;
  4. Проверяет наличие `demo.mp4` для песни; если есть и
     `file.length() <= telegramAutoPublishMaxFileSizeMb * 1024 * 1024`
     → использует файл; иначе (нет файла или превышает лимит)
     ставит `KaraokeProcess` задачу `RENDER_MP4_DEMO` (с
     уменьшенными параметрами при превышении лимита, см. FR-003
     сценарии 2/3), пишет `state=RENDERING` в `song` через
     `saveToDb()`, возвращает `Result(state=RENDERING)` (синхронный
     ответ — публикация продолжится асинхронно через
     `onRenderCompleted` после завершения `KaraokeProcess`);
  5. Если файл готов → пишет `state=PUBLISHING` через
     `saveToDb()`, вызывает `telegramApiClient.sendVideo(...)`;
  6. По успеху → пишет `state=PUBLISHED`, `idTelegramDemo=<messageId>`
     через `saveToDb()` (FR-006), возвращает
     `Result(state=PUBLISHED, messageId=...)`;
  7. По исчерпанию ретраев → пишет `state=SEND_FAILED` +
     `lastError=<text>` через `saveToDb()` (FR-010),
     возвращает `Result(state=SEND_FAILED, error=...)`.
  Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishService.kt`.
  KDoc с `@see docs/features/telegram-auto-publish.md`.
- [ ] T008 [P] Создать `TelegramAutoPublishService.onRenderCompleted(songId:
  Long, success: Boolean, error: String?)` —
  callback, вызываемый из `KaraokeProcessWorker` при завершении
  `RENDER_MP4_DEMO`. Если `success=true` — загружает `Song`,
  вызывает тот же код из шагов 4–7 метода `publishToTelegram`
  (файл уже отрендерен, проверяем лимит 50 МБ, идём по
  шагам 5–7). Если `success=false` — пишет
  `state=SEND_FAILED`, `lastError="render failed: <error>"`.
  Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishService.kt`
  (тот же файл, что T007).
- [ ] T009 Создать `TelegramAutoPublishScheduler.kt` —
  `@Service` с методом
  `@Scheduled(fixedDelayString = "PT${window}M")` (где
  `window` = `KaraokeProperties.getLong("telegramAutoPublishWindowMinutes")`).
  Метод `tick()`:
  1. `if (!KaraokeProperties.getBoolean("telegramAutoPublishEnabled")) return`;
  2. Выполняет дешёвый raw-JDBC `SELECT id, date, time,
     id_status, id_telegram_demo FROM tbl_settings
     WHERE date IS NOT NULL AND time IS NOT NULL
     AND (id_telegram_demo IS NULL OR id_telegram_demo = '')`
     — без `loadListFromDb` (без base64/маркеров/картинок);
  3. Для каждой строки: парсит `dateTimePublish` в Kotlin;
     фильтрует `dateTimePublish in [now - window, now()]`
     (Q1 spec.md: «5–10 минут»);
  4. Для каждой строки, прошедшей фильтр, загружает
     `Song.loadFromDb(id)` и вызывает
     `telegramAutoPublishService.publishToTelegram(song)`.
  Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishScheduler.kt`.
  KDoc с `@see docs/features/telegram-auto-publish.md`.
- [ ] T010 [P] Создать `TelegramAutoPublishSchedulerStarter.kt` —
  `@Component` с `@EventListener(ApplicationReadyEvent)`
  (по образцу `TelegramUpdatesConsumerStarter`):
  если `KaraokeProperties.getBoolean("telegramAutoPublishEnabled")` —
  стартует scheduler (для `fixedDelay` это вырожденная операция
  в Spring, но для согласованности с `TelegramUpdatesConsumer`
  добавляем явный `println("TelegramAutoPublishScheduler: старт")`).
  Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/scheduler/TelegramAutoPublishSchedulerStarter.kt`.
  KDoc с `@see docs/features/telegram-auto-publish.md`.
- [ ] T011 [P] Добавить endpoint `POST /api/song/publishToTelegramNow`
  в `ApiController.kt`:
  - `@RequestParam songId: Long` (обязательный);
  - `@RequestParam adminKey: String?` (обязательный, проверяется
    как в других admin-эндпоинтах — см. `MainController.kt`,
    `/api/private/**`);
  - Загружает `Song`, проверяет `idTelegramDemo == ''` (FR-016,
    иначе `400` с `error = "Song <id> is already published (...)"`);
  - Вызывает `telegramAutoPublishService.publishToTelegram(song)`;
  - Возвращает JSON `{ success, state, messageId, error }`
    (контракт см. `contracts/telegram-auto-publish.md`, раздел 1).
  Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`.
  KDoc на endpoint с `@see docs/features/telegram-auto-publish.md`.
- [ ] T012 [P] [US1] Добавить кнопку «Опубликовать сейчас» в
  `SongEdit.vue`:
  - Кнопка видна **только** если `settings.idTelegramDemo === ''`
    (FR-016, через `v-if` или `disabled` binding);
  - По клику — `POST /api/song/publishToTelegramNow?songId=...
    &adminKey=<adminKey>` (adminKey берётся из
    `localStorage` или Vuex, как у других admin-эндпоинтов);
  - На `success=true` — перезагрузить карточку (SSE-обновление
    `settings` придёт само);
  - На `success=false` — показать тост с `error`;
  - На `state in ["rendering", "publishing"]` — disabled
    («Публикация в процессе…»);
  - На `state == "published"` — кнопка скрыта.
  Файл: `webvue3/src/components/Songs/edit/SongEdit.vue`.
  JSDoc на новый метод с `@see docs/features/telegram-auto-publish.md`.

**Checkpoint**: User Story 1 полностью функциональна и независимо
тестируема. Это и есть **MVP**.

---

## Phase 4: User Story 2 - Существующая ручная публикация по-прежнему понимается ботом (Priority: P2)

**Goal**: Если администратор вручную опубликовал пост в Telegram
(в обход бота Фазы 2), `TelegramUpdatesConsumer` (Фаза 1) ловит
его через long-polling и привязывает `message_id` к песне.
Эта логика не должна сломаться после введения Фазы 2.

**Independent Test** (quickstart.md, Step 8):
- Вручную опубликовать в Telegram-канале пост с корректной
  ссылкой `https://sm-karaoke.ru/song?id=<id>` для песни;
- `TelegramUpdatesConsumer` поймал пост через long-polling;
- `Settings.idTelegram<Version>` заполнен тем же `message_id`,
  что и до введения Фазы 2.

### Verification for User Story 2 (без нового кода)

> **Важно**: User Story 2 НЕ требует нового кода — это требование
> «не сломать существующее поведение». Проверка — через
> quickstart.md Step 8.

- [ ] T013 [P] [US2] Убедиться, что `TelegramUpdatesConsumer.kt`
  **НЕ модифицирован** (diff против `master` показывает 0
  изменений). Файл:
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramUpdatesConsumer.kt`.
  Если файл был случайно изменён — откатить (это нарушение
  FR-009 spec.md).
- [ ] T014 [P] [US2] Убедиться, что `Song.parseTelegramPostSongId`
  и `Song.parseTelegramPostSongVersion` **НЕ модифицированы**
  (та же проверка через diff). Файл:
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`
  (только секции с этими функциями — другие изменения в Song.kt
  для Фазы 2 (T005/T006) допустимы).

**Checkpoint**: User Stories 1 И 2 обе работают независимо.

---

## Phase 5: User Story 3 - Состояние «запланирована / опубликована» и связанные метаданные доступны администратору (Priority: P2)

**Goal**: Администратор в любой момент может посмотреть, для каких
песен заполнена дата/время публикации, какие из них уже отправлены
в Telegram-канал ботом, какие ещё нет, и при необходимости —
очистить дату/время публикации конкретной песни из админки
`webvue3`, не заходя в Telegram.

**Independent Test** (quickstart.md, Steps 1, 2, 3, 6, 11):
- В таблице песен (`SongsTable.vue`) виден badge с состоянием
  публикации для песен с заполненной `date`/`time`;
- В карточке песни (`SongEdit.vue`) — блок «Telegram-публикация»
  с текущим `state`, `lastAttemptAt`, `lastError` (если есть);
- Очистка `date`/`time` через UI переводит в `SCHEDULED`
  (или `CANCELLED` при явной отмене).

### Implementation for User Story 3

- [ ] T015 [P] [US3] Добавить badge-индикатор состояния
  Telegram-публикации в `SongsTable.vue`:
  - Новый столбец (или inline badge в существующем) для песен,
    у которых `id_telegram_demo` заполнен ИЛИ `date`/`time` заполнены;
  - Цвета/иконки по 6 значениям `TelegramAutoPublishState`
    (по образцу существующих badge в `NewsTable.vue` для статусов
    новостей — `badge bg-success/bg-warning/bg-danger/bg-secondary`).
  Файл: `webvue3/src/components/Songs/SongsTable.vue`.
- [ ] T016 [P] [US3] Добавить блок «Telegram-публикация» в
  `SongEdit.vue` (рядом с существующим блоком «Telegram
  посты»):
  - `state` (локализованный лейбл: «Запланирована» /
    «Рендерится» / «Публикуется» / «Опубликована» /
    «Ошибка отправки» / «Отменена»);
  - `lastAttemptAt` (если непусто — «Последняя попытка:
    <время>»);
  - `lastError` (если непусто — текст ошибки в `<details>` для
    компактности);
  - Для `state == "send_failed"` — ссылка «Повторить»,
    вызывающая тот же endpoint `/api/song/publishToTelegramNow`
    (то же, что и кнопка «Опубликовать сейчас» в Phase 3 T012).
  Файл: `webvue3/src/components/Songs/edit/SongEdit.vue`.

**Checkpoint**: User Stories 1, 2 И 3 все работают независимо.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Улучшения, затрагивающие несколько user stories,
соответствие Constitution Principle VI (per-feature документ +
KDoc), прогон quickstart-сценариев.

- [ ] T017 Обновить `docs/features/telegram-auto-publish.md` —
  добавить секцию «Фаза 2 — автопубликация» (с тем же форматом,
  что уже есть для Фазы 1: «Как работает», «Инварианты / правила»,
  «Известные ловушки», «Ссылки на ключевые классы/файлы»).
  Включить: scheduler + service, `POST /api/song/publishToTelegramNow`,
  6 состояний публикации, retry-политику, переиспользование
  `KaraokeProcess*` (RENDER_MP4_DEMO). Файл:
  `docs/features/telegram-auto-publish.md`.
- [ ] T018 Добавить `@see docs/features/telegram-auto-publish.md`
  в KDoc всех новых/изменённых классов:
  `TelegramAutoPublishState.kt`, `TelegramAutoPublishResult.kt`,
  `TelegramAutoPublishService.kt`, `TelegramAutoPublishScheduler.kt`,
  `TelegramAutoPublishSchedulerStarter.kt`, `TelegramApiClient.kt`
  (на `sendVideo`), `Song.kt` (на `effectiveTelegramAutoPublishState`),
  `ApiController.kt` (на endpoint), `SongEdit.vue`
  (JSDoc на новый метод).
- [ ] T019 Запустить `./gradlew ktlintCheck` (в корне репозитория) —
  все новые/изменённые Kotlin-файлы из T004–T011 (KaraokeProperties.kt,
  TelegramApiClient.kt, Song.kt, TelegramAutoPublishState.kt,
  TelegramAutoPublishResult.kt, TelegramAutoPublishService.kt,
  TelegramAutoPublishScheduler.kt, TelegramAutoPublishSchedulerStarter.kt,
  ApiController.kt) проходят `ktlint` (или в
  `config/ktlint/baseline-*.xml` нет новых нарушений).
  Если нарушения — исправить в изменённых файлах.
- [ ] T020 Запустить `cd webvue3 && npm run lint:check` —
  все новые/изменённые Vue/JS-файлы проходят `eslint` (или в
  `webvue3/.eslint-baseline.json` нет новых нарушений).
  Если нарушения — исправить.
- [ ] T021 Запустить `bash tools/check-kdoc-coverage.sh` —
  должно вернуть 100% (новые Kotlin-классы с KDoc, см. T018);
  если меньше — добавить KDoc.
- [ ] T022 Запустить `bash tools/check-jsdoc-coverage.sh webvue3` —
  должно вернуть 100% (новые Vue/JS-методы с JSDoc, см. T018);
  если меньше — добавить JSDoc.
- [ ] T023 Прогнать quickstart.md сценарии Step 1–11 вручную на
  prod-like окружении (admin-машина или LOCAL docker-стек, по
  согласию пользователя на каждое действие с admin-машиной).
  Каждый шаг — отдельный `git commit` для удобства review.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей — стартует сразу
- **Foundational (Phase 2)**: зависит от Phase 1 — **БЛОКИРУЕТ**
  все user stories
- **User Stories (Phase 3, 4, 5)**: зависят от Phase 2
  - User Stories могут идти параллельно (если есть ресурсы)
  - Или последовательно в порядке приоритета: P1 → P2 → P2
- **Polish (Phase 6)**: зависит от завершения нужных user stories
  (T013, T014 требуют Phase 4; T015, T016 требуют Phase 5;
  T017–T023 — от всего)

### User Story Dependencies

- **User Story 1 (P1)**: может стартовать после Phase 2 — нет
  зависимостей от других stories
- **User Story 2 (P2)**: это **проверка**, а не новый код —
  запускается параллельно с Phase 3 (T013/T014) для
  верификации, что Phase 3 не сломала Фазу 1
- **User Story 3 (P2)**: может стартовать после Phase 2 —
  использует `effectiveTelegramAutoPublishState` (T006) и
  `telegramAutoPublishState/LastAttemptAt/LastError` accessors
  (T005); UI-изменения независимы от бизнес-логики

### Within Each User Story

- Phase 3 (US1): `TelegramAutoPublishService` (T007+T008) →
  `TelegramAutoPublishScheduler` (T009) →
  `TelegramAutoPublishSchedulerStarter` (T010) →
  endpoint в `ApiController` (T011) →
  UI button в `SongEdit.vue` (T012). T007+T008 — параллельно
  (один файл, но разные методы); T009, T010, T011, T012 — каждый
  в своём файле, можно параллельно.
- Phase 4 (US2): T013, T014 — оба верификации diff'а, можно
  параллельно.
- Phase 5 (US3): T015, T016 — разные файлы, можно параллельно.

### Parallel Opportunities

- Phase 1: T001 (KaraokeProperties) — единственная задача; T002, T003
  можно параллельно (разные файлы).
- Phase 2: T005, T006 — параллельно (оба в Song.kt, но разные
  property-блоки; на практике — последовательно, чтобы не
  конфликтовать в одном файле; в quickstart сценарии не
  критично).
- Phase 3:
  - T007 + T008 — последовательно (один файл, разные методы,
    лучше одной правкой).
  - T009, T010, T011, T012 — каждый в своём файле, можно
    параллельно разными разработчиками.
- Phase 4: T013, T014 — оба read-only diff-проверки, можно
  параллельно.
- Phase 5: T015, T016 — разные файлы, можно параллельно.
- Phase 6: T019, T020, T021, T022 — разные скрипты, можно
  параллельно.

---

## Parallel Example: User Story 1

```bash
# Phase 3 — после завершения Phase 1+2:

# 1. Реализовать сервис (T007 + T008 — последовательно, один файл):
Task: "TelegramAutoPublishService.kt — publishToTelegram(song) + onRenderCompleted(...)"

# 2. Параллельно (разные файлы — 3 потока):
Task: "TelegramAutoPublishScheduler.kt — @Scheduled tick()"
Task: "TelegramAutoPublishSchedulerStarter.kt — @EventListener(ApplicationReadyEvent)"
Task: "ApiController.kt — POST /api/song/publishToTelegramNow"
Task: "SongEdit.vue — кнопка 'Опубликовать сейчас'"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (**CRITICAL** — blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Прогнать quickstart.md Steps 1, 2, 3, 4, 6, 7, 9
   (publish, idempotency, past date, render-on-demand, manual trigger,
   button hidden after publish, retry)
5. Deploy на admin-машину (по согласию пользователя)
6. Включить `telegramAutoPublishEnabled=true` через Properties UI
7. Наблюдать 1–2 эфира (например, в течение 1–2 часов)
8. Если стабильно — PR (CI 7/7), merge

### Incremental Delivery

1. Setup + Foundational → Foundation готова
2. Phase 3 (US1) → протестировать quickstart Steps 1–7, 9 → **MVP demo**
3. Phase 4 (US2) → quickstart Step 8 (verify no regression) → demo
4. Phase 5 (US3) → UI badges + блок в SongEdit → demo
5. Phase 6 (Polish) → docs, KDoc, lint, coverage, final quickstart → PR

### Parallel Team Strategy

С несколькими разработчиками (если применимо):
1. Все вместе: Setup + Foundational
2. После Phase 2:
   - Developer A: Phase 3 (T007–T012)
   - Developer B: Phase 4 (T013–T014, verification)
   - Developer C: Phase 5 (T015–T016, UI)
3. Phase 6 — все вместе

---

## Notes

- `[P]` задачи = разные файлы, нет зависимостей
- `[Story]` label связывает задачу с user story для traceability
- Каждая user story независимо завершаема и тестируема
- Тесты в CI не запускаются (Constitution → Tests); quickstart.md
  — единственный источник верификации
- Коммит после каждой задачи или логической группы
- Останавливаться на любом checkpoint для валидации story
- Избегать: расплывчатых задач, конфликтов в одном файле,
  cross-story зависимостей, ломающих независимость

---

## Task Count Summary

| Phase | Task IDs | Количество |
|-------|----------|------------|
| Phase 1: Setup | T001–T003 | 3 |
| Phase 2: Foundational | T004–T006 | 3 |
| Phase 3: User Story 1 (P1, MVP) | T007–T012 | 6 |
| Phase 4: User Story 2 (P2) | T013–T014 | 2 |
| Phase 5: User Story 3 (P2) | T015–T016 | 2 |
| Phase 6: Polish | T017–T023 | 7 |
| **ИТОГО** | T001–T023 | **23** |

- **По user story**: US1 = 6, US2 = 2, US3 = 2 (бизнес-логика);
  Setup/Foundational/Polish = 13 (общая инфраструктура)
- **Параллельных возможностей**: ~10 (см. секцию «Parallel
  Opportunities»)
- **MVP**: Phase 1 + Phase 2 + Phase 3 (T001–T012, 12 задач) =
  полностью функциональная автопубликация + ручной триггер
- **Формат-валидация**: все 23 задачи соответствуют
  чеклист-формату (`- [ ] [ID] [P?] [Story] Description with
  file path`)
