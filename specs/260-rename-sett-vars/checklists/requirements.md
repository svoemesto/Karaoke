# Specification Quality Checklist: Переименование `sett`/`settings` → `song` (260-rename-sett-vars)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-28
**Feature**: [`spec.md`](./spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — допустимы ссылки на конкретные модули (`karaoke-app`, `karaoke-web`, `karaoke-public`), файлы и SQL, но это часть контракта legacy/regression, не выбор стека.
- [x] Focused on user value and business needs — задача исключительно про читаемость кода для разработчика (внутренний рефакторинг).
- [x] Written for non-technical stakeholders — формулировки «разработчик открывает файл и видит понятное имя», без жаргона Kotlin/Vue/SQL где можно.
- [x] All mandatory sections completed — User Stories, Edge Cases, Functional Requirements, Key Entities, Success Criteria, Assumptions.

## Requirement Completeness

- [x] No `[NEEDS CLARIFICATION]` markers remain — все пять Q в Clarifications разрешены в этой же секции (полный охват, `albumSettings` отдельно, `model.addAttribute` переименовываем, `karaoke-public` включаем, `webvue3` вне scope — `SubsEdit.vue:183` label остаётся).
- [x] Requirements are testable and unambiguous — FR-001…FR-015 задают конкретные правила (какие места, какие имена, какие исключения); SC-001…SC-007 задают измеримые критерии (грепы, линтеры, ручные проверки).
- [x] Success criteria are measurable — SC-001 baseline `grep`, SC-002 baseline + post-`grep`, SC-003/004 lint и сборка, SC-005 KDoc-греп, SC-006 ручная проверка UI, SC-007 финальный греп.
- [x] Success criteria are technology-agnostic — формулировки «поиск не находит», «сборка проходит», «рендеринг показывает корректные поля» (без привязки к конкретной версии ktlint/ESLint/PostgreSQL).
- [x] All acceptance scenarios are defined — для каждой из трёх User Story заданы 3 сценария Given/When/Then.
- [x] Edge cases are identified — 11 edge-кейсов (конфликт имён, `setts` мн.ч., SQL-алиас, `for`/`map`-итераторы, закомментированный код, исключения в виде `albumSettings`/`tbl_public_settings`/`LS_SETTINGS_KEY`/endpoint плейлиста/конфигурация платформ/физическая колонка БД/импорт `Song` в `mlt/mko/*.kt`/синхронность деплоя).
- [x] Scope is clearly bounded — внутри: `sett` в Kotlin+Thymeleaf+Vue+JS+SQL, остаточные `settings` в Kotlin+DTO-комментарии, `tbl_settings` в комментариях. Снаружи: `albumSettings` (отдельная задача), миграция БД, имя класса `Song` (не меняется).
- [x] Dependencies and assumptions identified — ссылка на спеку 102 как прецедент (FR-005, FR-014, FR-016), ссылка на спеку 011 (миграция БД ранее), предположение об атомарном деплое backend+HTML.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — каждый FR привязан либо к конкретному файлу/месту из Edge Cases, либо к проверяемому правилу (FR-001: «параметр функции с типом Song и именем sett»), либо к исключению (FR-006/FR-007 со списком).
- [x] User scenarios cover primary flows — US1 (Kotlin), US2 (Thymeleaf+Vue+SQL), US3 (исключения).
- [x] Feature meets measurable outcomes defined in Success Criteria — SC покрывают все три US.
- [x] No implementation details leak into specification — упоминания `Song`, `mtString`, `v-for` — это **имена сущностей/синтаксиса**, не выбор реализации.

## Notes

- Спека готова к `/speckit.plan`. Все Clarifications (5 вопросов) разрешены, исключения явные, baseline-числа известны (546 `sett`, ≥5 `settings:` в Kotlin).
- Особо обратить внимание на этапе планирования: правильный порядок правок для `MainController.kt` (сначала внутренние `sett` в лямбдах, потом атрибут `model.addAttribute` + синхронно шаблоны), и для `StatBySong.kt` (переименование SQL-алиаса не сломает Kotlin-компиляцию, но нужно grep-подтверждение, что больше нигде эта строка как Kotlin-литерал не формируется).
- `karaoke-public` коммитится в той же ветке, но деплоится независимо — это надо явно зафиксировать в `plan.md` как риск-решение (FR-014).
