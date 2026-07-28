# Phase 0 Research: Unrestricted dev-pc agent permissions

No `[NEEDS CLARIFICATION]` markers remain in `spec.md` — the spec's Assumptions
section already resolved the open questions. This document records the small set of
approach decisions made while turning those assumptions into a concrete plan.

## Decision 1: Express the exception as prose in the rule documents, not as executable logic

**Decision**: The `dev-pc`/`dev` exception is written as a plain-language rule in
`constitution.md` and `AGENTS.md` — the same way every other permission rule in
those files is expressed. No config flag, environment variable, or script gate is
introduced.

**Rationale**: Agents read `AGENTS.md` (and, per its own priority statement, the
constitution) as instructions at session start — this is how every other
Ограничения агента rule already works (e.g., "не деплоить на сервер",
"не использовать `nginx:alpine`"). Introducing a machine-detection *mechanism*
would be new infrastructure for a policy question the existing documents already
answer by prose convention.

**Alternatives considered**:
- *Runtime enforcement via a script/hook that checks `hostname`/`whoami` and blocks
  or allows actions programmatically* — rejected: no such enforcement mechanism
  exists today for any of the other permission rules in `AGENTS.md`/`constitution.md`;
  adding one here would be disproportionate infrastructure for a single scoped
  exception, and out of step with how the rest of the document works.
- *A `.env`/config flag (e.g., `AGENT_UNRESTRICTED=true`) read by `do.sh`* —
  rejected: conflates a documentation/policy change with a build-tooling change,
  and do.sh is explicitly a deploy/build tool, not a permissions gate today.

## Decision 2: Amend `constitution.md`, not only `AGENTS.md`

**Decision**: The exception is added to `constitution.md` § "Ограничения и доступы
агента" item 1, with a MAJOR version bump (`1.2.0` → `2.0.0`) and Sync Impact
Report, and mirrored in `AGENTS.md` § "Ограничения агента".

**Rationale**: `constitution.md` states explicitly that its "Ограничения и доступы
агента" section overrides `AGENTS.md` on conflict, and its own Governance rule 3
classifies any change to agent access restrictions as MAJOR. Editing `AGENTS.md`
alone would leave a live contradiction where the higher-priority document still
forbids what the lower-priority one now allows.

**Alternatives considered**:
- *Edit `AGENTS.md` only, since that's the document the spec named* — rejected per
  above: the constitution would win the conflict, making the edit a no-op.
- *Treat this as a MINOR/PATCH bump* — rejected: Governance rule 3's classification
  of "изменение ограничений доступа агента" as MAJOR has no narrow-scope carve-out.

## Decision 3: Scope key is the (hostname, OS user) pair, matched literally

**Decision**: The rule activates only for the exact pair `dev-pc` + `dev` (confirmed
via `hostname`/`whoami` in this environment), not for "any local machine" or "any
user named dev."

**Rationale**: This matches the user's literal request and the spec's Edge Cases
(different host, or same host different user, both fall outside the exception). It
also keeps the admin/production machine's existing restrictions fully intact, since
that machine is not `dev-pc`.

**Alternatives considered**:
- *Scope by "any local/non-production machine"* — rejected: broader than requested,
  and would blur the line with the admin machine, which has its own distinct
  GPU/Demucs/ffmpeg responsibilities and is not what the user named.

## Decision 4: Record the change as a dated entry in `docs/architecture-notes.md`

**Decision**: Add a short dated changelog entry noting the constitution amendment,
consistent with Governance rule 5's sync obligation ("при добавлении нового
принципа обновлять `docs/architecture-notes.md`").

**Rationale**: Rule 5 talks about *new* principles, but this change alters the
scope of an existing one (a MAJOR bump), which is at least as significant; keeping
the changelog in sync avoids future readers finding a version jump in
`constitution.md` with no explanation of why.

**Alternatives considered**:
- *Skip the changelog entry since rule 5's literal trigger is "new principle," not
  "modified principle"* — rejected as the weaker reading; the existing
  `docs/architecture-notes-archive.md` pattern is specifically meant to answer
  "why is it like this," and a MAJOR governance version bump is exactly that kind
  of event.

**Output**: All Technical Context unknowns resolved (none required external
technology research — this is a two-document policy edit). Proceeding to Phase 1.
