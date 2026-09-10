# Data Model: 414 Request-URI Too Large — POST /playlists/membership

> **Date**: 2026-09-10
> **Spec**: [spec.md](./spec.md)

## Новые entities

### `MembershipRequest` (новый, request DTO)

| Поле | Тип | Описание | Валидация |
|---|---|---|---|
| `ids` | `List<Long>` | Список ID песен, для которых запрашивается membership | Не `null`. Пустой список = `{"items": {}}` (валидно). Без верхнего лимита (нет смысла, см. Edge Cases). |

**Файл**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/dtos/MembershipRequest.kt`

**Kotlin**:
```kotlin
data class MembershipRequest(
    @JsonProperty("ids")
    val ids: List<Long>,
)
```

**Используется только в** `PublicPlaylistController.membershipPost()`.

## Существующие entities (без изменений)

### `SiteUser`

| Поле | Тип | Использование |
|---|---|---|
| `id` | Long | PK |
| `isEffectivePremium` | Boolean | Влияет на `visibleNonFav` (FR-013 спеки #239): для премиума — все не-избранные плейлисты, для остальных — `emptySet()` |
| (прочие) | ... | Не используются в membership endpoint |

**Источник**: устанавливается в `request.siteUser` через `SiteAuthInterceptor`
(см. `SiteAuthInterceptor.kt:33`).

### `SitePlaylist`

| Поле | Тип | Использование |
|---|---|---|
| `id` | Long | PK, используется как ключ в `playlistIds` ответа |
| `ownerId` | Long | Фильтр по `user.id` (только свои плейлисты) |
| `isFavorites` | Boolean | Определяет, какие плейлисты показывать в `favorited` |

**Сущность** уже существует, изменений **не требуется**.

### `SitePlaylistItem`

| Поле | Тип | Использование |
|---|---|---|
| `playlistId` | Long | FK → `SitePlaylist.id` |
| `songId` | Long | FK → `Song.id`, ключ membership |

**Бизнес-метод**: `SitePlaylistItem.songIdsInPlaylists(playlistIds: List<Long>, db)`
— используется без изменений, возвращает `Map<Long, List<Long>>`
(songId → playlistIds).

## Существующие таблицы БД (без миграций)

### `tbl_site_playlist_items`

```sql
CREATE TABLE tbl_site_playlist_items (
  id BIGSERIAL PRIMARY KEY,
  playlist_id BIGINT NOT NULL,
  song_id BIGINT NOT NULL,
  position INT,
  ...
);
```

**Без изменений**. Используется для `WHERE song_id IN (...)` запроса.

### `tbl_site_playlists`

```sql
CREATE TABLE tbl_site_playlists (
  id BIGSERIAL PRIMARY KEY,
  owner_id BIGINT NOT NULL,
  name TEXT,
  is_favorites BOOLEAN NOT NULL DEFAULT FALSE,
  ...
);
```

**Без изменений**.

## Response DTO (без изменений)

```json
{
  "items": {
    "22982": {
      "favorited": true,
      "playlistIds": [12345, 12346]
    },
    "22983": {
      "favorited": false,
      "playlistIds": []
    }
  }
}
```

**Контракт не меняется** между GET и POST — фронт получит тот же
формат. Это сознательное решение для минимизации изменений в `usePlaylistMembership.js`.

## State Transitions

Нет state transitions (read-only endpoint). Endpoint только читает
существующие данные, не модифицирует БД.

## Relationships

```
SiteUser (1) ───< (owns) ─── SitePlaylist (N)
                                  │
                                  └──< (contains) ── SitePlaylistItem (N)
                                                         │
                                                         └──> (song) ── Song
```

Для membership endpoint используется только owner_id (фильтр плейлистов)
и связь playlist→items (для membership map). Изменений в схеме нет.

## Validation Rules

| Условие | Поведение |
|---|---|
| `ids: null` | Spring бросает `HttpMessageNotReadableException` → 400 Bad Request (валидно). |
| `ids: []` | Сервер возвращает `{"items": {}}` с HTTP 200 (FR-008 спеки). |
| `ids: [1, 2, 1]` (дубликаты) | Дедупликация на бэке (FR-007 спеки). |
| `ids: [0, -1, 999999999999]` | Невалидные id фильтруются на бэке: `request.ids.filter { it > 0 }`. |
| `ids: <5000 элементов>` | OK, ≤ ~25 КБ JSON body. |
| `ids: <50000 элементов>` | Теоретически OK (~250 КБ JSON), но nginx `client_max_body_size = 1m` может ограничить. **Не предмет спеки** (за пределами сценария). |