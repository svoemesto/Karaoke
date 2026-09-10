# Specification Quality Checklist: 362 — Исправить «Element not found» при открытии компонента «Статистика» в webvue3

**Purpose**: Validate specification completeness and quality before proceeding to planning.
**Created**: 2026-09-10
**Feature**: [`spec.md`](../spec.md) — Issue #79 «Статистика, ошибка в консоли браузера»

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - Spec описывает **ЧТО** (lazy load, удаление reloadAll, TTL-кеш),
    а не КАК (конкретные импорты, методы Vue 3). Framework
    упомянут только в контексте (apexcharts, vue3-apexcharts),
    чтобы связать с ошибкой.
- [x] Focused on user value and business needs
  - Главная ценность: администратор работает со «Статистикой»
    без красных ошибок в консоли (Issue #79).
- [x] Written for non-technical stakeholders
  - Язык: русский, без жаргона. Use cases описаны через
    «Администратор открывает / переключает / возвращается».
- [x] All mandatory sections completed
  - OpenProject Tracking, Knowledge References, User Scenarios &
    Testing, Requirements, Success Criteria, Clarifications,
    Assumptions — все заполнены.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
  - Все 4 вопроса из Clarifications секции разрешены (см. ниже).
- [x] Requirements are testable and unambiguous
  - FR-001..FR-014: каждый имеет глагол MUST + конкретное действие.
    Например, FR-003 «MUST быть удалён» — проверяется через
    grep по `StatsView.vue`.
- [x] Success criteria are measurable
  - SC-001..SC-006: все измеримы (0 ошибок в console, ≤ 2 HTTP
    запросов на mount, ровно N запросов на вкладку и т.д.).
- [x] Success criteria are technology-agnostic (no implementation details)
  - SC-001..SC-003 описаны через DevTools Console/Network
    (это инструмент наблюдения, не имплементация). SC-005
    говорит про «кнопка должна быть удалена» — это UX-свойство.
- [x] All acceptance scenarios are defined
  - User Story 1: 4 сценария, User Story 2: 3 сценария,
    User Story 3: 3 сценария. Edge Cases: 5 случаев.
- [x] Edge cases are identified
  - 503 БД, медленная БД, F5 во время загрузки, быстрое
    переключение табов, 2 инстанса браузера — все описаны.
- [x] Scope is clearly bounded
  - Фича чисто фронтовая (webvue3). SQL, backend, public
    frontend — out of scope.
- [x] Dependencies and assumptions identified
  - См. Assumptions: lazy load паттерн webvue3, нет
    AbortController, apexcharts upstream issue, TTL 60 сек,
    Vuex singleton, LiveDocs updates.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
  - FR-001..FR-014 → SC-001..SC-006 → User Stories 1-3.
- [x] User scenarios cover primary flows
  - US1 (главный баг — открытие без ошибок),
    US2 (backward-compat при смене БД/периода),
    US3 (TTL-кеш не блокирует работу).
- [x] Feature meets measurable outcomes defined in Success Criteria
  - Все 6 SC проверяемы через DevTools Console/Network
    (не нужен сложный e2e).
- [x] No implementation details leak into specification
  - Единственное упоминание `vue3-apexcharts` / `apexcharts` —
    в разделе «Если ничего не нашлось» (Knowledge pre-flight),
    чтобы обосновать отсутствие ADR. В Requirements — только
    высокоуровневые MUST (lazy load, удаление reloadAll).

## Notes

- **Прецедент Pass 339**: спека должна была пройти Knowledge
  pre-flight. ✅ выполнено — 5 grep-запросов, 8 knowledge
  файлов прочитаны.
- **Прецедент спеки 174**: аналогичная фича была **обещана**,
  но **не реализована** в коде. Спека 362 выполняет обещание
  спеки 174. Это пример **regression governance**: спека
  может быть зафиксирована в merge, но не применена в коде.
  В рамках этой спеки проверяется, что **все** FR спеки 174
  (которые были про lazy load) применены.
- **Кнопка «Обновить всё» удаляется** — это UX-изменение,
  видимое пользователю. В Specimen (Pass 354) нужно упомянуть
  в release notes: «Кнопка "Обновить всё" в Статистике заменена
  на "Обновить" — теперь обновляется только активная вкладка».

## Knowledge Compliance *(MANDATORY — see Constitution Principle IX)*

> Без `[x]` по всем пунктам этой секции спека **НЕ ДОЛЖНА**
> переходить в `/speckit.plan`. Это failure-stop, добавленный
> после прецедента 2026-09-09 (spec #339).

- [x] Knowledge pre-flight выполнен ДО `codegraph_explore` / grep по `src/`
  - Все 5 grep-запросов (`StatsView`, `store stats`, `EventType`,
    `lazy load`, `apexcharts`) выполнены ДО чтения
    `webvue3/src/views/StatsView.vue`,
    `webvue3/src/components/Stats/store.js`,
    `webvue3/src/components/Stats/*.vue`.
- [x] `knowledge/README.md` + `knowledge/domains/README.md` прочитаны **полностью**
  - ✅ Прочитаны в этом сеансе (Pass 362 / speckit-full #79).
- [x] Релевантные домены определены через `grep -r '<keyword>' knowledge/` (минимум 3 запроса)
  - ✅ 5 grep-запросов выполнены (см. Pre-flight log в spec.md).
- [x] `domain.md` + **все** `components/*.md` для каждого релевантного домена прочитаны
  - ✅ stats/domain.md + stats/components/dictionaries.md прочитаны.
  - ✅ system/frontend/store-stats.md + system/frontend/webvue3-views-detailed.md
    прочитаны (не домен, а system-level docs, но релевантны).
- [x] **Все** `local-*.md` ADR из `knowledge/adr/` прочитаны
  - ✅ `local-0004-lazy-eager-load-webvue3-pagination.md` прочитан
    (главный принятый паттерн, обосновывающий FR-001).
- [x] Если ничего не нашлось — зафиксировано явно (no-op)
  - ✅ Зафиксировано: «apexcharts / Element not found →
    no relevant docs» (внешняя зависимость).
