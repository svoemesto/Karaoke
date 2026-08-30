---
description: "Task list для parent спеки 241 — каталог hotspots (не реализует код)"
---

# Tasks: Аудит производительности БД и хранилища (prod) — parent

**Input**: Design documents from `/specs/241-db-storage-perf-audit/`
- plan.md (required)
- spec.md (required for user stories)

**Note**: Эта спека — **parent (каталог)**, не реализует код. Все Tier-1 P0 фичи реализованы как отдельные дочерние спеки.

## Phase 1: Setup

- [x] T001 Создать parent спеку с FR-001..FR-110 + каталогом hotspots в Приложении A
- [x] T002 Провести `/speckit.clarify` (Session 2026-08-26): scope Q1=все 4 P0, baseline Q2=без pg_stat_statements, валидация Q3=pre/post pg_log

## Phase 2: Foundational

- [x] T003 Создать отдельные спеки для каждой Tier-1 P0 фичи:
  - [x] specs/242-db-sync-batch-worker/ (FR-101)
  - [x] specs/243-db-table-schema-cache/ (FR-102)
  - [x] specs/244-songs-createkaraokeall-batch/ (FR-103)
  - [x] specs/245-storage-download-streaming/ (FR-104)
- [x] T004 Реализовать 4 Tier-1 фичи (через параллельных агентов в worktree'ах)
- [x] T005 Создать 4 PR (PR #364, #365, #366, #367)
- [x] T006 Починить CI gates (ktlint baseline, AGENTS.md ≤100, prettier, LiveDoc'и)

## Phase 3: Polish

- [x] T007 LiveDoc для parent спеки — `livedocs/features/241-db-storage-perf-audit.md`
- [x] T008 LiveDoc для каждой Tier-1 фичи (242/243/244/245)
- [x] T009 CI PASS на всех 4 PR + этом parent PR (PR #364-#368 — все MERGED 2026-08-26)
- [x] T010 Merge всех 4 Tier-1 PR → master (PR #364, #365, #366, #367 — MERGED)
- [x] T011 Merge parent PR (246-db-storage-perf-audit) → master (PR #368 — MERGED 2026-08-26T21:08Z)
- [x] T011a Документация Pass 241 в CHANGELOG + architecture-notes (PR #369 — MERGED)
- [x] T012 Tier-2/Tier-3 hotspots — отдельные фичи (backlog)
  - [x] T012.1 **FR-105** — кеш `/api/public/authors-tiles` (PR #370 — MERGED, спека `248-*.md`)
  - [x] T012.2 **FR-106** — кеш `PublicSettingsWebController.getProperty` (PR #386 — MERGED, спека `249-*.md`)
  - [ ] T012.3 **FR-107** — ограничить `limit=100_000` в Thymeleaf `/statbysong`
  - [ ] T012.4 **FR-108** — `pg_stat_statements` (в backlog, deferred из Clarifications 2026-08-26)
  - [ ] T012.5 **FR-109** — batch INSERT для `tbl_events`
  - [x] T012.6 **FR-110** — индексы `tbl_songs_song_author_index`, `tbl_songs_id_status_index`,
        `tbl_events_song_id_index` (PR #387 — MERGED, спека `270-*.md`). Контекстная находка:
        индексы уже созданы в `01_initdb.sql:148,173` — миграция no-op на текущем проде,
        ценность — документирование + защита от восстановления БД из старого дампа.

## Definition of Done

- [x] Спека содержит каталог hotspots с file:line + severity
- [x] Все 4 Tier-1 P0 фичи реализованы + закоммичены + запушены
- [x] 4 PR созданы и замержены (PR #364, #365, #366, #367)
- [x] Parent PR замержен (PR #368)
- [x] LiveDocs созданы для всех 5 спек (241, 242, 243, 244, 245)
- [x] CHANGELOG + architecture-notes обновлены (PR #369)
- [x] Tier-2 фича FR-105 реализована и замержена (PR #370)
- [ ] Runtime-валидация (OOM-test для 245, sync-batch для 242, pg_log для 243, webvue3 для 244)
- [ ] Остальные Tier-2/Tier-3 hotspots — backlog