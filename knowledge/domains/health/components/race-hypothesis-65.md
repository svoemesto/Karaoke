# Component: race hypothesis (#65)

> **Домен**: [health](../domain.md)
> **Компонента**: гипотеза о race condition в `HealthReport`
> repair-loop (вероятная корневая причина OpenProject #65).

## Контекст

**OpenProject #65** (in progress): «Ошибка при проверке наличия
файла в удаленном хранилище». Race condition в
`StorageApiClient.fileExists` HTTP-вызовах.

**Гипотеза**: single-flight guard **отсутствует** на repair-loop.
Когда `startRepairAll` и `onRepairProcessFinished` вызываются из
разных потоков (HTTP UI + worker), они оба модифицируют
`autoRepairSongIds` (Set<Long>) и оба вызывают
`recomputeAndBroadcast` + `executeResolvable` параллельно.

## Анализ

### Текущий код (`HealthReport.kt:2259-2302`)

```kotlin
fun startRepairAll(song: Song, database, storageService, storageApiClient) {
    autoRepairSongIds.add(song.id)                                          // (1) write
    val reports = recomputeAndBroadcast(song.id, ...)                       // (2) read
    executeResolvable(reports)                                            // (3) execute
    recomputeAndBroadcast(song.id, ...)                                    // (4) recompute
}

fun onRepairProcessFinished(songId, success, database, ...) {
    val reports = recomputeAndBroadcast(songId, ...)                        // (5) read
    if (songId !in autoRepairSongIds) return                              // (6) read
    if (!success) { autoRepairSongIds.remove(songId); return }             // (7) write
    val resolvable = reports.filter { ... }
    if (resolvable.isNotEmpty()) {
        executeResolvable(resolvable)                                     // (8) execute
        recomputeAndBroadcast(songId, ...)                                 // (9) recompute
    }
}
```

### Race scenarios

**Scenario A**: `startRepairAll` (HTTP) vs `onRepairProcessFinished`
(worker, тот же `songId`):

- T1: `startRepairAll` начинается: (1) `add(songId)`.
- T2: worker `onRepairProcessFinished` запускается: (5)-(6) `recompute` +
  `in autoRepairSongIds` (true).
- T1: (2)-(4) `recomputeAndBroadcast` + `executeResolvable` + `recompute`.
- T2: (8)-(9) `executeResolvable` + `recompute` — **ВТОРОЙ раз!**

Результат: **двойное выполнение** actions (или race на `reports`).

**Scenario B**: `autoRepairSongIds` — **не thread-safe** Set.
`HashSet` без синхронизации → `ConcurrentModificationException`
или lost updates.

## Решение (рекомендация)

**PerSong single-flight guard** через `AtomicBoolean`:

```kotlin
// HealthReport companion object:
private val repairInFlight: ConcurrentHashMap<Long, AtomicBoolean> = ConcurrentHashMap()

private fun tryEnterRepair(songId: Long): Boolean =
    repairInFlight.computeIfAbsent(songId) { AtomicBoolean(false) }
        .compareAndSet(false, true)

private fun exitRepair(songId: Long) {
    repairInFlight[songId]?.set(false)
}
```

**Использование**:

```kotlin
fun startRepairAll(song: Song, ...) {
    if (!tryEnterRepair(song.id)) return  // skip — уже чинится
    try {
        autoRepairSongIds.add(song.id)
        // ... существующая логика ...
    } finally {
        exitRepair(song.id)
    }
}

fun onRepairProcessFinished(songId: Long, success: Boolean, ...) {
    if (!tryEnterRepair(songId)) return  // skip — другой вызов уже чинит
    try {
        // ... существующая логика ...
    } finally {
        exitRepair(songId)
    }
}
```

## Trade-offs

1. **PerSong lock** — разные песни не блокируют друг друга.
2. **Не отменяет retry** — если `tryEnterRepair` вернул `false`, мы
   просто пропускаем этот вызов (worker сделает следующий).
3. **Memory leak risk** — `repairInFlight` Map может расти, если
   песни никогда не очищаются. Решение: `repairInFlight.remove(songId)`
   в `onRepairProcessFinished` когда `autoRepairSongIds.remove(songId)`.

## Связь

- [health-report.md](health-report.md) — общая компонента.
- [health-report.md#reconcileplayerreadinessflags](health-report.md) —
  ещё один race-кандидат.
- OpenProject #65 — исходная задача.

## Статус

- ❌ **НЕ исправлено** в Pass 433-441 — требует careful review
  владельцем перед изменением production-кода.
- 📝 **Документировано** как гипотеза.

## Changelog

- **Pass 451** (2026-09-09): Initial hypothesis. Автор: agent (Karaoke).