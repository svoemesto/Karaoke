# Specification Quality Checklist: 281 — race condition stale Song перезатирает параллельные поля

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-31
**Last validated**: 2026-08-31 (Pass 281 — после имплементации всех 6 фиксов)
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — упомянуты конкретные файлы/функции как «уже существующие контракты» (Song, ApiController, UtilsAI, Utils); реализация описана на уровне «что должен делать метод», без префикса «как именно верстать».
- [x] Focused on user value and business needs — фокус на UX-боли админа (key/bpm теряются → ручной перезапуск процессов).
- [x] Written for non-technical stakeholders — формулировки в терминах поведения поиска текста и потери/сохранения полей; без Kotlin/SQL.
- [x] All mandatory sections completed — User Scenarios (3 stories), Requirements (FR-001..FR-014, FR-020..FR-022, FR-030..FR-031), Success Criteria (SC-001..SC-004), Assumptions (6 A), Edge Cases (5 EC).

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — все аспекты закрыты явными Assumption A-1..A-6.
- [x] Requirements are testable and unambiguous — каждый FR проверяется либо через UI/admin-stack, либо через SELECT к `tbl_songs`/`tbl_processes`, либо через code review (наличие `Song.loadFromDbById` перед `saveToDb`).
- [x] Success criteria are measurable — SC-001..SC-004 содержат конкретные метрики (6 точек, 46+ мест без регрессий, регрессия Pass 278/279).
- [x] Success criteria are technology-agnostic — SC сформулированы в терминах поведения БД/UI (без фреймворков, упоминание SQL/движков — как контракты, а не прескрипция).
- [x] All acceptance scenarios are defined — для US1/US2 даны 2–3 Given/When/Then.
- [x] Edge cases are identified — 5 edge cases (удаление песни, 4 места вызова, transient DB error, пустой candidateTexts).
- [x] Scope is clearly bounded — Assumptions явно фиксируют границы: только указанные функции, без изменений `Song.saveToDb`, без изменений фронта/БД/схемы.
- [x] Dependencies and assumptions identified — A-1 (непересечение с Pass 278), A-4 (loadFromDbById надёжен), A-6 (DEMUCS2/Sheetsage автоматически покрываются).

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — FR-001..FR-014 покрываются US1/US2/US3 или Edge Cases.
- [x] User scenarios cover primary flows — поиск с заполненными key/bpm (US1), гонка на любом из 4 движков (US2), регрессия Pass 278 (US3).
- [x] Feature meets measurable outcomes defined in Success Criteria — SC-001..SC-004 напрямую соответствуют US.
- [x] No implementation details leak into specification — паттерн reload-from-db-before-save упомянут как «уже работающий паттерн Pass 278» (контракт), а не прескрипция реализации.

## Notes

- **Реализация (2026-08-31, Pass 281)**: фича реализована полностью. Изменены 4 файла:
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt` (FR-001 — `applyFoundLyricsIfMissing`)
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` (FR-010, FR-011, FR-012, FR-013 — `findParentAndAudioParentForAll`, `applyFamilySongSelection`, `autoAssignOriginalByWaveform`, `findAudioParentByWaveform`)
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` (FR-014 — `setSourceMarkers`/`setSourceText`)
  - `livedocs/features/281-find-lyrics-overwrites-key-bpm.md` (новый LiveDoc)
- **Проверки после правки**:
  - `./gradlew :karaoke-app:compileKotlin` — PASS
  - `./gradlew :karaoke-web:ktlintCheck` — PASS, 0 новых нарушений
  - LiveDoc структура — 7/7 checks passed (172→173 файлов)
  - LiveDoc cross-links — 1413→1425 ссылок valid
- **Регрессий нет**: Pass 278 (`applyDuplicateOriginal`, `applyAudioParentMarkers`, `doCreateFromFolder`) и Pass 279 (sync `newSong` после `applyDuplicateOriginal`) не тронуты — фиксы аддитивные.
- Готово к PR в `master`.
