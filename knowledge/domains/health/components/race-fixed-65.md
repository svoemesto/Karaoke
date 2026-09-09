# Component: race fix (#65) ✅

> **Домен**: [health](../domain.md)
> **Компонента**: per-song single-flight guard для
> `HealthReport.repair-loop` (реальный fix race condition).

## Контекст

**OpenProject #65** (in progress): «Ошибка при проверке наличия
файла в удаленном хранилище». Race condition в
`StorageApiClient.fileExists` HTTP-вызовах.

**Гипотеза** (Pass 343+): один из возможных источников race —
**отсутствие single-flight guard** на repair-loop. Когда
`startRepairAll` (HTTP UI) и `onRepairProcessFinished` (worker)
вызываются из разных потоков для одной песни, они оба
модифицируют `autoRepairSongIds` и оба вызывают
`recomputeAndBroadcast` + `executeResolvable` параллельно.

## Решение (FIXED in Pass 343)

**Per-song single-flight guard** через `AtomicBoolean`:

```kotlin
// HealthReport companion object:
private val repairInFlight: ConcurrentHashMap<Long, AtomicBoolean> = ConcurrentHashMap()

fun attemptEnterRepair(songId: Long): Boolean =
    repairInFlight.computeIfAbsent(songId) { AtomicBoolean(false) }
        .compareAndSet(false, true)

fun exitRepair(songId: Long) {
    repairInFlight[songId]?.set(false)
    // NB: не удаляем ключ из map, чтобы избежать memory churn.
    // cleanupRepair(songId) — для окончательной очистки.
}

fun cleanupRepair(songId: Long) {
    repairInFlight.remove(songId)
}
```

## Использование в `startRepairAll` / `onRepairProcessFinished`

```kotlin
fun startRepairAll(song, database, storageService, storageApiClient) {
    if (!attemptEnterRepair(song.id)) return  // skip — другой поток чинит
    try {
        // ... существующая логика ...
    } finally {
        exitRepair(song.id)
    }
}

fun onRepairProcessFinished(songId, success, database, storageService, storageApiClient) {
    if (!attemptEnterRepair(songId)) return  // skip
    try {
        // ... существующая логика ...
    } finally {
        exitRepair(songId)
    }
}
```

## Unit-тесты (Pass 343)

`HealthReportRepairRaceTest.kt`:

1. `attemptEnterRepair returns true on first call, false on second call before exit`
2. `exitRepair allows re-entry`
3. `concurrent attemptEnterRepair allows exactly one winner` (10 потоков)
4. `different songIds do not block each other`

**Результат**: 4/4 passed за 0.794s.

## Trade-offs

1. **PerSong lock** — разные песни не блокируют друг друга.
2. **Не отменяет retry** — если `tryEnterRepair` вернул `false`,
   мы просто пропускаем этот вызов (worker сделает следующий).
3. **Memory leak risk** — `repairInFlight` Map может расти, если
   песни никогда не очищаются. Решение: `cleanupRepair(songId)`
   (пока НЕ вызывается нигде — TODO Pass 343+).

## Защита от race сценариев

| Сценарий | Без fix | С fix |
|---|---|---|
| `startRepairAll` (HTTP) vs `onRepairProcessFinished` (worker) на одной песне | **двойное выполнение** actions | один выполняет, другой **skip** |
| `autoRepairSongIds` — не thread-safe Set | потенциальный `ConcurrentModificationException` | **synchronized** через `computeIfAbsent` (AtomicBoolean) |
| Разные песни | не блокируют друг друга | **не блокируют** (perSong lock) |

## Связь

- [health-report.md](health-report.md) — общая компонента.
- [health-report.md#reconcileplayerreadinessflags](health-report.md) —
  ещё один race-кандидат.
- [storage-api-client.md](../../storage/components/storage-api-client.md) —
  `fileExists` (исходная задача #65).

## Changelog

- **Pass 343** (2026-09-09):
  - Гипотеза документирована (Pass 343 detail-2).
  - **FIXED** с unit-тестами (4/4 passed).
  - Реальный код: `HealthReport.kt` companion object +
    `startRepairAll` / `onRepairProcessFinished` обёрнуты в guard.
  - Тесты: `HealthReportRepairRaceTest.kt`.