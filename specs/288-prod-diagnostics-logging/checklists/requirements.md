# Specification Quality Checklist: 288-prod-diagnostics-logging

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-01
**Feature**: [specs/288-prod-diagnostics-logging/spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - Примечание: спека упоминает PostgreSQL `ALTER SYSTEM SET`, SLF4J, Docker — это не implementation details, а required операционные действия (per Constitution § «Ограничения и доступы агента»). Языки/фреймформы описаны только в контексте «уже используется в проекте» (Kotlin + Spring Boot, SLF4J из local-0005).
- [x] Focused on user value and business needs
  - Цель фичи — диагностика инцидентов на проде, явная user value (сокращение времени на root-cause analysis).
- [x] Written for non-technical stakeholders
  - User Stories описаны в терминах «разработчик видит / может найти / может коррелировать», а не «агент выполняет SQL».
- [x] All mandatory sections completed
  - User Scenarios & Testing (4 US), Functional Requirements (23 FR), Key Entities, Success Criteria (7 SC), Assumptions (10), Out of Scope, Clarifications.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
  - Все 3 вопроса резолвнуты в текущей сессии (см. Clarifications).
- [x] Requirements are testable and unambiguous
  - FR-001..FR-007 имеют конкретные SQL-команды, FR-011..FR-016 имеют конкретные замены кода, FR-019..FR-020 имеют конкретные артефакты (документы).
- [x] Success criteria are measurable
  - SC-001..SC-007 — измеримы через `docker logs`, `grep -c`, `find`, временные рамки (15 минут на диагностику).
- [x] Success criteria are technology-agnostic (no implementation details)
  - SC-006 — user-facing: «за 15 минут можно найти» (не «grep по такому-то логгеру»). SC-007 — «объём логов PostgreSQL растёт на ≤100 строк/день» — это измеримая характеристика, не технологическая деталь.
  - Примечание: SC-001..SC-004 содержат конкретные команды (`docker logs`, `grep`) — это **тестовые команды**, а не implementation details фичи. По аналогии с другими спеками проекта (242, 244, 248 — там тоже SC указывают на `pg_log`).
- [x] All acceptance scenarios are defined
  - US1: 5 acceptance scenarios; US2: 5; US3: 4; US4: 2. Все Given/When/Then форматы заполнены.
- [x] Edge cases are identified
  - 8 edge cases: рестарт `ALTER SYSTEM`, `work_mem`, TZ контейнера, `pg_log` mixing, «мигание» сайта, `pg_max_connections`, `log_statement`, `printStackTrace`.
- [x] Scope is clearly bounded
  - Out of Scope содержит 7 явных исключений (pg_stat_statements, logging_collector, nginx log_format, ELK, karaoke-public, другие println, log_statement=all, мониторинг объёма pg_log, KaraokeConnection).
- [x] Dependencies and assumptions identified
  - A-001..A-010 + явное упоминание Constitution § VI FR-006 (KDoc), § VIII.5 (секреты), § II (сырой JDBC), § «Категорически запрещено» п.2 (DDL к прод-БД только по согласию).

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
  - Каждый FR привязан к US или к AC. FR-021..FR-023 — конституционные инварианты, не имеют отдельных AC, но упоминаются как «MUST быть выполнены».
- [x] User scenarios cover primary flows
  - US1 — диагностика медленных SQL; US2 — диагностика недоступности прода; US3 — корреляция по TZ; US4 — документация. Покрыты primary flows: «что-то зависло → как понять почему».
- [x] Feature meets measurable outcomes defined in Success Criteria
  - Все 7 SC измеримы через стандартные операции (docker logs, grep, find) и сопоставлены с US.
- [x] No implementation details leak into specification
  - Спека описывает **ЧТО** (FR-001: `log_min_duration_statement = 1000`), не **КАК ИМЕННО** делать (manual SQL через psql, скрипт, или агент — это решение в `plan.md`).

## Notes

- Спека валидна. Архитектурные детали (расположение документа `log-correlation.md` — `docs/ops/` или `livedocs/runbooks/`; log_format nginx — `$time_iso8601` или оставить `$time_local`; способ применения `ALTER SYSTEM SET` — ручной psql или admin-эндпоинт) вынесены в `plan.md` как technical decisions.
- A-009 явно фиксирует: агент НЕ выполняет `ALTER SYSTEM SET` без явного согласия пользователя в каждой сессии. Это per Constitution § «Категорически запрещено» п. 2.
- A-006 фиксирует важный факт: `ProdContainerCheck` работает на admin-машине (не на проде, т.к. `karaoke-app` не развёрнут на проде). Это влияет на то, как именно делается корреляция логов (по общему timestamp, а не по единому stdout).
- Все 3 [NEEDS CLARIFICATION] из clarification-сессии резолвнуты до написания спеки — никаких маркеров в spec.md нет.
- Готово к `/speckit.plan`.