# Quickstart: Validate the dev-pc agent-permissions exception

Prerequisites: `constitution.md` and `AGENTS.md` have been edited per `tasks.md`
(Phase 2, not yet generated as of this writing).

## 1. Confirm this environment is in scope

```bash
hostname   # expect: dev-pc
whoami     # expect: dev
```

If either differs, the exception described below does not apply here — stop and
treat this as "any other machine" (see spec Edge Cases).

## 2. Confirm the constitution amendment landed correctly

```bash
grep -n "dev-pc" .specify/memory/constitution.md
grep -n "^\*\*Version\*\*" .specify/memory/constitution.md   # expect 2.0.0, Last Amended = today
```

Expected: the "Ограничения и доступы агента" § item 1 (`karaoke-app` rebuild/restart)
now carries a `dev-pc`/`dev` exception; version line reads `2.0.0`; a Sync Impact
Report HTML comment at the top of the file documents the change (added/modified
principle, version bump reason).

## 3. Confirm `AGENTS.md` mirrors the exception

```bash
grep -n "dev-pc" AGENTS.md
```

Expected: § "Ограничения агента" → "Запрещено" no longer unconditionally reserves
`karaoke-app` rebuild/restart to the user — it now states the `dev-pc`/`dev`
exception, and the DB-operations exception is present too.

## 4. Confirm everything else is untouched (regression check)

```bash
git diff --stat .specify/memory/constitution.md AGENTS.md
git diff .specify/memory/constitution.md AGENTS.md
```

Expected: diffs are limited to the "Ограничения и доступы агента" /
"Ограничения агента" sections (plus the constitution's version line and Sync Impact
Report comment). No unrelated line should change — confirms SC-004.

## 5. Positive scenario — container rebuild/restart (User Story 1)

In a live agent session on this machine, ask the agent to rebuild and restart
`karaoke-app` after a trivial code change (or a no-op touch).

**Expected**: the agent runs the rebuild/restart directly — no confirmation prompt
for that specific action. (SC-001)

## 6. Positive scenario — local database operation (User Story 2)

Ask the agent to run a harmless read query (or a reversible local migration) against
the local database.

**Expected**: the agent runs it directly — no confirmation prompt for that specific
action. (SC-002)

## 7. Negative scenario — scope does not leak

Review the new rule text and confirm it names the exact `dev-pc` + `dev` pair, not
"any local machine" or "any user." (SC-003 — cannot be mechanically tested without a
second machine/user, so this step is a text-review check instead.)
