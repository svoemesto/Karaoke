# Component: external-api-clients

> **Домен**: integration (внешние API)
> **Компонента**: каталог внешних HTTP-клиентов проекта.

## Ответственность | Responsibility

Это **все HTTP-клиенты** проекта к внешним сервисам. Каждый
клиент — отдельный `object` или `class` в `services/`. Используются
через DI или прямой вызов.

Сервисы:

- **VK** — ВКонтакте (публикация постов, фото, превью, токены).
- **Telegram** — Bot API + Updates consumer (публикация, polling).
- **Yandex Captcha** — проверка капчи (karaoke-web).
- **LM Studio** — локальная LLM (admin).
- **Whisper ASR** — распознавание речи (admin).
- **Alignment** — forced alignment маркеров.
- **GeoIp** — определение страны по IP (admin).

Каждый клиент использует SLF4J для логирования (некоторые — `println`,
см. gaps).

## Интерфейсы и Контракты | Interfaces and Contracts

Контракт каждого клиента — его методы, DTO запроса/ответа, HTTP-клиент
и источник конфигурации. Ниже — сводка, затем детали по клиентам.

| # | Клиент | Файл | Что делает | Протокол |
|---|---|---|---|---|
| 1 | `VkApiClient` | `services/VkApiClient.kt` | wall.post, photos.*, и др. | VK Open API |
| 2 | `VkPhotoUploadClient` | `services/VkPhotoUploadClient.kt` | Загрузка PNG-обложки в группу | VK photos.* + docs.* fallback |
| 3 | `VkPreviewWarmupClient` | `services/VkPreviewWarmupClient.kt` | Прогрев preview-кэша в VK | HTTP GET на preview URL |
| 4 | `VkTemplateService` | `services/VkTemplateService.kt` | Шаблоны постов для VK | Шаблоны (не HTTP) |
| 5 | `TelegramApiClient` | `services/TelegramApiClient.kt` | sendMessage, sendPhoto, getUpdates | Telegram Bot API |
| 6 | `TelegramTemplateService` | `services/TelegramTemplateService.kt` | Шаблоны постов для TG | Шаблоны |
| 7 | `TelegramUpdatesConsumer` | `services/TelegramUpdatesConsumer.kt` | Consumer getUpdates (long-polling) | Telegram Bot API |
| 8 | `TelegramProxyManager` | `services/TelegramProxyManager.kt` | Управление SOCKS proxy | Утилита |
| 9 | `TelegramAutoPublishService` | `services/TelegramAutoPublishService.kt` | Оркестратор авто-постинга | (оркестратор) |
| 10 | `LmStudioService` | `services/LmStudioService.kt` | OpenAI-совместимый /v1/chat/completions | HTTP POST |
| 11 | `WhisperAsrService` | `services/WhisperAsrService.kt` | Whisper ASR (multipart upload) | HTTP multipart |
| 12 | `AlignmentServiceClient` | `services/AlignmentServiceClient.kt` | Forced alignment маркеров | HTTP multipart |
| 13 | `GeoIpService` | `services/GeoIpService.kt` | api.country.is/<ip> | HTTP GET |
| 14 | `YandexCaptchaValidationService` | `karaoke-web/.../services/YandexCaptchaValidationService.kt` | SmartCaptcha validation | HTTP POST |

---

### 1. `VkApiClient`

**Файл**: `karaoke-app/.../services/VkApiClient.kt`.

**Что**: VK Open API клиент (тонкая обёртка над HTTP).

**Методы** (по grep KDoc):

- `wall.post` — публикация поста на стене.
- `photos.*` — upload/get/сохранение фото (через отдельный
  `VkPhotoUploadClient`).
- `wall.post` с attachments.

**HTTP**: `java.net.http.HttpClient` (не OkHttp).

**Логирование**: SLF4J.

**Прокси-fallback** (specs/437, #161): как у [TelegramApiClient](#5-telegramapiclient) —
каждый запрос сначала напрямую, при сетевом сбое → HTTP-прокси (`vkProxyUrl`) на
TTL (`vkProxyModeTtlMs`) с авто-возвратом. **Отличие**: прокси для VK
**опционален**. Если `vkProxyUrl` не задан, при сбое прямого запроса выполняется
повторная прямая попытка, затем бросается типизированная `VkNetworkException`
(её конвертируют в `SEND_FAILED` оркестраторы — `VkAutoPublishService` /
`VkAutoPublishScheduler` / `VkPhotoUploadClient`). Решение «direct vs proxy»
вынесено в чистую функцию `VkApiClient.decideSendMode` (тесты
`VkApiClientSendModeTest`). Прецедент: 2026-09-23 — `IllegalStateException`
пролетал через `@Scheduled`-тик 12× подряд.

---

### 2. `VkPhotoUploadClient`

**Файл**: `karaoke-app/.../services/VkPhotoUploadClient.kt`.

**Что**: HTTP-оркестратор загрузки PNG-обложки песни в группу ВК
через `photos.*` с fallback на `docs.*`.

**Результат**: `data class PhotoUploadResult(method, attachment, error)`.

**Метод**: `method` — какой метод использован (`photos` / `docs`).
**Attachment**: строка для `wall.post attachments`
(например, `photo-123_456` или `doc-123_789`).

**Логирование**: SLF4J.

**Связь**: спецификация `archive/docs/features/vk-photo-preview-attachment.md` (138).

---

### 3. `VkPreviewWarmupClient`

**Файл**: `karaoke-app/.../services/VkPreviewWarmupClient.kt`.

**Что**: прогрев preview-кэша в VK перед публикацией поста.

**Результат**: `VkPreviewWarmupResult(status, songId, attempts, httpStatus, contentType, bytes, durationMs, error)`.

**HTTP**: GET на preview URL.

**Связь**: спецификация `archive/docs/features/vk-preview-generation.md` (130).

**Используется**: `VkAutoPublishService` для оркестрации публикации
(чтобы preview был закэширован ВК к моменту публикации).

---

### 4. `VkTemplateService`

**Файл**: `karaoke-app/.../services/VkTemplateService.kt`.

**Что**: шаблоны постов для VK (не HTTP, просто Kotlin-шаблонизация
строк).

---

### 5. `TelegramApiClient`

**Файл**: `karaoke-app/.../services/TelegramApiClient.kt`.

**Что**: Telegram Bot API клиент.

**Методы** (по grep DTO + KDoc):

- `sendMessage` — отправка текстового сообщения.
- `sendPhoto` — отправка фото.
- `getUpdates` (через `TelegramUpdatesConsumer`) — long-polling
  channel_post.
- **Long-polling** через `offset + timeout=long`.

**DTO**: `TelegramChat`, и др. (snake_case формат как у Telegram).

**HTTP**: `java.net.http.HttpClient`.

**Ловушка**: rate limits Telegram (1 запрос/сек на токен). Решение —
встроенный rate-limiter или очередь `KaraokeProcess`.

---

### 7. `TelegramUpdatesConsumer`

**Файл**: `karaoke-app/.../services/TelegramUpdatesConsumer.kt`.

**Что**: long-polling для отлова `channel_post` (новые посты в канале).

**Используется**: для детекции новых постов karaoke в Telegram
(когда user постит в наш канал — нужно отслеживать ссылку).

---

### 8. `TelegramProxyManager`

**Файл**: `karaoke-app/.../services/TelegramProxyManager.kt`.

**Что**: управление SOCKS-прокси для Telegram (MTU black-hole та же
проблема, что nginx-прокси для MinIO).

---

### 9. `TelegramAutoPublishService`

**Файл**: `karaoke-app/.../services/TelegramAutoPublishService.kt`.

**Что**: оркестратор авто-постинга в TG (вызывается из
`TelegramAutoPublishScheduler`).

**Результат**: `TelegramAutoPublishResult` — DTO с результатом.

**Состояние**: `TelegramAutoPublishState` (in-memory).

---

### 10. `LmStudioService`

**Файл**: `karaoke-app/.../services/LmStudioService.kt`.

**Что**: тонкий клиент над LM Studio (OpenAI-совместимый
`/v1/chat/completions`), поднятым на **хост-машине админа отдельно
от контейнеров** этого проекта. LM Studio с включённым
«Обслуживание по локальной сети» слушает конкретный LAN IP хоста
(не 0.0.0.0), поэтому `host.docker.internal` не подходит.

**Конфигурация** через `KaraokeProperties`:

- `lmStudioUrl` — URL хоста.
- `lmStudioModel` — название модели.
- `lmStudioTimeoutMs` — timeout (default 120s).
- `lmStudioApiKey` — Bearer token (опционально).

**Метод**: `chat(systemPrompt, userText): String?`.

**HTTP**: `OkHttpClient`.

**Логирование**: SLF4J.

---

### 11. `WhisperAsrService`

**Файл**: `karaoke-app/.../services/WhisperAsrService.kt`.

**Что**: HTTP multipart upload к Whisper для распознавания речи.

**DTO**: `WhisperWordDto(word, start, confidence)`, `WhisperSegmentDto(text, start)`.

**Используется**: `FORCED_ALIGN_MARKERS` KaraokeProcessType.

---

### 12. `AlignmentServiceClient`

**Файл**: `karaoke-app/.../services/AlignmentServiceClient.kt`.

**Что**: HTTP multipart upload к сервису forced alignment (см.
`alignment-ml/` — отдельный Python-сервис).

**DTO**: `AlignSyllableDto(label, startMs, endMs)`,
`AlignResponseDto(ok, syllables)`.

---

### 13. `GeoIpService`

**Файл**: `karaoke-app/.../services/GeoIpService.kt`.

**Что**: определение страны по IP клиента через `api.country.is/<ip>`.

**Кэш** (двухуровневый):

- **In-memory**: `ConcurrentHashMap<ip, country>`.
- **БД**: `tbl_ip_country` (`deploy/karaoke-db/08_ip_country.sql`).

**NB**: кэш-таблица живёт **ТОЛЬКО на LOCAL** админ-БД (не на
проде). Поэтому все обращения идут через `Connection.local()`
независимо от источника событий. IP есть IP, страна есть страна, кэш
общий для админ-машины.

**Пустая строка `""`** — валидный закэшированный результат «страна
не определена» (приватный/локальный IP, серый адрес, сервис не
ответил). Это защита от re-fetch по тем же IP.

**Батч-резолв с бюджетом времени** (`resolveMany(rawIps, maxFetch, timeBudgetMs)`):
сначала память, затем один `IN`-запрос в `tbl_ip_country`, затем недостающие
резолвятся по одному с паузой 80 мс (~12 req/s). Два лимита:

- `maxFetch` — сколько ВНЕШНИХ резолвов разрешено за вызов;
- `timeBudgetMs` — общий бюджет времени (default `Long.MAX_VALUE` — без лимита).

Не поместившиеся в лимит/бюджет IP возвращаются как `""` и **не кэшируются** —
их подхватит следующий вызов, кэш наполняется за несколько обновлений страницы.
Уже начатый внешний резолв бюджет не прерывает (connect/read timeout по 5 с),
поэтому фактическое время может превысить бюджет на один запрос.

Текущие call-site'ы (spec `478-fix-admin-stats-tabs`, OP #184):

| Call-site | `maxFetch` | `timeBudgetMs` |
| --- | --- | --- |
| `getCountryBreakdown` → `/api/stats/countries` | 150 | 3 000 |
| страница `/api/webevents` (страна для 50 событий) | 100 | 2 000 |

Причина лимита по времени: 150 IP × (сетевой запрос + пауза 80 мс) держали
вкладку «География» в загрузке 25+ секунд.

---

### 14. `YandexCaptchaValidationService`

**Файл**: `karaoke-web/.../services/YandexCaptchaValidationService.kt`.

**Что**: проверка SmartCaptcha (Yandex) для защиты от ботов.

**HTTP**: POST к Yandex Captcha API.

**Используется**: `PublicAuthController` (login/register) — капча
защищает формы аутентификации; `SiteAuthInterceptor` покрывает только
`/api/public/account/**` и капчу не вызывает.

---

## Логика и Алгоритмы | Logic and Algorithms

Сквозные алгоритмы, общие для нескольких клиентов:

**Send-mode fallback (VK и Telegram)**: каждый запрос сначала идёт
**напрямую**; при сетевом сбое клиент переключается на HTTP-прокси
(`vkProxyUrl` / `telegramProxyUrl`) и запоминает режим на TTL
(`vkProxyModeTtlMs` / `telegramProxyModeTtlMs`, default 60 с), после
чего снова пробует напрямую. У VK прокси **опционален**: если
`vkProxyUrl` пуст, выполняется повторная прямая попытка и бросается
`VkNetworkException`; оркестраторы (`VkAutoPublishService`,
`VkAutoPublishScheduler`, `VkPhotoUploadClient`) конвертируют её в
`SEND_FAILED`. Выбор режима у VK вынесен в чистую функцию
`VkApiClient.decideSendMode` (тесты `VkApiClientSendModeTest`).

**Загрузка обложки в VK** (`VkPhotoUploadClient.uploadCover`):

1. При `vkPhotoAttachEnabled` — путь `photos.*` c **user-token**:
   `photos.getWallUploadServer` → POST multipart на `upload_url` →
   `photos.saveWallPhoto`.
2. При ошибке авторизации (`error_code in 27/15/5/29`) — fallback на
   `docs.*` c **community-token** (если `vkDocAttachEnabled`):
   `docs.getWallUploadServer` → POST multipart → `docs.save`.
3. Transient-ошибки повторяются внутри цепочки (`transientAttempts = 2`,
   backoff `transientBackoffMs = 5 000`); `error_code=100` → типизированное
   исключение.
4. Полный сбой обоих путей → `VkBothAttachFailedException` →
   деградация: пост без превью.

**GeoIP-резолв** (`GeoIpService`): двухуровневый кэш — in-memory
`ConcurrentHashMap` и таблица `tbl_ip_country` в LOCAL-БД
(`Connection.local()`). Пустая строка `""` — валидный **закэшированный**
результат «страна не определена» (защита от re-fetch). `resolveMany`:
память → один `IN`-запрос в БД → недостающие резолвятся по одному с
паузой 80 мс (~12 req/s), ограничение — `maxFetch` (число внешних
запросов) и `timeBudgetMs` (бюджет времени). Не поместившиеся
возвращаются как `""` и **не кэшируются**.

**Yandex SmartCaptcha** (`YandexCaptchaValidationService.validate`): если
серверный ключ не заведён — проверка пропускается (`true`); пустой
токен → `false`; иначе POST `/validate` (form: `secret`, `token`,
`ip`) и результат `status == "ok"`.

**Тонкие ML-клиенты** (`LmStudioService`, `WhisperAsrService`,
`AlignmentServiceClient`) при любой ошибке (пустой URL, не-2xx,
исключение) возвращают `null` — retry-политики у них нет, решение
принимает вызывающий код.

### Архитектурные решения

#### Решение 1: VK / TG / Yandex — все идут через nginx-прокси

См. [storage-flow.md](../../../domains/storage/components/storage-flow.md)
— та же проблема MTU black-hole. Решение — nginx proxy (см.
WebClientConfig).

#### Решение 2: LM Studio — LAN-only

`host.docker.internal` НЕ подходит. LM Studio слушает конкретный
LAN IP хоста (НЕ `0.0.0.0`), поэтому URL должен быть настроен через
`lmStudioUrl` в `KaraokeProperties`.

#### Решение 3: VkPreviewWarmupClient — диагностический результат

`VkPreviewWarmupResult` НЕ персистится в БД (используется только
оркестратором). Это сознательное решение — данные нужны только для
немедленного решения оркестратора, не для аудита.

---

## Зависимости | Dependencies

**Конфигурация и бины**:

- `KaraokeProperties` — источник URL/токенов/флагов для всех клиентов
  (`vkAccessToken`, `vkUserAccessToken`, `telegramBotToken`,
  `telegramBotApiBaseUrl`, `whisperAsrUrl`, `whisperApiKey`,
  `alignmentServiceUrl`, `lmStudioUrl`, `lmStudioModel`, `lmStudioApiKey`).
- `WebClient`-бины (`karaoke-web/.../config/WebClientConfig.kt`):
  `yandexCaptchaWebClient` (baseUrl = `${captcha.proxy-url}` =
  `http://minio-proxy/smartcaptcha`), `yookassaWebClient`, `@Primary`
  `smKaraokeWebClient`. `YandexCaptchaValidationService` дополнительно
  зависит от `CaptchaConfigService` (серверный ключ).
- Потребители-зависимости: `PublicAuthController` (Captcha),
  `VkAutoPublishService` / `VkAutoPublishScheduler`,
  `TelegramAutoPublishService` / `TelegramAutoPublishScheduler`,
  `TelegramUpdatesConsumer`.

**HTTP-клиенты**: `java.net.http.HttpClient` (VK, Telegram),
`OkHttpClient` (LM Studio, Whisper, Alignment), Spring `WebClient`
(Yandex Captcha).

**nginx-proxy (обход MTU black-hole)**:

- Хостовый `minio-proxy` (`extra_hosts: minio-proxy:host-gateway`) с
  путями `/smartcaptcha`, `/yookassa`, storage — см.
  `deploy/prod-single-host/docker-compose-web.yml`.
- Локальный аналог — контейнер `karaoke-minio-proxy`
  (`deploy/karaoke-web/minio-proxy-local.conf`,
  `deploy/docker-compose-web.yml`).
- Telegram — отдельный `karaoke-telegram-proxy` (xray,
  `deploy/docker-compose-telegram-proxy.yml`, конфиг
  `/sm-karaoke/system/telegram-proxy/config.json`), которым управляет
  `TelegramProxyManager`, а не minio-proxy.

**Кэши и хранилище**: `GeoIpService` — in-memory
`ConcurrentHashMap<ip, country>` + таблица `tbl_ip_country` в LOCAL-БД
(`Connection.local()`, `deploy/karaoke-db/08_ip_country.sql`);
VK/Telegram держат in-memory состояние режима прокси (`useProxy`,
`modeSetAtMs`).

## Известные TODO

- [ ] **Конкретные эндпоинты** каждого клиента (что и куда шлёт).
- [ ] **Rate-limit handling** — Telegram (1 req/sec) и VK (3 req/sec).
- [ ] **Retry policy** при HTTP failure.
- [ ] **MTU black-hole**: как именно настроен nginx-proxy для каждого
      внешнего сервиса.
- [ ] **Whisper/Alignment host URL** — где работает ML-сервис
      (LAN, Docker container, отдельная машина).
- [ ] **Логирование**: унифицировать println → SLF4J.
- [ ] **`alignment-ml`** Python-сервис — отдельный документ
      (см. gaps).
- [ ] **VkSchedulerStarter / TelegramSchedulerStarter** — что
      именно делают (`Starter` — отдельная сущность).

## Код (физическая реализация)

См. таблицу выше.

## Связанные компоненты

- **Schedulers** — [schedulers.md](../../../domains/processing/components/schedulers.md)
  (TelegramAutoPublishScheduler, VkAutoPublishScheduler).
- **Storage flow** — [storage-flow.md](../../../domains/storage/components/storage-flow.md)
  (nginx-прокси).
- **Two-DB sync** — [two-db-sync.md](../../../domains/processing/components/two-db-sync.md)
  (cross-machine sync через HTTP).

## Changelog

- **Pass 484** (2026-09-27, spec `484-knowledge-domain-integration`): секции приведены к шаблону. Автор: agent (Karaoke).
- **Pass 437** (2026-09-23, issue #161): `VkApiClient` proxy-fallback — прокси
  опционален; добавлена `VkNetworkException`, `decideSendMode` (чистая функция),
  unit-тесты `VkApiClientSendModeTest`. Оркестраторы (`VkAutoPublishService`,
  `VkAutoPublishScheduler`, `VkPhotoUploadClient`) конвертируют сетевую ошибку
  в `SEND_FAILED` / transient.
- **Pass 345** (2026-09-09): Initial. Автор: agent (Karaoke).