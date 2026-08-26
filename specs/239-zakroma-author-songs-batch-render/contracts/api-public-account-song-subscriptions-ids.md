# API Contract: `GET /api/public/account/song-subscriptions/ids`

**Branch**: `239-zakroma-author-songs-batch-render` | **Date**: 2026-08-25

## Endpoint

`GET /api/public/account/song-subscriptions/ids`

## Назначение

Возвращает плоский список `id` песен, на которые у пользователя активная персональная подписка (`scope='SONG'`, `status='PAID'`). Используется фронтендом для логики «зелёный vs золотой» плеера без per-row запросов.

## Авторизация

- Требуется заголовок `Authorization: Bearer <km_auth_token>` (опционально — если токена нет, возвращается пустой массив).

## Request

```
GET /api/public/account/song-subscriptions/ids HTTP/1.1
Host: sm-karaoke.ru
Authorization: Bearer <token>
```

Query: нет.
Body: нет.

## Response

### Успех (200 OK)

```json
[42, 43, 44]
```

- Тип ответа: `application/json; charset=utf-8`.
- Тело: JSON-массив `Long[]` (id песен с активной подпиской).
- Пустой массив `[]` если у пользователя нет подписок или он аноним.

### Ошибки

- **401 Unauthorized** — невалидный токен. Body: пусто или `{"error": "invalid_token"}`. Фронт: редирект на `/login`.
- **500 Internal Server Error** — ошибка БД. Body: `{"error": "internal"}`. Фронт: логирует, иконки остаются в «off» до logout/reload.

## SQL

```sql
SELECT id_song
FROM tbl_subscriptions
WHERE site_user_id = :userId
  AND status = 'PAID'
  AND id_song IS NOT NULL
```

**Фильтр `id_song IS NOT NULL`** — отсекает подписки на сайт (`scope='SITE'`, у них нет конкретной песни).

## Сырой JDBC

```kotlin
val ids = mutableListOf<Long>()
database.preparedStatement(
    "SELECT id_song FROM tbl_subscriptions WHERE site_user_id = ? AND status = 'PAID' AND id_song IS NOT NULL",
    listOf(userId)
).use { rs ->
    while (rs.next()) {
        rs.getLong("id_song")?.let { ids.add(it) }
    }
}
return ResponseEntity.ok(ids)
```

## Кеширование

- Сервер: нет кеша (БД-запрос O(1) с индексом `(site_user_id, status)`).
- Клиент: module-level `Set<number>` в `useSongSubscriptions.js`. Инвалидируется при logout (token change).

## Связь с другими endpoint'ами

- `GET /api/public/account/playlists/membership?ids=...` — для не-избранных плейлистов (отдельный канал).
- `GET /api/public/account/favorites/ids` — для избранного (отдельный канал).
- `tbl_subscriptions` — общий источник; фильтр `scope='SONG'` для избранного и `id_song IS NOT NULL` для подписок — по сути одно и то же подмножество.

**Замечание**: если пользователь подписан на песню, она автоматически у него в избранном (один и тот же record). Различие endpoint'ов — семантическое: «избранное» = пользователь явно добавил, «подписка» = пользователь заплатил.

## Примеры

### Пример 1: пользователь с 2 подписками

Request:
```
GET /api/public/account/song-subscriptions/ids HTTP/1.1
Authorization: Bearer eyJ...
```

Response:
```json
[42, 87]
```

### Пример 2: пользователь без подписок

Response:
```json
[]
```

### Пример 3: аноним (нет токена)

Response:
```json
[]
```

## KDoc (для контроллера)

```kotlin
/**
 * GET /api/public/account/song-subscriptions/ids
 *
 * Возвращает плоский список id песен, на которые у текущего пользователя
 * активная персональная подписка (scope='SONG', status='PAID'). Используется
 * фронтом для логики «зелёный vs золотой» плеера в страницах списка песен —
 * без per-row запросов (см. PublicPlayerController.stemsReady для серверной
 * проверки на странице одиночной песни).
 *
 * Endpoint приватный (требует токен), но для анонима возвращает [] — иконки
 * плеера показывают золотую (демо) по умолчанию.
 *
 * @see specs/239-zakroma-author-songs-batch-render
 */
```