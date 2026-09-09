# Component: health-report

> **Домен**: [health](../domain.md)
> **Компонента**: детальное описание `HealthReport` data class и его
> companion object.

## Ответственность | Responsibility

`HealthReport` — единичный отчёт о нарушении по одной песне.
Содержит: **что не так** + **как исправить** (набор actions в виде
лямбд). Не выполняет actions сам — это **план**, который пользователь
(webvue3) может запустить одной кнопкой или автоматически через
`autoRepair`.

`HealthReport.getHealthReportList(song: Song)` собирает **полный
список нарушений по песне**, перебирая все её `KaraokeFileType`
× все `KaraokeFileTypeLocations`, где этот тип должен быть.

## Интерфейсы и Контракты | Interfaces and Contracts

### Data class `HealthReport`

```kotlin
data class HealthReport(
    val song: Song,
    val description: String = "",
    val healthReportType: HealthReportType,
    val healthReportStatus: HealthReportStatus,
    val canResolve: Boolean = false,
    val problemText: String = "",
    val solutionText: String = "",
    val solutionActions: List<() -> Unit> = emptyList(),
)
```

| Поле | Что | Когда заполняется |
|---|---|---|
| `song` | Сама песня | Всегда (даже для OK-отчётов) |
| `description` | Что проверяли: `"MLT/LOCAL_FILESYSTEM"`, `"COVER/LOCAL_STORAGE"`, ... | Всегда; формат `"<type>/<location>"` |
| `healthReportType` | Категория нарушения | Всегда |
| `healthReportStatus` | `OK` / `WARNING` / `ERROR` / `FATAL_ERROR` / `IN_PROGRESS` | Всегда |
| `canResolve` | Может ли система исправить сама | Почти всегда true, кроме FATAL_ERROR |
| `problemText` | Человеческое описание проблемы | Если status ≠ OK |
| `solutionText` | Человеческое описание решения | Если status ≠ OK и есть actions |
| `solutionActions` | Лямбды для исправления | Если canResolve && status ∈ {ERROR, WARNING, IN_PROGRESS} |

### `HealthReportType`

```kotlin
enum class HealthReportType {
    CONSISTENCY_VIOLATION,  // файл есть, но симлинки/метаданные не консистентны
    FILE_VIOLATION,         // файл отсутствует или повреждён
}
```

### `HealthReportStatus`

```kotlin
enum class HealthReportStatus(val color: String) {
    OK(color = "#99FF99"),          // Всё хорошо
    WARNING(color = "#99CCFF"),     // Всё хорошо, но есть нюансы
    IN_PROGRESS(color = "#FFFF99"), // Уже чиним (есть KaraokeProcess)
    ERROR(color = "#FF9999"),       // Всё плохо, но можно сделать хорошо
    FATAL_ERROR(color = "#FF0000"), // Всё совсем плохо
}
```

## Логика и Алгоритмы | Logic and Algorithms

### Сборка отчёта: `getHealthReportList(song: Song)`

Точка входа: `HealthReport.kt:1119`. Возвращает `List<HealthReport>`
для одной песни. Перебирает:

1. Все `KaraokeFileType` для песни (см. `KaraokeFileType.willBeInFileSystem` / `willBeInLocalStorage` / `willBeInRemoteStorage`).
2. Для каждой комбинации (type, location) вызывает `actions(location = ...)`.
3. `actions()` (строка 62) делегирует в `actionsLocalFileSystem` / `actionsLocalStorage` / `actionsRemoteStorage`.

**Внутри каждого `actions*`** — большой decision tree (комментарий в коде на 50+ строк, см. `HealthReport.kt:186-230`):

```
Если файл должен быть (canBe):
  Если файл реально есть:
    Если должны быть симлинки:
      Если симлинки есть и целые → OK
      Если симлинки есть, но broken → удалить + создать (ERROR)
      Если симлинков нет → создать (ERROR)
  Если файла реально нет (!exists):
    Если canResolve:
        Если есть задача в процессах (inProgressOwn) → IN_PROGRESS
        Если !inProgressOwn и canCreate → создать процесс (ERROR + action)
        Если !canCreate:
          Если inProgressParent → поставить в очередь после родителя
          Если !inProgressParent → tryRestoreFromStorage
      Если !canResolve → FATAL_ERROR (tryRestoreFromStorage)

Если файл НЕ должен быть (!canBe):
  Если реально есть → удалить (ERROR + action)
  Если реально нет:
    Если должны быть симлинки → удалить симлинки (ERROR)
    Иначе → OK
```

### Hot spots и производительность

`getHealthReportList` — главный источник MinIO-запросов:

- Для каждой песни вызывает **`StorageApiClient.fileExists`** для
  каждого `KaraokeFileType`, для которого `willBeInRemoteStorage=true`.
- Для 18k песен × 4 типов = ~72k HTTP round-trips к MinIO.
- Это и есть проблема OpenProject #69 «кеширование».

### Repair-loop: `startRepairAll` / `recomputeAndBroadcast`

Точка входа: `HealthReport.kt:2259`. Используется при:

1. Завершении `KaraokeProcess*` (`onRepairProcessFinished`, строка 2277).
2. Ручном запуске из webvue3 «Починить всё».
3. Старте admin-машины (cold start).

Цикл:
```
startRepairAll →
  for each Song (через SearchResult/songId):
    getHealthReportList
    for each HealthReport where canResolve:
      executeSolutionActions()         // выполнить лямбды
  recomputeAndBroadcast()              // пересчитать PlayerReady, SSE
  reconcilePlayerReadinessFlags()      // Song.status синхронизировать с реальностью
```

После repair пользователь видит обновление **через SSE**
(`HEALTH_REPORTS`), без перезагрузки страницы.

### `reconcilePlayerReadinessFlags`

`HealthReport.kt:2113`. Решает, может ли плеер играть песню:

- Все обязательные `KaraokeFileType` (playerdata + audio stems + ...) должны
  быть в правильных местах.
- Результат → поле `Song.status` (READY / NOT_READY) и публикуется в
  `playerData` (читается webvue3 плеером).

Эта функция **НЕ описана** в Knowledge (см. Known gaps).

## Связь с подсистемами

- **Async Process Queue** (`KaraokeProcess`):
  repair часто создаёт `KaraokeProcess.createProcess(...)` как одну
  из solution-actions. См. Pass 342 (P1).
- **Storage layer** (`KaraokeStorageService` + `StorageApiClient`):
  см. [storage domain](../../storage/domain.md) (Pass 341).
- **SSE / WebSocket**:
  `recomputeAndBroadcast` публикует в SSE-канал. SSE ещё не описан
  (P2).
- **Monitor checks** (`monitor-checks.md`):
  НЕ часть мониторинга — это **реактивный** механизм. Monitor-checks
  это **проактивный** пинг (ProdContainerCheck, RenderQueueStalledCheck).

## Known issues

- **#65** (in progress) «Ошибка при проверке наличия файла в
  удаленном хранилище»: race condition в `StorageApiClient.fileExists`
  HTTP-вызовах (вероятно, в `WebKaraokeStorageServiceImpl`). **TODO
  для Pass 342+**: детальный анализ с repro-сценарием.

## Код (физическая реализация)

- `karaoke-app/.../HealthReport.kt` (2302 строк, всё в companion)
- `karaoke-app/.../HealthReportDTO.kt`
- `karaoke-app/.../HealthReportType.kt`
- `karaoke-app/.../HealthReportStatus.kt`
- `karaoke-app/.../KaraokeFileType.kt`
- `karaoke-app/.../KaraokeFileTypeLocations.kt`
- `karaoke-app/.../KaraokeFileSymlink.kt`
- `karaoke-app/.../KaraokeFileTypeKind.kt`
- `karaoke-app/.../KaraokeFileTypeFor.kt`

## Known gaps (TODO для следующих Pass)

- [ ] **UI-сторона**: `webvue3/src/views/HealthReportView.vue`,
      Vuex `healthReport/store.js`, API-эндпоинт
      `/api/health/getHealthReportList`.
- [ ] **`reconcilePlayerReadinessFlags`** (строк 50): детальный
      алгоритм, что делает `isOkInLocalStorage`.
- [ ] **`executeResolvable`** (строка 2252): точка входа repair-loop,
      7 строк, но не описана.
- [ ] **`recomputeAndBroadcast`** (строка 2153): связь с SSE.
- [ ] **Race conditions в `StorageApiClient.fileExists`** (задача #65).
- [ ] **`KaraokeProcess.THREAD_LANE_HEALTH_REPORT`** — отдельный
      thread lane; описан в Async Process Queue (Pass 342).
- [ ] **`LEGACY_MLT_FILE_TYPES`** (строка 1107): set из 4 типов,
      помечен как legacy; нужно понять, планируется ли удаление.

## Связанные ADR | Related ADRs

- `archive/docs/features/monitoring.md` — оригинальный документ,
  archive (НЕ Knowledge). Требует миграции в Knowledge.
- `archive/docs/features/dual-db-sync.md` — упомянут в KDoc.