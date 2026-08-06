# Song State Colors

> **Status**: active
> **Feature Key**: `song-state-colors`
> **Last Updated**: 2026-08-06

> Канонические пять состояний песен (DONE / TODAY / ON_AIR / EXCLUSIVE / IN_WORK) и их цвета в админке. Заменяет устаревшую 16-значную палитру, привязанную к публикациям в социальных сетях.

## Что делает

Заменяет устаревшую 16-значную палитру `SongState` (ALL_DONE / OVERDUE / WO_TG / WO_VK / WO_DZEN /
WO_VKG / BOOSTY_SPONSR и т.д.), привязанную к публикациям в социальных сетях, на пять производных
состояний онлайн-плеера: **`DONE`, `TODAY`, `ON_AIR`, `EXCLUSIVE`, `IN_WORK`**. Каждое вычисляется
по `Song.idStatus`, `Song.free`, `Song.dateTimePublish` и текущему московскому времени; цвет
передаётся через существующее поле `SongDTO.color` / `SongDTOdigest.color`.

## Зачем

После перехода на онлайн-плеер атрибуты публикации в Telegram / VK / Dzen / VKG / Sponsr / PL
потеряли смысл. Цвета строк должны отражать актуальное состояние каталога (готово / в работе /
на эфире), а не стадию публикации в каналах, которые больше не используются. Единая классификация
во всех административных представлениях (таблица песен, сетка публикаций, обновление строки)
предотвращает расхождения между модулями.

## Как работает

### Классификация

```
1. idStatus < 6                       → IN_WORK         ""             (пустой — без подстановки)
2. free=true или active free window   → ON_AIR          #33FF33
3. нет валидного dateTimePublish      → EXCLUSIVE       #99CCFF
4. дата = сегодня, момент в будущем   → TODAY           #FFFF00
5. иначе при наличии валидного расписания → DONE        #CCFFCC
```

Ключевая функция — [`Song.resolveStateFor`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt) →
[`SongStateResolver.resolve`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongStateResolver.kt);
публичное свойство `Song.state` делегирует в `resolveStateFor(moscowNow())` в проде. Тесты подменяют
`now` через параметр, что изолирует границы дня и бесплатного окна от системных часов.

### Endpoint

`POST /api/publications/date` принимает только пять токенов:

| Токен | Состояние |
|---|---|
| `STATE_DONE` | DONE |
| `STATE_TODAY` | TODAY |
| `STATE_ON_AIR` | ON_AIR |
| `STATE_EXCLUSIVE` | EXCLUSIVE |
| `STATE_IN_WORK` | IN_WORK |

Невалидный / старый токен → пустая строка, без 5xx. Маппинг инкапсулирован в
`ApiController.paramToSongState(param)` (см.
[`ApiController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt))
и покрыт [`PublicationsDateFilterTest`](../../karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/controllers/PublicationsDateFilterTest.kt).

### Цвет и потребители

`SongDTO.color` / `SongDTOdigest.color` остаются существующими полями DTO. Контрактная таблица —
[`contracts/song-state-color.md`](../../specs/155-song-state-colors/contracts/song-state-color.md).
Фронт-рендер — везде только через `:style="{ backgroundColor: data.item.color }"`
(`SongsTable.vue`, `PublishTableBodyTd.vue`); никаких логических веток по `SongState` во Vue
не остаётся.

## Инварианты / правила

- Правила взаимоисключающие: каждая песня при каждом пересчёте получает ровно одно из пяти
  состояний (FR-007).
- `IN_WORK` имеет пустой цвет и **никаких fallback'ов** на старую палитру `idStatus` (FR-011).
  Удалена ветка в `Song.loadListFromDb`, подставлявшая `#DDA0DD`/`#EE82EE`/... для песен со
  `state.color == ""`.
- `TODAY` (будущий эфир сегодня) приоритетнее `DONE` (FR-003) — иначе сегодняшние эфиры
  растворялись бы в зелёном цвете «готово».
- `isFreelyAvailableNow` (см. [`song-free-access`](./song-free-access.md)) — единственный
  источник истины для «бесплатно прямо сейчас»; `onAir` отдельно не используется (FR-012).
- Тесты: [`SongStateTest`](../../karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/SongStateTest.kt)
  (9 кейсов + канонический контракт + smoke для `moscowNow`) и
  [`PublicationsDateFilterTest`](../../karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/controllers/PublicationsDateFilterTest.kt)
  (5+ негативных). Прогон без БД и Spring-контекста.

## Известные ловушки

- **Часовой пояс**: `Song.moscowNow()` всегда `Europe/Moscow`. В контейнере `karaoke-app` JVM-локаль
  = UTC; без явного TZ парсинг `dateTimePublish` даст ошибку в 3 часа. См. комментарий в
  `dateTimePublish` геттере.
- **`STATUS_*` и `STATE_WO_*`** больше не часть контракта endpoint. UI-компонент
  `PublishTableFooter` отправляет только пять новых токенов. Любое использование старых токенов
  во вью/сторе — регрессия SC-003.
- **Чистый фон для `IN_WORK`**: фронт применяет `background-color` через inline-стиль. Если
  `color == ""`, в DOM попадёт пустая строка — в таблице песня останется без заливки, без
  подстановки. Проверять визуально: строки с `idStatus < 6` не должны быть фиолетовыми/розовыми.
- **SSE/обновление строки**: `SongDTO.color` обновляется через тот же endpoint `/song/update`
  и пробрасывается в Vuex; для проверки согласованности цвета между таблицами достаточно
  выполнить quickstart-шаг 5 из [`quickstart.md`](../../specs/155-song-state-colors/quickstart.md).

## Ссылки

- [Спека фичи `specs/155-song-state-colors/spec.md`](../../specs/155-song-state-colors/spec.md) — FR-001..FR-013, SC-001..SC-005.
- [Контракт цвета `specs/155-song-state-colors/contracts/song-state-color.md`](../../specs/155-song-state-colors/contracts/song-state-color.md).
- [Контракт endpoint `specs/155-song-state-colors/contracts/publications-date-filter.md`](../../specs/155-song-state-colors/contracts/publications-date-filter.md).
- [Технический план `specs/155-song-state-colors/plan.md`](../../specs/155-song-state-colors/plan.md).
- [Quickstart `specs/155-song-state-colors/quickstart.md`](../../specs/155-song-state-colors/quickstart.md).
- [Документ `docs/features/songs-table.md`](./songs-table.md) — административная таблица песен (потребитель `color`).
- [Документ `docs/features/song-free-access.md`](./song-free-access.md) — семантика `isFreelyAvailableNow` / `free`.
- [Документ `docs/features/dual-db-sync.md`](./dual-db-sync.md) — почему цвет **не** сохраняется в БД (производное значение).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` — `state` + `resolveStateFor(now)` + `moscowNow()`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongStateResolver.kt` — чистый резолвер без БД/Spring.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` — `getPublicationsDateFrom` + `paramToSongState`.
- `webvue3/src/components/Publish/components/PublishTableFooter.vue` — легенда из 5 кнопок и clickColorButton.
