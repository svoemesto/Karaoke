# Component: race fix (#65) [OK]

> **Домен**: [health](../domain.md)
> **Компонента**: per-song single-flight guard для
> `HealthReport.repair-loop` (реальный fix race condition).


## Ответственность | Responsibility


per-song single-flight guard для `HealthReport.repair-loop` (реальный fix race condition).

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

## Интерфейсы и Контракты | Interfaces and Contracts

Публичная поверхность guard'а — три функции в `companion object`
`HealthReport` (`karaoke-app/.../HealthReport.kt:2393-2407`):

| Функция | Контракт |
|---|---|
| `attemptEnterRepair(songId: Long): Boolean` | `true` — поток вошёл в repair; `false` — для этой песни repair уже идёт (skip). Внутри `repairInFlight.computeIfAbsent(songId) { AtomicBoolean(false) }.compareAndSet(false, true)`. |
| `exitRepair(songId: Long)` | Освобождает флаг: `repairInFlight[songId]?.set(false)`. Ключ в map остаётся (осознанно — чтобы не было memory churn). |
| `cleanupRepair(songId: Long)` | Полное удаление ключа: `repairInFlight.remove(songId)`. **Пока не вызывается нигде** (см. Trade-offs). |

Внутреннее состояние (private, companion):

- `repairInFlight: ConcurrentHashMap<Long, AtomicBoolean>` — per-song
  single-flight.
- `autoRepairSongIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()` —
  песни в каскаде «Исправить всё».

Точки входа, обёрнутые в guard:

- `startRepairAll(song: Song, database: KaraokeConnection, storageService: KaraokeStorageService, storageApiClient: StorageApiClient)` — HTTP-поток (UI «Исправить всё»), repair уходит в `repairExecutor` (fire-and-forget, HTTP-тред не блокируется).
- `onRepairProcessFinished(songId: Long, success: Boolean, database: KaraokeConnection, storageService: KaraokeStorageService, storageApiClient: StorageApiClient)` — worker-поток, после завершения задания каскада.

## Логика и Алгоритмы | Logic and Algorithms

### Решение (FIXED in Pass 343)

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

### Использование в `startRepairAll` / `onRepairProcessFinished`

```kotlin
// HealthReport.startRepairAll (HTTP-поток, fire-and-forget):
if (!attemptEnterRepair(song.id)) return      // skip — другой поток чинит
autoRepairSongIds.add(song.id)
recomputeAndBroadcast(song.id, database, storageService, storageApiClient)
repairExecutor.submit {
    try {
        // пересчёт → executeResolvable(reportsBefore) → финальный пересчёт
    } finally {
        exitRepair(songId)                    // флаг снимается по завершении repair
    }
}

// HealthReport.onRepairProcessFinished (worker-поток):
if (!attemptEnterRepair(songId)) return        // skip — другой поток чинит
try {
    val reports = recomputeAndBroadcast(songId, database, storageService, storageApiClient)
    if (songId !in autoRepairSongIds) return
    if (!success) { autoRepairSongIds.remove(songId); return }  // ERROR обрывает каскад
    // resolvable → executeResolvable + пересчёт; иначе !inProgress → выход из каскада
} finally {
    exitRepair(songId)
}
```

Порядок для обоих входов: `attemptEnterRepair` → (`true`) работа →
`finally exitRepair`; при `false` вызов тихо пропускается.

### Защита от race сценариев

| Сценарий | Без fix | С fix |
|---|---|---|
| `startRepairAll` (HTTP) vs `onRepairProcessFinished` (worker) на одной песне | **двойное выполнение** actions | один выполняет, другой **skip** |
| `autoRepairSongIds` — не thread-safe Set | потенциальный `ConcurrentModificationException` | **synchronized** через `computeIfAbsent` (AtomicBoolean) |
| Разные песни | не блокируют друг друга | **не блокируют** (perSong lock) |

## Unit-тесты (Pass 343)

`karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/HealthReportRepairRaceTest.kt`:

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

## Зависимости | Dependencies

- [health-report.md](health-report.md) — общая компонента.
- [health-report.md#reconcileplayerreadinessflags](health-report.md) —
  ещё один race-кандидат.
- [storage-api-client.md](../../storage/components/storage-api-client.md) —
  `fileExists` (исходная задача #65).

## Changelog

- **Pass 486** (2026-09-27, spec `486-knowledge-domains-others`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 343** (2026-09-09):
  - Гипотеза документирована (Pass 343 detail-2).
  - **FIXED** с unit-тестами (4/4 passed).
  - Реальный код: `HealthReport.kt` companion object +
    `startRepairAll` / `onRepairProcessFinished` обёрнуты в guard.
  - Тесты: `HealthReportRepairRaceTest.kt`.