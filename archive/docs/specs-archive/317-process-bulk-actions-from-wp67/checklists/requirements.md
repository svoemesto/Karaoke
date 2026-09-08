# Specification Quality Checklist: Массовые действия с процессами

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-08
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] **CHK001** No implementation details (languages, frameworks, APIs) — спека описывает WHAT/WHY; ссылки на `setWebvueProp`/`getWebvueProp`, `Vuex`, `<custom-confirm>`, `SongsTable.vue`, `MainController.kt`, `tbl_processes_audit` — в Assumptions как указатели на существующую инфраструктуру (Constitution II, V, VI), а не выбор стека.
- [x] **CHK002** Focused on user value and business needs — оба US (bulk change field + bulk delete) — про администратора и его workflow с большой выборкой процессов; решения про soft-delete, диапазон приоритетов, лимит 10000 — взяты из Constitution / spec #315 / требований владельца к админ-инструменту.
- [x] **CHK003** Written for non-technical stakeholders — формулировки на русском, без жаргона; admin-friendly статусы (NEW/QUEUED/...) явно out of scope — используем прямые enum-значения CREATING/WAITING/WORKING/DONE/ERROR в UI (прецедент ProcessEdit.vue:184-185, Р-2), технические термины (`tbl_processes_audit`, `WHERE id IN`, `ExceptionInInitializerError`) — в Assumptions и FR в виде ссылок на существующую инфраструктуру.
- [x] **CHK004** All mandatory sections completed — User Scenarios & Testing (US1 + US2 + 7 edge cases), Requirements (FR-001..FR-009 + 4 Key Entities), Success Criteria (SC-001..SC-008), Assumptions, Out of scope — все на месте.

## Requirement Completeness

- [x] **CHK005** No `[NEEDS CLARIFICATION]` markers remain — ни одного; все спорные места закрыты assumptions с явным указанием источника (soft-delete — миграция 47 / spec #315, диапазон приоритетов — N/A в v1 (Р-1: целое число без MIN/MAX, согласовано с ProcessEdit.vue `type="number"` без min/max) — разумный UI-верх, маппинг status — N/A в v1 (Р-2: enum передаётся как есть, без маппинга)).
- [x] **CHK006** Requirements are testable and unambiguous — FR-001..FR-009 имеют явные MUST и observable outcome (UI-кнопки заблокированы, диалог с тремя полями, HTTP POST/DELETE endpoints, audit-trail, soft-delete, skipped для head-процессов с детьми, валидация).
- [x] **CHK007** Success criteria are measurable — SC-001/SC-002 (100% показ диалога), SC-003 (сумма updated+skipped == len(ids)), SC-004 (audit-trail COUNT == updated), SC-005 (soft-delete verified через psql), SC-006 (head с детьми в skipped), SC-007 (валидация), SC-008 (≤ 2 сек для 1000 процессов).
- [x] **CHK008** Success criteria are technology-agnostic — SC проверяемы через UI/audit-trail/psql, без привязки к конкретному фреймворку (где упоминается psql — это инструмент владельца для верификации, не стек реализации).
- [x] **CHK009** All acceptance scenarios are defined — US1: 6 acceptance scenarios (открытие диалога, status/priority/threadId, валидация, пустая выборка); US2: 5 acceptance scenarios (открытие диалога, soft-delete, обновление таблицы, пустая выборка, отмена).
- [x] **CHK010** Edge cases are identified — 7 edge cases: тысячи процессов, идемпотентность (processDeletedAt IS NOT NULL), head-процессы с дочерними, 5xx mid-UPDATE, двойной клик, применяются transition-правила (FR-017 315): недопустимый → skipped (Р-3), невалидный priority.
- [x] **CHK011** Scope is clearly bounded — секция «Out of scope» явно перечисляет: hard-delete, bulk других полей (chainId/name/start/end), bulk-retry, bulk-restore, undo, workflow-валидация, батчи > 10000, per-user authorization.
- [x] **CHK012** Dependencies and assumptions identified — 12 assumptions, включая single-user, UI placement (прецедент SongsTable.vue), soft-delete (миграция 47), маппинг status (spec #315), placement в karaoke-app (Constitution V WORKING_DATABASE gotcha), лимит 10000, audit-trail (Constitution III + spec #315 FR-007), governance boundaries (владелец делает runtime-проверки), 5-step verification канон brief.md:59-63, git workflow NON-NEGOTIABLE.

## Feature Readiness

- [x] **CHK013** All functional requirements have clear acceptance criteria — FR ↔ SC мапятся явно: FR-001→SC-001/SC-002, FR-002→SC-001, FR-003→SC-003/SC-004, FR-004→SC-002/SC-005, FR-005→SC-003/SC-004, FR-006→SC-003, FR-007→SC-006, FR-008→SC-007, FR-009→SC-007.
- [x] **CHK014** User scenarios cover primary flows — US1 (bulk change field, P1) и US2 (bulk delete, P1) — оба обязательных из WP #67. Покрывают весь flow: кнопка → диалог → подтверждение → backend → обновление таблицы → сообщение.
- [x] **CHK015** Feature meets measurable outcomes defined in Success Criteria — все 8 SC проверяемы: SC-001/SC-002 через UI-инспекцию, SC-003 через math равенство, SC-004/SC-005 через psql, SC-006 на тестовой цепочке, SC-007 через отсутствие side-effects, SC-008 через benchmark.
- [x] **CHK016** No implementation details leak into specification — спека не предписывает конкретную технологию/БД/язык; только user-observable поведение и ссылки на существующую инфраструктуру (Vuex, `<custom-confirm>`, `tbl_processes_audit` — это всё уже существующие абстракции проекта, не выбор стека).

## Notes

- Все 16 проверок PASS → спека готова к ревью Кириллом (Stage 2).
- Lessons applied (из iter #1..#4 Karaoke-спек и уроков Boss AGENTS.md):
  - **Урок #309 / Boss lesson**: ничего не выдумывал сверх WP #67. Только то, что прямо запрошено (bulk change field + bulk delete). Никаких лишних bulk-операций в v1.
  - **Урок iter #3 #11 (Boss AGENTS.md)**: разделение «agent делает» / «owner делает» зафиксировано явно в Assumptions (audit, schema lookup — агент; smoke-test, visual verify — владелец). Никаких требований к агенту, нарушающих governance.
  - **Урок #15 (MVP-checkpoint)**: задача средняя (UI + backend + audit + soft-delete с пропуском head-процессов), MVP-checkpoint после US1 (bulk change field) перед US2 (bulk delete) — на усмотрение владельца в ревью. Если владелец скажет «MVP-checkpoint нужен» — будет в plan.md.
  - **Прецедент spec #316**: UI placement — в существующих `<custom-confirm>` (принцип «сделай так же, как рядом»), не отдельные модалки. Прецедент spec #315: маппинг admin-friendly ↔ enum, placement в karaoke-app (не karaoke-web), soft-delete через `process_deleted_at`.
- **Clarifications, на которые стоит обратить внимание Кириллу при ревью (Stage 2)**:
  - **Q1 (scope)**: правильно ли ограничили v1 тремя полями (priority, status, threadId)? Или владелец хочет больше (например, `chainId` для reparenting)?
  - **Q2 (governance)**: правильно ли, что bulk-delete НЕ удаляет head-процессы с дочерними (только пропускает)? Или владелец хочет каскадное удаление / запрет на удаление child без parent?
  - **Q3 (limit)**: разумный ли лимит 10000? Или жёстче (например, 1000 — текущий `perPage` в `ProcessesTable`)?
- **Plan-level pre-conditions (Stage 5 ревью Кирилла)**:
  - N/A (Р-1): `MIN_PRIORITY` / `MAX_PRIORITY` НЕ существуют в `Constants.kt`; используем целое без диапазона.
  - Confirm `tbl_processes_audit` schema uses jsonb for old_value/new_value, action enum (Р-4: фактическая схема — `process_id, actor, action, old_value::jsonb, new_value::jsonb, created_at`).
  - Confirm `ProcessesTable.vue` has computed `countRows` (Р-6: сейчас grep=0, нужно добавить по прецеденту `SongsTable.vue:410/:421`).