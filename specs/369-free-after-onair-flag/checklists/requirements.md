# Specification Quality Checklist: Флаг «не снимать с эфира» (free_after_on_air)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - Примечание: имена Kotlin-полей (`freeAfterOnAir`, `SongStateResolver`) упомянуты как «имена в коде и БД должны быть согласованы» (FR-001) — это контракт, а не реализация. Vue-компонент `SongEdit.vue` упомянут как место UX по требованию задачи #81.
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
  - SC-002..SC-005 ссылаются на unit-тесты как на средство проверки, но не навязывают фреймворк или конкретный API (можно реализовать и через integration-тест).
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Все 12 FR разбиты на 3 User Stories (P1, P1, P2). FR-008..FR-012 относятся к
  «внутренним» инвариантам / Knowledge / дисциплине проекта и не имеют прямого
  acceptance scenario в User Stories — это OK, они покрываются Success Criteria и
  CI-gates (`tools/check-ssot-impact.py`, `tools/check-knowledge-structure.sh`).
- Решение оформлено через явную ссылку на релевантные knowledge-файлы
  (catalog domain + Song entity + Song lifecycle + dictionaries + publishing domain).
- Триггер: OpenProject #81 (задача пользователя, описание явно предлагает имя
  поля `free_after_on_air` — фича соответствует). Issue ID = #81, статус In progress
  (claimed через tracker-bootstrap hook).