# Specification Quality Checklist: Аудит производительности БД и хранилища (prod)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-26
**Feature**: [spec.md](spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - Примечание: спека сознательно ссылается на file:line и class names — это **каталог hotspots**, а не
    дизайн фичи. Конкретные API/фреймворки (Spring Boot, Tomcat, PostgreSQL JDBC) упомянуты только как
    контекст для измерения эффекта (RPS, латентность, heap). Constitutional Principle II
    («Сырой JDBC + дифф по хэшам») явно зафиксирован как инвариант — никаких JPA/Hibernate.
- [x] Focused on user value and business needs
  - Цель: снизить латентность публичного сайта и убрать риск OOM/подвисаний прода.
- [x] Written for non-technical stakeholders
  - User Stories и Success Criteria — на языке бизнеса (RPS, латентность, heap, подвисания).
    Технические детали (file:line, SQL) собраны в Приложении A как каталог для разработчиков.
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
  - 0 маркеров. Все принятые решения задокументированы в Assumptions.
- [x] Requirements are testable and unambiguous
  - FR-101 … FR-110 — каждое с конкретным file:line и измеримым эффектом.
- [x] Success criteria are measurable
  - SC-001 … SC-007 — все с числовыми метриками (RPS, латентность p95, heap, время выполнения).
- [x] Success criteria are technology-agnostic (no implementation details)
  - Примечание: SC упоминают «PostgreSQL», `pg_stat_statements`, `ConcurrentHashMap`, `heap` — это
    намеренно: спека — каталог hotspots, и метрики привязаны к технологическому стеку (Constitution).
    User-facing часть (SC-001 «RPS к БД снижается на 50%», SC-006 «p95 ≤ 200 мс») — технологически
    нейтральна.
- [x] All acceptance scenarios are defined
  - 4 User Story × 1–3 сценария = 8 acceptance scenarios.
- [x] Edge cases are identified
  - 4 edge case'а в разделе User Stories.
- [x] Scope is clearly bounded
  - In scope: PostgreSQL hotspots в karaoke-app + karaoke-web + MinIO hotspots через
    `KaraokeStorageService`. Out of scope (явно): JPA/Hibernate миграция, шардинг БД, репликация,
    новые индексы (вынесены в Tier 2/3).
- [x] Dependencies and assumptions identified
  - Assumptions: нагрузка ≤50 RPS, 18k+ песен, MinIO на отдельном хосте через nginx, `pg_stat_statements`
    либо уже включён, либо включается миграцией.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
  - FR-001 … FR-005 (документация) — проверяются простым наличием разделов в spec.md.
  - FR-101 … FR-110 (будущие фичи оптимизации) — каждое привязано к file:line и метрике.
- [x] User scenarios cover primary flows
  - US1 — каталог hotspots; US2 — Tier 1 план; US3 — Tier 2/3 план; US4 — карта scheduled.
- [x] Feature meets measurable outcomes defined in Success Criteria
  - Все SC привязаны к конкретным FR и hotspots в Приложении A.
- [x] No implementation details leak into specification
  - За исключением обоснованных ссылок на file:line и имена классов в Приложении A (каталог для
    будущих фич). Сам `spec.md` (без Приложения A) — на уровне User Stories и Success Criteria.

## Notes

- Эта спецификация — **аналитическая**, а не фича-в-себе. Основная её ценность — каталог hotspots
  в Приложении A (file:line + severity + план). После принятия пользователем Tier-1 пунктов,
  каждый из них превратится в отдельную фичу через `/speckit.specify` с собственным branch.
- Сознательно НЕ включали конкретные SQL-планы (`EXPLAIN ANALYZE`) — для этого нужны реальные
  метрики прода, которые собираются после развёртывания инструмента из FR-108.
- **Рекомендуется** перед переходом к `/speckit.plan` провести `/speckit.clarify` с пользователем
  по вопросу: «Какие из hotspots Tier 1 (FR-101, FR-102, FR-103, FR-104) берём в первый спринт?».
  Варианты по умолчанию (если пользователь не уточнит): все четыре, так как они независимы и
  каждый имеет измеримый эффект.
- План оптимизации согласован с Constitutional Principle II: все предложения сохраняют сырой JDBC +
  `WHERE id IN (..)` для batch. Никаких JPA/Hibernate.
- Спека НЕ противоречит уже сделанным оптимизациям (Pass 087, 186, 187, 234, 235, 236, 239) —
  см. раздел «Уже сделанные оптимизации».
- Потенциальные риски для будущих фич оптимизации (не блокеры):
  - FR-102 (schema-cache): при DDL-изменении таблицы кеш может протухнуть — нужна инвалидация
    через Spring-слушатель DDL или явный `Cache.invalidate(tableName)` в миграциях.
  - FR-104 (streaming): нужна синхронизация с nginx (`X-Accel-Redirect` для offload) или
    `Transfer-Encoding: chunked`.
  - FR-108 (`pg_stat_statements`): требует `shared_preload_libraries = 'pg_stat_statements'` в
    `postgresql.conf` — нужна координированная миграция с DBA (пользователь).
