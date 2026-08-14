---
status: Active
slug: 001-code-standards-docs
related:
  - ../features/002-ci-lint-enforcement.md
  - ../architecture/documentation-conventions.md
  - ../../specs/001-code-standards-docs/spec.md
---

# 001 — Code standards + per-feature documentation (Phase 001) (LiveDoc)

> Drill-down — [specs/001-code-standards-docs/spec.md](../../specs/001-code-standards-docs/spec.md).

## What it does

Phase 001 (2026-07-20, PR #12) — a big effort to bring the project to
production-grade state:

1. **Linters**:
   - ktlint for Kotlin.
   - ESLint + Prettier for Vue/JS/TS.
   - Baselines (~30k initial problems, reduction rate ≥10%/month).
2. **Documentation**:
   - KDoc coverage ≥ 50% for public classes (NON-NEGOTIABLE, FR-006).
   - JSDoc coverage ≥ 50% (Vue 3 + typedoc).
3. **Per-feature documents** in `docs/features/<slug>.md` (FR-009).
4. **HTML documentation**: Dokka (`docs/api/dokka/`) and typedoc.
5. **CI** (see `002-ci-lint-enforcement`).
6. **Constitution** v2.0.0 (semver MINOR amend).

## User Stories

- **US1** (P1): Developer understands code standards.
- **US2** (P1): Per-feature document describes the feature.

## Functional Requirements (pointer)

- **FR-006**: KDoc/JSDoc on public APIs with `@see` link to per-feature document.
- **FR-007**: Linters in pre-commit + CI.
- **FR-009**: On code change — update per-feature document.

## Acceptance Criteria

- [ ] **AC1**: Linters in pre-commit hooks.
- [ ] **AC2**: KDoc coverage ≥ 50% (baseline).
- [ ] **AC3**: Per-feature documents for key features.

## Related LiveDocs

- Feature: [002-ci-lint-enforcement.md](../features/002-ci-lint-enforcement.md)
- Architecture: [documentation-conventions.md](../architecture/documentation-conventions.md)
- This is **Phase 001** — the base for everything (Phase 002 cross-machine, Phase 003+).
- Architecture: [L1-system-context.md](../architecture/L1-system-context.md)

## Code

- `.pre-commit-config.yaml`
- `config/ktlint/baseline-*.xml`
- `webvue3/.eslint-baseline.json`, `karaoke-public/.eslint-baseline.json`
- `docs/api/dokka/`, `docs/api/typedoc-*/`
- `tools/check-kdoc-coverage.sh`, `tools/check-jsdoc-coverage.sh`
- `tools/check-feature-doc.sh`

## History

- Created: 2026-08-14
- Last updated: 2026-08-14