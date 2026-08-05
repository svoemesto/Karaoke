# Data Model: VK ID Personal Token

**Spec**: [spec.md](./spec.md)

> В этой спеке нет новых таблиц в БД — все данные хранятся в
> `Karaoke.properties` (как существующий `vkUserAccessToken`). Здесь
> описаны **ключи** KaraokeProperties и transient-поля для PKCE-flow.

## KaraokeProperties: новые ключи

### Конфигурация приложения (задаются вручную, Фаза 0)

| Ключ | Тип | Default | Описание |
|------|-----|---------|----------|
| `vkIdClientId` | Long | 0 | ID приложения VK ID (числовой, например 54704235). Задаётся при регистрации приложения. |
| `vkIdClientSecret` | String | "" | Секрет приложения VK ID (защищённый ключ). Показывается 1 раз при регистрации. |
| `vkIdRedirectUri` | String | "" | URL редиректа для OAuth callback. Должен **точно** совпадать с тем, что в кабинете VK ID. |

### Токены (заполняются автоматически после callback)

| Ключ | Тип | Default | Описание |
|------|-----|---------|----------|
| `vkIdAccessToken` | String | "" | Access token для VK API (живёт ~1 час). Формат: `<user_id>_<token>`. |
| `vkIdRefreshToken` | String | "" | Refresh token для обновления access_token (живёт ~1 год). |
| `vkIdAccessTokenExpiresAt` | String | "" | ISO datetime истечения access_token. Формат: `2026-08-05T12:34:56Z`. |
| `vkIdIdToken` | String | "" | JWT-токен с информацией о пользователе (OIDC). Сохраняется, но не валидируется в первой версии. |

### Состояние refresh

| Ключ | Тип | Default | Описание |
|------|-----|---------|----------|
| `vkIdRefreshNeeded` | Boolean | false | Флаг «требуется повторная авторизация» (true когда refresh_token истёк). |
| `vkIdRefreshLastError` | String | "" | Текст последней ошибки refresh (для UI-индикатора и логов). |

### Transient-поля для PKCE (TTL 10 минут)

| Ключ | Тип | Default | Описание |
|------|-----|---------|----------|
| `vkIdPendingCodeVerifier` | String | "" | PKCE code_verifier для текущего OAuth flow. Очищается в callback. |
| `vkIdPendingState` | String | "" | CSRF state для текущего OAuth flow. Очищается в callback. |
| `vkIdPendingAt` | String | "" | ISO datetime создания pending-значений (для TTL-проверки). |

## Обратная совместимость с `vkUserAccessToken`

Поле `vkUserAccessToken` (существующее) **остаётся** для обратной совместимости.
Но `VkApiClient.userAccessToken()` теперь возвращает:

```kotlin
private fun userAccessToken(): String {
    val idToken = KaraokeProperties.getString("vkIdAccessToken")
    return idToken.ifBlank { KaraokeProperties.getString("vkUserAccessToken") }
}
```

То есть, приоритет:
1. `vkIdAccessToken` (новый, через VK ID).
2. `vkUserAccessToken` (старый, fallback) — для переходного периода.

## Существующие ключи VK (deprecated, но не удаляются)

| Ключ | Статус | Описание |
|------|--------|----------|
| `vkAccessToken` | Активен | Community access token (для `wall.post`, `docs.*` fallback). |
| `vkUserAccessToken` | Deprecated | User access token (старый, через oauth.vk.ru). Используется как fallback. |
| `vkAppId` | Deprecated | ID Standalone-приложения (для старого OAuth). |
| `vkRedirectUri` | Deprecated | Redirect URI для старого OAuth. |
| `vkClientSecret` | Deprecated | Client secret Web-приложения (для старого Authorization Code Flow). |
| `vkApiVersion` | Активен | Версия VK API (по умолчанию `5.199`). |

> ⚠️ Эти ключи остаются в `KaraokeProperties.kt` и `application.yml` для
> обратной совместимости и истории. Со временем (после полного перехода на
> VK ID) могут быть удалены — отдельная задача в backlog.

## Сущности (entities)

### VkIdApplication (приложение VK ID)

Регистрируется на https://id.vk.com/about/business/go/. Хранится **вне проекта**
(в кабинете VK ID), в проекте — только `client_id` + `client_secret`.

```kotlin
data class VkIdApplication(
    val clientId: Long,           // = KaraokeProperties.getLong("vkIdClientId")
    val clientSecret: String,     // = KaraokeProperties.getString("vkIdClientSecret")
    val redirectUri: String,      // = KaraokeProperties.getString("vkIdRedirectUri")
    val scopes: List<String> = listOf(
        "vkid.personal_info",
        "photos",
        "wall",
        "video",
    ),
)
```

### VkIdAccessToken (access token)

Короткоживущий токен (~1 час) для вызова VK API.

```kotlin
data class VkIdAccessToken(
    val value: String,           // = KaraokeProperties.getString("vkIdAccessToken")
    val expiresAt: Instant,      // = parse(KaraokeProperties.getString("vkIdAccessTokenExpiresAt"))
    val issuedAt: Instant,       // вычисляется как expiresAt - 3600s
)
```

### VkIdRefreshToken (refresh token)

Долгоживущий токен (~1 год) для обновления access_token.

```kotlin
data class VkIdRefreshToken(
    val value: String,           // = KaraokeProperties.getString("vkIdRefreshToken")
    val issuedAt: Instant,       // вычисляется как expiresAt access_token (момент первого refresh)
)
```

### VkIdTokenRefreshResult (результат refresh)

Возвращается из `VkApiClient.refreshVkIdAccessToken()`.

```kotlin
data class VkIdTokenRefreshResult(
    val accessToken: String,      // новый access_token
    val refreshToken: String,     // новый refresh_token (VK ID ротирует!)
    val expiresIn: Long,          // секунды до истечения нового access_token
    val idToken: String?,         // новый id_token (опционально)
)
```

### VkIdPendingAuth (transient-состояние OAuth flow)

Хранится в KaraokeProperties с TTL 10 минут.

```kotlin
data class VkIdPendingAuth(
    val codeVerifier: String,     // = KaraokeProperties.getString("vkIdPendingCodeVerifier")
    val state: String,            // = KaraokeProperties.getString("vkIdPendingState")
    val createdAt: Instant,       // = parse(KaraokeProperties.getString("vkIdPendingAt"))
)
```

## Диаграмма потоков данных

```
┌─────────────────────────────────────────────────────────────────┐
│                         Karaoke.properties                      │
│                                                                 │
│  ┌─────────────────────┐    ┌─────────────────────┐           │
│  │ vkIdClientId        │    │ vkIdAccessToken     │           │
│  │ vkIdClientSecret    │    │ vkIdRefreshToken    │           │
│  │ vkIdRedirectUri     │    │ vkIdAccessToken     │           │
│  │ (конфигурация)      │    │ ExpiresAt           │           │
│  └─────────────────────┘    │ vkIdIdToken         │           │
│                             │ (токены)            │           │
│                             └─────────────────────┘           │
│                                                                 │
│  ┌─────────────────────┐    ┌─────────────────────┐           │
│  │ vkIdRefreshNeeded   │    │ vkIdPending*        │           │
│  │ vkIdRefreshLastError│    │ (transient, TTL     │           │
│  │ (состояние)         │    │  10 минут)          │           │
│  └─────────────────────┘    └─────────────────────┘           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
         │                                  │
         │ читается                          │ записывается
         ▼                                  ▼
┌─────────────────────┐          ┌─────────────────────┐
│ PublicVkIdAuth-     │          │ ApiController.      │
│ Controller          │          │ vkIdSaveTokens      │
│ (karaoke-web)       │          │ (karaoke-app)       │
└─────────────────────┘          └─────────────────────┘
         │                                  ▲
         │  HTTP POST                       │ HTTP POST
         │  /oauth2/token                   │ /api/utils/vkIdSaveTokens
         ▼                                  │
┌─────────────────────┐                      │
│ VK ID               │                      │
│ id.vk.ru            │                      │
└─────────────────────┘                      │
                                             │
┌─────────────────────┐                      │
│ VkIdTokenRefresh-   │──────────────────────┘
│ Scheduler           │
│ (каждый час)        │
└─────────────────────┘
```

## Миграция данных (если нужна)

В первой версии **миграция не нужна** — мы не переносим токены из старого
приложения в новое (старое приложение заблокировано). Пользователь просто
получает новый токен через VK ID.

Если позже потребуется миграция (например, переход на другое VK ID
приложение) — это будет отдельная задача.

## Безопасность

- ⚠️ `vkIdClientSecret`, `vkIdAccessToken`, `vkIdRefreshToken` — **секреты**.
  Не логировать, не коммитить в git, не передавать в URL.
- ⚠️ `Karaoke.properties` хранится на сервере с ограниченным доступом.
- ⚠️ Transient-поля `vkIdPending*` очищаются после успешного callback
  или через 10 минут TTL.
- ⚠️ Если кто-то получает доступ к `Karaoke.properties` — может
  выдавать себя за админа группы ВК. Поэтому файл должен быть защищён
  (chmod 600, root-only).

## Что НЕ хранится в Karaoke.properties

- ❌ **Полный ответ VK ID** (содержит много лишних полей, раздувает файл).
- ❌ **PKCE `code_verifier` навсегда** — только transient (10 минут TTL).
- ❌ **История refresh'ей** — только последний `expiresAt` и `lastError`.
- ❌ **Логи** — хранятся в стандартных логах Spring Boot, не в properties.

Если в будущем потребуется детальная история — отдельная таблица в БД
(например, `tbl_vk_id_token_history`). В backlog.