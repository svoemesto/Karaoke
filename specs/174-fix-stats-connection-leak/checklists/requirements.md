# Specification Quality Checklist: Починить flood JDBC-соединений при открытии вкладки «Статистика»

**Purpose**: Валидация полноты и качества спецификации перед переходом к планированию.
**Created**: 2026-08-12
**Feature**: [spec.md](./spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — спека описывает ЧТО (lazy load табов, кеш агрегатов, 503 при сбое), а не КАК (HikariCP, ConcurrentHashMap и т.п. упоминаются только как один из вариантов и помечены как MAY/separate task)
- [x] Focused on user value and business needs — все User Stories привязаны к пользователю (администратор открывает вкладку, не получает «пустые графики»)
- [x] Written for non-technical stakeholders — секция «Симптом» и «Корневая причина» объясняются на уровне поведения, без погружения в JDBC internals
- [x] All mandatory sections completed — User Stories, Requirements, Success Criteria, Assumptions, Clarifications, Edge Cases, Key Entities — всё на месте

## Requirement Completeness

- [x] **Q1 ЗАКРЫТ** (2026-08-12, ответ пользователя): только frontend (lazy load + кеш), HikariCP — отдельная задача. FR-007 переформулирован.
- [x] **Q2 ЗАКРЫТ** (2026-08-12, ответ пользователя): `503` + `Retry-After: 10` + `errorCode: "stats.unavailable"` + `retryAfterSeconds: 10`. FR-003 финален.
- [x] **Q3 ЗАКРЫТ** (2026-08-12, ответ пользователя): TTL = 60 секунд, без SSE-инвалидации. FR-004 финален.
- [x] Requirements are testable and unambiguous — FR-001 (≤3 HTTP-запроса), FR-005 (баннер), FR-006 (сохранить `withDb`) — все проверяемы
- [x] Success criteria are measurable — SC-001 (≤3 запроса), SC-002 (0 exceptions), SC-003 (≤70 connections), SC-004 (p95 ≤500ms), SC-005 (100% баннеров) — все количественные
- [x] Success criteria are technology-agnostic — «HTTP-запросов», «соединений», «p95 времени ответа» — без упоминания HikariCP/Tomcat/etc.
- [x] All acceptance scenarios are defined — US1 имеет 3 сценария, US2 — 3, US3 — 2, всего 8 сценариев покрывают основные пути
- [x] Edge cases are identified — 4 edge case'а (max_connections=20, SSE отвалился, sync в фоне, race с Vuex init)
- [x] Scope is clearly bounded — FR-007 явно выносит HikariCP в опциональную/отдельную задачу, FR-008 фиксирует «не сломать 174 других вызова»
- [x] Dependencies and assumptions identified — Assumptions содержит 5 явных допущений (host=dev-pc, max_connections=100, no SSE, URL-контракт, HikariCP — отдельная задача)

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — каждый FR ссылается на US, у которых есть Acceptance Scenarios
- [x] User scenarios cover primary flows — открытие дашборда, lazy load, обработка сбоя — три ключевых потока
- [x] Feature meets measurable outcomes defined in Success Criteria — US1 → SC-001..SC-003, US2 → SC-001, US3 → SC-005
- [x] No implementation details leak into specification — HikariCP упомянут как «опциональная отдельная задача», Composite endpoint — на уровне контракта, а не реализации

## Notes

- **ВСЕ 3 [NEEDS CLARIFICATION] ЗАКРЫТЫ** ответами пользователя 2026-08-12. Спека готова к `/speckit.plan`.
- US3 (Priority: P2) помечен как «можно отложить в backlog, если effort на US1+US2 превышает разумный» — это явный сигнал для приоритизации в `/speckit.plan`.
- FR-007 (HikariCP) явно вынесен в **отдельную задачу** (НЕ в эту спеку) — пользователь подтвердил.
- Спека ссылается на 3 соседние спеки (`087`, `091`, `167`) и 1 секцию AGENTS.md — это хорошая трассируемость для будущего `/speckit.plan`.
- **Готова к `/speckit.plan`.**
- **2026-08-12 (`/speckit.clarify`)**: 3 дополнительных уточнения закрыты:
  - **scope кеша** → только чистые агрегаты (5 + `/monetization`); FR-004 уточнён.
  - **observability** → SLF4J-логи + `/api/stats/debug` endpoint (без Prometheus); FR-010 добавлен.
  - **throttling Retry** → клиентский disabled-кнопка + авто-retry через `retryAfterSeconds`; FR-011 добавлен, `DbOverloadBanner` обновлён.
- **Re-validate чеклиста**: 12/12 пунктов `[x]`, никаких регрессий.
