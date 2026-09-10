# API Contract: `/api/public/account/playlists/membership`

> **Date**: 2026-09-10
> **Spec**: [spec.md](../spec.md)
> **Issue**: OpenProject #77

Два метода для одного endpoint'а (path collision):

1. **GET `/api/public/account/playlists/membership`** — **DEPRECATED** (legacy, backward-compat).
   Сохранён для клиентов с маленькими `ids` (≤ 500). На больших `ids`
   (> 1350 id) прокси/браузер вернёт 414 — это **не** наша забота,
   клиент должен переключиться на POST.

2. **POST `/api/public/account/playlists/membership`** — **NEW, рекомендуемый**.
   Без лимита на размер `ids` (ограничен только `nginx client_max_body_size`,
   по умолчанию 1m). Используется фронтом `karaoke-public` начиная с
   этой версии.

Оба метода возвращают **идентичный** JSON-ответ.

---

## POST (NEW, рекомендуемый)

### Request

```
POST /api/public/account/playlists/membership HTTP/1.1
Host: karaoke.svoemesto.ru
Authorization: Bearer <JWT>
Content-Type: application/json
Content-Length: ~20000

{
  "ids": [22982, 22983, 22984, ..., 25512]
}
```

| Поле | Тип | Описание |
|---|---|---|
| `Authorization` | header | `Bearer <JWT>` (обязательно) |
| `Content-Type` | header | `application/json` (обязательно) |
| `ids` | `number[]` | Список ID песен (обязательно). Дубликаты дедуплицируются. Пустой массив = пустой ответ. |

### Responses

#### 200 OK — успех

```json
{
  "items": {
    "22982": {
      "favorited": true,
      "playlistIds": [12345]
    },
    "22983": {
      "favorited": false,
      "playlistIds": [12346, 12347]
    },
    "22984": {
      "favorited": false,
      "playlistIds": []
    }
  }
}
```

| Поле ответа | Тип | Описание |
|---|---|---|
| `items` | `object` | Map: songId (string) → membership |
| `items.<songId>.favorited` | `boolean` | Песня в «Избранном» у текущего пользователя |
| `items.<songId>.playlistIds` | `number[]` | ID не-избранных плейлистов пользователя, содержащих эту песню. Премиум видит свои премиум-плейлисты, остальные — только «Избранное». |

**Гарантии**:
- Для анонимов (без токена) — `request.siteUser = null` → `currentUser(request)` бросает исключение → 401 Unauthorized (FR-010 спеки #239 / `SiteAuthInterceptor`).
- Пустой `ids` → `{"items": {}}` с HTTP 200.
- `ids` с дубликатами → дедуплицируются на бэке.
- `ids` с невалидными id (≤ 0, не-числа) → тихо игнорируются.

#### 400 Bad Request — некорректный JSON

```json
{
  "error": "malformed_json",
  "message": "Cannot deserialize MembershipRequest: ids field is required"
}
```

Возникает если: JSON невалидный, поле `ids` отсутствует, `ids` не массив.

#### 401 Unauthorized — нет/битый JWT

```json
{"error":"unauthorized"}
```

(`SiteAuthInterceptor` отвечает ДО контроллера.)

#### 413 Payload Too Large — body превышает nginx `client_max_body_size`

Возникает при очень большом `ids` (> ~50000). Не наша ответственность — клиент должен chunked POST (вне scope этой спеки).

#### 500 Internal Server Error — БД/прочие ошибки

```json
{
  "error": "internal_error",
  "message": "..."
}
```

---

## GET (DEPRECATED, backward-compat)

### Request

```
GET /api/public/account/playlists/membership?ids=22982,22983,22984 HTTP/1.1
Host: karaoke.svoemesto.ru
Authorization: Bearer <JWT>
```

| Поле | Тип | Описание |
|---|---|---|
| `Authorization` | header | `Bearer <JWT>` (обязательно) |
| `ids` | query | CSV songId (обязательно). |

**Лимит**: URL ≤ 8 КБ (nginx `large_client_header_buffers`).
При превышении nginx возвращает **414 Request-URI Too Large** — клиент
должен переключиться на POST.

### Responses

Идентичны POST (см. выше).

### Отличия от POST

- Параметры передаются через query-string (CSV) вместо JSON body.
- Лимит ~1350 id (средний id = 5 цифр, 6 байт с запятой).
- Для авторизованных пользователей — возвращает то же, что POST.
- **DEPRECATED** — новый фронт использует POST.

---

## Authentication

Оба метода требуют JWT в `Authorization: Bearer <token>` header
(см. `SiteAuthInterceptor.kt:25`). Cookie-based сессия не
поддерживается, CSRF невозможен by design.

---

## Backward-compat стратегия

| Период | GET | POST |
|---|---|---|
| **Сейчас** (после этого PR) | Работает, DEPRECATED-предупреждение в логах | Работает, основной |
| **+1 месяц** | Работает, WARN-лог на каждый вызов | Основной |
| **+2-3 месяца** | Можно удалить (если `membership_get_calls_total` < 1% от POST) | Основной |

Решение об удалении GET принимается владельцем по метрике.

---

## Curl примеры

### POST (рекомендуемый)

```bash
curl -X POST https://karaoke.svoemesto.ru/api/public/account/playlists/membership \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{"ids":[22982,22983,22984]}'
```

### GET (legacy)

```bash
curl -X GET "https://karaoke.svoemesto.ru/api/public/account/playlists/membership?ids=22982,22983" \
  -H "Authorization: Bearer $JWT"
```

### Ожидаемый ответ

```json
{
  "items": {
    "22982": {"favorited": true, "playlistIds": []},
    "22983": {"favorited": false, "playlistIds": []}
  }
}
```

---

## Frontend integration

**Файл**: `karaoke-public/src/services/playlistApi.js`

**Было** (GET):
```javascript
export function fetchMembership(ids) {
  return authGet(`${BASE}/playlists/membership${qs({ ids: ids.join(',') })}`, token())
}
```

**Стало** (POST):
```javascript
export function fetchMembership(ids) {
  return authPostJson(`${BASE}/playlists/membership`, { ids }, token())
}
```

**Файл**: `karaoke-public/src/services/authApi.js`

**Добавить**:
```javascript
export function authPostJson(path, jsonBody, token) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', path, true)
    xhr.setRequestHeader('Content-Type', 'application/json')
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.onload = () => {
      let body = null
      if (xhr.response) {
        try { body = JSON.parse(xhr.response) } catch (e) { body = null }
      }
      resolve({ status: xhr.status, body })
    }
    xhr.onerror = () => reject(new Error('network_error'))
    xhr.send(JSON.stringify(jsonBody))
  })
}
```

(Не трогать существующий `authPost` — используется в 10+ местах для form-encoded POST.)