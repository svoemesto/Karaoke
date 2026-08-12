# Contract: `POST /api/stats/debug`

**Branch**: `174-fix-stats-connection-leak` | **Date**: 2026-08-12
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Data Model**: [../data-model.md](../data-model.md)

## Endpoint

`POST /api/stats/debug`

**Auth**: `permitAll()` (admin-зона, `webvue3`).

**Purpose**: ручная диагностика состояния stats-инфраструктуры
(размер кеша, ключи, `pg_stat_activity`) при инцидентах. По образцу
`/api/public/share/debug` из спеки 167.

## Request

**Body**: пустой (или `{}`).

**Headers**: стандартные (Content-Type: application/json не обязателен).

**Пример**:
```bash
curl -X POST http://localhost:8080/api/stats/debug
```

## Response

**Status**: `200 OK` (если БД доступна для `pg_stat_activity` запроса)
или `503 Service Unavailable` (если даже debug-запрос не может
подключиться к Postgres — см.
[stats-unavailable.md](./stats-unavailable.md)).

**Body schema** (200):

```typescript
{
  cacheSize: number,                    // текущий размер кеша (включая expired)
  cacheKeys: Array<{
    endpoint: string,                   // "summary" | "timeseries" | ...
    params: Record<string, string>,     // для чистых агрегатов всегда {}
    ageSeconds: number,                 // how old is the entry
    expired: boolean,                   // true если ageSeconds > 60
  }>,
  pgActiveConnections: number,          // SELECT count(*) FROM pg_stat_activity
  pgMaxConnections: number,             // SELECT setting FROM pg_settings WHERE name='max_connections'
  timestamp: string,                    // ISO-8601 UTC, e.g. "2026-08-12T10:23:45.123Z"
}
```

**Пример ответа**:

```json
{
  "cacheSize": 6,
  "cacheKeys": [
    {"endpoint": "summary", "params": {}, "ageSeconds": 12, "expired": false},
    {"endpoint": "timeseries", "params": {}, "ageSeconds": 12, "expired": false},
    {"endpoint": "channels", "params": {}, "ageSeconds": 12, "expired": false},
    {"endpoint": "countries", "params": {}, "ageSeconds": 12, "expired": false},
    {"endpoint": "referrers", "params": {}, "ageSeconds": 12, "expired": false},
    {"endpoint": "monetization", "params": {}, "ageSeconds": 45, "expired": false}
  ],
  "pgActiveConnections": 47,
  "pgMaxConnections": 100,
  "timestamp": "2026-08-12T10:23:45.123Z"
}
```

## Errors

| Status | Когда |
|---|---|
| 200 | Успех (БД доступна, кеш инициализирован) |
| 503 | БД недоступна — возвращается [stats-unavailable.md](./stats-unavailable.md) формат |
| 500 | Непредвиденное исключение (catch-all в контроллере) |

## Backward compatibility

Новый endpoint, нет breaking changes.

## Implementation notes (для `/speckit.tasks`)

- Использовать `withDb(target = "local") { ... }` (тот же helper, что
  и в `StatsController`, см. [research.md](../research.md) §1.3).
- Получить `pgMaxConnections` через `SELECT setting FROM pg_settings WHERE name = 'max_connections'`.
- Получить `pgActiveConnections` через `SELECT count(*) FROM pg_stat_activity`.
- `StatsCache.snapshot()` — новая функция для получения всех записей
  (включая expired для observability).
