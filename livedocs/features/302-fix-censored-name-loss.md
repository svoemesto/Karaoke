---
status: Active
slug: 302-fix-censored-name-loss
related:
  - ../../specs/302-fix-censored-name-loss/spec.md
  - ../../docs/features/song-edit-and-censored.md
  - ../architecture/L3-components.md
---

# 302 — Bugfix: цензурированное имя не сохраняется в SongEdit (LiveDoc)

> Drill-down — [specs/302-fix-censored-name-loss/spec.md](../../specs/302-fix-censored-name-loss/spec.md).
> Per-feature документ — [docs/features/song-edit-and-censored.md](../../docs/features/song-edit-and-censored.md).

## Что чинит

**Bug #52** из OpenProject: редактор правит поле «Censored» в `SongEdit.vue`,
видит тост «Изменения сохранены», но при F5 значение слетает.

**Root cause**: Spring Web молча отбрасывает неизвестные query-параметры.
В `ApiController.songs2Update` не было `@RequestParam songNameCensored`,
поэтому `?songNameCensored=...` терялся на полпути между фронтом и `fields[SongField.NAME_CENSORED]`.

## Архитектурное решение (FR-011)

Не «добавить ещё один `@RequestParam`», а **устранить корневую причину** —
рассинхрон между UI-полями и backend-параметрами.

**До** (95 `@RequestParam`):
```kotlin
fun songs2Update(
    @RequestParam(required = false) id: String,
    @RequestParam(required = false) songName: String?,
    @RequestParam(required = false) author: String?,
    // ... ещё ~90 @RequestParam
    @RequestParam(required = false) warning: String?,
): SongUpdateResultDto { /* ~250 строк */ }
```

**После** (1 `@RequestParam Map<String, String>` + `SongUpdateMapper`):
```kotlin
fun songs2Update(@RequestParam all: Map<String, String>): SongUpdateResultDto {
    val song = Song.loadFromDbById(all["id"]?.toLongOrNull() ?: throw BadRequest400, ...)
    val result = SongUpdateMapper.apply(song, all, WORKING_DATABASE, storageService, storageApiClient)
    song.saveToDb(); song.saveToFile()
    if (result.freeChanged || result.idStatusChanged) notifyStatsDirty()
    return SongUpdateResultDto(result.albumLinkValid, result.fileNameRenameError)
}
```

`songs2Update` сократился с ~250 строк до ~30. **Невозможно** добавить `v-model="song.X"` в SongEdit.vue и забыть поддержать в backend — параметр автоматически попадает в `Map`.

## SongUpdateMapper — 5 фаз

```
Phase A: Special-case fields (fileName sanitize, albumId cross-author, songType enum)
Phase B: Standard string fields (fields[SongField.X] через fieldLookup)
Phase C: Direct setters (tags, rootFolder, description, shortDescription, warning)
Phase D: Baseline автоцензурирование (если censored пустое И name непустое)
Phase E: SongUpdateApplyResult (albumLinkValid, fileNameRenameError, freeChanged, idStatusChanged)
```

Неизвестные параметры — WARN-лог + ignore (обратная совместимость).

## Защитные чеки (FR-005/006/007/008)

| Чек | Что ловит | Пар |
|---|---|---|
| `tools/check-songedit-field-coverage.sh` | `v-model="song.X"` без поддержки в маппере | SongEdit ↔ /song/update |
| `tools/check-endpoint-field-coverage.sh` | то же для всех пар | все пары из `tools/endpoint-pairs.yml` |

**Whitelist** (≤10 полей) для нестандартных setter'ов: id (path-param),
albumId (cross-author), songType (enum). После рефактора B1 whitelist
всего 3 поля (раньше планировалось 18).

Оба чека в pre-commit + CI, exit 1 блокирует merge.

## Изменения в C4

- **L3 Component** `ApiController.songs2Update` — драматическое упрощение.
- **NEW L3 Component** `SongUpdateMapper` — централизованный маппер.
- **NEW Tool** `check-songedit-field-coverage.sh` + `check-endpoint-field-coverage.sh`.
- **NEW Config** `tools/endpoint-pairs.yml` — список пар UI↔backend.

## Связь с другими BC

- **Song BC** (главный) — без изменений, использует существующие `fields[SongField.X]`.
- **Censored Words Dictionary BC** (specs/140) — Phase D маппера вызывает
  `song.songName.censored(database)` если пользователь не задал значение
  руками (политика «доверие редактору» из specs/277).
- **OpenProject BC** (specs/295) — issue #52 → `mark-review` после merge.

## Что осталось за скобками

- Расширение endpoint-pairs на Album/Author/SiteUser/Dictionary — следующий раунд.
- AST-анализ вместо grep (если whitelist вырастет >15).
- Trim/валидация длины/whitespace-only — политики, не баг.
