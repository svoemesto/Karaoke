# Specification Quality Checklist: Ограничение лимита для Thymeleaf /statbysong (FR-007)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-26
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - Примечание: спека ссылается на конкретные Kotlin-методы и SQL — это требуется для
    верификации правки (FR-001..FR-005). Минимальные технические детали — file:line и API.
- [x] Focused on user value and business needs
  - Цель: ускорить загрузку `/statbysong` (минуты → секунды), защитить от DoS через REST.
- [x] Written for non-technical stakeholders
  - User Stories и Success Criteria — на языке бизнеса (latency, count of rows, баннер).
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
  - 4 вопроса закрыты в Clarifications Session 2026-08-26 (вариант A, safety-guard, баннер, StatsController).
- [x] Requirements are testable and unambiguous
  - FR-001..FR-005 — каждое с конкретным file:line и измеримым эффектом.
- [x] Success criteria are measurable
  - SC-001..SC-007 — все с числовыми метриками (<2 сек, ≤1000 строк, KDoc 100%, CI PASS).
- [x] All acceptance scenarios are defined
  - 3 User Story × 2–4 сценария = 10 acceptance scenarios.
- [x] Edge cases are identified
  - 5 edge cases (limit=0, offset huge, empty DB, full export, getStatBySongCount perf).
- [x] Scope is clearly bounded
  - In scope: limit, safety-guard, баннер. Out of scope: CSV, пагинация в Thymeleaf,
    оптимизация getStatBySongCount.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
  - FR-001..FR-005 привязаны к US1/US2/US3 acceptance scenarios.
- [x] User scenarios cover primary flows
  - US1 — быстрая загрузка /statbysong, US2 — safety-guard, US3 — REST API для полных данных.
- [x] Feature meets measurable outcomes defined in Success Criteria
  - SC-001..SC-007 проверяются через browser devtools + pg_log + CI.
- [x] No implementation details leak into specification
  - Реализация описана в plan.md, не в spec.md (разделение ответственности).
- [x] Dependencies and assumptions identified
  - Assumptions: Thymeleaf + Bootstrap 4, getStatBySongCount лёгкий (Index Only Scan через
    миграцию 41), getStatBySong уже оптимизирован.

## Notes

- Эта спека — реализация Tier-3 FR-007 из parent спеки 241 (см. References в spec.md).
- **Архитектурное решение** про **safety-guard в `getStatBySong`** (Clarifications Q2):
  единая точка clamp'а — защищает оба endpoint'а (`/statbysong` Thymeleaf + `/api/stats/by-song`
  REST). Это single point of truth для лимита, невозможно случайно обойти.
- **Архитектурное решение** про **баннер в UI** (Clarifications Q3): ненавязчивый
  `alert alert-info` — явно сообщает администратору об ограничении + даёт ссылку на REST API
  для полных данных. Минимальная правка UI (5 строк), не ломает вёрстку.
- **Без пагинации в Thymeleaf**: при `LIMIT 1000` 1000 строк — это 1000 × ~20 колонок = 20k ячеек,
  помещается на 1 экран (Bootstrap 4 table-responsive). Пагинация не нужна.
- **Безопасность**: `?pageSize=100000` через REST API — потенциальный DoS-вектор, который
  ЗАКРЫВАЕТСЯ safety-guard'ом. Без этого фикса пользователь мог бы получить огромный response
  и нагрузить сервер.
- На текущем проде `/statbysong` — admin-only endpoint, но при локальной отладке / чтении логов
  минутная загрузка мешает. После фикса — секунды.