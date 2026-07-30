# Contract: /api/song/{id}/shortinfo

**Feature**: specs/023-songs-audio-root-column
**Date**: 2026-07-30

## Endpoint

`GET /api/song/{id}/shortinfo`

## Purpose

Return minimal song information for tooltip display in the admin Songs table when hovering over `root` or `A-root` cells.

## Request

| Parameter | Type | Required | Description |
|---|---|---|---|
| `id` | Long (path) | Yes | Song id. |

## Response

### Success (200)

```json
{
  "id": 12345,
  "author": "ДДТ",
  "year": 1988,
  "album": "Я получил эту роль",
  "songName": "Что такое осень"
}
```

### Not Found (404)

Returned when no song with the given id exists.

```json
{
  "id": 0,
  "author": "",
  "year": 0,
  "album": "",
  "songName": ""
}
```

Or HTTP 404 with empty body, depending on frontend error handling preference.

## Notes

- This endpoint is read-only.
- It should not load heavy fields like `source_text`, `source_markers`, or pictures.
- Caching is optional but recommended if tooltips are triggered frequently.
