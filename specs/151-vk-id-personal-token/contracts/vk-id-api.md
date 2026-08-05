# API Contract: VK ID Personal Token

**Spec**: [spec.md](./spec.md)

> Контракт endpoints для VK ID OAuth flow. Все endpoints работают через HTTP.

---

## Endpoints

| Endpoint | Метод | Где | Назначение |
|----------|-------|-----|-----------|
| `/api/public/utils/vkIdOAuthUrl` | GET | karaoke-web (прод) | Генерация URL авторизации |
| `/api/public/utils/vkIdOAuthCallback` | GET | karaoke-web (прод) | Обработка редиректа от VK ID |
| `/api/utils/vkIdSaveTokens` | POST | karaoke-app (admin) | Сохранение токенов в Karaoke.properties |
| `/api/utils/vkIdTokenStatus` | GET | karaoke-app (admin) | Состояние токена |
| `/api/utils/vkIdRefreshNow` | POST | karaoke-app (admin) | Принудительный refresh |

> Старые endpoints (`/api/utils/vkOAuthUrl`, `/api/utils/vkOAuthCodeUrl`,
> `/api/public/utils/vkOAuthCodeUrl`, `/api/public/utils/vkOAuthCallback`)
> возвращают **HTTP 410 Gone** (FR-010 спеки).

---

## 1. `GET /api/public/utils/vkIdOAuthUrl`

**Назначение**: генерирует URL для OAuth авторизации через VK ID.

### Запрос

```http
GET /api/public/utils/vkIdOAuthUrl HTTP/1.1
Host: sm-karaoke.ru
Accept: application/json
```

### Ответ (успех)

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "success": true,
  "url": "https://id.vk.ru/authorize?client_id=54704235&redirect_uri=https%3A%2F%2Fsm-karaoke.ru%2Fapi%2Fpublic%2Futils%2FvkIdOAuthCallback&scope=vkid.personal_info+photos+wall+video&response_type=code&state=ABC123XYZ&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256",
  "scopes": "vkid.personal_info photos wall video",
  "clientId": 54704235,
  "redirectUri": "https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback",
  "instructions": [
    "1. Откройте этот URL в браузере от лица администратора группы svoemestokaraoke.",
    "2. Подтвердите все scopes (vkid.personal_info, photos, wall, video).",
    "3. VK ID редиректит на /api/public/utils/vkIdOAuthCallback — endpoint обменивает на токен.",
    "4. Токены сохранятся в Karaoke.properties admin-машины автоматически."
  ]
}
```

### Ответ (ошибка — настройки не заданы)

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "success": false,
  "error": "vk.id.client-id is empty in application.yml"
}
```

### Параметры ответа

| Поле | Тип | Описание |
|------|-----|----------|
| `success` | Boolean | Успех генерации URL |
| `url` | String | URL для авторизации (если `success: true`) |
| `scopes` | String | Список scopes через пробел |
| `clientId` | Long | ID приложения VK ID |
| `redirectUri` | String | Redirect URI |
| `instructions` | List<String> | Инструкции для админа |
| `error` | String | Описание ошибки (если `success: false`) |

---

## 2. `GET /api/public/utils/vkIdOAuthCallback`

**Назначение**: обрабатывает редирект от VK ID после подтверждения прав.
Обменивает `code` на токены, сохраняет в Karaoke.properties.

### Запрос

```http
GET /api/public/utils/vkIdOAuthCallback?code=ABC123&state=XYZ789 HTTP/1.1
Host: sm-karaoke.ru
Accept: text/html
```

### Параметры запроса

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| `code` | String | Да (если нет `error`) | Authorization code от VK ID |
| `state` | String | Да (если нет `error`) | CSRF state (должен совпадать с сохранённым `vkIdPendingState`) |
| `error` | String | Нет | Ошибка от VK ID (например, `access_denied`) |

### Ответ (успех)

```http
HTTP/1.1 200 OK
Content-Type: text/html; charset=UTF-8

<html>
<body style="font-family:sans-serif;padding:40px;max-width:900px">
  <h2 style="color:green">✅ Токен VK ID получен</h2>
  <p><b>access_token</b> (первые 30 символов): <code>123456_abc123def456...</code></p>
  <p><b>user_id:</b> <code>123456</code></p>
  <p><b>expires_in:</b> <code>3600</code> сек</p>
  <p>Состояние: <span style='background:#d4edda;padding:2px 8px;border-radius:4px'>✅ авто-сохранено в Karaoke.properties admin-машины</span></p>
  <hr>
  <h3>Готово к публикации!</h3>
  <p>Токен сохранён в Karaoke.properties. Можно закрыть эту вкладку.</p>
</body>
</html>
```

### Ответ (ошибка VK ID)

```http
HTTP/1.1 200 OK
Content-Type: text/html; charset=UTF-8

<html>
<body>
  <h2>❌ Ошибка авторизации VK ID</h2>
  <p>VK ID вернул: <b>access_denied</b></p>
  <p>Закройте эту вкладку и попробуйте снова.</p>
</body>
</html>
```

### Ответ (state mismatch)

```http
HTTP/1.1 200 OK
Content-Type: text/html; charset=UTF-8

<html>
<body>
  <h2>❌ Ошибка CSRF-защиты</h2>
  <p>state не совпадает. Возможно, ссылка устарела или подделана.</p>
  <p>Откройте эту страницу через /api/public/utils/vkIdOAuthUrl.</p>
</body>
</html>
```

### Ответ (обмен code → token не удался)

```http
HTTP/1.1 200 OK
Content-Type: text/html; charset=UTF-8

<html>
<body>
  <h2>❌ VK ID отверг code</h2>
  <p>error: <b>invalid_grant</b></p>
  <p>Возможно, code уже использован или истёк (TTL ~1 минута).</p>
  <p>Откройте эту страницу через /api/public/utils/vkIdOAuthUrl.</p>
</body>
</html>
```

---

## 3. `POST /api/utils/vkIdSaveTokens` (admin)

**Назначение**: принимает токены от `PublicVkIdAuthController` и сохраняет
в Karaoke.properties.

### Запрос

```http
POST /api/utils/vkIdSaveTokens HTTP/1.1
Host: nsa-i9:8898
Content-Type: application/x-www-form-urlencoded

accessToken=123456_abc123def456ghi789&refreshToken=def456ghi789jkl012&expiresIn=3600&idToken=eyJhbGciOiJSUzI1NiIs...
```

### Параметры запроса

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| `accessToken` | String | Да | Access token от VK ID |
| `refreshToken` | String | Да | Refresh token от VK ID |
| `expiresIn` | Long | Да | Срок жизни access_token (секунды) |
| `idToken` | String | Нет | ID token (JWT) от VK ID |

### Ответ (успех)

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "success": true,
  "userId": 123456,
  "userFirstName": "Иван",
  "userLastName": "Иванов",
  "expiresAt": "2026-08-05T12:34:56Z",
  "message": "Токены сохранены в Karaoke.properties.vkIdAccessToken / vkIdRefreshToken"
}
```

### Ответ (ошибка — токен невалиден)

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "success": false,
  "error": "VK rejected token: 5 User authorization failed"
}
```

### Ответ (ошибка — настройки не заданы)

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "success": false,
  "error": "vkIdClientSecret is empty — задайте в Karaoke.properties"
}
```

### Параметры ответа

| Поле | Тип | Описание |
|------|-----|----------|
| `success` | Boolean | Успех сохранения |
| `userId` | Long | ID пользователя ВКонтакте (если `success: true`) |
| `userFirstName` | String | Имя пользователя |
| `userLastName` | String | Фамилия пользователя |
| `expiresAt` | String | ISO datetime истечения access_token |
| `message` | String | Описание результата |
| `error` | String | Описание ошибки (если `success: false`) |

---

## 4. `GET /api/utils/vkIdTokenStatus` (admin)

**Назначение**: возвращает состояние VK ID токена для мониторинга.

### Запрос

```http
GET /api/utils/vkIdTokenStatus HTTP/1.1
Host: nsa-i9:8898
Accept: application/json
```

### Ответ

```http
HTTP/1.1 200 OK
Content-Type: application/json

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

### Параметры ответа

| Поле | Тип | Описание |
|------|-----|----------|
| `hasClientId` | Boolean | Настроен ли `vkIdClientId` |
| `hasClientSecret` | Boolean | Настроен ли `vkIdClientSecret` |
| `hasAccessToken` | Boolean | Есть ли валидный access_token |
| `hasRefreshToken` | Boolean | Есть ли refresh_token |
| `expiresAt` | String | ISO datetime истечения access_token (пусто если нет) |
| `refreshNeeded` | Boolean | true, если требуется повторная авторизация |
| `lastError` | String | Текст последней ошибки refresh |

---

## 5. `POST /api/utils/vkIdRefreshNow` (admin)

**Назначение**: принудительно вызывает refresh access_token.

### Запрос

```http
POST /api/utils/vkIdRefreshNow HTTP/1.1
Host: nsa-i9:8898
```

### Ответ (успех)

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "success": true,
  "expiresAt": "2026-08-05T13:34:56Z"
}
```

### Ответ (ошибка)

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "success": false,
  "error": "invalid_grant: refresh_token expired or revoked",
  "refreshNeeded": true
}
```

### Параметры ответа

| Поле | Тип | Описание |
|------|-----|----------|
| `success` | Boolean | Успех refresh |
| `expiresAt` | String | Новый ISO datetime истечения access_token |
| `error` | String | Описание ошибки (если `success: false`) |
| `refreshNeeded` | Boolean | true, если требуется повторная авторизация |

---

## VK ID endpoints (внешние)

### Authorization endpoint

```
GET https://id.vk.ru/authorize
  ?client_id={client_id}
  &redirect_uri={redirect_uri}
  &scope={scopes через +}
  &response_type=code
  &state={state}
  &code_challenge={code_challenge}
  &code_challenge_method=S256
```

### Token endpoint

**Authorization Code Flow:**
```
POST https://oauth.vk.ru/access_token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code={code}
&code_verifier={code_verifier}
&client_id={client_id}
&client_secret={client_secret}
&redirect_uri={redirect_uri}
```

**Refresh Token Flow:**
```
POST https://oauth.vk.ru/access_token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token
&refresh_token={refresh_token}
&client_id={client_id}
&client_secret={client_secret}
```

### UserInfo endpoint

```
GET https://id.vk.ru/oauth2/user_info
Authorization: Bearer {access_token}
```

### Response formats

**Token endpoint (успех):**
```json
{
  "access_token": "123456_abc123def456ghi789jkl012mno345pqr678",
  "refresh_token": "def456ghi789jkl012mno345pqr678stu901vwx234",
  "expires_in": 3600,
  "token_type": "Bearer",
  "id_token": "eyJhbGciOiJSUzI1NiIs...",
  "user_id": 123456
}
```

**Token endpoint (ошибка):**
```json
{
  "error": "invalid_grant",
  "error_description": "refresh_token expired or revoked"
}
```

**UserInfo endpoint (успех):**
```json
{
  "user": {
    "user_id": "123456",
    "first_name": "Иван",
    "last_name": "Иванов",
    "email": "ivan@example.com",
    "phone": "+79001234567"
  }
}
```

---

## Коды ошибок

| Код | Описание | Что делать |
|-----|----------|-----------|
| `invalid_request` | Невалидный запрос (например, нет `client_id`) | Проверить параметры |
| `invalid_client` | Невалидный `client_id` или `client_secret` | Проверить настройки VK ID приложения |
| `invalid_grant` | `code` или `refresh_token` невалиден / истёк | Повторить OAuth flow |
| `unauthorized_client` | Приложение не имеет права на этот grant type | Проверить scopes |
| `unsupported_grant_type` | VK ID не поддерживает `grant_type` | Проверить доку VK ID |
| `invalid_scope` | Невалидный scope | Проверить scopes в настройках приложения |

---

## Безопасность

- ⚠️ Все запросы к `/api/utils/*` (admin) должны проходить через
  аутентификацию (basic auth, токен, IP-whitelist). В первой версии —
  только IP-whitelist (admin-машина доступна только из локальной сети).
- ⚠️ `/api/public/utils/vkIdOAuthCallback` доступен **только** на проде
  по HTTPS. VK ID не принимает HTTP redirect_uri.
- ⚠️ Параметр `state` (CSRF) ОБЯЗАТЕЛЬНО проверяется в callback.
  Не отключать.
- ⚠️ PKCE (`code_verifier`, `code_challenge`) ОБЯЗАТЕЛЬНО используется.
  Не отключать.
- ⚠️ Секреты (`client_secret`, `access_token`, `refresh_token`)
  передаются ТОЛЬКО через POST с `Content-Type: application/x-www-form-urlencoded`.
  Не передавать в URL (GET-параметры).
- ⚠️ Секреты НЕ логируются. Только маскированный токен
  (первые 8 + `...` + последние 4 символа) для диагностики.