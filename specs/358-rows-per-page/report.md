# Report: Настраиваемое количество строк на странице таблиц в админке (Pass 358)

**Spec**: [spec.md](./spec.md) | **Branch**: `359-rows-per-page` | **Issue**: OpenProject #74
**Status**: Implementation complete

## Что сделано

### Backend (`karaoke-app`)

1. **`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`** —
   расширен `listKaraokeProperties` 14 записями `ui.<table>.rows_per_page` типа INT
   с дефолтами 25/30/50/500 (FR-010 — совпадают с текущим hardcoded поведением).
2. **`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`** —
   в эндпоинт `/api/properties/setproperty` добавлена серверная валидация диапазона
   `1..1000` для ключей с шаблоном `ui.*.rows_per_page` (FR-006). Невалидное значение
   → HTTP 400 с сообщением `value must be integer in 1..1000`.

### Frontend (`webvue3`)

3. **`webvue3/src/store/modules/tableSettings.js`** — новый Vuex-модуль:
   - State: `{ rowsPerPage: {}, loaded: false, saving: {} }`.
   - Getters: `getRowsPerPage(tableKey)`, `isSavingRowsPerPage(tableKey)`,
     `getAllTableRowsPerPage()`.
   - Actions: `loadTableSettings()` (POST `/api/propertiesdigests`, фильтр
     `ui.*.rows_per_page`), `setRowsPerPage({tableKey, value})` (POST
     `/api/properties/setproperty`).
   - Hardcoded defaults через `DEFAULT_ROWS_PER_PAGE` (FR-010).
4. **`webvue3/src/store/index.js`** — зарегистрирован модуль `tableSettings`.
5. **14 таблиц** (`<Entity>Table.vue`) — добавлены:
   - Импорт `BFormInput` из `bootstrap-vue-next`.
   - UI-блок с `<b-form-input type="number" min="1" max="1000">` перед `<b-pagination>`
     в шапке таблицы.
   - Computed `isSavingRowsPerPage()`.
   - Метод `onPerPageChange(newValue)` (парсинг, валидация, `currentPage = 1`,
     `setRowsPerPage`, перезагрузка данных — без optimistic update).
   - В `mounted()` добавлен `loadTableSettings` + установка `perPage` из store.

### Документация

6. **`docs/features/rows-per-page.md`** — per-feature документ (FR-009 Constitution).
7. **`docs/architecture-notes.md`** — запись Pass 358.

## API контракт (без изменений)

- **Запись**: `POST /api/properties/setproperty?key=ui.<table>.rows_per_page&stringValue=<int>`.
- **Чтение**: `POST /api/propertiesdigests` (без фильтра) → массив всех параметров;
  фронт фильтрует по `ui.*.rows_per_page`.
- Никаких новых эндпоинтов не введено.

## Поведение

- Дефолты совпадают с текущим hardcoded `perPage` (FR-010, FR-011 — никаких регрессий).
- Изменение значения: UI отправляет запрос, ждёт ответа, показывает loading-индикатор,
  применяет значение (без optimistic — Clarifications Q3).
- При смене `perPage` сбрасывается `currentPage` на 1 (ADR-0004).
- Хранение — **глобально per-table** в `Karaoke.properties` (не per-user — Clarifications Q1).
- Серверная валидация `1..1000` отвергает невалидные значения (FR-006).

## Тесты

- **Manual E2E** обязателен (см. `quickstart.md` — 8 шагов).
- Автоматических тестов в CI нет (per Constitution § Testing).

## OpenProject Workflow

- ✅ claim (выполнено в начале Stage 1)
- ✅ work (выполнено в Stage 6 — этот отчёт)
- ⏳ add-comment (после merge PR)
- ⏳ mark-review (после add-comment)
- ⏳ close (после ревью владельцем)

## Файлы изменены

```
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt  (+ 14 строк)
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt  (+ 12 строк)
webvue3/src/store/modules/tableSettings.js  (NEW, 144 строки)
webvue3/src/store/index.js  (+ 2 строки)
webvue3/src/components/Songs/SongsTable.vue  (+ импорт, UI-блок, computed, methods, mounted)
webvue3/src/components/Authors/AuthorsTable.vue  (аналогично)
webvue3/src/components/Albums/AlbumsTable.vue  (аналогично)
webvue3/src/components/Pictures/PicturesTable.vue  (аналогично)
webvue3/src/components/SiteUsers/SiteUsersTable.vue  (аналогично)
webvue3/src/components/Subscriptions/SubscriptionsTable.vue  (аналогично)
webvue3/src/components/ShareLinks/ShareLinksTable.vue  (аналогично)
webvue3/src/components/Dictionaries/DictionariesTable.vue  (аналогично)
webvue3/src/components/Properties/PropertiesTable.vue  (аналогично)
webvue3/src/components/SitePlaylists/SitePlaylistsTable.vue  (аналогично)
webvue3/src/components/ListeningHistory/ListeningHistoryTable.vue  (аналогично)
webvue3/src/components/Processes/ProcessesTable.vue  (аналогично)
webvue3/src/components/News/NewsTable.vue  (аналогично)
# Stats таблицы (TopUsersTable, TopListenedSongsTable) — вне scope (chart-cards с $emit('page-size'))
docs/features/rows-per-page.md  (NEW)
docs/architecture-notes.md  (+ запись Pass 358)
```

## Известные ограничения / trade-offs

1. **Глобальное хранение вместо per-user** — пользователь явно выбрал в Clarifications Q1
   `KaraokeProperties` (глобальный по своей природе). Если потребуется per-user —
   отдельный эпик с новой таблицей `tbl_user_table_settings`.
2. **ADR-нарушение**: фича использует `KaraokeProperties` для UI-настроек (не только
   render-config), что переопределяет первоначальную интерпретацию ADR `local-0001`.
   Решение владельца зафиксировано в spec.md § Clarifications — это не silent override.
3. **Без optimistic update** — пользователь выбрал B в Clarifications Q3.
   ~200 мс задержки на изменение.

## Hotfixes (после первоначального review)

1. **`BFormInput is not defined`** — prettier --write удалил `BFormInput` из импорта
   в `Songs/SongsTable.vue` (prettier не видит template Vue SFC). Исправлено:
   добавлен `BFormInput` обратно в импорт.
2. **`onPerPageChange invalid value`** — `<b-form-input>` передавал в `@change` Event, а не
   значение. Исправлено во всех 13 таблицах: `@change="onPerPageChange($event)"` + метод
   `onPerPageChange(e)` с `const rawValue = e && e.target ? e.target.value : e`.
3. **Фильтры не применяются при изменении perPage** — `load<X>Digests` вызывался без
   `filters`. Исправлено: добавлен агрегирующий геттер `get<Entity>Filter` в filter stores
   (Songs, Authors, Albums, Pictures, SiteUsers, Dictionaries, Properties, SitePlaylists,
   Processes) и передаётся в `load<X>Digests({ page, perPage, filters })`. Для таблиц
   без filter-store (Subscriptions, ShareLinks, ListeningHistory, News) — без изменений.
4. **mutations/двойная-brace в filter stores** — после переноса getter'ов внутрь
   `getters: {...}` блока, mutations оказались внутри getters (не закрывалась скобка).
   Исправлено для всех 6 файлов: вытащил mutations на правильный уровень, добавил
   недостающие закрывающие скобки.
5. **Processes и ListeningHistory не применяли perPage** — backend поддерживает
   пагинацию, но под другими именами: `limit`/`offset` для Processes, `pageSize` для
   ListeningHistory. Frontend передавал `perPage` который backend игнорировал.
   Исправлено:
   - `ProcessesTable.onPerPageChange` передаёт `{ limit: this.perPage, offset: (page-1)*perPage, ...buildFilters() }` в `loadProcesses`.
   - `ListeningHistoryTable.onPerPageChange` передаёт `{ page, pageSize: this.perPage }` в `loadListeningHistoryDigest`.
   - `ListeningHistory store` action `loadListeningHistoryDigest` теперь пробрасывает `pageSize` в backend.
   - Watcher `currentPage` в `ListeningHistoryTable` и `reload()` также передают `pageSize`.
   - KaraokeProperties.kt descriptions обновлены: "передаётся как limit в /api/admin/processes" и "как pageSize в /api/listeninghistory/digest".
