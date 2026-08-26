# Specification Quality Checklist: Кеш для /api/public/authors-tiles

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-26
**Feature**: [spec.md](spec.md)
**Parent**: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md) — Tier-2 / FR-105

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - Спека ссылается на конкретные классы (`PublicApiController.authorsTiles`, `StatBySong.consumeDirty`, `KaraokeProperties.getBoolean`) — это **fix конкретного hotspot** из parent спеки (file:line указаны в parent, A.1, H-4). Никаких JPA/Hibernate — сохранён Constitution § II.
- [x] Focused on user value and business needs
  - Цель: снизить latency `/api/public/authors-tiles` с сотен мс до <50 мс warm path. Уменьшить RPS к `tbl_songs` на ≥80%.
- [x] Written for non-technical stakeholders
  - User Stories и SC — на языке бизнеса (latency, RPS, нагрузка на БД). Технические детали (TTL, ConcurrentHashMap, KDoc) — в Assumptions.
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
  - FR-001..FR-009 — каждое с конкретным file:line (или file:line-range) и измеримым поведением.
- [x] Success criteria are measurable
  - SC-001: <50 мс warm path, SC-002: <500 мс cold start, SC-003: ≤2 SQL на 100 запросов, SC-004: ≥80% снижение в `pg_log`, SC-005: cache disable работает, SC-006: ≤50 строк нового кода.
- [x] Success criteria are technology-agnostic (no implementation details)
  - SC-001/SC-002 привязаны к «latency в мс» и «SQL-запросам» — это намеренно: спека — fix hotspot в БД, метрики привязаны к пользовательскому времени отклика и нагрузке на БД (Constitution § Технологический стек).
- [x] All acceptance scenarios are defined
  - 2 US × 2–5 сценариев = 7 acceptance scenarios.
- [x] Edge cases are identified
  - 5 edge case'ов: пустой результат loadFn, KaraokeProperties недоступен, concurrent cache miss, неожиданный scope, consumeDirty() throws.
- [x] Scope is clearly bounded
  - In scope: cache-слой для `/api/public/authors-tiles`. Out of scope: индексы, кеш для других endpoints, изменение SQL, изменение DTO.
- [x] Dependencies and assumptions identified
  - Зависимости: `StatBySong.consumeDirty()` (есть), `KaraokeProperties.getBoolean()` (есть), cross-module import (allowed через gradle). 9 assumptions описаны.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
  - SC-001/SC-002 напрямую измеримы через browser devtools. SC-003/SC-004 — через `pg_log`. SC-005 — ручной тест. SC-006 — code-review.
- [x] No implementation details leak into specification
  - Исключение: FR-003 ссылается на `KaraokeProperties.getBoolean(key)` — это часть контракта из parent спеки, обязательно для получения default-value механизма.

## Notes

- **Зависимость от parent спеки**: эта фича — Tier-2 / FR-105 из parent спеки `241-db-storage-perf-audit`. Все ссылки на hotspots, cache-паттерн, Constitutional Principle II — в parent.
- **Рекомендуется** проверить наличие `KaraokeProperties.getBoolean(key)` ДО начала реализации (см. T004 в tasks.md Phase 1). Метод существует в `karaoke-app/.../KaraokeProperties.kt:96`.
- **Риск**: cross-module import `com.svoemesto.karaokeapp.KaraokeProperties` в `karaoke-web`. Уже используется в `KaraokeDbTable.kt` для schema-cache (см. parent спека, A.4). `karaoke-web` depends on `karaoke-app` через gradle.
- **Риск**: `StatBySong.consumeDirty()` атомарно сбрасывает dirty-флаг. `StatsCacheScheduler.refreshIfDirty` (вызывается раз в минуту) тоже использует `consumeDirty`. Порядок вызовов: scheduler → endpoint или endpoint → scheduler. В обоих случаях флаг корректно сбрасывается один раз — гонки нет.
- **Regression-риск**: поведение endpoint'а должно сохраниться 1-в-1 (тот же список `AuthorTilePublicDto`). После рефакторинга ОБЯЗАТЕЛЬНО проверить ручным тестом (открыть `/api/public/authors-tiles?scope=main` в браузере, сверить с baseline).
- **Backward compatibility**: контракт `/api/public/authors-tiles` сохраняется (тот же response shape, те же query-параметры). Cache прозрачен для клиента.
- **Тестирование**: автоматических тестов нет (см. Constitution § Тесты — `@Disabled`). Проверка — пользователем через deploy + `pg_log` + browser devtools.
- **TTL 30 минут**: компромисс между свежестью и нагрузкой. Альтернативы (TTL 1 час / 6 часов) ухудшают свежесть после save. 30 минут + dirty-инвалидация — оптимальный баланс.
- **Cache disable**: при `karaoke.public.authors-tiles-cache.enabled = false` endpoint работает без cache (loadFn на каждый запрос). Это полезно для отладки данных плашек авторов.
- **Per-feature документ**: `livedocs/features/248-authors-tiles-cache.md` создаётся в T018 (Phase 5).