# Specification Quality Checklist: 305-replace-systemerr-with-logger

**Purpose**: Validate specification completeness and quality
**Created**: 2026-09-04 (post-implementation checklist)
**Feature**: [spec.md](../spec.md)

> **Примечание**: спека 305 — узкая, workflow-тест. Чеклист формальный,
> потому что даже узкая спека должна проходить governance gates.

## Content Quality

- [x] No implementation details leak into spec — спека описывает **что** и
      **зачем** (заменить `System.err.println` на структурный logger, сохранить
      stack-trace, не сломать production routing через DualStream). Конкретные
      файлы упомянуты как контекст, не как предписание.
- [x] Focused on user value — лучшая наблюдаемость ошибок в логах (logback
      даёт timestamp, thread, MDC, level, logger-name) + stack-trace через
      второй аргумент.
- [x] Written for non-technical stakeholders — язык русский, терминология
      SLF4J / logback знакомая для разработчиков Karaoke.
- [x] All mandatory sections completed — контекст, корневая причина,
      acceptance criteria (FR-1..FR-8), out of scope.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers — спека полная.
- [x] Requirements are testable — каждый FR имеет конкретный verifiable check
      (grep / exit code / file content).
- [x] Success criteria are measurable — ktlintCheck exit 0, sha256 файла,
      grep на отсутствие `System.err.print`.
- [x] All acceptance scenarios covered — Phase 3 в tasks.md покрывает
      верификацию всех FR.
- [x] Edge cases identified — что делать если logger уже есть в файле
      (FR-2 explicit: «нет прецедента, добавляем»), и что если bootJar
      не запускается (FR-8 optional с обоснованием).

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — FR-1..FR-8.
- [x] User scenarios cover primary flows — 1 user story (замена logger).
- [x] Feature meets measurable outcomes — APPROVE от ревьюера с 0 findings.
- [x] No implementation details in spec — язык и фреймворк (Kotlin/SLF4J)
      упомянуты только как контекст (стиль остальных 27 файлов в проекте),
      не как требование по реализации.

## Governance Compliance

- [x] **AGENTS.md compliance**: имплементация в Karaoke, build evidence по
      5-шаговой верификации из Karaoke AGENTS.md.
- [x] **FR-014 livedocs-sync**: N/A (спека явно говорит — не меняет BC, не
      поднимает C4 уровень).
- [x] **KDoc/JSDoc coverage**: N/A (top-level `val log` — internal).
- [x] **Lint baseline**: ktlintCheck exit 0, нет новых нарушений.
- [x] **Pre-commit / CI**: в зоне имплементатора, признаков обхода нет.
- [x] **Build evidence**: все 3 команды из спеки задокументированы.

## Multi-agent workflow Compliance

- [x] **Спека**: создана оркестратором, передана имплементатору по
      протоколу `team-protocol.md`.
- [x] **Имплементация**: артефакт-отчёт по форме из спеки.
- [x] **Ревью**: по шаблону `reviews/000-template.md` (v0.2, без
      самоссылочного sha256).
- [x] **Вердикт**: APPROVE, 0 critical / 0 concerns.
- [x] **PR**: открыт оркестратором, смержен в master.
- [x] **Journal**: «305 closed» записана.

## Notes (для будущих спек)

1. **Файлы спеки в git** — спека должна быть закоммичена вместе с кодом
   (или отдельным PR) в той же ветке. Иначе спека живёт только в working
   tree одного агента и теряется.
2. **Шаблон `000-template.md`** — Sign-off не должен содержать sha256 файла
   (самоссылочное поле → drift). v0.2 исправляет.
3. **Полный набор артефактов** — даже узкая спека должна иметь
   `plan.md` + `report.md` + `checklists/requirements.md` (полнота
   governance). Не только `spec.md` + `tasks.md`.
