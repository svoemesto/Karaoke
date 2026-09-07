# Specification Quality Checklist: Admin UI для KaraokeProcess (iteration #5 — «эталон»)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-07
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — указаны Kotlin/Vue 3 как NON-NEGOTIABLE по Karaoke constitution
- [x] Focused on user value and business needs — 5 user stories + edge cases
- [x] Written for non-technical stakeholders — User Scenarios на русском
- [x] All mandatory sections completed (User Scenarios, Requirements, Key Entities, Success Criteria, Assumptions)

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous — 28 FR, каждое с acceptance scenario
- [x] Success criteria are measurable — 9 SC с конкретными метриками
- [x] Success criteria are technology-agnostic
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified — 6 edge cases
- [x] Scope is clearly bounded — admin bundle only
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows — P1=view, P2=edit/delete, P3=retry/audit
- [x] Feature meets measurable outcomes
- [x] No implementation details leak into specification

## Lessons Coverage Check (16 уроков iter #1-#4 + 1 process lesson = 17 total)

- [x] Lesson #1 (convention match) → FR-009
- [x] Lesson #2 (terminology) → FR-001
- [x] Lesson #3 (placement) → FR-020, FR-021
- [x] Lesson #4 (DI @DependsOn) → FR-022
- [x] Lesson #5 (race protection) → FR-019
- [x] Lesson #6 (owner clarifications) → FR-013, FR-017, FR-018
- [x] Lesson #7 (5-step verification) → FR-024 канон brief.md:59-63
- [x] Lesson #8 (5-step subset) → FR-024 note
- [x] Lesson #9 (single entry point) → FR-007 (с явным «УДАЛИТЬ» @click.left)
- [x] Lesson #10 (parent-child viz populated) → FR-004, FR-005 + миграция 48
- [x] Lesson #11 (runtime smoke-test только владелец) → SC-008 + Workflow
- [x] Lesson #12 (governance проверяется) → Workflow
- [x] Lesson #13 (frontend = владелец) → Workflow
- [x] Lesson #14 (governance приоритет) → Workflow
- [x] Lesson #15 (MVP-checkpoint обязателен) → SC-007 + Workflow
- [x] Lesson #16 (boss self-verify через psql) → Assumptions + notes/real-schema-tbl_processes.txt (зафиксировано ДО старта iter #5)
- [x] Lesson #17 (владелец сказал «не дёргать» — исключение, не норма) → Assumptions явно

## Real Schema Verification

- [x] `notes/real-schema-tbl_processes.txt` зафиксирована ДО старта (Lesson #16)
- [x] Реальные имена колонок: `last_update` (НЕ `updated_at`), `process_start`/`process_end` (НЕ `started_at`/`ended_at`)
- [x] FR-001 ссылается на реальные 20 отображаемых колонок из 28
- [x] actor VARCHAR(64) (НЕ 255)
- [x] БД откачена (миграция 48 не применена, 0 chains — владелец решает применять или нет)

## Workflow Compliance (iter #5)

- [x] MVP-checkpoint прописан в Workflow + SC-007 (Lesson #15, восстановлено после исключения iter #4)
- [x] Boss self-verify через реальную схему БД ДО отправки задач Алине (Lesson #16)
- [x] Агенты НЕ перезапускают контейнер — governance Karaoke/AGENTS.md, Lessons #12-14
- [x] Smoke-test реальной БД только владелец (Lesson #11, SC-008)
- [x] 0 коммитов (FR-028)
- [x] 5-step verification канон brief.md:59-63 (FR-024, Lesson #7)

## Что «эталон» iter #5 берёт из iter #1-#4

- Из iter #1: 8 уроков (convention, terminology, placement, DI, race protection, owner clarifications, 5-step, subset).
- Из iter #2: 2 урока (single entry point + parent-child viz).
- Из iter #3: 5 уроков (runtime smoke-test только владелец, governance проверяется, frontend = владелец, governance приоритет, MVP-checkpoint).
- Из iter #4: 1 process урок (boss self-verify через psql).

**Итого**: 16 lessons + 1 process lesson = 17 уроков покрыты.

## Spec-level Fixes (фаза 2 ревью Кирилла, REQUEST CHANGES, 2 пункта)

- ✅ **Р-1**: Assumption про миграции (стр. 256) — было «Миграции 47 + 48 применены на local», противоречило Context:77 (48 — владелец решает) и реальности (БД откачена). Заменено: «Миграция 47 применена (в master, commit 7ad6313c); миграция 48 НЕ применена (БД откачена, 0 chains — владелец решает). Спека НЕ зависит от 48: FR-005 обрабатывает legacy process_chain_id=null; применение 48 включает parent-child viz на legacy данных».
- ✅ **Р-2**: FR-024 5-step verification канон — был не 1:1 канон `brief.md:59-63` (опущен karaoke-public в шагах 2/4/5). Заменено: 5 шагов дословно по канону + строка «karaoke-public шаги 2/4/5 — no-op, пока karaoke-public не менялся».
- ✅ **Р-2а (residual)**: FR-024 шаг 4 для karaoke-public — потерян `format:check`. Исправлено: «(+ `cd karaoke-public && npm run build && npm run format:check` если менялся)».

## Notes

- **Spec готов к фазе plan** — все 16 lessons + 1 process lesson покрыты FR-ами и Workflow.
- **MVP-checkpoint восстановлен** — после исключения владельца в iter #4.
- **T052 [OPT] orphans endpoint** — опциональный backlog (FR-006 dead code full scope), решает Lesson #10 dead code в полном объёме. Владелец решает включать в iter #5 или оставить backlog.
- **Миграция 48 остаётся** в `deploy/karaoke-db/`, но **БД откачена** (0 chains). Владелец решает применять или нет.
- **Iter #1-#4 артефакты** в Boss для reference.
- Items marked incomplete require spec updates before `/speckit.clarify` or `/speckit.plan`.
