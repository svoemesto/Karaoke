# API Contract: `GET /api/public/account/favorites/ids`

**Branch**: `239-zakroma-author-songs-batch-render` | **Date**: 2026-08-25

## Endpoint

`GET /api/public/account/favorites/ids`

## Назначение

Возвращает плоский список `id` песен, которые пользователь добавил в «Избранное». Используется фронтендом для отрисовки иконок `FavoriteIcon` в страницах списка песен (Закрома/Поиск/Плейлист автора) без per-row запросов.

## Авторизация

- Требуется заголовок `Authorization: Bearer <km_auth_token>` (опционально — если токена нет, возвращается пустой массив; иконки показывают «гостевой» вид).

## Request

```
GET /api/public/account/favorites/ids HTTP/1.1
Host: sm-karaoke.ru
Authorization: Bearer <token>
```

Query: нет.
Body: нет.

## Response

### Успех (200 OK)

```json
[123, 456, 789]
```

- Тип ответа: `application/json; charset=utf-8`.
- Тело: JSON-массив `Long[]` (id песен в «Избранном»).
- Пустой массив `[]` если у пользователя нет избранного или он аноним.

### Ошибки

- **401 Unauthorized** — невалидный токен. Body: пусто или `{"error": "invalid_token"}`. Фронт: редирект на `/login`.
- **500 Internal Server Error** — ошибка БД. Body: `{"error": "internal"}`. Фронт: логирует, иконки остаются в «off» до logout/reload.

## SQL

```sql
SELECT id_song
FROM tbl_subscriptions
WHERE site_user_id = :userId
  AND scope = 'SONG'
  AND status = 'PAID'
```

## Сырой JDBC

```kotlin
val ids = mutableListOf<Long>()
database.preparedStatement(
    "SELECT id_song FROM tbl_subscriptions WHERE site_user_id = ? AND scope = 'SONG' AND status = 'PAID'",
    listOf(userId)
).use { rs ->
    while (rs.next()) {
        rs.getLong("id_song")?.let { ids.add(it) }
    }
}
return ResponseEntity.ok(ids)
```

## Кеширование

- Сервер: нет кеша (БД-запрос O(1) с индексом).
- Клиент: module-level `Set<number>` в `usePlaylistMembership.js`. Инвалидируется при `toggleFavorite` или logout.

## Связь с другими endpoint'ами

- `POST /api/public/account/favorites/toggle` — добавляет/убирает песню из избранного, после чего клиент optimistic-update'ит локальный store и broadcast'ит через `BroadcastChannel('km-favorites')`.
- `GET /api/public/account/playlists/membership?ids=...` — существующий endpoint для НЕ-избранных плейлистов (отдельный канал).

## Примеры

### Пример 1: пользователь с 3 избранными

Request:
```
GET /api/public/account/favorites/ids HTTP/1.1
Authorization: Bearer eyJ...
```

Response:
```json
[42, 87, 1234]
```

### Пример 2: пользователь без избранного

Response:
```json
[]
```

### Пример 3: аноним (нет токена)

Request:
```
GET /api/public/account/favorites/ids HTTP/1.1
```

Response:
```json
[]
```

(Сервер не падает на отсутствии токена — возвращает пустой массив.)

## KDoc (для контроллера)

```kotlin
/**
 * GET /api/public/account/favorites/ids
 *
 * Возвращает плоский список id песен в «Избранном» у текущего пользователя.
 * Используется фронтом для bulk-fetch membership на страницах списка песен
 * (Закрома/Поиск/Плейлист автора) — без per-row запросов.
 *
 * Endpoint приватный (требует токен), но для анонима возвращает [] —
 * иконки на фронте показывают «гостевой» вид с редиректом на /login.
 *
 * @see specs/239-zakroma-author-songs-batch-render
 */
```