# Implementation Plan: HealthReport WAITING status + SSE re-compute on cache fill

**Branch**: `368-healthreport-waiting-cache-update` | **Date**: 2026-09-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/368-healthreport-waiting-cache-update/spec.md`

## Summary

Задача OpenProject #80 «Ускорение HealtReport-2» — баг после спеки #364 (Pass 364): при открытии страницы Songs, когда `StorageMetadataCache` ещё не заполнен, UI показывает **ложные ERROR-записи** (по одной на каждый location: LOCAL_FILESYSTEM / LOCAL_STORAGE / REMOTE_STORAGE) и после фактического заполнения кеша они не пересчитываются — нет SSE-сигнала.

Решение: добавить новое значение `WAITING` (`#FFCCFF`) в enum `HealthReportStatus`, использовать его вместо `ERROR` при cache miss в `cachedFileExists`, и после успешного fill в `StorageMetadataCache` отправлять SSE `HEALTH_REPORTS` через `recomputeAndBroadcast` для затронутой песни.

## Technical Context

**Language/Version**: Kotlin 1.x / JDK 17

**Primary Dependencies**:
- `karaoke-app/.../HealthReport.kt` (companion object ~2480 строк, 8 вызовов `cachedFileExists`, 1 вызов `cachedFileExistsAsync`)
- `karaoke-app/.../HealthReportStatus.kt` (enum, 5 значений → нужно добавить 6-е: `WAITING`)
- `karaoke-app/.../services/StorageMetadataCache.kt` (Pass 344/348, `getFileExists`/`getFileExistsAsync`, executor `cacheFillerExecutor`)
- `karaoke-app/.../services/StorageCircuitBreaker.kt` (Pass 351, используется в `actionsLocalStorage`)
- `karaoke-app/.../services/SseNotificationService.kt` (`SNS.send`, тип `HEALTH_REPORTS`)
- `HealthReport.recomputeAndBroadcast` (HealthReport.kt:2303) — единственная точка рассылки SSE `HEALTH_REPORTS`

**Storage**: PostgreSQL через raw JDBC (`tbl_storage_metadata_cache`, миграция `deploy/karaoke-db/48_storage_metadata_cache.sql`). Никаких изменений схемы БД для этой спеки.

**Testing**: нет автотестов (CI не падает на `@Disabled`). Верификация — пользователем через UI + логи `docker logs karaoke-app | grep 'infra.cache.storage.waiting'`.

**Target Platform**: `karaoke-app` (admin machine). UI обновление через webvue3 (`HealthReportView.vue`, Vuex `healthReport/store.js`) — SSE-событие `HEALTH_REPORTS` уже слушается (Pass 341).

**Project Type**: backend Kotlin + SSE → Vue 3 webvue3. Изменения только в backend, UI не трогается.

**Performance Goals**:
- Cold start HTTP-ответ: ≤500 мс для `getHealthReportList` (3 WAITING-записи сразу)
- Прогрев 20 песен через SSE: ≤3 секунды
- Hit rate после прогрева: ≥80% (через `infra.cache.storage.waiting` логи)
- Никаких лишних SSE на warm cache (≤1 событие на cold start; ноль на warm)

**Constraints**:
- НЕ менять существующие 8 вызовов `cachedFileExists` на async (нужен sync для большинства мест — async только для REMOTE cold-start).
- Сохранить backward compatibility: `cachedFileExists` (sync) без callback должен продолжать работать (для мест, где song ещё не известен или callback не нужен).
- `IN_PROGRESS` (repair-loop) НЕ трогать — это другое состояние (см. Clarifications спеки #1).
- UI webvue3 не меняется (Pass 341 уже подключает SSE).

**Scale/Scope**: 18k+ песен в БД. Страница Songs показывает 20–100 песен за раз. Каждый `getHealthReportList` проверяет 5–8 `KaraokeFileType` × 3 locations → до ~240 проверок на страницу Songs, но после Pass 344/348 — уникальных cache keys ≤ ~40 (см. SC-003 спеки #364).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Self-contained pipeline | ✅ N/A | Нет внешних API в hot path |
| II. Raw JDBC + diff by hashes | ✅ N/A | Не затрагивает схему БД |
| III. Two-DB sync | ✅ N/A | Не затрагивает sync LOCAL↔SERVER |
| IV. Async queue + stdout parsing | ✅ **Применимо** | `cacheFillerExecutor` уже существует (Pass 364), fill — fire-and-forget через этот executor |
| V. Two frontends | ✅ N/A | Backend-only изменение; UI уже слушает SSE `HEALTH_REPORTS` |
| VI. Code standards (KDoc, linters) | ✅ Применимо | KDoc для нового enum-значения + KDoc для новых параметров `cachedFileExists(cachedFileExists, onFillComplete)` |
| VII. Cross-machine setup | ✅ N/A | Локальная оптимизация |
| VIII. Secrets | ✅ N/A | Нет секретов |
| IX. Knowledge-first | ✅ Пройден | Pre-flight выполнен, Knowledge прочитана ДО codegraph |

**Вердикт**: Constitution Check пройден. Нарушений нет.

## Project Structure

### Documentation (this feature)

```
specs/368-healthreport-waiting-cache-update/
├── plan.md              # Этот файл
├── research.md          # Phase 0: исследования не требуется (база — Pass 364 + Knowledge)
├── data-model.md        # Phase 1: только перечисление enum-значений (HealthReportStatus)
├── quickstart.md        # Phase 1: валидация через docker logs + UI
└── tasks.md             # Phase 2: (/speckit.tasks — НЕ создаётся здесь)
```

### Source Code (repository root)

```
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── HealthReportStatus.kt        # +1 enum значение: WAITING(color = "#FFCCFF")
├── HealthReport.kt              # cachedFileExists принимает onFillComplete callback; getHealthReportList возвращает WAITING вместо ERROR
└── services/
    └── StorageMetadataCache.kt  # (не требует изменений, см. research §4)
```

**Structure Decision**: Минимальное изменение — все правки в существующих файлах. Не вводим новых helper-классов. Никаких миграций БД (используем существующую `tbl_storage_metadata_cache`).

## Phase 0: Research

Research не требуется в классическом виде — база решена в Pass 364 (`specs/364-healthreport-speedup/`), Pass 344 (`specs/344-storage-metadata-cache/`), Pass 345 (`specs/348-storage-cache-eternal/`) и задокументирована в Knowledge.

Вместо этого — резюме уже проведённых исследований (со ссылками на источники):

### 1. Почему ломается UI после Pass 364

В `HealthReport.actionsRemoteStorage` (HealthReport.kt:963) уже используется `cachedFileExistsAsync` с `CompletableFuture.get(50ms)`. На cache miss возвращается `true` (safe default) → `existsInRemoteStore = true` → ветка "Файл есть в удалённом хранилище" → может дать `ERROR` (если есть другие проблемы).

В `HealthReport.actionsLocalStorage` (HealthReport.kt:664) и `actionsRemoteStorage` (строки 736, 800) используется **синхронный** `cachedFileExists` (Pass 344). На cache miss → `loader()` блокирует HTTP-тред → возвращает реальное значение → `OK`/`ERROR` корректно. **Но**: после этого cache заполняется, следующий запрос — cache hit.

Проблема #80: при **первом** открытии страницы все `cachedFileExists` синхронно блокируют HTTP-тред (slow), и если MinIO ответил положительно но файл реально есть только в одном location — генерируется ложная `ERROR`. После возврата ответа UI видит ERROR, но кеш заполнился — следующий клик на «Обновить» вернёт OK. Без SSE — UI не узнает.

### 2. Решение: WAITING + SSE

- **WAITING вместо ERROR** при cache miss → UI сразу видит цвет `#FFCCFF` (PASS SPEC US1/AC1).
- **`recomputeAndBroadcast` после fill** → UI получает SSE → перерисовывает. (FR-003 спеки).
- **`onFillComplete` callback** в `cachedFileExists` → передаётся в `StorageMetadataCache.getFileExists` (для sync метода тоже нужен callback, потому что fill происходит **прямо в момент вызова** loader'а, см. StorageMetadataCache.kt:122).

### 3. Альтернативы рассмотрены

| Альтернатива | Почему rejected |
|---|---|
| Single-flight guard на `cachedFileExists` через AtomicBoolean per-song | US3/AC2 спеки (≤1 событие на cold start) уже подразумевает, что cache miss сам по себе естественный single-flight (один `loader()` на ключ, но внутри `StorageMetadataCache` через executor нет — это нужно добавить). |
| Background sweeper polling каждые N секунд для пересчёта всех WAITING | Overengineering: cache miss происходит 1 раз за life of cache (Pass 348 — eternal), polling не нужен. |
| Отдельный SSE-канал `healthReportsUpdate` (новый тип события) | `HEALTH_REPORTS` уже существует и принимается UI. Reuse. |
| WebSocket вместо SSE | Уже принято решение SSE (см. Knowledge `sse/domain.md`, раздел «Архитектурные решения»). |

### 4. Один важный нюанс: `StorageMetadataCache` уже запускает fill в background для async метода, но НЕ для sync метода

В `StorageMetadataCache.getFileExists` (sync, строка 114): cache miss → `loader()` → `upsert(...)` — fill происходит **синхронно** в caller-thread. После возврата cache уже заполнен.

В `StorageMetadataCache.getFileExistsAsync` (строка 397): cache miss → `CompletableFuture.completedFuture(null)` + fire-and-forget `cacheFillerExecutor.submit { ... }` — fill в background, callback срабатывает **после** upsert.

**Для спеки #80**: нам нужен callback в **обоих** случаях. В sync методе callback сработает **сразу** после upsert (в caller-thread) — это безопасно. В async методе callback сработает после background fill (в executor thread) — это тоже безопасно.

Но: если cache уже заполнен (hit) → callback НЕ вызывается. Это правильно (US3/AC1 спеки: «на warm cache ноль лишних SSE»).

## Phase 1: Design & Contracts

### Key Entities

| Компонент | Изменение | Почему |
|---|---|---|
| `HealthReportStatus.WAITING` | +1 enum значение с `color = "#FFCCFF"` | FR-001 спеки, явный запрос владельца |
| `HealthReport.cachedFileExists` | +1 параметр `onFillComplete: (() -> Unit)? = null` | FR-003: после успешного fill — recompute + SSE |
| `HealthReport.getHealthReportList` | Если cache miss → `WAITING` вместо `ERROR` для fileExists/fileIsActual | FR-002 спеки |
| `StorageMetadataCache.getFileExists` | Без изменений (callback пробрасывается caller'ом через inline lambda) | Минимальная правка, см. research §4 |
| `StorageMetadataCache.getFileExistsAsync` | Без изменений | То же самое |
| `HealthReport.recomputeAndBroadcast` | Без изменений | Уже принимает songId, шлёт SSE `HEALTH_REPORTS` |

### Interface Contracts

#### `HealthReportStatus` (updated)

```kotlin
enum class HealthReportStatus(val color: String) {
    OK(color = "#99FF99"),
    WARNING(color = "#99CCFF"),
    IN_PROGRESS(color = "#FFFF99"),  // repair-loop (НЕ трогаем)
    WAITING(color = "#FFCCFF"),     // NEW: cache miss, async fill
    ERROR(color = "#FF9999"),
    FATAL_ERROR(color = "#FF0000"),
}
```

#### `HealthReport.Companion.cachedFileExists` (updated)

```kotlin
/**
 * @param onFillComplete callback, вызываемый ПОСЛЕ успешного fill кеша
 *   (как для sync, так и для async случая). Если кеш уже был заполнен
 *   (hit) — callback НЕ вызывается. Используется для recompute+SSE после
 *   cold-start (спека #368).
 */
fun cachedFileExists(
    source: String,
    bucket: String,
    fileName: String,
    loader: () -> Boolean,
    onFillComplete: (() -> Unit)? = null,
): Boolean
```

**Backward compatibility**: новый параметр `onFillComplete` имеет default `null` → все 8 существующих вызовов в `HealthReport.kt` (строки 466, 484, 664, 736, 800, 1051, 1115) **не требуют изменений**.

#### `cachedFileExistsAsync` (updated)

```kotlin
/**
 * @param onFillComplete callback, вызываемый ПОСЛЕ background fill
 *   (cache miss case). Hit case — callback НЕ вызывается.
 */
fun cachedFileExistsAsync(
    source: String,
    bucket: String,
    fileName: String,
    loader: () -> Boolean,
    onFillComplete: (() -> Unit)? = null,
): CompletableFuture<Boolean?>
```

**Backward compatibility**: параметр default `null` → существующий вызов (HealthReport.kt:963) не требует изменений.

### SLF4J Logging Convention

Новая категория: `infra.cache.storage.waiting` (FR-005 спеки).

```kotlin
// Per-cache-miss (sync и async оба)
INFO  [infra.cache.storage.waiting] source=LOCAL bucket=karaoke fileName=song-12345.mp4
      status=FILLED durationMs=42
WARN  [infra.cache.storage.waiting] source=LOCAL bucket=karaoke fileName=song-12345.mp4
      status=FAILED durationMs=5000 error="Connection refused"
```

Это отдельная категория от `infra.cache.storage` (Pass 344) — там логируется `cache:miss`/`cache:write-through`/`cache:refresh`. Здесь — специфичные события fill с duration (для hit rate анализа).

### Wiring

`HealthReport.attachStorageMetadataCache` (HealthReport.kt:100) уже подключает `StorageMetadataCache` — никаких изменений в DI не нужно. `StorageCircuitBreaker` уже подключён (Pass 364) — тоже без изменений.

`recomputeAndBroadcast` уже принимает `songId`, `database`, `storageService`, `storageApiClient` — все эти параметры уже доступны в `HealthReport.companion object` через `@JvmStatic` функции.

### Quickstart Validation Guide

См. `quickstart.md` (детатный сценарий).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Нет | Нет нарушений Constitution | — |

## Open Questions Resolved

| Вопрос | Решение |
|--------|---------|
| Как передать songId в fill completion, если `StorageMetadataCache` оперирует только bucket/fileName? | Callback пробрасывается через `cachedFileExists` от caller'а (HealthReport.kt), который имеет `song` в замыкании. `StorageMetadataCache` ничего не знает про Song — separation of concerns. |
| Нужно ли менять StorageMetadataCache? | НЕТ. Метод `getFileExists` синхронный — callback сработает inline после `upsert`. Метод `getFileExistsAsync` уже запускает fill в background — добавляем callback в lambda submit. |
| Что если HTTP-запрос уже завершился, а fill ещё в background? | Callback сработает в executor thread, `recomputeAndBroadcast` пересчитает и пошлёт SSE. Клиент получит обновление даже после закрытия HTTP-ответа. |
| Что если MinIO недоступен (loader бросил exception)? | `cachedFileExists` (sync) — loader exception пробрасывается (текущее поведение). Меняется только happy path: success → callback. Для async — exception логируется (Pass 364), callback НЕ вызывается. Согласно US2/AC3 спеки, система остаётся в WAITING. |
| Single-flight на fill? | Уже реализовано через `StorageMetadataCache`: один cache key → одна строка в БД → `upsert` через `ON CONFLICT DO UPDATE` (Pass 348). Два параллельных вызова на один ключ не создают две записи. Это и есть single-flight. |