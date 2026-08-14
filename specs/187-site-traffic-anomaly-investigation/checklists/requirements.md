# Specification Quality Checklist: 187 — Анализ и устранение источников аномальной нагрузки на сайт

**Purpose**: Validate specification completeness and quality before proceeding to planning.
**Created**: 2026-08-14
**Feature**: [spec.md](./spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — FR-018 явно требует соблюдение Constitution (сырой JDBC, recordhash, sync-флаги). Технические детали (thread-local кеш, BufferedImage, nginx) упомянуты только как КОНТЕКСТ в Background и Acceptance Scenarios, но не как implementation plan.
- [x] Focused on user value and business needs — User Stories описывают поведение посетителя сайта (открывает страницу, держит вкладки, не видит сбоев), а не технические компоненты.
- [x] Written for non-technical stakeholders — User Stories на языке пользователя («открывает страницу», «видит тайлы», «сайт недоступен»). Технические термины (JDBC, SELECT, INSERT) вынесены в Assumptions и Requirements как «что нужно», а не «как делать».
- [x] All mandatory sections completed — User Scenarios (6 stories), Requirements (20 FRs), Success Criteria (9 SCs), Assumptions, Key Entities, Out of Scope, Open Questions — все заполнены.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — 4 Open Questions вынесены в отдельную секцию для `/speckit.clarify`, НЕ как блокеры (это намеренные clarifications с рекомендациями).
- [x] Requirements are testable and unambiguous — каждый FR имеет проверяемый критерий (можно curl/wrk/DevTools замерить).
- [x] Success criteria are measurable — SC-001..SC-009 с конкретными числами (0 запросов, 5 INSERT/мин, p95 ≤ 200 мс, ≤ 4 сек FCP, 60 запросов/мин rate limit, 100% покрытие аудита).
- [x] Success criteria are technology-agnostic — критерии описаны с точки зрения пользователя/админа (открытие страницы, скорость загрузки, размер таблицы), а не внутренних компонентов (HikariCP, кеш-хитрейт, memory).
- [x] All acceptance scenarios are defined — каждый User Story имеет 2-4 acceptance scenarios в формате Given-When-Then.
- [x] Edge cases are identified — 6 edge cases (3G, боты, долгие сессии, 404 картинки, CDN, race-conditions).
- [x] Scope is clearly bounded — «Out of Scope» явно вынесены: HikariCP, шардинг, замена nginx на CDN, изменение retention для других таблиц.
- [x] Dependencies and assumptions identified — 8 Assumptions (A-001..A-008) + FR-018 ссылка на Constitution.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — каждый FR покрыт одним или несколькими SC (например, FR-002 → SC-001, FR-003 → SC-002, FR-007 → SC-003).
- [x] User scenarios cover primary flows — US1 (загрузка Закромов — основной сценарий), US2 (polling — побочный сценарий, но частая причина инцидентов), US3 (events INSERT — основная DB-нагрузка), US4 (HTTP-кеш картинок), US6 (мониторинг для post-hoc анализа).
- [x] Feature meets measurable outcomes defined in Success Criteria — FR → SC покрытие полное, проверено вручную.
- [x] No implementation details leak into specification — Implementation вынесен в `plan.md`/`research.md` (создаются на этапе `/speckit.plan`). Спека описывает ЧТО (например, «nginx добавляет Cache-Control»), а не КАК (какие именно директивы nginx).

## Audit Coverage

- [x] Все упоминаемые в спецификации file:line ссылки реальны (проверено по codegraph):
  - `MainController.kt:121` (doRegisterEvent)
  - `KaraokeConnection.kt:30` (thread-local)
  - `PublicApiController.kt` (picture endpoint, picture 302)
  - `AuthorTiles.vue:15` (`loading="lazy"`)
  - `NewsBell.vue:108` (polling timer)
  - `useAuth.js:55` (`autoRefreshStarted` dedup)
  - `27_listening_history.sql:3-6` (sync events)
  - `docs/features/stats.md` (HikariCP context)
  - Pass 60 (SEO HTML for bots)
  - Pass 52 (news/since for anonymous)
  - Spec 174 (stats connection leak)

## Constitution Alignment

- [x] **Principle I (Self-contained автопайплайн)**: фичи относятся к публичному сайту, не к медиа-пайплайну. Не нарушает.
- [x] **Principle II (сырой JDBC)**: фиксы не вводят JPA/Hibernate. Все INSERT'ы идут через `connection.prepareStatement()`. FR-018 явно это запрещает.
- [x] **Principle III (sync через SyncRegistry)**: `tbl_events` не участвует в sync (см. `27_listening_history.sql:3-6`). Фикс retention не нарушает.
- [x] **Principle IV (async-очередь)**: фичи не касаются `KaraokeProcess*`. Не нарушает.
- [x] **Principle V (двух-фронтенд)**: фиксы относятся к обоим фронтам (admin + public) — `/api/public/*` общие для них.
- [x] **Principle VI (code standards)**: KDoc/JSDoc обязательны для новых публичных функций (см. `MainController.doRegisterEvent` и новый debug endpoint). FR-020 создаёт per-feature документ.
- [x] **Principle VII (cross-machine setup)**: фиксы не меняют локальные конфиги. Влияют только на серверную часть (nginx + karaoke-web).
- [x] **Principle VIII (secrets)**: новые env-переменные (sampling rate, retention days) — не секреты. Не нарушает.

## Notes

- Спека написана для этапа `/speckit.plan` — следующий шаг: создать `research.md` с таблицей аудита всех REST-эндпоинтов и scheduled-task'ов. Спека сама требует этого в FR-015 и FR-016.
- 4 Open Questions должны быть резолвнуты ДО перехода к plan.md (через `/speckit.clarify` или direct answer).
- Файл сохранён в ветке `187-site-traffic-anomaly-investigation` — следующие коммиты: research.md → plan.md → tasks.md → implementation.