# Data Model: Аудит публичного DTO песни

**Дата**: 2026-08-14
**Spec**: [spec.md](./spec.md)
**Research**: [research.md](./research.md)

## Резюме

Data model меняется МИНИМАЛЬНО. Этот документ фиксирует финальный набор полей трёх затронутых data-классов **ПОСЛЕ** рефакторинга:

1. **`SongPublicDto`** (karaoke-web) — публичный DTO отдельной песни.
2. **`ZakromaAlbumSongPublicDto`** (karaoke-web) — публичный DTO песни в альбоме (Закрома / поиск / стрим).
3. **`ZakromaAlbumSong`** (karaoke-app, модель) — внутренний класс, используется только для построения `ZakromaAlbumSongPublicDto`.

Поля, которые НЕ входят ни в один из этих классов, не трогаем:

- **`Song.kt`** (модель поверх `tbl_songs`) — НЕ меняется (нужна админке и ботам публикации).
- **`tbl_songs` / `tbl_songs_sync`** (БД; ранее называлась `tbl_settings`, см. `28_rename_settings_to_songs.sql`) — НЕ меняется (нужны админке и ботам публикации).

## 1. SongPublicDto (после рефакторинга)

**Файл**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt`

**Удалено** (относительно текущего состояния, ~50 полей):

- `sponsrLinkGeneral` — захардкожен `"https://sponsr.ru/smkaraoke"`, нигде не читается фронтом.
- `haveVkGroupLink` — используется только legacy `testpage.html` (нужен для картинки в testpage.html, остаётся в `Song.kt` и в шаблоне).
- `idStatus` — используется только legacy `testpage.html` (нужен для условия в `testpage.html:300`).
- `vkPictureBase64` — всегда `""`, фронт подгружает `songPictureUrl` сам.
- `linkSponsrPlay, linkBoostyTxt` (2 поля).
- `linkDzenKaraoke, linkDzenLyrics, linkDzenTabs, linkDzenChords` (4 поля).
- `linkVkKaraoke, linkVkLyrics, linkVkTabs, linkVkChords` (4 поля).
- `linkTgKaraoke, linkTgLyrics, linkTgTabs, linkTgChords` (4 поля).
- `linkMaxKaraoke, linkMaxLyrics, linkMaxTabs, linkMaxChords` (4 поля).
- `linkPlKaraoke, linkPlLyrics, linkPlTabs, linkPlChords` (4 поля).

**Остаётся** (~25 полей):

```kotlin
data class SongPublicDto(
    val id: Long,
    val songName: String,
    val author: String,
    val authorAlias: String = "",
    val album: String,
    val year: Long,
    val track: Long,
    val key: String,
    val bpm: Long,
    val onAir: Boolean,
    val datePublish: String,
    val airTimestamp: Long?,
    // specs/143-song-free-access-window
    val alwaysFree: Boolean,
    val freelyAvailableNow: Boolean,
    val freeAccessWindowEndText: String?,
    val songPictureUrl: String,
    val formattedTextSong: String,
    val formattedTextTabs: String,
    val formattedTextChords: String,
    val description: String = "",
    val shortDescription: String = "",
    val warning: String = "",
    // VK video embeds (только ID-поля, не текстовые ссылки)
    val idVkKaraoke: String,
    val idVkKaraokeOID: String,
    val idVkKaraokeID: String,
    val idVkLyrics: String,
    val idVkLyricsOID: String,
    val idVkLyricsID: String,
    val idVkMelody: String,
    val idVkMelodyOID: String,
    val idVkMelodyID: String,
    val idVkChords: String,
    val idVkChordsOID: String,
    val idVkChordsID: String,
    val contentRemoved: Boolean,
    val songSubscriptionAvailable: Boolean,
    // specs/182-editor-self-assign-tasks (только для self-assign-редакторов, иначе null)
    val assignment: SongAssignmentBriefDto? = null,
)
```

**Конвертер `fromSong`** (после рефакторинга):
- `sponsrLinkGeneral = "https://sponsr.ru/smkaraoke"` → УДАЛИТЬ.
- `haveVkGroupLink = s.haveVkGroupLink` → УДАЛИТЬ.
- `idStatus = s.idStatus` → УДАЛИТЬ.
- `vkPictureBase64 = ""` → УДАЛИТЬ.
- `linkSponsrPlay = s.linkSponsrPlay` → УДАЛИТЬ.
- `linkBoostyTxt = s.linkBoostyTxt` → УДАЛИТЬ.
- `linkDzen*` → УДАЛИТЬ (4 строки).
- `linkVk*` → УДАЛИТЬ (4 строки).
- `linkTg*` → УДАЛИТЬ (4 строки).
- `linkMax*` → УДАЛИТЬ (4 строки).
- `linkPl*` → УДАЛИТЬ (4 строки).
- Остальные строки — без изменений.

**Связь с другими сущностями**:
- `SongAssignmentBriefDto` (импортируется) — НЕ меняется в этой спеке (см. specs/182).
- `Song` (импортируется) — НЕ меняется.

## 2. ZakromaAlbumSongPublicDto (после рефакторинга)

**Файл**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaPublicDto.kt`

**Удалено** (относительно текущего состояния, 21 поле):

- `linkBoosty`
- `linkSponsrPlay`
- `linkDzenKaraoke, linkDzenLyrics, linkDzenTabs, linkDzenChords` (4)
- `linkVkKaraoke, linkVkLyrics, linkVkTabs, linkVkChords` (4)
- `linkTgKaraoke, linkTgLyrics, linkTgTabs, linkTgChords` (4)
- `linkPlKaraoke, linkPlLyrics, linkPlTabs, linkPlChords` (4)
- `linkMaxKaraoke, linkMaxLyrics, linkMaxTabs, linkMaxChords` (4)

**Остаётся** (10 полей):

```kotlin
data class ZakromaAlbumSongPublicDto(
    val id: Long,
    val track: Long,
    val songName: String,
    val onAir: Boolean,
    val datePublish: String,
    val airTimestamp: Long?,
    val songSubscriptionAvailable: Boolean,
    val alwaysFree: Boolean,
    val freelyAvailableNow: Boolean,
    val freeAccessWindowEndText: String?,
)
```

**Конвертер `fromZakroma`** (после рефакторинга): удалить строки с `linkBoosty = ...`, `linkSponsrPlay = ...`, `linkDzen* = ...`, `linkVk* = ...`, `linkTg* = ...`, `linkPl* = ...`, `linkMax* = ...` (21 строка).

**Inline-конструктор в `PublicApiController.zakromaStream`** (строки 344-389): удалить 21 параметр.

## 3. ZakromaAlbumSong (после рефакторинга, модель)

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt`

**Удалено** (21 поле, переменные `var ... = ""`):

- `var linkBoosty: String = ""`
- `var linkSponsrPlay: String = ""`
- `var linkDzenKaraoke: String = ""`, ... (всего 4 Dzen)
- `var linkVkKaraoke: String = ""`, ... (всего 4 Vk)
- `var linkTgKaraoke: String = ""`, ... (всего 4 Tg)
- `var linkPlKaraoke: String = ""`, ... (всего 4 Pl)
- `var linkMaxKaraoke: String = ""`, ... (всего 4 Max)

**Остаётся** (поля метаданных, без ссылок): `id`, `track`, `songName`, `onAir`, `datePublish`, `airTimestamp`, `songSubscriptionAvailable`, `alwaysFree`, `freelyAvailableNow`, `freeAccessWindowEndText`.

**Метод-сборщик** (строки 207-225 `Zakroma.kt`): удалить 21 строку `zakromaAlbumSong.linkXxx = song.linkXxx`. Перед удалением проверить через `grep -rn "zakromaAlbumSong\." karaoke-app/src/main/kotlin/` что нет других потребителей.

## Связь с БД

Поля, которые сейчас читаются из `Song.kt` (модель поверх `tbl_songs`) и записываются в `ZakromaAlbumSong`:

- `song.linkBoostyTxt` → `zakromaAlbumSong.linkBoosty` (УДАЛЕНО).
- `song.linkSponsrPlay` → `zakromaAlbumSong.linkSponsrPlay` (УДАЛЕНО).
- `song.linkDzenKaraoke` → `zakromaAlbumSong.linkDzenKaraoke` (УДАЛЕНО).
- ... и т.д.

После рефакторинга метод-сборщик НЕ читает `song.linkBoostyTxt`, `song.linkSponsrPlay`, `song.linkDzen*`, `song.linkVk*`, `song.linkTg*`, `song.linkMax*`, `song.linkPl*`. Эти геттеры остаются в `Song.kt` (используются админкой `webvue3`), но НЕ читаются при сборке `ZakromaAlbumSong`.

## Валидация / State Transitions

Поскольку это рефакторинг публичного DTO без изменения бизнес-логики:

- Нет новых полей с ограничениями.
- Нет state transitions.
- Нет валидационных правил.

Все три data class'a — это проекции (read-only views), у них нет собственной валидации. Валидация живёт в `Song.kt` (admin-side, не в этой спеке).

## Связь с другими DTO

| DTO | Где используется | Связь |
|---|---|---|
| `SongPublicDto` | `/api/public/song/{id}`, `/api/public/songs` | Содержит `assignment: SongAssignmentBriefDto?` (из specs/182). |
| `ZakromaAlbumSongPublicDto` | `/api/public/zakroma`, `/api/public/zakroma/stream` (NDJSON), `ZakromaStreamMessageDto.song` | Содержит только метаданные. |
| `ZakromaAlbumSong` (модель) | Только в `Zakroma.kt` для построения DTO | Удаляется по факту неиспользования. |

## Миграции БД

**Нет.** Этот рефакторинг НЕ затрагивает:

- Схему `tbl_songs` (поля ссылок там живут как колонки `link_*`).
- `recordhash`-триггеры (поля не переименовываются и не удаляются на уровне БД).
- `SyncRegistry` (не нужна перерегистрация — синхронизируемые сущности не меняются).

Если в будущем понадобится БД-миграция (например, удалить колонки `link_*` из `tbl_songs`), это будет отдельная фича с собственным планом и миграционным SQL.