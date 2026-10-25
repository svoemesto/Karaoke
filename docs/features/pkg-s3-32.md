# PKG-S3-32 — правки аудита #336 (волна E, webvue3)

> **Status**: active
> **Feature Key**: pkg-s3-32
> **Last Updated**: 2026-10-26
> **Spec**: [specs/404-pkg-s3-32/report.md](../../specs/404-pkg-s3-32/report.md)
> **Issue**: OpenProject #404 (пакет `PKG-S3-32`, волна E бэклога аудита #336)
> **Branch**: `637-pkg-s3-32`
> Per-feature документ (Constitution § VI FR-009).

## Что делает

Пакет закрывает 40 записей аудита #336 в модуле `webvue3` — админском SPA.
Записи восьми классов: **гонки ответов (RACE), неразличимые состояния «пусто /
ошибка» (ERR), ложные «успехи» (COMPAT), потерянные данные (DATA), тихие отказы
(RULE), незакрытые ресурсы (LEAK), лишняя работа на каждый ввод (PERF) и
CSV-формулы (SEC)**. Правки сгруппированы по шести блокам:

- **Картинки** (BR-395, BR-396) — снимок автосейва собирается из отправленного
  payload, а не из живого `pictureCurrent`; таймер и запрос отменяются при
  размонтировании; сетевые вызовы ушли из мутаций в actions; удаление ждёт ответа,
  убирает строку из дайджеста и чистит картинку; ошибка загрузки видна пользователю.
- **Процессы** (BR-398, BR-400, BR-401, BR-402, BR-403, BR-408) — CSV-экспорт
  отчёта нейтрализует формулы; блок ошибки загрузки вынесен из-под `v-if="process"`
  и появились состояние `loading|loaded|error` с кнопками «Повторить»/«Закрыть»;
  отказ опроса async-задачи даёт терминальный `FAILED` и отчёт (без unhandled
  rejection); из таблицы вычитаются только id своей операции, а для `bulk_update`
  список перезагружается; audit-лог привязан к процессу; применение фильтра
  сбрасывает страницу и грузит её один раз, с `limit`/`offset` и `catch`.
- **Настройки, акции, публикации** (BR-404, BR-405, BR-406, BR-412, BR-413) —
  значение настройки проверяется по типу до отправки, при отказе диалог
  переоткрывается с введённым значением; дайджест настроек грузится при открытии
  раздела; отказ загрузки не залипает флагом; `0L`/`false` в теле ответа больше не
  выдаются за успех; тихая SSE-перезагрузка повторяет cond-вид (`filterCond`).
- **Плейлисты и пользователи сайта** (BR-414, BR-415, BR-420, BR-424) — detail
  плейлиста принадлежит последнему запрошенному id, есть состояния загрузки и
  ошибки; дайджест плейлистов привязан к пользователю; исход операций над
  пользователем проверяется по телу ответа; фильтр пишется на сервер один раз по
  кнопке «Применить», гидратация параллельна и без записи.
- **Онлайн-редактор** (BR-418, BR-419, BR-421, BR-422, BR-423, BR-425, BR-427,
  BR-428, BR-430, BR-431, BR-433) — правка во время сохранения не теряется и не
  помечается сохранённой; модалка ревью не ловушка; цикл назначения
  останавливается при закрытии; сбой поиска не выглядит как пустой результат;
  дайджест заданий грузится с поколением запроса; код `song_not_found_in_local_db`
  разбирается; `defaultTarget` читается из значения свойства; настройки редактора
  не затираются; потеря фокуса окна останавливает удержание клавиш; скрытие плеера
  уничтожает его инстанс; ошибка загрузки дайджеста заданий видна.
- **Песни** (BR-429, BR-432, BR-434, BR-435, BR-436, BR-437, BR-438, BR-439,
  BR-440, BR-441, BR-442, BR-443) — «похожие песни» и обложка альбома различают
  отказ и отсутствие; diff текста ограничен по длине; страница считается по
  порядку таблицы; массовые операции не разбирают пустое тело; ответ статусов
  назначений проверяется на актуальность; обход каталога без квадратичной
  сложности; мёртвые параметры пагинации убраны; «Repair All» идёт с лимитом
  параллелизма, прогрессом и сводкой ошибок; таймеры очищаются при уходе со
  страницы; `threadId` передаётся в массовые операции; параметры фильтра песен
  собираются одним builder'ом.

## Зачем

Каждая запись — наблюдаемый дефект, а не стилистика:

- **Ложные «успехи».** Бэкенд сообщает об отказе ТЕЛОМ при коде 200 (`0L` для
  `promorules`/`siteusers`, `false` для удаления), а фронт тело не проверял:
  «правило не найдено» выглядело как сохранённое действие. Обратная сторона:
  эндпоинты без возвращаемого типа отвечают пустым телом, и `JSON.parse('')`
  ронял обработчик — подтверждения о завершении не появлялось вовсе.
- **Ложное «пусто».** Отказ загрузки показывался как «похожих песен не найдено»,
  «Картинки альбома пока нет», «Ничего не найдено» — утверждения о данных, которые
  при отказе неверны.
- **Гонки.** Ответ audit-лога приходил для предыдущего процесса; поздний ответ
  статусов назначений затирал статусы текущей страницы; тихая SSE-перезагрузка
  подменяла cond-выборку обычной сеткой публикаций.
- **Потерянные данные.** Снимок автосейва брался из живого объекта: правки,
  сделанные во время запроса, помечались сохранёнными и молча терялись.
- **Ресурсы.** Таймеры удержания клавиш продолжали тикать после потери фокуса окна;
  инстанс плеера продолжал работать после «Скрыть плеер»; debounce-таймер активной
  страницы срабатывал после ухода со страницы.
- **Лишняя работа.** Word-level diff строил полную LCS-матрицу (квадратичная память
  и время на длинном тексте); обход каталога песен делал `filter`/`find` внутри
  цикла (≈18 млн сравнений при perPage=1000); батч health-report уходил на сервер
  дважды на каждую смену страницы; «Repair All» слал до тысячи тяжёлых запросов без
  лимита параллелизма.
- **Фильтры.** Ключ «Free» уходил под именем, которого бэкенд не принимает, а
  фильтр «Задание редактора» терял `target` — статусы назначений читались не из
  той БД.
- **Безопасность.** CSV-экспорт отчёта о bulk-операции не нейтрализовал значения,
  начинающиеся с `= + - @` — табличный редактор исполнял их как формулы.

## Как работает

Изменения по слоям:

| Слой | Файлы | Что изменилось |
|---|---|---|
| Транспорт | `lib/utils.js` | поддержка `AbortSignal` (`createAbortError`) — запрос можно отменить |
| Новые общие модули | `lib/csv-export.js`, `lib/server-result.js`, `lib/property-value.js`, `lib/song-editor-errors.js`, `lib/text-diff.js`, `lib/table-sort.js` | правила, которые раньше копировались по компонентам (CSV-формулы, разбор тела ответа, проверка значения настройки, коды ошибок редактора, diff с пределом длины, порядок строк таблицы) |
| Сторы | `Pictures/store.js`, `Processes/store.js`, `Properties/store.js`, `Promotions/store.js`, `Publish/store.js`, `SitePlaylists/store.js`, `SiteUsers/store.js`, `SongEditor/store.js`, `Songs/filter/store.js`, `Songs/store.js` | сетевые вызовы из мутаций — в actions; поколения запросов; состояния ошибок в state; разбор тела ответа; ключи приоритета SHEETSAGE |
| Компоненты | `PictureEdit.vue`, `ProcessEdit.vue`, `ProcessDeleteModal.vue`, `ProcessAuditModal.vue`, `ProcessesTable.vue`, `ProcessesFilterModal.vue`, `ProcessBulkReportModal.vue`, `PropertiesTable.vue`, `PromotionsTable.vue`, `SitePlaylistDetailModal.vue`, `UserPlaylistsModal.vue`, `SiteUserEdit.vue`, `SiteUsersFilterModal.vue`, `AssignModal.vue`, `ReviewModal.vue`, `SongEditorTable.vue`, `SongKaraokeEditorModal.vue`, `SongKaraokeEditorView.vue`, `SongsTable.vue`, `AiTextEditorModal.vue`, `AlbumCoverModal.vue`, `FamilySongsModal.vue`, `SongsFilterModal.vue`, `App.vue` | состояния загрузки/ошибки, блоки ошибок в шаблонах, отмена таймеров, лимиты параллелизма, разбор ответов |

Общие модули **переиспользуют** помощники предыдущего пакета (`lib/list-loader.js`,
`lib/target-request-guard.js`, `lib/song-numeric-fields.js` из `PKG-S3-31`) — свои
дубликаты не создаются.

## Инварианты

| Инвариант | Где зафиксирован |
|---|---|
| Снимок автосейва собирается из ОТПРАВЛЕННОГО payload, а не из живого объекта | `webvue3/src/components/Pictures/store.js` (`savePicture`) |
| Таймер автосейва и запрос в полёте отменяются при размонтировании | `webvue3/src/components/Pictures/edit/PictureEdit.vue`, `webvue3/src/lib/utils.js` (`obj.signal`) |
| В мутациях нет сетевых запросов; удаление/сохранение ждут ответа и не закрывают UI до успеха | `webvue3/src/components/Pictures/store.js`, `PictureEdit.vue` |
| Блок ошибки загрузки доступен при `process === null`; есть состояние `loading/loaded/error` | `webvue3/src/components/Processes/edit/ProcessEdit.vue`, `Processes/delete/ProcessDeleteModal.vue` |
| Отказ опроса async-задачи даёт терминальный `FAILED` и отчёт (без unhandled rejection) | `webvue3/src/components/Processes/store.js` (`pollBulkTask`) |
| Из таблицы вычитаются только id СВОЕЙ операции; для `bulk_update` список перезагружается | `webvue3/src/components/Processes/store.js` (`reloadProcessesItems`) |
| Audit-лог чистится до запроса и применяется только к текущему id процесса | `webvue3/src/components/Processes/store.js` (`loadAudit`) |
| Применение фильтра сбрасывает страницу и грузит её один раз, с `limit`/`offset` и `catch` | `webvue3/src/components/Processes/ProcessesTable.vue`, `Processes/filter/ProcessesFilterModal.vue` |
| CSV-экспорт нейтрализует формулы; числа не префиксуются | `webvue3/src/lib/csv-export.js` |
| Значение настройки проверяется по типу ДО отправки; ошибка видна, диалог переоткрывается | `webvue3/src/lib/property-value.js`, `webvue3/src/components/Properties/PropertiesTable.vue` |
| Раздел «Настройки» грузит дайджест при открытии | `webvue3/src/components/Properties/PropertiesTable.vue` (`mounted`) |
| Загрузка дайджеста снимает флаг в `finally`, текст ошибки в state и в UI | `webvue3/src/components/Properties/store.js`, `webvue3/src/lib/list-loader.js` |
| «0»/«false» в теле ответа (код 200) — отказ, а не успех | `webvue3/src/lib/server-result.js`, `Promotions/store.js`, `SiteUsers/store.js` |
| Тихая SSE-перезагрузка повторяет cond-вид текущей выборки | `webvue3/src/components/Publish/store.js` (`publishFilterCond`) |
| Detail плейлиста применяется только к последнему запрошенному id | `webvue3/src/components/SitePlaylists/store.js`, `SitePlaylistDetailModal.vue` |
| Дайджест плейлистов несёт владельца; модалка показывает только свой `siteUserId` | `webvue3/src/components/SiteUsers/UserPlaylistsModal.vue` |
| Фильтр пишется на сервер один раз по кнопке «Применить»; гидратация параллельна и без записи | `webvue3/src/components/SiteUsers/filter/store.js` (`persistSiteUsersFilter`, `restoreSiteUsersFilter`) |
| Правка во время сохранения не помечается сохранённой; сохранение перезапускается | `webvue3/src/components/SongEditor/SongKaraokeEditorModal.vue` |
| Модалка ревью не ловушка: «Закрыть» доступна без задания | `webvue3/src/components/SongEditor/ReviewModal.vue`, `SongEditorTable.vue` |
| Цикл массового назначения прерывается при закрытии; родитель уведомляется о назначенных песнях | `webvue3/src/components/SongEditor/AssignModal.vue` |
| Сбой поиска показывается как ошибка, а не как «Ничего не найдено» | `webvue3/src/components/SongEditor/AssignModal.vue` |
| Дайджест заданий грузится с поколением запроса; ошибка видна | `webvue3/src/components/SongEditor/store.js`, `SongEditorTable.vue` |
| Код `song_not_found_in_local_db` разбирается отдельно от общего текста | `webvue3/src/lib/song-editor-errors.js` |
| `defaultTarget` читается из значения свойства (`prop.value`), а не из DTO | `webvue3/src/components/SongEditor/store.js` (`loadEditorDefaultTarget`) |
| Настройки редактора: полный набор ключей, partial мержится в сохранённый объект | `webvue3/src/components/SongEditor/useReviewModalFormat.js` |
| Потеря фокуса окна останавливает удержание клавиш и восстанавливает `playbackRate` | `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` |
| Скрытие плеера уничтожает его инстанс | `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` (`destroyPlayer`) |
| Сбой «похожих песен» и обложки показывается как ошибка, а не как «данных нет» | `webvue3/src/components/Songs/edit/FamilySongsModal.vue`, `Songs/edit/AlbumCoverModal.vue` |
| Diff текста вырезает общий префикс/суффикс и не считается при превышении предела | `webvue3/src/lib/text-diff.js` |
| Страница считается по тому же порядку, что отдан в таблицу | `webvue3/src/lib/table-sort.js`, `webvue3/src/components/Songs/SongsTable.vue` |
| Тело `void`-эндпоинтов не разбирается; успех — по факту 2xx | `webvue3/src/components/Songs/SongsTable.vue` (`reportBulkOperation`) |
| Ответ статусов назначений применяется только к текущему набору id | `webvue3/src/components/Songs/SongsTable.vue`, `Songs/edit/SongEdit.vue` |
| Обход каталога — по карте «id → песня», без `filter`/`find` в цикле; батч HR — один раз | `webvue3/src/components/Songs/SongsTable.vue` (`buildSongById`) |
| В запрос списка песен не уходят мёртвые `page`/`perPage` | `webvue3/src/components/Songs/SongsTable.vue` (`loadData`) |
| Массовая операция идёт очередью с лимитом параллелизма, с прогрессом и сводкой | `webvue3/src/components/Songs/SongsTable.vue` (`REPAIR_ALL_CONCURRENCY`) |
| Таймеры страницы очищаются при уходе со страницы | `webvue3/src/components/Songs/SongsTable.vue` (`beforeUnmount`) |
| `threadId` передаётся в массовые операции; у SHEETSAGE свой ключ приоритета | `webvue3/src/components/Songs/SongsTable.vue`, `webvue3/src/components/Songs/store.js` |
| Параметры фильтра песен собираются одним builder'ом; ключи ≡ контракту бэкенда | `webvue3/src/components/Songs/filter/store.js` (`getSongsFilter`) |

## Известные ловушки

1. **«Успех» без тела.** `promisedXMLHttpRequest` резолвит строку. Эндпоинты
   `void` (`/api/songs/createkaraokeall`, `createdemucs2all`, `createdemucs5all`,
   `sheetsageall`, `createmp3karaokeall`, `createsymlinksall`,
   `createforcedalignmarkersforall`) отвечают **пустым телом** — `JSON.parse('')`
   бросает `SyntaxError`, и `.then` отклонялся без `.catch`: подтверждения не было,
   ошибка — только в консоли. Разбирать такое тело нельзя; успех определяется по
   факту 2xx.
2. **Отказ в теле при коде 200.** `promorules` (`create`/`update`) и `siteusers`
   (`update`) возвращают `Long`, `promorules/delete` и `siteusers/{ban,unban,delete}` —
   `Boolean`. `0`/`false` — отказ. Проверка — `parseServerId`/`parseServerFlag`
   (`lib/server-result.js`).
3. **«Нет данных» ≠ «отказ».** Пустой список настроек, «похожих песен не найдено»,
   «Картинки альбома пока нет», «Ничего не найдено» — это утверждения о данных.
   При отказе нужен отдельный текст ошибки, а прежний (не обновлённый) результат
   помечается как устаревший.
4. **Снимок автосейва — из отправленного payload.** Иначе правки во время запроса
   помечаются сохранёнными и теряются при закрытии модалки.
5. **`b-table` сортирует сам.** Страница обязана считаться по тому же порядку, что
   отдан в таблицу (`sortTableItems`), иначе массовые операции применяются к
   другому набору строк, чем видит пользователь.
6. **Ключи фильтра ≡ контракт бэкенда.** `flagFree` (не `filterFlagFree`),
   `target` при `filterAssignmentStatus`. Геттер и модалка обязаны собирать
   параметры одним builder'ом.
7. **Массовая операция — очередь с лимитом.** «Repair All» шёл по одному запросу
   на песню без ожидания и без лимита (до 1000 тяжёлых задач). Лимит —
   `REPAIR_ALL_CONCURRENCY`, плюс прогресс и сводка ошибок.
8. **Тихое обновление повторяет условия вида.** Фоновая SSE-перезагрузка обязана
   передавать активный `filterCond`, иначе cond-выборка подменяется обычной сеткой.
9. **Потеря фокуса останавливает удержание клавиш.** `keyup` не приходит при
   `blur`/`visibilitychange` — таймеры продолжают тикать, а «играть» — каждые 16 мс.
10. **Скрытие плеера уничтожает его.** WebAudio и `requestAnimationFrame` продолжают
    работать без UI управления.
11. **Настройки редактора — полный набор ключей.** Локальная копия
    `loadEditorSettings`/`saveEditorSettings` (в `webvue3` ключ localStorage общий с
    публичным редактором) знает только `textFontSize`/`previewFontSize`; запись из
    ReviewModal затирала остальные настройки публичного редактора.
12. **`defaultTarget` читается из `prop.value`.** `getPropertyValuePromise` возвращает
    DTO, а сравнение со строкой всегда было ложным — `defaultTarget` всегда был
    `remote`.
13. **Пустой ответ ≠ «отказ» для `void`-эндпоинтов, но `0`/`false` — отказ для
    `Long`/`Boolean`.** Оба правила живут рядом; путать их нельзя.
14. **Страница сбрасывается при смене фильтра.** Таблица грузит страницу однажды и
    с `limit`/`offset` из своих `perPage`/`currentPage`; модалка фильтра не грузит
    список сама.

## Проверки

- `cd webvue3 && npm run lint` — ESLint (0 ошибок).
- `cd webvue3 && npx prettier --check "src/**/*.{vue,js,ts,json}"` — форматирование.
- `cd webvue3 && npm run build` — vite build.
- `cd webvue3 && npm run test:unit` — 439 тестов (было 315; +124 регрессионных теста
  пакета).
- Регрессионные тесты пакета: `src/**/__tests__/pkg-s3-32-*.test.js` (11 файлов).

## Ссылки

- Отчёт пакета: [specs/404-pkg-s3-32/report.md](../../specs/404-pkg-s3-32/report.md)
  (по записям, «до/после», побочные эффекты, решения за владельцем).
- Источник правды: [бэклог аудита § PKG-S3-32](../../specs/336-code-audit/backlog.md)
  и карточки `specs/336-code-audit/bug-report.md` (записи BR-395…BR-443).
- Регресс-тесты: `webvue3/src/**/__tests__/pkg-s3-32-*.test.js`
  (11 файлов, 124 кейса); запуск — `cd webvue3 && npm run test:unit`.
- SSoT: [webvue3-audit-invariants.md](../../knowledge/system/frontend/webvue3-audit-invariants.md)
  — инварианты админского SPA, §13–22 (этот пакет);
  [vuex-patterns.md](../../knowledge/system/frontend/vuex-patterns.md) — контракт
  мутаций и actions; [filter-stores.md](../../knowledge/system/frontend/filter-stores.md) —
  хранение и server-side persistence фильтров.
- Предыдущий пакет того же модуля: [pkg-s3-31.md](pkg-s3-31.md)
  (OpenProject #403).
