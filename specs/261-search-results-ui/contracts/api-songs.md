# Contracts: 261-search-results-ui

## Внешний контракт — публичный HTTP endpoint

### `GET /api/public/songs` (расширен)

**Назначение**: публичный эндпоинт поиска песен. Возвращает JSON-массив `SongPublicDto`. Используется `SearchView.vue` (через `state.songs.searchResults`) и потенциально другими клиентами публичного сайта.

**Изменение**: расширение каждого элемента массива тремя новыми полями (`contentReady`, `albumPictureUrl`, `authorPictureUrl`). Контракт обратно совместим: все три поля опциональны для потребителей (фронт ловит `undefined` и трактует как «нет данных»), существующие поля и их JSON-ключи не переименованы и не удалены.

**Контроллер**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`, метод `songs(...)` (см. `PublicApiController.kt:649-718`). Поведение эндпоинта не меняется; меняется только наполнение DTO внутри `song.map { SongPublicDto.fromSong(it, includeDetails = false) }`.

**Запрос** (`@RequestParam`, все опциональны):
- `songName: String?` — подстрока имени песни (`LOWER(song_name) LIKE`).
- `author: String?` — автор; резолвится через `Author.resolveByTerm` (алиасы участников групп → реальные имена); если совпадений нет — фолбэк на строгое равенство `song.author`.
- `text: String?` — подстрока текста (`LOWER(text) LIKE`).
- `album: String?` — подстрока альбома (`LOWER(song_album) LIKE`).
- `anonId: String?` — анонимный ID (event-tracking).
- `referrer: String?` — реферер страницы (event-tracking).

**Ответ**: `200 OK`, `application/json`, массив `SongPublicDto[]`.

**Структура одного элемента** (полный список полей; **новые** помечены ➕):

```json
{
  "id": 12345,
  "songName": "Звезда по имени Солнце",
  "author": "Кино",
  "authorId": 42,
  "authorAlias": "",
  "album": "Ночь",
  "year": 1989,
  "track": 1,
  "key": "Em",
  "bpm": 124,
  "onAir": true,
  "datePublish": "01.06.1989",
  "airTimestamp": 612000000000,
  "alwaysFree": false,
  "freelyAvailableNow": true,
  "freeAccessWindowEndText": "01.07.1989",
  "songPictureUrl": "/api/public/song-picture/12345",
  "formattedTextSong": "",
  "formattedTextTabs": "",
  "formattedTextChords": "",
  "description": "",
  "shortDescription": "",
  "warning": "",
  "idVkKaraoke": "...", "idVkKaraokeOID": "...", "idVkKaraokeID": "...",
  "idVkLyrics":   "...", "idVkLyricsOID":   "...", "idVkLyricsID":   "...",
  "idVkMelody":   "...", "idVkMelodyOID":   "...", "idVkMelodyID":   "...",
  "idVkChords":   "...", "idVkChordsOID":   "...", "idVkChordsID":   "...",
  "contentRemoved": false,
  "songSubscriptionAvailable": true,
  "idStatus": 7,

  "➕ contentReady": true,
  "➕ albumPictureUrl": "/api/public/picture?file=%2Fauthors%2FKino%2FNoch.jpg",
  "➕ authorPictureUrl": "/api/public/picture?file=%2Fauthors%2FKino%2Fpreview.jpg"
}
```

**Значения по умолчанию** (если данных нет):
- `contentReady`: `false` (серая иконка плеера на фронте).
- `albumPictureUrl`: `""` (фронт показывает плейсхолдер «♪»).
- `authorPictureUrl`: `""` (фронт показывает плейсхолдер «👤»).

**Коды ответов**:
- `200 OK` — массив (включая пустой `[]`).
- (Никаких других кодов, изменения контракта ошибок нет.)

**Пример (минимальный запрос)**:
```
GET /api/public/songs?songName=Солнце&author=Кино
→ 200 OK
[
  { "id": 12345, "songName": "Звезда по имени Солнце", ...,
    "contentReady": true,
    "albumPictureUrl": "/api/public/picture?file=...",
    "authorPictureUrl": "/api/public/picture?file=..." }
]
```

### Потребители контракта

- **`karaoke-public/src/views/SearchView.vue`** (основной потребитель). Использует `song.contentReady` (`<PlayerIcon :content-ready-state="...">`), `song.albumPictureUrl` (`<img :src="...">`), `song.authorPictureUrl` (`<img :src="...">`).
- Другие потребители не регистрируются; опциональные поля могут ими игнорироваться.

## Внутренний контракт — маппинг `fromSong`

### `SongPublicDto.fromSong(s: Song, ...)` (расширен)

**Сигнатура**: `fun fromSong(s: Song, includeDetails: Boolean = true): SongPublicDto`. На текущий момент принимает только `Song`. Для добавления `albumPictureUrl`/`authorPictureUrl` нужно либо:
- (A) Принять ещё 2 параметра `albumPictureUrl: String, authorPictureUrl: String` и заполнять из них. Контроллер делает batch-lookup.
- (B) Перенести URL-сборку в helper (`SongPublicUrls.fromSong(s, db, storageService, storageApiClient): SongPublicUrls`), который вернёт пару строк.

**Делается**: Implementation Notes (на этапе `tasks.md`); контракт-минимум — оба варианта валидны.

### `PublicApiController.songs(...)` (расширен)

Внутри существующего `song.map { SongPublicDto.fromSong(it, includeDetails = false) }` перед/после `map` добавляется batch-резолв:

```text
// Псевдокод, не для копирования — только очерчивает суть
val albumIds: List<Long> = song.mapNotNull { it.albumId }.distinct()
val authorNames: List<String> = song.map { it.author }.distinct()
val albumsById: Map<Long, Album>   = if (albumIds.isNotEmpty()) Album.getAlbumsByIds(albumIds, ...) else emptyMap()
val authorsByName: Map<String, Author> = Author.getAuthorsByNames(authorNames, ...) // новый helper
// после map{} можно ещё прогнать один patch-map с подстановкой URL-ов по картам, если fromSong не принял их параметрами.
```

Эти batch-резолвы — единственные новые SQL на запрос (при больших результатах — не больше 2 дополнительных запросов с WHERE id IN (...) / author IN (...)).

## UI-контракт (DOM)

### CSS-классы строки результата поиска

| Класс | Назначение | Скопировано из PlaylistEditView |
|---|---|---|
| `.km-song-list` | Контейнер списка (`v-for` строк) | `PlaylistEditView.vue:801-805` |
| `.km-song-row` | Одна строка | `PlaylistEditView.vue:806-826` |
| `.km-song-pictures` | Чёрная плашка с двумя `<img>` (превью) | `PlaylistEditView.vue:844-853` |
| `.km-song-cover`, `.km-song-author` | Сами картинки | `PlaylistEditView.vue:854-880` |
| `.km-song-cover-fallback`, `.km-song-author-fallback` | Плейсхолдеры «♪»/«👤» | `PlaylistEditView.vue:881-885` |
| `.km-song-info` | Блок названия + подписи | `PlaylistEditView.vue:915-918` |
| `.km-song-title-link` | Кликабельная ссылка «название песни» | `PlaylistEditView.vue:934-941` |
| `.km-song-author-link` | Кликабельная ссылка «имя автора» | `PlaylistEditView.vue:942-951` |
| `.km-song-sub` | Подпись «Автор - год, альбом» | `PlaylistEditView.vue:966-972` |

### ARIA / focus-visible

- `<a href="#song?id=X">` (рендерится Vue Router'ом из `<router-link>`) — встроенная поддержка Ctrl+клик (новая вкладка), `focus-visible` стилей.
- `:hover` / `:focus-visible` уже прописаны в `PlaylistEditView.vue:959-965`.

### Вьюпорт / адаптивность

- Без media-query: row-разметка одинакова для десктопа и мобилки (Clarification Q1 → A, 2026-08-28). Адаптивность за счёт flex-контейнера `.km-song-list { display: flex; flex-direction: column; gap: 0.35rem; }` — на узком экране блок превью и иконки переносятся/сжимаются естественно.
- Уже существующий `<div class="km-cards">` mobile-fallback — удаляется, как и `<table class="km-table">` десктопная ветка.

## Контракт событий/аналитики

- Клик по иконке плеера в SearchView → `PlayerIcon.onOpen()` → `openPlayer(songId)` → открытие `/player/<id>`. Аналитика `OPENED` пишется на стороне `PublicPlayerController.access:182-191` (как и для ZakromaView), фронт-перехват не нужен.
- Никакие новые типы событий в `EventTypes.kt`/`tbl_events` НЕ добавляются.
