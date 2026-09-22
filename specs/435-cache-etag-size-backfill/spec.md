# Feature Specification: Backfill etag/size в tbl_storage_metadata_cache + кнопка (Pass 435, #159)

**Feature Branch**: `435-cache-etag-size-backfill`
**Created**: 2026-09-22
**Status**: Draft
**Input**: Задача владельца: кастом-функция, которая для всех записей
`tbl_storage_metadata_cache` с флагом `exists`, но пустыми `etag`/`size`, заполняет
эти поля; кнопка вызова — на главный экран админки; прогресс — в логах.
Follow-up на #158 (кеш getFileInfo).

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#159` («Backfill etag/size в tbl_storage_metadata_cache»).
- **Title**: «Backfill etag/size в tbl_storage_metadata_cache + кнопка на главной».
- **Created in OpenProject**: 2026-09-22.
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 159` — выполнено 2026-09-22.
  2. **Add comment**: `bash tools/tracker.sh add-comment 159 --file specs/435-cache-etag-size-backfill/report.md`.
  3. **Mark review**: `bash tools/tracker.sh mark-review 159`.
  4. **Close** (owner, после merge + рестарт): `bash tools/tracker.sh close-issue 159`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-22
- **Grep-запросы**:
  1. `grep -rln "storage_metadata_cache|StorageMetadataCache" knowledge/`
     → `storage/*`, `health/*`, `caching/*`, `monitoring/log-categories.md`.
  2. `grep -rln "customFunction|кастом|custom-function" knowledge/ specs/` →
     `system/utilities.md`, спеки (#141, #277 и др.) — паттерн «кнопка на главной + фоновый поток + SSE».
  3. `grep -rn "SNS.send|SseNotification.message" karaoke-app/src/main` →
     `ExportAlignmentDataset.kt`, `Utils.kt` — паттерн прогресса/итога.
  4. `docker exec karaoke-db psql` → **56531 LOCAL + 52679 REMOTE** строк
     `exists=true` с пустыми `etag`/`size`.
  5. `grep -rn "isFastFail" karaoke-app/src/main` → circuit-aware проверка (#157).

### Knowledge files consulted

- [`knowledge/domains/storage/components/storage-api-client.md`](../../knowledge/domains/storage/components/storage-api-client.md)
  — `getFileInfo`, circuit (Pass 434).
- [`knowledge/domains/health/components/health-report.md`](../../knowledge/domains/health/components/health-report.md)
  — use-case кеша.
- [`knowledge/domains/caching/components/web-caches.md`](../../knowledge/domains/caching/components/web-caches.md)
  — паттерны.
- [`knowledge/system/utilities.md`](../../knowledge/system/utilities.md) — `Utils.kt`.
- [`knowledge/domains/monitoring/components/log-categories.md`](../../knowledge/domains/monitoring/components/log-categories.md)
  — категории логов.

### Прецедент

2026-09-22: в БД `tbl_storage_metadata_cache` — `exists` заполнен почти везде, но
`etag`/`size` только у 813 LOCAL / 4608 REMOTE (остальные созданы через
`fileExists`, где etag/size = NULL). Именно поэтому #158 не мог отвечать
`fileIsActual` из кеша. Нужен разовый backfill.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Запустить backfill с главного экрана (Priority: P1)

**Описание**: Админ нажимает кнопку «Backfill etag/size кеша хранилища» →
подтверждение → фоновая обработка; прогресс в логах (`infra.cache.storage`), итог —
SSE-уведомлением.

**Independent Test**: POST `/api/utils/backfillcacheetagsize` → «OK»; в логах
прогресс `cache:backfill ... processed=N/M`; по завершении SSE-сводка.

**Acceptance Scenarios**:

1. **Given** строки с `exists=true, size IS NULL`, **When** запуск, **Then**
   `getFileInfo` заполняет `etag`/`size`.
2. **Given** файл в хранилище не найден (`size = -1`), **When** обработка,
   **Then** `exists` → `false` (самокоррекция), лог WARN.
3. **Given** уже запущено, **When** повторный клик, **Then** `ALREADY_RUNNING`.
4. **Given** remote circuit OPEN, **When** обработка REMOTE-строк, **Then** строки
   пропускаются (не долбим нестабильный remote).
5. **Given** идёт обработка, **When** `tail` логов, **Then** видим прогресс
   каждые N строк.

### User Story 2 — Идемпотентность (Priority: P2)

**Описание**: Повторный запуск не находит уже заполненные строки (фильтр `size IS
NULL`), обрабатывает только новые/оставшиеся.

## Requirements *(mandatory)*

### Functional

- **FR-001**: Функция `backfillCacheEtagSize(...)` MUST перебирать строки
  `tbl_storage_metadata_cache` c `exists = true AND (size IS NULL OR etag IS NULL OR etag = '')`.
- **FR-002**: Для каждой строки MUST получать `StorageFileInfo` через
  `getFileInfo` соответствующего бэкенда (LOCAL → `KaraokeStorageService`,
  REMOTE → `StorageApiClient`).
- **FR-003**: При успехе (`size >= 0`) MUST обновлять `etag`/`size` строки.
- **FR-004**: При `size = -1` (файл не найден) MUST выставлять `exists = false`
  (самокоррекция) + WARN-лог.
- **FR-005**: Для REMOTE MUST использовать circuit-aware проверку (`isFastFail()`):
  при OPEN строки MUST пропускаться (без MinIO-вызова) до следующего запуска.
- **FR-006**: MUST логировать прогресс в SLF4J-категорию `infra.cache.storage`
  каждые N строк (`cache:backfill processed=.../total=... updated=... missing=...`).
- **FR-007**: По завершении MUST отправлять SSE-сводку (`SnsNotification.message`).
- **FR-008**: MUST быть single-flight guard (in-memory `AtomicBoolean`) →
  `ALREADY_RUNNING` при повторном запуске.
- **FR-009**: MUST быть endpoint `POST /api/utils/backfillcacheetagsize`
  (ApiController) + зеркало в MainController (Thymeleaf, как у других utils).
- **FR-010**: Кнопка MUST быть на `HomeView.vue` с подтверждением (как
  `customFunction`/`exportAlignmentDataset`).
- **FR-011**: Прогон MUST идти в фоновом потоке (не блокировать HTTP).

### Non-Functional

- **NFR-001**: Не менять публичные сигнатуры storage-сервисов.
- **NFR-002**: Обработка ~109k строк — пачками/курсором, не держать всю выборку в памяти.
- **NFR-003**: Unit-тест на чистую логику решения (exists/size/etag по строке).
- **NFR-004**: Ошибка на одной строке MUST не валить весь прогон (try/catch + счётчик).

### Key Entities

- **`backfillCacheEtagSize`** (NEW, `Utils.kt` или отдельный файл) — фоновый backfill.
- **`StorageMetadataCache`** (MODIFY) — `updateFileInfo(source, bucket, fileName, etag, size)`
  и `markNotExists(source, bucket, fileName)` (или переиспользовать `upsert`/`recordDelete`).
- **`ApiController` / `MainController`** (MODIFY) — endpoint.
- **`HomeView.vue` + `store.js`** (MODIFY) — кнопка + promise.

## Success Criteria *(mandatory)*

- **SC-001**: После прогона `SELECT COUNT(*) ... WHERE exists AND size IS NULL`
  стремится к 0 (кроме пропущенных из-за OPEN circuit).
- **SC-002**: Кнопка на главной запускает прогон; прогресс виден в логах; SSE-итог приходит.
- **SC-003**: Повторный запуск обрабатывает только оставшиеся строки.
- **SC-004**: `:karaoke-app:test` PASS; `ktlintCheck` 0; `bootJar` OK;
  `check-knowledge-structure.sh` 9/9; `gh pr checks` all PASS.

## Assumptions

1. **`getFileInfo` локально — 10-30ms** на строку; ~56k local → ~15-30 мин в фоне.
   Приемлемо для разовой операции.
2. **REMOTE circuit-aware** — при нестабильном remote часть строк останется до
   следующего запуска (идемпотентно).
3. **`size = -1`** — маркер «не найдено» (из `getFileStat` null).

## Out of Scope

- **Изменение схемы кеша** — нет.
- **Периодический авто-запуск** — только ручная кнопка.
- **Обработка строк с `exists=false`** — они не в выборке.

## Migration Path

### Что нужно изменить

- `StorageMetadataCache.kt` (MODIFY) — `updateFileInfo`/`markNotExists` (или `upsert` public).
- `Utils.kt` или `CacheEtagSizeBackfill.kt` (NEW) — функция.
- `ApiController.kt` + `MainController.kt` (MODIFY) — endpoint.
- `webvue3/src/components/Songs/store.js` + `views/HomeView.vue` (MODIFY) — кнопка.
- `knowledge/domain/storage/*`, `docs/features/storage-metadata-cache.md` (MODIFY).

### Что НЕ нужно менять

- FSM circuit, публичные сигнатуры storage-сервисов, `selectFileInfo`.

## Validation

| Проверка | Ожидаемо |
|---|---|
| `SELECT COUNT(*) ... size IS NULL` после прогона | → 0 |
| `:karaoke-app:test` (unit решения) | PASS |
| Кнопка + SSE | работают |
| `:karaoke-app:ktlintCheck` / `bootJar` | OK |

## Rollback

`git revert <merge-commit>` — функция/кнопка исчезают; кеш остаётся как есть
(данные не теряются).

## Clarifications

### Session 2026-09-22

- **Q**: Файл не найден (`size=-1`)?
  - **A**: `exists=false` (самокоррекция) + WARN.
- **Q**: Remote-нагрузка?
  - **A**: circuit-aware (`isFastFail()` → пропуск строки).
