# Vuex store: TableSettings (rows per page)

> **Домен**: system (frontend)
> **Компонента**: `webvue3/src/store/modules/tableSettings.js` — UI-настройки
> «количество строк на странице» для admin-таблиц webvue3.

## Файл

`webvue3/src/store/modules/tableSettings.js` (NEW, ~140 строк).
Зарегистрирован в `webvue3/src/store/index.js` как модуль `tableSettings`.

## Назначение

Спека **specs/358-rows-per-page** — каждая admin-таблица имеет UI-поле
«Строк на странице», значение хранится **per-table глобально** в
`KaraokeProperties` (через backend `/api/properties/setproperty`).

Этот модуль — **client-side cache** этих значений:
- **state.rowsPerPage** — Map `{ 'songs': 50, 'authors': 30, ... }` после первой загрузки.
- **state.loaded** — защита от повторных вызовов `loadTableSettings`.
- **state.saving** — Map `{ 'songs': true }` для индикатора loading в инпуте.

## State

```javascript
state: () => ({
    rowsPerPage: {},     // Map tableKey → int
    loaded: false,       // защита от дублей
    saving: {},          // Map tableKey → bool (для loading indicator)
})
```

## Hardcoded defaults

`DEFAULT_ROWS_PER_PAGE` (frozen объект в модуле) — ДОЛЖНЫ совпадать с
`listKaraokeProperties` в `karaoke-app/.../KaraokeProperties.kt` (FR-010 спеки).

| tableKey | default |
|---|---|
| songs | 50 |
| authors | 30 |
| albums | 30 |
| pictures | 30 |
| site_users | 30 |
| subscriptions | 25 |
| share_links | 25 |
| dictionaries | 30 |
| properties | 50 |
| site_playlists | 30 |
| listening_history | 500 |
| processes | 50 |
| news | 30 |

## Getters

- **`getRowsPerPage(tableKey)`** — возвращает сохранённое значение или
  `DEFAULT_ROWS_PER_PAGE[tableKey]`. Используется в `<Table>.vue` для
  начального `this.perPage` в `data()`.
- **`isSavingRowsPerPage(tableKey)`** — bool, true пока идёт сохранение
  для таблицы. Используется для disabled-состояния инпута.
- **`getAllTableRowsPerPage()`** — Map всех effective значений (для отладки).

## Actions

### `loadTableSettings({ commit, state })`

Загружает ВСЕ настройки таблиц из backend (`POST /api/propertiesdigests`),
фильтрует ключи с префиксом `ui.` и суффиксом `.rows_per_page`,
кладёт в `state.rowsPerPage`. Защищён флагом `loaded`.

**Когда вызывать**: в `async mounted()` каждой admin-таблицы **ДО**
первой загрузки данных (await!). Иначе `this.perPage` остаётся
дефолтным из `data()`, и backend получает неправильное значение.

### `setRowsPerPage({ commit }, { tableKey, value })`

Сохраняет `value` для `tableKey` в backend (`POST /api/properties/setproperty`).
Только после успешного ответа обновляет `state.rowsPerPage`.

**Не оптимистично** (см. Clarifications Q3 спеки 358) — UI применяет
значение только после успешного ответа backend.

## Использование в таблицах

Каждая из 13 admin-таблиц (Songs, Authors, Albums, Pictures, SiteUsers,
Subscriptions, ShareLinks, Dictionaries, Properties, SitePlaylists,
ListeningHistory, Processes, News):

```javascript
async mounted() {
    await this.$store.dispatch('loadTableSettings')
    this.perPage = this.$store.getters.getRowsPerPage('<table_key>')
    this.reload()
}

async onPerPageChange(e) {
    // ...
    const ok = await this.$store.dispatch('setRowsPerPage', {
        tableKey: '<table_key>',
        value: parsed,
    })
    if (ok) {
        this.perPage = this.$store.getters.getRowsPerPage('<table_key>')
        // ... перезагрузка с фильтрами
    }
}
```

## Связь

- **Backend**: `karaoke-app/.../KaraokeProperties.kt` — хранит 14 параметров
  `ui.<table>.rows_per_page` (Pass 358).
- **Frontend**: используется через `state.rowsPerPage` / `getRowsPerPage`.
- **ADR `local-0001-karaoke-properties-defaults.md`** — конвенция дефолтов.

## Changelog

- **Pass 358** (2026-09-10): Initial. Автор: agent (Karaoke).
