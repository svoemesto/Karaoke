# ADR-0003: LiveDocs = Markdown + YAML frontmatter + Mermaid (not MkDocs/Docusaurus)

* **Status**: Accepted
* **Date**: 2026-08-14 (Phase 62, feature 189-live-documentation)
* **Deciders**: Karaoke team

> **Russian version**: [`../../livedocs/architecture/decisions/0003-livedocs-markdown-yaml-mermaid.md`](../../livedocs/architecture/decisions/0003-livedocs-markdown-yaml-mermaid.md)

## Context

When introducing **LiveDocs** (feature 189) we considered documentation tool
options. Spec 189 § research D-1 documents this decision in detail;
this ADR formalizes it.

**Main requirements**:
1. **Single source of truth** for AI agent (first read document at session start).
2. **CI validation** of structure (blocks merge at structural failures).
3. **Architecture visualization** (C4 diagrams).
4. **Zero runtime** (no SaaS dependencies).
5. **Git-native** (diffs, blame, history).
6. **Minimal cognitive load** for team members (Russian language, Markdown familiar).

## Decision

We use **Markdown + YAML frontmatter + Mermaid** in the directory `livedocs/`
(see [`livedocs/README.md`](../../livedocs/README.md)).

**Structure**:
```
livedocs/
├── README.md             # Manifesto
├── INDEX.md              # Layer map + decision tree for AI
├── features/             # SDD: feature summaries (≤ 2 pages)
├── domain/               # DDD: bounded contexts + Ubiquitous Language
├── architecture/         # C4 + topic-documents
├── templates/             # Templates for new entries
└── decisions/             # ADRs (this file)
```

**Each LiveDoc format**:
- YAML frontmatter (3 fields): `status`, `slug`, `related` (optionally `type`, `level`).
- Body in Markdown (CommonMark).
- Mermaid-blocks for diagrams (GitHub renders automatically).

**CI validation**: `tools/check-livedocs-structure.sh` (7 checks, exit ≠ 0
blocks merge).

## Consequences

**Positive**:
- **Git-native**: `git diff`, `git blame`, `git log --follow` work without tools.
- **Zero runtime**: no dependencies (no MkDocs Python, no Docusaurus Node.js).
- **CI validation without pain**: POSIX bash + `head/grep/wc/find/test` — no
  parsers.
- **AI agent reads first**: rule in `AGENTS.md` (1 page manifesto + 1 page INDEX).
- **Versioned through git**: links between LiveDocs relative
  (`../domain/catalog.md`), not broken on rebuild.
- **Language — Russian**: corresponds to `AGENTS.md` "ABSOLUTE RULE".
- **Deletion/rename without complications**: `git mv`, link checking via
  `lychee --offline`.
- **Markdown familiar**: ktlint/ESLint/PRETTIER already support it.
- **Reduction in start-session tokens**: from ~40K to ≤ 5K (SC-001 of spec 189).

**Negative**:
- **No search in LiveDocs** (need `grep` or manual traversal).
- **No auto-generation of cross-links** (CI-grep on `../`).
- **No template inheritance** (but `templates/` for new entries).
- **CI-script is manual**: `tools/check-livedocs-structure.sh` — if new layer
  added, need to update script.
- **No versioning of LiveDocs** (semver not applied; version is git).
- **Mermaid diagrams** not full C4 (no native C4 notation; see
  [`livedocs/architecture/c4-level-template.md`](../../livedocs/templates/c4-level-L1.md)).

**Neutral**:
- AI agent is trained to read Markdown well; no special training needed.
- External readers (if they appear) see plain MD — not critical for project.

## Alternatives Considered

- **MkDocs (Python) + Material theme**: rejected — Python dependency, build step,
  no native CI structure validation.
- **Docusaurus (Node.js)**: rejected — Node dependency, heavy (no native C4),
  overkill for internal documentation.
- **Hugo (binary)**: rejected — binary build, heavy, poorly versioned through git.
- **Antora (multi-repo)**: rejected — for multi-repo, we have one.
- **AsciiDoc**: rejected — less common than Markdown.
- **PlantUML**: rejected — requires Java-runtime + Graphviz, Mermaid renders
  on client.
- **Confluence / Notion / external SaaS**: rejected — violates Constitution § I
  (self-contained).

## References

- Spec 189-live-documentation § research D-1 — detailed rationale
  (10 design decisions).
- [`livedocs/README.md`](../../livedocs/README.md) — root manifesto of LiveDocs.
- [`livedocs/INDEX.md`](../../livedocs/INDEX.md) — map + decision tree.
- [AGENTS.md](../../../AGENTS.md) — "AI agent at session start reads LiveDocs
  first" rule.
- [Constitution § I](.specify/memory/constitution.md) — self-contained.