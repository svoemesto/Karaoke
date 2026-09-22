# Feature Specification: HealthReport — кешировать getFileInfo/fileIsActual (Pass 434, #158)

**Feature Branch**: `434-healthreport-cache-fileinfo`
**Created**: 2026-09-22
**Status**: Draft
**Input**: Вопрос владельца: «зачем HealthReport лезет в хранилище, если у всех
песен всё закешировано?» Разбор подтвердил: `StorageMetadataCache` кеширует
`fileExists`, но `fileIsActual`/`getFileInfo` идут в MinIO (`statObject`) **каждый
раз**, даже на тёплом кеше. Follow-up на #344/#348 (кеш), #75.

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#158` («HealthReport кеширует getFileInfo/fileIsActual»).
- **Title**: «HealthReport: кешировать getFileInfo/fileIsActual — тёплый кеш не ходит в хранилище».
- **Created in OpenProject**: 2026-09-22.
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 158` — выполнено 2026-09-22.
  2. **Add comment**: `bash tools/tracker.sh add-comment 158 --file specs/434-healthreport-cache-fileinfo/report.md`.
  3. **Mark review**: `bash tools/tracker.sh mark-review 158`.
  4. **Close** (owner, после merge): `bash tools/tracker.sh close-issue 158`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-22
- **Grep-запросы**:
  1. `grep -rln "getFileInfo|fileIsActual|StorageMetadataCache" knowledge/`
     → `health/components/health-report.md`, `storage/components/*`, `caching/*`.
  2. `grep -n "fileIsActual|getFileInfo" knowledge/domains/health/components/health-report.md`
     → кеш описан только для `fileExists` (строка 109).
  3. `grep -rn "fileIsActual" --include='*.kt' karaoke-app/src/main`
     → вызывается **только** из HealthReport (4 места) + реализация.
  4. `grep -rn "\.getFileInfo(" --include='*.kt' karaoke-app/src/main`
     → HealthReport (2) + Utils.kt (1); кеш-метод `getFileInfo` не вызывается.
  5. `docker exec karaoke-db psql ... SELECT ... etag, size` → `exists` заполнен
     почти всегда (139k), но `etag`/`size` — только **813 LOCAL / 4608 REMOTE**.

### Knowledge files consulted

- [`knowledge/domains/health/components/health-report.md`](../../knowledge/domains/health/components/health-report.md)
  — кеш `fileExists`; `fileIsActual`/`getFileInfo` не закешированы.
- [`knowledge/domains/storage/components/storage-api-client.md`](../../knowledge/domains/storage/components/storage-api-client.md)
  — `decorate`, `fileIsActual`, `getFileInfo`.
- [`knowledge/domains/storage/components/karaoke-storage-service.md`](../../knowledge/domains/storage/components/karaoke-storage-service.md)
  — local `fileIsActual`/`getFileInfo`.
- [`knowledge/domains/caching/components/web-caches.md`](../../knowledge/domains/caching/components/web-caches.md)
  — паттерны кеша.
- [`specs/348-storage-cache-eternal/spec.md`](../../specs/348-storage-cache-eternal/spec.md),
  [`specs/344-storage-metadata-cache/spec.md`](../../specs/344-storage-metadata-cache/spec.md).

### Прецедент

2026-09-22, разбор `HealthReport` по вопросу владельца: для «хорошей» песни
(файл есть в хранилище и на диске) HealthReport вызывает
`storageService.fileIsActual(...)` (`HealthReport.kt:737`), который внутри
`KaraokeStorageService.kt:501` делает `getFileInfo` → `statObject` в MinIO.
Аналогично remote (`HealthReport.kt:1082`, `1142`). Итог: `fileExists` — из кеша
(`cachedFileExists`), а `fileIsActual`/`getFileInfo` — прямой MinIO каждый раз.

## Проблема

`StorageMetadataCache` содержит методы `getFileIsActual`/`getFileInfo` (таблица
имеет колонки `etag`/`size`), но HealthReport их **не использует** — идёт напрямую
в `KaraokeStorageService`/`StorageApiClient`. Дополнительно:

1. `getFileIsActual` (dead code) **семантически неверен**: читает `selectExists`
   (значение `fileExists`), а не актуальность.
2. `selectFileInfo` при `size IS NULL` возвращает `size = 0` (`rs.getLong` для NULL
   = 0), что дало бы ложное «файл неактуальный» при сравнении.
3. `fileIsActual(storageFileInfo)` в `StorageApiClientImpl` (`StorageApiClient.kt:387`)
   зовёт `getFileInfo(...).block()` **без** circuit-обёртки.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Тёплый кеш не ходит в хранилище (Priority: P1)

**Описание**: Если `exists`, `etag`, `size` файла уже в кеше, HealthReport строит
вердикт по актуальности **без** обращения к MinIO.

**Independent Test**: unit-тест: после прогрева кеша (`getFileInfo` с loader'ом)
второй вызов `fileIsActual` не вызывает loader.

**Acceptance Scenarios**:

1. **Given** кеш содержит `size` файла, **When** HealthReport проверяет
   актуальность, **Then** вердикт по кешу, `statObject` не вызывается.
2. **Given** кеш пуст, **When** проверка, **Then** loader вызывается один раз и
   результат попадает в кеш.
3. **Given** файл на диске и в хранилище одного размера, **When** `fileIsActual`,
   **Then** `true` (из закешированного size).

### User Story 2 — Корректность при неполном кеше (Priority: P1)

**Описание**: Если в кеше только `exists` (без `size`), кеш НЕ должен отдавать
ложный вердикт — вызывается loader.

**Acceptance Scenarios**:

1. **Given** строка кеша с `exists=true`, `size=NULL`, **When** `getFileInfo`,
   **Then** cache miss → loader (не `size=0`).
2. **Given** `exists=false`, **When** `getFileInfo`, **Then** miss/`null`.

## Requirements *(mandatory)*

### Functional

- **FR-001**: HealthReport MUST получать `StorageFileInfo` через **кеш**
  (`StorageMetadataCache.getFileInfo`) вместо прямого `storageService.getFileInfo`/
  `storageApiClient.getFileInfo`.
- **FR-002**: `fileIsActual` MUST вычисляться из закешированного `StorageFileInfo`
  (сравнение `size`), а не отдельным MinIO-вызовом.
- **FR-003**: `selectFileInfo` MUST возвращать `null`, если `exists = false`
  ИЛИ `size IS NULL` (неполная строка от `fileExists`) — чтобы не отдавать
  `size=0` как реальный размер.
- **FR-004**: Мёртвый и семантически неверный `StorageMetadataCache.getFileIsActual`
  MUST быть удалён (dead code).
- **FR-005**: `fileIsActual(storageFileInfo)` в `StorageApiClientImpl` MUST быть
  обёрнут в circuit (`decorateOrEmpty`), как path-вариант (FR Pass 351).
- **FR-006**: Поведение при miss (loader → запись в кеш) MUST сохраниться;
  на hit loader НЕ вызывается.
- **FR-007**: `StorageMetadataCache` MUST предоставить helper'ы уровня HealthReport
  (или HealthReport использует существующий `getFileInfo`).

### Non-Functional

- **NFR-001**: На тёплом кеше `fileIsActual`/`getFileInfo` — без MinIO-вызова.
- **NFR-002**: Публичные сигнатуры `KaraokeStorageService`/`StorageApiClient` MUST
  NOT меняться (FR-011 спеки #344).
- **NFR-003**: Unit-тесты: hit без loader; miss с loader; неполный кеш → miss.
- **NFR-004**: write-through (`recordUpload`/`recordDelete`) MUST заполнять
  `size`/`etag` — уже делает.

### Key Entities

- **`StorageMetadataCache`** (MODIFY) — `selectFileInfo` null-guard; удалить
  `getFileIsActual`.
- **`HealthReport`** (MODIFY) — `cachedFileInfo(...)` helper; `fileIsActual` из кеша.
- **`StorageApiClientImpl`** (MODIFY) — circuit на `fileIsActual(storageFileInfo)`.

## Success Criteria *(mandatory)*

- **SC-001**: Unit: тёплый кеш → `fileIsActual`/`getFileInfo` без loader.
- **SC-002**: Unit: `size=NULL` → miss (не ложное «неактуально»).
- **SC-003**: `StorageCircuitBreaker*` + новые тесты — PASS; `ktlintCheck` 0;
  `bootJar` OK; knowledge 9/9; `gh pr checks` all PASS.
- **SC-004**: После рестарта: `grep cache:miss ... operation=getFileInfo` на повторной
  загрузке страницы Songs не растёт (hit).

## Assumptions

1. **`fileIsActual` вызывается только из HealthReport** — проверено; значит кеш-lookup
   на уровне HealthReport безопасен.
2. **Кеш-eternal (#348)** — TTL нет; write-through инвалидация сохраняется.
3. **`size` — достаточный критерий актуальности** (текущая логика сравнивает только
   размеры).

## Out of Scope

- **Изменение модели актуальности** (etag/hash вместо size) — нет.
- **Изменение FSM circuit** — нет.
- **Кеширование `fileIsActual` как отдельного Boolean** — по решению: вычисляем из
  `StorageFileInfo`.

## Migration Path

### Что нужно изменить

- `StorageMetadataCache.kt` (MODIFY) — `selectFileInfo` null-guard, удалить
  `getFileIsActual`.
- `HealthReport.kt` (MODIFY) — `cachedFileInfo` helper + замена 4 мест.
- `StorageApiClient.kt` (MODIFY) — circuit на `fileIsActual(storageFileInfo)`.
- Tests (ADD).
- `knowledge/domains/health/components/health-report.md`, `storage/*` (MODIFY);
  `docs/features/storage-metadata-cache.md` (MODIFY) — V2.9.

### Что НЕ нужно менять

- Сигнатуры `KaraokeStorageService`/`StorageApiClient`; write-through хуки.

## Validation

| Проверка | Ожидаемо |
|---|---|
| Unit: тёплый кеш без loader | PASS |
| Unit: size=NULL → miss | PASS |
| `:karaoke-app:test --tests "*Storage*"` | PASS |
| `:karaoke-app:ktlintCheck` / `bootJar` | OK |

## Rollback

`git revert <merge-commit>` — HealthReport снова ходит в MinIO за `getFileInfo`.

## Clarifications

### Session 2026-09-22

- **Q**: Кешировать отдельный Boolean `fileIsActual` или вычислять из `StorageFileInfo`?
  - **A**: Вычислять из `StorageFileInfo` (сравнение size) — не плодим отдельный
    ключ/поля, переиспользуем готовые `etag`/`size`.
- **Q**: Что с мёртвым `getFileIsActual`?
  - **A**: Удалить (семантически неверен, не используется).
- **Q**: `selectFileInfo` size=NULL?
  - **A**: Обязательный null-guard (иначе `size=0` → ложное «неактуально»).
