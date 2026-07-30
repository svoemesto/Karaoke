# Contract: /api/songsdigests

**Feature**: specs/023-songs-audio-root-column
**Date**: 2026-07-30

## Endpoint

`POST /api/songsdigests`

## Existing Behavior

Returns a list of song digests for the admin Songs table.

## New Request Parameter

| Parameter | Type | Required | Description |
|---|---|---|---|
| `filterAudioParentId` | string | No | Exact match filter on `tbl_settings.audio_parent_id`. Empty string is ignored. |

## Response

Same shape as before, but each digest item now includes:

```json
{
  "audioParentId": 12345,
  "rootId": 67890,
  "id": 11111,
  ...
}
```

`audioParentId` is a `Long`. Value `0` means "no audio parent".

## Backend Mapping

- `filterAudioParentId` → `args["filter_audio_parent_id"]` in `ApiController.apisSongsDigests`.
- `Song.getWhereList` maps `filter_audio_parent_id` to SQL column `audio_parent_id`.

## Error Handling

- Non-numeric `filterAudioParentId` may produce a SQL error; frontend should restrict input to digits.
- Empty or missing parameter is ignored (no filtering).
