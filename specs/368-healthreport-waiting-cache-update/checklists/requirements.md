# Specification Quality Checklist: HealthReport WAITING status + SSE re-compute

**Purpose**: Validate specification completeness and quality before proceeding
to planning. Задача OpenProject #80 «Ускорение HealtReport-2».

**Created**: 2026-09-11

**Feature**: [`specs/368-healthreport-waiting-cache-update/spec.md`](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - Спека описывает состояния (`WAITING`, цвет `#FFCCFF`), но не указывает
    конкретные файлы реализации (`HealthReportStatus.kt` упоминается только
    в Key Entities как anchor для читателя, а не как implementation guide)
- [x] Focused on user value and business needs
  - US1 (WAITING вместо ложных ERROR), US2 (real-time обновление),
    US3 (нет лишних SSE) — все про UX админа
- [x] Written for non-technical stakeholders
  - Описывает поведение UI (цвет, перерисовка, F5)
- [x] All mandatory sections completed
  - OpenProject Tracking, Knowledge References, User Scenarios, Requirements,
    Success Criteria, Clarifications, Assumptions

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
  - Все 3 вопроса закрыты в Clarifications (новое WAITING, без debounce,
    WAITING при ошибке fill)
- [x] Requirements are testable and unambiguous
  - FR-001..FR-009: каждый проверяем (новый enum, callback, логи, no-regression)
- [x] Success criteria are measurable
  - SC-001..SC-005: латентность HTTP, hit rate, количество SSE, регрессии
- [x] Success criteria are technology-agnostic
  - «≤500 мс HTTP-ответ», «WAITING цвет #FFCCFF», «hit rate ≥80%» — не про
    Kotlin/Spring/MinIO SDK
- [x] All acceptance scenarios are defined
  - US1: 3 AC, US2: 3 AC, US3: 2 AC + Edge Cases (4)
- [x] Edge cases are identified
  - MinIO fail, вкладка закрыта, несколько bg tasks, race в HTTP
- [x] Scope is clearly bounded
  - Только `HealthReport.getHealthReportList` + `StorageMetadataCache` +
    `recomputeAndBroadcast`. Не трогаем repair-loop, UI, MinIO SDK
- [x] Dependencies and assumptions identified
  - Зависимости: Pass 344 (кеш), Pass 341 (SSE), спека #364 (FR-007)

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
  - FR-001: новый enum WAITING + цвет — проверяемо через UI
  - FR-002..FR-003: cache miss → WAITING, fill → SSE — проверяемо через
    Network panel + Vuex state
  - FR-004: completion callback — проверяемо через code review
  - FR-005: SLF4J категория — проверяемо через `docker logs karaoke-app`
  - FR-006: idempotency — проверяемо через Network panel (нет лишних SSE)
  - FR-007: bulk-warm — проверяемо на cold start
  - FR-008: цвет — проверяемо через UI
  - FR-009: single-flight — проверяемо через логи (один fill на песню)
- [x] User scenarios cover primary flows
  - US1: cold start, US2: real-time fill, US3: warm cache (нет лишних SSE)
- [x] Feature meets measurable outcomes defined in Success Criteria
  - SC-001: ≤500 мс cold start HTTP — связано с FR-002
  - SC-002: ≤3 сек прогрев — связано с FR-003
  - SC-003: ноль лишних SSE — связано с FR-006
  - SC-004: hit rate ≥80% — связано с FR-005
  - SC-005: регрессии — covered Assumptions
- [x] No implementation details leak into specification
  - В Key Entities упомянуты файлы как anchor для читателя (не как план
    реализации), реализация остаётся в plan.md/tasks.md

## Notes

- Спека написана на основе Knowledge (Pass 341+), опирается на:
  - `specs/364-healthreport-speedup/spec.md` (FR-007 — async cold-start,
    выполнен, но без SSE-обратной связи — это и есть баг #80)
  - `knowledge/domains/health/components/health-report.md` (decision tree)
  - `knowledge/domains/caching/components/caching-patterns.md` (async cold-start)
  - `knowledge/domains/sse/domain.md` (`SseNotification.HEALTH_REPORTS`)
- Это follow-up спека к #364 (Pass 364). Использует существующие компоненты,
  не вводит новых паттернов.
- Владелец явно запросил: «ввести ещё одно состояние WAITING с цветом FFCCFF»
  (зафиксировано в Clarifications #1).
- Items marked complete (`[x]`) — спека готова к `/speckit.plan`.

## Knowledge Compliance *(MANDATORY — see Constitution Principle IX)*

> Без `[x]` по всем пунктам этой секции спека **НЕ ДОЛЖНА**
> переходить в `/speckit.plan`. Это failure-stop, добавленный
> после прецедента 2026-09-09 (spec #339).

- [x] Knowledge pre-flight выполнен ДО `codegraph_explore` / grep по `src/`
  - Grep выполнен 3+ запросами ДО любого обращения к коду (см. `spec.md §
    Knowledge References`)
- [x] `knowledge/README.md` + `knowledge/domains/README.md` прочитаны **полностью**
  - `knowledge/README.md` прочитан полностью
  - `knowledge/domains/README.md` прочитан полностью
- [x] Релевантные домены определены через `grep -r '<keyword>' knowledge/`
      (минимум 3 запроса)
  - 3 grep-запроса: `HealthReport`, `StorageMetadataCache`,
    `WAITING|SseNotification|HEALTH_REPORTS|cache fill|async refresh`
  - Релевантные домены: `health`, `sse`, `storage`, `caching`
- [x] Все `domain.md` + `components/*.md` релевантных доменов прочитаны
  - `knowledge/domains/health/domain.md` — прочитан
  - `knowledge/domains/health/components/health-report.md` — прочитан
  - `knowledge/domains/sse/domain.md` — прочитан
  - `knowledge/domains/storage/domain.md` — прочитан
  - `knowledge/domains/caching/components/caching-patterns.md` — упомянут,
    прочитан (Pass 341)
  - `knowledge/domains/caching/components/web-caches.md` — прочитан
- [x] Все `local-*.md` ADR в `knowledge/adr/` прочитаны
  - `knowledge/adr/local-0003-shared-minio-image-cache.md` — прочитан
    (MinIO TTL cache, cache key immutable for entity version — релевантно
    для понимания write-through invalidation в `StorageMetadataCache`)
- [x] В `spec.md` заполнена секция «Knowledge References» с конкретными путями
  - `spec.md § Knowledge References` заполнена с 7 путями + grep log
- [x] Если Knowledge противоречит реквесту — это явно зафиксировано в
      «Open Questions» (NEEDS CLARIFICATION)
  - Противоречий нет: запрос на `WAITING` явно дополнил существующий
    `HealthReportStatus` (было 5 значений, + `WAITING` = 6)
- [x] Если grep по `knowledge/` ничего не дал — зафиксировано явное
      «no relevant docs» (с перечислением запросов и файлов)
  - Grep дал результаты: 4 релевантных домена + 2 ADR. «No relevant docs»
    не нужен.