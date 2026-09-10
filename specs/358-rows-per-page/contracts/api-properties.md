# Contracts: API endpoints для per-table rowsPerPage

**Spec**: [spec.md](../spec.md) | **Branch**: `359-rows-per-page` | **Issue**: OpenProject #74

Фича использует **существующие** endpoints (никаких новых). Контракт ниже описывает,
как именно frontend вызывает их для целей этой фичи.

## 1. POST /api/propertiesdigests

**Назначение**: Получить список всех параметров `KaraokeProperties` (включая 14 новых `ui.*.rows_per_page`).

**Реализация**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:6065` (`apisPropertiesDigest`).

### Запрос

```
POST /api/propertiesdigests
Content-Type: application/x-www-form-urlencoded

(опциональные фильтры, в этой фиче не используются)
filterKey=
filterValue=
filterDefaultValue=
filterDescription=
filterType=
```

### Ответ

```json
{
  "workInContainer": true,
  "propertiesDigests": [
    {
      "key": "ui.songs.rows_per_page",
      "value": "50",
      "defaultValue": "50",
      "description": "Количество строк на странице в таблице «Песни»",
      "type": "INT",
      "isHidden": false
    },
    {
      "key": "ui.authors.rows_per_page",
      "value": "30",
      "defaultValue": "30",
      "description": "Количество строк на странице в таблице «Авторы»",
      "type": "INT",
      "isHidden": false
    }
    // ... ещё 12 параметров + ~150 старых параметров рендера
  ],
  "types": ["INT", "STRING", "BOOL", "FLOAT", "COLOR", "LIST", ...]
}
```

### Использование в этой фиче

Frontend (webvue3) при старте SPA делает **1 вызов** `/api/propertiesdigests`, фильтрует
ключи с префиксом `ui.` и суффиксом `.rows_per_page`, кладёт в `tableSettings` store.

### Коды ошибок

- 200 OK — успех.
- 500 Internal Server Error — БД недоступна (`workInContainer=false` если не в контейнере).

## 2. POST /api/properties/setproperty

**Назначение**: Записать значение одного параметра `KaraokeProperties`.

**Реализация**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` (метод `setPropertyValue` рядом со строкой 6055).

### Запрос

```
POST /api/properties/setproperty
Content-Type: application/x-www-form-urlencoded

key=ui.songs.rows_per_page&stringValue=100
```

### Ответ

```
"true"   // или "false" при ошибке (String!)
```

⚠️ **Замечание**: текущий контракт возвращает String `"true"`/`"false"`, не JSON boolean.
Изменение контракта — out of scope. Frontend проверяет `data === "true"`.

### Использование в этой фиче

Frontend отправляет запрос при изменении значения в UI-поле:
```
key="ui.<table>.rows_per_page"
stringValue="<новое значение>"
```

### Коды ошибок / валидация

⚠️ **TODO (FR-006)**: текущая реализация НЕ валидирует значение по типу/диапазону.
В этой фиче нужно добавить серверную валидацию:
- если `key` имеет шаблон `ui.*.rows_per_page`:
  - `stringValue` MUST быть парсимым Int.
  - MUST быть в диапазоне `1..1000`.
  - Иначе → HTTP 400 с сообщением.

### Альтернативный endpoint (НЕ используется)

`GET /api/properties/getproperty?key=ui.songs.rows_per_page` — есть в
`karaoke-web/.../PublicSettingsWebController.kt:117`, но ходит в `tbl_public_settings`,
а не в `KaraokeProperties`. **Не использовать** в этой фиче.

Вместо него для получения значений используется `/api/propertiesdigests` (один раз при
старте SPA, дальше — локальный кеш в Vuex `tableSettings`).

## 3. SSE Notification (опционально)

При успешном `setproperty` backend отправляет SSE-сообщение:
```kotlin
SseNotification.message(
    Message(
        type = "info",
        head = "SET PROPERTY",
        body = "Свойство «$key» установлено в значение по умолчанию",
    ),
)
```

(см. `ApiController.kt:6055`.)

### Использование в этой фиче

**Не используется** — для синхронизации между вкладками достаточно того, что каждая
вкладка при старте делает `/api/propertiesdigests`. Если в будущем потребуется real-time
синхронизация — подписаться на SSE event `SET_PROPERTY` и обновить `tableSettings.rowsPerPage`.

## Клиентский helper

В `webvue3/src/components/Properties/store.js:67` уже есть:
```javascript
setPropertyValuePromise(ctx, payload) {
    let params = { key: payload.propertyKey, stringValue: payload.propertyValue }
    let request = { method: 'POST', url: '/api/properties/setproperty', params: params }
    return promisedXMLHttpRequest(request)
}
```

**Переиспользовать** эту функцию в новом `tableSettings` store (не дублировать код).
