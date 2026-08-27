# Specification Quality Checklist: 252 — Закрома: корректное скрытие блока типов альбомов при скролле

**Purpose**: Validate specification completeness and quality before proceeding to planning.
**Created**: 2026-08-27
**Feature**: [spec.md](spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — на уровне FR/CSS есть Vue-классы, но они уже заданы контрактом `.km-*` (это не выбирается, а констатируется); без них требование неверифицируемо.
- [x] Focused on user value and business needs — все 3 User Story формулируются через поведение посетителя, не через код.
- [x] Written for non-technical stakeholders — кроме технических терминов `position: sticky`, `z-index`, `viewport` — всё объясняется поведенчески.
- [x] All mandatory sections completed — User Scenarios, Requirements, Success Criteria, Assumptions, Edge Cases присутствуют.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain.
- [x] Requirements are testable and unambiguous — FR-001..FR-008 проверяются визуально или через DevTools `getBoundingClientRect`.
- [x] Success criteria are measurable — SC-001..SC-007 имеют численные/инструментальные пороги (1280×800, 375×667, scrollY, UP-TO-DATE).
- [x] Success criteria are technology-agnostic — SC-001..SC-003 описаны через поведение пользователя и DevTools-замеры (не CSS-сниппеты).
- [x] All acceptance scenarios are defined — 5 + 3 + 2 = 10 acceptance scenarios, покрывающих граничные случаи.
- [x] Edge cases are identified — 3 кейса (1 тип альбома, resize, стрим).
- [x] Scope is clearly bounded — меняется ТОЛЬКО `ZakromaView.vue`; бэкенд явно исключён (FR + SC-007).
- [x] Dependencies and assumptions identified — ссылки на спеки 012 / 181 / 250; assumptions (a)-(e) покрывают ambiguity вокруг «sticky vs обычный in-flow».

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — каждое FR покрыто AS в US1-US3.
- [x] User scenarios cover primary flows — десктоп-скролл, мобильный, экстремальное число типов, стрим.
- [x] Feature meets measurable outcomes defined in Success Criteria — SC-001..SC-007 явно достигаются через US+FR.
- [x] No implementation details leak into specification — кроме имён `.km-*` классов (это observable-контракт, не выбор стека).

## Notes

- Items marked incomplete require spec updates before `/speckit.clarify` or `/speckit.plan`.
- Спека НЕ блокирует план: единственная фактическая ambiguity — Assumptions (a)/(b) «sticky vs hide-on-scroll». Это решение для `/speckit.plan`, не блокер для planning. По умолчанию (a) — sticky сохраняется, исправляется overlap; альтернатива (b) для UX-обсуждения уже зафиксирована как опция FR-004.
- Pass 251 (`251-fix-zakroma-progressbar`) — соседняя фича того же файла; `.km-stream-progress` учтён в US3 / FR-005.
- Никаких регрессий в `karaoke-app` (AGENTS.md, «Категорически запрещено пересобирать»).
- Validation: PASS — все пункты ✅. Готово к `/speckit.plan`.
