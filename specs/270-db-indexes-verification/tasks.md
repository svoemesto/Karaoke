---
description: "Task list для 270-db-indexes-verification — идемпотентная миграция индексов FR-110"
---

# Tasks: Верификация и идемпотентное создание индексов FR-110

**Input**: Design documents from `/specs/270-db-indexes-verification/`
- plan.md (required)
- spec.md (required for user stories)

## Phase 1: Setup

- [x] T001 Создать спеку (spec.md) с FR-001..FR-006, Clarifications, Success Criteria
- [x] T002 Создать plan.md с Implementation Steps + Risks + Constitution Check
- [x] T003 Создать checklists/requirements.md
- [x] T004 Создать tasks.md (этот файл)

## Phase 2: Foundational

- [ ] T005 Создать feature-ветку `270-db-indexes-verification` от master (через `./tools/reserve-branch-number.sh`)
- [ ] T006 Создать SQL-миграцию `deploy/karaoke-db/41_db_indexes_verification.sql`:
  - [ ] T006a Header-комментарий с описанием и FR-ссылкой
  - [ ] T006b Apply-блок (локально + прод)
  - [ ] T006c 3× `CREATE INDEX IF NOT EXISTS`:
    - [ ] `tbl_songs_song_author_index` ON public.tbl_songs (song_author)
    - [ ] `tbl_songs_id_status_index` ON public.tbl_songs (id_status)
    - [ ] `tbl_events_song_id_index` ON public.tbl_events (song_id)
- [ ] T007 Создать LiveDoc `livedocs/features/270-db-indexes-verification.md`:
  - [ ] T007a Frontmatter с related: links (parent спека 241, миграция, существующие индексы)
  - [ ] T007b Контекстная находка — индексы уже существуют с `01_initdb.sql`
  - [ ] T007b Effect (гарантия наличия + документирование)
  - [ ] T007c Cross-links на `01_initdb.sql` и `28_rename_settings_to_songs.sql`

## Phase 3: Polish

- [ ] T008 Запустить LiveDocs проверки:
  - [ ] `bash tools/check-livedocs-structure.sh`
  - [ ] `bash tools/check-livedocs-cross-links.sh`
  - [ ] `bash tools/check-livedocs-external-links.sh`
- [ ] T009 Создать PR через `gh pr create --base master`
- [ ] T010 Дождаться `gh pr checks` (CI 7/7 PASS, LiveDocs + Doc)
- [ ] T011 Merge в master (`gh pr merge --merge`, **БЕЗ --delete-branch**)
- [ ] T012 Обновить parent спеку 241:
  - [ ] `specs/241-db-storage-perf-audit/tasks.md` — T012.6 → `[x]`
  - [ ] `livedocs/architecture-notes.md` §Pass 241 — отметить FR-110 как done

## Definition of Done

- [ ] spec.md содержит FR-001..FR-006, Clarifications, Success Criteria, Reference
- [ ] plan.md содержит Implementation Steps + Risks + Constitution Check
- [ ] `deploy/karaoke-db/41_db_indexes_verification.sql` создан (3× CREATE INDEX IF NOT EXISTS)
- [ ] LiveDoc создан в `livedocs/features/270-db-indexes-verification.md`
- [ ] LiveDocs все 3 проверки PASS (structure, cross-links, external-links)
- [ ] PR создан и замержен в master
- [ ] Parent спека 241 обновлена (T012.6 → done, architecture-notes дополнен)

## Notes

- Эта фича — Tier-2 P1 из parent спеки 241, FR-110 (см. spec.md Clarifications Session 2026-08-26).
- **Контекстная находка**: все три индекса уже существуют с `01_initdb.sql:148,173` (исходно
  как `tbl_settings_*_index`, переименованы в `28_rename_settings_to_songs.sql:48,66`).
  На текущем проде миграция — **no-op**. Ценность — документирование + защита от восстановления
  БД из старого дампа.
- **Convention проекта**: имена индексов = `tbl_<table>_<column>_index` (см. `01_initdb.sql`),
  НЕ `idx_*` как в исходной спецификации FR-110. Миграция использует существующие имена.
- **Runtime-валидация** (опционально, делается пользователем после deploy на проде):
  `EXPLAIN ANALYZE` для `GROUP BY song_author WHERE id_status >= 6` и `GROUP BY song_id` —
  должны показывать Index Scan / Bitmap Index Scan, не Seq Scan.
- См. plan.md Risks & Mitigations для деталей про блокировки, CONCURRENTLY и convention имён.