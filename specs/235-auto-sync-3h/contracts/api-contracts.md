# API Contracts: Автозапуск «Синхронизации в 1 клик»

> Phase 1 output для фичи 235. Источник: [`spec.md`](../spec.md) (FR-009, FR-015), [`data-model.md`](../data-model.md).

## Обзор

| Endpoint | Метод | Auth | Изменения |
|----------|-------|------|-----------|
| `POST /api/sync/oneclick` | POST | `permitAll()` (admin SPA) | **MODIFIED** — оборачивается в `try { running.compareAndSet }` → `409 Conflict` если автозапуск уже идёт |
| `GET /api/sync/auto-status` | GET | `permitAll()` (admin SPA) | **NEW** — возвращает статус автозапуска |
| (прочие `/api/sync/*`) | — | — | **Без изменений** |

Auth — `permitAll()` как у всех sync-endpoint'ов (см. существующий `postSyncOneClick` в `ApiController.kt:5284` и `SecurityConfig`).

---

## 1. POST /api/sync/oneclick — MODIFIED

### Поведение до фичи
Существующая бизнес-логика (см. `ApiController.kt:5284-5320`): синхронно запускает `SyncRegistry.all.map { runEntitySync(target.key, target.oneClickDirection) }`. Возвращает `List<SyncOneClickResultDto>` (200 OK).

### Поведение после фичи
То же самое, **НО** перед запуском — попытка захватить общий `AtomicBoolean running`:
- **Успех** (`compareAndSet(false, true)`) → выполнить, в `finally` — `running.set(false)`. Возврат 200 OK.
- **Провал** (кто-то уже держит lock — автозапуск или другой ручной вызов) → **HTTP `409 Conflict`** с телом `{"error":"sync_in_progress","message":"Автосинхронизация уже выполняется в фоне, дождитесь завершения"}`. **Без** вызова `runEntitySync`.

### Контракт

**Request**:
```
POST /api/sync/oneclick
Content-Type: application/json
```

Тело: пустое (как раньше). Параметров нет.

**Response 200 OK** (ручной запуск прошёл):
```json
[
  {
    "key": "songs",
    "displayName": "Песни",
    "direction": "LOCAL_TO_SERVER",
    "skipped": false,
    "created": ["Song #123"],
    "updated": ["Song #456", "Song #789"],
    "deleted": [],
    "moved": []
  },
  {
    "key": "users",
    "displayName": "Пользователи",
    "direction": "SERVER_TO_LOCAL",
    "skipped": false,
    "created": ["User #42"],
    "updated": [],
    "deleted": [],
    "moved": []
  }
]
```
Структура `SyncOneClickResultDto` без изменений (см. `ApiController.kt:176-185`).

**Response 409 Conflict** (autosync или другой manual уже идёт):
```json
{
  "error": "sync_in_progress",
  "message": "Автосинхронизация уже выполняется в фоне, дождитесь завершения"
}
```

**Response 500** — то же, что было раньше (исключение из `runEntitySync` в случае бага). **Без изменений**.

### UI-поведение (webvue3)

`SyncTable.vue:208 doOneClick()` — без изменений в `dispatch('runSyncOneClickPromise')` (см. `store.js:48`). Только обработка ошибки 409 в `.catch()`:

```js
.catch((error) => {
  // Спека 235: 409 Conflict → понятный alert
  if (error && error.status === 409) {
    this.customConfirmParams = {
      isAlert: true,
      alertType: 'error',
      header: 'Синхронизация в 1 клик',
      body: 'Автосинхронизация уже выполняется в фоне, дождитесь завершения',
      timeout: 15,
    }
    this.isCustomConfirmVisible = true
    return
  }
  // Существующая обработка
  ...
})
```

Для этого `promisedXMLHttpRequest` должен пробрасывать `status` (проверить реализацию в `webvue3/src/lib/utils.js`; если нет — добавить). **OUT OF SCOPE спеки 235** — это деталь реализации `tasks.md`.

---

## 2. GET /api/sync/auto-status — NEW

### Назначение
Возвращает статус автозапуска для UI-блока «Автозапуск» на странице `/sync`. Вызывается в `mounted()` хуке `SyncTable.vue` через `loadSyncAutoStatusPromise`.

### Контракт

**Request**:
```
GET /api/sync/auto-status
Accept: application/json
```

Тело: пустое. Параметров нет.

**Response 200 OK** (всегда 200, даже если `enabled=false` или history пуста):
```json
{
  "enabled": true,
  "intervalMs": 10800000,
  "initialDelayMs": 300000,
  "lastRun": {
    "startedAt": "2026-08-16T15:00:00Z",
    "finishedAt": "2026-08-16T15:00:47Z",
    "status": "SUCCESS",
    "reason": null,
    "totals": { "created": 3, "updated": 12, "deleted": 1, "moved": 0 },
    "perTarget": [
      {
        "key": "songs",
        "displayName": "Песни",
        "direction": "LOCAL_TO_SERVER",
        "skipped": false,
        "created": ["Song #123"],
        "updated": ["Song #456"],
        "deleted": [],
        "moved": []
      }
    ]
  },
  "last10": [
    { "startedAt": "...", "finishedAt": "...", "status": "SUCCESS", "reason": null, "totals": {...}, "perTarget": [...] },
    { "startedAt": "...", "finishedAt": "...", "status": "FAILED", "reason": "SQLException: connection refused", "totals": {...}, "perTarget": [...] },
    ...  // до 10 элементов, newest first
  ],
  "nextRunEstimate": "2026-08-16T18:00:00Z"
}
```

### Поля

| Поле | Тип | Когда null/пусто |
|------|-----|---|
| `enabled` | `bool` | никогда (default `true` если ключ не задан) |
| `intervalMs` | `long` | никогда (default `10800000`) |
| `initialDelayMs` | `long` | никогда (default `300000`) |
| `lastRun` | `object \| null` | `null` если `history` пуст (т.е. ни один тик ещё не отработал с момента старта JVM) |
| `last10` | `array` | `[]` если `history` пуст |
| `nextRunEstimate` | `string \| null` | `null` если `enabled=false`; иначе `ISO-8601` момент в будущем, вычисленный как `lastRun.finishedAt + intervalMs` (если есть lastRun) или `appStartTime + initialDelayMs` (если lastRun ещё нет) |
| `perTarget[]` | `array` | переиспользует `SyncOneClickResultDto` |
| `totals` | `object` | сумма по всем `perTarget` для этого тика |
| `status` | `string` | `"RUNNING" \| "SUCCESS" \| "FAILED"` |
| `reason` | `string \| null` | текст ошибки для `FAILED`; null для `SUCCESS` |

### Error responses

- **500 Internal Server Error** — если scheduler-бин не зарегистрирован (не должно случаться в нормальной конфигурации). UI показывает alert «Не удалось получить статус автозапуска».
- Никаких 4xx — endpoint только для чтения и `permitAll()`.

---

## 3. Не-SSE-канал

**SSE НЕ используется** (Q2 в Clarifications). UI обновляет блок «Автозапуск» при монтировании `SyncTable.vue` и по F5. Существующий SSE-канал `SYNC` остаётся без изменений.

---

## 4. Backward compatibility

| Endpoint | Совместимость |
|----------|---------------|
| `POST /api/sync/oneclick` | ✅ Частичная — в happy-path 200 OK без изменений. Новое: 409 Conflict в случае гонки. Существующие клиенты, не обрабатывающие 409, **могут** упасть. UI webvue3 обновляется в этой же фиче. |
| `GET /api/sync/entities` | ✅ Без изменений |
| `GET /api/sync/run`, `POST /api/sync/setflag` | ✅ Без изменений |
| `GET /api/sync/auto-status` | NEW — нет breaking change |

---

## 5. Примеры cURL

### Ручной клик (happy path)
```bash
curl -X POST http://localhost:8080/api/sync/oneclick
# → 200 OK + JSON list (как раньше)
```

### Ручной клик во время автозапуска
```bash
# (в отдельном терминале — `tail -F` логов karaoke-app)
curl -X POST http://localhost:8080/api/sync/oneclick
# → 409 Conflict
# {"error":"sync_in_progress","message":"Автосинхронизация уже выполняется в фоне, дождитесь завершения"}
```

### Чтение статуса
```bash
curl http://localhost:8080/api/sync/auto-status
# → 200 OK + JSON (см. формат выше)
```

### Чтение статуса при выключенной фиче
```bash
# KaraokeProperties: autoOneClickSyncEnabled = false
curl http://localhost:8080/api/sync/auto-status
# → 200 OK
# {"enabled": false, "intervalMs": 10800000, "initialDelayMs": 300000,
#  "lastRun": null, "last10": [], "nextRunEstimate": null}
```
