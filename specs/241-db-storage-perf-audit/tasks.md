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
- [ ] T009 Дождаться CI PASS на всех 4 PR + этом parent PR
- [ ] T010 Merge всех 4 Tier-1 PR → master
- [ ] T011 Merge parent PR (246-db-storage-perf-audit) → master
- [ ] T012 Tier-2/Tier-3 hotspots — отдельные фичи (backlog)

## Definition of Done

- [x] Спека содержит каталог hotspots с file:line + severity
- [x] Все 4 Tier-1 P0 фичи реализованы + закоммичены + запушены
- [x] 4 PR созданы
- [ ] 4 PR (и этот parent) → CI 8/8 PASS
- [ ] Merge в master
- [ ] Runtime-валидация (OOM-test для 245, sync-batch для 242, pg_log для 243, webvue3 для 244)