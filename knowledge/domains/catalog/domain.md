---
id: domain-catalog
title: "Domain: Catalog (Каталог)"
status: Active
slug: catalog
related:
  - ../identity/domain.md
  - ../processing/domain.md
  - ../rendering/domain.md
  - ../publishing/domain.md
  - ../../adr/0001-raw-jdbc.md
---

# Domain: Catalog (Каталог)

> Главный домен проекта — каталог песен, альбомов, исполнителей, жанров.
>

## Обзор контекста (Bounded Context)

Catalog — **core domain** проекта Karaoke. Содержит агрегаты вокруг
музыкального каталога: песни, альбомы, исполнители, жанры. На проде
**18k+ записей песен**, большая часть бизнес-логики проекта работает
с этим контекстом.

Контекст **read-heavy** (много чтений с публичного сайта + админки) и
**write-light** (новые песни добавляются партиями по мере обработки
MLT-конвейером).

**Граница**: контекст НЕ отвечает за:

- обработку входных mp3 → стемы (→ [processing](../processing/domain.md));
- рендеринг MP4 из маркеров (→ [rendering](../rendering/domain.md));
- публикацию и подписки (→ [publishing](../publishing/domain.md));
- аутентификацию пользователей (→ [identity](../identity/domain.md)).

## Ubiquitous Language | Единый язык

| Термин | Определение | Пример в коде |
| --- | --- | --- |
| **Песня (Song)** | Единица каталога, AR | `Song.kt`, `tbl_settings` (legacy name) |
| **Альбом (Album)** | Коллекция песен одного исполнителя. Денормализованные счётчики `total_song_count` / `ready_song_count` поддерживаются DB-триггером `trg_tbl_songs_update_album_counts` (Pass 357, миграция 49) — зеркалит паттерн spec 286 для `tbl_authors` | `Album.kt`, `tbl_albums` |
| **Исполнитель (Author)** | Музыкальный исполнитель (НЕ автор текста) | `Author.kt`, `tbl_authors` |
| **Жанр (Genre)** | Музыкальный жанр, справочник | `Genre.kt`, `tbl_genres` |
| **Картинка (Picture)** | Обложка альбома / фото исполнителя | `Picture.kt`, `tbl_pictures` |
| **Спецтег (SpecTag)** | Специальный маркер в lyrics | `SpecTag.kt`, `tbl_settings.spec_tags` |
| **Маркер (Marker)** | Время начала/конца секции караоке | `sourceMarkers` (string) |
| **SKIP** | Тег скрытой песни (заглушка) | `tags` содержит `SKIP` |
| **Эфирная песня** | `publishDate` истёк → доступна всем | `Song.isContentReady()` |
| **Exclusive** | Доступна только по подписке | см. [publishing](../publishing/domain.md) |
| **IdStatus** | Статус обработки 1..6 | `Song.idStatus` |
| **SourceMarkers** | Строка маркеров (формат `[time]text`) | `Song.sourceMarkers` |

Полный словарь магических кодов (`IdStatus`, `SongType`, `SKIP`) — см.
[dictionaries](components/dictionaries.md). Жизненный цикл `Song` по
`idStatus` — см. [song-lifecycle](components/song-lifecycle.md).

## Aggregate Roots

- **Song (Песня)**: единица каталога. Identity = `id` (bigint). Включает
  метаданные (название, исполнитель, длительность), ссылки на альбом/жанр,
  технические поля (`idStatus`, `sourceMarkers`, и т.п.), статус публикации.
  Инварианты:
  - `idStatus` ∈ [1..6];
  - `publishDate <= now()` для эфирных песен;
  - email автора / альбом (если есть) ссылается на существующие AR.

- **Album (Альбом)**: коллекция песен одного исполнителя. Identity = `id`.
  Содержит метаданные альбома, обложку, год выпуска.

- **Author (Исполнитель)**: музыкальный исполнитель. Identity = `id`. Содержит
  имя, описание, фото, ссылку на источник.

- **Genre (Жанр)**: музыкальный жанр, справочник. Identity = `id`.

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

## Domain Invariants | Инварианты и правила бизнеса

1. **`idStatus` ∈ [1..6]**: коды статусов зафиксированы, изменение
   требует миграционного эпика (см. [dictionaries](components/dictionaries.md)).
2. **`SongType` — enum, не string**: тип песни в БД/API — `song` /
   `instrumental` / `poetry`, не произвольные литералы.
3. **Тег `SKIP` нельзя ставить без основания**: песня становится
   невидимой на публичном сайте, но остаётся в БД. Включение/выключение
   `SKIP` логируется в доменное событие `SongSkipped`.
4. **`Author` ≠ автор текста**: в Karaoke `Author` — исполнитель песни
   (Music performer), не lyricist. См. related context [publishing](../publishing/domain.md)
   для подписок на автора.
5. **`SourceMarkers` имеет формат `[time]text`**: парсер строгий, не
   принимает табы / лишние пробелы.

## Публичные контракты (API)

### Internal API (для других модулей)

- `SongService`, `AlbumService`, `AuthorService` — CRUD.
- Чтение через `SongDTO` (admin) и `SongPublicDTO` (публичный).

### Public API (для внешних клиентов)

- Публичный сайт (`karaoke-public`) читает каталог через
  `SongPublicDTO` / `AlbumPublicDTO`.
- Админка (`webvue3`) — полные `SongDTO` / `AlbumDTO`.

## Структура компонентов (C4 L3)

- [dictionaries](components/dictionaries.md) — `IdStatus` (1..6),
  `SongType` enum, тег `SKIP`. Централизованный словарь магических
  кодов домена.
- [song-lifecycle](components/song-lifecycle.md) — переходы `idStatus`
  1→2→3→4→5→6, доменные события `SongStatusChanged` / `SongPublished`,
  роли участников пайплайна.

## Связанные фичи

- `182-editor-self-assign-tasks` — задания редакторов на песни.
- `184-approve-status-choice` — выбор `idStatus` 5/6.
- `185-song-dto-audit-sponsr-remove` — очистка DTO.
- `186-zakroma-songs-fast-load` — оптимизация загрузки.
- [`356-zakroma-albums-by-author`](../../../docs/features/zakroma-albums-by-author.md) — промежуточный этап «Альбомы автора» в навигации `/zakroma`; песни привязаны к альбому через FK `tbl_songs.album_id` (Pass 357); бэкенд фильтрует стрим по `?albumId=N`.
- [`360-editor-sees-all-albums`](../../../specs/360-editor-sees-all-albums/spec.md) — bugfix #76 (Pass 361): редактор на публичном сайте видел только альбомы с готовыми, как гость; root cause — `ZakromaAlbumsView.vue` не передавал `Authorization: Bearer <token>` в fetch (`SiteUserResolver` берёт токен только из заголовка). Бэкенд уже корректен с Pass 360; фикс — только фронт, ~5 строк.

## Связанные ADR

- [0001-raw-jdbc](../../adr/0001-raw-jdbc.md) — сырой JDBC, без JPA/Hibernate.
- [local-0007-zakroma-album-id-in-stream-dto](../../adr/local-0007-zakroma-album-id-in-stream-dto.md) — обязательность `albumId` в `ZakromaAlbumMetaPublicDto` (Pass 359).

## Код (физическая реализация)

- Модели: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/{Song,Album,Author,Genre,Picture}.kt`
- Сервисы: `SongService.kt`, `AlbumService.kt`, `AuthorService.kt`
- DTO: `SongDTO.kt`, `AlbumDTO.kt`, `AuthorDTO.kt`, `SongPublicDTO.kt`, `AlbumPublicDTO.kt`
- SQL: `deploy/karaoke-db/<NNN>_tbl_settings.sql` (legacy name для Song),
  `<NNN>_tbl_albums.sql`, и т.д.
- Тесты: интеграционные (большинство `@Disabled`).
