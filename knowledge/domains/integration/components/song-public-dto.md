# Component: SongPublicDto (детальный)

> **Домен**: integration (API contracts)
> **Компонента**: `SongPublicDto` — главный публичный DTO песни.

## Файл

`karaoke-web/.../dto/SongPublicDto.kt`

## Назначение

**Главный публичный DTO** песни. Сериализуемое представление для
API/UI публичного сайта (`/api/public/song/...`).

## Поля (по `data class`)

| Поле | Тип | Описание |
|---|---|---|
| `id` | Long | ID песни |
| `songName` | String | Название |
| `author` | String | Имя автора |
| `authorId` | Long? | **Числовой ID** автора из `tbl_authors` (FK по имени) — для динамического back-link (`/zakroma/<authorId>`) |
| `authorAlias` | String | Псевдоним (для LLM-поиска) |
| `album` | String | Название альбома |
| `year` | Long | Год |
| `track` | Long | Номер трека |
| `key` | String | Тональность |
| `bpm` | Long | BPM |
| `onAir` | Boolean | В эфире ли |
| `datePublish` | String | Дата публикации |
| `airTimestamp` | Long? | Timestamp эфира |
| `alwaysFree` | Boolean | **Всегда** бесплатный (specs/143-song-free-access-window) |
| `freelyAvailableNow` | Boolean | Доступен ли бесплатно СЕЙЧАС |
| `freeAccessWindowEndText` | String? | Текст "доступно до..." |
| `songPictureUrl` | String | URL обложки (через nginx-proxy) |
| `formattedTextSong` | String | Текст песни (с разметкой) |
| `formattedTextTabs` | String | Табы (гитара) |
| `formattedTextChords` | String | Аккорды |
| `description` | String | Описание |
| `shortDescription` | String | Краткое описание |
| `warning` | String | Предупреждение |
| ... (ещё поля) | | |

## Отличия от admin DTO

- **`authorId`** — есть в публичном (для back-link), нет в admin.
- **`freelyAvailableNow`** — публичное (для UI «золотой/серебряной
  монетки»), нет в admin.
- **`formattedTextSong/Tabs/Chords`** — публичное (для показа на
  странице), admin имеет только `sourceText`.
- **НЕТ** `recordHash`, `isDeleted` — **внутренние** флаги не для
  публичного API.

## Hot paths

- **`/api/public/song/{id}`** — основной endpoint страницы песни.
- **`/api/public/news/...`** — для новостей.
- **`/api/public/zakroma/...`** — для Закромов.

## Связь

- [dtos.md](dtos.md) — общий каталог DTO.
- [song-entity.md](../../catalog/components/song-entity.md) — admin entity.
- [usePlayerAccess.md](../../../system/frontend/composable-use-player-access.md) —
  consumer.

## Известные TODO

- [ ] **Все поля** — полный список (Pass 343+).

## Changelog

- **Pass 440** (2026-09-09): Initial. Автор: agent (Karaoke).