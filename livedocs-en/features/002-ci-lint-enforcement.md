---
status: Active
slug: 002-ci-lint-enforcement
related:
  - ../features/001-code-standards-docs.md
  - ../architecture/L1-system-context.md
  - ../../specs/002-ci-lint-enforcement/spec.md
---

# 002 — CI lint enforcement (GitHub Actions) (LiveDoc)

> Drill-down — [specs/002-ci-lint-enforcement/spec.md](../../specs/002-ci-lint-enforcement/spec.md).

## What it does

GitHub Actions workflow `.github/workflows/lint.yml` runs linters on every
push to master and every PR to master:

- **ktlint** (Kotlin/Java)
- **ESLint + Prettier** (Vue/JS/TS) for `webvue3` and `karaoke-public`
- **lychee** (offline links check) for `docs/features/` + `CONTRIBUTING.md`
- **per-feature document structure** (`tools/check-feature-doc.sh`)
- **Baseline stats** (informational)
- **KDoc coverage** (strict, ≥50%)
- **JSDoc coverage** (strict, ≥50%)
- **LiveDocs structure** (7/7 checks)
- **LiveDocs cross-links** (818 checks, 0 broken)

**Main rule (governance)** — see `AGENTS.md` "CI-gate for master": direct
pushes to master are forbidden, every change goes through feature-branch +
PR + CI 8/8 (was 7/7 before LiveDocs jobs added).

Depends on Phase 001 (`001-code-standards-docs`).

## User Stories

- **US1** (P1): PR is blocked if any check fails.

## Functional Requirements (pointer)

- **FR-001**: `.github/workflows/lint.yml` — 9 jobs (8 was 7 before LiveDocs).
- **FR-002**: Branch protection rules — require "Lint / Lint 8/8 SUCCESS" for merge.

## Acceptance Criteria

- [ ] **AC1**: PR with lint error is blocked (merge unavailable).
- [ ] **AC2**: PR after fix can be merged.

## Related LiveDocs

- Architecture: [L1-system-context.md](../architecture/L1-system-context.md) (GitHub Actions)
- Feature: `001-livedocs-ci-gate.md` (LiveDocs job added)

## Code

- `.github/workflows/lint.yml` — 9 jobs
- Branch protection rules (on GitHub side): admin requires 8/8 SUCCESS for merge

## History

- Created: 2026-08-14
- Last updated: 2026-08-14