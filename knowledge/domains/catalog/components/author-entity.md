# Component: Author (детальный)

> **Домен**: [catalog](../domain.md)
> **Компонента**: детальное описание `Author` entity.

## Файл

`karaoke-app/.../model/Author.kt`

## Назначение

**Сущность «Автор» (исполнитель)** — отдельная таблица `tbl_authors`.
**Один автор — много песен** (для переиспользования).

## Поля (по `@KaraokeDbTableField`)

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGINT PK | Auto-generated |
| `author` | VARCHAR NOT NULL | Имя автора |
| `ym_id` | VARCHAR | **ID на Яндекс.Музыке** (для парсинга) |
| `vk_id` | VARCHAR | **ID в VK** (для парсинга) |
| `last_album_ym` | VARCHAR | ID последнего альбома (YM) |
| `last_album_vk` | VARCHAR | ID последнего альбома (VK) |
| `last_album_processed` | TIMESTAMP | Время последней обработки альбома |
| `watched` | BOOLEAN | Наблюдаемый ли (отслеживаются ли новые альбомы) |
| `skip` | BOOLEAN | Пропускать ли при обработке |
| `aliases` | TEXT | Псевдонимы (для LLM-поиска) |
| `description` | TEXT | Полное описание |
| `short_description` | TEXT | Короткое описание |
| `warning` | VARCHAR | Предупреждение (например, "автор отозвал согласие") |
| `is_special_order` | BOOLEAN | Спецзаказ (см. [editorial domain](../../editorial/domain.md)) |
| `sort_order` | INT | Порядок отображения |

## Sync

Синхронизируется LOCAL↔SERVER через `SyncTarget<Author>`.

## Hot paths

- **`/api/authors/list`** — список.
- **`/api/authors/getById`** — детали.
- **`/api/authors/withnewalbumcount`** — бейдж (см.
  [store-authors.md](../../../system/frontend/store-authors.md)).
- **Парсинг YM/VK** — по `ym_id` / `vk_id`.

## LLM-поиск (specs/llm-lyrics-search)

`aliases` — для LLM-поиска текстов песен. Автор может иметь
несколько имён/псевдонимов; LLM учитывает все.

## Связь

- [Song entity](song-entity.md) — `Song.author` (строковое имя, не FK).
- [Album entity](album-entity.md) — `Album.authorId` (FK).
- [store-authors.md](../../../system/frontend/store-authors.md) — UI.

## Известные TODO

- [ ] **Полный список полей** (Pass 343+).

## Changelog

- **Pass 427** (2026-09-09): Initial. Автор: agent (Karaoke).