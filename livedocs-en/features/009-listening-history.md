---
status: Active
slug: 009-listening-history
related:
  - ../domain/catalog.md
  - ../domain/identity.md
  - ../features/171-admin-subscriptions-history.md
  - ../../specs/009-listening-history/spec.md
---

# 009 — Listening history (LiveDoc)

> Drill-down — [specs/009-listening-history/spec.md](../../specs/009-listening-history/spec.md).

## What it does

For registered users on `karaoke-public`, separate page/section
**"What you listened to"** — list of recently played songs.

`tbl_listening_history` table with entries: `userId`, `songId`,
`last_played_at`, `play_count`. Trigger on every playback start in the
player.

This is a user-facing feature (for the user themselves) — for admin-list
of all users see `171-admin-subscriptions-history`.

## User Stories

- **US1** (P1): User opens "History" and sees their last played songs.

## Functional Requirements (pointer)

- **FR-001**: `tbl_listening_history` — FK to `tbl_site_users`, `tbl_settings`.
- **FR-002**: Trigger on track-play-start.
- **FR-003**: Skip-filter (songs tagged `SKIP` don't appear).
- **FR-004**: Endpoint `GET /api/public/account/history?limit=20`.

## Acceptance Criteria

- [ ] **AC1**: User listens to 5 songs → in history 5 latest.
- [ ] **AC2**: Skip-songs don't appear.
- [ ] **AC3**: Endpoint returns JSON.

## Related LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Song), [identity.md](../domain/identity.md) (SiteUser)
- Feature: [171-admin-subscriptions-history.md](../features/171-admin-subscriptions-history.md) (admin variant)

## Code

- Backend: `karaoke-app/.../model/ListeningHistoryEntry.kt`
- Backend: `karaoke-app/.../service/ListeningHistoryService.kt`
- SQL: `deploy/karaoke-db/<NNN>_tbl_listening_history.sql`
- Frontend: `karaoke-public/src/views/HistoryView.vue`

## History

- Created: 2026-08-14
- Last updated: 2026-08-14