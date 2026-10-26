# PKG-S3-33 — правки аудита #336 (волна E, webvue3)

> **Status**: active
> **Feature Key**: pkg-s3-33
> **Last Updated**: 2026-10-26
> **Spec**: [specs/405-pkg-s3-33/report.md](../../specs/405-pkg-s3-33/report.md)
> **Issue**: OpenProject #405 (пакет `PKG-S3-33`, волна E бэклога аудита #336)
> **Branch**: `639-pkg-s3-33`
> Per-feature документ (Constitution § VI FR-009).

## Что делает

Пакет закрывает 32 записи аудита #336 в модуле `webvue3` — админском SPA. Записи
восьми классов: **запросы без конечного срока (ERR), гонки записи состояния (RACE),
ложные «успехи» в главном окне (ERR), потерянные поля при копировании маркера (DATA),
неразличимые состояния «пусто / ошибка» (ERR), незакрытые ресурсы (LEAK), лишняя работа
на каждый кадр и на каждое нажатие (PERF), разрыв контракта API (COMPAT) и отсутствие
dev-прокси (BUILD)**. Правки сгруппированы по семи блокам:

- **Транспорт и запись настроек** (BR-481, BR-482, BR-488) — запрос получает конечный
  таймаут (`ontimeout`/`onabort`), запись настройки дебаунсируется и сериализуется по
  ключу (сервер больше не хранит промежуточный префикс фильтра), dev-сервер Vite
  проксирует `/api` на `karaoke-app`.
- **Фильтр песен** (BR-444, BR-445) — восстановление из «Истории» читает ключи по
  контракту бэкенда (`is_sync`, `filter_audio_parent_id`, `flag_free`), история грузится
  независимо от списка авторов, отказ загрузки виден пользователю.
- **Панели и таблицы** (BR-446, BR-447, BR-463, BR-464, BR-465, BR-466) — отказ
  Sponsr-синхронизации не оставляет прежний «успешный» результат; «Активный премиум»
  считается объединением источников на бэкенде, а не `max()`; фильтр дат подписок
  уходит стенным временем без зоны; флаги синхронизации меняются последовательно по
  сущности; drill-down статистики очищает прежние события и показывает ошибку; ошибки
  загрузки sync-таблицы и статуса автозапуска видны в UI.
- **Главная страница и шаблоны** (BR-475, BR-476, BR-477, BR-448) — каждая операция
  ловит отказ и показывает его; ответы-счётчики проверяются по формату; флаг
  «Auth YM» не залипает; ошибка сохранения шаблона публикации не прячет редактор;
  «Искать заново»/«Удалить результаты поиска» не выглядят как успех при отказе.
- **Редактор субтитров** (BR-452, BR-453, BR-454, BR-455, BR-456, BR-457, BR-468,
  BR-469, BR-470, BR-471, BR-472, BR-473, BR-474) — удержание кнопок транспорта
  гасится глобально, `mounted()` проверяет размонтирование после каждого `await`,
  выход из модалки поиска возвращает режим редактирования, computed не пишет в
  сохраняемое поле, хоткеи гасятся поверх открытой модалки, тег-якоря считаются
  построчно, регион маркера может быть `null`, индекс маркера нормализуется, регион
  создаётся только после готовности аудио, копия маркера копирует все поля, пустой
  список не роняет «Сдвинуть сюда первый маркер», содержимое региона пересобирается
  по сигнатуре, отказ загрузки аудио виден.
- **Мини-редактор и плеер** (BR-479, BR-480, BR-478, BR-486) — индексы маркеров
  ищутся бинарным поиском без аллокаций на кадр, `songType` добавлен в контракт
  редакторского API и в inline-данные плеера, уход со страницы во время `init()` не
  оставляет открытый `AudioContext`, `_silentOffset` считается до сборки моментов
  вспышек.

## Зачем

Каждая запись — наблюдаемый дефект, а не стилистика:

- **Запросы без срока.** Если бэкенд принял соединение и не ответил, промис оставался
  в состоянии «ожидание» навсегда: кнопка «Добавление файлов из папки» не давала ни
  ошибки, ни отклика, а «фильтр молча не сохранился».
- **Запись настройки.** Фильтр писался на сервер на каждый символ ввода («П», «Пе»,
  «Пес»…). Сервер пишет «слепо», порядок запросов не гарантирован — итоговое серверное
  значение могло оказаться промежуточным префиксом: локально фильтр показывал полное
  значение, после перезагрузки страницы — усечённое.
- **Ложные «успехи».** После неудачного импорта подписчиков Sponsr на экране оставался
  ПРЕДЫДУЩИЙ успешный результат в зелёной рамке; после неудачного сохранения шаблона
  публикации админ терял доступ к редактору и к кнопке «Сохранить», а несохранённый
  текст оставался только в памяти компонента.
- **Гонки.** Два быстрых клика по флагу sync давали два запроса с разными значениями, и
  более ранний ответ, пришедший позже, возвращал в UI старое право, тогда как в
  `KaraokeProperties` уже было новое.
- **Неразличимые состояния.** Drill-down статистики после неудачной загрузки показывал
  события ПРЕДЫДУЩЕГО пользователя под именем нового; пустая таблица синхронизации не
  отличалась от «сущностей нет».
- **Потерянные данные.** «Вставить маркеры из региона в новое место» копировал только
  подпись и цвет — терялись нота, аккорд и привязка к ладу, то есть ровно те поля,
  которые сохраняются в БД.
- **Незакрытые ресурсы.** Уход со страницы `/player/:id` во время инициализации оставлял
  открытый `AudioContext` с декодированными стемами (десятки МБ) — закрывать его было
  уже некому; отпускание кнопки перемотки вне самой кнопки оставляло интервал тикать.
- **Лишняя работа.** Индексы маркеров искались линейным проходом на каждом `timeupdate`
  (до 60 раз в секунду), тег-якоря спецтегов пересчитывались на всём префиксе текста
  для каждой строки (квадратично: 200 строк — ~121 мс на одно нажатие клавиши), а
  содержимое каждого региона пересобиралось при каждом пересчёте разметки.
- **Контракт.** Мини-редактор не получал `songType` — превью инструментала показывало
  пустую караоке-разметку вместо надписи, то есть расходилось с реальным плеером.
- **Разрыв зон.** Фильтр дат подписок уходил на сервер как UTC-строка с `Z`, а колонка
  `created_at` — это `timestamp without time zone` со стенным временем МСК: «с 10:00»
  возвращало лишние 3 часа.
- **Dev-режим.** В `webvue3/vite.config.js` не было `server.proxy` для `/api` — все
  разделы админки в dev-режиме показывали пустые таблицы без сообщения об ошибке.

## Как работает

Изменения по слоям:

| Слой | Файлы | Что изменилось |
|---|---|---|
| Транспорт | `lib/utils.js` | конечный таймаут, `ontimeout`/`onabort`, очередь записи настроек с дебаунсом и сериализацией по ключу |
| Новый общий модуль | `lib/datetime-wall-clock.js` | стенное время часового пояса сервера без суффикса `Z` |
| Конфиг сборки | `vite.config.js` | `server.proxy`/`preview.proxy` для `/api` на `karaoke-app` |
| Сторы | `Songs/filter/store.js`, `SponsrSync/store.js`, `Stats/store.js`, `Sync/store.js` | состояния ошибок, очистка устаревших данных, промисы из действий, `finally` для флагов |
| Компоненты | `Songs/filter/SongsFilterModal.vue`, `Songs/edit/SearchText.vue`, `Songs/edit/SubsEdit.vue`, `SponsrSync/SponsrSyncPanel.vue`, `Stats/MonetizationPanel.vue`, `Stats/UserEventsModal.vue`, `Stats/SongEventsModal.vue`, `Subscriptions/SubscriptionsFilterModal.vue`, `Sync/SyncTable.vue`, `SongEditor/SongKaraokeEditorModal.vue`, `SongEditor/SongKaraokeEditorView.vue` | показ ошибок, восстановление фильтра по контракту, очередь флагов, гарды и инварианты редактора |
| Представления | `views/HomeView.vue`, `views/PublishTemplatesView.vue`, `views/PlayerView.vue`, `views/StatsView.vue` | обработка отказов, локальная ошибка сохранения, отмена инициализации плеера |
| Плеер и редактор | `player/KaraokePlayer.js`, `composables/useKaraokeEditor.js` | флаг отмены инициализации, порядок `_silentOffset`, бинарный поиск индексов, `songType` в inline-данных |
| Бэкенд | `karaoke-app/.../model/MonetizationStats.kt`, `karaoke-app/.../controllers/SongEditorController.kt` | точное число активных премиумов (объединение источников), `songType` в ответе редакторского API |

Общие модули **переиспользуют** помощники предыдущих пакетов (`lib/list-loader.js`,
`lib/target-request-guard.js`, `lib/song-numeric-fields.js` из `PKG-S3-31`;
`lib/csv-export.js`, `lib/server-result.js`, `lib/property-value.js`,
`lib/song-editor-errors.js`, `lib/text-diff.js`, `lib/table-sort.js` из `PKG-S3-32`) —
свои дубликаты не создаются.

## Инварианты

| Инвариант | Где зафиксирован |
|---|---|
| У запроса есть конечный срок; таймаут и отмена отклоняют промис | `webvue3/src/lib/utils.js` (`DEFAULT_REQUEST_TIMEOUT_MS`, `createTimeoutError`) |
| Запись настройки дебаунсируется и сериализуется по ключу | `webvue3/src/lib/utils.js` (`PROP_WRITE_DEBOUNCE_MS`, `propWriteQueue`) |
| Dev-сервер проксирует `/api` на `karaoke-app` | `webvue3/vite.config.js` (`server.proxy`) |
| Фильтр из «Истории» восстанавливается по ключам бэкенда | `webvue3/src/components/Songs/filter/SongsFilterModal.vue` (`onRowClicked`) |
| История фильтров грузится независимо и показывает ошибку | `webvue3/src/components/Songs/filter/store.js` (`loadSongsHistory`) |
| Отказ Sponsr-синхронизации не оставляет прежний успешный результат | `webvue3/src/components/SponsrSync/store.js` (`sponsrSyncError`) |
| «Активный премиум» — точное число (объединение источников) | `karaoke-app/.../model/MonetizationStats.kt` (`active_premium_total`), `Stats/MonetizationPanel.vue` |
| Фильтр дат подписок уходит стенным временем без зоны | `webvue3/src/lib/datetime-wall-clock.js`, `Subscriptions/SubscriptionsFilterModal.vue` |
| Флаги sync меняются последовательно по сущности | `webvue3/src/components/Sync/SyncTable.vue` (`flagQueues`) |
| Drill-down очищает прежние события и показывает ошибку | `webvue3/src/components/Stats/store.js`, `Stats/UserEventsModal.vue`, `Stats/SongEventsModal.vue` |
| Ошибки загрузки sync-таблицы и статуса автозапуска видны | `webvue3/src/components/Sync/store.js`, `Sync/SyncTable.vue` |
| Каждая операция главной страницы ловит отказ; счётчик проверяется по формату | `webvue3/src/views/HomeView.vue` (`showError`, `isCounterResponse`) |
| Флаг «Auth YM» сбрасывается при любом исходе | `webvue3/src/views/HomeView.vue` (`finally`) |
| Ошибка сохранения шаблона локальна и не прячет редактор | `webvue3/src/views/PublishTemplatesView.vue` (`tpl.saveError`) |
| Поиск/удаление результатов поиска не выглядят как успех при отказе | `webvue3/src/components/Songs/edit/SearchText.vue` (`try/catch`) |
| Удержание кнопок гасится глобально и по потере фокуса | `webvue3/src/components/Songs/edit/SubsEdit.vue` (`onGlobalPointerRelease`) |
| `mounted()` проверяет размонтирование после каждого `await` | `webvue3/src/components/Songs/edit/SubsEdit.vue` |
| Выход из модалки поиска возвращает режим редактирования | `webvue3/src/components/Songs/edit/SubsEdit.vue` (`returnSearchText`) |
| Computed не пишет в сохраняемое поле | `webvue3/src/components/Songs/edit/SubsEdit.vue` (`getFormattedNotes`) |
| Хоткеи гасятся поверх открытой модалки | `webvue3/src/components/Songs/edit/SubsEdit.vue` (`listenerKeyDown`) |
| Тег-якоря считаются построчно | `webvue3/src/components/Songs/edit/SubsEdit.vue`, `composables/useKaraokeEditor.js` (`specTagAnchors`) |
| `region` маркера может быть `null` — снятие через хелпер | `webvue3/src/components/Songs/edit/SubsEdit.vue` (`removeMarkerRegion`) |
| Индекс вставки маркера нормализуется | `webvue3/src/components/Songs/edit/SubsEdit.vue` (`addMarker`) |
| Регион создаётся только после готовности аудио; координаты — живые | `webvue3/src/components/Songs/edit/SubsEdit.vue` (`setRegionMode`, `setMoveMode`) |
| Копия маркера копирует все поля | `webvue3/src/components/Songs/edit/SubsEdit.vue` (`pasteMarkersInRegionToNewPlace`) |
| «Сдвинуть сюда первый маркер» на пустом списке не падает | `webvue3/src/components/Songs/edit/SubsEdit.vue` (`doMarkersFirst`) |
| Содержимое региона пересобирается по сигнатуре | `webvue3/src/components/Songs/edit/SubsEdit.vue` (`getMarkerContentSignature`) |
| Отказ загрузки аудио виден пользователю | `webvue3/src/components/Songs/edit/SubsEdit.vue` (`loadSong`) |
| Уход со страницы прерывает инициализацию плеера | `webvue3/src/player/KaraokePlayer.js` (`_destroyed`, `_isDestroyed`), `views/PlayerView.vue` |
| Индексы маркеров ищутся бинарным поиском без аллокаций | `webvue3/src/composables/useKaraokeEditor.js`, `SongEditor/SongKaraokeEditorView.vue` |
| `songType` в контракте редакторского API и inline-данных | `karaoke-app/.../SongEditorController.kt`, `composables/useKaraokeEditor.js` (`buildInlinePlayerData`) |
| `_silentOffset` считается до `_buildFlashTimes()` | `webvue3/src/player/KaraokePlayer.js` (`init`) |

## Известные ловушки

1. **Промис без срока — это «вечная загрузка».** `promisedXMLHttpRequest` без
   `xhr.timeout` не завершается никогда, если сервер не ответил: `.catch` не
   срабатывает, флаг загрузки не снимается, пользователь не видит отказа.
2. **Отказ ≠ «данных нет».** Пустой список, «нет событий», пустая таблица синхронизации
   и «не удалось загрузить» — разные утверждения; для отказа нужен отдельный текст.
3. **Слепая запись на сервере.** `POST /api/setwebvueprop` пишет значение без сверки
   версии, поэтому порядок запросов решает, что сохранится. Отсюда — дебаунс и
   сериализация по ключу.
4. **`region` маркера может быть `null`.** Скрытый тип маркера, ещё не готовое аудио,
   `dummyMarker`. Любое обращение к `marker.region.*` без проверки — `TypeError`.
5. **`addRegion()` до готовности аудио обнуляет позицию региона.** `clampPosition` при
   `totalDuration === 0` даёт 0 для любого времени — регион «схлопывается» в ноль.
6. **`region.content` — DOM-элемент.** Сравнение с новым элементом всегда истинно;
   для «перерисовывать только при изменении» нужна сигнатура содержимого.
7. **Частичное копирование маркера теряет данные.** `note`/`chord`/`stringLad`/`lockLad`
   не восстанавливаются из `label` — копировать надо целиком.
8. **Computed не должен писать в маркер.** Иначе сохранённое значение поля
   перезаписывается производным при каждом пересчёте.
9. **Квадратичные вычисления на каждый кадр.** Пересчёт на префиксе для каждой строки и
   линейный проход по маркерам на каждом `timeupdate` — главные источники «тормозов»
   редактора.
10. **Глобальный флаг ошибки прячет редактор.** `v-if="!loading && !error"` делает
    ошибку локальной операции недоступной для исправления.
11. **`timestamp without time zone` не понимает зону.** Строка с `Z` сравнивается как
    стенное время — для дат подписок уходить надо без зоны, в TZ сервера.
12. **Отпускание кнопки вне кнопки не приходит элементу.** `mouseup` надо слушать на
    `window` (capture), плюс `blur`/`visibilitychange`.
13. **Поздний `await` после размонтирования.** `beforeUnmount` обнуляет `this.ws` —
    проверка `isUnmounted` нужна после КАЖДОГО `await`.
14. **Уход со страницы во время `init()` плеера.** `destroy()` обязан ставить флаг
    отмены, иначе `init()` создаст новый `AudioContext`, который уже никто не закроет.

## Проверки

- `cd webvue3 && npm run lint` — ESLint (0 ошибок).
- `cd webvue3 && npx prettier --check "src/**/*.{vue,js,ts,json}"` — форматирование.
- `cd webvue3 && npm run build` — vite build.
- `cd webvue3 && npm run test:unit` — 497 тестов (было 439; +58 регрессионных тестов
  пакета).
- Регрессионные тесты пакета: `src/**/__tests__/pkg-s3-33-*.test.js` (3 файла).

## Ссылки

- Отчёт пакета: [specs/405-pkg-s3-33/report.md](../../specs/405-pkg-s3-33/report.md)
  (по записям, «до/после», побочные эффекты, решения за владельцем).
- Источник правды: [бэклог аудита § PKG-S3-33](../../specs/336-code-audit/backlog.md)
  и карточки `specs/336-code-audit/bug-report.md` (записи BR-444…BR-488).
- Регресс-тесты: `webvue3/src/lib/__tests__/pkg-s3-33-lib.test.js`,
  `webvue3/src/components/__tests__/pkg-s3-33-admin.test.js`,
  `webvue3/src/components/Songs/edit/__tests__/pkg-s3-33-subsedit.test.js`;
  запуск — `cd webvue3 && npm run test:unit`.
- SSoT: [webvue3-audit-invariants.md](../../knowledge/system/frontend/webvue3-audit-invariants.md)
  — инварианты админского SPA, §23–33 (этот пакет);
  [vuex-patterns.md](../../knowledge/system/frontend/vuex-patterns.md) — контракт
  мутаций и actions; [filter-stores.md](../../knowledge/system/frontend/filter-stores.md) —
  хранение и server-side persistence фильтров.
- Предыдущие пакеты того же модуля: [pkg-s3-31.md](pkg-s3-31.md) (OpenProject #403),
  [pkg-s3-32.md](pkg-s3-32.md) (OpenProject #404).
