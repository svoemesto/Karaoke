# Quickstart: Массовые действия с процессами (iter #317)

**Branch**: `317-process-bulk-actions` | **Date**: 2026-09-08 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Contracts**: [contracts/karaoke-process-bulk-actions-api.md](./contracts/karaoke-process-bulk-actions-api.md)

**Назначение**: документ для **runtime-верификации** владельцем после implement (Stage 8 + Stage 9 re-review APPROVE). Агент НЕ делает эти сценарии — только владелец (governance, Constitution V).

## Prerequisites

- `karaoke-app` собран и развёрнут на admin-машине (nsa-i9 / nsa).
- `webvue3` собран (`pnpm vite build`) и admin-SPA доступна в браузере.
- В `ProcessesTable.vue` есть 2 новые кнопки в шапке (после Фильтр): «Массовое изменение поля» + «Массовое удаление».
- 5-step verification subset зелёный (compileKotlin + ktlintCheck + bootJar для app+web, eslint + vite build для frontend).

## Сценарии верификации

### Scenario 1 (US1 — bulk-update status)

**Setup**: `ProcessesView` → применить фильтр `status=ERROR` → 5-10 процессов.

**Действия**:
1. Нажать кнопку **«Массовое изменение поля»**.
2. В `<custom-confirm>`-диалоге выбрать поле **«Статус»** → новое значение **«WAITING»** → нажать **«Да»**.
3. Backend отвечает `{"updated": N, "skipped": 0}`.
4. Таблица обновляется — у всех N процессов `processStatus` стал `WAITING`.

**Verification** (psql):
```sql
SELECT id, process_status FROM tbl_processes
WHERE id = ANY(ARRAY[12345, 12346, ...]);
-- ожидается process_status = 'WAITING'
```

### Scenario 2 (US1 — bulk-update status с transition error)

**Setup**: filter `status=CREATING` → 3 процесса.

**Действия**:
1. Кнопка «Массовое изменение поля» → поле **«Статус»** → значение **«DONE»** (запрещённый переход CREATING→DONE, см. FR-017 spec #315) → **«Да»**.
2. Backend отвечает `{"updated": 0, "skipped": 3, "reasons": {"<id>": "недопустимый переход CREATING→DONE", ...}}`.
3. Таблица НЕ изменилась (CREATING → CREATING).

**Verification**:
```sql
SELECT process_status FROM tbl_processes WHERE id IN (...);
-- ожидается всё ещё CREATING
```

### Scenario 3 (US1 — bulk-update priority performance, SC-008)

**Setup**: filter без фильтра → все процессы (должно быть ≥ 1000).

**Действия**:
1. Кнопка «Массовое изменение поля» → поле **«Приоритет»** → значение **«-1»** → **«Да»**.
2. Backend выполняет за ≤ **2 секунды** (SC-008).
3. Backend отвечает `{"updated": ≥1000, "skipped": 0}`.

**Verification**:
```sql
SELECT COUNT(*) FROM tbl_processes WHERE process_priority = -1;
-- ожидается ≥ 1000
```

### Scenario 4 (US1 — bulk-update threadId)

**Setup**: filter `status=ERROR` → 3 процесса.

**Действия**:
1. Кнопка «Массовое изменение поля» → поле **«threadId»** → значение **«42»** → **«Да»**.
2. Backend отвечает `{"updated": 3, "skipped": 0}`.
3. Таблица обновляется — у всех 3 процессов `threadId = 42`.

### Scenario 5 (US1 — bulk-update пустая выборка)

**Setup**: filter, дающий 0 процессов (например, `chainId=999999` если такого нет).

**Действия**:
1. Кнопка **«Массовое изменение поля»** — **disabled** (`:disabled="countRows === 0"`).
2. Клик не открывает диалог.

### Scenario 6 (US2 — bulk-delete soft-delete)

**Setup**: filter `status=DONE` AND `process_end < NOW() - INTERVAL '30 days'` → 10 процессов.

**Действия**:
1. Кнопка **«Массовое удаление»**.
2. `<custom-confirm>`-диалог «Будет удалено процессов: 10. Действие необратимо. Продолжить?» → **«Да»**.
3. Backend отвечает `{"deleted": 10, "skipped": 0}`.
4. Таблица обновляется — 10 процессов исчезли (фильтр по умолчанию исключает `processDeletedAt IS NOT NULL`).

**Verification**:
```sql
SELECT process_deleted_at FROM tbl_processes WHERE id IN (...);
-- ожидается process_deleted_at NOT NULL
```

### Scenario 7 (US2 — bulk-delete head with children, FR-007)

**Setup**: создать тестовую цепочку (head + 2 child) одним ручным INSERT'ом в БД:

```sql
INSERT INTO tbl_processes (..., process_chain_id, process_deleted_at)
VALUES
  (..., NULL, NULL),  -- head (id=12345)
  (..., 12345, NULL),  -- child1
  (..., 12345, NULL);  -- child2
```

**Действия**:
1. Filter → head id (12345) — выбрать только head.
2. Кнопка «Массовое удаление» → **«Да»**.
3. Backend отвечает `{"deleted": 0, "skipped": 1, "reasons": {"12345": "has_children (child_count: 2)"}}`.
4. Head остался (`process_deleted_at IS NULL`), дети тоже остались.

**Verification**:
```sql
SELECT id, process_chain_id, process_deleted_at FROM tbl_processes
WHERE process_chain_id = 12345 OR id = 12345;
-- ожидается: все 3 строки с process_deleted_at IS NULL
```

### Scenario 8 (US2 — bulk-delete пустая выборка)

**Setup**: filter, дающий 0 строк.

**Действия**: кнопка «Массовое удаление» — **disabled**.

### Scenario 9 (audit — bulk-update audit-trail, SC-004)

**Verification** (после Scenario 1):
```sql
SELECT COUNT(*) FROM tbl_processes_audit
WHERE process_id = ANY(ARRAY[12345, 12346, ...])
  AND action = 'bulk-update'
  AND created_at >= NOW() - INTERVAL '1 minute';
-- ожидается = updated count из Scenario 1
```

### Scenario 10 (audit — bulk-delete audit-trail)

**Verification** (после Scenario 6):
```sql
SELECT COUNT(*) FROM tbl_processes_audit
WHERE process_id = ANY(ARRAY[12345, 12346, ...])
  AND action = 'bulk-delete'
  AND created_at >= NOW() - INTERVAL '1 minute';
-- ожидается = deleted count из Scenario 6
```

## Cleanup

После всех 10 scenarios:

1. **OpenProject WP #67**: add-comment с результатами runtime-верификации (владелец).
2. `tracker.sh mark-review 67` (после успешной runtime-верификации).
3. `tracker.sh close-issue 67` (по владельцу).
4. Git: squash commit + push + merge (по владельцу).
5. Финальный comment в WP #67 — "runtime-verify passed, closed".

## Governance

- Владелец: runtime-verify (этот quickstart), smoke-test (curl, psql), visual verify (браузер).
- Агент: 5-step verification subset (compile/lint/build), regression grep, **НЕ** runtime-verify.
- Если сценарий провалился — откатить изменения в working tree, повторить fix, повторить cycle 2 (Кирилл Stage 5/6 + Марк Stage 9 + владелец runtime-verify).

## Self-check (после implementation, перед mark-review)

- [ ] **SC-001**: 100% запусков bulk-update открывают `<custom-confirm>`-диалог (нет прямого вызова API без диалога).
- [ ] **SC-002**: 100% запусков bulk-delete открывают `<custom-confirm>`-диалог.
- [ ] **SC-003**: `updated + skipped == ids.length` (или меньше если 4xx).
- [ ] **SC-004**: `SELECT COUNT(*) FROM tbl_processes_audit` равен `updated` для bulk-update.
- [ ] **SC-005**: soft-delete verified через psql `process_deleted_at NOT NULL`.
- [ ] **SC-006**: bulk-delete head с детьми → `{"deleted": 0, "skipped": 1}` + дети нетронуты.
- [ ] **SC-007**: invalid input (priority=abc, status=UNKNOWN, threadId=-1, пустой ids, ids > 10000) → 400 с понятным message.
- [ ] **SC-008**: bulk-update/bulk-delete для 1000 процессов ≤ 2 сек.

**Все 8 SC PASS → runtime-verify passed → mark-review → close-issue.**
