# Specification Quality Checklist: 289-fix-statbysong-cache-on-cold-start

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-01
**Feature**: [specs/289-fix-statbysong-cache-on-cold-start/spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - Примечание: упоминаются конкретные файлы/строки `StatBySong.kt:102` (Kotlin) — это ссылки на источник проблемы (file:line), не implementation details фичи. SLF4J-категория `infra.cache.statbysong` — конвенция проекта (см. local-0005).
- [x] Focused on user value and business needs
  - Цель — устранить блокирующее поведение cold-start (12 сек) и full-scan SQL (4 сек) → меньше зависаний прода.
- [x] Written for non-technical stakeholders
  - US1: «Пользователь открывает сайт сразу после deploy — ответ за < 100 мс» (user-facing).
- [x] All mandatory sections completed
  - US (3), FR (13), Key Entities, SC (5), Assumptions (7), Out of Scope, Clarifications.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
  - Архитектурные детали (`TaskScheduler` vs `Executor`, fallback 0 vs -1, persist куда) вынесены в `plan.md` как `D-N` решения.
- [x] Requirements are testable and unambiguous
  - FR-001: конкретный SQL-файл. FR-004: конкретное поведение «не блокировать > 100 мс». FR-005: конкретный `AtomicBoolean`.
- [x] Success criteria are measurable
  - SC-001: < 100 мс. SC-002: < 500 мс. SC-003: ровно 1 набор из 3 SQL. SC-004: 0 записей > 1000 мс за 24ч. SC-005: потребление памяти (через `docker stats`).
- [x] Success criteria are technology-agnostic (no implementation details)
  - SC-001, SC-002, SC-003 — user-observable. SC-004, SC-005 — измеримые характеристики, не tech-deps.
- [x] All acceptance scenarios are defined
  - US1: 4 scenarios; US2: 4; US3: 2. Все Given/When/Then.
- [x] Edge cases are identified
  - 6 edge cases (миграция во время refresh, повторная ошибка, sync DDL, существующий индекс, scheduler ordering, cold-start window).
- [x] Scope is clearly bounded
  - Out of Scope содержит 5 пунктов (materialized view, Caffeine, полный рефакторинг, refreshHourly change, pg_stat_user_tables).
- [x] Dependencies and assumptions identified
  - A-001..A-007 + Constitution § «Категорически запрещено» п. 2 (per-action согласие для DDL).

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
  - FR-001..FR-013 привязаны к US (FR-001..FR-003 → US2, FR-004..FR-008 → US1, FR-005 → US3, FR-009..FR-010 → US1/US2).
- [x] User scenarios cover primary flows
  - US1: cold-start (P1, основная проблема); US2: индекс (P1, устраняет root cause); US3: guard от параллельных refresh (P2, дополнительная защита).
- [x] Feature meets measurable outcomes defined in Success Criteria
  - SC-001..SC-005 — все измеримы, прямо соответствуют US.
- [x] No implementation details leak into specification
  - FR описывают ЧТО (async refresh, индекс, guard). КАК (использовать ли ScheduledExecutorService vs TaskScheduler, persist куда) — в plan.md.

## Notes

- Спека валидна. Архитектурные детали вынесены в `plan.md`:
  - D-1: использовать Spring `TaskScheduler` или создать `ScheduledExecutorService`?
  - D-2: fallback — возвращать 0 или `-1` (текущее поведение)?
  - D-3: persist последних значений (если кеш ещё не прогрет, но есть файл с предыдущими значениями)?
  - D-4: место применения индекса на SERVER (миграция `45_*` для новых контейнеров + ручной psql для существующего).
- A-002 явно фиксирует: per-action согласие для `CREATE INDEX CONCURRENTLY` на проде.
- A-006 явно фиксирует: fallback 0 — безопасное значение (не 500-ошибка на главной).
- Готово к `/speckit.plan`.