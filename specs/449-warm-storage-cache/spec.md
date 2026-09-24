# Feature Specification: Полный прогрев кеша хранилища (обход всех песен)

**Feature Branch**: `449-warm-storage-cache`

**Created**: 2026-09-24

**Status**: Draft

**Input**: Владелец: «Нужен полный прогрев с обходом всех песен». Отдельная кнопка
«Прогреть кеш хранилища» на главном экране админки: обход **всех** песен, листинг
бакета MinIO (`listObjects` даёт `etag`/`size` без `statObject`), bulk-upsert в
`tbl_storage_metadata_cache` для LOCAL+REMOTE. Заполняет и `exists=true`
(с etag/size), и `exists=false` (чего нет).

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#182` («Полный прогрев кеша хранилища (обход всех песен)»).
- **Title**: «Полный прогрев кеша хранилища (обход всех песен)».
- **Created in OpenProject**: 2026-09-24.
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 182` — выполнено 2026-09-24 (PATCH, прецедент #152).
  2. **Add comment**: `bash tools/tracker.sh add-comment 182 --file specs/449-warm-storage-cache/report.md`.
  3. **Mark review**: `bash tools/tracker.sh mark-review 182`.
  4. **Close**: `bash tools/tracker.sh close-issue 182`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-24
- **Grep-запросы**:
  1. `grep -rln "warm|прогрев|listFiles|обход" knowledge/`
     → `storage/*`, `caching/*`, `integration/*`.
  2. `javap -cp minio-8.6.0.jar io.minio.messages.Item` → `etag()`, `size()`,
     `objectName()`, `isDir()` — LIST отдаёт метаданные без `statObject`.
  3. `grep -rn "listFilesInfo" karaoke-app karaoke-web` → обе реализации в
     `KaraokeStorageService`/`StorageApiClientImpl` делают `statObject` на каждый файл.
  4. `grep -n "storageFileNameFor" StorageCacheReset.kt` → единый источник 9 имён/песня (#446).
  5. `docker exec karaoke-db psql` → 26 575 песен; кеш: 139 461 LOCAL / 1 539 REMOTE.

### Knowledge files consulted

- [`knowledge/domains/storage/domain.md`](../../knowledge/domains/storage/domain.md)
  — `StorageMetadataCache`, bucket'ы, hot paths.
- [`knowledge/domains/storage/components/karaoke-storage-service.md`](../../knowledge/domains/storage/components/karaoke-storage-service.md)
  — `listFiles`/`listFilesInfo`, MinIO SDK.
- [`knowledge/domains/storage/components/storage-api-client.md`](../../knowledge/domains/storage/components/storage-api-client.md)
  — remote-клиент, circuit breaker.
- [`knowledge/domains/caching/components/web-caches.md`](../../knowledge/domains/caching/components/web-caches.md)
  — паттерны (прецедент #339).
- [`specs/446-storage-cache-reset-button/spec.md`](../446-storage-cache-reset-button/spec.md)
  — `StorageCacheReset.storageFileNamesForSong`.

### Прецедент

После переезда (#165) кеш сброшен/рассинхронизирован; backfill (#435/#448) проверяет
только **имеющиеся** строки, а сброшенные (DELETE) он не видит. Нужен полный прогрев:
построить кеш с нуля по всем песням и обоим источникам.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Полный прогрев кеша (Priority: P1)

Как администратор, я нажимаю «Прогреть кеш хранилища» и получаю полностью
наполненный `tbl_storage_metadata_cache`: по каждому файлу каждой песни в LOCAL и
REMOTE — `exists` + `etag`/`size`.

**Why this priority**: это цель владельца; без неё кеш неконсистентен после сброса.

**Independent Test**: запустить → в логах `cache:warm start songs=26575`;
по завершении SSE-сводка; `SELECT source, count(*) FROM tbl_storage_metadata_cache`
показывает ~ожидаемое число строк; health-report не уходит в WAITING.

**Acceptance Scenarios**:

1. **Given** файл есть в MinIO, **When** прогрев, **Then** строка `exists=true` с etag/size.
2. **Given** файла нет, **When** прогрев, **Then** строка `exists=false`.
3. **Given** REMOTE circuit OPEN, **When** прогрев, **Then** REMOTE-часть пропускается
   с WARN (LOCAL всё равно прогревается).
4. **Given** прогрев идёт, **When** повторный клик, **Then** `ALREADY_RUNNING`.

### Edge Cases

- **Бакет недоступен** → пустой map → все строки источника `exists=false` (не падать).
- **26 575 песен × 9 имён × 2 source ≈ 478k строк** → память и SQL чанками.
- **Общие ключи** (картинка автора) → ON CONFLICT DO UPDATE, без дублей.
- **Имена с `%`** (URL-encoded) → листинг отдаёт сырой ключ; сверка с `storageFileName` по сырому.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Backend MUST предоставлять `POST /api/utils/warmstoragecache`
  (ApiController) + зеркало в MainController (Thymeleaf).
- **FR-002**: Прогрев MUST перебирать **все** песни (`SELECT id, song_author,
  song_year, song_album, file_name FROM tbl_songs`) и все имена из
  `StorageCacheReset.storageFileNamesForSong`.
- **FR-003**: Для LOCAL и REMOTE MUST строиться map из листинга бакета
  (`listFilesInfo`), без `statObject` на каждый файл (LIST отдаёт etag/size).
- **FR-004**: Для каждого имени × источник MUST быть upsert: найден → `exists=true`
  + etag/size; не найден → `exists=false`.
- **FR-005**: Upsert MUST быть bulk (пачками), не по строке.
- **FR-006**: REMOTE MUST быть circuit-aware: при OPEN — весь REMOTE-источник
  пропускается с WARN (LOCAL прогревается).
- **FR-007**: Прогрев MUST идти в фоне; single-flight `AtomicBoolean` → `ALREADY_RUNNING`.
- **FR-008**: Прогресс MUST логироваться в `infra.cache.storage`; итог — SSE.
- **FR-009**: `listFilesInfo` обеих реализаций MUST использовать `Item.etag()`/`size()`
  (оптимизация: сейчас — `statObject`/HTTP на каждый файл).

### Non-Functional

- **NFR-001**: Не менять публичные сигнатуры storage-сервисов (только оптимизировать impl).
- **NFR-002**: Память: строить upsert-батчи чанками (не держать 478k объектов разом).
- **NFR-003**: Unit-тест на чистую логику выбора (`exists`/etag/size по map).

## Success Criteria *(mandatory)*

- **SC-001**: После прогрева health-report песни отвечает без WAITING (кеш полон).
- **SC-002**: Число строк кеша ≈ `песни × имена × 2` (с учётом общих ключей).
- **SC-003**: LOCALD и REMOTE строки с etag/size у существующих файлов.
- **SC-004**: Повторный прогрев идемпотентен (те же значения).
- **SC-005**: Линтеры/тесты/CI — зелёные.

## Assumptions

- Bucket — `karaoke` (константа `song.storageBucketName`).
- Прогрев заполняет **все** песни (не только ready) — как HealthReport.
- Кнопка — отдельная от backfill (разная семантика: backfill — дозаполнить
  имеющиеся строки; warm — построить с нуля по всем песням).
