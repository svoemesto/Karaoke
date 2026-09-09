# Spec Quality Checklist: [OpenProject Tracker MUST-link gate (Pass 349)]

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-09
**Feature**: [349-tracker-must-link](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — основная implementation-деталь — это `tools/check-spec-issue-link.py` (Python stdlib), которая явно описана как REQUIRED tool.
- [x] Focused on user value and business needs — value = governance compliance, не пропуск workflow как в #69.
- [x] Written for non-technical stakeholders — описывает workflow с командами bash, без Kotlin деталей.
- [x] All mandatory sections completed — Knowledge References + OpenProject Tracking (в рамках этой спеки) + User Scenarios + Requirements + SC + Assumptions.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers — Issue ID для этой спеки = none (governance amendment, не работа по task).
- [x] Requirements are testable — FR-001..FR-007 — каждое проверяемо через запуск `tools/check-spec-issue-link.py` или grep по AGENTS.md.
- [x] Success criteria are measurable — SC-001: exit 0 на modern-spec, SC-002: 4 строки в таблице, SC-004: 9/9 CI PASS.
- [x] Success criteria are technology-agnostic — нет фреймворков, только bash/Python/CI.
- [x] All acceptance scenarios are defined — Story 1 has 3 scenarios.
- [x] Edge cases are identified — grandfather для pre-Phase-002 (151 specs), `Issue ID = none` для spontaneous.
- [x] Scope is clearly bounded — Out of Scope перечислен: autohooks (#350), migration 151 specs, tracker API.
- [x] Dependencies and assumptions identified — OpenProject token через `.env.local-tracker`.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — FR-001..FR-007 привязаны к User Stories.
- [x] User scenarios cover primary flows — Story 1 = spec creation, Story 2 = AGENTS.md workflow, Story 3 = CI gate.
- [x] Feature meets measurable outcomes — SC-001..SC-005.
- [x] No implementation details leak — implementation в Plan-stage, не в Spec.

## Constitution Compliance

- [x] **Principle VI (Code Standards)**: KDoc — not relevant (Python lint script, не Kotlin). Per-feature документ — эта спека IS per-feature документ для governance.
- [x] **Principle IX (Knowledge-first)**: полный pre-flight log (4 grep queries), 4 knowledge files consulted. Прецедент #339 учтён в Knowledge References секции.
- [x] **Principle II (raw JDBC)**: N/A — нет DB изменений.
- [x] **Principle I (Self-contained)**: новый lint script — pure Python stdlib, без внешних зависимостей.
- [x] **Pragma — Issue-tracker OpenProject (Pass 295 → 349 amendment)**: Это САМА спека реализует governance amendment.

## Notes

- Pass 349 amendment — прецедент OpenProject #69 (2026-09-09) был обработан без claim/add-comment/mark-review.
- Это pass, не spec-based: добавление governance gates, не фича. Но всё равно оформлено через spec template для traceability.
- Implementation уже сделана в этой ветке (см. `Реализация (План будет в следующей фазе /speckit.plan)` в спеке — фактически готово, осталось только commit + PR).
