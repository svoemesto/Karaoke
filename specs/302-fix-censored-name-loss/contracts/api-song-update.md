# API Contract: `POST /api/song/update`

**Spec**: [spec.md](../spec.md) | **Research**: [research.md](../research.md) | **Data Model**: [data-model.md](../data-model.md)

## Endpoint Summary

| Property | Value |
|---|---|
| HTTP Method | POST |
| URL | `/api/song/update` |
| Controller | `karaoke-app/.../controllers/ApiController.kt` |
| Controller method | `songs2Update` |
| Request format | `application/x-www-form-urlencoded` (query parameters) |
| Response format | `application/json` |
| Auth | `permitAll()` (admin SPA, internal network only) |

## Request Contract

### До рефактора (текущее состояние)

95 `@RequestParam(required = false) String?/Int?/Long?/Boolean?` параметров.
Spring Web молча отбрасывает неизвестные query-параметры — это
корневая причина бага #52 (см. [spec.md → Root cause](../spec.md#root-cause-pre-implementation-analysis)).

**Пример запроса (фронт, усечённо)**:
```
POST /api/song/update
?id=42
&songName=Тест
&songNameCensored=Тест★  ← этот параметр ТЕРЯЕТСЯ
&author=Иванов
&year=2024
...
```

**Проблема**: `songNameCensored` отсутствует в `@RequestParam` списке
→ Spring отбрасывает → бэкенд не обновляет `fields[SongField.NAME_CENSORED]`
→ `getDiff()` не видит изменения → UPDATE SET не содержит
`song_name_censored` → значение не сохраняется в БД.

### После рефактора (FR-011, вариант B1)

Один `@RequestParam Map<String, String> all` — Spring автоматически
собирает ВСЕ query-параметры в Map.

**Сигнатура**:
```kotlin
@PostMapping("/song/update")
@ResponseBody
fun songs2Update(
    @RequestParam all: Map<String, String>,
): SongUpdateResultDto
```

**Пример запроса** (тот же, что и до рефактора):
```
POST /api/song/update
?id=42
&songName=Тест
&songNameCensored=Тест★  ← теперь принимается автоматически
&author=Иванов
&year=2024
...
```

`all` после парсинга:
```kotlin
mapOf(
    "id" to "42",
    "songName" to "Тест",
    "songNameCensored" to "Тест★",  // ← теперь в Map
    "author" to "Иванов",
    "year" to "2024",
    // ...
)
```

`SongUpdateMapper.apply(song, all, ...)` обрабатывает каждый ключ:
- Известный стандартный → `fields[SongField.X] = value`.
- Известный special-case (`fileName`, `albumId`, `songType`) →
  custom логика (sanitize, collision check, enum mapping).
- Неизвестный → WARN-лог + ignore (FR-014 edge case).

## Response Contract

**Без изменений** (FR-013 — обратная совместимость):

```kotlin
data class SongUpdateResultDto(
    val albumLinkValid: Boolean = true,
    val fileNameRenameError: String? = null,
)
```

**HTTP коды**:
| Code | Когда |
|---|---|
| 200 OK | Успешное сохранение (или частичное — если `fileNameRenameError != null`, остальные поля всё равно сохранены) |
| 400 Bad Request | Отсутствует обязательный параметр `id` ИЛИ невалидное значение для non-string типа (например, `idStatus=abc`) |
| 404 Not Found | Song с указанным `id` не найдена |
| 500 Internal Server Error | Ошибка БД, OOM, etc. |

**Пример успешного ответа**:
```json
{
  "albumLinkValid": true,
  "fileNameRenameError": null
}
```

**Пример частичного ответа** (fileName collision):
```json
{
  "albumLinkValid": true,
  "fileNameRenameError": "Песня с именем файла «Тест.mp3» уже существует в этой папке."
}
```

## Payload Equivalence (golden-requests)

Существующие клиенты (скрипты проекта, прямые вызовы из других
контроллеров) ДОЛЖНЫ работать без изменений. Payload-формат
(query-параметры) остаётся тем же.

**Golden-request #1**: songs2Update с минимальным набором
```
POST /api/song/update?id=42&songName=Тест&songNameCensored=Тест★
```
- ДО: `songName` сохраняется, `songNameCensored` теряется.
- ПОСЛЕ: оба сохраняются.

**Golden-request #2**: songs2Update со всеми 95 параметрами
(генерируется integration-тестом; до/после сравнение `tbl_songs`
через SELECT).

## Error Handling

### Невалидный тип (FR-012)

**Запрос**:
```
POST /api/song/update?id=42&idStatus=abc
```

**Ответ** (400):
```json
{
  "error": "Bad Request",
  "message": "Invalid value for param 'idStatus': 'abc' is not a number"
}
```

### Неизвестный параметр (FR-014 edge case)

**Запрос**:
```
POST /api/song/update?id=42&unknownField=value
```

**Поведение**: WARN-лог `[SongUpdateMapper] Unknown param 'unknownField' ignored`, HTTP 200 (если остальные параметры валидны).

Альтернативное поведение (если решено строже): HTTP 400 «Unknown param 'unknownField'». Финальное решение — при реализации.

### Отсутствует `id`

**Запрос**:
```
POST /api/song/update?songName=Тест
```

**Ответ** (400):
```json
{
  "error": "Bad Request",
  "message": "Missing required param 'id'"
}
```

### Song не найдена

**Запрос**:
```
POST /api/song/update?id=999999999
```

**Ответ** (404):
```json
{
  "error": "Not Found",
  "message": "Song 999999999 not found"
}
```

## Backwards Compatibility (FR-013)

| Aspect | Status |
|---|---|
| Payload format (query-параметры) | **unchanged** |
| Response format (JSON) | **unchanged** |
| Имена параметров (camelCase) | **unchanged** |
| Обязательность `id` | **unchanged** (был `id: String`, стал `all["id"]`) |
| HTTP коды | **extended** (400 для type errors добавлен, не было раньше явно) |
| Семантика специальной обработки (fileName, albumId, songType) | **unchanged** (FR-014) |
| Семантика baseline-автоцензурирования | **unchanged** (FR-014) |

**Risk**: существующие скрипты могут полагаться на то, что `idStatus=abc`
не вызывает HTTP 400, а просто игнорируется. Митигация: текущее
поведение `@RequestParam Int?` тоже бросает 400, так что это эквивалентно.
Если найдётся regression — откатываем FR-012 на silent ignore + лог.

## Versioning

**Не версионируется отдельно.** Endpoint остаётся `/api/song/update`
(без `/v2/`), поскольку payload-формат не меняется. Если в будущем
потребуется несовместимое изменение (например, JSON body вместо query) —
вводить `/api/song/update/v2` или новый endpoint.

## Security Notes

- **Auth**: `permitAll()` (admin SPA). Доступ только из internal network.
- **SQL injection**: исключена — `saveToDb()` использует prepared statement (`?`).
- **XSS**: исключена на backend-стороне; UI использует Vue `v-model` с автоматическим escaping.
- **CSRF**: не применимо (POST из admin SPA без CSRF token — internal trust).
- **Rate limiting**: не реализован; manual editor only.
