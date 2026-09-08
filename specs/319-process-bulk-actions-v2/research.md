# Research: Массовые действия с процессами (v2)

**Created**: 2026-09-08 | **Spec**: `specs/319-process-bulk-actions-v2/spec.md`

Phase 0 исследования. Каждый раздел — Decision / Rationale / Alternatives.

---

## R-1: SyncRegistry для tbl_processes

**Decision**: tbl_processes НЕ участвует в sync; sync-миграция не требуется.

**Rationale**: Прямой grep `SyncRegistry.all` (`karaoke-app/.../sync/SyncTarget.kt:503-523`)
содержит 18 sync-сущностей (Song, Pictures, Authors, Albums, SongCoAuthors,
Dictionaries, News, SiteUsers, SitePlaylists, SitePlaylistItems,
ListeningHistory, SongAssignments, SongAssignmentDrafts, ShareLinks, Events,
PriceTariffs, Subscriptions, SiteChatMessages). `KaraokeProcess` / `ProcessSyncTarget`
отсутствуют. Constitution III в части sync-регистра к этой фиче неприменима.

**Alternatives considered**:
- A: Добавить `KaraokeProcessSyncTarget` в `SyncRegistry.all` (синхронизировать процессы на прод).
  Отклонено: процессы — runtime-объекты admin-машины, у них нет смысла на проде
  (там другой стек: karaoke-web + публичный сайт).
- B: Снять процесс с sync при hard-delete (изначально предполагалось в A-001).
  Отклонено: нечего снимать — процесс там и не был.

## R-2: Существующая схема покрывает требования

**Decision**: Новая SQL-миграция НЕ требуется.

**Rationale**: Миграция `47_admin_process_audit.sql` создаёт:
- `tbl_processes.process_deleted_at TIMESTAMP NULL` (line 19)
- `tbl_processes.process_chain_id BIGINT NULL` (line 18)
- `tbl_processes_audit` с FK CASCADE на `tbl_processes(id)` (line 33-41)
- recordhash-триггер обновлён под новые колонки (line 51-76)
- BACKFILL recordhash для существующих строк (line 79-98)

Bulk-edit и bulk-delete используют существующие колонки + существующий audit-trail.
FK CASCADE на audit (line 35) — это и есть «без следов в БД» для hard-delete.

**Alternatives considered**:
- A: Добавить колонку `process_bulk_batch_id` в audit для группировки.
  Отклонено: `batch_id` уже хранится в `old_value`/`new_value` (jsonb), отдельная
  колонка не нужна. Альтернативно — добавить колонку `batch_id UUID NULL`
  в `tbl_processes_audit`, если потребуется индекс по batch (Stage 4 task).
- B: Изменить FK CASCADE → SET NULL для сохранения audit после hard-delete.
  Отклонено: владелец явно выбрал «без следов» (Clarifications Q1).
  Если потребуется — отдельная миграция в будущем.

## R-3: Паттерн bulk в SongsTable

**Decision**: Использовать паттерн SongsTable (custom-confirm + dispatch в Vuex-store
→ backend-endpoint), с минимальными адаптациями.

**Rationale**: SongsTable уже реализует аналогичный UX:
- `<custom-confirm>` для подтверждений (SmartCopyModal — прецедент).
- Vuex `dispatch` с promise-based `promisedXMLHttpRequest` (см. `store.js:309`).
- Один backend endpoint на каждое массовое действие.

Кодовая база: 5+ bulk-операций на songs уже работают в проде (spec 273/274/etc).
Это проверенный паттерн, нет причин изобретать новый.

**Alternatives considered**:
- A: GraphQL/SSE для прогресса.
  Отклонено: overkill, существующий REST+admin-task паттерн уже есть.
- B: WebSocket.
  Отклонено: не введено в проекте, лишний движущийся кусок.

## R-4: Async endpoint или sync?

**Decision**: Sync для ≤ 1000, async для > 1000 (через admin-task endpoint).

**Rationale**:
- SC-001/SC-002 (60 секунд на 1000) — выполнимо на sync endpoint.
- > 1000 — UI timeout, риск разрыва соединения, нет прогресса.
- Admin-task паттерн уже есть в проекте (search-timeout-configurable, spec 316 —
  прецедент конфигурируемого timeout). Используем его для polling.

**Alternatives considered**:
- A: Всегда async.
  Отклонено: для 5-10 процессов лишний round-trip + сложность UI.
- B: Batch'ами внутри sync (например, по 100).
  Отклонено: для 1000 это 10 round-trip'ов в одном HTTP — норм, но 60s target
  уже на пределе; > 1000 — без async не обойтись.

## R-5: Прерывание runtime-потоков при bulk-delete

**Decision**: Использовать существующий `KaraokeProcessWorker.threadsMap.findThread(id)`
для WORKING/WAITING/CREATING — повторяет single-record паттерн.

**Rationale**: `KaraokeProcessAdminService.deleteProcess` (single, line 330-354) уже
делает:
- WORKING: `thread.interrupt()` + 5 сек grace + `destroyForcibly()`
- WAITING/CREATING: `thread.interrupt()` (no grace)
- DONE/ERROR: ничего

Это протестировано на single-record. Дублируем логику для каждого id батча.

**Alternatives considered**:
- A: Пропускать runtime-потоки, надеясь что они сами завершатся.
  Отклонено: WORKING-поток держит OS-процесс, который может писать в БД уже
  удалённую строку → race + FK violations.
- B: Ждать завершения всех потоков перед DELETE.
  Отклонено: timeout (5 сек × N процессов). Лучше interrupt + grace + forcibly.

## R-6: Bulk-edit field validation

**Decision**: Backend принимает любое поле из `editableColumns` whitelist;
frontend (v1) ограничивает select до `{priority, status, threadId}` (FR-009, Clarifications Q3).

**Rationale**: Whitelist в backend — обязательная защита (нельзя редактировать
`process_chain_id` или `process_deleted_at` пачкой). Frontend — для UX,
чтобы админ не сломал цепочку редактированием служебных полей.
Расширение UI не требует backend-изменений — добавляешь select-option.

**Alternatives considered**:
- A: UI показывает все editable поля.
  Отклонено: пользовательский UX (см. Clarifications Q3, owner choice).
- B: Backend тоже ограничивает до {priority, status, threadId}.
  Отклонено: убирает расширяемость.

## R-7: Каркас расширяемости (FR-009)

**Decision**: Шаблон расширения задокументирован в `quickstart.md`:
- Backend: новый метод `bulkXxxProcesses` + endpoint.
- Frontend: новый `<Xxx>Modal.vue` + Vuex action + кнопка.

**Rationale**: Конкретные будущие bulk-операции (reassign-chain, retry, restore)
не реализуются в этой спеке (Out of scope), но архитектура должна их вместить
без переделки.

**Alternatives considered**:
- A: Generic `bulkAction(action, params)` endpoint.
  Отклонено: type erasure в JSON, валидация сложнее, читаемость ниже.
  Явные endpoint'ы проще для отладки.
- B: Plugin-style регистрация bulk-операций.
  Отклонено: overkill для 2-5 операций.

## R-8: Audit action strings — расширение CHECK constraint

**Decision**: `tbl_processes_audit.action` имеет CHECK constraint
`CHECK (action IN ('EDIT', 'RETRY', 'DELETE'))` (line 37 миграции 47).
Добавить `'bulk_update_field'` и `'bulk_delete'` через миграцию.

**Rationale**: Без расширения CHECK constraint INSERT с новым action упадёт.

**Alternatives considered**:
- A: Убрать CHECK совсем.
  Отклонено: опасно (можно записать любое значение, баги в audit).
- B: Расширить CHECK через миграцию `48_admin_process_bulk_actions.sql`.
  Принято (D-2 было про «миграция не нужна» — но это была про `tbl_processes`,
  не про `tbl_processes_audit`. Audit-Check требует отдельной миграции).
  Уточнение в D-2: миграция `tbl_processes_audit` нужна для расширения CHECK.

## R-9: Snapshot семантика для bulk-выборки

**Decision**: Frontend при открытии bulk-операции делает snapshot всех id,
удовлетворяющих текущему фильтру (через дополнительный endpoint
`GET /api/admin/processes?idsOnly=true&<filters>`).

**Rationale**: Текущий `GET /api/admin/processes` возвращает первые 1000 (limit).
Если фильтр даёт 5000 — bulk-edit/edit/delete получат только первые 1000.
Нужен отдельный запрос «только id, без limit» для snapshot.

**Alternatives considered**:
- A: Повысить limit до 10 000.
  Отклонено: payload раздувается (там целые DTO), нет смысла тащить всё,
  если нужны только id.
- B: Передать filter в bulk endpoint и выполнить WHERE на backend.
  Принято как дополнительный механизм: основной — snapshot через отдельный
  endpoint, fallback — передача фильтра в bulk endpoint для очень больших выборок
  (если snapshot возвращает > 10000, переключаемся на server-side filter).

## R-10: KDoc/JSDoc покрытие

**Decision**: Все новые публичные API — с KDoc/JSDoc, включая `@see specs/319-process-bulk-actions-v2/spec.md`.

**Rationale**: Constitution VI (FR-006) NON-NEGOTIABLE; CI падает на `missing description`.

**Alternatives considered**: нет (обязательно).

## Summary of NEEDS CLARIFICATION resolved

Все вопросы из Stage 2 закрыты:
- Q1 (soft/hard delete) → hard-delete (A-001)
- Q2 (confirm-word) → только confirm-dialog (FR-008)
- Q3 (chainId scope) → не в v1 (A-002)

Новых NEEDS CLARIFICATION в Phase 0 не возникло.

## References

- Spec: `specs/319-process-bulk-actions-v2/spec.md`
- Plan: `specs/319-process-bulk-actions-v2/plan.md`
- Constitution: `.specify/memory/constitution.md`
- Existing controller: `karaoke-app/.../controllers/KaraokeProcessAdminController.kt`
- Existing service: `karaoke-app/.../services/KaraokeProcessAdminService.kt`
- Migration (audit): `deploy/karaoke-db/47_admin_process_audit.sql`
- Sync registry: `karaoke-app/.../sync/SyncTarget.kt:503-523`
- SongsTable bulk pattern: `webvue3/src/components/Songs/SongsTable.vue`
- SmartCopyModal (custom-confirm): `webvue3/src/components/Common/SmartCopy/SmartCopyModal.vue`
