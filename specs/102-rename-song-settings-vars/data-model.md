# Data Model: Идентификаторы, подлежащие переименованию

Эта фича не вводит новых доменных сущностей — «модель данных» здесь это
исчерпывающий реестр категорий идентификаторов, которые переименовываются
(`settings*` → `song*`), с привязкой к функциональным требованиям `spec.md`.

## Категория 1 — Прямые Song-типизированные идентификаторы (FR-001, FR-002, FR-003)

Параметры функций, локальные переменные и поля классов, чей объявленный или
выводимый тип — ровно `Song`. Переименование: `settings` → `song` (или
осмысленное производное имя при конфликте, см. `spec.md` Edge Cases).

| Файл | Прямые `settings: Song`-параметры | `val`/`var settings` (типизированные/выводимые) |
|---|---|---|
| `controllers/ApiController.kt` | 2 | 111 |
| `controllers/MainController.kt` (karaoke-app) | 0 | 60 |
| `UtilsPictures.kt` | 12 | 0 |
| `Utils.kt` | 10 | 8 |
| `HealthReport.kt` | 9 | 2 |
| `controllers/PublicPlayerController.kt` (karaoke-web) | 5 | 8 |
| `UtilsAI.kt` | 5 | 0 |
| `controllers/SongEditorController.kt` | 2 | 7 |
| `services/PlayerMp4MuxService.kt` | 2 | 0 |
| `Functions.kt` | 2 | 0 |
| `model/Song.kt` | 1 | 4 |
| `model/SongRenderContext.kt` (свойство конструктора) | 1 | 1 |
| `KaraokeProcess.kt` | 1 | 1 |
| `KaraokePlatformPublication.kt` | 1 | 0 |
| `KaraokePlatform.kt` | 1 | 0 |
| `controllers/PublicCartController.kt`, `PublicApiController.kt`, `PublicSubscriptionController.kt`, `PublicSongEditorController.kt`, `MainController.kt` (karaoke-web) | 0 | 1–3 каждый |
| `mlt/Mlt.kt`, `mlt/mko/*.kt` (11 файлов), `KaraokeProcessWorker.kt`, `ExportAlignmentDataset.kt` | 0 | 1 каждый |

Итого: 54 прямые сигнатуры `settings: Song` (в 14 файлах) + 227 `val`/`var`-
объявлений (в 30 файлах) = 281 идентификатор суммарно (точный подсчёт на
момент `/speckit-analyze`, заменяет более ранний приблизительный «14
сигнатур» / «~279 объявлений» из черновика `/speckit-specify` — там «14»
было числом файлов, а не вхождений). Точное число подтверждается
grep-проходом перед началом реализации (`tasks.md`, T002).

## Категория 2 — Производные идентификаторы (FR-013)

Не типизированы буквально как `Song`, но по контексту хранят значение/
идентификатор/коллекцию `Song`. Переименование распространяется на них:

| Идентификатор | Файл(ы) | Новое имя |
|---|---|---|
| `settingsId: Long`/`Int` (локальные/поля, не пересекающие HTTP/JSON-контракт) | `Utils.kt`, `MainController.kt` (внутренние), `HealthReport.kt` (параметры функций), `KaraokeProcessWorker.kt`, `StemJobPollScheduler.kt`, `StemJobProcessing.kt` | `songId` |
| `settingsList: List<Song>` | `Utils.kt` | `songList` |
| `settingsLocal` | `KaraokeProcessWorker.kt` | `songLocal` |
| `settingsByAuthor`, `settingsByAlbum` | `model/Zakroma.kt` | `songsByAuthor`, `songsByAlbum` |

## Категория 3 — DTO-поля (JSON-контракт, FR-010)

| DTO | Поле было | Поле стало | Backend-потребители (создание/чтение) | Frontend-потребители (webvue3) |
|---|---|---|---|---|
| `HealthReportDTO` | `settingsId: Long` | `songId: Long` | `HealthReport.kt` (фабричные методы, `ApiController.kt`) | `Common/HealthReport/store.js` (`item.settingsId`), `Common/HealthReport/components/HealthReportTableBody.vue` (`:key`), `Common/HealthReport/components/HealthReportTableHeader.vue` |
| `HealthReportDTO` | `settingsFileName: String` | `songFileName: String` | `HealthReport.kt` | *(проверить при реализации — на момент разведки прямых обращений по имени в webvue3 не найдено, поле передаётся, но не читается по ключу; финальная проверка — grep перед завершением задачи)* |
| `KaraokeProcessDTO` | `settingsId: Int` | `songId: Int` | `KaraokeProcess.kt` (маппинг из БД) | Нет (см. `research.md`, Решение 1) — переименование backend-only |

## Категория 4 — HTTP wire-параметры (FR-011)

| Эндпоинт | Параметр(ы) было | Параметр(ы) стало | Потребитель |
|---|---|---|---|
| `POST /changesettingsstatus` (`MainController.kt`) | `settingsId: Long` | `songId: Long` | `karaoke-app/src/main/resources/static/settings_context.js` |
| `POST /songs_update` (метод 1, `MainController.kt`, строки ~1728-1754) | `settings_id`, `settings_songName`, `settings_author`, `settings_year`, `settings_album`, `settings_track`, `settings_tags`, `settings_date`, `settings_time`, `settings_key`, `settings_bpm`, `settings_ms`, `settings_fileName`, `settings_rootFolder`, `settings_idBoosty`, `settings_idBoostyFiles`, `settings_idVk`, `settings_idDzenLyrics`, `settings_idDzenKaraoke`, `settings_idDzenChords`, `settings_idVkLyrics`, `settings_idVkKaraoke`, `settings_idVkChords`, `settings_idTelegramLyrics`, `settings_idTelegramKaraoke`, `settings_idTelegramChords`, `settings_resultVersion` (27 параметров) | Те же имена с префиксом `song_` вместо `settings_` | `songs.html`, `songs2.html`, `area_center_column.html` |
| `POST /songs_update` (метод 2, `MainController.kt`, строки ~1917-1943, те же 27 имён параметров) | аналогично (27 параметров) | аналогично | те же 3 шаблона |

Итоговое число form-параметров — точно 54 (27+27), подтверждено построчным
подсчётом (`awk` по обоим блокам `@RequestParam`); заменяет более раннюю
приблизительную оценку `/speckit-clarify` («≈60 в двух методах» и
черновую асимметричную разбивку «26/34»).

## Категория 5 — SSE-ключи (FR-012)

| Ключ было | Ключ стало | Отправитель | Получатель |
|---|---|---|---|
| `"settingsId"` (map в `healthReports(...)`) | `"songId"` | `SseNotification.kt` | `webvue3/src/components/Songs/store.js:1779` (`userEventData.settingsId`) |

## Категория 6 — Исключено из переименования (FR-004, FR-005, FR-014)

| Идентификатор/объект | Файл | Причина исключения |
|---|---|---|
| `settingsFieldPublicationId`, `settingsFieldVersionNumber` | `KaraokePlatform.kt` | Конфигурация per-платформе (`Map<String, SongField>`), не значение/идентификатор `Song` и не часть найденного DTO/HTTP/SSE-контракта. |
| `LS_SETTINGS_KEY` и другие `settings*` | `karaoke-public/src/**` | Настройки плеера пользователя — не связаны с сущностью `Song`; модуль `karaoke-public` вне области задачи. |
| Колонка `settings_id` (и любые другие физические объекты БД с этим именем) | PostgreSQL, `deploy/karaoke-db/*.sql` | Физическая схема БД не меняется (прецедент `28_rename_settings_to_songs.sql`). |
| `@KaraokeDbTableField(name = "settings_id")` — строковый аргумент | `KaraokeProcess.kt` | Аргумент указывает физическое имя колонки (см. выше) — остаётся неизменным даже при переименовании самого Kotlin-свойства. |
| `SyncTarget.key = "settings"` | `sync/SyncTarget.kt` (упомянуто в `28_rename_settings_to_songs.sql` как независимая деталь) | Используется в несохранённом в git `Karaoke.properties`; переименование сломало бы существующую конфигурацию на машине администратора без миграции конфига — вне области этой задачи. |
