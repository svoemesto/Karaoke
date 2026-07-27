# Data Model: выбор поискового движка

## Новые enum'ы (не персистируются как отдельные таблицы — только как строка в KaraokeProperties)

### LyricsSearchEngine

| Значение | Описание | Self-hosted? |
|---|---|---|
| `YANDEX_SYNC` | Yandex Cloud Search API, синхронный запрос (быстро, платно) | Нет (внешний, уже существовал в коде до этой задачи) |
| `YANDEX_ASYNC` | Yandex Cloud Search API, асинхронный запрос (дешевле, дольше) | Нет (аналогично) |
| `SEARXNG` | Прямой запрос к self-hosted SearXNG (`searxng.base-url`) | Да |
| `FOURGET` | self-hosted fourget, brave→yep (см. фичу 014) | Да |

### AlbumCoverSearchEngine

| Значение | Описание |
|---|---|
| `SEARXNG` | `AlbumCoverService.searchSearxngImages` — без изменений (сегодняшнее поведение) |
| `FOURGET` | Новый метод `searchFourgetImages` — `fourget` `/api/v1/images` |

## Изменённые/новые свойства KaraokeProperties (`KaraokeProperties.kt` → `listKaraokeProperties`)

| key | Тип | Default | Описание |
|---|---|---|---|
| `lyricsSearchEngine` | String | `"FOURGET"` | Движок поиска текстов песен по умолчанию — одно из значений `LyricsSearchEngine` |
| `albumCoverSearchEngine` | String | `"SEARXNG"` | Движок поиска обложек альбомов по умолчанию — одно из значений `AlbumCoverSearchEngine` (дефолт сохраняет сегодняшнее поведение без изменений) |

Оба значения — свободная строка (по типовой системе `KaraokeProperties`,
которая не имеет нативного enum-типа), валидируется/парсится в enum на
границе использования (`enumValueOf<...>`, фолбэк на default при
некорректном/устаревшем значении — например, если в будущем движок будет
удалён из enum, а строка в БД останется старой).

## Изменения существующих сущностей (без изменения схемы БД)

### SearchAsync / SearchResult (`model/SearchAsync.kt`, `model/SearchResult.kt`)

Схема таблиц (`tbl_search_async`, `tbl_search_results`) НЕ меняется. Добавляются
два новых companion-метода массового удаления по `songId` (по образцу
`CartItem.deleteByUserAndSongs`):

```kotlin
// SearchAsync.kt
fun deleteBySongId(songId: Long, database: KaraokeConnection, storageService: KaraokeStorageService, storageApiClient: StorageApiClient) {
    getSearchAsyncListBySongId(songId, database, storageService, storageApiClient)
        .forEach { delete(it.id, database) }
}

// SearchResult.kt — аналогично, через getSearchResultListBySongId
fun deleteBySongId(songId: Long, database: KaraokeConnection, storageService: KaraokeStorageService, storageApiClient: StorageApiClient) {
    getSearchResultListBySongId(songId, database, storageService, storageApiClient)
        .forEach { delete(it.id, database) }
}
```

**Порядок удаления**: сначала `SearchResult` (дочерние записи, ссылаются на
`searchAsyncId`), затем `SearchAsync` (родитель) — чтобы не оставить
осиротевших `SearchResult`, если между вызовами что-то пойдёт не так.

**Валидация**: удаление затрагивает ТОЛЬКО записи с данным `songId` — не
массовая/глобальная операция (FR из `spec.md`, Assumptions) — за исключением
явной массовой обёртки для FR-012 (см. ниже), которая просто вызывает эти же
методы в цикле по списку `songId`.

## Автоматическая очистка по достижении статуса готовности (FR-011)

`model/Song.kt` → `saveToDb()`, рядом с уже существующей переменной
`crossedReadyThreshold` (строка ~4991) и существующим вызовом
`HealthReport.recomputeAndBroadcast(...)` (см. `research.md`, Вопрос 6):

```kotlin
if (crossedReadyThreshold) {
    HealthReport.recomputeAndBroadcast(...)          // существующий код, не меняется
    SearchResult.deleteBySongId(id, database, storageService, storageApiClient)   // новое
    SearchAsync.deleteBySongId(id, database, storageService, storageApiClient)    // новое
}
```

## Массовая очистка для уже готовых песен (FR-012/FR-013)

Новая функция (по образцу `HealthReport.recalculatePlayerReadiness`,
`ApiController.doRecalcPlayerReadiness`):

```kotlin
fun deleteSearchResultsForReadySongs(
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): Int // возвращает количество обработанных песен
```

Загружает все `Song` с `id_status >= 3`, для каждой вызывает
`SearchResult.deleteBySongId`/`SearchAsync.deleteBySongId`. Песни со статусом
<3 не выбираются запросом — физически не могут быть затронуты (FR-013).

## Функциональный диспетчер (не сущность БД, но ключевая точка данных)

```kotlin
fun getLyricsSearch(
    settings: Song,
    lyricsFinderService: LyricsFinderService,
    engine: LyricsSearchEngine,
    forceResearch: Boolean = false,
): SearchAsync
```

Заменяет собой сегодняшний вызов `getSearXNGSearch(settings,
lyricsFinderService)` внутри `getSearchSongTextAll` (`ApiController.kt`) —
имя `getSearXNGSearch` переименовывается (см. `research.md`, Вопрос 2),
т.к. оно уже не отражает реальность после фичи 014.
