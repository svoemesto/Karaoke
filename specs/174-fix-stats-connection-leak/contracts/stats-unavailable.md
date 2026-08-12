# Contract: `503 stats.unavailable`

**Branch**: `174-fix-stats-connection-leak` | **Date**: 2026-08-12
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Data Model**: [../data-model.md](../data-model.md)

## Когда возвращается

Когда `KaraokeConnection.getConnection()` возвращает `null` ИЛИ
бросает исключение с message содержащим `"too many clients already"`
(или другие PostgreSQL-ошибки подключения) при обработке любого
`/api/stats/*` endpoint'а.

Паттерн заимствован из спеки 167 (`share.internal`).

## Response

**Status**: `503 Service Unavailable`

**Headers**:
- `Retry-After: 10` (в секундах; константа в текущей реализации,
  потенциально через env в будущем).

**Body schema**:

```typescript
{
  errorCode: "stats.unavailable",       // фиксированная строка
  retryAfterSeconds: 10,                // дублирует Retry-After заголовок для удобства фронта
  endpoint?: string,                    // имя endpoint'а (для логов, опционально)
  cause?: string,                       // класс исключения (для логов, опционально)
}
```

**Пример ответа**:

```bash
HTTP/1.1 503 Service Unavailable
Retry-After: 10
Content-Type: application/json

{
  "errorCode": "stats.unavailable",
  "retryAfterSeconds": 10,
  "endpoint": "/api/stats/summary"
}
```

## Frontend handling

`StatsView.vue` / дочерние компоненты (`KpiCards`, `MonetizationPanel`,
и т.п.):

1. Перехватывают HTTP `503` с `Content-Type: application/json`.
2. Парсят body, извлекают `retryAfterSeconds`.
3. Показывают `<DbOverloadBanner :retry-after-seconds="..." :error-code="..." />`
   вместо пустого графика.
4. НЕ делают повторный запрос автоматически в течение
   `retryAfterSeconds` (см. [FR-011](../spec.md) — клиентский throttling).

## Logging

Каждый `503` MUST логироваться через SLF4J `log.warn`:

```kotlin
log.warn(
    "stats.unavailable endpoint={} cause={}",
    requestURI,
    e::class.simpleName,
)
```

Пример лог-строки:
```
2026-08-12T10:23:45.123 WARN  [http-nio-8080-exec-7] StatsController - stats.unavailable endpoint=/api/stats/summary cause=PSQLException
```

## Backward compatibility

Существующие endpoint'ы возвращали `200 OK` с пустым массивом при сбое
БД (текущее поведение `StatBySong.getStatBySong()` и др.). После фикса
**поведение МЕНЯЕТСЯ** — это intentional breaking change для
admin-эндпоинтов (не для публичного API). Документировано в
[spec.md](../spec.md) FR-003.

`/api/stats/*` — admin-only (`webvue3`, `permitAll()`), не затрагивает
публичный сайт (`karaoke-public`).

## Implementation notes (для `/speckit.tasks`)

В `StatsController`:

```kotlin
@GetMapping("/api/stats/summary")
fun summary(@RequestParam(required = false) target: String?): ResponseEntity<Map<String, Any>> {
    return try {
        val body = withDb(target) { db -> mapOf("summary" to StatsByEvents.getSummary(database = db)) }
        ResponseEntity.ok(body)
    } catch (e: SQLException) {
        if (e.message?.contains("too many clients") == true) {
            log.warn("stats.unavailable endpoint=/api/stats/summary cause=${e::class.simpleName}")
            ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "10")
                .body(mapOf(
                    "errorCode" to "stats.unavailable",
                    "retryAfterSeconds" to 10,
                    "endpoint" to "/api/stats/summary",
                ))
        } else {
            throw e  // другие SQL-исключения — стандартный Spring 500
        }
    }
}
```

Касается всех 6 кешируемых endpoint'ов + опционально других stats
endpoint'ов (`/api/stats/by-song`, и т.д.) — но последние могут остаться
с текущим поведением «пустой массив + 200» (см. FR-004 в spec.md —
кешируются только 6 чистых агрегатов).
