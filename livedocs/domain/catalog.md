---
status: Active
slug: catalog
type: bounded-context
related:
  - ../features/182-editor-self-assign-tasks.md
  - ../features/184-approve-status-choice.md
  - ../features/185-song-dto-audit-sponsr-remove.md
  - ../features/186-zakroma-songs-fast-load.md
  - ../architecture/L3-components.md
---

# Bounded Context: catalog (Каталог)

> Главный домен проекта — каталог песен, альбомов, исполнителей, жанров.

## Назначение

Catalog — core domain проекта Karaoke. Содержит агрегаты вокруг музыкального
каталога: песни, альбомы, исполнители, жанры. На проде 18k+ записей песен,
большая часть бизнес-логики проекта работает с этим контекстом.

Контекст **read-heavy** (много чтений с публичного сайта + админки) и
**write-light** (новые песни добавляются партиями по мере обработки MLT-конвейером).

## Aggregate Roots

- **Song (Песня)**: единица каталога. Identity = `id` (bigint). Включает
  метаданные (название, исполнитель, длительность), ссылки на альбом/жанр,
  технические поля (idStatus, sourceMarkers, и т.п.), статус публикации.
  Инварианты: `idStatus` ∈ [1..6], `publishDate <= now()` для эфирных песен.

- **Album (Альбом)**: коллекция песен одного исполнителя. Identity = `id`.
  Содержит метаданные альбома, обложку, год выпуска.

- **Author (Исполнитель)**: музыкальный исполнитель. Identity = `id`. Содержит
  имя, описание, фото, ссылку на источник.

- **Genre (Жанр)**: музыкальный жанр. Identity = `id`. Справочник.

## Entities

- **Picture (Картинка)**: хранит превью/full URL для альбомов/исполнителей.
  MinIO storage.
- **MltTag (Спецтег)**: теги в lyrics (VERSE, CHORUS, BRIDGE, ...).
- **SpecTag (Спектег)**: special markers в lyrics (intro, solo, etc.).

## Value Objects

- **SongType (song | instrumental | poetry)**: тип песни (см. AGENTS.md).
- **Tags (set of strings)**: теги песни (включая `SKIP` для скрытых).
- **IdStatus (1..6)**: статус обработки (1=новая, 6=готова).
- **SourceMarkers (string)**: маркеры для караоке-плеера.
- **PublishDate / PublishTime**: когда песня становится доступной публично.

## Domain Events

- **SongAdded**: новая песня добавлена в каталог.
- **SongUpdated**: метаданные или маркеры изменены.
- **SongStatusChanged**: `idStatus` перешёл (1→2→3→4→5→6).
- **SongPublished**: `publishDate` истёк, песня стала эфирной.
- **AlbumPublished**: альбом опубликован.
- **SongSkipped**: добавлен тег `SKIP`, песня скрыта.

## Ubiquitous Language (глоссарий)

| Термин | Определение | Пример в коде |
|--------|-------------|----------------|
| **Песня (Song)** | Единица каталога, AR | `Song.kt`, `tbl_settings` (legacy name) |
| **Альбом (Album)** | Коллекция песен одного исполнителя | `Album.kt`, `tbl_albums` |
| **Исполнитель (Author)** | Музыкальный исполнитель (НЕ автор текста) | `Author.kt`, `tbl_authors` |
| **Жанр (Genre)** | Музыкальный жанр, справочник | `Genre.kt`, `tbl_genres` |
| **Картинка (Picture)** | Обложка альбома / фото исполнителя | `Picture.kt`, `tbl_pictures` |
| **Спецтег (SpecTag)** | Специальный маркер в lyrics | `SpecTag.kt`, `tbl_settings.spec_tags` |
| **Маркер (Marker)** | Время начала/конца секции караоке | `sourceMarkers` (string) |
| **SKIP** | Тег скрытой песни (заглушка) | `tags` содержит `SKIP` |
| **Эфирная песня** | `publishDate` истёк → доступна всем | `Song.isContentReady()` |
| **Exclusive** | Доступна только по подписке | см. publishing context |
| **IdStatus** | Статус обработки 1..6 | `Song.idStatus` |
| **SourceMarkers** | Строка маркеров (формат `[time]text`) | `Song.sourceMarkers` |

## Связанные фичи

- [182-editor-self-assign-tasks.md](../features/182-editor-self-assign-tasks.md) — задания редакторов на песни
- [184-approve-status-choice.md](../features/184-approve-status-choice.md) — выбор idStatus 5/6
- [185-song-dto-audit-sponsr-remove.md](../features/185-song-dto-audit-sponsr-remove.md) — очистка DTO
- [186-zakroma-songs-fast-load.md](../features/186-zakroma-songs-fast-load.md) — оптимизация загрузки

## Связанные LiveDocs

- Architecture: [L3-components.md](../architecture/L3-components.md) (где живёт код контекста)
- Domain: [publishing.md](publishing.md) (связь с публикацией)

## Код

- Модели: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`, `Album.kt`, `Author.kt`, `Genre.kt`, `Picture.kt`
- Сервисы: `SongService.kt`, `AlbumService.kt`, `AuthorService.kt`
- DTO: `SongDTO.kt`, `AlbumDTO.kt`, `AuthorDTO.kt`, `SongPublicDTO.kt`, `AlbumPublicDTO.kt`
- SQL: `deploy/karaoke-db/<NNN>_tbl_settings.sql` (legacy name для Song), `<NNN>_tbl_albums.sql`, и т.д.
- Тесты: интеграционные (большинство `@Disabled`)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14