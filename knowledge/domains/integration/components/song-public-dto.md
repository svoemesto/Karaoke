# Component: SongPublicDto (детальный)

> **Домен**: integration (API contracts)
> **Компонента**: `SongPublicDto` — главный публичный DTO песни.


## Ответственность | Responsibility


`SongPublicDto` — главный публичный DTO песни.

## Файл

`karaoke-web/.../dto/SongPublicDto.kt`

## Назначение

**Главный публичный DTO** песни. Сериализуемое представление для
API/UI публичного сайта (`/api/public/song/...`).

## Интерфейсы и Контракты | Interfaces and Contracts

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
| `idVkKaraoke`, `idVkKaraokeOID`, `idVkKaraokeID` | String | VK-идентификаторы караоке (OID/ID — owner/id) |
| `idVkLyrics`, `idVkLyricsOID`, `idVkLyricsID` | String | VK-идентификаторы текста |
| `idVkMelody`, `idVkMelodyOID`, `idVkMelodyID` | String | VK-идентификаторы мелодии |
| `idVkChords`, `idVkChordsOID`, `idVkChordsID` | String | VK-идентификаторы аккордов |
| `contentRemoved` | Boolean | Вычисляется из `Song.tags` (содержит `SKIP`) |
| `songSubscriptionAvailable` | Boolean | Доступна ли отдельная подписка (`Song.idTariff >= 0`) |
| `idStatus` | Long | Статус пайплайна песни (0..7) |
| `contentReady` | Boolean | Зеркало `Song.isContentReady` (spec 261) |
| `albumPictureUrl` | String | URL превью альбома в MinIO (`""` → плейсхолдер) |
| `authorPictureUrl` | String | URL превью автора в MinIO (`""` → плейсхолдер) |
| `assignment` | SongAssignmentBriefDto? | Задание (только self-assign-редакторам, specs/182) |

## Логика и Алгоритмы | Logic and Algorithms

**Маппинг `SongPublicDto.fromSong(s, includeDetails, albumPictureUrl,
authorPictureUrl)`** (по коду `SongPublicDto.kt`):

1. `id`, `songName`, `author`, `album`, `year`, `track`, `key`, `bpm`,
   `onAir`, `datePublish` и все `idVkKaraoke*` / `idVkLyrics*` /
   `idVkMelody*` / `idVkChords*` — копируются из `Song` 1:1.
2. `airTimestamp = s.dateTimePublish?.time` — `Date?` → `Long?`.
3. `alwaysFree = s.free`; `freelyAvailableNow = s.isFreelyAvailableNow`;
   `freeAccessWindowEndText = s.freeAccessWindowEndText`
   (specs/143-song-free-access-window).
4. `songPictureUrl = "/api/public/song-picture/${s.id}"` — URL
   формируется DTO, а не берётся из entity.
5. `contentRemoved` — **вычисляется** из `s.tags`: `split(" ")` →
   `uppercase()` → `contains("SKIP")`.
6. `songSubscriptionAvailable = s.idTariff >= 0` (`0` — тариф по
   умолчанию разрешён, `-1` — автор запретил в карточке песни).
7. `contentReady = s.isContentReady` (spec 261) — зеркало флага entity
   для иконки плеера в результатах поиска.
8. `includeDetails=false` пропускает тяжёлые поля — `formattedTextSong`,
   `formattedTextTabs`, `formattedTextChords`, `description`,
   `shortDescription`, `warning` (пустые строки). Причина: список/поиск
   их не показывает, а `getVKPictureBase64()` может уйти в
   `rootFolder`-фоллбек `Constants.PROJECT_ROOT_FOLDERS`, требующий
   `APP_WORK_ON_SERVER` (в процессе karaoke-web `KaraokeAppService` не
   поднимается).
9. `authorId` заполняется **вне** `fromSong` — в `PublicApiController.song()`
   через `Author.loadIdsByNames([author], db)`; `null`, если запись автора
   удалена из `tbl_authors`.
10. `albumPictureUrl` / `authorPictureUrl` приходят параметрами от
    контроллера (batch-lookup превью), default `""`.
11. `assignment` (`SongAssignmentBriefDto?`) заполняется **только** для
    self-assign-редакторов в `/api/public/song/{id}` (specs/182), для
    остальных — `null` (лишний JOIN не выполняется).

**Сериализация**: Jackson берёт ключ JSON из имени Kotlin-свойства.
Boolean-поля намеренно **без** `is`-префикса (`onAir`, `alwaysFree`,
`freelyAvailableNow`, `contentRemoved`, `contentReady`,
`songSubscriptionAvailable`) — иначе Jackson съел бы префикс и ключ
разошёлся бы с фронтом (инвариант проекта).

### Отличия от admin DTO

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

## Зависимости | Dependencies

- [dtos.md](dtos.md) — общий каталог DTO.
- [song-entity.md](../../catalog/components/song-entity.md) — admin entity.
- [usePlayerAccess.md](../../../system/frontend/composable-use-player-access.md) —
  consumer.

## Известные TODO

- [ ] **Контракт полей `idVk*`** — как именно OID/ID используются фронтом.

## Changelog

- **Pass 484** (2026-09-27, spec `484-knowledge-domain-integration`): секции приведены к шаблону. Автор: agent (Karaoke).
- **Pass 440** (2026-09-09): Initial. Автор: agent (Karaoke).