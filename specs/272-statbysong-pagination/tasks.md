---
description: "Task list для 272-statbysong-pagination — limit=1000 + safety-guard + баннер (FR-007)"
---

# Tasks: Ограничение лимита для Thymeleaf /statbysong (FR-007)

**Input**: Design documents from `/specs/272-statbysong-pagination/`
- plan.md (required)
- spec.md (required for user stories)

## Phase 1: Setup

- [x] T001 Создать спеку (spec.md) с FR-001..FR-005, Clarifications, Success Criteria
- [x] T002 Создать plan.md с Implementation Steps + Risks
- [x] T003 Создать checklists/requirements.md
- [x] T004 Создать tasks.md (этот файл)

## Phase 2: Foundational

- [ ] T005 Создать feature-ветку `272-statbysong-pagination` от master
- [ ] T006 Изменить `MainController.kt:486-496`:
  - [ ] T006a `limit = 100_000` → `limit = 1000`
  - [ ] T006b Добавить KDoc на `doStatBySong` со ссылкой на FR-007
  - [ ] T006c Передать `totalCount` через `getStatBySongCount` в модель
- [ ] T007 Изменить `StatBySong.kt:449-541`:
  - [ ] T007a Добавить константы `MAX_STAT_BY_SONG_LIMIT = 1000`, `MIN_STAT_BY_SONG_LIMIT = 1`
  - [ ] T007b В `getStatBySong` — `val safeLimit = limit.coerceIn(MIN, MAX)`, использовать в SQL
  - [ ] T007c Обновить KDoc с упоминанием safety-guard (FR-002)
- [ ] T008 Изменить `statbysong.html`:
  - [ ] T008a Добавить баннер `alert alert-info` под заголовком
  - [ ] T008b Текст: «Показано топ-1000 из ~{totalCount} доступных. Для полной выгрузки используйте /api/stats/by-song с пагинацией.»

## Phase 3: Polish

- [ ] T009 Создать LiveDoc `livedocs/features/272-statbysong-pagination.md`
- [ ] T010 Проверить все 7 CI gates:
  - [ ] `./gradlew :karaoke-web:compileKotlin :karaoke-app:compileKotlin --parallel`
  - [ ] `./gradlew :karaoke-web:ktlintCheck :karaoke-app:ktlintCheck`
  - [ ] `bash tools/check-kdoc-coverage.sh`
  - [ ] `pre-commit run --all-files`
- [ ] T011 Создать PR через `gh pr create --base master`
- [ ] T012 Дождаться `gh pr checks` (CI 7/7 PASS)
- [ ] T013 Merge в master
- [ ] T014 Обновить parent спеку 241:
  - [ ] `specs/241-db-storage-perf-audit/tasks.md` — T012.3 → `[x]`
  - [ ] `livedocs/architecture-notes.md` §Pass 241 — отметить FR-007 как done

## Definition of Done

- [ ] spec.md содержит FR-001..FR-005 + Success Criteria + Clarifications
- [ ] plan.md содержит Implementation Steps + Risks + Constitution Check
- [ ] `MainController.kt` — `limit = 1000` + KDoc + `totalCount` в модели
- [ ] `StatBySong.kt` — safety-guard `coerceIn(MIN, MAX)` + KDoc
- [ ] `statbysong.html` — баннер `alert alert-info` с `totalCount`
- [ ] LiveDoc создан в `livedocs/features/272-statbysong-pagination.md`
- [ ] Все 7 CI gates PASS
- [ ] PR создан и замержен в master
- [ ] Parent спека 241 обновлена (T012.3 → done)

## Notes

- Эта фича — Tier-3 P2 из parent спеки 241, FR-007 (см. spec.md Clarifications Session 2026-08-26).
- Паттерн safety-guard через `coerceIn(MIN, MAX)` — стандартный Kotlin-идиом для защиты от
  мусорных значений и DoS через REST query params.
- Sister endpoints: `StatsController.statsBySong` уже защищён автоматически через safety-guard
  в `getStatBySong` (не нужно отдельно менять).
- Runtime-валидация (опционально, делается пользователем): `EXPLAIN ANALYZE` SQL с `LIMIT 1000`
  — должен показать Index Scan + HashAggregate, миллисекунды.
- См. plan.md Risks & Mitigations для деталей про coerceIn edge cases и breaking changes.