# LiveDocs — Layer Map (INDEX)

> **Russian version**: [`livedocs/INDEX.md`](../livedocs/INDEX.md)

## Decision tree

```
Task is about...
│
├─ A specific feature (NNN)?
│   └─ → livedocs/features/<NNN-slug>.md
│       (if not yet — specs/<NNN>-*/spec.md)
│
├─ A module / domain (Song, Album, KaraokeVideo, ...)?
│   └─ → livedocs/domain/<context>.md
│       (catalog | processing | rendering | publishing | stats | identity | editorial)
│
├─ Architecture (how does the system work)?
│   ├─ Overall (who uses the system) → livedocs/architecture/L1-system-context.md
│   ├─ Containers (apps + stores) → livedocs/architecture/L2-containers.md
│   └─ Components (inside karaoke-app) → livedocs/architecture/L3-components.md
│
└─ Specific pattern / pitfall / decision?
    └─ → livedocs/architecture/<topic>.md
        or livedocs/architecture/decisions/<NNNN-slug>.md
        (jackson-conventions | docker-conventions | nginx-conventions | ...)
```

## Layers and their contents

### SDD — `livedocs/features/`

SDD summaries of existing features (1-2 pages each). Drill-down — in `specs/`.

See [`features/README.md`](../livedocs/features/README.md) for full list.

### DDD — `livedocs/domain/`

Bounded contexts of project Karaoke + ubiquitous language glossary.

| File | Bounded Context | Aggregate Roots |
|------|------------------|------------------|
| [`catalog.md`](../livedocs/domain/catalog.md) | Каталог / Catalog | Song, Album, Author, Genre |
| [`processing.md`](../livedocs/domain/processing.md) | Обработка / Processing | KaraokeVideo, MLTProject, RenderMp4Params |
| [`rendering.md`](../livedocs/domain/rendering.md) | Рендеринг / Rendering | KaraokeVideo file, MLTProject |
| [`publishing.md`](../livedocs/domain/publishing.md) | Публикация / Publishing | PublishWindow, Subscription |
| [`stats.md`](../livedocs/domain/stats.md) | Статистика / Statistics | StatBySong, SiteEvent |
| [`identity.md`](../livedocs/domain/identity.md) | Идентификация / Identity | SiteUser, Session |
| [`editorial.md`](../livedocs/domain/editorial.md) | Редакторы / Editorial | EditorAssignment, ReviewTask |

### C4 — `livedocs/architecture/`

#### C4 Levels

| File | Level | What it shows |
|------|-------|----------------|
| [`L1-system-context.md`](../livedocs/architecture/L1-system-context.md) | L1 | Karaoke ↔ external systems |
| [`L2-containers.md`](../livedocs/architecture/L2-containers.md) | L2 | Apps + stores inside Karaoke |
| [`L3-components.md`](../livedocs/architecture/L3-components.md) | L3 | Components inside karaoke-app |

#### Topics (drill-down by specific theme)

| File | Topic |
|------|-------|
| [`dual-db-access.md`](../livedocs/architecture/dual-db-access.md) | KaraokeConnection, ThreadLocal, retry |
| [`mlt-pipeline.md`](../livedocs/architecture/mlt-pipeline.md) | MLT-generator: mko-objects, Playwright, ~150 KaraokeProperties |
| [`concurrent-editing.md`](../livedocs/architecture/concurrent-editing.md) | OptimisticConcurrency + tbl_audits + VoteEnd |
| [`nginx-conventions.md`](../livedocs/architecture/nginx-conventions.md) | nginx-config: User-Agent routing, NDJSON |
| [`observability.md`](../livedocs/architecture/observability.md) | SSE + heartbeat + self-healing |
| [`cache-invalidation.md`](../livedocs/architecture/cache-invalidation.md) | setWebvueProp + Vuex + SSE |
| [`idempotency.md`](../livedocs/architecture/idempotency.md) | Idempotency-Key + UNIQUE + lease |
| [`data-sync.md`](../livedocs/architecture/data-sync.md) | LOCAL ↔ SERVER sync (SyncRegistry, recordhash) |
| [`queue-lanes.md`](../livedocs/architecture/queue-lanes.md) | Async queue (HEAVY_RENDER, etc.) |
| [`jackson-conventions.md`](../livedocs/architecture/jackson-conventions.md) | Jackson `is`-prefix in Kotlin DTO |
| [`docker-conventions.md`](../livedocs/architecture/docker-conventions.md) | Docker images |
| [`documentation-conventions.md`](../livedocs/architecture/documentation-conventions.md) | KDoc/JSDoc, blame-ignore-revs |
| [`webvue3-patterns.md`](../livedocs/architecture/webvue3-patterns.md) | Vuex pagination persistence |

#### ADR (Architecture Decision Records)

| File | Decision |
|------|----------|
| [`0001-raw-jdbc.md`](../livedocs/architecture/decisions/0001-raw-jdbc.md) | Raw JDBC without JPA/Hibernate |
| [`0002-mlt-instead-of-ffmpeg.md`](../livedocs/architecture/decisions/0002-mlt-instead-of-ffmpeg.md) | MLT as primary stack |
| [`0003-livedocs-markdown-yaml-mermaid.md`](../livedocs/architecture/decisions/0003-livedocs-markdown-yaml-mermaid.md) | LiveDocs = Markdown + YAML + Mermaid |
| [`0004-karaoke-app-admin-only.md`](../livedocs/architecture/decisions/0004-karaoke-app-admin-only.md) | KaraokeApp only on admin-machine |
| [`0005-self-hosted-ml.md`](../livedocs/architecture/decisions/0005-self-hosted-ml.md) | Self-hosted ML, not SaaS |
| [`0006-processbuilder-redirect-errorstream.md`](../livedocs/architecture/decisions/0006-processbuilder-redirect-errorstream.md) | ProcessBuilder + redirectErrorStream(true) |

### Runbooks — `livedocs/runbooks/`

| File | When to read |
|------|--------------|
| [`how-to-deploy.md`](../livedocs/runbooks/how-to-deploy.md) | Before `deploy_web.sh` |
| [`how-to-migrate-db.md`](../livedocs/runbooks/how-to-migrate-db.md) | Before SQL migration |
| [`how-to-add-new-feature.md`](../livedocs/runbooks/how-to-add-new-feature.md) | Creating new feature via SDD |
| [`how-to-debug-connection-leak.md`](../livedocs/runbooks/how-to-debug-connection-leak.md) | `FATAL: too many clients already` |
| [`how-to-add-new-domain.md`](../livedocs/runbooks/how-to-add-new-domain.md) | New bounded context |
| [`how-to-update-livedocs.md`](../livedocs/runbooks/how-to-update-livedocs.md) | After code/architecture change |

### Tools

- [`../../tools/search-livedocs.sh`](../tools/search-livedocs.sh) — grep wrapper
  for AI agents.
- [`../../tools/check-livedocs-structure.sh`](../tools/check-livedocs-structure.sh) — CI gate.
- [`../../tools/check-livedocs-cross-links.sh`](../tools/check-livedocs-cross-links.sh) — link validator.

## When to update INDEX

- New LiveDoc in any layer → add row to corresponding table.
- New layer → create section with table.
- Renamed / deleted LiveDoc → update table.