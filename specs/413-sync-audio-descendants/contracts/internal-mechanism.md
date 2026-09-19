# Contract: внутренний механизм синхронизации

**Branch**: `413-sync-audio-descendants`
**Date**: 2026-09-19
**Spec**: `../spec.md`
**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SyncAudioDescendants.kt` (new)

## Публичный API (внутри karaoke-app)

```kotlin
object SyncAudioDescendants {
    /** Хук из Song.saveToDb() — гейт + постановка в очередь. Не блокирует. */
    fun onSongSaved(
        song: Song,
        previous: Song?,
        changedContentFields: Set<String>,
    )

    /** Admin: массовый проход. "OK" | "ALREADY_RUNNING". */
    fun syncAll(): String
}
```

## Контракт гейта `onSongSaved`

Вход: сохраняемая `song`, её `previous` состояние из БД (может быть null для
INSERT), множество `recordDiffName` изменённых content-полей.

Условия запуска (все обязательны):

| # | Условие | Источник |
|---|---|---|
| 1 | `song.idStatus >= 5` | FR-002 |
| 2 | `changedContentFields` непусто **ИЛИ** `previous.idStatus < 5 <= song.idStatus` | FR-001, Q13 |
| 3 | `song.songType == SONG` ⇒ маркеры непусты | Q19, Q23 |
| 4 | не `inSync.get()` | FR-020 |
| 5 | `song.audioParentId` — не важно (родитель может не иметь родителя) | — |

При выполнении — `pendingParents.add(song.id)` и запуск воркера, если он не идёт.

## Контракт `syncOneParent(parentId)`

1. Загрузить родителя `WORKING_DATABASE`. Нет / `idStatus < 5` → skip+лог.
2. `SELECT id FROM tbl_songs WHERE audio_parent_id = ? AND id_status < 6`.
3. Для каждого потомка:
   - `hasActiveProcess(child.id)` → skip `active_process`;
   - `WaveformCompare.compareWaveforms(child, parent)`; `!ok` → skip `no_audio`;
   - `similarityPercent < AUDIO_PARENT_THRESHOLD` → skip `below_threshold:N%`;
   - иначе `syncAudioDescendantFromParent(child, parent, pct, deltaMs)`:
     перенос контента/метрик/history + статус 5 + `saveToDbLocked()` + `.srt`.
4. Вернуть `SyncAudioResult(parentId, processed, synced, skipped, reason)`.

Ошибка на потомке → `catch`, лог, continue (FR-023).

## Инварианты

- **Никакой рекурсии**: синхронизация пишет только потомка, хук подавлен
  `inSync` → дочерние потомки текущего потомка не трогаются (FR-006, FR-020).
- **Атомарность записи потомка**: один `saveToDbLocked()` (spec 299).
- **Только LOCAL**: `WORKING_DATABASE`, без push (FR-022).
- **Порядок**: очередь обрабатывается последовательно одним воркером (FR-019,
  FR-026). `LinkedHashSet` сохраняет порядок постановки.

## Лог-строки (для диагностики, префикс единый)

```
[sync-audio] parent=<id> child=<id> — synced (94%..., delta=<ms>)
[sync-audio] parent=<id> child=<id> — skipped: active_process
[sync-audio] parent=<id> child=<id> — skipped: below_threshold:87%
[sync-audio] parent=<id> — skipped: not_ready (id_status=<N>)
[sync-audio] admin — processed_parents=<N> processed_children=<M> synced=<X> skipped=<Y>
```
