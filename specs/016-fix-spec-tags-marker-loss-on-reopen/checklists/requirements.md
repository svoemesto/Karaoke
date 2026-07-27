# Specification Quality Checklist: Спецтеги — сохранение маркеров после «Точные маркеры → Apply → Save → reopen»

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-07-27
**Feature**: [spec.md](./spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Спецификация ограничивается admin-редактором `SubsEdit.vue` и потоком «Точные маркеры + Apply + Save + reopen». Лёгкий admin-редактор, краудсорсинг, поток «Распознать текст (Whisper)» явно вынесены в «Out of scope».
- Гипотеза первопричины (асимметрия вызовов `syncMarkersFromSpecTags`, поведение `updateMarkersBySyllables`, потенциальный рассинхрон `sourceSyllables` ↔ syllables-маркеры, возможная неполная Save) зафиксирована в разделе «Контекст» — но **точная первопричина будет установлена на этапе `/speckit.plan`** по фактическому коду и трассировке на воспроизводящем стенде.
- SC-001/SC-002/SC-003/SC-004/SC-005 измеримы (число/процент тестовых прогонов), проверяемы без знания внутренностей фреймворка — через наблюдаемое поведение UI и БД.
- US2 и US3 (P2/P3) — защитные сценарии, на случай если первопричина окажется не в US1. Если на этапе plan выяснится, что US1 покрывает всё — US2/US3 можно опустить, но в текущей формулировке спека оставляет их как «явно зафиксированный риск».
- Файл `contracts/` / `data-model.md` / `research.md` / `quickstart.md` / `plan.md` / `tasks.md` НЕ создаются в рамках `/speckit.specify` — это работа `/speckit.plan` (и `/speckit.tasks`), когда первопричина будет локализована.
- Зависимости: спеке 010 (`specs/010-lyrics-spec-tags/`) — FR-005/FR-006/FR-007/FR-010. Эта спецификация их **не переопределяет**, а только добавляет FR-001..FR-011 как локализацию «где именно в коде должна быть видна их реализация для reopen-потока».
- Изменения в `AGENTS.md` / `CONTRIBUTING.md` / `docs/architecture-notes.md` / `docs/features/*` — вне scope этой спецификации, решается на `/speckit.plan` (если потребуется — обновить per-feature документ для spec-tags, либо создать запись в `architecture-notes.md` по факту PR).
- Items marked incomplete require spec updates before `/speckit.clarify` or `/speckit.plan`
