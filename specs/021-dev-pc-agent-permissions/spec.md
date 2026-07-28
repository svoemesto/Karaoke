# Feature Specification: Unrestricted dev-pc agent permissions

**Feature Branch**: `021-dev-pc-agent-permissions`

**Created**: 2026-07-28

**Status**: Draft

**Input**: User description: "Внеси изменения в правила для агентов: если агент работает нам компьютере dev-pc под пользователем dev то ограничений на пересборку/перезапуск контейнеров на этой машине у него нет, точно так же как и на работу с базой данных на этой машине, дополнительные разрешения у пользователя спрашивать не надо."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Rebuild/restart any local container without asking (Priority: P1)

An AI coding agent is working in a session running on host `dev-pc` under OS user `dev`. The agent needs to rebuild and/or restart a project container (including `karaoke-app`, which today is explicitly reserved for the human user) to pick up a code change. The agent proceeds directly, without pausing to request the user's confirmation.

**Why this priority**: This is the exact restriction the user called out — `karaoke-app` rebuild/restart is currently the one container explicitly reserved for the human. Lifting it on `dev-pc`/`dev` removes the most disruptive back-and-forth in the agent's day-to-day loop on this machine.

**Independent Test**: On a session identified as `dev-pc`/`dev`, ask the agent to rebuild and restart `karaoke-app` (or any other project container) after a code change. Test passes if the agent runs the rebuild/restart directly and does not stop to ask permission first.

**Acceptance Scenarios**:

1. **Given** an agent session on host `dev-pc` under user `dev`, **When** the agent needs to rebuild/restart `karaoke-app` locally, **Then** it does so without pausing for user confirmation.
2. **Given** an agent session on host `dev-pc` under user `dev`, **When** the agent needs to rebuild/restart `karaoke-web`, `webvue3`, or `karaoke-public` locally, **Then** it does so without pausing for user confirmation (already allowed today, must remain allowed).

---

### User Story 2 - Work with the local database without asking (Priority: P1)

The same agent, in the same `dev-pc`/`dev` session, needs to query, migrate, or otherwise modify the local database instance used by the project. The agent proceeds directly, without pausing to request confirmation first.

**Why this priority**: Equal in scope and intent to User Story 1 — the user named container operations and database operations together as the two categories to unblock on this machine.

**Independent Test**: On a session identified as `dev-pc`/`dev`, ask the agent to run a schema migration or a data-modifying query against the local database. Test passes if the agent runs it directly without a permission pause.

**Acceptance Scenarios**:

1. **Given** an agent session on host `dev-pc` under user `dev`, **When** the agent needs to run a query, migration, or schema change against the local database, **Then** it does so without pausing for user confirmation.

---

### Edge Cases

- What happens when the agent is running on a different machine (e.g., the admin/production machine, or a developer's laptop) or under a different OS user? The existing restrictions (container rebuild/restart, database caution) continue to apply unchanged — this exception is scoped strictly to the `dev-pc` host + `dev` user combination.
- What happens when the agent is on host `dev-pc` but under a different OS user? The exception does not apply; standard restrictions remain in effect.
- What happens with actions this exception does not name — deploying to the production server, editing files directly on the server, overwriting `do.env` on the server, force-pushing to `master`? These remain governed by the existing rules in `AGENTS.md`, unchanged by this feature.
- What happens with the production database (reached via the production server, `79.174.95.69`)? Out of scope — this exception covers only the local database instance reachable on `dev-pc`, not the remote/production database.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The agent rules documentation (`AGENTS.md`, "Ограничения агента" section) MUST state that when a session is running on host `dev-pc` under OS user `dev`, the agent may rebuild and/or restart any local project container — including `karaoke-app` — without asking the user for confirmation first.
- **FR-002**: The same documentation MUST state that on `dev-pc`/`dev`, the agent may perform database operations (queries, migrations, schema or data changes) against the local database instance without asking the user for confirmation first.
- **FR-003**: The documentation MUST make explicit that this exception is scoped to the `dev-pc` host + `dev` user combination only — it does not apply to any other machine (e.g., the admin/production machine) or to any other OS user, where the pre-existing restrictions continue to apply verbatim.
- **FR-004**: The documentation MUST make explicit that all other existing restrictions unrelated to local containers/database — production deployment, editing files directly on the server, overwriting `do.env` on the server, and general git-safety rules (no direct commits to `master`, no `--no-verify`, no force-push to `master`) — remain unchanged and continue to apply on `dev-pc` as everywhere else.
- **FR-005**: The updated rule MUST live in the same section agents already read as part of the mandatory session-start checklist (`AGENTS.md`), so it is picked up automatically rather than requiring a separate lookup.

### Key Entities

- **Machine identity**: The (hostname, OS user) pair an agent session runs under; the exception activates only for the exact pair (`dev-pc`, `dev`).
- **Local container**: Any Docker container in this project's local `docker-compose` stack (`karaoke-app`, `karaoke-web`, `webvue3`, `karaoke-public`, database, storage, etc.) reachable and rebuildable from `dev-pc`.
- **Local database instance**: The Postgres instance running locally on `dev-pc` for this project, as distinct from the production/server database.
- **Permission rule**: An entry in `AGENTS.md`'s "Ограничения агента" section describing what the agent may or may not do without asking; this feature adds a machine-scoped exception to two existing entries.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a session identified as `dev-pc`/`dev`, 100% of container rebuild/restart actions (including `karaoke-app`) complete without a permission-confirmation pause, verified by reviewing the session transcript.
- **SC-002**: In a session identified as `dev-pc`/`dev`, 100% of local database operations complete without a permission-confirmation pause, verified by reviewing the session transcript.
- **SC-003**: In a session on any other machine or OS user, container rebuild/restart and database operations continue to trigger the existing confirmation behavior — i.e., the exception does not leak beyond its scope.
- **SC-004**: After the change, every pre-existing restriction not related to local containers/database (server deploy, server file edits, `do.env` handling, git-safety rules) is still present and worded the same in `AGENTS.md`, confirmed by diff review.

## Assumptions

- "dev-pc" refers to the machine whose hostname is `dev-pc` and whose OS user is `dev` — this matches the environment this session is currently running in.
- The exception covers only this project's local `docker-compose` containers and the local database instance on `dev-pc` — it does not extend to the production server (`79.174.95.69`) or its database, which remain governed by the existing "Разрешено/Запрещено" and "Деплой" rules.
- "No need to ask for additional permission" is scoped to the two named categories (container rebuild/restart, database operations) on `dev-pc`/`dev`. It does not exempt the agent from unrelated safety practices already documented in `AGENTS.md` (branch-only commits, no `--no-verify`, no force-push to `master`, no direct server file edits, etc.).
- This is a documentation-only change to `AGENTS.md`'s "Ограничения агента" section (and any directly cross-referencing rule text); no application code changes are required.
- The existing line "Пересобирать/перезапускать контейнер `karaoke-app` локально — делает только пользователь" is superseded by a `dev-pc`/`dev`-scoped exception; the general restriction remains the default for every other host/user combination.
