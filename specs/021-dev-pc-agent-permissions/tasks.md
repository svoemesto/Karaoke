---

description: "Task list template for feature implementation"
---

# Tasks: Unrestricted dev-pc agent permissions

**Input**: Design documents from `/specs/021-dev-pc-agent-permissions/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Tests**: Not requested in spec.md — no automated test suite covers `AGENTS.md`/`constitution.md` prose, so no test tasks are generated. Verification is manual, via quickstart.md.

**Organization**: Tasks are grouped by user story (US1 = container rebuild/restart, US2 = local database operations) per spec.md.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2)
- Exact file paths are included in every task description

## Path Conventions

This feature has no `src/`/`tests/` tree — it edits two existing governance/rule
files at the repository root and (optionally) a changelog file:

- `.specify/memory/constitution.md`
- `AGENTS.md`
- `docs/architecture-notes.md` (changelog entry only)

---

## Phase 1: Setup

**Purpose**: Put the session in a state where the rest of the tasks can proceed safely

- [X] T001 Create and switch to git branch `021-dev-pc-agent-permissions` from `master` (per project git workflow — no direct commits to `master`)
- [X] T002 [P] Confirm this session's machine identity matches the feature's scope target: run `hostname` (expect `dev-pc`) and `whoami` (expect `dev`), per quickstart.md step 1

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared reconnaissance both user stories depend on — locating the exact
anchors both stories will edit, so US1 and US2 land in the right place without
re-deriving it and without stepping on each other's edits in the same files

**⚠️ CRITICAL**: No user story edit can begin until this phase is complete

- [X] T003 Locate and record the exact edit anchors: `.specify/memory/constitution.md` § "Ограничения и доступы агента" → item 1 of "Категорически запрещено агенту" (the `karaoke-app` rebuild/restart line, and the surrounding "Разрешено агенту" list), and `AGENTS.md` § "Ограничения агента" → "Запрещено"/"Разрешено" lists (the mirrored `karaoke-app` line)

**Checkpoint**: Foundation ready — user story edits can now begin

---

## Phase 3: User Story 1 - Rebuild/restart any local container without asking (Priority: P1) 🎯 MVP

**Goal**: On a session running on host `dev-pc` under OS user `dev`, the agent may rebuild/restart any local project container — including `karaoke-app` — without pausing for confirmation.

**Independent Test**: On a `dev-pc`/`dev` session, ask the agent to rebuild and restart `karaoke-app` after a code change. Passes if the agent does so directly, with no permission pause, and a session on any other host/user still pauses as before.

### Implementation for User Story 1

- [X] T004 [P] [US1] In `.specify/memory/constitution.md` § "Ограничения и доступы агента", amend item 1 of "Категорически запрещено агенту" (currently *«Пересобирать/перезапускать контейнер `karaoke-app` локально (только пользователь)»*) to add a `dev-pc`/`dev`-scoped exception: on that exact host+OS-user pair, the agent may rebuild/restart `karaoke-app` (and every other local project container) without asking; on every other host/user the existing restriction is unchanged
- [X] T005 [P] [US1] In `AGENTS.md` § "Ограничения агента", mirror the same exception: the "Запрещено" line reserving `karaoke-app` rebuild/restart to the user gets the `dev-pc`/`dev` carve-out, and the "Разрешено" list's `karaoke-web`/`webvue3`/`karaoke-public` entry is explicitly noted as already covered plus now joined by `karaoke-app` on `dev-pc`/`dev`
- [X] T006 [US1] Verify via quickstart.md steps 2-3 (`grep -n "dev-pc" .specify/memory/constitution.md` and `grep -n "dev-pc" AGENTS.md`) that the container-exception wording landed in both files and reads consistently between them (depends on T004, T005)

**Checkpoint**: User Story 1 is independently functional and testable — this alone is a shippable MVP increment.

---

## Phase 4: User Story 2 - Work with the local database without asking (Priority: P1)

**Goal**: On the same `dev-pc`/`dev` session, the agent may query, migrate, or otherwise modify the local database instance without pausing for confirmation.

**Independent Test**: On a `dev-pc`/`dev` session, ask the agent to run a schema migration or a data-modifying query against the local database. Passes if the agent runs it directly, with no permission pause.

### Implementation for User Story 2

- [X] T007 [US2] In `.specify/memory/constitution.md` § "Ограничения и доступы агента", add a `dev-pc`/`dev`-scoped statement that local-database operations (queries, migrations, schema/data changes) against the local Postgres instance require no confirmation on that host+OS-user pair — explicitly excluding the production/server database, which remains governed by the existing "Деплой на сервер ... прямые DDL/DML к серверной БД" restriction (depends on T004 for consistent wording/placement; same file, sequential — not `[P]` with T004)
- [X] T008 [US2] In `AGENTS.md` § "Ограничения агента", mirror the same local-database exception, again excluding the production/server database (depends on T005 for consistent wording/placement; same file, sequential — not `[P]` with T005)
- [X] T009 [US2] Verify via quickstart.md steps 2-3 that the database-exception wording is present in both files and does not reference or loosen anything about the production/server database (depends on T007, T008)

**Checkpoint**: User Stories 1 AND 2 both work independently — both P1 stories are now complete.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Governance bookkeeping the constitution itself requires for this kind of change, plus the regression/positive validation from quickstart.md

- [X] T010 [P] Add/update the Sync Impact Report HTML comment at the top of `.specify/memory/constitution.md`, summarizing both changes (item 1 exception + local-database exception) — version change, modified principle, templates checked (depends on T004, T007 being final)
- [X] T011 Bump the `**Version**` line in `.specify/memory/constitution.md` from `1.2.0` to `2.0.0` and update `**Last Amended**` to the current date, per Governance rule 3 ("изменение ограничений доступа агента" = MAJOR) (depends on T010, same file — sequential)
- [X] T012 [P] Add a dated changelog entry to `docs/architecture-notes.md` documenting the constitution amendment (host/user-scoped exception for container rebuild/restart + local DB ops), per Governance rule 5's sync obligation (depends on T004, T007 being final; different file — `[P]` relative to T010/T011)
- [X] T013 Run quickstart.md step 4 (`git diff --stat .specify/memory/constitution.md AGENTS.md` and `git diff` on both) to confirm every change is confined to the intended sections plus the constitution's version/Sync Impact Report preamble — no unrelated line changed (SC-004) (depends on T006, T009, T011)
- [X] T014 Run quickstart.md steps 5-6 live validation in a `dev-pc`/`dev` session: ask the agent to rebuild/restart a container and to run a local database operation; confirm neither triggers a confirmation pause (SC-001, SC-002) (depends on T013)
- [X] T015 Run quickstart.md step 7 text review: confirm the new rule text names the exact `dev-pc` + `dev` pair and does not broaden to "any local machine" or "any user" (SC-003) (depends on T013)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories
- **User Story 1 (Phase 3)**: Depends on Foundational; independent of US2
- **User Story 2 (Phase 4)**: Depends on Foundational; edits the same two files as US1, so its file-editing tasks (T007, T008) run *after* US1's (T004, T005) to avoid clobbering each other's diffs, even though the stories are conceptually independent
- **Polish (Phase 5)**: Depends on both user stories' content edits being final (it computes the version bump and diff over the finished text)

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) — no dependency on US2
- **User Story 2 (P1)**: Can start after Foundational (Phase 2) conceptually, but its edits touch the same file sections as US1, so in practice it is sequenced after US1's edits land (T004/T005) to keep the diff clean — this is a same-file ordering constraint, not a logical dependency between the two rules

### Within Each User Story

- Edit `constitution.md` and `AGENTS.md` (parallel across files within US1; sequential across stories within each file)
- Verify wording landed correctly before moving to the next story/phase

### Parallel Opportunities

- T001 and T002 (Setup) can run in parallel
- T004 and T005 (US1, different files) can run in parallel
- T010 and T012 (Polish, different files) can run in parallel
- T007/T008 (US2) and T011 (Polish version bump) touch `constitution.md`/`AGENTS.md` at different times in the sequence and are **not** parallel with each other or with T004/T005

---

## Parallel Example: User Story 1

```bash
# Launch both file edits for User Story 1 together (different files, no conflict):
Task: "Amend constitution.md item 1 with the dev-pc/dev container exception"
Task: "Mirror the same exception in AGENTS.md"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1 (container rebuild/restart exception)
4. **STOP and VALIDATE**: Run quickstart.md steps 2-3 and 5 for the container scenario only
5. This alone is a shippable increment: the single most disruptive restriction (`karaoke-app`) is lifted on `dev-pc`/`dev`

### Incremental Delivery

1. Setup + Foundational → ready
2. User Story 1 → validate → this is the MVP
3. User Story 2 (local database exception) → validate
4. Polish (version bump, Sync Impact Report, changelog, full regression + live validation) → done

### Solo-Agent Strategy

Both stories touch the same two files, so — unlike a typical multi-developer
feature — there is little value in parallelizing across stories here. The
realistic execution order is: Setup → Foundational → US1 (T004, T005 in parallel)
→ US1 verification → US2 (T007, T008 in parallel) → US2 verification → Polish.

---

## Notes

- `[P]` tasks touch different files and have no completed-work dependency on each other
- `[Story]` label maps task to specific user story for traceability
- This is a documentation/governance change (per plan.md) — "commit after each
  task" from the generic template guidance is superseded by this project's rule:
  do not commit without an explicit user request, and never commit directly to
  `master` (T001 already moves work onto the feature branch)
- Avoid: editing `constitution.md` and `AGENTS.md` out of the T004→T007 / T005→T008
  order, which would produce inconsistent wording between the two files at any
  intermediate point
