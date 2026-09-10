# Data Model: HealthReport Speedup

## Key Entities

### HealthReport

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt`

```kotlin
data class HealthReport(
    val song: Song,
    val description: String = "",          // "<KaraokeFileType>/<KaraokeFileTypeLocation>"
    val healthReportType: HealthReportType, // FILE_VIOLATION | CONSISTENCY_VIOLATION
    val healthReportStatus: HealthReportStatus, // OK | WARNING | ERROR | FATAL_ERROR | IN_PROGRESS
    val canResolve: Boolean = false,
    val problemText: String = "",
    val solutionText: String = "",
    val solutionActions: List<() -> Unit> = emptyList(),
)
```

**Companion object** (line ~61–2302): вся логика сборки отчёта.

| Метод | Описание |
|-------|---------|
| `getHealthReportList(song: Song)` :1119 | Главная точка входа |
| `actionsLocalFileSystem` :161 | FS-проверки |
| `actionsLocalStorage` :501 | MinIO local |
| `actionsRemoteStorage` :789 | MinIO remote |
| `reconcilePlayerReadinessFlags` :2113 | Обновление 4 `*Ready` флагов |
| `recomputeAndBroadcast` :2153 | SSE после repair |
| `startRepairAll` :2259 | Repair-loop |

### HealthReportDTO

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReportDTO.kt`

Сериализуемая версия для webvue3 (minimal set):

```kotlin
data class HealthReportDTO(
    val description: String,
    val healthReportType: HealthReportType,
    val healthReportStatus: HealthReportStatus,
    val canResolve: Boolean,
    val problemText: String,
    val solutionText: String,
)
```

### KaraokeFileType

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeFileType.kt`

```kotlin
enum class KaraokeFileType {
    MLT,
    MLT_PREVIEW,
    AUDIO,
    VIDEO,
    COVER,
    CARDS,
    KARAFILE_MP4,
    MARKERS_JSON,
    // ... (полный список ~15 типов)
}
```

### KaraokeFileTypeLocations

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeFileTypeLocations.kt`

```kotlin
enum class KaraokeFileTypeLocations {
    LOCAL_FILESYSTEM,   // FS на admin-машине
    LOCAL_STORAGE,      // MinIO local
    REMOTE_STORAGE,     // MinIO remote
}
```

### StorageMetadataCache

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt` (Pass 344)

```kotlin
class StorageMetadataCache {
    private val cache = PollingCache<StorageFileInfo>()

    fun getOrCompute(key: String, ttlSeconds: Int = 300, loader: () -> StorageFileInfo): StorageFileInfo
}
```

### StorageCircuitBreaker

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageCircuitBreaker.kt` (Pass 351, спека #352)

```kotlin
enum class CircuitState { CLOSED, OPEN, HALF_OPEN }

class StorageCircuitBreaker {
    var state: CircuitState
    fun recordSuccess()
    fun recordFailure()
    fun canExecute(): Boolean
}
```

## Relationships

```
Song (1) ──hasMany──► HealthReport (N)
                           │
                           ├──► KaraokeFileType
                           ├──► KaraokeFileTypeLocation
                           │
                           └──► StorageMetadataCache (reads file metadata)
                           └──► StorageCircuitBreaker (protects MinIO calls)
```

## API Contract

**Endpoint**: `GET /api/health/getHealthReportList?songId=<id>`

```kotlin
// Response
data class HealthReportListResponse(
    val healthReports: List<HealthReportDTO>,
    val songId: String,
    val durationMs: Long,
    val error: String? = null  // "Storage unavailable" при circuit breaker open
)
```
