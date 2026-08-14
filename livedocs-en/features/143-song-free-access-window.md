---
status: Active
slug: 143-song-free-access-window
related:
  - ../domain/publishing.md
  - ../domain/catalog.md
  - ../features/169-share-link-in-premium-compare.md
  - ../../specs/143-song-free-access-window/spec.md
---

# 143 — Time window of free access to songs (LiveDoc)

> Drill-down — [specs/143-song-free-access-window/spec.md](../../specs/143-song-free-access-window/spec.md).

## What it does

Change of monetization model:
- **Removed `exclusive` flag** from admin (and from code).
- **Song available to all** for a limited time after `publish_at`
  (default **1 calendar month**), then only by subscription.
- **`free` flag** makes song "eternally on-air" (free forever), even without `publish_at`.
- Description of rules updated on "About" page.
- Counters recalculated (including main page and StatBySong).
- In "Zakroma" for unauthorized/non-premium: "Will be on-air from ..." /
  "On-air until ...", for eternally-free/purchased/premium — nothing.

## User Stories

- **US1** (P1): Free access limited to 1 calendar month after on-air.
- **US2** (P1): After window — "This song is available only by subscription" (anonymized).
- **US3** (P1): `free` flag → eternally free, without window.
- **US4** (P2): In Zakroma correct labels for unauthorized/non-premium.

## Functional Requirements (pointer)

- **FR-001**: Remove `exclusive` field from Song / DTO / UI.
- **FR-002**: Constant `FREE_ACCESS_WINDOW_DAYS = 30` (calendar month) → variable in `KaraokeProperties`.
- **FR-003**: Method `isInFreeAccessWindow(now)`: returns true if `free == true` OR (`publish_at` exists AND `now() < publish_at + FREE_ACCESS_WINDOW`).
- **FR-004**: Text "This song is available only by subscription" (anonymized, no date/cause).
- **FR-005**: `SongView.vue` / `ZakromaView.vue` — correct labels.
- **FR-006**: Update "About" page (access rules).
- **FR-007**: Recalculate `StatBySong` (main, widget).

## Acceptance Criteria

- [ ] **AC1**: Song on-air < 30 days → available free (anonymous / without subscription).
- [ ] **AC2**: Song on-air > 30 days → "This song is available only by subscription".
- [ ] **AC3**: Song `free = true` → always free (without window).
- [ ] **AC4**: `exclusive` flag absent from UI, DTO, migrations.
- [ ] **AC5**: "About" page describes new rules.

## Related LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (window logic), [catalog.md](../domain/catalog.md) (Song)
- Feature: [169-share-link-in-premium-compare.md](../features/169-share-link-in-premium-compare.md)
- Specs: `005-free-vs-premium` (old model), `143` — new model

## Code

- Backend: `karaoke-app/.../model/Song.kt` — remove `exclusive`, add `isInFreeAccessWindow()`
- Backend: `karaoke-app/.../KaraokeProperties.kt` — `FREE_ACCESS_WINDOW_DAYS`
- Backend: `karaoke-app/.../service/StatService.kt` — recalculate counters
- SQL: `deploy/karaoke-db/<NNN>_tbl_settings_drop_exclusive.sql` — migration (drop column)
- Frontend: `karaoke-public/src/views/SongView.vue` — check `isInFreeAccessWindow`
- Frontend: `karaoke-public/src/views/AboutView.vue` — updated description

## History

- Created: 2026-08-14
- Last updated: 2026-08-14