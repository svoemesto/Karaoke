# Phase 1 — Data Model: 261-search-results-ui

## Что меняется

Расширение существующего DTO `SongPublicDto` (бэк) тремя новыми полями.
Никаких новых сущностей, никаких миграций БД.

## 1. Сущности (UI/фронт)

### SearchResult (UI-элемент массива)

Источник: `state.songs.searchResults` в Vuex-сторе `karaoke-public/src/store/modules/songs.js:16`. Заполняется ответом `GET /api/public/songs?...`.

| Поле | Тип | Семантика | Источник |
|---|---|---|---|
| (существующие поля: id, songName, author, album, year, onAir, datePublish, airTimestamp, alwaysFree, freelyAvailableNow, freeAccessWindowEndText, songPictureUrl, songSubscriptionAvailable, idStatus, …) | … | Без изменений | бэк |
| **`contentReady`** | `boolean` | Зеркало `Song.isContentReady` с бэка. `true` ⇔ плеер можно открыть (стемы+картинка+маркеры залиты, idStatus≥6). | **новое**, FR-014 |
| **`albumPictureUrl`** | `string` | URL превью обложки альбома или `""` (→ плейсхолдер «♪» на фронте). | **новое**, FR-014 |
| **`authorPictureUrl`** | `string` | URL превью автора или `""` (→ плейсхолдер «👤» на фронте). | **новое**, FR-014 |

**Поведение фронта при пустых URL'ах**: см. `data-model.md#nullability` ниже; и `spec.md:FR-005` (плейсхолдер «♪» / «👤» в той же чёрной плашке).

**Идемпотентность / конкурентность**: ключ строки остаётся `song.id` (как сейчас, см. `SearchView.vue:v-for="song in searchResults" :key="song.id"`); дублей с одинаковым id быть не должно (Spring Data синхронный источник). Если всё же — Vue warning, это уже не часть спеки 261.

## 2. Сущности (бэк)

### SongPublicDto (изменяемый)

Расположение: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt`.

| Поле | Тип | Default | JSON-ключ | Источник |
|---|---|---|---|---|
| (все существующие поля 1..65 без изменений) | … | … | … | … |
| **`contentReady`** | `Boolean = false` | `false` | `contentReady` (без `is`-префикса, инвариант Jackson проекта) | `s.isContentReady` |
| **`albumPictureUrl`** | `String = ""` | `""` | `albumPictureUrl` | URL-строитель по `Album.picturePreviewFileName` (FK через `song.albumId`); `""` если `albumId == null` или `picturePreviewFileName` пустой |
| **`authorPictureUrl`** | `String = ""` | `""` | `authorPictureUrl` | URL-строитель по `Author.picturePreviewFileName` (резолв `song.author` → `Author.id` по `Author.loadIdsByNames`); `""` если автора нет в `tbl_authors` или `picturePreviewFileName` пустой |

**Nullability / Defaults**:
- `contentReady = false` для null-входа — безопасно: фронт трактует `false` как серую иконку, что не хуже текущего (сейчас все иконки серые, см. bug).
- `albumPictureUrl = ""` для null/empty — фронт мапит `""` на плейсхолдер. Никаких `null` в JSON (Jackson + Kotlin-default `""`).
- `authorPictureUrl = ""` — то же.

**Правила валидации** на DTO:
- Нет (поля уже валидируются внутри `Song.isContentReady` на бэке и условиями выборки на контроллере).
- На фронте: `Boolean`/`String` `""` — JS-движок трактует `undefined`/`null` как falsy/`""` без падения.

**State transitions**: нет (поисковый результат — snapshot, никакого жизненного цикла).

**Связи**:
- `albumPictureUrl ← Album.picturePreviewFileName` — `1:1` через `Song.albumId` (nullable FK).
- `authorPictureUrl ← Author.picturePreviewFileName` — `N:1` через `Song.author` (free text → `tbl_authors.id`).
- Резолвы батчатся в `PublicApiController.songs()` (по образцу уже-существующего `aliasByAuthor` lookup) — один запрос на уникальные id (не N+1).

### Song (без изменений)

Существующая модель. Используется как источник `isContentReady` для нового `SongPublicDto.contentReady`. Никаких правок `Song.kt` не требуется (см. `SongField.kt:124 PLAYER_READINESS_FLAGS`, `PublicPlayerController.stemsReady:139` — поле уже используется).

### Author (без изменений)

Существующая модель. `Author.loadIdsByNames([names], db)` уже используется в `PublicApiController.song()` для `authorId` (см. спеку 259, FR-012). Расширение использования на `authorPictureUrl` — в текущем методе `songs()` контроллера.

### Album (без изменений)

Существующая модель. `Album.getAlbumsByIds(ids, db)` уже существует (`Album.kt:294-309`); используется для резолва `albumPictureUrl` батчем.

## 3. Сущности (фронт, без новых)

- **`SearchView.vue`** — перерисовывается: `<table>` → одна разметка строки (div-row по образцу `PlaylistEditView.vue:95-189`).
- **`PlayerIcon.vue`** — без изменений (FR-001 логика уже совпадает).
- **`FavoriteIcon.vue`, `PlaylistIcon.vue`, `CartIcon.vue`, `PremiumIcon.vue`** — без изменений.
- **`store/modules/songs.js`** — без изменений (поля приходят по тому же `setSearchResults`).

## 4. Изменение модели данных — итог

| Что | Было | Стало | Где |
|---|---|---|---|
| DTO `SongPublicDto` | без `contentReady`/`albumPictureUrl`/`authorPictureUrl` | +3 поля | `karaoke-web/.../dto/SongPublicDto.kt` |
| JS-объект строки `searchResults` | без 3 полей | +3 поля | (тот же shape — Jackson сериализует) |
| `<PlayerIcon :content-ready-state>` | `'notready'` всегда | `'ready'`/`'notready'` по `song.contentReady` | `SearchView.vue:103,143` |
| Render of `<table>` + `<div class="km-cards">` | обе ветки | одна row-разметка | `SearchView.vue:32-117,118-164` |
| Превью альбома/автора | нет (только `songPictureUrl` если где) | `<img>`×2 в чёрной плашке | новая разметка в `SearchView.vue` |
| CSS-блок | `km-table*`, `km-td-*`, `km-card*` | + `km-song-row*`, `km-song-pictures*`, `km-song-info*` (копия PlaylistEditView) | `SearchView.vue` `<style scoped>` |

Никаких БД-миграций. Никаких новых DTO/контроллеров/эндпоинтов. Diff: +1 файл DTO, правки в `SearchView.vue`.
