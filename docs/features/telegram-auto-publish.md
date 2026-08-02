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

## Настройка бота с нуля (BotFather → канал → Karaoke.properties)

> Если бот уже создан и настроен — пропустите этот раздел. Все ключи
> хранятся в `/sm-karaoke/system/Karaoke.properties` (admin-машина,
> base64-файл, в git НЕ лежит). Менять можно через Properties UI в
> `webvue3` (раздел Telegram) с последующим перезапуском `karaoke-app`,
> либо прямым редактированием файла.

### Шаг 1. Создать бота в @BotFather

1. В Telegram откройте [@BotFather](https://t.me/BotFather) → `/newbot`.
2. Имя (Display Name) — любое, напр. «Svoemesto Караоке Бот».
3. Username — любое, оканчивающееся на `bot`, напр. `svoemesto_karaoke_bot`.
4. BotFather ответит сообщением с **токеном** вида
   `123456789:AA-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`. Скопируйте его.
5. (Опц.) `/setdescription` — краткое описание, что бот публикует демо
   караоке-видео по расписанию.
6. (Опц.) `/setuserpic` — иконка бота (можно ту же, что у канала).

### Шаг 2. Создать Telegram-канал

Если канал `@svoemestokaraoke` уже существует — пропустите. Иначе:

1. В Telegram: «Создать канал».
2. Тип — **Публичный** (нужен username для ссылки `t.me/...`),
   напр. `@svoemestokaraoke`.
3. Имя — «Svoemesto Караоке» (или существующее).

### Шаг 3. Добавить бота администратором канала

**Обязательно для Фазы 2** (sendVideo требует прав на публикацию):

1. Откройте канал → «Управление каналом» → «Администраторы» → «Добавить».
2. Найдите бота по username (из Шага 1).
3. Дайте права: **«Публикация сообщений»** (обязательно для Фазы 2),
   «Редактирование сообщений» (опц.). Не давайте право «Удаление» —
   бот не должен удалять посты.
4. Подтвердите.

### Шаг 4. Узнать числовой chat_id канала

`chat_id` публичного канала можно получить через `getUpdates` (после
первого поста в канале) или через @userinfobot. Самый надёжный способ
для канала — через Bot API:

1. Опубликуйте ЛЮБОЙ пост в канал вручную (бот должен быть админом к
   этому моменту).
2. Откройте в браузере:
   `https://api.telegram.org/bot<ТОКЕН>/getUpdates`
   (замените `<ТОКЕН>` на токен из Шага 1).
3. В JSON-ответе найдите `result[].channel_post.chat.id` — это число
   вида `-1001234567890` (с префиксом `-100`).
4. Скопируйте это число — это и есть `telegramChannelChatId`.

### Шаг 5. Заполнить Karaoke.properties

Откройте `webvue3` → **Properties** (раздел Telegram) и заполните:

| Ключ | Значение | Зачем |
|------|----------|-------|
| `telegramBotToken` | токен из Шага 1 | Авторизация всех запросов к Bot API |
| `telegramChannelUsername` | `svoemestokaraoke` (без @) | Фаза 1: фильтр «наш канал» по username |
| `telegramChannelChatId` | `-1001234567890` (из Шага 4) | Фаза 1: фильтр «наш канал» по chat_id |
| `telegramBotApiBaseUrl` | `https://api.telegram.org` (по умолчанию) | Базовый URL Bot API. Локальный сервер — `http://localhost:8081` |
| `telegramPollingEnabled` | `true` | Включить Фазу 1 (отлов постов) |
| `telegramProxyUrl` | `http://telegram-xray:1082` или пусто | HTTP-прокси для доступа к Telegram из России (см. Шаг 6) |
| `telegramDirectTimeoutMs` | `10000` (по умолчанию) | Таймаут прямой попытки перед переключением на прокси |
| `telegramProxyModeTtlMs` | `60000` (по умолчанию) | Как часто перепроверять восстановление прямого доступа |
| `telegramAutoPublishEnabled` | `true` | Включить Фазу 2 (автопубликация) |
| `telegramAutoPublishChannelId` | `-1001234567890` (тот же, что telegramChannelChatId) | Куда публиковать демо-MP4 |
| `telegramAutoPublishWindowMinutes` | `5` (по умолчанию) | Ширина окна тика (мин) |
| `telegramAutoPublishMaxFileSizeMb` | `50` (по умолчанию) | Лимит размера MP4 для sendVideo |

После заполнения — **перезапустить `karaoke-app`** (properties читаются
при старте, см. `KaraokeProperties.loadPropertiesMap`).

### Шаг 6. Прокси для доступа к Telegram из России (опционально)

Telegram из Docker-контейнера на admin-машине в РФ может быть недоступен
напрямую (блокировки). Решение — HTTP-прокси (VLESS/xray), который
`TelegramApiClient` использует для авто-fallback «напрямую → прокси».

В проекте есть готовый xray-контейнер `karaoke-telegram-proxy`
(`deploy/docker-compose-telegram-proxy.yml`), слушающий
`http://karaoke-telegram-proxy:1082` внутри сети `deploy_karaokenet`.
Содержимое его `config.json` монтируется из
`/sm-karaoke/system/telegram-proxy/config.json`.

#### Вариант A. Управление VLESS из кода (рекомендуется)

Все параметры VLESS — через Properties UI в `webvue3` → раздел Telegram:

| Ключ | Пример | Описание |
|------|--------|----------|
| `telegramVlessEnabled` | `true` | Включить генерацию config.json из свойств |
| `telegramVlessAddress` | `87.58.202.244` | Адрес удалённого xray |
| `telegramVlessPort` | `443` | Порт |
| `telegramVlessUuid` | `38ea8438-a353-4a82-ac2b-b79f64640736` | UUID |
| `telegramVlessFlow` | `""` (пусто для xhttp/tcp) | flow (xtls-rprx-vision для XTLS-direct) |
| `telegramVlessNetwork` | `xhttp` / `tcp` / `ws` / `grpc` | transport |
| `telegramVlessSecurity` | `tls` / `reality` / `none` | stream security |
| `telegramVlessPath` | `/` | path для ws/grpc/xhttp |
| `telegramVlessHost` | `""` (пусто) | Host header |
| `telegramVlessSni` | `""` (пусто) | SNI для tls/reality |
| `telegramVlessAlpn` | `h2,http/1.1,h3` | ALPN (через запятую) |
| `telegramVlessFingerprint` | `chrome` | uTLS fingerprint |
| `telegramVlessPadding` | `100-1000` | xPaddingBytes для xhttp |
| `telegramProxyConfigPath` | `/sm-karaoke/system/telegram-proxy/config.json` | Путь к config.json |
| `telegramProxyContainerName` | `karaoke-telegram-proxy` | Имя контейнера для restart |

`TelegramProxyManager` на `ApplicationReadyEvent`:
1. Если `telegramVlessEnabled=true` и `telegramVlessAddress`/`Uuid` заполнены —
   генерирует `config.json` из свойств и пишет в `telegramProxyConfigPath`.
2. Выполняет `docker restart <telegramProxyContainerName>` через
   `ProcessBuilder` (docker-socket доступен karaoke-app).
3. Контейнер поднимается с актуальным outbound → `TelegramApiClient`
   использует его как прежде (`http://karaoke-telegram-proxy:1082`).

> После изменения VLESS-свойств в Properties UI — перезапустить
> `karaoke-app` (docker container). Менеджер перегенерирует config.json и
> перезапустит xray-контейнер.

> Bind-mount в `docker-compose-telegram-proxy.yml` — `:rw` (с правом
> записи для karaoke-app). Если раньше было `:ro` — обновите compose-файл
> и выполните `cd deploy && docker compose up -d karaoke-telegram-proxy`.

#### Вариант B. Ручной config.json (legacy)

Если `telegramVlessEnabled=false` (по умолчанию) — `TelegramProxyManager`
не трогает файл и не перезапускает контейнер. Можно редактировать
`/sm-karaoke/system/telegram-proxy/config.json` вручную. После правок —
`docker restart karaoke-telegram-proxy`.
4. Если прокси не нужен (сервер вне РФ / Telegram доступен) — оставить
   `telegramProxyUrl` пустым.

### Проверка настройки

1. **Фаза 1**: в канале вручную опубликовать пост со ссылкой
   `https://sm-karaoke.ru/song?id=<id>` → в логах `karaoke-app` должно
   появиться `TelegramUpdatesConsumer: записана ссылка Telegram ... для
   песни id=<id>, message_id=...`.
2. **Фаза 2**: у песни с заполненными `date`/`time` (в будущем, через 5
   мин) и `isContentReady=true` — дождаться тика scheduler'а (лог
   `TelegramAutoPublishService: song id=... → PUBLISHED`). Либо нажать
   кнопку «Опубликовать сейчас» в `webvue3` (карточка песни, вкладка
   Telegram) — пост должен появиться в канале, `idTelegramDemo`
   заполнится.

### Ловушки настройки

- **Бот не админ канала** → `sendVideo` вернёт `403 Forbidden`,
  `telegramAutoPublishState` = `send_failed`, `lastError` = «...403...».
  Решение: Шаг 3.
- **Неверный chat_id** → `getUpdates` ничего не возвращает (Фаза 1 не
  ловит посты), `sendVideo` → `400 Bad Request`. Проверьте Шаг 4.
- **Прокси не отвечает** → `sendVideo`/`getUpdates` падают с
  network-ошибкой, `lastError` = «Telegram недоступен напрямую, а
  telegramProxyUrl не задан». Решение: Шаг 6.
- **Токен невалидный** → все запросы → `401 Unauthorized`. Проверьте
  копирование токена из BotFather (без лишних пробелов/символов).
- **`telegramAutoPublishEnabled=false`** → scheduler no-op (Фаза 2 не
  работает), но кнопка «Опубликовать сейчас» работает (endpoint
  доступен всегда).

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

## Премиум-публикация (автоматическая при становлении песни доступной)

> **Status**: active (auto-publish PREMIUM-выпусков одновременно с Telegram и VK)
> **Feature Key**: premium-auto-publish
> **Spec**: specs/122-premium-auto-publish/

### Что делает

Автоматически публикует PREMIUM-выпуск песни в Telegram-канал **и** в группу
ВК в момент, когда песня становится доступной для premium-подписчиков
(переход флага `newsAvailableAnnounced` false→true). Использует отдельный
PREMIUM-шаблон (отличается от AIR-шаблона, по умолчанию содержит маркер
«премиум» в тексте). Главная особенность: `message_id` Telegram-поста и
`post_id` VK-поста **НЕ записываются** в `Song.idTelegramDemo` /
`Song.idVk` — те же слоты должны заполниться будущей AIR-публикацией
при выходе песни в эфир (это и есть основная «фишка»: один bot-cycle
с PREMIUM-шаблоном, потом отдельный bot-cycle в эфире с AIR-шаблоном).

### Триггер

Хук стоит в `Song.markNewsAvailableIfReady()` (Song.kt). При первом
переходе `newsAvailableAnnounced` false→true выставляется
`newsPremiumPublishPending=true`. Этот же `saveToDb()` дальше запишет
новое значение в БД через стандартный diff/UPDATE (как и остальные
изменения в этом сейве). Никаких отдельных таблиц/колонок не нужно.

### Поток

1. `markNewsAvailableIfReady()` в `Song.saveToDb()` — `newsPremiumPublishPending=true`.
2. `PremiumAutoPublishScheduler` (отдельный `@Scheduled`-tick каждые 30 сек)
   — `SELECT id FROM tbl_songs WHERE player_readiness_flags LIKE '%newsPremiumPublishPending%'`
   + JSON-парсинг для фильтрации точного `true`.
3. Для каждой песни:
   - Загружает полный `Song`, перепроверяет `newsPremiumPublishPending=true`.
   - Если канал занят RENDERING/PUBLISHING (от прошлой попытки) — skip до следующего тика.
   - Если `idTelegramDemo`/`idVk` уже заполнены или `newsPremiumTelegramSent`/`newsPremiumVkSent`
     уже установлены — отмечаем закрытие задачи (`state=COMPLETE`).
   - Последовательно `TelegramAutoPublishService.publishToTelegram(song,
     allowPastDate=true, publicationType=PREMIUM, persistMessageId=false)` и
     `VkAutoPublishService.publishToVk(song, type=PREMIUM, persistPostId=false)`.
   - При успехе в обоих каналах — `newsPremiumPublishPending=false`,
     `premiumAutoPublishState="COMPLETE"`.

### Идемпотентность

Четыре независимых «галочки» в `player_readiness_flags` JSON-блобе:

- `newsPremiumPublishPending` (Boolean) — задача для scheduler'а; снимается после
  `COMPLETE` или `FAILED`.
- `newsPremiumTelegramSent` (Boolean) — успешная PREMIUM-публикация в Telegram;
  не сбрасывается (новое событие «доступна» для одной песни быть не может —
  `newsAvailableAnnounced` монотонно растёт).
- `newsPremiumVkSent` (Boolean) — то же для ВКонтакте.
- `premiumAttemptCount` (int-as-string) — счётчик SEND_FAILED-попыток (общий для TG+VK).

Плюс уже существующие идемпотентности:
- `song.idTelegramDemo.isNotEmpty()` / `song.idVk.isNotEmpty()` — если AIR-публикация
  уже прошла до PREMIUM-тика (теоретически возможно, если эфир наступил раньше
  премиум-тика), skip (защита от дублирования сообщений).

### Лимит попыток

`premiumAutoPublishMaxAttempts` (KaraokeProperties, default 3). При каждом
`SEND_FAILED` (в любом канале) `premiumAttemptCount++`. При достижении лимита —
`newsPremiumPublishPending=false` и `premiumAutoPublishState="FAILED"`. После
FAILED админ видит проблему в UI и может сбросить через прямой
`UPDATE tbl_songs SET player_readiness_flags = REPLACE(...)` или просто
пересохранить песню.

### Шаблоны

PREMIUM-шаблоны конфигурируются отдельно от AIR:
- `telegramTemplatePremium` (KaraokeProperties) — caption для PREMIUM-Telegram-поста.
- `vkTemplatePremium` (KaraokeProperties) — текст для PREMIUM-VK-поста.

Дефолты — `TelegramTemplateService.DEFAULT_PREMIUM_TEMPLATE` и
`VkTemplateService.DEFAULT_PREMIUM_TEMPLATE`. Оба содержат
маркер «премиум» (например, `#премиум`). Плейсхолдеры — те же, что у AIR
({author}, {songName}, {link}, {description}, ...). UI редактируется в том же
«Шаблоны публикаций» в `webvue3` (вкладка «Telegram» → premium).

### Endpoint для ручного триггера

- `POST /api/song/publishPremiumTelegram?id=<songId>` — PREMIUM-публикация в Telegram.
- `POST /api/song/publishPremiumVk?id=<songId>` — PREMIUM-публикация в VK.

Оба возвращают стандартный JSON `{success, state, messageId/postId, error,
newsPremiumPublishPending, newsPremiumTelegramSent, newsPremiumVkSent,
premiumAutoPublishState}` и доступны всегда (даже при
`premiumAutoPublishEnabled=false`) для ручного форсирования/тестов.

### Известные нюансы

- **Задержка до 30 сек** между `saveToDb()` и фактической PREMIUM-публикацией
  (следующий tick scheduler'а). Если нужен моментальный эффект — endpoint
  `/api/song/publishPremiumTelegram`.
- **Две публикации подряд** (PREMIUM + AIR) для одной песни с разными шаблонами —
  в Telegram/VK это нормально, разные сообщения в одном канале с разным текстом.
- **`persistMessageId=false` / `persistPostId=false`** — критичный флаг, без него
  PREMIUM-публикация перезапишет `idTelegramDemo`/`idVk` и сломает идемпотентность
  для будущей AIR-публикации.

### Ссылки на ключевые классы/файлы (премиум)

- [`PremiumAutoPublishScheduler.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PremiumAutoPublishScheduler.kt) — `@Scheduled` бот
- [`Song.markNewsAvailableIfReady`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt) — хук на переход `newsAvailableAnnounced`
- [`TelegramAutoPublishService.publishToTelegram`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishService.kt) — параметр `publicationType`, `persistMessageId`
- [`VkAutoPublishService.publishToVk`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt) — параметр `persistPostId`
- [`ApiController.publishPremiumTelegram` / `publishPremiumVk`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt) — ручные endpoint'ы
- [`KaraokeProperties.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt) — `premiumAutoPublishEnabled` (default false), `premiumAutoPublishMaxAttempts` (default 3)

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
