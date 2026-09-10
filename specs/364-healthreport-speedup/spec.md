# Feature Specification: HealthReport Speedup

**Feature Branch**: `364-healthreport-speedup`

**Created**: 2026-09-10

**Status**: Draft

**Input**: User description: "Работа над задачей #75 в трекере OpenProject"

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

### Идентификация

- **Issue ID**: `#75`
- **Title**: «Ускорение HealthReport»
- **Created in OpenProject**: `2026-09-10`

### Workflow (NON-NEGOTIABLE при наличии Issue ID)

| Шаг | Команда | Когда | Кто |
|---|---|---|---|
| 1. **Claim** | `source .env.local-tracker && bash tools/tracker.sh claim-issue 75` | ПЕРЕД первой строкой кода спеки. Переводит `New` → `In progress`, assignee=ai-agent. | Agent |
| 2. **Pre-flight Knowledge** | `spec.md § Knowledge References` | Согласно Constitution Principle IX (см. ниже). | Agent |
| 3. **Work** | код, tests, knowledge updates | `/speckit.implement` | Agent |
| 4. **Add comment с отчётом** | `bash tools/tracker.sh add-comment 75 --file specs/364-healthreport-speedup/report.md` | После merge. Файл `report.md` — REQUIRED. | Agent |
| 5. **Mark review** | `bash tools/tracker.sh mark-review 75` | После публикации комментария. Переводит `In progress` → `In review`. | Agent |
| 6. **Close** | `bash tools/tracker.sh close-issue 75` | После ревью владельцем. | Agent или Owner |

### Проверки (validation)

- `tools/check-spec-issue-link.py` (NEW, Pass 349) проверяет в CI наличие секции `## OpenProject Tracking`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-10
- **Grep-запросы** (минимум 3, по релевантным ключевым словам задачи):
  1. `HealthReport` → файлы: `knowledge/domains/health/domain.md`, `knowledge/domains/health/components/health-report.md`, `knowledge/domains/storage/components/storage-api-client.md`, `knowledge/system/02-containers.md`
  2. `cach` → файлы: `knowledge/domains/caching/components/caching-patterns.md`, `knowledge/domains/caching/components/web-caches.md`, `knowledge/domains/storage/domain.md`
  3. `performance|speed|optimization` → файлы: `knowledge/adr/local-0003-shared-minio-image-cache.md`, `knowledge/adr/0001-raw-jdbc.md`

### Knowledge files consulted

- [`knowledge/domains/health/domain.md`](../../knowledge/domains/health/domain.md)
  — Bounded Context Health: HealthReport, actionsLocalFileSystem/Storage/RemoteStorage, autoRepair, reconcilePlayerReadinessFlags
- [`knowledge/domains/health/components/health-report.md`](../../knowledge/domains/health/components/health-report.md)
  — Детальная компонента HealthReport: getHealthReportList (точка входа: HealthReport.kt:1119), decision tree, repair-loop, Hot spots (~72k MinIO запросов без кеша → ~144 после Pass 344)
- [`knowledge/domains/caching/components/caching-patterns.md`](../../knowledge/domains/caching/components/caching-patterns.md)
  — 6 caching patterns: AtomicInteger, Cron, Dirty-flag, Denormalization, Async cold-start, Single-flight guard
- [`knowledge/domains/caching/components/web-caches.md`](../../knowledge/domains/caching/components/web-caches.md)
  — DedupCache + PollingCache (already used for StorageMetadataCache в Pass 344, #69 FIXED)
- [`knowledge/domains/storage/domain.md`](../../knowledge/domains/storage/domain.md)
  — Таблица «Storage calls from HealthReport»: ~72k → ~144/страница после кеша (#69 FIXED)
- [`knowledge/adr/local-0003-shared-minio-image-cache.md`](../../knowledge/adr/local-0003-shared-minio-image-cache.md)
  — MinIO TTL cache, cache key = immutable for entity version

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Admin sees Songs table faster (Priority: P1)

**Why this priority**: Основная жалоба из issue #75 — страница «Songs» в админке загружается медленно из-за HealthReport. Это рабочее место редактора — скорость критична.

**Independent Test**: Открыть страницу Songs в webvue3 с ≥10 песнями, замерить время до появления данных в колонке HealthReport. После оптимизации время должно сократиться.

**Acceptance Scenarios**:

1. **Given** админ открыл страницу Songs с 20 песнями, **When** страница загружается, **Then** колонка HealthReport заполняется данными за ≤3 секунды (было ~20 секунд)
2. **Given** админ открыл страницу Songs с 1 песней (полностью «OK»), **When** страница загружается, **Then** HealthReport для этой песни готов за ≤1 секунды (было ~1 секунда, цель — ≤200 мс)

---

### User Story 2 — HealthReport вызывается асинхронно без блокировки UI (Priority: P2)

**Why this priority**: Сейчас HealthReport запрашивается синхронно для каждой песни на странице. Пользователь видит «прыгающие» данные по мере загрузки отчётов.

**Independent Test**: Открыть Songs page, визуально убедиться что данные HealthReport появляются синхронно для всех строк (без «догрузки» после появления таблицы).

**Acceptance Scenarios**:

1. **Given** админ открыл страницу Songs, **When** таблица отображается, **Then** HealthReport колонка отображается сразу для всех строк с валидными данными
2. **Given** один из HealthReport ещё считается, **When** админ кликает на строку, **Then** детальный отчёт доступен немедленно (кеш уже готов)

---

### User Story 3 — Repair-loop не блокирует UI (Priority: P3)

**Why this priority**: После запуска «Починить всё» пользователь раньше не мог работать со страницей Songs.

**Independent Test**: Запустить repair для 5 песен, параллельно открыть страницу Songs.

**Acceptance Scenarios**:

1. **Given** запущен repair для 5 песен, **When** админ открывает страницу Songs, **Then** страница загружается без блокировки (HealthReport берётся из кеша или пересчитывается в фоне)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Система ДОЛЖНА обрабатывать HealthReport для одной «OK» песни (без ошибок) за ≤200 мс при локальном MinIO
- **FR-002**: Система ДОЛЖНА логировать время каждой sub-operation внутри `getHealthReportList` с метрикой `infra.health.report.duration`. Формат: `infra.health.report.duration type=<KaraokeFileType> location=<location> operation=<operation> durationMs=<ms>`. Пример: `type=MLT_LOCAL_STORAGE operation=fileExists durationMs=12`. Логи включаются на этапе анализа (отладка), выключаются после выявления bottleneck'ов.
- **FR-003**: Система ДОЛЖНА использовать существующий `StorageMetadataCache` (Pass 344) для повторных проверок fileExists в рамках одной страницы
- **FR-004**: Система НЕ ДОЛЖНА делать повторных MinIO запросов для одного и того же файла в рамках одного `getHealthReportList`
- **FR-005**: Система ДОЛЖНА поддерживать параллельный вызов `getHealthReportList` для разных песен без блокировки
- **FR-006**: Система ДОЛЖНА использовать circuit breaker при обращении к MinIO: при недоступности storage — возвращать `FATAL_ERROR` (текст «Storage unavailable»), блокировать повторные вызовы на 30 секунд. Логировать переходы circuit breaker в `infra.health.circuit`.
- **FR-007**: При cache miss в `StorageMetadataCache` система ДОЛЖНА возвращать `IN_PROGRESS` placeholder немедленно, без блокировки HTTP-ответа, и фоново заполнять кеш. Cache hit возвращает реальные данные.

### Key Entities

- **HealthReport**: data class с одним нарушением по одной песне. Companion object (~2240 строк) содержит всю логику проверки.
- **Song**: содержит 4 `*Ready` флага, обновляемых через `reconcilePlayerReadinessFlags`.
- **StorageMetadataCache**: TTL-кеш для `fileExists`/`fileIsActual` (Pass 344, #69).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: HealthReport для полностью «OK» песни (без ошибок) обрабатывается за ≤200 мс — измерить через SLF4J-лог `infra.health.report.duration`
- **SC-002**: Страница Songs с 20 песнями загружается за ≤3 секунды — измерить через browser DevTools Network
- **SC-003**: Количество уникальных cache keys (ключей `StorageMetadataCache`), проверяемых на странице Songs, не превышает количество песен × 2 (было ~72k, стало ~144 после Pass 344, цель — ≤40). Cache hit = не делается MinIO-запрос.
- **SC-004**: Кеш `StorageMetadataCache` показывает hit rate ≥80% при повторной загрузке страницы Songs

## Clarifications

### Session 2026-09-10

- **Q: Как система должна обрабатывать ситуацию, когда MinIO (local storage) недоступен или отвечает медленно (>1 секунды)?**
  → **A: Fail fast + circuit breaker.** При недоступности MinIO возвращать `FATAL_ERROR` с текстом «Storage unavailable», без попытки retry. Circuit breaker блокирует повторные запросы к недоступному storage на 30 секунд. Это защищает UI от cascade failure и не скрывает реальные проблемы за ложными `OK`.

- **Q: Что должно происходить при «холодном» старте (cache miss), когда `StorageMetadataCache` пуст — какой fallback использовать?**
  → **A: Return empty/in-progress state immediately, compute in background.** При cache miss UI сразу показывает placeholder «Checking...» и фоново загружает данные. User Story 2 (async без блокировки) выполнено. Cache fill происходит асинхронно.

- **Q: Должны ли метрики `infra.health.report.duration` записываться для каждой sub-operation (отдельные замеры для `fileExists`, `fileIsActual`, `reconcilePlayerReadinessFlags`) или только на уровне всего `getHealthReportList`?**
  → **A: Per-file-type (5-8 замеров на песню).** Избыточный объём логов не проблема — на этапе анализа (задача #75) подробные логи важны для понимания, какой именно `KaraokeFileType` и какая операция занимает время. После выявления bottleneck'ов логи будут отключены. Формат: `infra.health.report.duration type=MLT_LOCAL_STORAGE operation=fileExists durationMs=12`.

## Assumptions

- Кеширование `StorageMetadataCache` (Pass 344) уже работает — это baseline для оптимизации.
- MinIO (local) отвечает за ≤10 мс при кешированном запросе; цель — убрать overhead с стороны JVM/Kotlin.
- Речь идёт о `karaoke-app` (admin) — HealthReport запускается из webvue3 через REST API.
- Метрики логируются в категорию `infra.health.report.*` — формат аналогичен `infra.cache.storage`.
- При недоступности MinIO система возвращает `FATAL_ERROR` (circuit breaker active) — это не блокирует UI.
- При cache miss система возвращает `IN_PROGRESS` placeholder сразу, cache fill асинхронный.
