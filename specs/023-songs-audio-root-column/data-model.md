# Data Model: Колонка audio_parent_id в таблице песен админки

**Feature**: specs/023-songs-audio-root-column
**Date**: 2026-07-30

## Entities

### `tbl_settings` (existing)

Already contains the column required by the feature.

| Column | Type | Nullable | Notes |
|---|---|---|---|
| `id` | bigint | NOT NULL | Primary key |
| `root_id` | bigint | NOT NULL | Parent song id (displayed as column "root") |
| `audio_parent_id` | bigint | NOT NULL | Audio source song id (displayed as column "A-root") |
| `author` | varchar | | Main author |
| `song_name` | varchar | | Song title |
| `album` | varchar | | Album name |
| `song_year` | bigint | | Release year |
| ... | | | Other existing columns |

### `SongDTOdigest` (to be extended)

Digest returned by `/api/songsdigests` and rendered by `SongsTable.vue`.

| Field | Type | Source | Notes |
|---|---|---|---|
| `audioParentId` | Long | `tbl_settings.audio_parent_id` | New field. `0` means "no value". |
| `rootId` | Long | `tbl_settings.root_id` | Already present. |
| `id` | Long | `tbl_settings.id` | Already present. |
| `author` | String | `tbl_settings.author` | Already present. |
| `year` | Long | `tbl_settings.song_year` | Already present. |
| `album` | String | `tbl_settings.album` | Already present. |
| `songName` | String | `tbl_settings.song_name` | Already present. |

### `SongShortInfoDto` (new)

Lightweight read-only DTO for tooltip content.

| Field | Type | Source |
|---|---|---|
| `id` | Long | `tbl_settings.id` |
| `author` | String | `tbl_settings.author` |
| `year` | Long | `tbl_settings.song_year` |
| `album` | String | `tbl_settings.album` |
| `songName` | String | `tbl_settings.song_name` |

---

## Relationships

- `SongDTOdigest.audioParentId` → `tbl_settings.id` (optional, `0` means unset).
- `SongDTOdigest.rootId` → `tbl_settings.id` (optional, `0` means unset).
- `SongShortInfoDto.id` → `tbl_settings.id`.

---

## Validation Rules

- `audioParentId` is a non-negative `Long`. `0` is treated as "no parent".
- Tooltip request for `id <= 0` or non-existent id must return empty/404 without throwing.
- Filter value `songsFilterAudioParentId` is parsed as string and mapped to exact equality SQL on `audio_parent_id`.

---

## State Transitions

None. This feature is read-only plus filter UI state.
