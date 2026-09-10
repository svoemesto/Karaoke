# Implementation Plan: HealthReport Speedup

**Branch**: `364-healthreport-speedup` | **Date**: 2026-09-10 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/364-healthreport-speedup/spec.md`

## Summary

Задача #75: ускорение HealthReport. Текущая проблема — `getHealthReportList` для полностью «OK» песни занимает ~1 секунду. Цель — ≤200 мс. Подход: добавить детальное логирование `infra.health.report.duration` для каждого `KaraokeFileType` × `KaraokeFileTypeLocation` × operation, чтобы выявить bottleneck'ы. Параллельно: circuit breaker для защиты от cascade failure при недоступности MinIO, async cold-start для cache miss.

## Technical Context

**Language/Version**: Kotlin 1.x / JDK 17

**Primary Dependencies**: `karaoke-app/.../HealthReport.kt` (companion object ~2240 строк), `StorageMetadataCache` (Pass 344, `PollingCache<V>`), `StorageCircuitBreaker` (Pass 351, спека #352)

**Storage**: PostgreSQL (raw JDBC), MinIO local (S3) через MinIO SDK

**Testing**: нет автотестов (CI не падает на @Disabled); верификация — пользователем

**Target Platform**: `karaoke-app` (admin machine), доступ через REST API `/api/health/getHealthReportList` → webvue3

**Performance Goals**: ≤200 мс на HealthReport для «OK» песни; ≤3 сек на страницу с 20 песнями; hit rate `StorageMetadataCache` ≥80%

**Constraints**: Не ломать существующий кеш `StorageMetadataCache`; не добавлять новых синхронных блокирующих вызовов; логирование должно быть выключаемым

**Scale/Scope**: 18k+ песен в БД; страница Songs показывает 20–100 песен за раз; каждый `getHealthReportList` проверяет 5–8 `KaraokeFileType` × 3 locations

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Self-contained pipeline | ✅ N/A | Нет внешних API в hot path |
| II. Raw JDBC + diff by hashes | ✅ N/A | Не затрагивает БД-схему |
| III. Two-DB sync | ✅ N/A | Нет синхронизации LOCAL↔SERVER |
| IV. Async queue + stdout parsing | ✅ N/A | Нет KaraokeProcess в этой задаче |
| V. Two frontends | ✅ N/A | Backend-only изменение |
| VI. Code standards (KDoc, linters) | ✅ Затронуто | KDoc для новых методов |
| VII. Cross-machine setup | ✅ N/A | Локальная оптимизация |
| VIII. Secrets | ✅ N/A | Нет секретов |
| IX. Knowledge-first | ✅ Пройден | Pre-flight выполнен, spec создана |

**Вердикт**: Constitution Check пройден. Нарушений нет.

## Project Structure

### Documentation (this feature)

```
specs/364-healthreport-speedup/
├── plan.md              # Этот файл
├── research.md          # Phase 0: NEEDS CLARIFICATION → не требуется (bottleneck выявляется через логирование)
├── data-model.md       # Phase 1: Key Entities (HealthReport, StorageMetadataCache, CircuitBreaker)
├── quickstart.md        # Phase 1: Валидация — как измерить latency
└── tasks.md            # Phase 2: (/speckit.tasks — НЕ создаётся здесь)
```

### Source Code (repository root)

```
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── HealthReport.kt                      # Основные изменения: логирование, circuit breaker, async cold-start
└── services/
    └── (новые утилиты если нужны)
```

**Structure Decision**: Все изменения в существующем `HealthReport.kt`. Никаких новых файлов не требуется для Phase 0-1. Phase 2 может добавить helper-классы для circuit breaker state machine.

## Phase 0: Research

**Research не требуется** — задача #75 сама является исследованием. Bottleneck'и выявляются через детальное логирование (`infra.health.report.duration`), а не через исследование кода. Known gaps в Knowledge показывают что `health-report.md` уже покрывает decision tree.

## Phase 1: Design & Contracts

### Key Entities

#### HealthReport.kt (существующий, изменения)

| Компонент | Что делает сейчас | Что добавить |
|-----------|-----------------|--------------|
| `getHealthReportList(song: Song)` :line 1119 | Перебирает все `KaraokeFileType` × locations, вызывает `actions*` | Добавить `logger` с категорией `infra.health.report.duration`; замерять время для каждого `KaraokeFileType` × operation |
| `actionsLocalStorage` :line 501 | Вызывает `storageService.fileExists` / `fileIsActual` | Добавить circuit breaker (по образцу `StorageCircuitBreaker.kt` Pass 351) |
| `actionsRemoteStorage` :line 789 | Вызывает `storageApiClientRemote.fileExists` | Добавить circuit breaker |
| `startRepairAll` :line 2259 | Repair-loop | Не изменяется |

#### StorageMetadataCache (существующий, Pass 344)

| Метод | Текущее поведение | Что добавить |
|-------|-----------------|--------------|
| `getOrCompute(key, ttlSeconds, loader)` | TTL 300s, async fill | При cache miss: return placeholder + background fill (FR-007) |

#### StorageCircuitBreaker (существующий, Pass 351)

Использовать существующий `StorageCircuitBreaker` из `karaoke-app/.../services/StorageCircuitBreaker.kt`. Модель:
- CLOSED → OPEN (после 3 последовательных failures) 
- OPEN → HALF_OPEN (через 30 секунд)
- HALF_OPEN → CLOSED (1 success) или OPEN (failure)
- При OPEN: выбросить `StorageUnavailableException` сразу, без вызова MinIO

### Interface Contracts

**REST endpoint** `/api/health/getHealthReportList` (существующий):

```json
// Request
{ "songId": "string" }

// Response (успех)
{
  "healthReports": [
    {
      "description": "MLT/LOCAL_FILESYSTEM",
      "healthReportType": "FILE_VIOLATION",
      "healthReportStatus": "OK",
      "canResolve": false,
      "problemText": "",
      "solutionText": ""
    }
  ],
  "songId": "string",
  "durationMs": 45
}

// Response (circuit breaker open)
{
  "healthReports": [],
  "songId": "string",
  "error": "Storage unavailable",
  "durationMs": 2
}
```

### SLF4J Logging Convention

Формат аналогичен `infra.cache.storage`:

```
// Per-file-type замер (FR-002):
INFO  [infra.health.report.duration] type=MLT_LOCAL_STORAGE operation=fileExists durationMs=12
INFO  [infra.health.report.duration] type=MLT_LOCAL_STORAGE operation=fileIsActual durationMs=3
INFO  [infra.health.report.duration] type=COVER_LOCAL_STORAGE operation=fileExists durationMs=8

// Circuit breaker events (FR-006):
INFO  [infra.health.circuit] state=CLOSED storage=local
WARN  [infra.health.circuit] state=OPEN storage=local reason="Connection refused"
INFO  [infra.health.circuit] state=HALF_OPEN storage=local
INFO  [infra.health.circuit] state=CLOSED storage=local
```

### Quickstart Validation Guide

**Как измерить latency HealthReport**:

1. **Включить детальное логирование** (FR-002):
   - В `karaoke-app` добавить SLF4J category `infra.health.report` в DEBUG/INFO
   - Пересобрать: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar`

2. **Замерить для одной «OK» песни**:
   ```bash
   curl -s "http://localhost:8899/api/health/getHealthReportList?songId=<ID>" | jq '.durationMs'
   # Ожидаем: ≤200 мс
   ```

3. **Проверить логи**:
   ```bash
   docker logs karaoke-app --tail 1000 2>&1 | grep 'infra.health.report.duration'
   # Формат: type=<KaraokeFileType> location=<location> operation=<operation> durationMs=<ms>
   ```

4. **Проверить circuit breaker**:
   - Остановить MinIO: `docker stop karaoke-minio`
   - Вызвать HealthReport: должен вернуть `error: "Storage unavailable"` за ≤5 мс
   - Запустить MinIO: `docker start karaoke-minio`
   - Подождать 30 сек: следующий вызов должен восстановиться

5. **Проверить cold-start cache miss**:
   - Очистить кеш: нет прямой команды (PollingCache чистится по TTL)
   - Открыть страницу Songs с 20 песнями
   - UI должен показать `IN_PROGRESS` placeholder ≤100 мс
   - Повторный вызов: данные готовы из кеша

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Нет | Нет нарушений Constitution | — |

## Open Questions Resolved

| Вопрос | Решение |
|--------|---------|
| Circuit breaker при недоступности MinIO | `StorageCircuitBreaker` (Pass 351) — переиспользовать |
| Cold-start cache miss | Return `IN_PROGRESS` + async fill (FR-007) |
| Гранулярность логирования | Per-file-type (5-8 замеров на песню) |
