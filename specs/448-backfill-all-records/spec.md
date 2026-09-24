# Feature Specification: Backfill etag/size по всем записям кеша

**Feature Branch**: `448-backfill-all-records`

**Created**: 2026-09-24

**Status**: Draft

**Input**: Владелец: «Кнопка "Заполнить etag/size кеша хранилища" заполняет поля для
записей, у которых exists. Надо сделать чтобы она проходила по всем записям — тогда
можно будет прогреть кеш». Повод — после ручного сброса REMOTE-кеша (#165) и наличия
80k+ строк `exists=false` в LOCAL-кеше, которые backfill не проверял.

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#181` («Backfill etag/size: проходить по всем записям (включая exists=false)»).
- **Title**: «Backfill etag/size: проходить по всем записям (включая exists=false)».
- **Created in OpenProject**: 2026-09-24.
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 181` — выполнено 2026-09-24
     (assignee + `In progress` через PATCH, прецедент #152).
  2. **Add comment**: `bash tools/tracker.sh add-comment 181 --file specs/448-backfill-all-records/report.md`.
  3. **Mark review**: `bash tools/tracker.sh mark-review 181`.
  4. **Close**: `bash tools/tracker.sh close-issue 181`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-24
- **Grep-запросы**:
  1. `grep -rln "backfill|etag|size IS NULL" knowledge/` →
     `storage/domain.md`, `storage/components/*`, `health/components/health-report.md`,
     `adr/local-0003-shared-minio-image-cache.md`.
  2. `grep -rn "backfillCacheEtagSize|loadRowsNeedingBackfill|decideBackfillAction" karaoke-app/src` →
     `CacheEtagSizeBackfill.kt`, `ApiController.kt`, `MainController.kt`, тест `CacheEtagSizeBackfillTest`.
  3. `docker exec karaoke-db psql` → **80 279 LOCAL + 933 REMOTE** строк с
     `exists=false` (старый фильтр их не видел).
  4. `grep -rn "getFileInfo" storage-api-client.md` → обе реализации возвращают
     `size=-1` при отсутствии объекта (это уже обрабатывает `decideBackfillAction`).

### Knowledge files consulted

- [`knowledge/domains/storage/domain.md`](../../knowledge/domains/storage/domain.md)
  — `StorageMetadataCache`, `tbl_storage_metadata_cache`, hot paths.
- [`knowledge/domains/storage/components/storage-api-client.md`](../../knowledge/domains/storage/components/storage-api-client.md)
  — `getFileInfo` обеих реализаций (`size=-1` при отсутствии), circuit-aware.
- [`knowledge/domains/caching/components/web-caches.md`](../../knowledge/domains/caching/components/web-caches.md)
  — паттерны кеша (precedent #339).
- [`docs/features/storage-metadata-cache.md`](../../docs/features/storage-metadata-cache.md)
  — контракт кеша, backfill (Pass 435).
- [`specs/435-cache-etag-size-backfill/spec.md`](../435-cache-etag-size-backfill/spec.md)
  — исходная фича (фильтр `exists=true`).

### Прецедент

2026-09-24: после переезда (#165) и сброса REMOTE-кеша владелец заметил, что
кнопка backfill не помогает «прогреть» кеш, т.к. обрабатывает только `exists=true`.
Фактически в LOCAL-кеше 80 279 `exists=false` строк никогда не проверяются —
backfill не может ни подтвердить их отсутствие, ни самокорректировать, если файл
появился.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Backfill проверяет все записи (Priority: P1)

Как администратор, я нажимаю «Заполнить etag/size кеша хранилища» и хочу, чтобы
**все** записи кеша (не только `exists=true`) были проверены через `getFileInfo`,
чтобы кеш стал консистентным с реальным MinIO.

**Why this priority**: без этого 80k+ записей `exists=false` остаются непроверенными.

**Independent Test**: запустить backfill → в логах `cache:backfill start total≈81213`;
по завершении SSE-сводка с `updated/missing`.

**Acceptance Scenarios**:

1. **Given** строка `exists=false`, файл в MinIO есть → **Then** `UPDATE` (exists=true + etag/size).
2. **Given** строка `exists=false`, файла нет → **Then** `MARK_MISSING` (exists=false + WARN).
3. **Given** строка `exists=true` с пустым etag/size → **Then** `UPDATE`.
4. **Given** remote circuit OPEN → **Then** REMOTE-строки пропускаются (SKIP).

---

### User Story 2 — Идемпотентность (Priority: P2)

Повторный запуск не перепроверяет уже заполненные строки (`exists=true` с etag/size).

**Acceptance Scenarios**:

1. **Given** прогон завершён, **When** повторный запуск, **Then** выборка пуста
   (или содержит только строки, снова ставшие требующими проверки).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Выборка backfill MUST включать **все** строки, требующие проверки:
  `NOT exists OR size IS NULL OR etag IS NULL OR etag = ''` (было: только `exists=true`).
- **FR-002**: Для каждой строки MUST получаться `StorageFileInfo` (LOCAL →
  `KaraokeStorageService`, REMOTE → `StorageApiClient`), как в Pass 435.
- **FR-003**: `size >= 0` → `UPDATE` (exists=true + etag/size); `size < 0` →
  `MARK_MISSING` (exists=false) + WARN.
- **FR-004**: REMOTE — circuit-aware (`isFastFail()` → SKIP).
- **FR-005**: Single-flight guard (`AtomicBoolean`) → `ALREADY_RUNNING`.
- **FR-006**: Прогресс `infra.cache.storage` каждые N строк; итог — SSE.
- **FR-007**: Поведение при нехватке времени/ошибках — per-row try/catch + счётчик.

### Non-Functional

- **NFR-001**: Не менять публичные сигнатуры storage-сервисов.
- **NFR-002**: Выборка ~81k строк — курсором в память (компактно), не держать MinIO-данные.
- **NFR-003**: Unit-тест на расширенную семантику `decideBackfillAction` (exists-независимость).

## Success Criteria *(mandatory)*

- **SC-001**: После прогона выборка backfill пуста (все проверены).
- **SC-002**: `exists=false`-строки, чьи файлы реально есть, становятся `exists=true`.
- **SC-003**: Идемпотентность: повторный запуск обрабатывает 0 «заполненных» строк.
- **SC-004**: Линтеры/тесты/CI зелёные.

## Assumptions

- `getFileInfo` для отсутствующего файла возвращает `size=-1` (обе реализации — проверено).
- Прогон 81k строк может занять минуты-часы (LOCAL statObject on-demand) — фон, SSE.
- Прогрев **удалённых** (сброшенных) ключей — не эта фича; её вернёт health-report
  или отдельный warm-обход по песням (вне scope).
