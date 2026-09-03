# Specification Quality Checklist: Не сохраняется цензурированное имя песни в SongEdit

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-03
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - FR-001 ссылается на Spring `@RequestParam`/setter — это **необходимая
    точность** для bugfix-спеки (без точного указания файла/строки
    разработчик не сможет починить баг). Фреймворки/языки не leak'ают
    в user stories/success criteria.
- [x] Focused on user value and business needs
  - US-1, US-2, US-3 описывают user-facing поведение
    (ручная правка, защита от регрессии, аудит).
- [x] Written for non-technical stakeholders
  - Root cause секция — техническая по необходимости (pre-implementation
    analysis). Сама спека написана в терминах «поле не сохраняется» /
    «ввод не доходит до БД», понятных редактору/менеджеру.
- [x] All mandatory sections completed
  - User Scenarios & Testing, Requirements, Success Criteria, Assumptions,
    Out of Scope — все заполнены.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
  - Все 8 FR + 3 NFR сформулированы однозначно, Open Questions = 0.
- [x] Requirements are testable and unambiguous
  - FR-001: точный код (имя параметра, имя setter'а, аналогия).
  - FR-005: имя скрипта, exit code, формат whitelist.
  - SC-001: 100% / 0% — измеримо.
  - SC-004: ≤1 секунды — измеримо.
- [x] Success criteria are measurable
  - SC-001..SC-008 — все с числовыми/булевыми метриками.
- [x] Success criteria are technology-agnostic (no implementation details)
  - SC-001/002/007/008 — поведенческие. SC-003/004/005/006 упоминают
    имена файлов/скриптов, но как часть «что проверить», не «как
    реализовать» (аналогично SC «чек зелёный» в любой методологии).
- [x] All acceptance scenarios are defined
  - US-1: 4 scenarios, US-2: 4 scenarios, US-3: 3 scenarios.
- [x] Edge cases are identified
  - 8 edge cases (XSS, длина, whitespace, race, старый клиент, sync,
    DTO mismatch, debounce).
- [x] Scope is clearly bounded
  - «Out of Scope» явно перечисляет, что НЕ входит (trim, maxlength,
    расширение чека на другие компоненты).
- [x] Dependencies and assumptions identified
  - Assumptions: Vue-diff корректен, Spring Web behavior, baseline-механизм,
    политика «доверие редактору», whitelist как явный механизм.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
  - FR-001 → US-1 AS-1, AS-2, AS-3.
  - FR-005/FR-006 → US-2 AS-1, AS-3, AS-4 + SC-005.
  - FR-007/FR-008 → US-3 AS-1, AS-2, AS-3 + SC-006.
- [x] User scenarios cover primary flows
  - US-1 (главный bugfix), US-2 (защита), US-3 (гигиена).
- [x] Feature meets measurable outcomes defined in Success Criteria
  - SC-001..SC-008 напрямую отслеживаются через FR-001..FR-010.
- [x] No implementation details leak into specification
  - Там, где упомянуты Spring/Vue/Kotlin — это прояснение root cause
    и точность bugfix-спеки. User-facing части (US, SC, Assumptions,
    Out of Scope) — implementation-free.

## Notes

- **Clarifications Session 2026-09-03** интегрированы:
  - Q1 (объём чек-листа) → **B (оба сразу)**: FR-005/006 + FR-007/008
    в одной спеке, общий чек обязателен.
  - Q2 (архитектурный подход) → **C (гибрид)**: FR-011 рефактор
    endpoint на централизованный приём + чек. Добавлены FR-011..FR-014,
    SC-009..SC-010, расширены edge cases для рефактора.
  - Q3 (OpenProject workflow) → **A (явно как DoD)**: NFR-004
    `claim-issue 52` на старте, NFR-005 `add-comment`+`mark-review`
    при завершении, SC-011.
  - Q4 (whitelist по умолчанию) → **B (предзаполненный)**: FR-005
    расширен списком нестандартных setter'ов (id, albumId, songType,
    free, не-String типы, fileName, tags, rootFolder, description/...,
    ≤10 полей).
  - Q5 (стратегия тестирования SC-001) → **B (ручная на LOCAL-БД с
    откатом)**: SC-001 уточнён, NFR-006 cleanup-скрипт.
- Спека готова к `/speckit.plan`. Root cause секция добавлена
  осознанно (pre-implementation analysis) — это bugfix, без точного
  указания файла/строки разработчик не сможет эффективно починить
  баг. Удаление этой секции снизит ценность спеки на порядок.
- FR-011 (рефактор endpoint) — **основной подход**, не fallback.
  FR-001 остаётся как fallback на случай, если рефактор не пройдёт
  code review (например, из-за неприемлемого риска regression в
  специальной обработке fileName/albumId).
- FR-005/FR-006 (защитный чек для SongEdit) + FR-007/FR-008 (общий
  аудит всех пар UI↔backend) — **оба обязательны** в этой спеке
  (Session Q1→B). Это означает бо́льший объём работы, но архитектурно
  правильнее.
- Whitelist (FR-005) предзаполнен (Q4→B) ≤10 полями. Если в ходе
  реализации выяснится, что whitelist должен быть >15 — это сигнал
  для редизайна чека (AST-анализ вместо grep).
- Test data cleanup (NFR-006, Q5→B): перед запуском SC-001 на
  LOCAL-БД сохранить исходные `song_name_censored` 10 тестовых песен
  в `.report-tracker-52.md`, после теста выполнить UPDATE обратно.
- Объём спеки — большой (FR-001..FR-014 + 11 SC + 6 NFR + 14 edge
  cases). За счёт гибридного подхода (Q2→C) реализация потребует
  тщательного integration-тестирования (SC-009/010).
