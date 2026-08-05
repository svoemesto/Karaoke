# Research: VK ID Authorization

**Spec**: [spec.md](./spec.md)

> **Архитектурный контекст**: где работает OAuth, а где — бот.
> См. секцию «Архитектурный контекст» в `spec.md` — там же
> диаграмма потока и объяснение, почему `redirect_uri` указывает
> на прод (`karaoke-web`), а публикация делается на admin-машине
> (`karaoke-app`, `nsa-i9`).

## Что исследовали

VK ID — это новая система авторизации VK, которая пришла на замену
устаревшему `oauth.vk.ru`. Изучали:

1. Официальную документацию VK ID (https://id.vk.com/about/business/go/docs/).
2. Спецификацию OAuth 2.0 (RFC 6749).
3. Спецификацию PKCE (RFC 7636).
4. Спецификацию OpenID Connect (OIDC) Core 1.0.
5. Текущую реализацию OAuth в проекте (PublicVkAuthController).
6. Поведение текущего приложения VK (`client_id=54704234`).

## 1. Текущее состояние: почему oauth.vk.ru не работает

### 1.1. Что тестировали

Тестировали 5 августа 2026 — все варианты `/oauth.vk.ru/authorize`
возвращают `{"error":"invalid_request","error_description":"Security Error"}`
с HTTP 401:

| Вариант | `redirect_uri` | `response_type` | Результат |
|---------|----------------|-----------------|-----------|
| Auth Code Flow | `https://sm-karaoke.ru/api/utils/vkOAuthCallback` | `code` | ❌ Security Error |
| Auth Code Flow | `https://sm-karaoke.ru/api/public/utils/vkOAuthCallback` | `code` | ❌ Security Error |
| Implicit Flow | `https://oauth.vk.ru/blank.html` | `token` | ❌ Security Error |
| Implicit Flow | `https://localhost` | `token` | ❌ Security Error |
| Без redirect_uri | (пусто) | `token` | ❌ Security Error |

### 1.2. Гипотезы о причине

| Гипотеза | Вероятность | Обоснование |
|----------|-------------|-------------|
| **Приложение заблокировано VK** за нарушение правил | Высокая | "Security Error" — общий термин VK для заблокированных приложений. Проверить невозможно без входа в кабинет VK ID / oauth.vk.ru. |
| **Приложение не активировано** (нет email/телефона) | Средняя | Если Standalone-приложение не прошло активацию — VK может блокировать `/authorize`. |
| **Приложение Standalone, а не Web** | Низкая | Implicit Flow для Standalone должен работать в браузере. Но если приложение Standalone, Auth Code Flow может быть заблокирован. |
| **redirect_uri не зарегистрирован** | Низкая | VK возвращает конкретную ошибку `redirect_uri_mismatch`, а не `Security Error`. |
| **origin не разрешён** | Низкая | Для Standalone origin обычно не проверяется. |
| **VK глобально ограничил Standalone-приложения** | Средняя | VK может ввести ограничения для Standalone-приложений из-за abuse. |

**Вывод**: скорее всего, приложение `54704234` заблокировано. Чтобы получить
новый токен, нужно зарегистрировать **новое приложение** через **VK ID**.

## 2. VK ID: что это и как работает

### 2.1. Что такое VK ID

VK ID (`id.vk.ru`) — это **новый OAuth 2.0 / OIDC провайдер** от ВКонтакте,
который заменяет устаревший `oauth.vk.ru`. Основные отличия:

- ✅ **Современный протокол** — OAuth 2.0 + OIDC (вместо устаревшего OAuth 2.0).
- ✅ **PKCE по умолчанию** — обязателен для public clients (SPA, мобильные).
- ✅ **Refresh token** — выдаётся по умолчанию, не требует scope `offline`.
- ✅ **OpenID Connect** — id_token (JWT) с информацией о пользователе.
- ✅ **UserInfo endpoint** — `https://id.vk.ru/oauth2/user_info` для получения
  профиля пользователя по access_token.
- ✅ **Чёткая модерация** — приложение сразу понятно зарегистрировано,
  видно scopes и redirect_uri.

### 2.2. Endpoints

| Endpoint | URL | Назначение |
|----------|-----|-----------|
| Authorization | `https://id.vk.ru/authorize` | Получение authorization code |
| Token | `https://oauth.vk.ru/access_token` | Обмен code → token / refresh token |
| UserInfo | `https://id.vk.ru/oauth2/user_info` | Получение профиля пользователя |
| Public Key | `https://id.vk.ru/oauth2/public_key` | JWK keys для проверки id_token |
| Logout | `https://id.vk.ru/oauth2/logout` | Выход (отзыв токена) |

### 2.3. Authorization Code Flow

```
┌──────────┐                ┌──────────┐                ┌──────────┐
│  Админ   │                │ Наш сервер│                │  VK ID   │
└────┬─────┘                └────┬─────┘                └────┬─────┘
     │                          │                            │
     │  1. Открыть URL          │                            │
     │─────────────────────────────────────────────────────►│
     │      GET /authorize      │                            │
     │      ?client_id=...      │                            │
     │      &redirect_uri=...   │                            │
     │      &scope=...          │                            │
     │      &response_type=code │                            │
     │      &state=...          │                            │
     │      &code_challenge=... │                            │
     │      &code_challenge_    │                            │
     │       method=S256        │                            │
     │                          │                            │
     │  2. Подтвердить права    │                            │
     │◄─────────────────────────────────────────────────────│
     │      HTML-форма          │                            │
     │                          │                            │
     │  3. Редирект с code      │                            │
     │◄─────────────────────────────────────────────────────│
     │      302 Location:       │                            │
     │      https://oursite/    │                            │
     │      vkIdOAuthCallback?  │                            │
     │      code=XXX&state=YYY  │                            │
     │                          │                            │
     │                          │  4. Обмен code → token    │
     │                          │───────────────────────────►│
     │                          │      POST /oauth2/token    │
     │                          │      grant_type=          │
     │                          │       authorization_code  │
     │                          │      &code=XXX            │
     │                          │      &code_verifier=ZZZ   │
     │                          │      &client_id=...       │
     │                          │      &client_secret=...   │
     │                          │      &redirect_uri=...    │
     │                          │                            │
     │                          │  5. Token response        │
     │                          │◄───────────────────────────│
     │                          │      {access_token,       │
     │                          │       refresh_token,      │
     │                          │       expires_in,         │
     │                          │       id_token}           │
     │                          │                            │
     │  6. Сохранить токены     │                            │
     │◄─────────────────────────│                            │
     │      HTML-страница       │                            │
     │      с подтверждением    │                            │
     │                          │                            │
```

### 2.4. Refresh Token Flow

```
┌──────────┐                ┌──────────┐                ┌──────────┐
│Scheduler │                │ Наш сервер│                │  VK ID   │
└────┬─────┘                └────┬─────┘                └────┬─────┘
     │                          │                            │
     │  1. Триггер (cron)       │                            │
     │─────────────────────────►│                            │
     │                          │                            │
     │                          │  2. POST /oauth2/token    │
     │                          │───────────────────────────►│
     │                          │      grant_type=           │
     │                          │       refresh_token        │
     │                          │      &refresh_token=XXX    │
     │                          │      &client_id=...        │
     │                          │      &client_secret=...    │
     │                          │                            │
     │                          │  3. Token response        │
     │                          │◄───────────────────────────│
     │                          │      {access_token,       │
     │                          │       refresh_token,      │
     │                          │       expires_in,         │
     │                          │       id_token}           │
     │                          │                            │
     │  4. Сохранить            │                            │
     │◄─────────────────────────│                            │
```

## 3. PKCE (Proof Key for Code Exchange)

### 3.1. Зачем нужен

PKCE (RFC 7636) защищает Authorization Code Flow от перехвата кода
авторизации злоумышленником. Без PKCE:

```
Злоумышленник перехватывает code → обменивает на access_token → получает доступ
```

С PKCE:

```
Злоумышленник перехватывает code → обменивает на access_token,
НО не знает code_verifier → VK отклоняет обмен → нет доступа
```

### 3.2. Параметры

| Параметр | Где используется | Описание |
|----------|------------------|-----------|
| `code_verifier` | Сервер генерирует → передаёт в `/token` | Случайная строка 43-128 символов из `[A-Z][a-z][0-9]-._~` |
| `code_challenge` | Сервер генерирует → передаёт в `/authorize` | `base64url(SHA256(code_verifier))` |
| `code_challenge_method` | Сервер передаёт в `/authorize` | `S256` (или `plain`, но `S256` рекомендуется) |

### 3.3. Алгоритм

```kotlin
// 1. Сервер генерирует code_verifier
val codeVerifier = (1..64)
    .map { CHARSET.random() }
    .joinToString("")
// Пример: "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

// 2. Сервер вычисляет code_challenge
val codeChallenge = Base64
    .getUrlEncoder()
    .withoutPadding()
    .encodeToString(MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray()))
// Пример: "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"

// 3. Сервер передаёт в /authorize
val url = "https://id.vk.ru/authorize?" +
    "client_id=$clientId&" +
    "redirect_uri=$redirectUri&" +
    "scope=$scope&" +
    "response_type=code&" +
    "state=$state&" +
    "code_challenge=$codeChallenge&" +
    "code_challenge_method=S256"

// 4. После редиректа сервер передаёт в /token
val params = "grant_type=authorization_code&" +
    "code=$code&" +
    "code_verifier=$codeVerifier&" +  // <-- вот тут!
    "client_id=$clientId&" +
    "client_secret=$clientSecret&" +
    "redirect_uri=$redirectUri"
```

VK ID проверяет, что `SHA256(code_verifier) == code_challenge`. Если не
совпадает — возвращает ошибку `invalid_grant`.

## 4. Где хранить code_verifier между /authorize и /callback

Между `/authorize` (где генерируется code_verifier и передаётся
code_challenge) и `/callback` (где code_verifier нужен для обмена)
проходит несколько секунд/минут. Нужно где-то сохранить code_verifier.

### Варианты

| Вариант | Плюсы | Минусы |
|---------|-------|--------|
| **A. HTTP-сессия (Spring Session)** | Стандартный подход | Требует distributed session (Redis/Hazelcast) |
| **B. KaraokeProperties с TTL** | Просто, работает в KaraokeProperties | Transient, нужно чистить |
| **C. In-memory cache (Caffeine)** | Быстро | Не работает между инстансами (если несколько) |
| **D. Передавать через state** | Не требует хранения | State становится большим, нет CSRF-защиты |

**Выбор**: Вариант B (KaraokeProperties с TTL). Простота важнее
производительности — flow выполняется раз в несколько месяцев.

Транзиентные ключи:

```kotlin
KaraokeProperty(
    key = "vkIdPendingCodeVerifier",
    defaultValue = "",
    description = "Транзиентное значение для PKCE (TTL 10 мин, очищается в callback)",
),
KaraokeProperty(
    key = "vkIdPendingState",
    defaultValue = "",
    description = "Транзиентное значение для CSRF-защиты (TTL 10 мин, очищается в callback)",
),
KaraokeProperty(
    key = "vkIdPendingAt",
    defaultValue = "",
    description = "ISO datetime создания pending-значений (для TTL)",
),
```

## 5. Совместимость токенов VK ID с VK API

### 5.1. Формат access_token

VK ID выдаёт access_token в формате, **совместимом с VK API**:

```
access_token = "<user_id>_<token>"
Пример: "123456_abc123def456..."
```

Этот токен можно использовать в VK API методах:

```
POST https://api.vk.ru/method/photos.getWallUploadServer
?group_id=<group_id>
&access_token=<access_token>
&v=5.199
```

### 5.2. Совместимые методы

VK ID access_token работает для:

- ✅ `photos.*` (getWallUploadServer, saveWallPhoto, getById, ...)
- ✅ `video.*` (save, getById, ...)
- ✅ `wall.*` (post, edit, getById, ...)
- ✅ `users.get` (проверка валидности)
- ✅ `account.*` (управление аккаунтом)

### 5.3. Scope mapping

VK ID scopes → VK API scopes (примерное соответствие):

| VK ID scope | VK API scope | Назначение |
|-------------|--------------|-----------|
| `vkid.personal_info` | (нет аналога) | Базовая информация |
| `photos` | `photos` | Фотографии |
| `wall` | `wall` | Стена |
| `video` | `video` | Видео |
| `email` | (нет аналога) | Email |
| `phone` | (нет аналога) | Телефон |
| `offline` | `offline` | Бессрочный токен |

Для нашего use case нужны: `vkid.personal_info`, `photos`, `wall`, `video`.

## 6. Refresh Token — срок жизни и поведение

### 6.1. Срок жизни

| Параметр | Значение (по доке VK ID) |
|----------|--------------------------|
| `access_token` | 3600 секунд (1 час) |
| `refresh_token` | 31536000 секунд (1 год) |
| `id_token` | 3600 секунд (1 час, JWT exp) |

### 6.2. Ротация refresh_token

VK ID **может ротировать** refresh_token при каждом refresh (best practice).
Это значит, что после каждого refresh нужно сохранять новый `refresh_token`.
Если старый `refresh_token` уже использован — он становится невалидным.

### 6.3. Что делать, если refresh_token истёк

1. `VkIdTokenRefreshScheduler` пытается refresh → получает `error=invalid_grant`.
2. Устанавливает `vkIdRefreshNeeded=true`, `vkIdRefreshLastError`.
3. Логирует WARNING.
4. Бот продолжает работать со старым `access_token` пока не истечёт.
5. Через ~1 час бот перестаёт работать.
6. Админ видит `vkIdRefreshNeeded=true` (через UI или `vkIdTokenStatus`).
7. Админ повторяет Шаг 5 из quickstart.md.

## 7. Альтернативы VK ID (что НЕ выбрали)

### 7.1. Прямой OAuth 2.0 без VK ID

Можно использовать `https://oauth.vk.ru/authorize` с новым приложением.
Но:

- ❌ VK рекомендует переходить на VK ID.
- ❌ Без PKCE (или опционально).
- ❌ Без `refresh_token` (только с scope `offline`, токен бессрочный).
- ❌ Может быть заблокирован так же, как текущее приложение.

### 7.2. Мобильное приложение VK ID

VK ID поддерживает мобильные приложения (Android/iOS). Для них:

- ✅ Можно использовать в Karaoke (через `oauth.vk.ru/blank.html` redirect).
- ❌ Требует отдельного `client_id` для мобильного.

**Не выбрали** — слишком сложно для нашего use case.

### 7.3. Service Account (для ботов)

VK ID не поддерживает service account (для ботов). Только user token.

**Не выбрали** — не подходит.

## 8. Открытые вопросы и решения

| Вопрос | Решение |
|--------|---------|
| Где хранить code_verifier между /authorize и /callback? | KaraokeProperties с TTL (`vkIdPendingCodeVerifier`) |
| Где хранить state для CSRF? | KaraokeProperties с TTL (`vkIdPendingState`) |
| Как часто делать refresh? | Раз в час, если до expires < 30 минут |
| Что делать при ошибке refresh? | `vkIdRefreshNeeded=true`, логирование WARNING |
| Что делать при refresh_token expiry? | UI-индикатор + повторный OAuth flow |
| Поддерживать ли старый oauth.vk.ru? | Только как deprecated (HTTP 410) для обратной совместимости |
| Можно ли использовать несколько VK ID приложений? | В первой версии — нет. Одно приложение. |
| Валидировать ли подпись id_token? | В первой версии — нет (сохраняем как есть). Валидация — backlog. |

## 9. Ссылки

- [VK ID документация](https://id.vk.com/about/business/go/docs/ru/vkid/latest/vk-id/connection/main.html)
- [OAuth 2.0 (RFC 6749)](https://datatracker.ietf.org/doc/html/rfc6749)
- [PKCE (RFC 7636)](https://datatracker.ietf.org/doc/html/rfc7636)
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [VK API: photos.getWallUploadServer](https://dev.vk.com/ru/reference/photos/get-wall-upload-server)
- [VK API: photos.saveWallPhoto](https://dev.vk.com/ru/reference/photos/save-wall-photo)
- [specs/138-vk-photo-preview-attachment/spec.md](../138-vk-photo-preview-attachment/spec.md)
- [specs/121-vk-news-auto-publish/spec.md](../121-vk-news-auto-publish/spec.md)
- [docs/features/vk-news-auto-publish.md](../../docs/features/vk-news-auto-publish.md)