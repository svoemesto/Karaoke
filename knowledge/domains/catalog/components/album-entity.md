# Component: Album (детальный)

> **Домен**: [catalog](../domain.md)
> **Компонента**: детальное описание `Album` entity.

## Файл

`karaoke-app/.../model/Album.kt`

## Назначение

**Сущность «Альбом»** — отдельная таблица `tbl_albums`. Связи:
- Один автор — много альбомов (через `authorId`).
- Один альбом — много песен (через `Song.albumId`).

## Поля (по `@KaraokeDbTableField`)

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGINT PK | Auto-generated |
| `author_id` | BIGINT NOT NULL | FK на `tbl_authors.id`, **ON DELETE RESTRICT** |
| `year` | INT | Год выпуска |
| `name` | VARCHAR | Название альбома |
| `album_type` | VARCHAR | Тип альбома (хранится как `AlbumType.dbValue` — строка) |
| `sort_order` | INT | Порядок отображения (сквозной, не привязан к году; меньше = раньше) |
| `description` | TEXT | Полное описание |
| `short_description` | TEXT | Короткое описание |
| `warning` | VARCHAR | Предупреждение (например, "архив") |
| ... (ещё поля) | | |

## `albumType` (Enum)

```kotlin
enum class AlbumType {
    STANDARD,    // обычный
    ARCHIVE,     // архивный
    TRIBUTE,     // трибьют
    COVER,       // кавер
    // ...
}
```

**Хранение**: `album_type` хранится как `dbValue` (строка) —
**reflection-слой `KaraokeDbTable` не поддерживает enum-поля напрямую**
(только скалярные типы). Типизированный доступ через
`albumTypeEnum`.

## Sync

Синхронизируется LOCAL↔SERVER через
`GenericKaraokeDbTableSyncTarget<Album>` (`key = "albums"`).

## Hot paths

- **`/api/albums/list`** — список (admin, public).
- **`/api/albums/getById`** — детали.
- **`/api/albums/update`** — редактирование.
- **`/api/albums/sort`** — drag-drop в модалке «Альбомы автора»
  (webvue3, компонент Authors) — переупорядочивает `sort_order`.

## Связь

- [dictionaries.md#albumtype](dictionaries.md) — AlbumType enum.
- [Song entity](song-entity.md) — `Song.albumId`.
- [store-albums.md](../../../system/frontend/store-albums.md) — UI.

## Известные TODO

- [ ] **Все поля** — полный список (Pass 343+).
- [ ] **`Song.albumId` FK** — cascade vs restrict.

## Changelog

- **Pass 426** (2026-09-09): Initial. Автор: agent (Karaoke).