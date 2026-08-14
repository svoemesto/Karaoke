# ADR-0004: KaraokeApp — only on admin-machine, not on prod

* **Status**: Accepted
* **Date**: 2026-07-20 (Phase 001, fundamental; see Constitution § "Deployment environments")
* **Deciders**: Karaoke team

> **Russian version**: [`../../livedocs/architecture/decisions/0004-karaoke-app-admin-only.md`](../../livedocs/architecture/decisions/0004-karaoke-app-admin-only.md)

## Context

The Karaoke project consists of several containers (see [L2-containers.md](../../livedocs/architecture/L2-containers.md)):

- `karaoke-web` — on **prod** (8090, behind nginx).
- `karaoke-public`, `webvue3` — on **prod** (Vue SPA static).
- `karaoke-app` — where?

Architectural decision: **`karaoke-app` is NOT deployed to prod**.
Only on admin-machine. This is a non-obvious decision for new developers.

## Decision

**`karaoke-app` is deployed only on admin-machine**, not on prod.

On prod only `karaoke-web` is used — it provides all public HTTP endpoints,
but **does not start heavy tasks** (MLT rendering, Demucs, Sheetsage,
Telegram bot, sync LOCAL↔SERVER — all this happens on admin-machine, where
`karaoke-app` lives).

## What works on admin-machine (with `karaoke-app`)

- Task queue `KaraokeProcessWorker` + lanes
  (HEAVY_RENDER, LIGHT_BACKGROUND, REMOTE_STORE_UPLOAD, STEM_JOBS).
- MLT/melt + Playwright + ffmpeg rendering → MP4 to MinIO.
- Demucs (stem separation) and Sheetsage (key/BPM/chords) — Python in Docker.
- `pgsql` → sync LOCAL ↔ SERVER PROD.
- Telegram-bot / VK auto-publish.
- Lyrics search engines (Ollama + SearXNG).
- `setWebvueProp` server-side cache (for webvue3).

## What works on prod (only `karaoke-web`)

- REST API for public pages (`/api/public/...`).
- REST API for webvue3 (`/api/admin/...`, `/api/editor/...`).
- Thymeleaf pages (legacy).
- Proxy and nginx-wrap (`80to8897`).
- Connection to `tbl_*` DB on prod — through `KaraokeConnection.Target.REMOTE`.

## Why `karaoke-app` is NOT on prod

1. **Heavy dependencies**. MLT/melt + Playwright + Python + ML-models (1-2 GB).
   Container is heavy, requires GPU/CPU, takes disk. On prod this is not
   needed (rendering happens on admin-machine, result → MinIO → prod).
2. **Webhooks and long-running jobs**. Karaoke-app works as orchestrator
   for many async-operations (renders, demuxes, Telegram-bot). On prod
   this is extra noise in public site logs.
3. **`KaraokeProperties` contains secrets** (Telegram tokens, OAuth secrets,
   etc.) — on prod they're not needed, but defined in Karaoke-app image.
4. **Security**. Fewer containers on prod = less attack surface.
   Karaoke-app is not needed for public HTTP.

## Contract: what's available on prod

`karaoke-web` (on prod) imports modules from `karaoke-app` as JAR
(`./gradlew clean karaoke-app:bootJar`). That is, **the same code** is
available on prod (through `karaoke-web` fat jar), but **NOT started**
as process.

What this means:
- `KaraokeDbTable`, `KaraokeConnection` — work on prod (through `karaoke-web`).
- `@Scheduled` methods (e.g., `SongAirScheduler`) — DON'T work on prod
  (no `karaoke-app` bean).
- `KaraokeProcessWorker.doStart()` — DOESN'T work on prod.

This is **deliberate** — sync triggers, render-triggers must be on admin-machine.

## Exceptions (Pass 21+)

- On machine with hostname `dev-pc` under user `dev` agent **is allowed**
  to rebuild/restart `karaoke-app` (see Constitution § "Agent restrictions").
  This is for development convenience.

## Alternatives Considered

- **`karaoke-app` on prod**: would inflate image, require ML-dependencies on
  prod, extra attack surface.
- **`karaoke-app` and `karaoke-web` as one process**: mixes responsibilities.
  Used this approach before Phase 001, turned out to complicate diagnostics
  (what crashed — web or app?).
- **Light `karaoke-app` without async**: trim functionality. Not done —
  two beans of one project, just don't load `_app` bean on prod through
  `ComponentScan`.

## References

- [Constitution § "Deployment environments"](.specify/memory/constitution.md).
- [L2-containers.md](../../livedocs/architecture/L2-containers.md) — what runs where.
- [ADR-0005](0005-self-hosted-ml.md) — how this is related to self-hosted ML.