# Data Model: Автозапуск «Синхронизации в 1 клик» каждые 3 часа

> Phase 1 output для фичи 235. Источник: [`spec.md`](../spec.md) (FR-001..FR-016), [`research.md`](../research.md).

## Обзор

Фича вводит **2 singleton bean** (backend) и **3 DTO** для API/UI. Никаких миграций БД не требуется (только in-memory state + 3 новых property в `Karaoke.properties`).

## Entities

### 1. `AutoOneClickSyncScheduler` (Kotlin, Spring `@Component`)

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/AutoOneClickSyncScheduler.kt`.

| Поле | Тип | Default | Источник | Описание |
|------|-----|---------|----------|----------|
| `lastRunMs` | `@Volatile var Long` | `0L` | runtime | Момент последнего выполненного тика (`System.currentTimeMillis()`). Используется для проверки `now - lastRunMs >= intervalMs` |
| `running` | `AtomicBoolean` | `false` | runtime | Lock для «ручной + авто не одновременно» (FR-007). Общий bean-синглтон, доступен также из `ApiController` |
| `history` | `ConcurrentLinkedDeque<AutoOneClickSyncRun>` | empty | runtime | История последних ≤10 тиков (FR-009) |
| `enabled` | (read-only каждый тик) | `true` | `KaraokeProperties.getBoolean("autoOneClickSyncEnabled")` | FR-004 |
| `intervalMs` | (read-only каждый тик) | `10_800_000L` (3 ч) | `KaraokeProperties.getLong("autoOneClickSyncIntervalMs")` | FR-005 |
| `initialDelayMs` | (read-only каждый тик) | `300_000L` (5 мин) | `KaraokeProperties.getLong("autoOneClickSyncInitialDelayMs")` | FR-006 |

**Метод**:
- `tick()` — `@Scheduled(fixedDelay = 60_000L, initialDelay = 5_000L)`. Псевдокод — см. `research.md §2`.

**Связи**:
- Зависит от: `KaraokeProperties`, `SyncRegistry`, `runEntitySync(key, direction)` (из `Utils.kt:629`), `notifyStatsDirtyIfSongsPushed(...)` (из `ApiController.kt:2915`), `SseNotificationService.send(SseNotification.crud(...))` (опционально, по образцу существующего `postSyncOneClick`).
- Зависит на: `AutoOneClickSyncStatusController` (читает `history` через `getStatus()`).

### 2. `AutoOneClickSyncRun` (Kotlin, value-класс)

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/AutoOneClickSyncRun.kt`.

| Поле | Тип | Default | Описание |
|------|-----|---------|----------|
| `startedAt` | `Instant` | `Instant.now()` | Начало тика |
| `finishedAt` | `Instant?` | `null` | Конец тика (заполняется в `finally`) |
| `status` | `String` | `"RUNNING"` | Один из: `RUNNING`, `SUCCESS`, `FAILED` |
| `reason` | `String?` | `null` | Текст ошибки для `FAILED` (например, `"SQLException: connection refused"`) |
| `totals` | `Totals` | `Totals(0,0,0,0)` | Суммарные `created/updated/deleted/moved` |
| `perTarget` | `List<SyncOneClickResultDto>` | `emptyList()` | Per-target результат (переиспользует существующий DTO из `ApiController.kt:176`) |

**Вложенный класс** `Totals(created: Int, updated: Int, deleted: Int, moved: Int)`.

**State transitions**:

```
[created] → RUNNING ──→ SUCCESS ──→ (в history)
                  └──→ FAILED  ──→ (в history)
```

`SUCCESS` и `FAILED` — терминальные. `RUNNING` — transient, существует только в момент тика. После завершения всегда переходит в `SUCCESS` или `FAILED` (в `finally`-блоке).

### 3. `AutoOneClickSyncStatusController` (Kotlin, Spring `@RestController`)

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/AutoOneClickSyncStatusController.kt`.

| Endpoint | Метод | Описание |
|----------|-------|----------|
| `/api/sync/auto-status` | `GET` | Возвращает `AutoOneClickSyncStatusDto` |
| (no other endpoints) | — | Никаких POST/PUT/DELETE — только чтение |

**Зависимости**: `AutoOneClickSyncScheduler` (получает через `@Autowired` или constructor injection — как в существующих контроллерах).

## DTOs (API-контракт)

### 4. `AutoOneClickSyncStatusDto` (data class)

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/dto/AutoOneClickSyncStatusDto.kt`.

```kotlin
data class AutoOneClickSyncStatusDto(
    val enabled: Boolean,                              // текущее значение KaraokeProperties
    val intervalMs: Long,                              // текущее значение KaraokeProperties
    val initialDelayMs: Long,                          // текущее значение KaraokeProperties
    val lastRun: AutoOneClickSyncRunDto?,              // самый последний тик или null
    val last10: List<AutoOneClickSyncRunDto>,          // до 10 последних тиков, newest first
    val nextRunEstimate: String?                       // ISO-8601, e.g. "2026-08-16T18:00:00Z"; null если выключено
)
```

### 5. `AutoOneClickSyncRunDto` (data class)

```kotlin
data class AutoOneClickSyncRunDto(
    val startedAt: String,                             // ISO-8601
    val finishedAt: String?,                           // ISO-8601 или null для RUNNING (на практике не отдаётся — RUNNING transient)
    val status: String,                                // "RUNNING" | "SUCCESS" | "FAILED"
    val reason: String?,                               // null для SUCCESS; текст ошибки для FAILED
    val totals: TotalsDto,                             // см. ниже
    val perTarget: List<SyncOneClickResultDto>         // переиспользует существующий DTO
)

data class TotalsDto(
    val created: Int,
    val updated: Int,
    val deleted: Int,
    val moved: Int
)
```

### 6. Изменение `SyncOneClickResultDto`

**Без изменений** — переиспользуем существующий DTO из `ApiController.kt:176-185`:
```kotlin
data class SyncOneClickResultDto(
    val key: String,
    val displayName: String,
    val direction: String,
    val skipped: Boolean,
    val created: List<String>,
    val updated: List<String>,
    val deleted: List<String>,
    val moved: List<String>,
)
```

## KaraokeProperties: новые ключи

Добавить 3 записи в `KaraokeProperties.kt` (порядок: рядом с `editorAssignmentDefaultTarget`, ~строка 319):

```kotlin
KaraokeProperty(
    key = "autoOneClickSyncEnabled",
    defaultValue = true,
    description = "Автозапуск «Синхронизации в 1 клик» каждые autoOneClickSyncIntervalMs мс. true = автозапуск включён, false = выключен (ручной клик продолжает работать)",
),
KaraokeProperty(
    key = "autoOneClickSyncIntervalMs",
    defaultValue = 10_800_000L,   // 3 часа
    description = "Интервал автозапуска «Синхронизации в 1 клик» в миллисекундах (по умолчанию 3 часа)",
),
KaraokeProperty(
    key = "autoOneClickSyncInitialDelayMs",
    defaultValue = 300_000L,      // 5 минут
    description = "Задержка перед первым автозапуском после старта karaoke-app (мс, по умолчанию 5 минут)",
),
```

## Validation rules

- `intervalMs >= 60_000L` (минимум 1 минута). `.coerceAtLeast(60_000L)` в коде scheduler'а — иначе риск DDoS на БД.
- `initialDelayMs >= 0L` (без `.coerceAtLeast` — допустим `0L` для тестов).
- `history.size <= 10` — поддерживается через `pollFirst()` после каждого `addLast`.

## Relationships (ERD)

```text
KaraokeAppApplication
    └── KaraokeProperties (existing, base64-file)
            └── 3 new keys: autoOneClickSyncEnabled, autoOneClickSyncIntervalMs, autoOneClickSyncInitialDelayMs

AutoOneClickSyncScheduler (@Component, singleton)
    ├── reads: KaraokeProperties.{enabled, intervalMs, initialDelayMs}
    ├── reads: SyncRegistry.all (existing)
    ├── invokes: runEntitySync(key, direction) (existing, Utils.kt:629)
    ├── invokes: notifyStatsDirtyIfSongsPushed (existing, ApiController.kt:2915)
    ├── emits: SseNotification.crud (existing, optional)
    ├── writes: history: ConcurrentLinkedDeque<AutoOneClickSyncRun>
    ├── guards: running: AtomicBoolean (shared with ApiController)
    └── exposed via: getStatus(): AutoOneClickSyncStatusDto

ApiController (existing, modified)
    ├── /api/sync/oneclick (POST) — wraps existing logic in try { running.cas } catch { 409 }
    └── reads: AutoOneClickSyncScheduler.running (shared AtomicBoolean)

AutoOneClickSyncStatusController (@RestController, new)
    └── /api/sync/auto-status (GET) — returns AutoOneClickSyncStatusDto

webvue3 (admin SPA, modified)
    └── src/components/Sync/
        ├── store.js — add loadSyncAutoStatusPromise
        └── SyncTable.vue — add «Автозапуск» block
```

## Миграции БД

**Не требуются.** Все state — in-memory. Свойства — в `Karaoke.properties` (base64-файл, обновляется через Properties UI, см. AGENTS.md §«Граница доступа к MLT/Karaoke.properties»).
