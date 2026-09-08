# Specification Quality Checklist: Настраиваемый таймаут между поисковыми запросами

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-08
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] **CHK001** No implementation details (languages, frameworks, APIs) — спека описывает WHAT/WHY; ссылки на `setWebvueProp`/`getWebvueProp` и `webvue3` — в Assumptions как указатели на существующий механизм (Constitution V), а не выбор стека.
- [x] **CHK002** Focused on user value and business needs — единственный user story про администратора и его workflow массового поиска; решение про дефолт 10 сек взято из WP #61 verbatim.
- [x] **CHK003** Written for non-technical stakeholders — формулировки на русском, без жаргона; технические термины (admin-SPA, webvue3) — в Assumptions и FR в виде ссылок на существующую инфраструктуру.
- [x] **CHK004** All mandatory sections completed — User Scenarios & Testing, Requirements (FR + Key Entities), Success Criteria, Assumptions — все на месте.

## Requirement Completeness

- [x] **CHK005** No `[NEEDS CLARIFICATION]` markers remain — ни одного; все спорные места закрыты assumptions с явным указанием источника.
- [x] **CHK006** Requirements are testable and unambiguous — FR-001..FR-007 имеют явные MUST и observable outcome (диалог, default, persist, ≥1, единое значение для двух точек входа).
- [x] **CHK007** Success criteria are measurable — SC-001 (100% запусков), SC-002 (задержка ≥ N сек), SC-003/SC-006 (round-trip), SC-004 (default=10), SC-005 (валидация отвергает ввод).
- [x] **CHK008** Success criteria are technology-agnostic — SC не упоминают фреймворки/БД/языки; только user-observable поведение.
- [x] **CHK009** All acceptance scenarios are defined — 5 acceptance scenarios для US1 покрывают: первый запуск, повторный запуск, persist + использование, валидация, обе точки входа.
- [x] **CHK010** Edge cases are identified — 4 edge cases: таймаут=0, повреждённое сохранённое значение, несколько вкладок, серверный сбой запроса.
- [x] **CHK011** Scope is clearly bounded — секция «Out of scope» в Assumptions явно перечисляет: серверная DDoS-защита, логирование, история таймаутов, sync между пользователями.
- [x] **CHK012** Dependencies and assumptions identified — 9 assumptions, включая хранение через `setWebvueProp`/`getWebvueProp`, 5-step verification, code standards (Constitution VI), git workflow, default 10 сек (из WP #61).

## Feature Readiness

- [x] **CHK013** All functional requirements have clear acceptance criteria — FR ↔ SC мапятся явно: FR-001→SC-001, FR-002→SC-003/SC-004, FR-003→SC-002, FR-004→SC-003, FR-005→SC-003, FR-006→SC-005, FR-007→SC-006.
- [x] **CHK014** User scenarios cover primary flows — единственный US1 (P1) покрывает весь flow: диалог → ввод → запуск с задержкой → persist → повторный запуск с default.
- [x] **CHK015** Feature meets measurable outcomes defined in Success Criteria — все 6 SC проверяемы на уровне admin-SPA: видимость диалога (FR-001), pre-fill (FR-002), задержка между запросами (FR-003), persist (FR-004), default (FR-005), валидация (FR-006), единое значение для двух точек входа (FR-007).
- [x] **CHK016** No implementation details leak into specification — спека не предписывает конкретную технологию/БД/язык; только user-observable поведение и ссылки на существующую инфраструктуру.

## Notes

- Все 16 проверок PASS → спека (rev 2) готова к повторному ревью (Stage 5 round 2 после scope-расширения).
- Lessons applied (из iter #1..#4 Karaoke-spek):
  - **Урок #309**: ничего не выдумывал сверх WP #61. Валидация (FR-006) и round-trip (SC-003) — закрытые требованиями, не додуманные.
  - **Урок iter #3 #11**: boss self-verify против реального кода (HomeView.vue → createfromfolder, ApiController.kt:5302 → lyricsSearchExecutor.submit, Karaoke.kt → KaraokeProperties.getInt/set) выполнен ДО расширения scope.
  - **Урок #15 (MVP-checkpoint)**: задача маленькая, MVP-checkpoint не нужен — единый батч до финала.
- **Plan-level Fixes (Stage 5 ревью Кирилла, итерация #1)**:
  - **Р-1**: добавить Assumption про `livedocs/features/316-search-timeout-configurable.md` (прецеденты 304/305/307/310/315) — формат `NNN-name`, **НЕ** `docs/features/`. В `livedocs/INDEX.md` — строка-ссылка на новую фичу.
  - **Р-2**: Assumption про backend уточнён — межзапросного интервала в `LyricsFinderService.kt` НЕТ (только HTTP `.timeout(10000)` стр. 153); grep `Thread.sleep`/`delay` = 0. Точка внедрения определяется на фазе plan, reference `LyricsFinderService.kt`, интервал добавляется заново.
- **Scope-expansion fix (rev 2, после расследования Алины #131)**:
  - **rev 1 → rev 2**: владелец указал «Делать надо так, чтобы и импорт из папки покрывался таймаутом» и «Подумайте над механизмом хранения значения таймаута не на вебе, а на беке, к KaraokeProperties». Решение: глобальная backend-настройка `KaraokeProperties.lyricsSearchTimeoutSeconds` (Int, default 10), чтение в момент каждого запуска (FR-008, НЕ кэш). FR-007 расширен: оба пути — searchsongtextall (серийный, `Thread.sleep`) и createfromfolder (параллельный пул 4 потока, rate-limit перед submit).
  - **FR-008 новый**: backend MUST читать значение в момент запуска (не кэш), чтобы изменения через Properties UI применялись без перезапуска JVM.
  - **SC-007 новый**: после изменения таймаута через Properties UI новые запуски используют обновлённое значение.
  - **AS-5 переформулирован**: «администратор запускает массовый поиск двумя разными способами (импорт из папки **и** кнопка в компоненте «Песни») — оба сценария применяют таймаут одинаково».
- **Не использовано**: NEEDS CLARIFICATION. Все 5 спорных моментов решены в Assumptions с явным обоснованием.
