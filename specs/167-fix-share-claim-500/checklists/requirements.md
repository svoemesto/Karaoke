# Specification Quality Checklist: Починить 500 на `POST /api/public/share/claim`

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-11
**Feature**: [specs/167-fix-share-claim-500/spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - Спека описывает поведение системы (что должен делать claim, какие ответы должен возвращать, какие коды ошибок). Конкретные Kotlin-классы (`SongShareLinkService`, `PublicShareController`) упоминаются только как «точки привязки» для разработчика, реализация остаётся на усмотрение плана. Spring Boot / PostgreSQL / Kotlin не диктуются — это уже выбор проекта, зафиксированный в архитектуре.
- [x] Focused on user value and business needs
  - Все три user stories привязаны к user-journey или user-impact (гость видит «Ссылка недоступна» вместо плеера, разработчик не может диагностировать 500-ошибку).
- [x] Written for non-technical stakeholders
  - Используется язык домена (claim, грант, lease, сессия, владелец, гость). Технические термины (HTTP 500, errorCode) объяснены в контексте. Сценарии даны как «Given/When/Then», понятные продакт-менеджеру.
- [x] All mandatory sections completed
  - Контекст, Clarifications (4 вопроса), User Scenarios & Testing (3 истории + Edge Cases), Requirements (FR-001..FR-032), Key Entities, Success Criteria (SC-001..SC-011), Assumptions, Out of Scope.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
  - 4 вопроса вынесены в Clarifications, все закрыты решениями A. Маркеров `[NEEDS CLARIFICATION: ...]` в теле спеки нет.
- [x] Requirements are testable and unambiguous
  - FR-001..FR-014, FR-020..FR-021, FR-030..FR-032 — каждый проверяем вручную или curl'ом. Термины «идемпотентна», «HTTP 500», `share.internal`, `ShareException.InternalError` определены или имеют однозначные ссылки на существующий код.
- [x] Success criteria are measurable
  - SC-001 (100% валидных ссылок возвращают 200), SC-002 (0 ошибок 500 за 7 дней), SC-003 (моделируемая системная ошибка даёт 500 share.internal), SC-004 (debug возвращает реальные классы исключений), SC-005 (`\dt` возвращает 2 строки).
- [x] Success criteria are technology-agnostic (no implementation details)
  - SC-001..SC-005 говорят на языке «HTTP 500 с errorCode», «JSON с полями», «psql \dt» — это наблюдаемое поведение. SC-010/SC-011 — про восприятие пользователя/разработчика.
- [x] All acceptance scenarios are defined
  - US1: 4 сценария (новый claim, повторный с тем же browserHash, повторный с другим browserHash, превышение concurrentLimit). US2: 4 сценария (SQLException, NPE, ожидаемое 404, ожидаемое 400). US3: 4 сценария (нормальный debug, debug при отсутствии таблиц, debug без secret, debug с ошибкой на step1).
- [x] Edge cases are identified
  - 7 edge cases (частичная миграция, отсутствие recordhash-триггера, «хвосты» в БД, отсутствие колонок, отозванная ссылка, истёкший TTL, rate-limit, потеря премиума).
- [x] Scope is clearly bounded
  - Чётко сказано, что это hotfix на 500-ошибку, что НЕ покрывает (heartbeat, release, sweeper, admin, таймзоны) — отсылки к spec 164 и 166. Раздел «Out of Scope» фиксирует границы.
- [x] Dependencies and assumptions identified
  - Assumptions: 6 пунктов (полнота DDL, идемпотентность, поведение кода без правок после миграции, отсутствие конфликта имён триггеров, `WORKING_DATABASE` указывает на нужную БД, FR spec 164 остаётся в Draft). Зависимости от AGENTS.md и spec 164/166 явно упомянуты.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
  - FR-001/FR-002 (миграция) → SC-005 (`\dt` 2 строки). FR-003 (таблицы с правильной схемой) → SC-005 + ручная проверка `\d`. FR-004 (README/PR-описание) → review PR. FR-010..FR-014 (разделение ошибок) → SC-002, SC-003, US2#1..#3. FR-020/FR-021 (debug) → SC-004, US3#1..#4. FR-030..FR-032 (документация) → review PR.
- [x] User scenarios cover primary flows
  - US1 — главный user-journey (claim работает). US2 — основной путь диагностики (разделение ошибок). US3 — инструмент диагностики. Все три приоритета P1/P1/P2 распределены по важности.
- [x] Feature meets measurable outcomes defined in Success Criteria
  - SC-001, SC-002, SC-003, SC-004, SC-005 — все привязаны к acceptance-сценариям US1-US3.
- [x] No implementation details leak into specification
  - «Kotlin-класс `ShareException.InternalError`», «catch-блок», «psql» — упоминаются как точки привязки или существующий стек, но не предписывают стиль кода. Главное требование — наблюдаемое поведение endpoint'а.

## Notes

- Все checklist items PASS после первого прохода.
- Спека готова к `/speckit.plan`.
- Известное ограничение: SC-001 требует ручного применения миграции на проде (за пределами возможностей агента — см. AGENTS.md «Ограничения агента» → «Деплоить на сервер»). Это явно зафиксировано в FR-004 и Assumptions.
- Перекрёстные ссылки на `specs/164-complete-guest-share-link` и `specs/166-fix-share-link-timezone` намеренные — это hotfix, а не дубликат.
