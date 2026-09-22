# Feature Specification: Разъединение circuit breaker для local и remote storage (Pass 429, #153)

**Feature Branch**: `429-split-local-remote-circuit-breakers`
**Created**: 2026-09-22
**Status**: Draft
**Input**: Требование владельца: «Проблемы с удалённым хранилищем не должны влиять
на работу с локальным, и наоборот». Follow-up на Pass 351 (#352), Pass 364 (#75),
Pass 426 (#150), Pass 428 (#152).

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#153` («Разъединение circuit breaker: local и remote storage независимы»).
- **Title**: «Разъединение circuit breaker: local и remote storage независимы».
- **Created in OpenProject**: 2026-09-22.
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 153` — выполнено 2026-09-22
     (assignee=`ai agent`, статус `In progress`; PATCH вручную — `tracker.sh claim-issue`
     падает на смене assignee для Task в проекте Karaoke).
  2. **Add comment с отчётом** (после merge):
     `bash tools/tracker.sh add-comment 153 --file specs/429-split-local-remote-circuit-breakers/report.md`.
  3. **Mark review** (после add-comment): `bash tools/tracker.sh mark-review 153`.
  4. **Close** (owner, после merge + согласия на рестарт `karaoke-app`): `bash tools/tracker.sh close-issue 153`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-22
- **Grep-запросы** (минимум 3):
  1. `grep -rln "StorageCircuitBreaker\|circuit breaker\|circuit-breaker" knowledge/`
     → `storage/components/storage-api-client.md`, `storage/domain.md`,
     `health/components/health-report.md`, `monitoring/components/log-categories.md`.
  2. `grep -rn "KaraokeStorageServiceImpl\|local MinIO" knowledge/domains/storage/`
     → `domain.md`, `components/karaoke-storage-service.md`, `components/storage-flow.md` (поток 2).
  3. `grep -rn "circuit\|FastFail\|FATAL_ERROR" knowledge/domains/health/components/health-report.md`
     → строка 122-125: «`StorageCircuitBreaker` защищает local MinIO в `actionsLocalStorage`»
     — **документация расходится с кодом** (фактически breaker один и защищает remote).
  4. `grep -n "local\|LOCAL" specs/364-healthreport-speedup/spec.md`
     → FR-006 + Clarifications: «circuit breaker при обращении к MinIO» (без
     разделения local/remote); Assumption «MinIO (local) отвечает ≤10 мс».
  5. `grep -rn "decorate\|circuit" karaoke-app/src/main/kotlin/.../services/KaraokeStorageService.kt`
     → **0 hits** — локальный путь вообще без circuit.
  6. `grep -rn "storageCircuitBreaker\|cb.acquire" karaoke-app/src/main/kotlin/.../HealthReport.kt`
     → одно место (строка 638, `actionsLocalStorage`) использует **remote**-брейкер.

### Knowledge files consulted

- [`knowledge/domains/storage/domain.md`](../../knowledge/domains/storage/domain.md)
  — два бэкенда (local/remote MinIO), «Решение 2» (blocking vs reactive), hot paths #75.
- [`knowledge/domains/storage/components/storage-api-client.md`](../../knowledge/domains/storage/components/storage-api-client.md)
  — circuit в `decorate` (Pass 351/426/428).
- [`knowledge/domains/storage/components/karaoke-storage-service.md`](../../knowledge/domains/storage/components/karaoke-storage-service.md)
  — локальная реализация `KaraokeStorageServiceImpl`.
- [`knowledge/domains/storage/components/storage-flow.md`](../../knowledge/domains/storage/components/storage-flow.md)
  — Поток 2 (HealthReport): local и remote пути.
- [`knowledge/domains/health/components/health-report.md`](../../knowledge/domains/health/components/health-report.md)
  — секция «Circuit breaker (Pass 364)» — **расходится с кодом**, исправляем.
- [`knowledge/domains/monitoring/components/log-categories.md`](../../knowledge/domains/monitoring/components/log-categories.md)
  — категории `infra.cache.storage`, `infra.health.circuit`.
- [`specs/364-healthreport-speedup/spec.md`](../../specs/364-healthreport-speedup/spec.md)
  — FR-006 (исходный circuit).
- [`specs/352-storage-graceful-degradation/spec.md`](../../specs/352-storage-graceful-degradation/spec.md)
  — FSM circuit.

### Прецедент

Production-наблюдение 2026-09-22 (nsa-i9): нестабильность **remote** MinIO вызывала
каскад `UPLOAD_TO_LOCAL_STORE ... ERROR (данные не найдены)`, хотя локальный MinIO жив.
Причина — единый breaker на remote, который `actionsLocalStorage` читает для
**локального** пути. Обратная асимметрия: локальный MinIO **не защищён** circuit'ом
вовсе (нет `decorate`), поэтому его сбой блокирует вызовы без fail-fast.

**Вывод**: один breaker смешивает два независимых бэкенда. Нужны два.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Remote-сбой не ломает локальный путь (Priority: P1)

**Описание**: Когда remote MinIO недоступен (circuit remote = OPEN), локальные
операции (`actionsLocalStorage`, `UPLOAD_TO_LOCAL_STORE`) продолжают работать
нормально, если локальный MinIO жив.

**Why P1**: именно это порождает каскад ложных `ERROR` в проде.

**Independent Test**: Unit/интеграционный тест: remote-брейкер OPEN, локальный
CLOSED → `actionsLocalStorage` не возвращает FATAL_ERROR; local-брейкер не затронут.

**Acceptance Scenarios**:

1. **Given** remote circuit OPEN, local CLOSED, **When** `actionsLocalStorage`
   для песни с живым локальным MinIO, **Then** отчёт строится нормально (не
   `FATAL_ERROR «Удалённое хранилище недоступно»`).
2. **Given** remote circuit OPEN, **When** `getHealthReport`, **Then** local-часть
   не деградирует; remote-часть может вернуть FATAL_ERROR (изолированно).
3. **Given** серия remote-таймаутов, **When** смотрим local-брейкер, **Then**
   `failureCount` local = 0 (не инкрементируется от remote-сбоя).

### User Story 2 — Локальный сбой не ломает remote путь (Priority: P1)

**Описание**: Когда локальный MinIO недоступен, remote-операции продолжают
работать; local circuit открывается независимо и даёт fail-fast для local.

**Why P1**: симметричная изоляция — требование владельца.

**Independent Test**: local-брейкер OPEN, remote CLOSED → `StorageApiClient.fileExists`
работает; local `fileExists` fail-fast.

**Acceptance Scenarios**:

1. **Given** local circuit OPEN, **When** `storageService.fileExists` (local),
   **Then** fast-fail (без блокирующего MinIO-вызова), возврат `false` за <1ms.
2. **Given** local circuit OPEN, **When** `StorageApiClient.fileExists` (remote),
   **Then** normal remote-вызов (local-состояние не влияет).
3. **Given** локальный MinIO восстановился, **When** cooldown истёк, **Then**
   local circuit закрывается (probe success → CLOSED), как у remote.

### User Story 3 — Observability разделена по storage (Priority: P2)

**Описание**: Метрики и логи явно указывают `storage=local|remote`; reset
позволяет сбросить каждый независимо.

**Acceptance Scenarios**:

1. **Given** `GET /api/health/circuit-breaker`, **When** запрос, **Then** ответ
   содержит оба состояния (`local`, `remote`).
2. **Given** `POST /api/health/circuit-breaker/reset?storage=local`, **When** вызов,
   **Then** сбрасывается только local; remote не тронут.
3. **Given** `storage=remote`, **When** вызов, **Then** сбрасывается только remote.
4. **Given** без параметра, **When** вызов, **Then** сбрасываются оба (backward-compat).

## Requirements *(mandatory)*

### Functional

- **FR-001**: Система MUST иметь **два независимых** экземпляра
  `StorageCircuitBreaker` — local и remote, с раздельными `state`, `failureCount`,
  `openedAtMs`, метриками и watchdog.
- **FR-002**: Локальный путь (`KaraokeStorageServiceImpl.fileExists`/`getFileStat`/
  `getFileInfo`) MUST быть обёрнут **local**-брейкером (fail-fast при OPEN).
- **FR-003**: Remote-путь (`StorageApiClientImpl.fileExists`/`getFileInfo`) MUST
  использовать **remote**-брейкер (как сейчас, но явно квалифицированный).
- **FR-004**: `HealthReport.actionsLocalStorage` MUST использовать **local**-брейкер
  для проверки circuit (не remote); `actionsRemoteStorage` (и remote-проверки) —
  **remote**-брейкер.
- **FR-005**: Оба брейкера MUST использовать общие пороги из конфигурации
  (`storage.file-exists-timeout-seconds`, `storage.circuit-breaker-threshold`,
  `storage.circuit-breaker-cooldown-seconds`, watchdog-*), но независимое состояние.
- **FR-006**: Логи circuit MUST содержать `storage=local|remote` (соответствие
  фактическому бэкенду).
- **FR-007**: `GET /api/health/circuit-breaker` MUST возвращать оба состояния;
  `POST /api/health/circuit-breaker/reset` MUST поддерживать `?storage=local|remote`
  (без параметра — оба, backward-compatible). `GET /api/health/cacheStats` MUST
  содержать оба блока.
- **FR-008**: Сбой/восстановление одного брейкера MUST NOT изменять состояние,
  счётчики или watchdog другого.

### Non-Functional

- **NFR-001**: Local fail-fast при OPEN — возврат за <1ms без MinIO-вызова.
- **NFR-002**: Hot path не должен деградировать: декорирование local-методов не
  должно добавлять сетевых/потоковых overhead'ов сверх существующего вызова.
- **NFR-003**: `StorageCircuitBreaker` public API остаётся совместимым (тесты могут
  конструировать напрямую); добавляется необязательный параметр `storageType`.
- **NFR-004**: Поведение при CLOSED и успешных вызовах не меняется.
- **NFR-005**: Unit-тесты покрывают изоляцию (local OPEN ≠ remote OPEN), fail-fast,
  reset по storage.

### Key Entities

- **`StorageCircuitBreaker`** (MODIFY) — добавить `storageType: String` (для логов);
  добавить blocking-API `executeBlocking(operation, loader, emptyValue)` (для
  локального синхронного пути).
- **`StorageCircuitBreakerConfig`** (NEW) — `@Configuration` с двумя `@Bean`:
  `localStorageCircuitBreaker`, `remoteStorageCircuitBreaker`.
- **`StorageCircuitBreakerWiring`** (MODIFY) — attach обоих в `HealthReport`.
- **`HealthReport`** (MODIFY) — два static-поля (local/remote), корректная привязка.
- **`StorageApiClientImpl`** (MODIFY) — `@Qualifier("remoteStorageCircuitBreaker")`.
- **`KaraokeStorageServiceImpl`** (MODIFY) — `@Qualifier("localStorageCircuitBreaker")`
  + декорирование read-методов.
- **`CircuitBreakerController` / `CacheStatsController`** (MODIFY) — оба брейкера.

## Success Criteria *(mandatory)*

- **SC-001**: Remote circuit OPEN → local-путь не возвращает FATAL_ERROR (тест).
- **SC-002**: Local circuit OPEN → remote-путь работает (тест); local fast-fail <1ms.
- **SC-003**: Сброс по `?storage=` сбрасывает только выбранный (тест + curl).
- **SC-004**: Существующие тесты `StorageCircuitBreakerTest`/`StorageTimeoutInterruptTest` — PASS.
- **SC-005**: `:karaoke-app:ktlintCheck` 0, `bootJar` OK, `check-knowledge-structure.sh` 9/9,
  `gh pr checks` all PASS.

## Assumptions

1. **Общий config** для обоих брейкеров (решение владельца), раздельное только состояние.
2. **Local MinIO — same docker network** → local-таймаут обеспечивается OkHttp
   (connect 10s); main protection — circuit fail-fast, без искусственного timeout-потока.
3. **Blocking-API** `executeBlocking` для local (не Mono) — проще и без потоковых
   overhead'ов; interrupt-safe по аналогии с Pass 428.
4. **Single replica** — распределённый circuit не нужен.

## Out of Scope

- **Нестабильность самого remote-канала** — инфраструктурная задача.
- **Изменение FSM/порогов** — нет.
- **Раздельные config-свойства** — общие (решение владельца).
- **`KaraokeStorageService.uploadFile`/`downloadFile` circuit** — только read-методы
  health-path (не трогаем write/stream, чтобы не ломать большие передачи).
- **`StorageApiClientWeb` (karaoke-web)** — не затронут.

## Migration Path

### Что нужно изменить

- `StorageCircuitBreaker.kt` (MODIFY) — `storageType`, `executeBlocking`, логи.
- `StorageCircuitBreakerConfig.kt` (NEW) — два bean.
- `StorageCircuitBreakerWiring.kt` (MODIFY) — attach обоих.
- `HealthReport.kt` (MODIFY) — два static-поля + привязка local/remote.
- `StorageApiClient.kt` (MODIFY) — qualifier.
- `KaraokeStorageService.kt` (MODIFY) — qualifier + decorate read.
- `CircuitBreakerController.kt`, `CacheStatsController.kt` (MODIFY) — оба.
- Tests (ADD/MODIFY) — изоляция.
- `knowledge/domains/health/components/health-report.md` (MODIFY) — исправить
  расхождение (сейчас «защищает local», на деле remote).
- `knowledge/domains/storage/*` (MODIFY), `docs/features/storage-metadata-cache.md` (MODIFY).

### Что НЕ нужно менять

- FSM, watchdog, reset логика (переиспользуется).
- `StorageMetadataCache` — не затронута.
- Сигнатуры публичного API `KaraokeStorageService`/`StorageApiClient`.

## Validation

| Проверка | Ожидаемо |
|---|---|
| `:karaoke-app:test --tests "*StorageCircuitBreaker*"` | PASS |
| Новый тест изоляции local/remote | PASS |
| curl `GET /api/health/circuit-breaker` | оба состояния |
| curl `POST /api/health/circuit-breaker/reset?storage=local` | сброшен только local |
| `check-knowledge-structure.sh` | 9/9 OK |

## Rollback

`git revert <merge-commit>` — возврат к одному breaker (remote), `actionsLocalStorage`
снова читает его. Проблема каскада возвращается, но данные не страдают.

## Clarifications

### Session 2026-09-22

- **Q**: Объём — минимальный фикс или два независимых breaker'а?
  - **A**: Два независимых breaker'а, полная симметрия изоляции.
- **Q**: Нужен ли local fail-fast?
  - **A**: Да, симметрично remote (FATAL_ERROR «Локальное хранилище недоступно» при OPEN).
- **Q**: Config и API?
  - **A**: Общий config, endpoints для обоих (`GET` — оба; `reset?storage=`, без
    параметра — оба, backward-compatible).
