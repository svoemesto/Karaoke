# Таблица песен в админке (webvue3)

> **Status**: active
> **Feature Key**: `songs-table`
> **Last Updated**: 2026-08-03

## Что делает

Главная таблица песен в admin SPA `webvue3` (`SongsTable.vue`) отображает список песен из `tbl_settings` с пагинацией, сортировкой, фильтрами, bulk-операциями и health-report. Поддерживает inline-открытие редактора песни, назначение заданий редакторам и открытие онлайн-плеера.

## Зачем

Администратору нужен быстрый обзор всего каталога: статус песни, платформы публикации, связи с другими песнями (`root`, `audio_parent_id`) и массовые операции. Без таблицы управление 18k+ песен невозможно.

## Как работает (кратко)

1. Данные загружаются через `POST /api/songsdigests` (`ApiController.apisSongsDigests`) в виде лёгких `SongDTOdigest`.
2. Фильтры хранятся в Vuex-модуле `webvue3/src/components/Songs/filter/store.js` и персистятся сервер-side через `setWebvueProp`/`getWebvueProp`.
3. `SongsTable.vue` рендерит `BTable` из `bootstrap-vue-next`; колонки и ячейки задаются через `songDigestFields()` и именованные слоты `#cell(<key>)`.
4. Пагинация сохраняется в Vuex (`songsTableCurrentPage`), чтобы при переходе на другой роут и обратно пользователь оставался на той же странице.
5. SSE-события `recordChange`/`recordDelete` обновляют строки без полного перезапроса.
6. Связанные песни (`root_id`, `audio_parent_id`) показываются в колонках «root» и «A-root»; при наведении появляется тултип с автором, годом, альбомом и названием песни, загружаемый лениво через `GET /api/song/{id}/shortinfo`.

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
