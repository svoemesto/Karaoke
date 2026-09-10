# Data Model: Редактор видит все альбомы автора (issue #76)

**Дата**: 2026-09-10
**Branch**: `360-editor-sees-all-albums`
**Spec**: [spec.md](./spec.md)

## Сущности

Этот фикс НЕ вводит новых сущностей и НЕ меняет существующую модель данных. Изменения — только в потоке авторизации на фронте. Ниже — справка по затронутым сущностям для контекста.

### Album (затронута косвенно)

**Файл**: `karaoke-app/.../model/Album.kt`
**Identity**: `id: BIGINT PK` (auto-generated)
**Связи**:
- `author_id: BIGINT NOT NULL` → `tbl_authors.id`
- `id` ↔ `tbl_songs.album_id` (one-to-many)

**Релевантные поля** (для этого фикса):
| Поле | Тип | Описание | Использование |
|---|---|---|---|
| `id` | BIGINT | PK | Фильтрация по `author_id` |
| `author_id` | BIGINT | FK на автора | WHERE в `loadAlbumTilesWithCounts` |
| `total_song_count` | INTEGER | Общее кол-во песен | Фильтр `> 0` для редактора, подпись «N песен» |
| `ready_song_count` | INTEGER | Кол-во готовых (id_status >= 6) | Фильтр `> 0` для гостя, подпись «N готовых» |
| `year` | INT | NULL допустим | Сортировка ASC NULLS LAST |
| `name` | VARCHAR | — | Сортировка ASC (тай-брейкер) |
| `album_type` | VARCHAR | STANDARD/ARCHIVE/TRIBUTE/COVER/... | Бейдж в UI |
| `picture_full` | VARCHAR | MinIO ключ обложки | URL в `pictureUrl` |

**Изменения в этом фиксе**: НЕТ.

### SiteUser (затронута косвенно)

**Файл**: `karaoke-app/.../model/SiteUser.kt`
**Identity**: `id: BIGINT PK`

**Релевантные поля**:
| Поле | Тип | Описание | Использование |
|---|---|---|---|
| `id` | BIGINT | PK | — |
| `is_editor` | BOOLEAN | Может работать как редактор | Определяет `onlyPublished = !isEditor` |
| `token` | VARCHAR | Bearer-токен | Резолвится через `Authorization: Bearer <token>` |
| `can_self_assign_tasks` | BOOLEAN | — | НЕ используется в этом фиксе |
| `can_work_with_skipped` | BOOLEAN | — | Используется для skip-фильтра (НЕ затрагивается этим фиксом) |

**Изменения в этом фиксе**: НЕТ.

### AlbumTilePublicDto (НЕ меняется)

**Файл**: `karaoke-web/.../dto/AlbumTilePublicDto.kt`

| Поле | Тип | Описание |
|---|---|---|
| `id` | Long | `tbl_albums.id` |
| `name` | String | Название альбома |
| `year` | Int? | Год (null допустим) |
| `pictureUrl` | String | URL обложки (MinIO) |
| `albumType` | String | STUDIO/SINGLE/LIVE/COMPILATION/BOOTLEG/ARCHIVE/TRIBUTE |
| `totalSongCount` | Int | Общее кол-во песен |
| `readySongCount` | Int | Кол-во готовых |
| `sortOrder` | Int? | На будущее |

**Изменения в этом фиксе**: НЕТ.

## Data Flow (до и после фикса)

### До фикса (баг)

```
1. Браузер (редактор, залогинен) открывает /zakroma/{id}/albums
2. ZakromaAlbumsView.vue → fetch(GET /api/public/authors/{id}/albums, { credentials: 'include' })
3. Сервер (PublicApiController.authorAlbums):
   a. onlyPublishedFor(request) → siteUserResolver.resolve(request) → null (нет Authorization-header)
   → onlyPublished = true (НЕПРАВИЛЬНО: редактор должен быть false)
   b. Album.loadAlbumTilesWithCounts(authorId, onlyPublished=true)
   → SQL: ... WHERE author_id = ? AND ready_song_count > 0
4. Ответ: только альбомы с готовыми песнями (НЕПРАВИЛЬНО)
5. Браузер: ZakromaAlbumsView отображает подмножество → баг #76
```

### После фикса

```
1. Браузер (редактор, залогинен) открывает /zakroma/{id}/albums
2. ZakromaAlbumsView.vue → fetch(GET /api/public/authors/{id}/albums, {
     credentials: 'include',
     headers: { Authorization: 'Bearer <token из localStorage>' }
   })
3. Сервер:
   a. onlyPublishedFor(request) → siteUserResolver.resolve(request) → SiteUser (isEditor=true)
   → onlyPublished = false (ПРАВИЛЬНО)
   b. Album.loadAlbumTilesWithCounts(authorId, onlyPublished=false)
   → SQL: ... WHERE author_id = ? (без фильтра по count)
4. Ответ: все альбомы (ПРАВИЛЬНО)
5. Браузер: ZakromaAlbumsView отображает все альбомы с подписью «N песен» → OK
```

### Для гостя (после фикса — без изменений)

```
1. Браузер (анонимный) → fetch без Authorization (нет токена в localStorage)
2. Сервер: siteUserResolver.resolve → null → onlyPublished = true (как раньше)
3. SQL: WHERE ready_song_count > 0 (как раньше)
4. Ответ: только альбомы с готовыми (как раньше, без изменений)
```

## Валидация

| Проверка | Метод |
|---|---|
| Миграция 49 не задета | `git diff deploy/karaoke-db/49_albums_song_counts.sql` — пусто |
| `tbl_albums.skip` НЕ добавлен | `\d tbl_albums` в psql — нет колонки `skip` (Pass 357 ещё не сделал) |
| Триггер `trg_tbl_songs_update_album_counts` не задет | `git diff` по `49_albums_song_counts.sql` — пусто |
| `AlbumTilePublicDto` поля не задеты | `git diff karaoke-web/.../dto/AlbumTilePublicDto.kt` — пусто |
| `Album.loadAlbumTilesWithCounts` не задет | `git diff karaoke-app/.../model/Album.kt` — пусто (только для этого issue; другие правки Album — отдельный PR) |