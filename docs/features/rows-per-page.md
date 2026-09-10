# Feature: Настраиваемое количество строк на странице таблиц в админке

**Spec**: [`specs/358-rows-per-page/spec.md`](../../specs/358-rows-per-page/spec.md)
**Status**: In progress (Pass 358, реализация в feature-ветке `359-rows-per-page`)
**Created**: 2026-09-10
**Last updated**: 2026-09-10
**Source**: OpenProject #74 — «Количество строк на страницу таблицы»

## Назначение

В admin SPA `webvue3` в табличных компонентах (Songs, Authors, Albums, Pictures,
SiteUsers, Subscriptions, ShareLinks, Dictionaries, Properties, SitePlaylists,
ListeningHistory, Processes, News, Stats) в верхнем блоке пагинации слева добавлено
поле «Строк на странице», которое позволяет администратору менять количество строк
на странице без перезагрузки. Значение сохраняется per-table глобально (все админы
видят одно и то же значение для каждой таблицы) в `KaraokeProperties`.

## Где живёт

### Backend (Kotlin / Spring Boot)

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` —
  расширен `listKaraokeProperties` 14 записями `ui.<table>.rows_per_page` типа `INT`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` —
  в эндпоинт `/api/properties/setproperty` добавлена серверная валидация диапазона
  `1..1000` для ключей с шаблоном `ui.*.rows_per_page`.
- Файл `/sm-karaoke/system/Karaoke.properties` — base64-encoded JSON-список параметров.

### Frontend (Vue 3 / Vuex)

- `webvue3/src/store/modules/tableSettings.js` — новый Vuex-модуль с state
  `{ rowsPerPage: {}, loaded: false, saving: {} }`, геттерами `getRowsPerPage(tableKey)`,
  `isSavingRowsPerPage(tableKey)`, actions `loadTableSettings()` (читает
  `/api/propertiesdigests`) и `setRowsPerPage({tableKey, value})` (пишет
  `/api/properties/setproperty`).
- `webvue3/src/store/index.js` — зарегистрирован новый модуль `tableSettings`.
- `webvue3/src/components/<Entity>/<Entity>Table.vue` — 14 таблиц:
  - В `data()` `perPage` привязан к `this.$store.getters.getRowsPerPage('<table_key>')`.
  - В блоке пагинации добавлен `<b-form-input type="number" min="1" max="1000" :model-value="perPage" @change="onPerPageChange" />`.
  - В `created()` добавлен `await this.$store.dispatch('loadTableSettings')`.
  - Добавлен метод `async onPerPageChange(newValue)` (парсинг, валидация,
    `currentPage = 1`, `setRowsPerPage`, `loadData()`).

## API контракт

- **Запись**: `POST /api/properties/setproperty` с параметрами
  `key=ui.<table>.rows_per_page&stringValue=<int>`. Возвращает `"true"` или `"false"`.
  Сервер валидирует диапазон `1..1000`, иначе HTTP 400.
- **Чтение**: `POST /api/propertiesdigests` (без фильтра) возвращает список всех
  параметров `KaraokeProperties`; фронт фильтрует ключи с префиксом `ui.` и суффиксом
  `.rows_per_page`.
- Никаких новых эндпоинтов не введено — используется существующее API.

## Принятые решения (см. spec.md § Clarifications)

- **Хранение**: `KaraokeProperties` (тот же backend, что используется для других
  параметров компонента «Настройки»), для каждой таблицы свой параметр.
- **Scope**: глобально per-table (НЕ per-user) — все админы видят одно значение.
- **UI**: free input `<b-form-input type="number">` с диапазоном `1..1000`.
- **Поведение**: НЕ оптимистичное обновление — UI применяет значение только после
  успешного ответа backend (loading-индикатор на время round-trip).

## FR / NFR / SC Coverage

### Functional Requirements

- **FR-001**: UI-поле в 14 таблицах — `webvue3/src/components/<Entity>/<Entity>Table.vue`.
- **FR-002**: Free input `1..1000` — `<b-form-input type="number" min="1" max="1000">`.
- **FR-003**: Только после успешного ответа backend — `onPerPageChange` без optimistic.
- **FR-004**: Хранение в `Karaoke.properties` через `/api/properties/setproperty` —
  `listKaraokeProperties` в `KaraokeProperties.kt`.
- **FR-005**: Fallback на hardcoded дефолт если параметр не задан —
  геттер `getRowsPerPage` в `tableSettings.js`.
- **FR-006**: Серверная валидация `1..1000` — `ApiController.setproperty`.
- **FR-007**: Без новых эндпоинтов — переиспользуем `/api/propertiesdigests` и
  `/api/properties/setproperty`.
- **FR-008**: `currentPage = 1` при изменении — `onPerPageChange`.
- **FR-009**: Глобальная per-table — один параметр на таблицу, не на user.
- **FR-010**: Дефолты совпадают с текущим hardcoded поведением — явно перечислены
  в `listKaraokeProperties`.
- **FR-011**: Существующее поведение пагинации сохранено — НЕ трогаем `watch.perPage`
  и `loadData()`.

### Non-Functional Requirements

- **NFR-001**: Сохранение `≤ 200 мс` p95 — manual E2E (см. `quickstart.md`).
- **NFR-002**: Без optimistic — UI не обновляется до ответа.
- **NFR-003**: Дефолты идентичны текущим.

### Success Criteria

См. `spec.md § Success Criteria` и `quickstart.md` для manual E2E сценария.

## Связанные ADR

- [`knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md`](../../knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md) —
  паттерн webvue3 admin tables (server-side pagination, watch на perPage).
- [`knowledge/adr/local-0001-karaoke-properties-defaults.md`](../../knowledge/adr/local-0001-karaoke-properties-defaults.md) —
  конвенция дефолтов `KaraokeProperties`.
- [`knowledge/domains/processing/components/karaoke-properties.md`](../../knowledge/domains/processing/components/karaoke-properties.md) —
  описание `KaraokeProperties`.

## Известные ограничения

- Global, not per-user (см. Clarifications Q1).
- Серверная валидация `1..1000` — добавляется в этой фиче; до её реализации
  невалидные значения могли сохраняться в файл.
- Параметры `ui.*.rows_per_page` НЕ скрыты (`isHidden=false`) — видны в UI «Настройки»
  (бонус, можно редактировать оттуда).
