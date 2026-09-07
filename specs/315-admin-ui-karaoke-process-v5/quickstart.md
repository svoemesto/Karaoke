# Phase 1 Quickstart: Admin UI для KaraokeProcess (iteration #5 — «эталон»)

**Branch**: `315-admin-ui-karaoke-process-v5`
**Date**: 2026-09-07
**Spec**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)

## Цель

Runnable validation scenarios — как доказать, что фича работает end-to-end. Не implementation code (это в `tasks.md` и в коде).

## Prerequisites

1. **Миграция 47 применена** на local + prod:
   ```bash
   psql -h <host> -U <user> -d karaoke -c '\d tbl_processes' | grep -E 'process_chain_id|process_deleted_at'
   # Должны быть 2 строки
   ```

2. **karaoke-app пересобран** с новым admin controller:
   ```bash
   ./gradlew :karaoke-app:bootJar
   ```

3. **webvue3 пересобран**:
   ```bash
   cd webvue3 && npm run build
   ```

4. **Container restart** (только по согласию владельца, NON-NEGOTIABLE per Karaoke AGENTS.md).

## Validation Scenarios

### Scenario 1: Просмотр процессов (US1, P1)

**Шаги**:
1. Открыть `/admin/processes` в браузере.
2. Убедиться что top-level список загружается (только head, `chainId IS NULL`).
3. Применить фильтр `status=DONE` → таблица фильтруется.
4. Применить фильтр `name=test` (substring `%x%`) → находит процессы с "test" в name.
5. Кликнуть expand на head-процессе с tail-детьми → дети подгружаются (lazy load, ≤1 сек).
6. Применить фильтр «включая удалённые» → видны soft-deleted процессы.

**Expected**:
- Загрузка top-level ≤2 сек (SC-001) для 18k+ процессов.
- Expand конкретного head ≤1 сек (SC-001).
- Все фильтры работают в соответствии с FR-002.

### Scenario 2: Редактирование процесса (US2, P2)

**Шаги**:
1. На `/admin/processes` выбрать процесс с id=12345.
2. Нажать кнопку «Редактировать» в Actions-колонке → открывается `ProcessEditModal` (FR-009 convention).
3. Проверить, что НЕ работает клик на ячейку `name` для редактирования (Lesson #9 iter #2).
4. Изменить name → «Demucs-v2».
5. Нажать «Сохранить».
6. Закрыть модалку → таблица обновляется.
7. Проверить audit: открыть audit-модалку → видна запись EDIT с diff old/new name (FR-016).

**Expected**:
- Edit-modal открывается ТОЛЬКО через кнопку (FR-007, single entry point).
- Изменение сохранено в БД.
- Audit-запись создана (SC-003: 100% операций → audit).

### Scenario 3: Попытка недопустимого status transition (US2, AS-2)

**Шаги**:
1. На `/admin/processes` выбрать процесс в статусе WAITING.
2. Нажать «Редактировать».
3. Изменить status на DONE (минуя WORKING) → попытка сохранения.
4. **Expected**: ошибка UI «Недопустимый переход: WAITING → DONE (must go through WORKING)» (FR-017).
5. Модалка остаётся открытой, изменения не сохранены.

### Scenario 4: Удаление WAITING-процесса (US3, AS-1)

**Шаги**:
1. Создать процесс с заведомо простой командой (например `sleep 60`) в статусе WAITING.
2. Нажать «Удалить».
3. Подтвердить удаление.
4. **Expected**:
   - Процесс отзывается из механизма исполнения (`KaraokeProcessWorker.threadsMap`; pickup-логика фильтрует `process_deleted_at IS NULL` — R-019), FR-011.
   - `process_deleted_at = NOW()` в БД.
   - Audit-запись DELETE создана (FR-016).
   - Процесс не отображается в top-level (по умолчанию `includeDeleted=false`).

### Scenario 5: Удаление WORKING-процесса с timeout (Edge case #2)

**Шаги**:
1. Запустить долгий процесс (например `sleep 120`).
2. Дождаться WORKING.
3. Нажать «Удалить».
4. **Expected**:
   - Worker thread получает `Thread.interrupt()`.
   - 5 секунд grace period (R-008, FR-012).
   - `destroyForcibly()` если не успел.
   - `process_deleted_at = NOW()`.
   - Audit-запись: `action='DELETE', old_value.status='WORKING', old_value.timeout=true`.

### Scenario 6: Retry ERROR-процесса (US4)

**Шаги**:
1. Создать процесс с заведомо ошибочной командой → дождаться ERROR.
2. Нажать «Retry».
3. **Expected**:
   - Status → WAITING (FR-014).
   - Audit-запись RETRY (FR-015).
   - Процесс попадает в очередь и запускается снова.

### Scenario 7: Retry disabled для удалённого (US4 AS-2)

**Шаги**:
1. Создать процесс с заведомо ошибочной командой → дождаться ERROR.
2. Удалить процесс (soft-delete через US3-delete).
3. Открыть таблицу, проверить кнопку «Retry».
4. **Expected**: кнопка «Retry» disabled или скрыта (FR-014 условие `process_deleted_at IS NULL`, T048 iter #3).

### Scenario 8: Просмотр audit log (US5)

**Шаги**:
1. Открыть процесс с историей изменений.
2. Нажать «Audit» → открывается audit-модалка.
3. **Expected**:
   - Список записей в обратном хронологическом порядке.
   - Каждая запись: timestamp, actor, action (EDIT/RETRY/DELETE), diff JSONB.
4. Запись старше 30 дней — НЕ видна (FR-018 retention).

### Scenario 9: Parent-child визуализация (US1, FR-004)

**Шаги**:
1. Открыть `/admin/processes`.
2. Убедиться что отображаются только head-процессы.
3. Кликнуть expand на head → видны tail-дети с `process_chain_id` = id head.
4. **Expected**:
   - Legacy tail-процессы (`process_chain_id=null`) отображаются как самостоятельные head-процессы БЕЗ меток «legacy» (R-005, FR-005).
   - Broken references (`process_chain_id` указывает на несуществующий parent) → UI показывает «⚠️ parent не найден» (FR-006), не падает.

### Scenario 10: Cascade delete (FR-013, US3 AS-4)

**Шаги**:
1. Создать parent-процесс и 2 tail-процесса с правильными chainId.
2. Удалить parent.
3. **Expected**: tail-дети НЕ удаляются (cascade OFF, FR-013, owner clarification iter #1).

## MVP-Checkpoint (R-016, SC-007, Lesson #15 — NON-NEGOTIABLE для iter #5)

После Phase 8 US1 задачи (задачи в `tasks.md`) → **STOP → владелец**:

1. **Rebuild + перезапуск контейнера** (karaoke-app + webvue3, по согласию владельца).
2. **Smoke-test 6 endpoints через curl** (governance: агенты НЕ делают runtime-проверки; SC-008):
   ```bash
   curl -H "X-Admin-Username: admin:test" "http://localhost:8898/api/admin/processes?topLevelOnly=true&limit=1"
   curl -H "X-Admin-Username: admin:test" "http://localhost:8898/api/admin/processes/{id}"
   curl -X POST -H "X-Admin-Username: admin:test" -H "Content-Type: application/json" -d '{"name":"test"}' "http://localhost:8898/api/admin/processes/{id}/edit"
   curl -X POST -H "X-Admin-Username: admin:test" "http://localhost:8898/api/admin/processes/{id}/delete"
   curl -X POST -H "X-Admin-Username: admin:test" "http://localhost:8898/api/admin/processes/{id}/retry"
   curl -H "X-Admin-Username: admin:test" "http://localhost:8898/api/admin/processes/{id}/audit?days=30"
   ```
3. **Visual verify Scenario 1, 9** (US1 scope: top-level + parent-child viz).
4. **Если ОК** → Алина продолжает US2..US5 + Polish. **Если REJECT** → откат к baseline Phase 2, фикс, повтор.
5. **Boss diff** (после каждой фазы): `git add -N . && git diff > /home/nsa/Agents/Boss/reviews/315-admin-ui-karaoke-process-v5-implement.diff` + sha256 → владельцу.

Без MVP-checkpoint → batch mode хрупкий (iter #2+#3 накопили 6 regressions подряд, урок #15).

## 5-step Verification (NON-NEGOTIABLE)

После каждого изменения кода (канон `brief.md:59-63`, FR-024):

```bash
# 1. Backend compile
./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel

# 2. Линтеры
./gradlew :karaoke-web:ktlintCheck
cd webvue3 && npm run lint && cd ..
cd karaoke-public && npm run lint && cd ..

# 3. Backend bootJar
./gradlew :karaoke-web:bootJar --parallel
# На nsa-i9 под nsa: также :karaoke-app:bootJar

# 4. Frontend Vite
cd webvue3 && npm run build && npm run format:check && cd ..
cd karaoke-public && npm run build && npm run format:check && cd ..

# 5. Docker-образы
cd deploy && bash do.sh build_webvue3
# Если менялся karaoke-public: bash do.sh build_public
```

На nsa-i9 под nsa для правок ТОЛЬКО в `karaoke-app`/`karaoke-web` допустим 3-step subset (compileKotlin + ktlintCheck + bootJar) — см. FR-024.

## Git workflow (NON-NEGOTIABLE)

- Вся работа в working tree до самого финала (modified/untracked).
- `git add / git status / git diff` — для локальной проверки (можно).
- `compile / ktlint / build / test` — для проверки (можно).
- `add-comment` в OpenProject WP #64 — после каждой фазы (можно).
- Финальный commit + push + merge — **ТОЛЬКО по прямому указанию владельца**.

## Visual verify владельцем

Перед `mark-review` владелец лично проверяет:
- Открывает `/admin/processes` → видит процессы.
- Открывает edit-modal → редактирует → сохраняет → видит обновление.
- Открывает audit-модалку → видит запись.
- Удаляет WAITING-процесс → видит, что пропал.
- Retry ERROR-процесса → видит WAITING.

Если владелец говорит REJECT (как в iter #2) — записываем в WP #64 + правим + повторяем фазы.

— Илья (boss)
