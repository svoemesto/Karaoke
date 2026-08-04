# Contract: `POST /api/dictionaries/test` (новый эндпоинт, FR-003)

Реализует User Story 2 / FR-003 / SC-003: администратор проверяет, как строка будет обработана
конкретным словарём — той же функцией, что использует реальная автопубликация, без похода к
реальной песне.

Добавляется в существующий `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/DictionariesController.kt`
(`@RequestMapping("/api/dictionaries")`), рядом с `list`/`names`/`create`/`update`/`delete` —
тот же паттерн `POST` + `@ResponseBody Map<String, Any>`, тот же `withDb { db -> ... }` хелпер уже
существующий в контроллере (`local` БД admin-машины — тестирование делается администратором на
admin-машине, по образцу `NewsTemplateController.preview`).

## Request

`POST /api/dictionaries/test`

| Параметр | Тип | Обязательный | Описание |
|---|---|---|---|
| `dictName` | `String` | да | Имя словаря (`"Censored"`, `"Слова с Ё"`, `"Sync Ids"` — любой зарегистрированный) |
| `text` | `String` | да | Произвольная тестовая строка |

## Response (200, всегда — как у соседних эндпоинтов этого контроллера)

```json
{
  "success": true,
  "dictName": "Censored",
  "input": "Название песни про нахуй",
  "result": "Название песни про нах█й",
  "changed": true
}
```

| Поле | Тип | Описание |
|---|---|---|
| `success` | `Boolean` | `false` только при неизвестном `dictName` |
| `dictName` | `String` | эхо входного параметра |
| `input` | `String` | эхо входной строки |
| `result` | `String` | строка после применения словаря (для `"Censored"` — через `String.censored()`, для `"Слова с Ё"`/прочих — соответствующая существующая функция замены, если применимо; если для словаря нет применяющей замену функции — `result == input`, `changed=false`) |
| `changed` | `Boolean` | `result != input` — быстрый визуальный сигнал в UI без построчного сравнения |
| `error` | `String?` | заполнено только при `success=false` (неизвестный `dictName`) |

## Ошибки

Неизвестный `dictName` → `{"success": false, "error": "unknown dictName: ..."}` (200, по образцу
`NewsTemplateController.save` — UI этого проекта не различает HTTP-статусы, только поле `success`).

## Потребитель

`webvue3/src/components/Dictionaries/DictionariesTable.vue` — новое поле «Проверить строку» рядом с
таблицей, вызывающее `POST /api/dictionaries/test` через существующий `promisedXMLHttpRequest`
(по образцу остальных actions в `store.js`), показывающее `result` инлайн.
