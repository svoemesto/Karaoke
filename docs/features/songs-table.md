# Таблица песен в админке (webvue3)

> **Status**: active
> **Feature Key**: `songs-table`
> **Last Updated**: 2026-08-06 (удалены 18 столбцов-флагов публикации, см. ниже)

> Цвета строк таблицы определяются производным состоянием песни — см.
> [song-state-colors.md](./song-state-colors.md). Эта фича отвечает только за рендер;
> классификация и приоритеты — в `song-state-colors.md`.

## Что делает

Главная таблица песен в admin SPA `webvue3` (`SongsTable.vue`) отображает список песен из `tbl_settings` с пагинацией, сортировкой, фильтрами, bulk-операциями и health-report. Поддерживает inline-открытие редактора песни, назначение заданий редакторам и открытие онлайн-плеера.

## Зачем

Администратору нужен быстрый обзор всего каталога: статус песни, связи с другими песнями (`root`, `audio_parent_id`) и массовые операции. Без таблицы управление 18k+ песен невозможно.

## Состав колонок (после фичи #156, 2026-08-06)

Шапка таблицы содержит 18 видимых колонок в порядке:
ID, Композиция, Исполнитель, Год, Альбом, №, Дата, Время, Tags, Status, V, BOO, Редактор, ▶ (player), ▶ (playerDemo), DE (`flagPlayerDemo`), TG (`telegramPublish` — badge автопубликации демо в Telegram), FR (`flagFree`).

**Удалено в #156 (2026-08-06)**: 18 узких столбцов-флагов публикации по платформам/типам контента —
`SP` (`flagSponsr`), `VG` (`flagVk`),
`ZL`/`ZK`/`ZC`/`ZM` (`flagDzenLyrics/Karaoke/Chords/Melody`),
`VL`/`VK`/`VC`/`VM` (`flagVkLyrics/Karaoke/Chords/Melody`),
`TL`/`TK`/`TC`/`TM` (`flagTelegramLyrics/Karaoke/Chords/Melody`),
`ML`/`MK`/`MC`/`MM` (`flagMaxLyrics/Karaoke/Chords/Melody`).
Удалены: 18 объектов из `fields[]`, 18 ячеек-шаблонов `<template #cell(flagX)>`, 18 CSS-блоков `.fld-flag-*` и 4 метода `playLyrics/Karaoke/Chords/Tabs` в `SongsTable.vue`; 10 определений из `fieldSongParams[]` в `store.js`. **Бэкенд и БД не затронуты**: поля `flag_sponsr`, `flag_vk`, `flag_dzen_*`, `flag_vk_*`, `flag_telegram_*`, `flag_max_*` и `processColor*` продолжают вычисляться и сохраняться как прежде — удаление чисто визуальное (FR-006). Метод `playDemo` и ячейки `flagPlayerDemo`/`telegramPublish`/`flagFree` остаются. Таблица публикаций `PublishTableBodyTd.vue` и редактор `SongEdit.vue` не затронуты (FR-007).

## Как работает (кратко)

1. Данные загружаются через `POST /api/songsdigests` (`ApiController.apisSongsDigests`) в виде лёгких `SongDTOdigest`.
2. Фильтры хранятся в Vuex-модуле `webvue3/src/components/Songs/filter/store.js` и персистятся сервер-side через `setWebvueProp`/`getWebvueProp`.
3. `SongsTable.vue` рендерит `BTable` из `bootstrap-vue-next`; колонки и ячейки задаются через `songDigestFields()` и именованные слоты `#cell(<key>)`.
4. Пагинация сохраняется в Vuex (`songsTableCurrentPage`), чтобы при переходе на другой роут и обратно пользователь оставался на той же странице.
5. SSE-события `recordChange`/`recordDelete` обновляют строки без полного перезапроса.
6. Связанные песни (`root_id`, `audio_parent_id`) показываются в колонках «root» и «A-root»; при наведении появляется тултип с автором, годом, альбомом и названием песни, загружаемый лениво через `GET /api/song/{id}/shortinfo`.

## Ручной выбор похожей версии и аудиополя

Кнопка «Похожие версии песни» в `SongEdit.vue` открывает модалку `FamilySongsModal.vue` со списком кандидатов из семьи текущей песни (`id`/`root_id`) и ручного поиска по названию. Клик по строке вызывает `POST /api/song/selectfamilysong`, который применяет helper `applyFamilySongSelection(song, another, deltaMs, audioParentId, audioSimilarityPercent, audioDeltaMs)`. Все три аудиополя (`audio_parent_id`, `audio_similarity_percent`, `audio_delta_ms`) устанавливаются до единственного `Song.saveToDb()`, поэтому попадают в один SQL `UPDATE` вместе с текстом, маркерами, `root_id` и условным `id_status`.

### Правило выбора аудиополей

| Состояние результата сверки в модалке | `audio_parent_id` | `audio_similarity_percent` | `audio_delta_ms` |
|---|---:|---:|---:|
| Успешная сверка (`status === 'done'`), любое значение | `another.id` | `similarityPercent` из результата | `deltaMs` из результата (signed) |
| Сверка не выполнялась / ошибка / в процессе | `another.id` | `0` | `0` |
| Клик по строке текущей песни (`current === true`) | без изменений | без изменений | без изменений |

Нулевые значения `0%` и `0 мс` при `status === 'done'` сохраняются как валидный результат, а не обрабатываются как «сверки не было». Повторный выбор другого кандидата атомарно заменяет все три поля — старые метрики не остаются.

### Контракт endpoint

- `POST /api/song/selectfamilysong` принимает обязательные `id`, `idAnother` и опциональные `deltaMs` (signed Long), `audioSimilarityPercent` (Int 0..100). Параметр `audioParentId` от клиента НЕ принимается — сервер вычисляет его из загруженного `another.id`.
- Ответ `SelectFamilySongResultDto` содержит `rootId`, `idStatus`, `audioParentId`, `audioSimilarityPercent`, `audioDeltaMs` — нормализованные значения, перечитанные из БД после `saveToDb()`.
- Self-selection (`id == idAnother`) → 400 Bad Request с сообщением «выбор текущей песни недопустим».
- Частичная пара метрик (только одна из `audioSimilarityPercent`/`deltaMs`) → 400 Bad Request с сообщением «Метрики сверки должны передаваться парой».
- Невалидный диапазон процента → 400 Bad Request.
- После `saveToDb()` backend перечитывает запись и проверяет, что три аудиополя действительно записаны; при расхождении возвращается ошибка (без ложного `success`).

### In-flight guard и UX

- Кнопка открытия модалки получает `:disabled="isSelectingFamilySong"` и текст «Применение…», чтобы исключить повторный клик во время запроса.
- Модалка закрывается только после успешного ответа. При ошибке остаётся открытой, показывается toast через `showTelegramToast` с текстом ошибки (`error.message`).
- После успешного ответа применяется Vuex-mutation `applyFamilySelectionResult`, которая обновляет `currentSong` и `snapshotSong` пятью полями (`rootId`, `idStatus`, `audioParentId`, `audioSimilarityPercent`, `audioDeltaMs`) одним коммитом. Это предотвращает повторную отправку этих полей через debounce-autosave.

### Ограничения

- `autoAssignOriginalByWaveform` (автоматический поиск аудио-родителя) использует тот же helper, но вызывает его без аудиопараметров — поведение этого сценария не меняется.
- `audioCompareHistory`, схема `tbl_songs`, `SongDTO`, `SyncRegistry`, recordhash-триггеры и публичный frontend НЕ изменяются.
- `audioParentId` относится к фактически выбранной строке, а не к её собственному `audioParentId` — flattening по цепочке не применяется.

## Инварианты / правила

- **MUST**: Новые колонки добавляются в `songDigestFields()` и имеют matching `#cell(<key>)` слот ([CONTRIBUTING.md#vue-table-layout-fixed](../../CONTRIBUTING.md)).
- **MUST**: Ширина каждой колонки задаётся явно (`minWidth`/`maxWidth`) из-за `table-layout: fixed` ([CONTRIBUTING.md#vue-table-layout-fixed](../../CONTRIBUTING.md)).
- **MUST**: Фильтры песен реализуются в `Songs/filter/store.js` + `SongsFilterModal.vue` и персистятся через `setWebvueProp`/`getWebvueProp` ([AGENTS.md#персистентность-страницы-пагинации-в-webvue3](../../AGENTS.md)).
- **MUST**: backend-фильтры преобразуются в SQL через `Song.getWhereList()` в `Song.kt` ([constitution.md#ii-сырой-jdbc-+-дифф-по-хэшам](../../.specify/memory/constitution.md)).
- **SHOULD**: Тултипы для связанных песен не должны делать N+1 запросов — использовать кэш в компоненте и lightweight endpoint `/api/song/{id}/shortinfo`.
- Иконка основного онлайн-плеера (`#cell(player)`) активна начиная со статуса `idStatus >= 4` (`MARKERS_CREATED`) — снижено с прежнего `>= 6` (`READY`), чтобы редактор мог проверить синхронизацию меток раньше финальной верификации (`specs/125-player-status-gate/`). Колонка DEMO-плеера (`#cell(playerDemo)`) сохраняет прежний порог `>= 6` — её доступность завязана на отдельную бизнес-логику показа демо-контента.

## Известные ловушки

- Если в `BTable` не задать явную ширину колонки, ячейки могут съехать или обрезаться из-за `table-layout: fixed` ([CONTRIBUTING.md#vue-table-layout-fixed](../../CONTRIBUTING.md)).
- Добавление нового фильтра только на фронте без обновления `Song.getWhereList()` и `ApiController.apisSongsDigests` приведёт к тому, что фильтр не будет применяться.
- Поля `root_id` и `audio_parent_id` в БД используют `0` как «нет значения» — UI должен отображать `—` или не показывать тултип для таких ячеек.

## Ссылки на ключевые классы/файлы

- [`webvue3/src/components/Songs/SongsTable.vue`](../../webvue3/src/components/Songs/SongsTable.vue) — основной компонент таблицы.
- [`webvue3/src/components/Songs/filter/SongsFilterModal.vue`](../../webvue3/src/components/Songs/filter/SongsFilterModal.vue) — модальное окно фильтров.
- [`webvue3/src/components/Songs/filter/store.js`](../../webvue3/src/components/Songs/filter/store.js) — Vuex-модуль фильтров.
- [`webvue3/src/main.js`](../../webvue3/src/main.js) — регистрация директивы `v-b-tooltip`.
- [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt) — endpoints `/api/songsdigests` и `/api/song/{id}/shortinfo`.
- [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTOdigest.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTOdigest.kt) — лёгкий DTO для таблицы.
- [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongShortInfoDto.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongShortInfoDto.kt) — DTO для тултипа.
- [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt) — загрузка списка и построение `WHERE`.
