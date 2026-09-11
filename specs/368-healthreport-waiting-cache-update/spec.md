# Feature Specification: HealthReport WAITING status + SSE re-compute on cache fill

**Feature Branch**: `368-healthreport-waiting-cache-update`

**Created**: 2026-09-11

**Status**: Draft

**Input**: User description: "Работа над задачей #80 в трекере OpenProject"

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

### Идентификация

- **Issue ID**: `#80`
- **Title**: «Ускорение HealtReport-2»
- **Created in OpenProject**: `2026-09-11`

### Workflow (NON-NEGOTIABLE при наличии Issue ID)

| Шаг | Команда | Когда | Кто |
|---|---|---|---|
| 1. **Claim** | `source .env.local-tracker && bash tools/tracker.sh claim-issue 80` | ПЕРЕД первой строкой кода спеки. Переводит `New` → `In progress`, assignee=ai-agent. | Agent |
| 2. **Pre-flight Knowledge** | `spec.md § Knowledge References` | Согласно Constitution Principle IX (см. ниже). | Agent |
| 3. **Work** | код, tests, knowledge updates | `/speckit.implement` | Agent |
| 4. **Add comment с отчётом** | `bash tools/tracker.sh add-comment 80 --file specs/368-healthreport-waiting-cache-update/report.md` | После merge. Файл `report.md` — REQUIRED. | Agent |
| 5. **Mark review** | `bash tools/tracker.sh mark-review 80` | После публикации комментария. Переводит `In progress` → In review. | Agent |
| 6. **Close** | `bash tools/tracker.sh close-issue 80` | После ревью владельцем. | Agent или Owner |

### Проверки (validation)

- `tools/check-spec-issue-link.py` (Pass 349) проверяет в CI наличие секции `## OpenProject Tracking`.

### Прецедент

Задача #80 — продолжение #75 («Ускорение HealthReport», спека #364, Pass 364).
После Pass 364 в `StorageMetadataCache` добавлен async cold-start (FR-007 спеки #364):
при cache miss UI сразу показывает placeholder и фоново заполняет кеш. Но
**визуальное поведение осталось некорректным**: если `getHealthReportList` вызывается
в момент, когда кеш ещё не заполнен, пользователь видит 3 ERROR-записи (по одной
на каждый location: LOCAL_FILESYSTEM / LOCAL_STORAGE / REMOTE_STORAGE), а после
фактического заполнения кеша ошибки остаются — UI не получает SSE-сигнал
пересчитать HealthReport.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

> **Прецедент**: 2026-09-09, spec #339 — агент пропустил Knowledge-first
> pre-flight и изобрёл форму кеша вместо паттернов из
> `knowledge/domains/caching/components/caching-patterns.md`. Без заполненной
> секции спека **НЕ ДОЛЖНА** переходить в `/speckit.plan`. См. `AGENTS.md`
> MUST #0, Constitution Principle IX.

### Pre-flight log

- **Дата pre-flight**: 2026-09-11
- **Grep-запросы** (минимум 3, по релевантным ключевым словам задачи):
  1. `HealthReport` → файлы: `knowledge/domains/health/domain.md`, `knowledge/domains/health/components/health-report.md`, `knowledge/system/02-containers.md`
  2. `StorageMetadataCache` → файлы: `knowledge/domains/storage/domain.md`, `knowledge/domains/caching/components/web-caches.md`, `specs/344-storage-metadata-cache/spec.md`, `specs/364-healthreport-speedup/spec.md`
  3. `WAITING|SseNotification|HEALTH_REPORTS|cache fill|async refresh` → файлы: `knowledge/domains/sse/domain.md`, `knowledge/domains/caching/components/caching-patterns.md`, `knowledge/adr/local-0003-shared-minio-image-cache.md`

### Knowledge files consulted

- [`knowledge/domains/health/domain.md`](../../knowledge/domains/health/domain.md)
  — Bounded Context Health: HealthReport data class, HealthReportStatus enum
  (OK / WARNING / ERROR / FATAL_ERROR / IN_PROGRESS), `recomputeAndBroadcast`,
  contract: `solutionActions: List<() -> Unit>`, repair-loop
- [`knowledge/domains/health/components/health-report.md`](../../knowledge/domains/health/components/health-report.md)
  — Детальная компонента HealthReport: decision tree (Pass 344 + Pass 364),
  `recomputeAndBroadcast` (HealthReport.kt:2153), `cachedFileExistsAsync`,
  `getHealthReportList` (HealthReport.kt:1119), `reconcilePlayerReadinessFlags`,
  FR-007 спеки #364 (async cold-start placeholder)
- [`knowledge/domains/sse/domain.md`](../../knowledge/domains/sse/domain.md)
  — `SseNotification` / `SseNotificationType` enum, тип события
  `HEALTH_REPORTS`, `SNS.send(SseNotification.healthReports(...))` — единственный
  контракт для real-time уведомлений UI
- [`knowledge/domains/storage/domain.md`](../../knowledge/domains/storage/domain.md)
  — Таблица «Storage calls from HealthReport», `StorageMetadataCache`
  (Pass 344), `cachedFileExists` в `actionsLocalStorage`
- [`knowledge/domains/caching/components/caching-patterns.md`](../../knowledge/domains/caching/components/caching-patterns.md)
  — Паттерн «Async cold-start» (HTTP-тред возвращает fallback, refresh в
  `bgExecutor`), «Single-flight guard» (один поток запускает refresh)
- [`knowledge/domains/caching/components/web-caches.md`](../../knowledge/domains/caching/components/web-caches.md)
  — `PollingCache<V>` (Pass 344, уже используется в `karaoke-app`),
  TTL-кеш с lazy cleanup, нет single-flight на первый loader
- [`knowledge/adr/local-0003-shared-minio-image-cache.md`](../../knowledge/adr/local-0003-shared-minio-image-cache.md)
  — Прецедент MinIO TTL + scheduled cleanup, decision: cache key immutable
  for entity version, write-through invalidation при Song.save()
- [`specs/364-healthreport-speedup/spec.md`](../../specs/364-healthreport-speedup/spec.md)
  — FR-007: «При cache miss в `StorageMetadataCache` система ДОЛЖНА возвращать
  `IN_PROGRESS` placeholder немедленно, без блокировки HTTP-ответа, и фоново
  заполнять кеш» — этот FR выполнен, но UI не получает SSE-сигнал пересчёта
  после fill, что и есть баг #80

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Admin видит WAITING-индикатор вместо ошибки при первом открытии песни (Priority: P1)

**Why this priority**: Это базовая UX-корректность: пользователь не должен видеть
ложные ошибки (3 ERROR-записи), пока система только проверяет наличие файлов.
Цвет `FFCCFF` (WAITING) делает это состояние явно отличным от ERROR.

**Independent Test**: Очистить `StorageMetadataCache` (рестарт `karaoke-app`),
открыть страницу Songs. Все `FILE_VIOLATION`-записи для песни, где файлы
реально есть, должны отображаться с цветом `#FFCCFF` и `HealthReportStatus = WAITING`
до момента заполнения кеша. После заполнения кеша (≤2 секунды) — `OK` без
перезагрузки страницы.

**Acceptance Scenarios**:

1. **Given** `StorageMetadataCache` пуст (cold start) и песня со всеми файлами
   на месте, **When** админ открывает страницу Songs, **Then** HealthReport
   для этой песни содержит 3 записи со статусом `WAITING` (цвет `#FFCCFF`)
   в течение ≤500 мс (HTTP-ответ)
2. **Given** `StorageMetadataCache` заполнился в фоне (через ~2 сек), **When**
   заполнение завершилось, **Then** UI получает SSE-событие `HEALTH_REPORTS`,
   и эти 3 записи пересчитываются в `OK` БЕЗ перезагрузки страницы
3. **Given** у песни реально есть нарушение (например, отсутствует MP3 на
   удалённом хранилище), **When** админ открывает страницу Songs, **Then**
   только реально нарушенные location показывают `ERROR`, а остальные
   (которые ещё проверяются) — `WAITING`

### User Story 2 — Race condition: cache fill завершается во время показа страницы (Priority: P2)

**Why this priority**: Без SSE-обновления пользователь видит устаревшие данные
(ERROR вместо OK) даже после фактического заполнения кеша. Это регрессия
по сравнению с Pass 344 (где cache hit сразу возвращал OK).

**Independent Test**: Открыть страницу Songs с 20 песнями, подождать 5 секунд.
Все песни, у которых `StorageMetadataCache` заполнился, должны отображаться
с `OK`-статусом. Если через 5 секунд какие-то записи остались `WAITING`,
проверить, что есть активный `bgExecutor` task на их заполнение.

**Acceptance Scenarios**:

1. **Given** 20 песен на странице Songs, `StorageMetadataCache` заполняется
   постепенно (по ~5 песен/сек), **When** админ наблюдает страницу, **Then**
   записи переходят `WAITING → OK` по мере заполнения кеша (без F5)
2. **Given** `StorageMetadataCache.getOrCompute` запустил фоновый fill, **When**
   fill завершился успешно, **Then** система отправляет SSE `HEALTH_REPORTS`
   с обновлённым списком отчётов для затронутых песен (изменённые песни =
   те, для которых был fill)
3. **Given** `StorageMetadataCache.getOrCompute` запустил фоновый fill, **When**
   fill завершился с ошибкой (MinIO недоступен), **Then** система отправляет
   SSE `HEALTH_REPORTS` со статусом `WAITING` (не возвращаемся к `ERROR` —
   продолжаем показывать «в процессе проверки»)

### User Story 3 — Backward compatibility: SSE не отправляется, если cache уже hit (Priority: P3)

**Why this priority**: Не засорять SSE-канал лишними событиями. Если cache
hit произошёл синхронно (без background fill), UI уже видит корректные данные
— никаких SSE-обновлений не нужно.

**Independent Test**: После того как `StorageMetadataCache` заполнен для всех
20 песен, перезагрузить страницу Songs. Все записи должны быть `OK` без
`WAITING`-переходов; никаких лишних SSE-событий `HEALTH_REPORTS` в Network panel.

**Acceptance Scenarios**:

1. **Given** `StorageMetadataCache` уже содержит записи для всех файлов,
   **When** админ открывает страницу Songs, **Then** все HealthReport-записи
   возвращаются как `OK` (синхронно, ≤200 мс), SSE `HEALTH_REPORTS` НЕ
   отправляется повторно для тех же песен
2. **Given** страница Songs открыта, **When** пользователь редактирует песню
   (не связанную с cache fill), **Then** никаких лишних SSE `HEALTH_REPORTS`
   для других песен не отправляется

### Edge Cases

- **Что если MinIO упал в момент fill?** Cache loader бросает exception,
  `bgExecutor` task завершается с ошибкой. Согласно US2/AC3 — система отправляет
  SSE со статусом `WAITING` (не возвращаемся к `ERROR`). Через некоторое время
  можно попробовать снова.
- **Что если пользователь закрыл вкладку до заполнения кеша?** SSE-события
  адресованы конкретным `userId/tabId`, если вкладка ушла — событие просто
  не доставится. Никаких side effects.
- **Что если HealthReportList уже содержит WAITING-записи (cold start нескольких
  песен), и пришёл первый cache fill?** Система пересчитывает HealthReport для
  всех песен, у которых WAITING-зависит от заполненной cache-key. Если ключ
  всё ещё не заполнен — оставляем WAITING.
- **Что если одновременно несколько bgExecutor tasks заполняют разные ключи
  для одной песни?** Каждое завершение task'а должно вызвать пересчёт
  HealthReport для затронутой песни. Может быть N SSE-событий подряд —
  допустимо (UI просто перерисует последний).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Система ДОЛЖНА ввести новое значение `WAITING` в enum
  `HealthReportStatus` с цветом `#FFCCFF` (по запросу задачи #80).
- **FR-002**: При cache miss в `StorageMetadataCache` (Pass 344) система ДОЛЖНА
  возвращать `WAITING` placeholder для всех `fileExists`/`fileIsActual` операций
  в `getHealthReportList`, а не `ERROR` (продолжение FR-007 спеки #364).
- **FR-003**: После асинхронного заполнения `StorageMetadataCache` система
  ДОЛЖНА вызвать `recomputeAndBroadcast(songId)` для всех песен, чьи
  `HealthReport`-записи зависели от заполненной cache-key. Это рассылает SSE
  `HEALTH_REPORTS` с обновлённым `errorsOnly`-списком.
- **FR-004**: `recomputeAndBroadcast` ДОЛЖЕН вызываться из completion-callback'а
  `StorageMetadataCache.getOrCompute`, а не изнутри `getHealthReportList`.
  Это гарантирует, что пересчёт происходит **после** фактического заполнения
  кеша.
- **FR-005**: Система ДОЛЖНА логировать cache fill событие через SLF4J-категорию
  `infra.cache.storage.waiting` в формате:
  `INFO [infra.cache.storage.waiting] songId=<id> bucket=<b> name=<n> status=<OK|FAILED> durationMs=<ms>`
  для последующего анализа hit rate.
- **FR-006**: Система НЕ ДОЛЖНА отправлять SSE `HEALTH_REPORTS`, если cache
  hit произошёл синхронно (без background fill) и HealthReportList не изменился
  (то есть пересчёт даёт идентичный список DTO).
- **FR-007**: Система ДОЛЖНА поддерживать bulk-warm-up: при cold start
  `karaoke-app`, если админ открывает страницу Songs с 20 песнями, все 20 песен
  получают WAITING-статус, фоновый fill обрабатывает их по мере сил, и SSE
  приходит по мере готовности каждой песни.
- **FR-008**: `HealthReportStatus.WAITING` ДОЛЖЕН иметь цвет `#FFCCFF` (по
  явному запросу задачи #80 — не путать с `IN_PROGRESS` `#FFFF99`).
- **FR-009**: Single-flight guard: если несколько HTTP-запросов для одной
  песни приходят одновременно (например, refresh страницы во время fill), только
  один fill запускается; остальные получают тот же `WAITING` placeholder и ждут
  SSE от единственного fill.

### Key Entities

- **`HealthReportStatus`** (existing enum): добавляется значение `WAITING` с
  цветом `#FFCCFF`. Расположение в `karaoke-app/.../HealthReportStatus.kt`.
- **`StorageMetadataCache`** (existing, Pass 344): получает callback
  `onFillComplete: (key, result) -> Unit`, который пересчитывает HealthReport
  для затронутых песен.
- **`HealthReport`** (existing): `getHealthReportList` использует `WAITING` вместо
  `ERROR` при cache miss в `cachedFileExists`.
- **`SseNotification.HEALTH_REPORTS`** (existing): единственный контракт UI
  обновления, пересчёт из `recomputeAndBroadcast`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: При cold start `karaoke-app` (пустой `StorageMetadataCache`) и
  открытии страницы Songs с 20 песнями — все песни получают `WAITING`-статус
  для 3 location (LOCAL_FILESYSTEM / LOCAL_STORAGE / REMOTE_STORAGE) в течение
  ≤500 мс (HTTP-ответ), без `ERROR`-записей.
- **SC-002**: В течение ≤3 секунд после cold start все 20 песен переходят в
  `OK` через SSE-обновления (без F5). Если MinIO недоступен — остаются в
  `WAITING` (не возвращаются к `ERROR`).
- **SC-003**: После прогрева `StorageMetadataCache` (≥80% hit rate) перезагрузка
  страницы Songs не порождает лишних SSE `HEALTH_REPORTS` событий (≤1 событие
  на cold start; ноль на тёплом кеше).
- **SC-004**: Логи `infra.cache.storage.waiting` показывают hit rate ≥80%
  после полного прогрева (первые 60 секунд после cold start). Это baseline
  для будущих оптимизаций.
- **SC-005**: Никаких регрессий в существующих сценариях: `IN_PROGRESS`
  (repair-loop) продолжает работать, `ERROR`/`FATAL_ERROR` показываются
  корректно для реальных нарушений, `OK` для согласованного состояния.

## Clarifications

### Session 2026-09-11

- **Q: Какое значение `HealthReportStatus` использовать для cache miss —
  ввести новое `WAITING` или переиспользовать существующее `IN_PROGRESS`?**
  → **A: Новое `WAITING` с цветом `#FFCCFF`** (по явному запросу задачи #80 —
  владелец хочет отличать «проверяется, ждём результат» от «уже чиним,
  есть KaraokeProcess»). `IN_PROGRESS` остаётся для repair-loop.

- **Q: Должна ли система отправлять SSE на каждый cache fill (N событий) или
  группировать (debounce)?**
  → **A: Без debounce — каждое cache fill порождает отдельный SSE
  `HEALTH_REPORTS`.** UI (webvue3) просто перерисует колонку HealthReport на
  последний список. Debounce добавляет сложности без явного выигрыша
  (20 песен × 3 location = 60 событий за 3 секунды = 20 событий/сек,
  это ниже лимита SSE-канала).

- **Q: Что делать если cache fill завершился с ошибкой (MinIO недоступен)?
  Возвращать ERROR или оставить WAITING?**
  → **A: Оставить WAITING.** Это поведение US2/AC3. ERROR означает «реальная
  проблема», а не «не знаем, MinIO не ответил». Через некоторое время
  можно попробовать снова (retry внутри `StorageMetadataCache`).

## Assumptions

- `StorageMetadataCache` (Pass 344, спека #344) уже работает в `karaoke-app`
  и сохраняет состояние между рестартами через `tbl_storage_metadata_cache`.
- `PollingCache<V>` (Pass 344) поддерживает completion-callback'и (проверить
  в `karaoke-app/services/PollingCache.kt`; если нет — добавить overload
  `getOrCompute(key, ttlSeconds, loader, onFillComplete: (V) -> Unit)`).
- `HealthReport.recomputeAndBroadcast` (HealthReport.kt:2153) уже
  используется в repair-loop и безопасен для повторного вызова (он же
  `idempotent` — пересчитывает из текущего состояния).
- UI webvue3 (`HealthReportView.vue`, Vuex `healthReport/store.js`) уже
  слушает SSE `HEALTH_REPORTS` и обновляет стор (Pass 341 P2 / спека #344).
- Цвет `#FFCCFF` — точно по запросу задачи #80 (владелец явно указал).
- `IN_PROGRESS` (repair-loop) остаётся как есть — это разные состояния.