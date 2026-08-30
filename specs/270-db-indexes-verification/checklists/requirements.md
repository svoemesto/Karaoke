# Specification Quality Checklist: Верификация и идемпотентное создание индексов FR-110

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-26
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - Примечание: спека сознательно ссылается на конкретные SQL-команды и имена индексов —
    это **DDL-миграция**, и проверка качества требует точных имён (иначе `IF NOT EXISTS`
    не сработает корректно). Миграция — это реализация в полном смысле слова.
- [x] Focused on user value and business needs
  - Цель: гарантировать наличие индексов на любой БД + задокументировать в git-истории.
- [x] Written for non-technical stakeholders
  - User Stories и Success Criteria — на языке бизнеса (гарантия индексов, EXPLAIN ANALYZE).
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
  - 3 вопроса закрыты в Clarifications Session 2026-08-26 (Q1=no-op на текущем проде, Q2=имена,
    Q3=CONCURRENTLY).
- [x] Requirements are testable and unambiguous
  - FR-001..FR-006 — каждое проверяемо через конкретный SQL.
- [x] Success criteria are measurable
  - SC-001..SC-005 — все с конкретными метриками (наличие индексов, no-op, EXPLAIN).
- [x] All acceptance scenarios are defined
  - 2 User Story × 2–4 сценария = 6 acceptance scenarios.
- [x] Edge cases are identified
  - 3 edge cases (существующий индекс, отсутствующая таблица, расхождение имён).
- [x] Scope is clearly bounded
  - In scope: 3 индекса, 1 миграция, 1 LiveDoc. Out of scope: новые индексы, CONCURRENTLY,
    DROP/CREATE, partitioning, полный аудит.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
  - FR-001..FR-006 привязаны к US1/US2 acceptance scenarios.
- [x] User scenarios cover primary flows
  - US1 — гарантия индексов на любой БД, US2 — документирование в git-истории.
- [x] Feature meets measurable outcomes defined in Success Criteria
  - SC-001..SC-005 проверяются через psql `\d` и `EXPLAIN ANALYZE`.
- [x] No implementation details leak into specification
  - Реализация описана в plan.md, не в spec.md.
- [x] Dependencies and assumptions identified
  - Convention имён (Clarifications Q2), PostgreSQL 9.5+ (Assumptions), порядок миграций
    (Assumptions, Edge Cases).

## Notes

- Эта спека — реализация Tier-2 FR-110 из parent спеки 241 (см. References в spec.md).
- **Контекстная находка** (зафиксирована в Clarifications Q1): все три индекса уже созданы
  в `01_initdb.sql:148,173` (исходно `tbl_settings_*_index`, переименованы в
  `28_rename_settings_to_songs.sql:48,66`). На текущем проде миграция — **no-op**.
  Ценность — документирование + защита от восстановления БД из старого дампа.
- **Архитектурное решение** про **имена индексов** (Clarifications Q2): используем существующие
  имена `tbl_*_*_index` (convention проекта), не `idx_*` (как в исходной спецификации FR-110).
  Это критично для корректной работы `IF NOT EXISTS`.
- **Архитектурное решение** про **CONCURRENTLY** (Clarifications Q3): НЕ используем
  (FR-006). На текущем проде — no-op, на fresh БД — таблицы маленькие, на большой существующей
  БД — окно блокировки миллисекунды для маленьких таблиц типа `tbl_songs` (18k записей).
- **Никакого Kotlin-кода** — только SQL-миграция + LiveDoc + спека. Это «dba-fix», не фича кода.
- CI-проверки минимальные — только LiveDocs (structure, cross-links, external-links).
  Никаких ktlint/ESLint/Prettier/JSDoc — Kotlin/JS код не менялся.