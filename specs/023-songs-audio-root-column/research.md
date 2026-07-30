# Research: Колонка audio_parent_id в таблице песен админки

**Date**: 2026-07-30
**Feature**: specs/023-songs-audio-root-column

## Unknowns Resolved

### 1. Does `audio_parent_id` already exist in the database and backend models?

**Decision**: Yes, the field already exists.

**Rationale**:
- `tbl_settings.audio_parent_id` is present and read in `Song.kt:7386`.
- `Song.audioParentId: Long` exists (`Song.kt:802`).
- `SongDTO.audioParentId: Long` exists (`SongDTO.kt:172`).
- `SongField.AUDIO_PARENT_ID` is already wired into `/api/song/update` (`ApiController.kt:2953`).
- The sync diff already handles `audio_parent_id` (`Song.kt:6457-6460`).

**Implication**: No backend schema migration or new model fields are required. We only need to expose the field in `SongDTOdigest` and the digest endpoint.

---

### 2. How is the Songs table built on the frontend?

**Decision**: `SongsTable.vue` uses `bootstrap-vue-next` `BTable` with a computed `songDigestFields` array and per-cell templates (`#cell(rootId)`, `#cell(songName)`, etc.).

**Rationale**:
- `songDigestFields` is defined at `SongsTable.vue:845`.
- The `root` column uses `key: 'rootId'` and is the second column after `id`.
- Data comes from Vuex `songsDigest` populated by `/api/songsdigests` (`ApiController.apisSongsDigests`).

**Implication**: Adding an "A-root" column means inserting a field definition after `rootId` in `songDigestFields` and adding a matching `#cell(audioParentId)` template.

---

### 3. How are filters implemented?

**Decision**: Filters are stored in a dedicated Vuex module `webvue3/src/components/Songs/filter/store.js`, persisted server-side via `setWebvueProp`/`getWebvueProp`, and applied in `SongsFilterModal.vue`.

**Rationale**:
- `SongsFilterModal.vue` has a `songsFilterRootId` field already (`webvue3/src/components/Songs/filter/SongsFilterModal.vue:85-98`).
- The same pattern can be copied for `audioParentId`.
- Backend `ApiController.apisSongsDigests` maps `filterRootId` to SQL `root_id` (`ApiController.kt:2368`).
- `Song.getWhereList` maps `filter_root_id` to column `root_id` (`Song.kt:7173`). There is no `filter_audio_parent_id` yet.

**Implication**: Add `filterAudioParentId` parameter to `/api/songsdigests`, map it to `filter_audio_parent_id`, and add the mapping in `Song.getWhereList` to `audio_parent_id`.

---

### 4. How to implement tooltips in `webvue3`?

**Decision**: Use `bootstrap-vue-next` directive `v-b-tooltip` or component `BTooltip`.

**Rationale**:
- `bootstrap-vue-next@0.40.5` exports `vBTooltip` directive (`webvue3/node_modules/bootstrap-vue-next/dist/src/directives/BTooltip/index.mjs`).
- No existing usage of `v-b-tooltip` in `webvue3/src`, so we need to register the directive globally in `main.js` or import it in the component.
- The tooltip content must be dynamic (author, year, album, name) per cell.

**Implication**: Preferred approach is a small reusable tooltip component or directive that fetches song info via a dedicated endpoint. To avoid per-cell fetch, we batch-resolve parent info in the table component or use a new `GET /api/song/{id}/shortinfo` endpoint lazily.

---

### 5. Where to get short song info for the tooltip?

**Decision**: Add a new read-only endpoint `GET /api/song/{id}/shortinfo` returning `{id, author, year, album, songName}`.

**Rationale**:
- Existing `Song.loadFromDbById` returns full `Song` entity. Converting to a lightweight DTO avoids leaking unnecessary data.
- A dedicated endpoint is cacheable and keeps the digest payload small.
- An alternative would be to denormalize parent info into `SongDTOdigest`, but that increases response size for every row and is unnecessary for most users who won't hover every cell.

**Implication**: New Kotlin DTO `SongShortInfoDto`, new controller method in `ApiController.kt` or a dedicated controller.

---

## Alternatives Considered

| Approach | Pros | Cons | Decision |
|---|---|---|---|
| Include parent info in `SongDTOdigest` | Single request, no extra endpoint | Larger payload for all rows; data stale if parent changes | Rejected |
| Lazy fetch via `GET /api/song/{id}/shortinfo` | Small payload, clean separation | Extra requests on hover | Selected |
| Use existing `Song.loadFromDbById` through `/api/song` | No new endpoint | Returns too much data; not ideal for tooltip | Rejected |

---

## Open Questions (none)

All technical unknowns resolved. Ready for Phase 1 design.
