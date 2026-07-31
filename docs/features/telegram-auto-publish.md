# Автопубликация в Telegram-канал

> **Status**: active (Фаза 1 — отлов ссылки; Фаза 2 — постинг демо-MP4 по расписанию)
> **Feature Key**: telegram-auto-publish
> **Last Updated**: 2026-07-31 (Фаза 2)

## Что делает

**Фаза 1** — фоновый демон-поток ловит вышедший `channel_post` Telegram-канала
через long-polling `getUpdates` и автоматически записывает `message_id`
в `Song` (песню). Администратор по-прежнему создаёт пост вручную.

**Фаза 2** — бот САМ публикует демо-MP4 в Telegram-канал по наступлению
даты/времени публикации песни (`Settings.date`+`Settings.time`,
`Song.dateTimePublish`). Без ручных действий в Telegram-UI. `message_id`
записывается в `Settings.idTelegramDemo`. Кнопка «Опубликовать сейчас» в
`webvue3` триггерит тот же путь вручную (например, для «опоздавших» публикаций).

## Зачем

Раньше пользователь вручную создавал отложенный пост в Telegram (метка
`-` в `id_telegram_*`) и вручную вставлял ссылку после выхода. Теперь
это автоматизировано: бот сам ловит вышедший пост и привязывает
`message_id` к правильной песне/версии.

## Как работает (кратко)

1. **Запуск** — авто-старт на `ApplicationReadyEvent`, флаг
   `KaraokeProperties.telegramPollingEnabled`. Паттерн — как
   `KaraokeProcessWorker`, но не блокирует HTTP/event-поток.
2. **Long-polling** — `TelegramApiClient.getUpdates()` с таймаутом
   ~30 секунд, фон-цикл.
3. **Парсинг поста** — `Song.parseTelegramPostSongId` /
   `parseTelegramPostSongVersion` (companion `Song.kt`):
   - Пост уже содержит `linkSM` (`https://sm-karaoke.ru/song?id=<id>`).
   - Явный разделитель версии.
4. **Сохранение** — штатный `Song.saveToDb()` пишет `message_id`
   в `id_telegram_<version>`.
5. **Прокси-фолбэк** — `TelegramApiClient` авто-fallback:
   - Сначала напрямую.
   - При ошибке — через HTTP-прокси (`telegramProxyUrl`).
   - Периодическая попытка вернуться на прямой путь.
   - Прокси — отдельный docker-сервис `karaoke-telegram-proxy`
     (`deploy/docker-compose-telegram-proxy.yml`).
6. **Конфиг прокси** — `/sm-karaoke/system/telegram-proxy/config.json`
   (реальный VLESS вне git).

## Инварианты / правила

- **MUST**: `telegramPollingEnabled` проверяется в `telegramPollingEnabled`-флага —
  без него цикл не стартует.
- **MUST**: при ошибке прокси — НЕ отключаем polling, только
  переключаемся на direct (через заданный таймаут).
- **MUST**: `message_id` сохраняется через `Song.saveToDb()`, не
  через прямой SQL (SSE-уведомления).
- **SHOULD**: rate-limit на `getUpdates` — Telegram рекомендует не чаще
  раз в несколько секунд. Сейчас — long-poll, что OK.

## Известные ловушки

- **Telegram 403 из Docker**: на прод-сервере (Германия) Telegram
  доступен. На admin-машине через VPN — может быть заблокирован. Прокси
  решает проблему.
- **Множественные инстансы**: если запустить `karaoke-app` дважды —
  оба будут long-polling'ить, конфликт на стороне Telegram. Используйте
  `--no-telegram-polling` для второго инстанса.
- **Auth token rotation**: при смене `telegramBotToken` нужно
  перезапустить `karaoke-app`. Polling не подхватывает новый токен
  на лету.

## Фаза 2 — автопубликация демо-MP4 по расписанию

> Спецификация: `specs/113-telegram-demo-publish/`. Реализация: ветка
> `114-telegram-demo-publish-impl`.

### Что делает

Бот автоматически публикует демо-версию песни (MP4, `RenderVersion.DEMO`)
в Telegram-канал, когда наступает её дата/время публикации. Идемпотентен
(FR-008: `idTelegramDemo != ''` → skip). Прошедшая дата — «опоздавшая»
(Q1 clarify): бот не публикует, админ должен переставить дату/время или
нажать «Опубликовать сейчас».

### Как работает

1. **Scheduler** — `TelegramAutoPublishScheduler` (`@Scheduled fixedDelay`
   ~60с) на каждом тике:
   - Проверяет `telegramAutoPublishEnabled` (FR-013).
   - Cheap SELECT кандидатов (только `id`+`publish_date`+`publish_time`,
     без текстов/маркеров) с фильтром `id_telegram_demo = ''` (FR-008).
   - Фильтр по скользящему окну `[now - window, now]` (Q1: «5-10 минут»,
     `telegramAutoPublishWindowMinutes`).
   - Для прошедших фильтр — загружает полный `Song` и вызывает
     `TelegramAutoPublishService.publishToTelegram(allowPastDate=false)`.
2. **Service** — `TelegramAutoPublishService.publishToTelegram`:
   - FR-008: если `idTelegramDemo` уже заполнен → `PUBLISHED` без действий.
   - Q1: прошедшая date/time → `SCHEDULED` («опоздавшая»), без действий
     (кроме ручного триггера `allowPastDate=true`).
   - FR-011: `isContentReady` (статус ≥6 + stems + pictures + markers).
   - FR-003: есть ли готовый демо-MP4 ≤ `telegramAutoPublishMaxFileSizeMb`?
     - Да → `PUBLISHING`, `sendVideo` с retry (FR-010).
     - Нет / превышает → `RENDERING`, ставит `KaraokeProcess` задачу
       `RENDER_MP4_DEMO` (дефолты 1280/720/30), публикация продолжится
       через `onRenderCompleted` после завершения рендера.
   - FR-006: по успеху `sendVideo` → `message_id` записывается в
     `Settings.idTelegramDemo` через штатный `Song.saveToDb()` (SSE + sync).
   - FR-010: по исчерпанию ретраев → `SEND_FAILED` + `lastError`.
3. **Resume RENDERING** — scheduler на каждом тике проверяет песни в
   состоянии `RENDERING` (из `player_readiness_flags`): если их
   `RENDER_MP4_DEMO` задача завершилась (DONE/ERROR), вызывает
   `TelegramAutoPublishService.onRenderCompleted` для продолжения.
   Это заменяет прямой callback из `KaraokeProcessWorker` (worker не
   знает про TelegramAutoPublishService) — scheduler единая точка
   orchestration.
4. **Ручной триггер** — `POST /api/song/publishToTelegramNow?songId=<id>`
   (кнопка «Опубликовать сейчас» в `SongEdit.vue`). Тот же путь, что
   scheduler, но `allowPastDate=true` (публикует даже «опоздавшую»).
   Доступен всегда, даже при `telegramAutoPublishEnabled=false`.
5. **Retry** — `TelegramApiClient.sendVideo`: до 3 попыток с backoff
   30с / 2м / 5м (FR-010). Non-retryable коды (400/403/404) — сразу
   `SEND_FAILED`. Каждая попытка использует прокси-fallback из
   существующего `send()`.

### Состояние публикации

6 значений (`TelegramAutoPublishState`), хранятся в JSON-блобе
`player_readiness_flags` (паттерн `specs/101-song-news-flag` — без новой
колонки, без правки recordhash-триггера):

| State | code | Когда |
|-------|------|-------|
| SCHEDULED | `scheduled` | date/time в будущем, бот ещё не начинал |
| RENDERING | `rendering` | бот рендерит демо-MP4 (FR-003 сц. 2/3) |
| PUBLISHING | `publishing` | демо-MP4 готов, идёт sendVideo (+ретраи) |
| PUBLISHED | `published` | `idTelegramDemo` заполнен (FR-006) |
| SEND_FAILED | `send_failed` | все ретраи FR-010 исчерпаны |
| CANCELLED | `cancelled` | админ очистил date/time |

Производное значение для UI/логики — `Song.effectiveTelegramAutoPublishState`:
`PUBLISHED` определяется по заполненному `idTelegramDemo` (FR-008), а не по
полю state — чтобы любая попытка записи (Фаза 2 или Фаза 1 через
`TelegramUpdatesConsumer`) согласованно отражалась в UI.

### Инварианты / правила (Фаза 2)

- **MUST**: `telegramAutoPublishEnabled=false` (по умолчанию) — scheduler
  no-op. Endpoint `/api/song/publishToTelegramNow` работает независимо.
- **MUST**: идемпотентность — `idTelegramDemo != ''` → skip (FR-008).
- **MUST**: прошедшая date/time — skip для scheduler (Q1). Ручной триггер
  игнорирует это ограничение.
- **MUST**: `message_id` записывается через штатный `Song.saveToDb()`, не
  через raw SQL (SSE + recordhash-diff LOCAL↔SERVER).
- **MUST**: состояние публикации — в `player_readiness_flags` JSON, не
  новая колонка (Principle II/III, паттерн `specs/101-song-news-flag`).
- **MUST**: `TelegramUpdatesConsumer` (Фаза 1) не модифицируется (FR-009) —
  ручные посты продолжают парситься.
- **SHOULD**: кандидатов за тик обычно 0–5; параллелизм не нужен
  (per-chat rate limit Telegram).

### Известные ловушки (Фаза 2)

- **Контракт `adminKey` не реализован**: в проекте `/api/**` — `permitAll`
  (SecurityConfig), `adminKey` нигде не используется. Endpoint
  `/api/song/publishToTelegramNow` следует паттерну `renderMp4Preview` —
  без `adminKey`. Это отклонение от контракта `specs/113-telegram-demo-publish/`,
  но согласуется с реальными паттернами проекта.
- **`fixedDelayString` с SpEL не работает**: `KaraokeProperties` хранит
  значения в собственном base64-файле, а не в Spring `application.properties`.
  Scheduler использует константный `fixedDelay=60_000L` и читает window из
  `KaraokeProperties` внутри `tick()` (как `SongReleaseAnnouncementScheduler`).
- **Демо >50 МБ после перерендера**: если даже перерендер с дефолтами
  (1280/720/30) не укладывается в лимит — `SEND_FAILED`. Админ должен
  уменьшить `demoFragmentBounds` или увеличить `telegramAutoPublishMaxFileSizeMb`.
- **Callback из worker не внедряется**: `KaraokeProcessWorker` не знает про
  `TelegramAutoPublishService`. Resume RENDERING делает scheduler на следующем
  тике (дешёвая проверка статуса `RENDER_MP4_DEMO` задачи). Это задержка до ~60с
  после завершения рендера — приемлемо для автопубликации.
- **`playerReadinessFlagsMap` — `Map<String, Boolean>`**: string-ключи
  состояния публикации парсятся через отдельный `JsonObject` helper
  (`readinessStringFlag`), не ломая существующие boolean readiness-флаги.

### Ссылки на ключевые классы/файлы (Фаза 2)

- [`TelegramAutoPublishState.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishState.kt) — enum 6 состояний
- [`TelegramAutoPublishResult.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishResult.kt) — результат одного цикла
- [`TelegramAutoPublishService.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishService.kt) — бизнес-логика
- [`TelegramAutoPublishScheduler.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishScheduler.kt) — плановый тик
- [`TelegramAutoPublishSchedulerStarter.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishSchedulerStarter.kt) — старт-лог
- [`TelegramApiClient.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramApiClient.kt) — `sendVideo` с retry
- [`Song.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt) — `effectiveTelegramAutoPublishState`, state accessors
- [`ApiController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt) — `POST /api/song/publishToTelegramNow`
- [`SongEdit.vue`](../../webvue3/src/components/Songs/edit/SongEdit.vue) — кнопка «Опубликовать сейчас» + state badge
- [`SongsTable.vue`](../../webvue3/src/components/Songs/SongsTable.vue) — TG-publish badge
- [`KaraokeProperties.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt) — 4 новых ключа (`telegramAutoPublish*`)

## Ссылки на ключевые классы/файлы

- [`TelegramApiClient.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramApiClient.kt) — клиент Telegram Bot API
- [`TelegramUpdatesConsumer.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramUpdatesConsumer.kt) — long-polling
- [`Song.parseTelegramPostSongId`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt) — парсинг поста
- [`KaraokeAppApplication.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeAppApplication.kt) — `@EventListener(ApplicationReadyEvent)`
- [`deploy/docker-compose-telegram-proxy.yml`](../../deploy/docker-compose-telegram-proxy.yml) — прокси-сервис
