# Contract: API-эндпоинты выбора движка поиска

## Изменённый эндпоинт: поиск текста песни

`POST /api/songs/searchsongtextall` (`ApiController.getSearchSongTextAll`,
уже существует)

| Параметр | Тип | Обязателен | Поведение |
|---|---|---|---|
| `songsIds` | String (`;`-разделённые id) | да (как сегодня) | без изменений |
| `engine` | String (`LyricsSearchEngine`) | нет | Если передан — используется вместо `KaraokeProperties.lyricsSearchEngine`. Некорректное значение → фолбэк на default (не ошибка) |
| `forceResearch` | Boolean | нет, default `false` | `true` → сначала удалить существующие `SearchAsync`/`SearchResult` для каждой песни из `songsIds`, затем искать заново |

Обратная совместимость: вызов без `engine`/`forceResearch` (как сегодня из
`searchTextForSong` до этой задачи) ведёт себя идентично — использует
`lyricsSearchEngine` из настроек, `forceResearch=false` (сегодняшнее
кэширующее поведение).

## Новый эндпоинт: удаление результатов поиска текста песни

`POST /api/song/deletesearchresults`

| Параметр | Тип | Обязателен |
|---|---|---|
| `songId` | Long | да |

**Поведение**: удаляет все `SearchResult`, затем все `SearchAsync` для
данного `songId` (см. `data-model.md`). Возвращает `Boolean` (успех) — по
образцу существующих simple-boolean эндпоинтов (`getSearchSongTextAll`).
Идемпотентно: повторный вызов на уже пустой песне — не ошибка, просто
ничего не делает.

## Изменённый эндпоинт: поиск обложки альбома

`POST /api/song/searchalbumcover` (`ApiController.searchAlbumCover`,
уже существует)

| Параметр | Тип | Обязателен | Поведение |
|---|---|---|---|
| `id` | Long | да (как сегодня) | без изменений |
| `query` | String? | нет (как сегодня) | без изменений |
| `skipYandex` | Boolean? | нет (как сегодня) | без изменений — управляет отдельным путём через Яндекс.Музыку/Playwright, не входит в 2 движка этой задачи |
| `engine` | String (`AlbumCoverSearchEngine`) | нет | Если передан — используется вместо `KaraokeProperties.albumCoverSearchEngine`. Некорректное значение → фолбэк на default |

## Новый эндпоинт: массовая очистка результатов поиска готовых песен

`POST /api/utils/deletesearchresultsforreadysongs` (по образцу уже
существующего `POST /api/utils/recalcplayerreadiness`)

Без параметров. Возвращает `Boolean` (запуск принят) — сама операция уходит
в фоновый поток (по образцу `doRecalcPlayerReadiness`), т.к. может обработать
много песен; итог (количество обработанных песен) приходит тостом через SSE
(`SseNotification.message`), не в теле HTTP-ответа.

**Поведение**: удаляет `SearchResult`+`SearchAsync` для ВСЕХ песен с
`idStatus >= 3` (FR-012). Песни со статусом <3 не затрагиваются (FR-013).
Идемпотентно: повторный запуск на уже очищенных песнях — не ошибка.

## Настройки (без нового эндпоинта — уже существующий механизм)

`lyricsSearchEngine` и `albumCoverSearchEngine` читаются/пишутся через уже
существующие универсальные эндпоинты `KaraokeProperties`
(`/api/properties/...` — см. `PropertiesTable.vue`/`ApiController`,
управление свойствами) — новых эндпоинтов настроек эта задача не добавляет.
