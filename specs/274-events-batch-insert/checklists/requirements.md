# Specification Quality Checklist: Batch INSERT для tbl_events (FR-109)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-26
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - Примечание: спека ссылается на конкретные Spring/Kotlin классы и JDBC API — это требуется
    для реализации сервиса. Минимальные технические детали — file:line и API.
- [x] Focused on user value and business needs
  - Цель: снизить RPS INSERT к `tbl_events` на пиках (≥80%), не ломая семантику.
- [x] Written for non-technical stakeholders
  - User Stories и Success Criteria — на языке бизнеса (RPS, latency, kill-switch).
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
  - 4 вопроса закрыты в Clarifications Session 2026-08-26 (вариант A, opt-in, оба триггера,
    JDBC addBatch).
- [x] Requirements are testable and unambiguous
  - FR-001..FR-007 — каждое с конкретным API/паттерном и измеримым эффектом.
- [x] Success criteria are measurable
  - SC-001..SC-005 — все с числовыми метриками (1 batch INSERT, ≥80% снижение RPS, KDoc 100%).
- [x] All acceptance scenarios are defined
  - 3 User Story × 2–4 сценария = 9 acceptance scenarios.
- [x] Edge cases are identified
  - 6 edge cases (БД недоступна, крэш, разные поля, race condition, escape, default last_update).
- [x] Scope is clearly bounded
  - In scope: EventsBuffer @Service + flush + backpressure + kill-switch. Out of scope:
    persistent queue, multi-row INSERT, async через LISTEN/NOTIFY.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
  - FR-001..FR-007 привязаны к US1/US2/US3 acceptance scenarios.
- [x] User scenarios cover primary flows
  - US1 — снижение RPS на пиках, US2 — корректность семантики, US3 — наблюдаемость.
- [x] Feature meets measurable outcomes defined in Success Criteria
  - SC-001..SC-005 проверяются через pg_log + docker logs + CI.
- [x] No implementation details leak into specification
  - Реализация описана в plan.md, не в spec.md (разделение ответственности).
- [x] Dependencies and assumptions identified
  - Assumptions: SamplingFilter + DedupCache уже работают, JDBC batch оптимизирован драйвером,
    in-memory потеря при крэше допустима.

## Notes

- Эта спека — реализация Tier-3 FR-109 из parent спеки 241 (см. References в spec.md).
- **Архитектурное решение про opt-in** (Clarifications Q2): kill-switch default `false` — на
  текущем проде INSERT как раньше, безопасный rollout. Tier-3 P2, не блокер.
- **Архитектурное решение про JDBC addBatch** (Clarifications Q5): PostgreSQL оптимизирует
  multi-statement batches через `reWriteBatchedInserts=true`. Гибче, чем multi-row INSERT
  (разные поля у разных eventType).
- **Архитектурное решение про fail-open** (Edge case «крэш»): потеря in-memory буфера при
  крэше допустима для логирования. Если потеря критична — нужен persistent queue (отдельная
  будущая фича).
- **Sister services**: `SamplingFilter` (sampling 1/N) и `DedupCache` (TTL 30 сек) уже
  уменьшают поток. `EventsBuffer` — дополнительный уровень (batch INSERT вместо одиночных).
- **Без реальной нагрузки на текущем проде** (SamplingFilter + DedupCache уменьшают поток
  до ~30 req/min) эффект будет заметен только при росте трафика. Но фундамент заложен.
- На текущем проде `karaoke.web.events.batch-enabled = false` (default) — поведение не меняется.
  Администратор может включить после наблюдения baseline.