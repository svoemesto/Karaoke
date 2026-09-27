# Public controllers: SongEditor/Subscription/Share/Cart/Playlist/Stats (средние)

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: обзор 7 critical Public-контроллеров (средние).


## Ответственность | Responsibility


обзор 7 critical Public-контроллеров (средние).

## Файлы

Строки и endpoints — по `wc -l` и grep `@GetMapping`/`@PostMapping` (Pass 481).

| Контроллер | Файл | Строк | Endpoints |
|---|---|---|---|
| `PublicSongEditorController` | `karaoke-web/.../controllers/PublicSongEditorController.kt` | 343 | 8 |
| `PublicSubscriptionController` | `karaoke-web/.../controllers/PublicSubscriptionController.kt` | 304 | 5 |
| `PublicShareController` | `karaoke-web/.../controllers/PublicShareController.kt` | 274 | 7 |
| `PublicCartController` | `karaoke-web/.../controllers/PublicCartController.kt` | 225 | 5 |
| `PublicPlaylistController` | `karaoke-web/.../controllers/PublicPlaylistController.kt` | 573 | 15 |
| `PublicSettingsWebController` | `karaoke-web/.../controllers/PublicSettingsWebController.kt` | 328 | 3 |
| `PublicStemJobController` | `karaoke-web/.../controllers/PublicStemJobController.kt` | 190 | 4 |
| `SiteShareLinksController` | `karaoke-web/.../controllers/SiteShareLinksController.kt` | 136 | 3 |
| `InternalStatsController` | `karaoke-web/.../controllers/InternalStatsController.kt` | 56 | 1 |

## Интерфейсы и Контракты | Interfaces and Contracts

### 1. `PublicSongEditorController` (8 endpoints, 343 строки)

`@RequestMapping("/api/public/account/editor")` — **онлайн-редактор
караоке-разметки** (см. [editorial domain](../../editorial/domain.md) +
[composable-karaoke-editor.md](../../../system/frontend/composable-karaoke-editor.md)).

**Endpoints** (по grep `@GetMapping`/`@PostMapping`):

- `GET /tasks` — мои назначения.
- `GET /tasks/{id}` — детали задания.
- `POST /tasks/{id}/save` — сохранить черновик (все голоса разом).
- `POST /tasks/{id}/submit` — отправить на проверку.
- `POST /tasks/{id}/recall` — отозвать с проверки.
- `POST /tasks/{id}/refuse` — отказаться.
- `POST /tasks/{id}/delete` — удалить.
- `POST /tasks/delete-approved` — удалить одобренные.

Отдельно лежит legacy `PublicSongeditorController.kt` (`POST
/api/public/songeditor/assign-self`) — другой контроллер, не путать.

**CRITICAL** — этот контроллер + `SongEditorController`
(внутренний) реализуют главный user-facing flow:
- assignment → work → submit → approve.
- `defaultTarget='remote'` (см. [store-song-editor.md](../../../system/frontend/store-song-editor.md)) — реальная работа на проде.

### 2. `PublicSubscriptionController` (5 endpoints, 304 строки)

`@RequestMapping("/api/public/account/subscription")` — оформление подписки
(см. [monetization domain](../../monetization/domain.md)).

**Endpoints** (по grep):

- `GET /tariffs?scope=` — активные тарифы (единственный путь под
  `/account/**`, открытый анонимам: `WebMvcConfig.excludePathPatterns`).
- `GET /price` — цена с учётом скидок/акций.
- `POST /create` — создать подписку.
- `GET /list` — мои подписки.
- `POST /cancel` — отмена.

### 3. `PublicShareController` (7 endpoints, 274 строки)

`@RequestMapping("/api/public/share")` — share-ссылки (см.
[SongShareLinkService](./song-share-link-service.md)):

- `POST /{songId}/create` — создать (`ttlSeconds`, дефолт 3600).
- `GET /mine/{songId}` — текущая.
- `POST /mine/{songId}/revoke` — отозвать (`reason`, дефолт `manual`).
- `POST /claim` — привязать share-ссылку к браузеру (guest-сессия).
- `POST /heartbeat` — продлить lease.
- `POST /release` — закрыть сессию (JSON или form-urlencoded).
- `POST /debug` — диагностика claim без создания сессии.

### 4. `PublicCartController` (5 endpoints, 225 строк)

`@RequestMapping("/api/public/account/cart")` — корзина (см.
[composable-use-cart.md](../../../system/frontend/composable-use-cart.md)):

- `GET /list` — список.
- `POST /toggle?songId=` — toggle.
- `POST /clear` — clear.
- `GET /price` — превью цены с учётом акций.
- `POST /checkout` — оформление (YooKassa).

### 5. `PublicPlaylistController` (15 endpoints, **самый большой**)

`@RequestMapping("/api/public/account")` — плейлисты пользователя
(см. [usePlaylistMembership.md](../../../system/frontend/composable-playlist-membership.md)):

- `GET /playlists` — список плейлистов.
- `GET /playlists/{id}` — детали.
- `POST /playlists/create` — создать.
- `POST /playlists/{id}/rename` — переименовать.
- `POST /playlists/{id}/delete` — удалить.
- `POST /playlists/{id}/settings` — настройки.
- `POST /playlists/{id}/addsong` — добавить песню.
- `POST /playlists/{id}/removesong` — убрать.
- `POST /playlists/{id}/reorder` — переставить.
- `POST /playlists/{id}/mute` — mute.
- `POST /favorites/toggle` — избранное.
- `GET /playlists/membership` — DEPRECATED (Pass 361, см. [ADR-0009](../../../adr/0009-get-vs-post-large-payload.md)).
- `POST /playlists/membership` — рекомендуемый (Pass 361, обход
  HTTP 414 на крупных авторах, см. [specs/361](../../../../specs/361-playlists-membership-uri-length/spec.md)).
  Оба делегируют в общий `buildMembershipResponse(user, songIds)`.
- `GET /favorites/ids` — плоский bulk-список избранного (Pass 239).
- `GET /song-subscriptions/ids` — плоский bulk-список подписок на песни (Pass 239).

### 6. `PublicSettingsWebController` (3 endpoints, 328 строк)

`@RequestMapping("/api/properties")` — публичные настройки (live в
`tbl_public_settings`):

- `POST /setproperty` — записать свойство.
- `GET /getproperty?key=` — одно свойство (TTL-кеш).
- `GET /digest` — все свойства (key/value/description, для аудита).

### 7. `PublicStemJobController` (4 endpoints)

`@RequestMapping("/api/public/account/stemjobs")` — премиум-фича «Создать
минусовку»:

- `GET /list` — мои задания.
- `POST /create` — загрузить файл (`file` multipart + `mode`) и запустить.
- `GET /{id}/download?stem=` — скачать результат.
- `POST /{id}/delete` — удалить/отменить (`deleteRequested`).

`POST /{id}/cancel` из старых описаний не существует (проверено Pass 481:
в контроллере нет такого маппинга; отмена идёт через `POST /{id}/delete`).

### 8. `SiteShareLinksController` (3 endpoints, 136 строк)

`@RequestMapping("/api/siteusers/share")` — admin управление share-ссылками
(`SiteShareLinksController`, Pass 477):

- `POST /links` — список ссылок пользователя.
- `POST /links/revoke` — отозвать ссылку.
- `POST /sessions` — playback-сессии по ссылке (аудит).

### 9. `InternalStatsController` (1 endpoint, 56 строк)

`@RequestMapping("/api/internal/stats")` — server-to-server (внутренний):

- `POST /mark-dirty` — взвести флаг `StatBySong.markDirty()`.

## Логика и Алгоритмы | Logic and Algorithms

### 1. `PublicSongEditorController` — assignment → submit → approve

- `save(@PathVariable id: Long, @RequestParam sourceTexts: String,
  @RequestParam markersPerVoice: String, request)` — `user.isEditor`, иначе
  404; `loadOwnedAssignment(id, user.id)`; `canEdit(a, draft)` иначе 409
  `not_editable`. Формат валидируется через
  `json.decodeFromString(ListSerializer(String.serializer()), sourceTexts)`
  и `ListSerializer(ListSerializer(SourceMarker.serializer()))`; ошибка → 400
  `bad_payload`. Черновик (`SongAssignmentDraft`) создаётся или обновляется,
  `userStatus = USER_IN_PROGRESS`.
- `submit(id, request)` — нет черновика → 400 `no_draft`; иначе
  `USER_SUBMITTED` + `submittedAt` (проверка `canEdit` → 409).
- `recall(id, request)` разрешён строго из статуса `SUBMITTED` — проверка на
  сервере, а не по клиентскому состоянию (админ мог уже вынести вердикт).

### 2. Playlist membership — GET vs POST

- `GET /playlists/membership(@RequestParam ids: String)` режет CSV:
  `ids.split(",").mapNotNull { it.trim().toLongOrNull() }`.
- `POST /playlists/membership(@RequestBody MembershipRequest)` берёт
  `request.ids`.
- Оба вызывают один `buildMembershipResponse(user, songIds)` — расходится
  только транспорт; POST добавлен из-за 414 на крупных авторах ([ADR-0009](../../../adr/0009-get-vs-post-large-payload.md)).

### 3. `PublicCartController` — toggle и цена

- `toggle(@RequestParam songId: Long, request)` — товар уже в корзине →
  `CartItem.delete` → `{"inCart": false}`; нет песни → 404; `song.idTariff < 0`
  → 400 `song_not_for_subscription`; уже есть подписка → 409
  `already_subscribed`; иначе `CartItem.createNew` → `{"inCart": true}`.
- `price(request)` — `loadCartCleaned(user)` (убирает уже купленные),
  `PriceTariff.getDefault(SCOPE_SONG)`, `priceService.computeCartPrice(...)`;
  в ответе `paymentsEnabled = paymentService.hasCredentials()`.
- `list(request)` — `loadCartCleaned` + `songInfo`, пустые позиции
  отфильтрованы.

### 4. `PublicSubscriptionController` — тарифы и создание

- `tariffs(@RequestParam scope: String)` — `PriceTariff.loadAll(...)`
  с фильтром `scope` + `isActive`; `paymentsEnabled` сообщает фронту, заданы
  ли ключи ЮKassa (иначе кнопка «Оплатить» отключается).
- Выбор тарифа — по `tariffId` с проверкой `scope` + `isActive`, fallback —
  `PriceTariff.getDefault(scope)`.

### 5. `PublicStemJobController` — валидация создания

`create(@RequestParam file: MultipartFile, @RequestParam mode: String, request)`
идёт по порядку:

1. `!user.isEffectivePremium` → 403 `premium_required`.
2. `mode` не `DEMUCS2`/`DEMUCS5` → 400 `invalid_mode`.
3. `file.isEmpty` → 400 `file_required`.
4. `file.size > StemJob.MAX_FILE_SIZE_BYTES` → 400 `file_too_large`.
5. Расширение не в `StemJob.ALLOWED_EXTENSIONS` → 400 `unsupported_format`.
6. `StemJob.countActiveByUser(user.id) >= StemJob.MAX_ACTIVE_JOBS_PER_USER`
   → 429 `queue_limit_reached`.
7. `StemJob.createNew(...)`; файл кладётся в `File(tempDir, "${job.id}.$ext")`
   через `file.transferTo`; сбой загрузки → `StemJob.delete(job.id)` +
   500 `upload_failed`.

`download(id, stem, request, response)` отдаёт только `status == DONE` и не
истёкший `expiresAt`; `stem` проверяется против
`StemJobMode.stemNames(job.mode) + "original"`. `delete` лишь ставит
`deleteRequested = true` — физическую уборку (в т.ч. в MinIO) делает
`StemJobPollScheduler.cleanup` на стороне karaoke-app.

### 6. `PublicShareController` — claim / heartbeat / release

- `claim(@RequestBody body)` требует `secret` + `browserHash` (иначе 400
  `share.tokenMissing`); `shareService.tryClaim(...)` возвращает
  `linkExpiresAt` (срок ссылки), `expiresAt` (lease 90s), `sessionTokenHash`,
  `redirectTo`, карточку песни. Исключения маппятся в 409 / 429 / 404 /
  500 (`share.internal` — системная ошибка, не маскируется под 404).
- `heartbeat(@RequestBody body)` кешируется `PollingCache` 15s по ключу
  `share_heartbeat:<sessionTokenHash>`; `LeaseExpired` → 410, системная
  ошибка → 500 (и НЕ кешируется).
- `release(...)` принимает JSON и form-urlencoded, потому что
  `navigator.sendBeacon` не отправляет `application/json`.

### 7. `PublicSettingsWebController` — кеш свойства

`getproperty(@RequestParam key: String)` читает
`SELECT value FROM tbl_public_settings WHERE key = ?` через TTL-кеш
(`getCachedProperty`, FR-001/FR-006 спеки `241-db-storage-perf-audit`).
`digest()` отдаёт `key/value/description` без `last_update`; при сбое БД
возвращает пустой список, не 500.

### 8. `SiteShareLinksController` — editor-only

Все три эндпоинта начинаются с `resolveEditorOrThrow(request)`;
`target` выбирает БД (`resolveDatabase`), недоступная удалённая БД → 503
`site.remote_unavailable`.

## Hot paths

- **`PublicPlaylistController`** — самый длинный (15 endpoints).
- **`PublicShareController`** — каждое воспроизведение по share-ссылке.
- **`PublicCartController`** — каждое добавление в корзину.

## Зависимости | Dependencies

- [public-controllers.md](public-controllers.md) — обзор.
- [karaoke-web/services-overview.md](services-overview.md) — backend services.
- [composable-use-cart.md](../../../system/frontend/composable-use-cart.md) — useCart.

## Связанные ADR | Related ADRs

- [ADR-0009](../../../adr/0009-get-vs-post-large-payload.md) — GET → POST для
  больших payload в membership-endpoint (`PublicPlaylistController`):
  query-string упирался в 8 КБ nginx, POST с JSON-телом `{"ids": [...]}`.
- [local-0006](../../../adr/local-0006-logging-and-error-handling-karaoke-web.md) —
  конвенция логирования и error handling в `karaoke-web`
  (`MdcLoggingFilter` + `GlobalExceptionHandler`): каждый HTTP-request
  логируется с `requestId`/`path`/`method`/`status`/`duration`, ошибки
  отдаются JSON-форматом `{code, message}`. Общая для всех Public* endpoints.

## Changelog

- **Pass 481** (2026-09-27, spec `481-knowledge-domain-karaoke-web`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 481-485** (2026-09-09): Initial. Автор: agent (Karaoke).
