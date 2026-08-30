# Specification Quality Checklist: TTL-кеш для PublicSettingsWebController.getProperty

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-26
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - Примечание: спека ссылается на конкретный Kotlin/Spring паттерн (`ConcurrentHashMap`, `AtomicBoolean`,
    companion object), т.к. это реализация уже существующего контроллера и проверяется через
    ktlint/KDoc coverage. Сама бизнес-логика описана в User Stories на языке проблем/эффектов.
- [x] Focused on user value and business needs
  - Цель: снизить SQL-нагрузку от admin-UI на прод-БД, ускорить отзывчивость админских страниц.
- [x] Written for non-technical stakeholders
  - User Stories и Success Criteria — на языке бизнеса (latency, SQL count, UI responsiveness).
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
  - 3 вопроса закрыты в Clarifications Session 2026-08-26 (dirty flag, TTL, fail-open).
- [x] Requirements are testable and unambiguous
  - FR-001..FR-009 — каждое с конкретным API/паттерном и измеримым эффектом.
- [x] Success criteria are measurable
  - SC-001..SC-005 — все с числовыми метриками (latency, SQL count).
- [x] All acceptance scenarios are defined
  - 3 User Story × 2–7 сценариев = 16 acceptance scenarios.
- [x] Edge cases are identified
  - 6 edge cases (NOT_FOUND, exception, ранняя инициализация, setProperty fail, race condition).
- [x] Scope is clearly bounded
  - In scope: только `getProperty`/`setProperty`, новый `companion object`, 1 helper. Out of scope:
    digest, single-execution guard, distributed cache.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
  - FR-001..FR-009 привязаны к US1/US2 acceptance scenarios.
- [x] User scenarios cover primary flows
  - US1 — cache hit/miss, US2 — инвалидация через setProperty, US3 — race condition.
- [x] Feature meets measurable outcomes defined in Success Criteria
  - SC-001..SC-005 проверяются через ktlint/KDoc coverage + manual smoke test.
- [x] No implementation details leak into specification
  - Реализация описана в plan.md, не в spec.md (разделение ответственности).
- [x] Dependencies and assumptions identified
  - Sister spec 248-authors-tiles-cache (паттерн), `KaraokeProperties` (зависимость от karaoke-app).

## Notes

- Эта спека — реализация Tier-2 FR-006 из parent спеки 241 (см. References в spec.md).
- Sister spec 248 (authors-tiles-cache, PR #370 MERGED) — проверенный паттерн, который
  воспроизводится здесь с минимальными отличиями (TTL 60 сек vs 30 мин, NOT_FOUND_SENTINEL
  для отсутствующих ключей, отдельный dirty-флаг).
- Архитектурное решение про **отдельный dirty-флаг** (а не переиспользование `StatBySong.dirty`)
  зафиксировано в Clarifications и plan.md «Архитектурное решение» — это single responsibility,
  предсказуемая инвалидация, ноль side-effect'ов между доменами.
- На проде (`karaoke-web` без admin-UI) endpoint не используется — эффект только на админ-машине.
  Это P1 для админки, не критично для публичного сайта, но всё равно полезная оптимизация.
- FR-007 (NOT_FOUND_SENTINEL) — отступление от sister-spec 248, где пустой результат НЕ кешируется.
  Здесь это оправдано: для несуществующих ключей не имеет смысла делать SELECT каждую минуту —
  sentinel предотвращает лишний SQL. Если key появится в БД — `setProperty` его триггернёт.