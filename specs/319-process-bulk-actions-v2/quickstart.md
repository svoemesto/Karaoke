# Quickstart: Массовые действия с процессами (v2)

**Created**: 2026-09-08 | **Spec**: `specs/319-process-bulk-actions-v2/spec.md`

Validation scenarios для проверки фичи end-to-end. **Не** содержит implementation
кода — только команды и проверки.

---

## Setup

```bash
# 1. На feature-ветке
git checkout 319-process-bulk-actions-v2

# 2. Применить миграцию на LOCAL
psql -h localhost -U karaoke -d karaoke_local < deploy/karaoke-db/48_admin_process_bulk_actions.sql
# Ожидаем: ALTER TABLE, CREATE INDEX — без ошибок

# 3. Проверить CHECK constraint
psql -h localhost -U karaoke -d karaoke_local -c "
  SELECT conname, pg_get_constraintdef(oid)
  FROM pg_constraint
  WHERE conrelid = 'tbl_processes_audit'::regclass AND contype = 'c';
"
# Ожидаем: action CHECK включает 'bulk_update_field' и 'bulk_delete'

# 4. Проверить batch_id колонку
psql -h localhost -U karaoke -d karaoke_local -c "
  \d tbl_processes_audit
"
# Ожидаем: колонка batch_id UUID NULL, индекс idx_tbl_processes_audit_batch_id

# 5. Backend: пересобрать
./gradlew :karaoke-app:bootJar --parallel

# 6. Frontend: пересобрать
cd webvue3 && npm run build && cd ..

# 7. Запустить (на nsa-i9 без согласия)
cd deploy && bash do.sh build_start_public
# Если менялся karaoke-app: bash do.sh build_webvue3
```

---

## Scenario 1: Sync bulk-edit (5 процессов)

**Цель**: проверить, что bulk-edit поля работает для малого объёма.

**Setup**:
```bash
# Создать 5 ERROR-процессов через тестовый скрипт или вручную через admin UI:
# 1. Открыть webvue3 → Processes
# 2. Создать 5 процессов с command="echo test" — дождаться ERROR
# 3. Записать их id (например: 100, 101, 102, 103, 104)
```

**Шаги**:
1. Открыть `webvue3/processes`.
2. Фильтр: `status = ERROR`, `type = echo`.
3. Должно появиться «Отобрано: 5» и активные bulk-кнопки.
4. Нажать «Изменить поле» → выбрать `status` → `WAITING` → подтвердить.

**Ожидаемый результат**:
```bash
# Проверить БД
psql -c "SELECT id, process_status FROM tbl_processes WHERE id IN (100,101,102,103,104);"
# Ожидаем: все 5 имеют process_status='WAITING'

# Проверить audit
psql -c "
  SELECT process_id, action, actor,
         jsonb_extract_path_text(new_value, 'process_status') AS new_status,
         batch_id
  FROM tbl_processes_audit
  WHERE batch_id IS NOT NULL
  ORDER BY created_at DESC LIMIT 5;
"
# Ожидаем: 5 строк action='bulk_update_field', batch_id одинаковый (UUID),
# new_status='WAITING'
```

**Готово, если**: UI показывает отчёт «5 из 5 успешно», БД обновлена, audit записан.

---

## Scenario 2: Sync bulk-delete (5 процессов)

**Цель**: проверить, что hard-delete работает и audit каскадно удаляется.

**Шаги**:
1. Продолжение Scenario 1 (5 процессов уже в status=WAITING).
2. Фильтр: `status = WAITING`, `type = echo`.
3. Нажать «Удалить» → подтвердить confirm-диалог.

**Ожидаемый результат**:
```bash
# Процессы удалены из tbl_processes
psql -c "SELECT count(*) FROM tbl_processes WHERE id IN (100,101,102,103,104);"
# Ожидаем: 0

# Audit каскадно удалён (FK ON DELETE CASCADE)
psql -c "SELECT count(*) FROM tbl_processes_audit WHERE process_id IN (100,101,102,103,104);"
# Ожидаем: 0
```

**Готово, если**: UI показывает «Удалено: 5 из 5», БД чистая, audit тоже чистый.

---

## Scenario 3: Async bulk-delete (1500 процессов)

**Цель**: проверить async endpoint и polling.

**Setup**:
```bash
# Создать 1500 ERROR-процессов через SQL
psql -c "
  INSERT INTO tbl_processes (process_status, process_type, process_command, process_priority)
  SELECT 'ERROR', 'echo', 'echo test', 0
  FROM generate_series(1, 1500);
"
```

**Шаги**:
1. Открыть `webvue3/processes`.
2. Фильтр: `status = ERROR`, `type = echo`.
3. Должно появиться «Отобрано: 1500».
4. Нажать «Удалить» → подтвердить.
5. Наблюдать прогресс в UI.

**Ожидаемый результат**:
- UI показывает spinner + «Удалено: X / 1500» (обновляется каждые 2 сек).
- По завершении — отчёт «Удалено: 1500 из 1500».

```bash
# Все ERROR-процессы этого типа удалены
psql -c "SELECT count(*) FROM tbl_processes WHERE process_status='ERROR' AND process_type='echo';"
# Ожидаем: 0
```

**Готово, если**: UI прогресс виден, финальный отчёт корректный, БД чистая.

---

## Scenario 4: Edge — изменение фильтра во время операции

**Цель**: проверить snapshot-семантику (FR-004).

**Setup**: иметь 100 ERROR-процессов.

**Шаги**:
1. Фильтр: `status = ERROR`.
2. Snapshot: 100 id (UI показывает «Отобрано: 100»).
3. Нажать «Изменить поле» → открывается модалка.
4. **Не подтверждая**, переключить фильтр на `status = DONE` (например, через другое окно).
5. Вернуться в модалку, подтвердить bulk-edit.

**Ожидаемый результат**: операция применяется к **первоначальным 100 ERROR**,
а не к текущему состоянию фильтра.

---

## Scenario 5: Edge — параллельная правка

**Цель**: проверить обработку race condition.

**Setup**: 10 ERROR-процессов.

**Шаги**:
1. В вкладке A: bulk-edit status=WAITING, нажать подтвердить.
2. В вкладке B (между шагом 1 и завершением): single-edit одного процесса из
   этих 10 на status=WORKING.
3. Дождаться завершения bulk-edit в вкладке A.

**Ожидаемый результат**: в отчёте вкладки A — 9 успешно, 1 «конфликт версии».

---

## Scenario 6: Edge — пустая выборка

**Шаги**:
1. Фильтр: `status = NONEXISTENT_STATUS`.

**Ожидаемый результат**:
- UI: «Отобрано: 0».
- Bulk-кнопки `disabled`, tooltip: «нет процессов в выборке».

---

## Scenario 7: Каркас расширяемости — добавление новой bulk-операции

**Цель**: проверить, что новые bulk-операции добавляются без переделки.

**Шаги (для владельца, после реализации)**:
1. Придумать новую операцию (например, `bulk-retry`).
2. Backend:
   - Добавить метод `bulkRetryProcesses(ids, actor, db)` в `KaraokeProcessAdminService`.
   - Добавить `POST /api/admin/processes/bulk-retry` в `KaraokeProcessAdminController`.
   - Расширить CHECK constraint в audit: добавить `'bulk_retry'`.
3. Frontend:
   - Добавить `bulkRetryProcesses` action в `store.js`.
   - Создать `ProcessesBulkRetryModal.vue` (если нужны параметры).
   - Добавить кнопку в группу bulk-actions в `ProcessesTable.vue`.
4. Pre-commit + smoke-тест.

**Готово, если**: новая операция работает без изменений в существующих
bulk-кнопках / endpoint'ах / Vuex actions.

---

## Validation Checklist (pre-commit)

- [ ] `./gradlew :karaoke-app:bootJar --parallel` — успех
- [ ] `cd webvue3 && npm run build` — успех
- [ ] `cd webvue3 && npm run lint:check` — 0 errors
- [ ] `cd webvue3 && npx prettier --check "src/**/*.{vue,js,ts,json}"` — 0 errors
- [ ] `./gradlew ktlintCheck` — 0 new violations
- [ ] `bash tools/check-kdoc-coverage.sh --strict` — ≥ 50% (FR-006)
- [ ] `bash tools/check-jsdoc-coverage.sh --strict webvue3` — ≥ 50% (FR-006)
- [ ] `pre-commit run --all-files` — 0 errors
- [ ] Все 7 scenarios выше прошли

---

## Reference

- Spec: `specs/319-process-bulk-actions-v2/spec.md`
- Plan: `specs/319-process-bulk-actions-v2/plan.md`
- Data model: `specs/319-process-bulk-actions-v2/data-model.md`
- REST API contract: `specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md`
- Vuex contract: `specs/319-process-bulk-actions-v2/contracts/webvue3-bulk-store-actions.md`
- Migration: `deploy/karaoke-db/48_admin_process_bulk_actions.sql`
