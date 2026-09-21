## Исправление массивов (semicolon-separated)

Владелец справедливо указал: массивы в этом проекте передаются **строкой с
разделителем `;`**, а не JSON-массивом. Я нарушил конвенцию.

### Что было сделано

PR #495 (merged):
- Backend `CacheAdminController.setActiveSongIds`: `@RequestParam String` + `split(";")`.
- Frontend `SongsTable.vue.sendActiveSongIds`: `params: { activeSongIds: ids.join(';') }`.

### Конвенция проекта

См. `KaraokeProcessAdminController.kt:181`:
```
// По аналогии с ApiController.getSmartCopyAll: ids приходит как semicolon-separated
// строка (`?ids=1;2;3` из webvue3/src/components/Processes/store.js — `ids.join(';')`).
```

Используется в `bulkRetry`, `bulkForceStop`, `bulkStart` (4 места в Processes/store.js).

### Результат

`POST /api/health/cache/active-song-ids?activeSongIds=28555;28556;28557` → 200 ✅

CI 12/12 PASS.
