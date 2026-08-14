---
status: Active
slug: 122-premium-auto-publish
related:
  - ../domain/publishing.md
  - ../domain/editorial.md
  - ../features/131-fix-approve-demo-render-telegram-sync.md
  - ../../specs/122-premium-auto-publish/spec.md
---

# 122 — Premium auto-publish in Telegram + VK when song appears in collection (LiveDoc)

> Drill-down — [specs/122-premium-auto-publish/spec.md](../../specs/122-premium-auto-publish/spec.md).

## What it does

When administrator approves editor's task (`SongEditorController.approve`),
the song receives `idStatus=6` + readiness flags. This event triggers
**two** independent, previously documented mechanisms:

1. **News "available" / "in collection"** (`category="premium"`,
   `specs/101-song-news-flag`) — `Song.markNewsAvailableIfReady()` inside
   `saveToDb()` on first `newsAvailableAnnounced` transition false→true.

**This spec adds**: after DEMO render — publish to Telegram and VK with
DEMO video.

## User Stories

- **US1** (P1): After approve → posts in Telegram + VK with DEMO video.
- **US2** (P1): News "in collection" appears on server.

## Functional Requirements (pointer)

- **FR-001**: `TelegramPublishService.publishDemo(songId)` (new).
- **FR-002**: `VkPublishService.publishWithDemo(songId)` (new).
- **FR-003**: Sync via `updateRemoteSongFromLocalDatabase` (see `131`).

## Acceptance Criteria

- [ ] **AC1**: Approve → DEMO rendered → Telegram post with DEMO → VK post with DEMO.
- [ ] **AC2**: News "in collection" appears on server.

## Related LiveDocs

- Domain: [publishing.md](../domain/publishing.md), [editorial.md](../domain/editorial.md)
- Feature: [131-fix-approve-demo-render-telegram-sync.md](../features/131-fix-approve-demo-render-telegram-sync.md)
- Specs: `121-vk-news-auto-publish`, `101-song-news-flag`

## Code

- Backend: `karaoke-app/.../service/TelegramPublishService.kt` — add `publishDemo()`
- Backend: `karaoke-app/.../service/VkPublishService.kt` — add `publishWithDemo()`

## History

- Created: 2026-08-14
- Last updated: 2026-08-14