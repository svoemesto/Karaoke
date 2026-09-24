# Feature Specification: Сброс кеша хранилища для песни и страницы

**Feature Branch**: `446-storage-cache-reset-button`

**Created**: 2026-09-24

**Status**: Draft

**Input**: Пользователь: «Думаю что имеет смысл сделать метод "Сбросить кеш для песни"
и "Сбросить кеш для выбранных песен". Поместить на кнопку в футере таблицы песен,
там же где "Исправить всё"». Повод — инцидент после переезда (#165): кеш
`tbl_storage_metadata_cache` хранил устаревшие `exists=true` от старого MinIO, из-за
чего HealthReport показывал «0 ошибок» при реально отсутствующих файлах.

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#180` («Кнопки сброса кеша хранилища (для песни и для страницы)»).
- **Title**: «Кнопки сброса кеша хранилища (для песни и для страницы)».
- **Created in OpenProject**: 2026-09-24.
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 180` — выполнено 2026-09-24
     (assignee + `In progress` через PATCH, т.к. CLI `claim-issue` падает на смене
     assignee для Task — прецедент #152).
  2. **Add comment с отчётом** (после merge):
     `bash tools/tracker.sh add-comment 180 --file specs/446-storage-cache-reset-button/report.md`.
  3. **Mark review** (после add-comment): `bash tools/tracker.sh mark-review 180`.
  4. **Close** (owner, после ревью): `bash tools/tracker.sh close-issue 180`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-24
- **Grep-запросы**:
  1. `grep -rln "StorageMetadataCache|storage-metadata-cache|refresh-all" knowledge/`
     → `caching/domain.md`, `caching/components/web-caches.md`, `storage/domain.md`,
     `storage/components/storage-api-client.md`, `health/components/health-report.md`,
     `health/components/health-report-batch-pool.md`, `monitoring/components/log-categories.md`,
     `sse/domain.md`.
  2. `grep -rln "healthReport|HealthReport|canResolve" knowledge/`
     → `health/*`, `storage/*`, `monitoring/*`.
  3. `grep -rn "refresh|manual|инвалид" knowledge/domains/storage knowledge/domains/health`
     → эндпоинты `refresh`/`refresh-all`, поведение HealthReport при miss.
  4. `grep -n "storageFileName =" karaoke-app/.../HealthReport.kt` → 11 формул имён файлов
     (audio/стемы/MP3/картинки альбома и автора).

### Knowledge files consulted

- [`knowledge/domains/caching/components/web-caches.md`](../../knowledge/domains/caching/components/web-caches.md)
  — готовые паттерны кеша; прецедент #339 (не изобретать структуру кеша).
- [`knowledge/domains/storage/domain.md`](../../knowledge/domains/storage/domain.md)
  — `StorageMetadataCache`, `tbl_storage_metadata_cache`, hot paths, ручной refresh.
- [`knowledge/domains/health/components/health-report.md`](../../knowledge/domains/health/components/health-report.md)
  — `HealthReport`, `canResolve`, `StorageMetadataCache.peekFileExists`, repair-loop.
- [`knowledge/domains/storage/components/storage-api-client.md`](../../knowledge/domains/storage/components/storage-api-client.md)
  — `recordUpload`/`recordDelete` write-through, кеш-ключи.
- [`docs/features/storage-metadata-cache.md`](../../docs/features/storage-metadata-cache.md)
  — контракт кеша, `DELETE /api/health/cache/refresh[-all]`.
- [`knowledge/domains/persistence/domain.md`](../../knowledge/domains/persistence/domain.md)
  — `KaraokeDbTable`, raw JDBC.

### Прецедент (почему фича нужна)

2026-09-24 (после переезда #165): `karaoke-app` писал картинки в старый MinIO, а
persistent-кеш REMOTE хранил `exists=true`. HealthReport доверяет кешу
(`peekFileExists`) → «0 ошибок» при реально отсутствующих файлах → картинка альбома
не показывалась, плеер отказывал. Сброс потребовал ручного `curl` с admin-машины.
Нужна **UI-кнопка** — штатный способ сбросить кеш песни/страницы.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Сброс кеша одной песни (Priority: P1)

Как администратор, я открыл health-report песни, вижу «ОК», но подозреваю, что
файлы на самом деле пропали (или, наоборот, появились). Хочу одной кнопкой сбросить
кеш и увидеть актуальное состояние.

**Why this priority**: основной сценарий инцидента; без него админ не может
самостоятельно разрулить рассинхрон.

**Independent Test**: открыть `HealthReportTable` песни → нажать «Сбросить кеш» →
статусы переходят в WAITING, затем (после фоновой проверки) в актуальные (OK/ERROR).

**Acceptance Scenarios**:

1. **Given** health-report песни загружен, **When** нажата «Сбросить кеш»,
   **Then** backend удаляет все кеш-строки файлов этой песни (LOCAL+REMOTE),
   фронт перезапрашивает список и получает WAITING/актуальные статусы.
2. **Given** в песне есть картинка альбома, но в MinIO её нет, **When** сброшен кеш,
   **Then** health-report показывает ERROR «Файл в удалённом хранилище отсутствует»
   (а не ложный OK).

---

### User Story 2 — Сброс кеша для песен страницы (Priority: P1)

Как администратор, я хочу сбросить кеш для всех песен текущей страницы таблицы —
там же, где «Исправить всё».

**Why this priority**: массовые рассинхроны (после смены endpoint) затрагивают
много песен сразу.

**Independent Test**: в футере таблицы песен нажать «Сбросить кеш (страница)» →
подтвердить → кеш песней страницы удалён, статусы обновились.

**Acceptance Scenarios**:

1. **Given** открыта страница песен, **When** нажата «Сбросить кеш (страница)» и
   подтверждено, **Then** backend принимает `songsIds` текущей страницы и возвращает
   `{songs, rowsDeleted}`.
2. **Given** сброс выполнен, **When** `sendBatchHealthReports(ids)` отработал,
   **Then** бейджи health-report песней страницы обновились (через SSE).

---

### Edge Cases

- **Песня не найдена в БД** → пропускается, не валит весь запрос.
- **Кеш уже пуст** → `rowsDeleted=0`, ответ успешный (идемпотентно).
- **Очень много песен** (страница 500 строк × ~15 файлов × 2 источника) → batch DELETE
  чанками, без N+1 HTTP.
- **Картинка автора общая для песен автора** → ключ `"$author/$author.*"`; при сбросе
  песни он тоже чистится (это безопасно: перезаполнится).
- **Конкурентный ремонт** → сброс не трогает `tbl_processes`, только кеш.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Backend MUST предоставлять `POST /api/song/resetStorageCache` с
  параметром `ids` (`1;2;3`); возвращает `{songs: Int, rowsDeleted: Int}`.
- **FR-002**: Для каждой песни MUST вычисляться **все** её storage-имена (все
  `KaraokeFileType` с `LOCAL_STORAGE`/`REMOTE_STORAGE`, включая картинки альбома и
  автора) — переиспользуя существующую логику `HealthReport`, без дублирования формул.
- **FR-003**: Сброс MUST затрагивать **оба** источника (`LOCAL` и `REMOTE`).
- **FR-004**: Сброс MUST быть идемпотентным (повторный вызов → `rowsDeleted=0`).
- **FR-005**: `StorageMetadataCache` MUST получить batch-метод удаления ключей по
  списку `(source, bucket, fileName)` одним/несколькими `DELETE ... IN (...)`.
- **FR-006**: Frontend MUST иметь кнопку «Сбросить кеш» в `HealthReportTableHeader`
  (одна песня) и «Сбросить кеш (страница)» в футере `SongsTable.vue`.
- **FR-007**: После сброса фронт MUST перезапросить health-report (`loadHealthReportList`
  для песни; `sendBatchHealthReports` + SSE для страницы).
- **FR-008**: Кнопки MUST иметь подтверждение (как у «Repair All»).
- **FR-009**: Действие MUST логироваться категорией `infra.cache.storage`.

### Key Entities

- **`StorageMetadataCache`**: persistent-кеш в `tbl_storage_metadata_cache`
  (PK `source,bucket,file_name`).
- **`HealthReport`**: отчёт; источник формул `storageFileName` по `KaraokeFileType`.
- **`KaraokeFileType`**: enum с `locations`, `suffix`, `extention`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Сброс кеша одной песни — **≤2 с** (HTTP + DELETE, без MinIO-обходов).
- **SC-002**: Сброс страницы 500 песен — **≤10 с**.
- **SC-003**: После сброса health-report песни с отсутствующим файлом показывает
  **ERROR** (не ложный OK) — проверено на кейсе «Бог кастрирует слона».
- **SC-004**: Idempotency: повторный сброс → `rowsDeleted=0`.
- **SC-005**: Линтеры (ktlint, ESLint, Prettier) и CI — зелёные.

## Assumptions

- Сброс кеша **не** удаляет файлы и **не** меняет БД песен — только строки кеша.
- Перезаполнение кеша — существующий фоновый механизм (`HealthReportBatchPool`).
- Мультивыбор чекбоксами в таблице песен — **вне scope**; «выбранные» = песни
  текущей страницы (как у «Repair All»).
