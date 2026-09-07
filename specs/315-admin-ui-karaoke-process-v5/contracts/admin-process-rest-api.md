# Contracts: Admin Process REST API (iteration #5 — «эталон»)

**Branch**: `315-admin-ui-karaoke-process-v5`
**Date**: 2026-09-07
**Spec**: [spec.md](./spec.md)
**Pattern**: `StemJobsAdminController` (lesson #3 iter #1)

## Endpoints

Все endpoints под `/api/admin/processes/`. Auth — `permitAll()` (как весь webvue3 по Karaoke constitution).

### GET /api/admin/processes

Список процессов с фильтрацией.

**Query parameters**:
- `status` (multi): enum KaraokeProcessStatuses (CREATING, WAITING, WORKING, DONE, ERROR)
- `type` (multi): enum KaraokeProcessTypes
- `threadId` (long): точное совпадение
- `chainId` (long): точное совпадение `process_chain_id`
- `includeDeleted` (bool, default false): включать `process_deleted_at IS NOT NULL`
- `name` (string, optional): `ILIKE '%' || ? || '%'`
- `topLevelOnly` (bool, default true): только head-процессы (`chainId IS NULL`). False = все.
- `parentId` (long, optional): если задан — возвращает всех детей конкретного parent (lazy load, FR-004).
- `limit` (int, default 100, max 1000)
- `offset` (int, default 0)

**Response**:
```json
{
  "total": 18,
  "items": [
    {
      "id": 12345,
      "name": "Demucs-stem-separation",
      "status": "WORKING",
      "type": "DEMUCS2",
      "command": "demucs --two-stems vocals -d cpu",
      "args": "input.wav",
      "envs": "",
      "description": "Стем-сепарация для песни X",
      "songId": 567,
      "order": 10,
      "priority": 5,
      "prioritet": 0,
      "withoutControl": false,
      "threadId": -1,
      "processChainId": null,
      "processDeletedAt": null,
      "updatedAt": "2026-09-07T10:05:00Z",
      "startedAt": "2026-09-07T10:01:00",
      "endedAt": null,
      "recordhash": "abc123..."
    }
  ]
}
```

> 20 отображаемых полей (FR-001) — без `createdAt` (нет такой колонки).

### GET /api/admin/processes/{id}

Один процесс.

**Response**: как items выше (single object).
**Errors**: 404 если не найден.

### POST /api/admin/processes/{id}/edit

Редактирование полей процесса.

**Body**:
```json
{
  "name": "Demucs-stem-separation-v2",
  "status": "WORKING",
  "order": 11,
  "priority": 5,
  "command": "demucs --two-stems vocals -d cuda",
  "args": "input.wav",
  "envs": "OMP_NUM_THREADS=4",
  "description": "Updated description",
  "songId": 567,
  "type": "DEMUCS",
  "startedAt": "2026-09-07T10:01:00Z",
  "endedAt": null,
  "prioritet": 0,
  "withoutControl": false,
  "threadId": -1
}
```

**Validation** (FR-017):
- Status transition (см. data-model.md).
- Все поля optional — обновляются только переданные.

**Side effects**:
1. UPDATE tbl_processes (без `process_deleted_at`, `process_chain_id` — FR-019).
2. INSERT INTO tbl_processes_audit (action='EDIT', old_value + new_value JSONB).
3. recordhash триггер пересчитывает md5.

**Response**: 200 OK с обновлённым процессом.
**Errors**: 400 (invalid transition), 404.

### POST /api/admin/processes/{id}/delete

Soft-delete + cancel/stop по статусу.

**Body**: empty.

**Logic** (FR-010..FR-013):
- WAITING → cancel из очереди + soft-delete.
- WORKING → Thread.interrupt() + 5 sec grace (FR-012) + destroyForcibly() + soft-delete.
- CREATING → cancel + soft-delete.
- DONE/ERROR → только soft-delete.

**Side effects**:
1. UPDATE tbl_processes SET process_deleted_at = NOW() WHERE id = ? (targeted, НЕ через save()).
2. INSERT INTO tbl_processes_audit (action='DELETE', old_value содержит статус + флаги, new_value.process_deleted_at = NOW()).

**Response**: 200 OK.
**Errors**: 404, 409 (если уже удалён).

### POST /api/admin/processes/{id}/retry

Retry ERROR-процесса.

**Body**: empty.

**Logic** (FR-014, T048 iter #3):
- Если `status == ERROR` И `process_deleted_at IS NULL` → UPDATE status = WAITING (targeted).
- INSERT INTO tbl_processes_audit (action='RETRY', old_value.status='ERROR', new_value.status='WAITING', FR-015).

**Response**: 200 OK.
**Errors**: 404, 409 (если status != ERROR), 409 (если process_deleted_at IS NOT NULL).

### GET /api/admin/processes/{id}/audit

Audit log процесса.

**Query parameters**:
- `days` (int, default 30, max 30): сколько дней назад.

**Response**:
```json
{
  "items": [
    {
      "id": 901,
      "processId": 12345,
      "actor": "admin",
      "action": "EDIT",
      "oldValue": {"name": "Demucs-stem-separation"},
      "newValue": {"name": "Demucs-stem-separation-v2"},
      "createdAt": "2026-09-07T11:00:00Z"
    }
  ]
}
```

**Filter**: `WHERE created_at > NOW() - make_interval(days => ?)` (FR-018, PostgreSQL bind в INTERVAL).

> ⚠️ **JDBC**: параметр передаётся `ps.setInt(idx, days)`. **НЕ** писать `INTERVAL '? days'` — `?` внутри строкового литерала **НЕ** является JDBC-плейсхолдером (bind не сработает); `INTERVAL ?` без кавычек — синтаксическая ошибка PG. Только `make_interval(days => ?)` корректен (урок RC-2 iter #3).

---

## Error Format

```json
{
  "error": "INVALID_STATUS_TRANSITION",
  "message": "Cannot transition from WAITING to DONE (must go through WORKING)",
  "details": {
    "currentStatus": "WAITING",
    "requestedStatus": "DONE"
  }
}
```

---

## Frontend ↔ Backend contract (Vue 3 store)

### webvue3/src/components/Processes/store.js

```javascript
// State
{
  items: [],
  total: 0,
  filters: {
    status: [],
    type: [],
    threadId: null,
    chainId: null,
    includeDeleted: false,
    name: null,
    topLevelOnly: true,
  },
  loading: false,
  error: null,
}

// Actions
loadProcesses(filters)
loadChildren(parentId)
editProcess(id, changes)
deleteProcess(id)
retryProcess(id)
loadAudit(id, days = 30)
```

**Note**: state живёт рядом с компонентами (convention проекта, см. iter #1 merge #418: `webvue3/src/components/Processes/store.js` + `webvue3/src/components/Processes/filter/store.js`). Глобальный `webvue3/src/store/processes/store.js` НЕ существует.

---

## Convention match (Lesson #1 iter #1)

Backend controller pattern: `StemJobsAdminController` (читай `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StemJobsAdminController.kt` как reference).

Frontend edit-modal pattern: `SongEdit.vue` + `SongEditModal.vue` (читай `webvue3/src/components/Songs/edit/` как reference).

DI: `@DependsOn("karaokeAppService")` на `KaraokeProcessAdminService` И на `KaraokeProcessAdminController` (Lesson #4 iter #1, plan.md стр. 96).

— Илья (boss)
