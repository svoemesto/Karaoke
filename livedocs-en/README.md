# LiveDocs — Root Manifest

> **Status**: Active (as of 2026-08-14, Pass 62)
> **Branch**: `189-live-documentation` (kept alive for follow-ups)
>
> **Russian version of this file**: [`livedocs/README.md`](../livedocs/README.md)

## What is LiveDocs

**LiveDocs** is the single source of truth for project knowledge for AI agents
and developers. It combines three approaches in one framework:

| Approach | What it gives | Artifact |
|----------|---------------|----------|
| **SDD** (Specification-Driven Development) | Features as specs | `livedocs/features/<NNN-slug>.md` |
| **DDD** (Domain-Driven Design) | Ubiquitous language, bounded contexts | `livedocs/domain/<context>.md` |
| **C4** (Context → Container → Component → Code) | Architecture diagrams | `livedocs/architecture/<L|n>-*.md` + Mermaid |

**Main goal**: reduce AI agent startup session context from ~40K tokens
to ≤ 5K (SC-001).

## Where to start

1. Read this file (`README.md`) — purpose + navigation.
2. Open [`INDEX.md`](INDEX.md) — layer map + decision tree.
3. Navigate to the right layer by task:
   - **Feature task** ("what does feature X do?") → `features/`.
   - **Module/domain task** ("what is Song?") → `domain/`.
   - **Architecture task** ("how does it work?") → `architecture/`.
   - **Process task** ("how do I deploy?") → `runbooks/`.
   - **Recent changes** ("what's new?") → `CHANGELOG.md`.

## Layers

| Layer | Purpose | Catalog | Example |
|-------|---------|---------|---------|
| **SDD** | Feature summaries (≤ 2 pages) | [`features/`](../livedocs/features/) | [`features/182-editor-self-assign-tasks.md`](../livedocs/features/182-editor-self-assign-tasks.md) |
| **DDD** | Bounded contexts + ubiquitous language | [`domain/`](../livedocs/domain/) | [`domain/catalog.md`](../livedocs/domain/catalog.md) |
| **C4** | Architecture diagrams (L1/L2/L3) | [`architecture/`](../livedocs/architecture/) | [`architecture/L1-system-context.md`](../livedocs/architecture/L1-system-context.md) |
| **Templates** | Templates for new entries | [`templates/`](../livedocs/templates/) | [`templates/feature-summary.md`](../livedocs/templates/feature-summary.md) |
| **Runbooks** | Operational how-to guides | [`runbooks/`](../livedocs/runbooks/) | [`runbooks/how-to-deploy.md`](../livedocs/runbooks/how-to-deploy.md) |
| **ADR** | Architecture Decision Records | [`architecture/decisions/`](../livedocs/architecture/decisions/) | [`architecture/decisions/0001-raw-jdbc.md`](../livedocs/architecture/decisions/0001-raw-jdbc.md) |

## Main rule

**AI agent at session start reads `livedocs/README.md` + `livedocs/INDEX.md` FIRST.**
Only if LiveDocs lacks needed information — read `specs/NNN-*/spec.md`
(full specs), `docs/features/*.md` (legacy drill-down), or `AGENTS.md`
(governance).

## Canonical naming

- **LiveDocs** (the system/catalog, CamelCase, with `s`).
- **LiveDoc** (a single document, CamelCase, no `s`).
- Directories: lowercase (`livedocs/`).
- Headers/entities: CamelCase.

## Conventions (brief)

- File names: kebab-case without numbering (for `domain/`, `architecture/`).
  Exception — `features/<NNN-slug>.md` where NNN is the spec number.
- Frontmatter (YAML): `status`, `slug`, `related` (optional: `type`, `level`).
- Size:
  - Feature summary: ≤ 2 pages (≤ 80 lines).
  - Bounded context: ≤ 3 pages (≤ 120 lines).
  - C4 level: ≤ 2 pages (≤ 80 lines).
  - Topic: ≤ 3 pages (≤ 120 lines).
  - README manifesto: ≤ 30 lines.

## Update process

- Code change → update corresponding LiveDoc in the same PR (FR-014).
- New feature → create `features/<NNN-slug>.md` from template.
- CI gate: `bash tools/check-livedocs-structure.sh` runs on every PR, exit ≠ 0 blocks merge.

## Validation

```bash
bash tools/check-livedocs-structure.sh
```

Checks: required directories, ≥ 5 features, ≥ 5 contexts, L1/L2/L3 C4,
frontmatter on each LiveDoc, `AGENTS.md` ≤ 100 lines, CI integration.

## Drill-down

- Full specs: [`../specs/`](../specs/).
- Governance & pitfalls: [`../AGENTS.md`](../AGENTS.md).
- Per-feature docs (legacy): [`../docs/features/`](../docs/features/).