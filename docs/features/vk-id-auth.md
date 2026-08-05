# Авторизация через VK ID для получения персонального токена

> **Status**: active (specs/151-vk-id-personal-token)
> **Feature Key**: vk-id-auth
> **Last Updated**: 2026-08-05

## Что делает

Реализует OAuth 2.0 Authorization Code Flow с PKCE через сервис **VK ID**
(`id.vk.ru`) для получения **персонального токена пользователя** (`access_token` +
`refresh_token`), который используется ботом ВКонтакте для:

- **`photos.getWallUploadServer` / `photos.saveWallPhoto`** — загрузка превью-фото
  для постов в группе (specs/138-vk-photo-preview-attachment).
- **`video.save`** — загрузка демо-MP4 (specs/121-vk-news-auto-publish).

Токен **обновляется автоматически** в фоне каждые 60 минут (`@Scheduled cron
'0 0 * * * *'`) через `refresh_token` — админу не нужно повторно открывать URL
и подтверждать права в ВК.

## Зачем

05.08.2026 обнаружено: текущее приложение VK (`client_id=54704234`, зарегистрировано
02.08.2026 через `oauth.vk.ru`) **полностью заблокировано** — все варианты
`/oauth.vk.ru/authorize` возвращают `{"error":"invalid_request","error_description":"Security Error"}`
(HTTP 401) и для серверных (curl), и для клиентских (браузер) запросов. Получить
новый токен через старое приложение невозможно.

VK ID (`id.vk.ru`) — это **рекомендуемый современный способ** авторизации VK:

- Выдаёт токены в формате, совместимом с VK API (включая `photos.*`).
- Поддерживает `refresh_token` для долгосрочного доступа (живёт ~1 год).
- Защищён PKCE (RFC 7636) — обязательно для public clients.
- Чёткая процедура модерации приложения.

## Архитектура

OAuth callback принимает **прод** (`karaoke-web`), потому что VK ID требует
**публичный HTTPS** для `redirect_uri`, а admin-машина `nsa-i9` за домашним
роутером/NAT. Токены сохраняются на admin-машину через внутренний HTTP POST
(`http://nsa-i9:8898/api/utils/vkIdSaveTokens`), где их использует бот
(`VkAutoPublishService`, `VkPhotoUploadClient`).

```
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│  Браузер     │         │  Прод        │         │  Admin-      │
│  админа      │         │  karaoke-web │         │  машина      │
│              │         │              │         │  karaoke-app │
└──────┬───────┘         └──────┬───────┘         └──────┬───────┘
       │                        │                        │
       │  /vkIdOAuthUrl         │                        │
       │───────────────────────►│                        │
       │                        │                        │
       │  https://id.vk.ru/authorize                      │
       │───────────────────────────────────────────────► │
       │  VK ID форма подтверждения                       │
       │◄────────────────────────────────────────────────│
       │                                                │
       │  VK ID redirect:                               │
       │  /api/public/utils/vkIdOAuthCallback?code=XXX  │
       │───────────────────────►│                        │
       │                        │                        │
       │                        │ POST /oauth2/token     │
       │                        │──────────────────────►│ (VK ID)
       │                        │                        │
       │                        │ access+refresh tokens │
       │                        │◄──────────────────────│
       │                        │                        │
       │                        │ POST /api/utils/      │
       │                        │ vkIdSaveTokens        │
       │                        │──────────────────────►│
       │                        │                        │
       │  HTML ✅                │  Karaoke.properties   │
       │◄───────────────────────│  vkIdAccessToken      │
       │                        │  vkIdRefreshToken     │
       │                        │                        │
       │                        │        ┌───────────────┐
       │                        │        │ Scheduler     │
       │                        │        │ каждый час:   │
       │                        │        │ refresh       │
       │                        │        │ access_token  │
       │                        │        └───────────────┘
```

## Как работает

### 1. Один раз: регистрация приложения в VK ID (админ, 5 минут)

1. Зайти на https://id.vk.com/about/business/go/ (от администратора группы
   `svoemestokaraoke`).
2. Создать Web-приложение (тип «Сайт»).
3. Указать `redirect_uri = https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback`.
4. Включить scopes: `vkid.personal_info`, `photos`, `wall`, `video`.
5. Записать `client_id` (числовой) и `client_secret` (показывается 1 раз).

### 2. Настройка (после деплоя кода)

**Admin-машина `nsa-i9`** — добавить в `Karaoke.properties`:

```properties
vkIdClientId=<из шага 1>
vkIdClientSecret=<из шага 1>
vkIdRedirectUri=https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback
```

**Прод `karaoke-web`** — добавить в `docker-compose.yml` (env-переменные):

```yaml
VK_ID_CLIENT_ID: <из шага 1>
VK_ID_CLIENT_SECRET: <из шага 1>
VK_ID_REDIRECT_URI: https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback
VK_ID_ADMIN_API_URL: http://nsa-i9:8898
```

### 3. Получение токена (админ, 2 минуты)

Открыть в браузере (от администратора группы):

```
https://sm-karaoke.ru/api/public/utils/vkIdOAuthUrl
```

В ответе — JSON с готовым `url`. Открыть его в браузере, подтвердить права,
дождаться редиректа. После редиректа HTML покажет «✅ авто-сохранено в
Karaoke.properties admin-машины» (если admin-машина доступна) или curl-команду
для ручного сохранения.

### 4. Автоматический refresh (каждый час, в фоне)

`VkIdTokenRefreshScheduler.refreshIfNeeded()` запускается каждый час (cron
`0 0 * * * *`). Если до `vkIdAccessTokenExpiresAt` осталось < 30 минут —
вызывает `VkApiClient.refreshVkIdAccessToken()` и сохраняет новые токены.
При ошибке (`error=invalid_grant` — `refresh_token` истёк) устанавливает
`vkIdRefreshNeeded=true` для UI-индикатора.

## Endpoints

### `GET /api/public/utils/vkIdOAuthUrl` (прод)

Генерирует URL авторизации с PKCE. Возвращает:

```json
{
  "success": true,
  "url": "https://id.vk.ru/authorize?client_id=...&code_challenge=...&code_challenge_method=S256&...",
  "scopes": "vkid.personal_info photos wall video",
  "clientId": 54704235,
  "redirectUri": "https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback",
  "instructions": ["..."]
}
```

### `GET /api/public/utils/vkIdOAuthCallback?code=XXX&state=YYY` (прод)

Обрабатывает редирект от VK ID: проверяет `state` (CSRF), обменивает `code` на
токены через `POST https://oauth.vk.ru/access_token` с `code_verifier` (PKCE),
(используется старый endpoint — VK ID не имеет отдельного `/oauth2/token`),
отправляет токены POST-ом на admin-машину. Возвращает HTML с подтверждением или
описанием ошибки.

### `POST /api/utils/vkIdSaveTokens` (admin)

Принимает токены от прода (`PublicVkIdAuthController`), проверяет валидность
через `users.get`, сохраняет в `Karaoke.properties`:

- `vkIdAccessToken`
- `vkIdRefreshToken`
- `vkIdAccessTokenExpiresAt = now + expiresIn`
- `vkIdIdToken`
- `vkIdRefreshNeeded = false`
- `vkIdRefreshLastError = ""`

### `GET /api/utils/vkIdTokenStatus` (admin)

Возвращает состояние токена для мониторинга:

```json
{
  "hasClientId": true,
  "hasClientSecret": true,
  "hasAccessToken": true,
  "hasRefreshToken": true,
  "expiresAt": "2026-08-05T12:34:56Z",
  "refreshNeeded": false,
  "lastError": ""
}
```

### `POST /api/utils/vkIdRefreshNow` (admin)

Принудительно вызывает `refreshVkIdAccessToken()`. Полезно для отладки и ручного
управления.

## Инварианты / правила

- **FR-001..FR-015** (см. `specs/151-vk-id-personal-token/spec.md`).
- **`redirect_uri` ДОЛЖЕН ТОЧНО** совпадать с зарегистрированным в кабинете VK ID.
  Любое отличие (https vs http, с/без слэша, query-параметры) → `Security Error`.
- **`code_verifier`** хранится in-memory в `PendingAuth` Map с TTL 10 минут.
  При нескольких инстансах `karaoke-web` потребуется distributed cache.
- **PKCE** обязателен. `code_challenge_method=S256` (не `plain`).
- **`state`** обязателен (CSRF-защита). Генерируется как 32 случайных символа.
- **`client_secret`** передаётся только в server-side запросах, никогда в браузер.
- **Refresh token job** (`VkIdTokenRefreshScheduler`) синхронизирован через
  `synchronized(this)` — защита от параллельного refresh в одном инстансе.
  Для горизонтального масштабирования потребуется distributed lock.

## Известные ловушки

- **`Security Error` от VK ID** — обычно означает, что `redirect_uri` не
  совпадает с зарегистрированным в кабинете VK ID. Проверить **точно**: https,
  домен, путь, без query-параметров.
- **PKCE state mismatch** — `code_verifier` не найден в `pendingAuths`. Возможные
  причины: TTL 10 минут истёк, callback пришёл с другого `state`, или между
  запросами был рестарт `karaoke-web`. Решение: повторить `/vkIdOAuthUrl`.
- **`error_code=5 User authorization failed`** — access_token истёк или отозван.
  Подождать `VkIdTokenRefreshScheduler` или вызвать `/api/utils/vkIdRefreshNow`.
- **`error_code=27 Group authorization failed`** — community-token использован для
  `photos.*`. Должен использоваться `vkIdAccessToken` (проверить, что
  `KaraokeProperties.getString("vkIdAccessToken")` не пуст).
- **Admin-машина недоступна с прода** (firewall) — HTML callback покажет
  curl-команду для ручного сохранения на admin-машине.
- **Refresh token истёк** (~1 год) — `vkIdRefreshNeeded=true`, нужно повторить
  `/vkIdOAuthUrl`.
- **VK ID недоступен при refresh** — scheduler не падает, следующая попытка через
  час. При систематических сбоях — `lastError` в `KaraokeProperties`.

## Backward compatibility

- **`vkUserAccessToken`** остаётся как fallback (`VkApiClient.userAccessToken()`
  читает `vkIdAccessToken.ifBlank { vkUserAccessToken }`). Если у вас уже есть
  валидный старый токен — он продолжит работать.
- **Старые endpoints** (`/api/utils/vkOAuthUrl`, `/api/utils/vkOAuthCodeUrl`,
  `/api/utils/vkOAuthCallback`, `/api/public/utils/vkOAuthCodeUrl`,
  `/api/public/utils/vkOAuthCallback`) возвращают HTTP 410 Gone с понятным
  сообщением «используйте новый VK ID flow». Код НЕ удалён (для истории).
- **`saveVkUserToken`** (admin) оставлен — может использоваться для ручного
  сохранения токена, полученного через Implicit Flow.

## Ссылки

- `specs/151-vk-id-personal-token/spec.md` — основная спецификация.
- `specs/151-vk-id-personal-token/plan.md` — план реализации.
- `specs/151-vk-id-personal-token/quickstart.md` — пошаговая инструкция для админа.
- `specs/151-vk-id-personal-token/contracts/vk-id-api.md` — контракт endpoints.
- `specs/121-vk-news-auto-publish/spec.md` — базовая спецификация автопубликации.
- `specs/138-vk-photo-preview-attachment/spec.md` — спецификация загрузки превью.
- `docs/features/vk-news-auto-publish.md` — общая документация по VK-публикации.
- [VK ID документация](https://id.vk.com/about/business/go/docs/)
- [OAuth 2.0 (RFC 6749)](https://datatracker.ietf.org/doc/html/rfc6749)
- [PKCE (RFC 7636)](https://datatracker.ietf.org/doc/html/rfc7636)