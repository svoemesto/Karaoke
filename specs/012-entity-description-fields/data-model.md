# Data Model: Доп. поля Author/Album/Song + новый UI Закромов

## Author (`tbl_authors`)

Файлы: `karaoke-app/.../model/Author.kt` (строки 32-127), `AuthorDTO.kt`.

| Поле | Тип | БД-колонка | По умолчанию | Примечание |
|---|---|---|---|---|
| `description` | `String` | `description TEXT` | `''` | Многострочный plain text (research.md §1) |
| `shortDescription` | `String` | `short_description VARCHAR(255)` | `''` | Однострочный текст |
| `warning` | `String` | `warning VARCHAR(255)` | `''` | Однострочный текст |

Реализация — по образцу уже существующих полей (`aliases`, `isSpecialOrder`):
`@KaraokeDbTableField(name = "description") var description: String = ""` и
аналогично для двух других — reflection-слой `KaraokeDbTable` подхватывает их
автоматически в `save()`/`getSqlToInsert()`/`getDiff()` (никакого ручного SQL
не требуется, в отличие от `Song`, см. ниже).

`AuthorDTO` — добавить 3 поля 1:1; `fromDto()`/`toDTO()` прокидывают их так же,
как `aliases`.

Валидация: нет (свободный текст, пустая строка — валидное значение по
умолчанию).

## Album (`tbl_albums`)

Файлы: `karaoke-app/.../model/Album.kt` (строки 32-354), `AlbumDTO.kt`.

| Поле | Тип | БД-колонка | По умолчанию | Примечание |
|---|---|---|---|---|
| `description` | `String` | `description TEXT` | `''` | Плейн-текст |
| `shortDescription` | `String` | `short_description VARCHAR(255)` | `''` | Напр. "Remastered 2018" |
| `warning` | `String` | `warning VARCHAR(255)` | `''` | Напр. «УЧАСТНИК ГРУППЫ ПРИЗНАН ИНОАГЕНТОМ» |

Та же reflection-модель (`@KaraokeDbTableField`), как `name`/`albumType`.
`AlbumDTO` — добавить 3 поля; `toDTO()`/`fromDto()` — 1:1 копирование, по
образцу уже существующих `name`/`sortOrder`.

## Song (`tbl_songs`)

Файл: `karaoke-app/.../model/Song.kt` — **НЕ** reflection-based
(`KaraokeDbTable`), а собственная модель на `fields: MutableMap<SongField, String>`
+ Kotlin-свойства-обёртки. Требует правок в 5 местах (по образцу
`formattedTextSong`, см. `Song.kt:164-188, 5489, 6386-6399, 7314-7316, 7838-7840`):

1. **`SongField.kt`** (строки 55-57 — рядом с `FORMATTED_TEXT_*`): добавить
   `DESCRIPTION`, `SHORT_DESCRIPTION`, `WARNING`.
2. **`Song.kt` ~164-188** — свойства-обёртки:
   ```kotlin
   var description: String
       get() = fields[SongField.DESCRIPTION] ?: ""
       set(value) { fields[SongField.DESCRIPTION] = value }
   // аналогично shortDescription → SHORT_DESCRIPTION, warning → WARNING
   ```
3. **`Song.kt` ~5489** (`getSqlToInsert`/INSERT field list) — добавить 3 пары
   `fieldsValues.add(Pair("description", settings.description))` и т.д.
4. **`Song.kt` ~6386-6399** (`getDiff`) — добавить 3 сравнения по образцу
   `formattedTextSong`-блока, с `RecordDiff("description", ...)`.
5. **`Song.kt` ~7314-7316** (загрузка из `ResultSet`) — добавить
   `rs.getString("description")?.let { value -> settings.description = value }`
   и аналогично для двух других.
6. **`Song.kt` ~7838-7840** (`toDTO()`) — прокинуть 3 поля в `SongDTO`.

`SongDTO`/`SongDTOdigest` — добавить 3 поля (в digest — только если
Zakroma/списочные представления песни должны их видеть; per spec.md Assumptions
`ZakromaAlbumSongPublicDto` НЕ получает эти поля, так что в digest они не
обязательны, но для консистентности admin-таблицы песен добавляются в основной
`SongDTO`).

Валидация: нет (свободный текст).

## AlbumType (расширение существующего enum, не новая сущность)

Файл: `karaoke-app/.../model/AlbumType.kt`. Добавить 2 новых поля к каждой
константе (рядом с уже существующим `description`, который остаётся как есть —
подпись «под названием альбома»):

```kotlin
enum class AlbumType(
    val dbValue: String,
    val description: String,   // существующее — "Студийный альбом" и т.п.
    val groupLabel: String,    // новое — заголовок группы: "Студийные альбомы"
    val filterLabel: String,   // новое — подпись кнопки фильтра: "Студийные"
) : Serializable {
    STUDIO(dbValue = "studio", description = "Студийный альбом", groupLabel = "Студийные альбомы", filterLabel = "Студийные"),
    LIVE(dbValue = "live", description = "Концертный альбом", groupLabel = "Концертные альбомы", filterLabel = "Концертные"),
    COMPILATION(dbValue = "compilation", description = "Сборник", groupLabel = "Сборники", filterLabel = "Сборники"),
    BOOTLEG(dbValue = "bootleg", description = "Бутлег", groupLabel = "Бутлеги", filterLabel = "Бутлеги"),
    SINGLE(dbValue = "single", description = "Сингл", groupLabel = "Синглы", filterLabel = "Синглы"),
    ;
    ...
}
```

Порядок группировки (studio → single → live → compilation → bootleg, FR-024) —
НЕ порядок объявления enum-констант (тот исторически studio/live/compilation/
bootleg/single) — реализуется отдельным явным `List<AlbumType>`-константой
(например, `AlbumType.ZAKROMA_GROUP_ORDER`), не полагаясь на `entries`-порядок.

## Новый DTO: AlbumTypeSummaryDto

Новый файл или дополнение в `ZakromaPublicDto.kt`:

```kotlin
data class AlbumTypeSummaryDto(
    val dbValue: String,      // "studio" — для сопоставления с albumType альбома на фронте
    val groupLabel: String,   // "Студийные альбомы"
    val filterLabel: String,  // "Студийные"
    val count: Int,           // число альбомов автора этого типа
)
```

Вычисляется на бэкенде (в `ZakromaPublicDto.fromZakroma`/`Zakroma.kt`) по
списку альбомов автора, **только для типов с `count > 0`**, в порядке
`AlbumType.ZAKROMA_GROUP_ORDER` (FR-025/FR-026 — типы без альбомов не попадают
в список → фронт не рендерит для них ни кнопку, ни заголовок группы).

## ZakromaPublicDto / ZakromaAlbumPublicDto (расширение существующих)

`ZakromaPublicDto` (`karaoke-web/.../dto/ZakromaPublicDto.kt:64`) — добавить:
`authorDescription`, `authorShortDescription`, `authorWarning: String`,
`albumTypeCounts: List<AlbumTypeSummaryDto>`.

`ZakromaAlbumPublicDto` (`:49`) — добавить: `description`, `shortDescription`,
`warning: String`. `albumType` остаётся как есть (`String`, `AlbumType.dbValue`)
— используется фронтом только как ключ сопоставления с `albumTypeCounts`/новыми
`groupLabel`/`filterLabel` из бэкендового enum (фронт больше не хранит
собственную мапу подписей, research.md §4). Название/год/тип уже сейчас
приходят из `Album`-сущности при наличии связи (Zakroma.kt строит `ZakromaAlbum`
из `Album`, где связь установлена) — FR-017 закрепляет это как обязательное
поведение, отдельного кода может не требоваться сверх подключения новых 3 полей.

`ZakromaAlbumSongPublicDto` — **не изменяется** (вне объёма, spec.md
Assumptions).

## SongPublicDto (расширение существующего)

`karaoke-web/.../dto/SongPublicDto.kt:10-70` — добавить `description`,
`shortDescription`, `warning: String`, прокинуть в `fromSettings()` (аналогично
`formattedTextSong` — строка ~102, тоже за флагом `includeDetails`, т.к. это
детальные поля страницы одной песни, не нужные в списках/поиске).

## Отношения и переходный период

- `Album.authorId` → `Author.id` (уже существует, `ON DELETE RESTRICT`).
- `Song.albumId` → `Album.id` (уже существует, nullable, `ON DELETE SET NULL`)
  — если `null` (песня ещё не привязана к сущности Album после миграции
  011-album-song-rename), Закрома продолжают использовать прежнюю эвристику
  отображения (без description/shortDescription/warning альбома, тип по
  умолчанию `studio`) — см. spec.md Edge Cases.
- Новые поля не участвуют ни в каких внешних ключах и не имеют собственных
  ограничений целостности — простые опциональные текстовые атрибуты.
