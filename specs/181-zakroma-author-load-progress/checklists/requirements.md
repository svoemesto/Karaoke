# Specification Quality Checklist: Zakroma — очистка + real-time прогресс через NDJSON-стрим

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-13 (обновлён после выбора Q5 = real-stream вместо гибрида)
**Feature**: [spec.md](./spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - **Note**: упоминаются `StreamingResponseBody`, `ReadableStream`, `AbortController`
    и nginx-директивы по имени — это НЕ implementation-leak, это **фиксированные
    контракты платформы** (Spring Boot streaming API + Web Streams API + nginx
    config syntax), без которых невозможно описать wire protocol и edge cases.
    По аналогии с упоминанием `recordhash`/`assoc-by` в других спеках: имя
    API/протокола — это не выбор реализации, а часть контракта.
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed (User Stories, Edge Cases, FR, Key Entities, SC, Assumptions, Out of Scope, Implementation Plan)

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
  - 19 FR (5 BE + 7 FE + 3 NX) с явными acceptance criteria, проверяемыми
    через curl + DevTools Network + UI inspection.
- [x] Success criteria are measurable
  - SC-001 (≤ 50мс очистка), SC-002 (≤ 500мс TTFB), SC-003 (0% зависаний),
    SC-004 (метрики через 7 дней), SC-005 (обратная совместимость),
    SC-006 (nginx -t), SC-007 (AbortController ≤ 100мс).
- [x] Success criteria are technology-agnostic (no implementation details)
  - Все SC описывают поведение/эффект с точки зрения посетителя или
    оператора (DevTools-видимость, метрики, конфиг-валидация) — не
    внутреннюю механику.
- [x] All acceptance scenarios are defined
  - US1: 3 сценария (очистка). US2: 5 сценариев (real progress start,
    incremental update, finish, error, abort). US3: 1 сценарий (debounce).
- [x] Edge cases are identified
  - 11 edge cases: nginx buffering, gzip, stream break, empty result,
    AbortController при уходе, переключение авторов, длинные стримы,
    gzip-разрыв NDJSON, поддержка браузеров, тёмная тема, A11y.
- [x] Scope is clearly bounded
  - Затрагивает: `PublicApiController.zakromaStream` (новый endpoint),
    nginx `80to8897`, `useZakromaStreamProgress` (новый composable),
    `zakroma.js` store, `ZakromaView.vue`. НЕ затрагивает: существующий
    `GET /api/public/zakroma` (обратная совместимость), `SearchView`,
    `loadSpecialBucket`, админку.
- [x] Dependencies and assumptions identified
  - Явные Assumptions: нативный ReadableStream (caniuse 99%+),
    nginx-конфиг для prod (skip на localhost), Spring `StreamingResponseBody`,
    AbortController везде где fetch.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
  - US1 — очистка (P1), US2 — real progress (P1), US3 — debounce (P2).
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification
  - **Note**: `NDJSON`-формат и `StreamingResponseBody` упоминаются не
    как «выбор технологии», а как **обязательный контракт wire protocol**
    (без него физически невозможно передавать прогресс порциями). Это
    эквивалентно упоминанию REST/JSON в спецификации публичного API —
    не implementation detail.

## Implementation Plan

5 коммитов в ветке `181-zakroma-author-load-progress`:

1. **#1** (✅ сделан): инфраструктура — `before_specify` hook.
2. **#2**: backend NDJSON endpoint + DTO wrapper.
3. **#3**: nginx config + `tools/deploy-nginx-stream.sh`.
4. **#4**: frontend streaming layer (composable + store + UI).
5. **#5**: cleanup old sync code (удалить `useZakromaLoadProgress`,
   debounce, текста «Загрузка...»), финальный CI pass.

## Notes

- Зафиксированное решение: **NDJSON chunked-stream** (Q5 clarification).
  Синтетический прогресс НЕ используется — всё реальное из бэка.
- Главный риск: nginx на проде (`80to8897`) сейчас БУФЕРИЗУЕТ ответы
  по умолчанию. Без правки конфига фича не работает в продакшене,
  только на localhost. Правка — отдельный коммит #3 + ручной шаг
  на проде (cp + nginx -t + reload, см. AGENTS.md).
- `useZakromaLoadProgress` (синтетический) **полностью удаляется** —
  нет смысла поддерживать две реализации.
- Обратная совместимость `GET /api/public/zakroma` (без `stream`)
  обязательна (FR-BE-007, SC-005) — другие потребители API не должны
  сломаться.
- Готово к `/speckit.plan`.
