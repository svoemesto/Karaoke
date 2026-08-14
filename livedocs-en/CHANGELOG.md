# LiveDocs — Changelog

> **Russian version (canonical)**: [`livedocs/CHANGELOG.md`](../livedocs/CHANGELOG.md)
>
> Semantic changelog (as opposed to `git log`) — **what is currently
> described** in LiveDocs. Useful for AI agent to understand current state.

## Conventions

- **One section = one PR**. Sections retroactively assembled from known
  history; going forward, each LiveDocs change appends a new section.
- See also: `git log livedocs/` for line-by-line history.

---

## 2026-08-14 — bootstrap + 80 features

- Created `livedocs/` with all layers (`features/`, `domain/`, `architecture/`,
  `templates/`, `runbooks/`).
- Manifests: `README.md`, `INDEX.md` (decision tree).
- **80+ feature summaries** migrated from `specs/NNN-slug/spec.md` to
  `livedocs/features/<NNN-slug>.md`. Each ≤ 2 pages.
- **5 templates** (`feature-summary`, `bounded-context`, `c4-level-{L1,L2,L3}`).
- **CI gate**: `tools/check-livedocs-structure.sh` — 7 checks.
- **`AGENTS.md` shrunk** from 230 → 100 lines ("LiveDocs first" rule).

## 2026-08-14 — 4 specs-duplicates (full migration)

- Added 4 summaries for specs with NNN duplicates: `017-fix-markers-at-position-zero`,
  `154-remove-scheduled-publications-monitoring`, `155-song-state-colors`,
  `156-publish-slots-range`.
- All 75+ unique specs are now in LiveDocs.

## 2026-08-14 — depth #1 (ADR + dual-db-access)

- **Added ADR-0001**: Raw JDBC without JPA/Hibernate.
- **Added ADR-0002**: MLT/melt as stack for karaoke video.
- **Added ADR-0003**: LiveDocs = Markdown + YAML + Mermaid (not MkDocs/Docusaurus).
- **Added**: `livedocs/architecture/decisions/` (new subdirectory).
- **Added topic**: `dual-db-access.md` (JDBC drill-down).
- CI: `check-livedocs-structure.sh` — exception for `*/decisions/*`.

## 2026-08-14 — depth #2 (3 topics)

- **Added topic**: `mlt-pipeline.md` (MLT-generator, mko, Playwright, ~150 KaraokeProperties).
- **Added topic**: `concurrent-editing.md` (OptimisticConcurrency + `tbl_audits` + `VoteEnd`).
- **Added topic**: `nginx-conventions.md` (config 80to8897, User-Agent routing, NDJSON proxy_buffering).

## 2026-08-14 — depth #3 (3 cross-cutting topics)

- **Added topic**: `observability.md` (SSE + heartbeat + self-healing).
- **Added topic**: `cache-invalidation.md` (`setWebvueProp` + Vuex + SSE).
- **Added topic**: `idempotency.md` (5 strategies: Idempotency-Key, UNIQUE, optimistic, lease, async job).

## 2026-08-14 — depth #4 (2 bounded contexts)

- **Added BC**: `stats` (StatBySong, tbl_events, StatsCacheScheduler, visitor/bot segmentation, funnel).
- **Added BC**: `rendering` (drill-down for processing — MP4 video via MLT/melt/Playwright).
- 7 bounded contexts total: catalog, processing, rendering, publishing, stats, identity, editorial.

## 2026-08-14 — depth #6 (6 runbooks)

- **Added**: `livedocs/runbooks/` (new layer for operational how-to).
- **Added**: `README.md` + 6 how-to:
  - `how-to-deploy.md`
  - `how-to-migrate-db.md`
  - `how-to-add-new-feature.md`
  - `how-to-debug-connection-leak.md`
  - `how-to-add-new-domain.md`
  - `how-to-update-livedocs.md`
- CI: `check-livedocs-structure.sh` — exception for `*/runbooks/*`.

## 2026-08-14 — depth #7 (cross-link validator + fix 196 broken)

- **Added**: `tools/check-livedocs-cross-links.sh` — validates relative
  paths `../X.md` and `related:` in frontmatter.
- **Fixed**: 196 broken references across all LiveDocs (sed-batches).
- CI: `lint.yml` — new step `Check LiveDocs cross-links`.
- **Total: 814 cross-links valid, 0 broken** at merge time.

## 2026-08-14 — depth #8 (3 ADRs)

- **Added ADR-0004**: KaraokeApp only on admin-machine, not on prod.
- **Added ADR-0005**: Self-hosted ML (Ollama + SearXNG + Sheetsage + Demucs) instead of SaaS.
- **Added ADR-0006**: ProcessBuilder + redirectErrorStream(true) for async.
- 6 ADRs total.

## 2026-08-14 — depth #9 (discoverability tool)

- **Added**: `tools/search-livedocs.sh` — grep wrapper for AI agents.

## 2026-08-14 — depth #10 (lychee external-links)

- **Added**: new job `livedocs-external-links` in `.github/workflows/lint.yml`.
- Checks EXTERNAL links in LiveDocs via lychee in `--offline`.
- `advisory` (continue-on-error=true). Strict in Pass 17+.

## State as of today (2026-08-14)

| Metric | Value |
|--------|-------|
| **Features in `features/`** | 84 |
| **Bounded contexts in `domain/`** | 7 |
| **C4 levels** | 3 (L1, L2, L3) |
| **Topic docs in `architecture/`** | 13 |
| **ADRs** | 6 |
| **Runbooks** | 7 (README + 6 how-to) |
| **Templates in `templates/`** | 6 |
| **`frontmatter` files** | 107 |
| **`total .md` files** | ~120 |
| **Cross-links valid** | 814 |
| **Broken references** | 0 |
| **`AGENTS.md`** | ≤ 100 lines ✓ |
| **CI checks for LiveDocs** | 7/7 + cross-links 0/814 broken + lychee advisory |
| **Spec migration coverage** | 100% (all 75+ unique specs) |