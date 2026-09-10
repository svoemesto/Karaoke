# Data Model: Настраиваемое количество строк на странице таблиц

**Spec**: [spec.md](./spec.md) | **Branch**: `359-rows-per-page` | **Issue**: OpenProject #74

## Хранение

### Backend: `KaraokeProperties` (existing)

Хранилище: файл `/sm-karaoke/system/Karaoke.properties` (base64-encoded JSON-список `KaraokePropertySerializable`).

**Существующая схема** (`karaoke-app/.../KaraokeProperties.kt`):
```kotlin
data class KaraokePropertySerializable(
    val key: String,           // e.g. "ui.songs.rows_per_page"
    val value: String,         // stringValue в файле
    val defaultValue: String,
    val description: String,
    val type: String,          // "INT" | "STRING" | "BOOL" | ...
    val isHidden: Boolean = false,
)
```

**Runtime-доступ**:
```kotlin
val perPage: Int? = KaraokeProperties.karaokePropertiesMap["ui.songs.rows_per_page"]?.toIntOrNull()
                  ?: 50   // hardcoded fallback
```

### 14 новых параметров (изменение в `KaraokeProperties.kt`)

| Ключ | Тип | Default | Описание |
|------|-----|---------|----------|
| `ui.songs.rows_per_page` | INT | 50 | Количество строк на странице в таблице «Песни» |
| `ui.authors.rows_per_page` | INT | 30 | Количество строк на странице в таблице «Авторы» |
| `ui.albums.rows_per_page` | INT | 30 | Количество строк на странице в таблице «Альбомы» |
| `ui.pictures.rows_per_page` | INT | 30 | Количество строк на странице в таблице «Картинки» |
| `ui.site_users.rows_per_page` | INT | 30 | Количество строк на странице в таблице «Пользователи сайта» |
| `ui.subscriptions.rows_per_page` | INT | 25 | Количество строк на странице в таблице «Подписки» |
| `ui.share_links.rows_per_page` | INT | 25 | Количество строк на странице в таблице «Шаринг-ссылки» |
| `ui.dictionaries.rows_per_page` | INT | 30 | Количество строк на странице в таблице «Справочники» |
| `ui.properties.rows_per_page` | INT | 50 | Количество строк на странице в таблице «Свойства» |
| `ui.site_playlists.rows_per_page` | INT | 30 | Количество строк на странице в таблице «Плейлисты сайта» |
| `ui.listening_history.rows_per_page` | INT | 500 | Количество строк на странице в таблице «История прослушиваний» |
| `ui.processes.rows_per_page` | INT | 50 | Количество строк на странице в таблице «Процессы» |
| `ui.news.rows_per_page` | INT | 30 | Количество строк на странице в таблице «Новости» |
| `ui.stats.rows_per_page` | INT | 30 | Количество строк на странице в таблице «Статистика» |

**Валидация на backend** (`/api/properties/setproperty`):
- Целое число: парсинг `value.toIntOrNull()`.
- Диапазон: `1..1000`.
- Невалидное → HTTP 400 + сообщение «value must be integer in 1..1000» (предложение impl: добавить серверную валидацию в `karaoke-app/.../ApiController.setproperty`, см. tasks.md).

⚠️ **Замечание**: текущий `/api/properties/setproperty` (см. `karaoke-app/.../controllers/ApiController.kt` строка 6055) сохраняет любое строковое значение без валидации по типу. Нужно добавить валидацию `1..1000` для ключей с префиксом `ui.*.rows_per_page`.

### Frontend (Vuex store): `webvue3/src/store/modules/tableSettings.js` (новый)

```javascript
export default {
    state: () => ({
        // Loaded once at SPA startup from /api/propertiesdigests
        // Map: { 'songs': 50, 'authors': 30, ... }
        rowsPerPage: {},
        // Track which tables have been loaded to avoid duplicate calls
        loaded: false,
        // In-flight requests per table for save (loading state)
        saving: {},  // { 'songs': false, ... }
    }),
    getters: {
        getRowsPerPage: (state) => (tableKey) => {
            return state.rowsPerPage[tableKey] || DEFAULT[tableKey] || 30
        },
        isSavingRowsPerPage: (state) => (tableKey) => {
            return !!state.saving[tableKey]
        },
    },
    actions: {
        async loadTableSettings({ commit, state }) {
            if (state.loaded) return
            // POST /api/propertiesdigests, filter keys starting with 'ui.'
            // and ending with '.rows_per_page'
            const response = await api.post('/api/propertiesdigests', { ... })
            const settings = {}
            for (const prop of response.propertiesDigests) {
                if (prop.key && prop.key.startsWith('ui.') && prop.key.endsWith('.rows_per_page')) {
                    const tableKey = prop.key.slice(3, -('.rows_per_page'.length))
                    const value = parseInt(prop.value, 10)
                    if (!isNaN(value) && value >= 1 && value <= 1000) {
                        settings[tableKey] = value
                    }
                }
            }
            commit('setRowsPerPageMap', settings)
            commit('markLoaded')
        },
        async setRowsPerPage({ commit }, { tableKey, value }) {
            commit('setSaving', { tableKey, saving: true })
            try {
                await api.post('/api/properties/setproperty', {
                    key: `ui.${tableKey}.rows_per_page`,
                    stringValue: String(value),
                })
                commit('setRow', { tableKey, value })
            } finally {
                commit('setSaving', { tableKey, saving: false })
            }
        },
    },
    mutations: {
        setRowsPerPageMap(state, map) { Object.assign(state.rowsPerPage, map) },
        setRow(state, { tableKey, value }) { state.rowsPerPage[tableKey] = value },
        markLoaded(state) { state.loaded = true },
        setSaving(state, { tableKey, saving }) {
            if (saving) state.saving[tableKey] = true
            else delete state.saving[tableKey]
        },
    },
}

// Hardcoded defaults — must match the backend KaraokeProperties defaults
const DEFAULT = {
    songs: 50, authors: 30, albums: 30, pictures: 30, site_users: 30,
    subscriptions: 25, share_links: 25, dictionaries: 30, properties: 50,
    site_playlists: 30, listening_history: 500, processes: 50, news: 30, stats: 30,
}
```

### Frontend: Изменения в 14 таблицах

Для каждой таблицы (`<Entity>Table.vue`):
- `data().perPage` → `this.$store.getters.getRowsPerPage('<table_key>')` (или initial 30/50/...).
- `created()` → добавить `await this.$store.dispatch('loadTableSettings')` (один раз, защищён `loaded` флагом).
- В блоке пагинации → добавить `<b-form-input type="number" min="1" max="1000" :model-value="perPage" @change="onPerPageChange" />`.
- Метод `onPerPageChange(value)`:
  ```javascript
  async onPerPageChange(newValue) {
      const parsed = parseInt(newValue, 10)
      if (isNaN(parsed) || parsed < 1 || parsed > 1000) {
          // Show toast, restore old value (don't update)
          return
      }
      this.currentPage = 1   // ADR-0004: page reset
      await this.$store.dispatch('setRowsPerPage', { tableKey: '<table_key>', value: parsed })
      this.loadData()        // refetch with new perPage
  }
  ```

## Связи (relationships)

- **`KaraokeProperties`** (existing) → расширяется 14 параметрами (без изменений схемы, только дополнение `listKaraokeProperties`).
- **`webvue3 tableSettings store`** (new) → читает из `KaraokeProperties` через `/api/propertiesdigests`.
- **14 таблиц webvue3** → читают/пишут через `tableSettings store` actions/getters.

## Миграции

- **Нет SQL-миграций** — данные в файле `Karaoke.properties` (не в БД).
- **На существующих БД**: после deploy новой версии `karaoke-app` — параметры автоматически появятся в `listKaraokeProperties` с дефолтами; UI использует fallback на hardcoded defaults, если файл не содержит записи (FR-005).
- **Обратная совместимость**: существующие таблицы продолжают работать с hardcoded `perPage` (никаких breaking changes).

## Валидация

### На backend (FR-006)

В `karaoke-app/.../controllers/ApiController.kt` (или новой middleware-функции):
```kotlin
if (key.startsWith("ui.") && key.endsWith(".rows_per_page")) {
    val parsed = stringValue.toIntOrNull()
    if (parsed == null || parsed < 1 || parsed > 1000) {
        return ResponseEntity.status(400).body(mapOf(
            "error" to "value must be integer in 1..1000",
        ))
    }
}
```

### На frontend (FR-002, FR-003)

- `<b-form-input type="number" min="1" max="1000">` — браузерная валидация (но не строгая, требуется JS-проверка).
- В `onPerPageChange` — `parseInt` + проверка диапазона до отправки на backend.
- Показывать loading-индикатор (disabled state + spinner) во время round-trip.

## Edge Cases (из спеки)

| Кейс | Поведение |
|------|-----------|
| Невалидное значение (0, отрицательное, >1000) | UI не отправляет, показывает toast. Backend возвращает 400 если дошёл. |
| Backend недоступен | UI показывает alert, perPage не применяется. |
| Параметр не найден в `Karaoke.properties` | Используется hardcoded default. |
| Конкурентное обновление | last-writer-wins. |
| Несколько таблиц на одной странице | Каждая таблица использует СВОЙ ключ (нет cross-talk). |
