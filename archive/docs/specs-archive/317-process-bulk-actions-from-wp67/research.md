# Research: Массовые действия с процессами (iter #317)

**Branch**: `317-process-bulk-actions` | **Date**: 2026-09-08 | **Spec**: [spec.md](./spec.md)

**Source**: Stage 2 ревью Кирилла (3 цикла → APPROVE, 9 правок Р-1..Р-8 + бонус Assumption) + плановое обследование code-фактов.

## NEEDS CLARIFICATION — все закрыты в Stage 2

Все спорные вопросы закрыты Кириллом в spec.md rev 3.1:

| # | Вопрос | Решение (Кирилл default / владелец default) | Reference |
|---|---|---|---|
| 1 | priority диапазон (MIN/MAX) | Убрать в v1 — целое без диапазона (прецедент ProcessEdit.vue) | spec.md:374-378 (Р-1) |
| 2 | admin-friendly маппинг NEW→CREATING | Убрать — прямые enum-значения (прецедент ProcessEdit.vue:184-185) | spec.md:368-374 (Р-2) |
| 3 | bulk обход transition-валидации | Применять те же правила (FR-017 spec #315), skipped+reason | spec.md:177-181 (Р-3) |
| 4 | audit-схема | Фактические колонки jsonb (`process_id, actor, action, old_value::jsonb, new_value::jsonb, created_at`) | spec.md:388-399 (Р-4) |
| 5 | placement | Существующий `KaraokeProcessAdminController.kt` (auth=permitAll) | spec.md:232-249 (Р-5) |
| 6 | DELETE с телом | POST вместо DELETE (прецедент webvue3) | spec.md:228-231 (Р-6) |

**Дополнительных NEEDS CLARIFICATION нет.** Plan + design self-contained.

## Зависимости

**Reuse (не новые зависимости)**:
- `KaraokeProcessAdminController.kt` (existing) — добавить 2 endpoints.
- `KaraokeProcessAdminService.kt` (existing) — добавить 2 метода + reuse INSERT :643 (audit), фильтр :155 (process_deleted_at IS NULL), guard :249 (idempotency).
- `KaraokeProcess.kt` (existing) — без изменений схемы; использовать `coerceValue` pattern (line :520).
- `KaraokeProcessStatuses` enum (existing, 5 значений CREATING/WAITING/WORKING/DONE/ERROR).
- `tbl_processes_audit` (existing schema, миграция 47 spec #315) — INSERT pattern :643.
- `webvue3/src/components/Processes/ProcessesTable.vue` (existing) — добавить computed `countRows` + 2 кнопки + 2 dialog-handlers.
- `webvue3/src/components/Processes/store.js` (existing) — добавить actions `processesBulkUpdate` / `processesBulkDelete`.
- `<custom-confirm>` (`webvue3/src/components/Common/CustomConfirm.vue`) — переиспользуем для обоих диалогов (spec 316 FR-007).

## Best practices

**Backend (Kotlin/Spring)**:
- **Single endpoint per action** — `POST /bulk-update` и `POST /bulk-delete` (не один общий endpoint с action discriminator).
- **Reuse `WHERE id IN (..)` pattern** — Constitution II (O(n), не цикл).
- **Reuse INSERT :643 audit pattern** — не дублировать INSERT-логику.
- **Reuse transition-валидацию FR-017** — вызвать существующий метод `validateTransition (AdminService:495)(...)` из `AdminService`.
- **Idempotency guard** — `process_deleted_at IS NULL` filter + atomic UPDATE.
- **KDoc с `@see`** — ссылка на spec.md (Constitution VI).

**Frontend (Vue 3)**:
- **`<custom-confirm>` reuse** — не отдельный модал (прецедент spec 316).
- **Symmetric с SongsTable** — `computed countRows` + `:disabled="countRows === 0"` (lines :410/:421).
- **Vuex action pattern** — `commit('SET_xxx')` после успешного ответа (не прямой мутации state).
- **JSDoc** для новых actions (Constitution VI).

**Audit-trail (FR-005 spec)**:
- **JSONB для old_value/new_value** — структура `{field_name: old_value}` / `{field_name: new_value}`.
- **action values**: `'bulk-update'` / `'bulk-delete'` (новые enum values).
- **actor** — из request header `X-Admin-Username` (spec 315 pattern, `AdminService:155`-area).

## Patterns

- **Backend bulk-update**: `WHERE id IN (?, ?, …, ?)` + `process_deleted_at IS NULL` + audit INSERT per affected row.
- **Backend bulk-delete**: same WHERE + soft-delete via `process_deleted_at = NOW()` + children-check для head-процессов.
- **Frontend bulk action**: открыть `<custom-confirm>` → dispatch Vuex action → backend response → обновить локальный список через `setItems` mutation.

## Risks

| Risk | Mitigation |
|---|---|
| Race condition: bulk-delete vs concurrent edit | `WHERE id IN (..) AND process_deleted_at IS NULL` — atomic, не блокирует concurrent reads |
| Transition-validation может падать на N процессов | Catch `InvalidStatusTransitionException` per-row, добавить в `skipped` массив с reason |
| Audit INSERT batch slow for 1000+ rows | Single INSERT with multi-VALUES (`INSERT INTO ... VALUES (?, ?, ...), (?, ?, ...)`) — O(1) roundtrips |
| UI re-render slow for 1000+ updates | Vuex `setItems` mutation — `Object.freeze` или immutable update pattern (existing pattern) |

## Готово к Phase 1

- [x] Все NEEDS CLARIFICATION закрыты (Stage 2 ревью Кирилла).
- [x] Зависимости mapped (reuse, не new).
- [x] Best practices определены (backend + frontend).
- [x] Risks identified + mitigation.

**Следующий шаг**: Phase 1 — design artifacts (data-model.md, contracts/, quickstart.md).
