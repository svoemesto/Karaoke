# API Contracts: Поле `song_name_censored` в `tbl_songs`

Ниже — только ИЗМЕНЕНИЯ относительно текущих контрактов. Все существующие
поля и пути остаются без изменений.

## Admin API (`karaoke-app`, без авторизации, `webvue3` — клиент)

### Новый endpoint

| Метод | Путь | Возвращает | Назначение |
|---|---|---|---|
| `POST` | `/api/utils/rescanallcensorednames` | `"OK"` или `"ALREADY_RUNNING"` | Запускает фоновую функцию `Utils.rescanAllCensoredNames()` — реckan всех `tbl_songs.song_name_censored` по актуальному словарю «Censored». |
| `GET`  | `/utils/rescanallcensorednames` | `"OK"` или `"ALREADY_RUNNING"` | Зеркало для Thymeleaf-страниц (по образцу существующего GET-зеркала `customfunction`). |

**Поведение**:
- Если функция уже запущена (`isCensoredRescanInProgress=true`) →
  немедленный ответ `"ALREADY_RUNNING"`, новый поток НЕ стартует.
- Иначе → стартует фоновый поток, немедленный ответ `"OK"`.
- По завершении фонового потока — SSE-уведомление
  (`SseNotification.send(...)` с заголовком «Пересканирование
  цензурированных названий» и телом «Обработано N песен за M секунд,
  обновлено K»).

### Изменения в существующем endpoint `/api/song/update`

Никаких изменений в API не требуется — сохранение идёт через существующий
механизм `saveSong` (`store.js:1941`) → `POST /api/song/update`
→ `Song.saveToDb()` → diff → UPDATE. Новое поле `songNameCensored`
добавляется в diff автоматически при изменении (после правок в
`getDiff`/`saveToDb`).

## Admin DTO

`SongDTO` (`karaoke-app/.../model/SongDTO.kt:27-28`) — поле
`songNameCensored` уже существует. После правки
`Song.songNameCensored` (read/write через `SongField`) значение DTO
будет приходить из БД (а не из `songName.censored(database)`), без
изменений в DTO.

`SongDTOdigest` (`karaoke-app/.../model/SongDTOdigest.kt`) — если есть
поле `songNameCensored`, никаких изменений (просто источник значения
меняется). Если нет — добавлять НЕ нужно (digest — краткая сводка
для списков, цензурированное название не критично для UI).

## Public API (`karaoke-web`, без авторизации, `karaoke-public` — клиент)

`SongPublicDto` (`karaoke-web/.../dto/SongPublicDto.kt:13`) — поле
`songNameCensored` НЕ добавляется (Out of Scope спеки). Уже
существующее поле `songName` остаётся как есть — без цензурирования
(политика проекта: на публичном сайте показывается оригинальное
название, цензурирование нужно только для постов в соцсети/рассылки).

## Шаблоны публикаций (`VkTemplateService`, `TelegramTemplateService`, `NewsTemplateService`)

`{songNameCensored}` (плейсхолдер в шаблонах) — значение берётся из
`Song.songNameCensored` (БД-поле), а НЕ из `Song.songName.censored(database)`.
Никаких изменений в API для редактирования шаблонов
(`/api/vk/templates`, `/api/news/templates`) не требуется — только
внутренняя правка `buildReplacements()` в каждом сервисе.

## Sync LOCAL↔SERVER

Никаких изменений в API синхронизации (`SyncRegistry` + `KaraokeProperties`):
- `tbl_songs` уже зарегистрирован в `SyncRegistry.all` (target=`"settings"`).
- Флаги `sync_settings_push_*` / `sync_settings_pull_*` остаются как есть.
- `recordhash` для `tbl_songs` пересобран в миграции с добавлением
  `song_name_censored` — синхронизация будет корректно ловить изменения
  этой колонки (Принцип II/III).

## UI Store (`webvue3/src/components/Songs/store.js`)

Новый action (рядом с `customFunctionPromise`, строка 2430):

```js
rescanAllCensoredNamesPromise() {
  let request = { method: 'POST', url: '/api/utils/rescanallcensorednames' }
  return promisedXMLHttpRequest(request)
},
```

Никаких изменений в `state`/`mutations`/`getters` — поле
`state.currentSong.songNameCensored` уже существует (после правки
`Song.songNameCensored` геттер будет возвращать значение из БД, а не
computed через `censored()`).