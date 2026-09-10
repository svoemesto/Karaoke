# Research: Настраиваемое количество строк на странице таблиц в админке

**Spec**: [spec.md](./spec.md) | **Branch**: `359-rows-per-page` | **Issue**: OpenProject #74

## Решения, принятые в Stage 1+2 (см. spec.md § Clarifications)

1. **Хранение**: `KaraokeProperties` (файл `/sm-karaoke/system/Karaoke.properties`, ~150 параметров). Используем существующее API `/api/properties/setproperty` (POST) и `/api/propertiesdigests` (POST). Никаких новых эндпоинтов.
2. **UI**: free `<input type="number">` 1..1000. Без оптимистичного обновления — только после успешного ответа backend.
3. **Scope**: глобально per-table (НЕ per-user) — все админы видят одно значение.

## Технические исследования

### R1. Где живёт `KaraokeProperties` (admin-only)

**Decision**: Использовать `karaoke-app/.../KaraokeProperties.kt` (companion object + `loadPropertiesMap()`).

**Rationale**:
- Это тот же файл, который редактируется через UI компонента «Настройки» (`KaraokePropertiesView.vue`) — пользователь явно на это сослался в Clarifications Q1.
- Backend API: `/api/propertiesdigests` (POST) → возвращает список параметров, `/api/properties/setproperty` (POST, `key` + `stringValue`) → записывает. Оба в `karaoke-app/.../controllers/ApiController.kt` (`@PostMapping("/propertiesdigests")` строка 6065, `/properties/setproperty` — через `KaraokeAppService.setPropertyValue`).
- Файл `/sm-karaoke/system/Karaoke.properties` — base64-encoded JSON-список `KaraokePropertySerializable` (строки).

**Alternatives considered**:
- `karaoke-web/.../services/KaraokeProperties.kt` — это **другой** `KaraokeProperties` (Spring `@Value`-based, для site-traffic resilience env-переменных), не подходит.
- `tbl_public_settings` (БД) — для публичных kill-switch'ей и лимитов; семантически не подходит для UI-настроек админки.
- `tbl_settings` (БД) — это таблица песен (не настроек!), отвергнуто.

### R2. Как добавить новый параметр в `KaraokeProperties`

**Decision**: Добавить записи в `listKaraokeProperties` (глобальный список в `KaraokeProperties.kt`) с типом `INT`.

**Rationale**:
- Конвенция задана в `knowledge/adr/local-0001-karaoke-properties-defaults.md`: дефолты через `KaraokePropertySerializable(key, defaultValue, description, type)`.
- На UI: параметр автоматически появляется в `PropertiesTable.vue` (читает через `/api/propertiesdigests`), может быть отредактирован через существующий UI.
- В коде доступ: `KaraokeProperties.karaokePropertiesMap[KEY]` (String → String, парсится вручную в тип).

**Concrete API contract** (для добавления):
```kotlin
KaraokePropertySerializable.create(
    key = "ui.songs.rows_per_page",
    value = "50",          // stringValue в БД / файле
    description = "Количество строк на странице в таблице «Песни»",
    type = "INT",
    defaultValue = "50",
    isHidden = false,       // видно в UI «Настройки» (полезно для отладки)
)
```

**Имена параметров** (per-table, глобально):
- `ui.songs.rows_per_page` (default 50)
- `ui.authors.rows_per_page` (default 30)
- `ui.albums.rows_per_page` (default 30)
- `ui.pictures.rows_per_page` (default 30)
- `ui.site_users.rows_per_page` (default 30)
- `ui.subscriptions.rows_per_page` (default 25)
- `ui.share_links.rows_per_page` (default 25)
- `ui.dictionaries.rows_per_page` (default 30)
- `ui.properties.rows_per_page` (default 50)
- `ui.site_playlists.rows_per_page` (default 30)
- `ui.listening_history.rows_per_page` (default 500)
- `ui.processes.rows_per_page` (default 50)
- `ui.news.rows_per_page` (default 30)
- `ui.stats.rows_per_page` (default 30)

### R3. Как UI получит текущее значение per-table при загрузке

**Decision**: При `created()` хука таблицы — `await getPropertyValuePromise('ui.<table>.rows_per_page')`. Если пусто — fallback на hardcoded default.

**Rationale**:
- Существующий паттерн уже используется в `webvue3/src/components/Properties/store.js:72` — `getPropertyValuePromise(ctx, propertyKey)` оборачивает `/api/properties/getproperty` (POST, в `PublicSettingsWebController.kt:117`).
- Этот endpoint читает `tbl_public_settings` — **НЕ** `KaraokeProperties`. ⚠️ Нужно проверить, есть ли аналогичный endpoint для `KaraokeProperties` или нужно добавить.

**Investigation result**: ⚠️ В `PublicSettingsWebController.kt` есть `/api/properties/getproperty` — но он ходит в `tbl_public_settings`. Для `KaraokeProperties` (которая у нас в файле, а не в БД) — нужно:
- либо добавить endpoint `/api/karaoke-properties/get` (читает файл),
- либо использовать существующий `/api/propertiesdigests` для UI (он возвращает все параметры из файла `Karaoke.properties`).

**Decision (R3a)**: использовать `/api/propertiesdigests` для UI — он уже есть и возвращает все параметры. Кэшировать на стороне клиента (одна загрузка на таблицу, дальше — локальный store).

### R4. Где хранить состояние per-table rowsPerPage в webvue3

**Decision**: Локальный Vuex-модуль `webvue3/src/store/modules/tableSettings.js` (новый) с состоянием `tableRowsPerPage: { songs: 50, authors: 30, ... }` и методами `loadTableSettings(tableKey)`, `setRowsPerPage(tableKey, value)`.

**Rationale**:
- Избегаем N запросов к `/api/propertiesdigests` (по одному на таблицу) — загружаем 1 раз при старте SPA и кэшируем.
- Простой модуль с явным контрактом: 14 констант для таблиц, метод `getRowsPerPage(tableKey)` для каждой таблицы.
- Альтернатива: использовать `setWebvueProp/getWebvueProp` (см. utils.js:57) — но эти ходят в `webvue_properties.txt` (UI-настройки webvue3, НЕ `KaraokeProperties`). Семантически это **другая** система — UI-настройки вроде «показывать ли подсказки», не наш кейс.

### R5. UI-компонент для ввода

**Decision**: Использовать существующий `<b-form-input type="number" min="1" max="1000">` из `bootstrap-vue-next`. Размещать слева в верхнем блоке пагинации (рядом с `<b-pagination>`).

**Rationale**:
- Соответствует Clarifications Q2 (free input 1..1000).
- Соответствует существующему стеку (`bootstrap-vue-next`).
- Без новых UI-зависимостей.
- Валидация — клиентская (`@blur` или `@change` → проверка диапазона), плюс серверная (FR-006).

### R6. Оптимистичное обновление — НЕТ (см. Clarifications Q3)

**Decision**: UI НЕ меняет `perPage` локально до ответа backend. Показывает loading-индикатор (`<b-spinner>` или disabled state) на инпуте во время round-trip.

**Rationale**:
- Пользователь явно выбрал B в Clarifications Q3 (отверг рекомендацию A).
- Упрощает реализацию — нет rollback-логики.
- Стоимость: ~200 мс задержки на каждое изменение (NFR-001) — допустимо для UI-настройки.

### R7. Существующий паттерн webvue3 admin tables (см. ADR-0004)

**Decision**: Следовать конвенции из `knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md`:
- `data()`: добавить `perPage: this.$store.getters.getRowsPerPage('<table>')` (вместо hardcoded числа).
- `watch.perPage()`: вызывает `loadData()` (существующий паттерн, см. ADR-0004 строки 71-79).
- `created()`: дополнительно `await this.$store.dispatch('loadTableSettings', '<table>')` перед `loadData()`.

**Rationale**: ADR-0004 зафиксировал конвенцию, не переизобретаем.

### R8. Глобально per-table — минусы

**Trade-off**: Пользователь выбрал `KaraokeProperties`, но эта система глобальная, не per-user. Если admin1 хочет 50 строк, а admin2 хочет 100 — увидят оба одно и то же.

**Rationale (принято)**: Соответствует явному решению пользователя в Clarifications Q1. Если в будущем потребуется per-user — отдельная миграция (например, новая таблица `tbl_user_table_settings`). Out of scope для этой фичи.

## Источники

- `knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md` — паттерн для таблиц.
- `knowledge/adr/local-0001-karaoke-properties-defaults.md` — конвенция дефолтов.
- `knowledge/domains/processing/components/karaoke-properties.md` — описание `KaraokeProperties`.
- `knowledge/system/frontend/store-properties.md` — Vuex store для Properties.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` — backend (companion object + `listKaraokeProperties`).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:6065` — endpoint `/api/propertiesdigests`.
- `webvue3/src/components/Properties/store.js:67` — Vuex actions `setPropertyValuePromise`/`getPropertyValuePromise`.

## Открытые вопросы (для Stage 4 tasks.md)

- **O1**: Точное место размещения Vuex-модуля — `webvue3/src/store/modules/tableSettings.js` или встроить в существующий модуль `webvue3/src/store/index.js`? Зависит от структуры store (нужно проверить в Stage 4).
- **O2**: Загрузка `/api/propertiesdigests` — однократно при старте SPA или при каждом открытии таблицы? (Склоняюсь к однократной + кэш в store.)
- **O3**: Нужно ли отображать параметр `ui.<table>.rows_per_page` в существующем UI «Настройки» (`PropertiesTable.vue`)? Если да — пользователь сможет редактировать и через этот UI тоже (бонус).
