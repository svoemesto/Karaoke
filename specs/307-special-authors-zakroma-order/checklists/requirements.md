# Specification Quality Checklist: Порядок плашек в Закромах и явная сортировка спец-авторов

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-06
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - В спеке упоминаются конкретные файлы Kotlin/Vue и SQL как «контрактные точки»,
    а не как детали реализации. Поведение описано в терминах «что должно быть»,
    а не «как именно кодить».
- [x] Focused on user value and business needs
  - Все user stories (US1–US3) описывают ценность для посетителя/редактора.
  - Технические требования (FR-001..FR-012) приведены как функциональный контракт.
- [x] Written for non-technical stakeholders
  - Используются термины: «тайл», «плашка», «сетка», «закрома», «редактор».
  - Раздел «Миграции БД» — отдельный, помечен как контракт, не как HOWTO.
- [x] All mandatory sections completed
  - User Scenarios & Testing, Requirements, Success Criteria, Assumptions — все заполнены.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
  - Все три вопроса решены в секции Clarifications (Q1 — отрицательные значения,
    Q2 — скрытие плашки в редакторской выборке, Q3 — сортировка в админ-таблице).
- [x] Requirements are testable and unambiguous
  - FR-001..FR-013 сформулированы с однозначной формулировкой «MUST»,
    каждый связан с конкретным сценарием или acceptance criteria.
- [x] Success criteria are measurable
  - SC-001..SC-007 — все измеримы (визуальная проверка, SQL-проверка, end-to-end,
    идемпотентность миграции, отсутствие регрессий, контракт API, per-feature doc).
  - SC-003 явно ссылается на Clarification Q5 (TTL ≤60с, без новых hook'ов).
- [x] Success criteria are technology-agnostic (no implementation details)
  - В SC используются «браузер», «БД», «API», «SQL», «per-feature документ».
    Никаких фреймворков, библиотек, внутренних ID классов.
- [x] All acceptance scenarios are defined
  - US1 — 3 сценария, US2 — 3 сценария, US3 — 3 сценария.
  - Edge cases — 10 пунктов с конкретным ожидаемым поведением
    (включая рассинхрон схемы LOCAL↔PROD и совместимость `at-selected` для обычных тайлов).
- [x] Edge cases are identified
  - См. секцию «Edge Cases»: конфликт с is_special_order, тай-брейкеры, пустые
    состояния, отрицательные/очень большие значения, идемпотентность миграции,
    кеш-несогласованность, рассинхрон схемы LOCAL↔PROD, совместимость
    `at-selected` для обычных тайлов после переноса спец-плашки.
- [x] Scope is clearly bounded
  - Что делаем: перенос плашки + sort_order. Что НЕ делаем: не трогаем серверную
    пагинацию/сортировку админ-таблицы (FR-009), не трогаем scope=special,
    не трогаем cache key.
- [x] Dependencies and assumptions identified
  - Секция Assumptions (8 пунктов): диапазон INTEGER, переиспользование recordhash,
    переиспользование KaraokeDbTable, не меняем cache key, имя файла миграции,
    механизм сохранения полей в админке.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
  - Каждый FR (включая добавленный FR-013 — пункт в PR-чеклисте)
    отражён хотя бы в одном acceptance-критерии US или в SC.
- [x] User scenarios cover primary flows
  - US1 — посетитель видит новый порядок.
  - US2 — посетитель видит sort_order в действии.
  - US3 — редактор задаёт sort_order.
- [x] Feature meets measurable outcomes defined in Success Criteria
  - SC привязаны к US: SC-001 к US1, SC-002 к US2, SC-003 к US3,
    SC-004 к FR-001/002, SC-005 к US1-3 без регрессий, SC-006 к FR-007,
    SC-007 к FR-012.
- [x] No implementation details leak into specification
  - Миграция SQL приведена в отдельной секции «Миграции БД» как
    контракт-референс (аналогично тому, как в spec 276 приведён патч для шапки).
    Это не «инструкция как писать код», а «вот такая схема в БД» для согласования.

## Notes

- Спека прошла все 13 проверок качества.
- Все [NEEDS CLARIFICATION] сняты в секции Clarifications (Session 2026-09-06).
- Файл `specs/307-special-authors-zakroma-order/spec.md` готов к следующей фазе
  (`/speckit.plan` или `/speckit.tasks`).
- Per-feature документ `docs/features/zakroma-tiles-sort-order.md` будет создан
  при имплементации (FR-012, SC-007) — это не блокер для спеки.
