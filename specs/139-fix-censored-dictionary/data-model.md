# Phase 1 Data Model: Исправление цензурирования {songNameCensored} на продакшене

Схема БД не меняется — миграций нет. Ниже — существующие сущности, участвующие в фиче, и их роль
(для контекста реализации, не для создания новых таблиц/колонок).

## Dictionary (существующая, `tbl_dictionaries`)

Одна запись = одно значение одного именованного словаря.

| Поле | Тип | Описание |
|---|---|---|
| `id` | `Long` | PK |
| `dict_name` | `String` | Имя словаря (`"Censored"`, `"Слова с Ё"`, `"Sync Ids"`) |
| `dict_value` | `String` | Значение. Для `"Censored"` — слово с разметкой маскируемой части: `сл[о]во` |
| `recordhash` | `String` | md5 для sync-диффа (см. `DictionariesSyncTarget`, Principle III) — не меняется этой фичей |

**Инвариант формата** (не в схеме, только в данных): для словаря `"Censored"` значение ДОЛЖНО
содержать `[...]` вокруг маскируемой части, иначе `getCensoredPair()` вернёт `s1 == s2`
(цензурированная форма визуально совпадает с исходной) — см. User Story 3 / FR-004. Эта фича не
меняет формат, только делает его очевиднее при вводе (UI hint, R5).

## CensoredPair (производная пара, не персистится)

Вычисляется на лету из одного `dict_value` функцией `getCensoredPair()`
(`karaoke-app/.../Extentions.kt:192`):

| Поле | Пример для `нах[у]й` | Роль |
|---|---|---|
| `s1` (uncensored key) | `нахуй` | Ключ для поиска в тексте (`\b<s1>\b`) |
| `s2` (censored value) | `нах█й` | Замена при совпадении |

## Изменение контракта функций (не данных)

Схема БД не затрагивается; меняется **сигнатура** цепочки чтения словаря — везде добавляется
опциональный параметр `database: KaraokeConnection` с дефолтом, сохраняющим текущее поведение:

| Функция | Файл | До | После |
|---|---|---|---|
| `String.censored()` | `Extentions.kt` | без параметров | `censored(database: KaraokeConnection = WORKING_DATABASE)` |
| `CensoredWordsDictionary` | `textfiledictionary/CensoredWordsDictionary.kt` | `class CensoredWordsDictionary : TextFileDictionary` (без state) | конструктор с `database: KaraokeConnection = WORKING_DATABASE`, передаваемым в `.dict` |
| `NewsTemplateService.render()` | `services/NewsTemplateService.kt` | `render(template, song, news, truncate)` | `render(template, song, news, truncate, database: KaraokeConnection = WORKING_DATABASE)` |
| `NewsTemplateService.buildReplacements()` | там же | без `database` | принимает `database`, передаёт в `.censored(database)` |
| `VkTemplateService`/`TelegramTemplateService` (аналогичные render-функции) | соотв. файлы | без `database` | аналогично, для консистентности FR-001 (все текущие потребители плейсхолдера) |

Вызывающая сторона (`SongReleaseAnnouncementService.detectAndAnnounceAvailability`/
`checkOnAirWindow`) уже имеет `database` в своей сигнатуре — меняется только то, что она теперь
передаёт его на один уровень глубже, в `render(...)`.

## Новый эндпоинт: DictionaryTestRequest / DictionaryTestResponse (FR-003)

См. `contracts/dictionary-test-endpoint.md` — не персистируемые DTO, только request/response
контракт нового REST-эндпоинта.
