# Phase 1 Data Model: Admin UI для KaraokeProcess (iteration #5 — «эталон»)

**Branch**: `315-admin-ui-karaoke-process-v5`
**Date**: 2026-09-07
**Spec**: [spec.md](./spec.md)

## Сущности

### 1. KaraokeProcess (existing, modified)

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt` (в корне пакета, **НЕ** `beans/`)

| Поле | Тип | Описание | Editable? |
|---|---|---|---|
| `id` | Int PK | Первичный ключ | no |
| `name` | String | Имя процесса | yes (FR-008) |
| `status` | Enum `KaraokeProcessStatuses` | CREATING/WAITING/WORKING/DONE/ERROR | yes (FR-008, FR-017 transitions) |
| `type` | Enum `KaraokeProcessTypes` | 31 активная константа (ещё 3 закомментированы, файл `KaraokeProcessTypes.kt`) | yes (FR-008) |
| `command` | String | Команда OS | yes (FR-008) |
| `args` | String | Аргументы | yes (FR-008) |
| `envs` | String | Переменные окружения | yes (FR-008) |
| `description` | String | Описание | yes (FR-008) |
| `songId` | Int? FK | Привязка к песне | yes (FR-008) |
| `order` | Int | Порядок | yes (FR-008) |
| `priority` | Int | Приоритет | yes (FR-008) |
| `prioritet` | Int | Доп. приоритет | yes (FR-008) |
| `withoutControl` | Boolean | Без контроля | yes (FR-008) |
| `threadId` | Int | Лейн (≠ chainId!) | yes (FR-008) |
| `process_chain_id` | Long? FK self | **NEW (миграция 47)** — parent в цепочке | **NO** (FR-019 race protection, управляется targeted UPDATE) |
| `process_deleted_at` | Timestamp? | **NEW (миграция 47)** — soft-delete timestamp | **NO** (FR-019, targeted UPDATE) |
| `last_update` | Timestamp | **Реальное имя** для "updatedAt" (НЕ `updated_at`!) | no (autoset) |
| `process_start` | Timestamp? | **Реальное имя** для "startedAt" (НЕ `started_at`!) | yes (FR-008, через Local-поля) |
| `process_end` | Timestamp? | **Реальное имя** для "endedAt" (НЕ `ended_at`!) | yes (FR-008, через Local-поля) |
| `recordhash` | String | md5(COALESCE-конкатенация значений полей) — для diff LOCAL↔SERVER | no (autoset триггером) |

**Lifecycle** (статусы):

```
CREATING ─→ WAITING ─→ WORKING ─→ DONE
   │          │          │       (terminal)
   │          │          ↓
   │          │        ERROR ─→ WAITING (retry, FR-014)
   │          │          │
   ↓          ↓          ↓
   └──────────┴──────────┴── delete (US3, soft + cancel/stop по статусу)
```

**CASCADE**: NO (owner clarification iter #1, FR-013). Дети остаются при удалении parent.

**Validation rules** (FR-017):
- CREATING → WAITING / WORKING / ERROR
- WAITING → WORKING (только; отмена = delete US3)
- WORKING → DONE / ERROR (отмена = delete US3)
- DONE → (terminal)
- ERROR → WAITING (только через retry FR-014)

---

### 2. ProcessAuditEntry (new table)

**Миграция**: `deploy/karaoke-db/47_admin_process_audit.sql` (уже в master, коммит `7ad6313c`)

| Поле | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | Первичный ключ |
| `process_id` | BIGINT FK tbl_processes.id | Какой процесс |
| `actor` | VARCHAR(64) | Кто (user/actor identifier) |
| `action` | VARCHAR CHECK (action IN ('EDIT','RETRY','DELETE')) | Тип операции |
| `old_value` | JSONB | Полный diff старое (snapshot полей) |
| `new_value` | JSONB | Полный diff новое (snapshot полей) |
| `created_at` | TIMESTAMP DEFAULT NOW() | Когда |

**Indexes** (из миграции 47):
- `idx_tbl_processes_audit_process_id` ON tbl_processes_audit(process_id)
- `idx_tbl_processes_audit_created_at` ON tbl_processes_audit(created_at)

**Retention** (FR-018): 30 дней. Cron `DELETE FROM tbl_processes_audit WHERE created_at < NOW() - INTERVAL '30 days'`.

**Запись при операциях** (FR-016):
- EDIT: `old_value` = снимок полей до, `new_value` = снимок полей после (только изменённые поля).
- RETRY: `old_value.status` = 'ERROR', `new_value.status` = 'WAITING'.
- DELETE: `old_value.status` = текущий статус + флаги (например WORKING+timeout), `new_value.process_deleted_at` = NOW().

---

### 3. Механизм исполнения (existing) — не `KaraokeProcessQueue`

**⚠️ Кирилл iter #4 grep-контроль**: класса `KaraokeProcessQueue` в кодовой базе НЕТ (0 вхождений по всем модулям; путь `beans/KaraokeProcessQueue.kt` неверен). Термин «KaraokeProcessQueue» — устоявшийся из старых спек 234/313/314 (владелец понимает). **Реальный механизм**:

- `KaraokeProcessWorker.kt` — `threadsMap[threadId] = KaraokeProcessThread(karaokeProcess)` (стр. ~1075).
- `KaraokeProcessWorker.kt` (class `KaraokeProcessThread` стр. ~70, статус WORKING выставляется здесь, стр. ~89).
- `KaraokeProcessWorker.kt` (стр. ~1340) — `runCatching { thread.interrupt() }` для stop WORKING.
- `KaraokeProcess.kt` companion-helpers (стр. ~609) — `setWorkingToWaiting(database)` — recovery-reset WORKING→WAITING; для retry **НЕ использовать**. Retry (FR-014) — отдельный targeted UPDATE `status: ERROR→WAITING` (+ условие `process_deleted_at IS NULL`).

**Cancel WAITING (FR-011)** = soft-delete (`process_deleted_at = NOW()`) + гарантия что pickup-логика Worker учитывает `process_deleted_at IS NULL` (конкретный механизм зафиксировать в tasks с verify по коду Worker).

**⚠️ Кирилл iter #5 Р-3** (новое для iter #5): `KaraokeProcessWorker.pickup*` методы должны добавить `WHERE process_deleted_at IS NULL` (сейчас 0 вхождений grep). Без этого pickup-логика может подхватить soft-deleted процессы. **Задача в tasks.md** для iter #5 (plan R-019).

---

## Отношения

```
KaraokeProcess (head) ──1:N──→ KaraokeProcess (tail)    via process_chain_id
KaraokeProcess ──1:N──→ ProcessAuditEntry                via process_id
```

## Race Protection (FR-019)

`KaraokeProcess.save()` НЕ пишет `process_deleted_at` и `process_chain_id`. Эти поля управляются через **targeted UPDATE через raw JDBC** в `KaraokeProcessAdminService`. Реальный паттерн (reference: `KaraokeProcess.kt:361`):

```kotlin
// НЕ делать так:
fun save() {
    // ... UPDATE tbl_processes SET ..., process_deleted_at = ?, process_chain_id = ? WHERE id = ?
}

// Делать так (raw JDBC через KaraokeConnection.getConnection()):
fun softDelete(db: KaraokeConnection, id: Long) {
    db.getConnection()?.use { conn ->
        conn.prepareStatement(
            "UPDATE tbl_processes SET process_deleted_at = NOW() WHERE id = ?"
        ).use { ps ->
            ps.setLong(1, id)
            ps.executeUpdate()
        }
    }
    // Audit пишется отдельной записью, НЕ через save() (FR-019)
}

fun setChainId(db: KaraokeConnection, id: Long, parentId: Long) {
    db.getConnection()?.use { conn ->
        conn.prepareStatement(
            "UPDATE tbl_processes SET process_chain_id = ? WHERE id = ?"
        ).use { ps ->
            ps.setLong(1, parentId)
            ps.setLong(2, id)
            ps.executeUpdate()
        }
    }
}
```

**Note**: `KaraokeConnection` экспонирует только `getConnection()` (ThreadLocal, self-healing). Ни `KaraokeConnection`, ни `KaraokeDbTable` НЕ имеют метода `executeUpdate(...)` — это метод `java.sql.PreparedStatement`, нельзя вызывать как `connection.executeUpdate(...)`.

---

## Миграции

- **47_admin_process_audit.sql** (master, `7ad6313c`):
  - `ALTER TABLE tbl_processes ADD COLUMN process_chain_id BIGINT NULL`
  - `ALTER TABLE tbl_processes ADD COLUMN process_deleted_at TIMESTAMP NULL`
  - `CREATE TABLE tbl_processes_audit (...)`
  - 5 indexes
  - recordhash trigger
  - **NO backfill** (per Assumption в спеке, R-005)

**Verify перед implement**: `psql -d karaoke_local -c '\d tbl_processes'` — должны быть новые колонки.

— Илья (boss)
