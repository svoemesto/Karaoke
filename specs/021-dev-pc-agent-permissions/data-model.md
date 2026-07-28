# Phase 1 Data Model: Unrestricted dev-pc agent permissions

This feature has no application data model (no DB tables, no DTOs). The "entities"
below are the conceptual objects the rule text in `constitution.md`/`AGENTS.md`
refers to — documented here so the Phase 1 wording change is unambiguous about what
it governs.

## Machine Identity

The (hostname, OS user) pair a session runs under.

- **hostname**: string, e.g. `dev-pc`.
- **os_user**: string, e.g. `dev`.
- **Relationship**: a **Permission Rule** exception (below) activates if and only if
  `hostname == "dev-pc"` **and** `os_user == "dev"`. Either mismatching disables the
  exception and the pre-existing restriction applies.

## Local Container

A Docker container in this project's local `docker-compose` stack.

- **name**: one of `karaoke-app`, `karaoke-web`, `webvue3`, `karaoke-public`, plus
  supporting services (db, storage/MinIO, etc.).
- **rebuild/restart authority (today)**: `karaoke-app` → user only; the other three →
  agent, via `deploy/do.sh`.
- **rebuild/restart authority (after this feature, on `dev-pc`/`dev` only)**: all of
  the above → agent, without confirmation.
- **Relationship**: scoped by **Machine Identity** — the widened authority only
  applies when the active session's identity matches `dev-pc`/`dev`.

## Local Database Instance

The Postgres instance running locally for this project (distinct from the
production/server database, which is out of scope — see spec Edge Cases).

- **operations**: queries, migrations, schema changes, data changes.
- **authority (today)**: implicitly cautious — not explicitly named as forbidden in
  `AGENTS.md`/`constitution.md`, but covered by the general "ask before risky/hard to
  reverse actions" guidance.
- **authority (after this feature, on `dev-pc`/`dev` only)**: agent may perform all
  of the above without asking first.
- **Relationship**: scoped by **Machine Identity**, same as Local Container; explicitly
  excludes the production/server database (spec Assumptions).

## Permission Rule

An entry in `constitution.md` § "Ограничения и доступы агента" and/or `AGENTS.md`
§ "Ограничения агента" describing what the agent may or may not do.

- **scope**: global (applies to every session) or machine-scoped (applies only when
  Machine Identity matches a named pair).
- **priority**: `constitution.md` entries override `AGENTS.md` entries on conflict
  (constitution's own stated rule).
- **State transition this feature introduces**: the `karaoke-app` rebuild/restart
  rule and the (previously implicit) local-DB caution move from **global-scope
  forbidden/cautious** to **global-scope forbidden/cautious, with a `dev-pc`/`dev`
  scoped exception** — a narrowing of scope, not a removal of the underlying rule.
