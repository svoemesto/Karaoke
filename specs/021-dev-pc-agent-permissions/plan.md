# Implementation Plan: Unrestricted dev-pc agent permissions

**Branch**: `021-dev-pc-agent-permissions` | **Date**: 2026-07-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/021-dev-pc-agent-permissions/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Add a machine-scoped exception to the agent's permission rules: when a session runs on
host `dev-pc` under OS user `dev`, the agent may rebuild/restart any local project
container (including `karaoke-app`) and perform local-database operations without
pausing for user confirmation. This is a **documentation/governance change**, not an
application feature — there is no source code, API, or UI to build. The technical
approach is: (1) amend `.specify/memory/constitution.md`'s "Ограничения и доступы
агента" section, because it explicitly states it takes priority over `AGENTS.md` on
conflict and its Governance rule 3 classifies "изменение ограничений доступа агента"
as a MAJOR version change requiring a Sync Impact Report; (2) mirror the same exception
in `AGENTS.md`'s "Ограничения агента" section so it stays consistent with the source of
truth; (3) leave every other restriction (server deploy, server file edits, `do.env`,
git-safety rules) untouched.

## Technical Context

<!--
  This feature has no software runtime — it edits two governance/rule documents.
  Fields below are marked N/A where the concept doesn't apply.
-->

**Language/Version**: N/A — Markdown documentation change only, no code

**Primary Dependencies**: N/A

**Storage**: N/A (the feature is *about* local-database permissions, but does not touch schema or data)

**Testing**: Manual verification only — no automated test suite covers `AGENTS.md`/`constitution.md` prose; validation is a human/agent read-through (see quickstart.md)

**Target Platform**: N/A — affects agent behavior when a session's host is `dev-pc` and OS user is `dev` (this sandbox's own identity, confirmed via `hostname`/`whoami`)

**Project Type**: Documentation/governance change (no `src/`, no `frontend`/`backend` split applies)

**Performance Goals**: N/A

**Constraints**: Must not weaken or remove any restriction unrelated to local containers/local DB on `dev-pc`/`dev` (FR-003, FR-004); must not silently widen scope to other machines/users

**Scale/Scope**: Two files (`.specify/memory/constitution.md`, `AGENTS.md`), one section each

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

**This feature's substance IS a constitution amendment — the gate below documents that explicitly rather than treating it as a side effect.**

`constitution.md` § "Ограничения и доступы агента" currently states, as item 1 of
"Категорически запрещено агенту": *«Пересобирать/перезапускать контейнер `karaoke-app`
локально (только пользователь)»* — and the same section's preamble states these rules
**take priority over `AGENTS.md` in case of conflict**. Editing only `AGENTS.md` (as a
literal reading of the spec's FR-001 might suggest) would leave the constitution
still forbidding the exact thing the user asked to unblock, and the constitution wins
that conflict — so the change would be a no-op in practice.

Governance rule 3 in the same file classifies *"изменение ограничений доступа
агента"* as a **MAJOR** semver bump (`X.0.0`), requiring:
- A Sync Impact Report HTML comment at the top of `constitution.md` (added/modified
  principles, templates requiring updates, follow-ups).
- A version bump from the current `1.2.0` to `2.0.0`, with `**Last Amended**` updated.
- A commit message of the form `docs: amend constitution to v2.0.0 (...)` (per
  Governance rule 2) — left to `/speckit-implement` / the user, not this plan.
- Sync obligations (rule 5): update dependent artifacts — here, `AGENTS.md`'s mirrored
  rule, and optionally `docs/architecture-notes.md` per the Phase 002 addendum, since
  this changes the scope of an existing constitutional restriction.

**Gate result**: PASS, with a required action, not a violation to work around — the
plan's Phase 1 design centers on producing the exact constitution edit (including its
Sync Impact Report and version bump) plus the mirrored `AGENTS.md` edit. This is
recorded in Complexity Tracking below per Governance rule 4, since it is a change to
Core constitutional restrictions and must be explicitly justified rather than folded
in silently.

No other Core Principle (I–VII) is implicated: this feature does not touch the
pipeline (I), JDBC/diff code (II), sync registry (III), the job queue (IV), the
two-frontend split (V), or code/doc-coverage standards (VI) — VII (Cross-Machine
Setup) is adjacent (it's about `AGENTS.md`/personal-config handling) but this change
doesn't alter which files are personal vs. shared, so it is unaffected.

## Project Structure

### Documentation (this feature)

```text
specs/021-dev-pc-agent-permissions/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── checklists/
│   └── requirements.md  # Spec quality checklist (/speckit.specify command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

No `contracts/` directory: this feature exposes no API, CLI schema, or UI contract —
it edits prose rules two AI agents (this project's, and future sessions') read at
session start. That prose *is* the contract, and it lives in the two files below.

### Source Code (repository root)

```text
.specify/memory/
└── constitution.md      # § "Ограничения и доступы агента", item 1 (dev-pc/dev exception + MAJOR version bump + Sync Impact Report)

AGENTS.md                # § "Ограничения агента" → "Запрещено"/"Разрешено" (mirrored dev-pc/dev exception)

docs/
└── architecture-notes.md  # Optional: dated entry noting the scope change, per constitution Governance rule 5 sync obligation
```

**Structure Decision**: No `src/`/`frontend`/`backend` split applies — this is a
two-file (plus optional changelog entry) documentation/governance edit. Both files
already exist and already contain the section being amended; no new files are
created except the changelog entry, which follows the existing dated-entry format in
`docs/architecture-notes.md`.

## Complexity Tracking

> Required by Governance rule 4: any code-adjacent change going through
> `/speckit.plan` must record a Core Principle-level effect explicitly, not fold it
> in silently — even when, as here, the effect is intentional and requested by the
> user rather than an accidental violation.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| Amending `constitution.md`'s "Ограничения и доступы агента" (a NON-NEGOTIABLE-priority section) rather than only `AGENTS.md` | The constitution explicitly states it overrides `AGENTS.md` on conflict; the restriction being lifted (`karaoke-app` rebuild/restart) is listed in the constitution itself, not just in `AGENTS.md` | Editing only `AGENTS.md` was rejected because the constitution would still "категорически" forbid the action and would win the conflict — the requested behavior change would not actually take effect |
| MAJOR version bump (`1.2.0` → `2.0.0`) of `constitution.md` | Governance rule 3 classifies any change to agent access restrictions as MAJOR, regardless of how narrow the scope (here: one host/user pair) | A MINOR/PATCH bump was rejected because the rule is unambiguous about the classification — it does not carve out narrow-scope exceptions from the MAJOR bucket |

## Post-Design Constitution Re-check

*Re-evaluated after Phase 1 (data-model.md, quickstart.md; no contracts/ — see Project Structure).*

- The design does not introduce any new mechanism, dependency, or code path — it
  stays within the two-document, prose-only approach validated in Research
  Decision 1. No new violation surfaced during Phase 1.
- `data-model.md`'s "Permission Rule" entity confirms the change is a **scope
  narrowing** (global rule + named exception), not a removal of the underlying
  restriction — consistent with the Constitution Check's framing above.
- `quickstart.md` step 4 (diff-scoped regression check) operationalizes FR-003/FR-004
  and SC-004 — i.e., the plan now has a concrete way to verify the constitution edit
  didn't leak beyond item 1 (and its version/Sync Impact Report preamble).
- **Gate result**: PASS. No entries need to move out of Complexity Tracking, and no
  new ones are needed.
