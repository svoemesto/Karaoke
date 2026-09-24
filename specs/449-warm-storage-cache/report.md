# Report: Полный прогрев кеша хранилища (Pass 449, #182)

**Issue**: OpenProject #182
**Branch**: `449-warm-storage-cache`
**Дата**: 2026-09-24

## Проблема

После переезда (#165) и сброса кеша (#446) `tbl_storage_metadata_cache` был
неконсистентен. Backfill (#435/#448) до(за)полняет только **существующие** строки;
сброшенные (DELETE) он не видит. Нужен полный прогрев — построить кеш с нуля по
всем песням и обоим источникам.

## Решение

Кнопка «Прогреть кеш хранилища» (главный экран админки) + backend
`POST /api/utils/warmstoragecache`. Гибрид (быстро и полно):

1. **Листинг бакета** `karaoke` LOCAL и REMOTE через `listFilesInfo` — MinIO LIST
   отдаёт `etag`/`size` **бесплатно** (без `statObject` на файл).
2. **Обход всех песен** (`SELECT id, song_author, song_year, song_album, file_name`)
   × имена из `StorageCacheReset.storageFileNamesForSong` (9 имён).
3. **Bulk-upsert** батчами по 2000: есть в листинге → `exists=true` + etag/size;
   нет → `exists=false`.

Фон, single-flight guard, прогресс `infra.cache.storage`, SSE-итог. REMOTE
circuit-aware: при OPEN REMOTE-источник пропускается целиком.

## Изменения

### Backend

- **NEW** `StorageCacheWarm.kt` — `warmStorageCache`, `loadSongsForWarm`, `listToMap`.
- `StorageMetadataCache` — `upsertBatch(rows)` (один INSERT ... ON CONFLICT на пачку) + `CacheUpsertRow`.
- `KaraokeStorageService.listFilesInfo` / `StorageApiClientImpl.listFilesInfo` —
  оптимизированы: `Item.etag()`/`size()` вместо `statObject`/`getFileInfo` на каждый файл.
- `ApiController` — `POST /api/utils/warmstoragecache`; `MainController` — зеркало GET.

### Frontend

- `HomeView.vue` — кнопка «Прогреть кеш хранилища» + confirm + handler.
- `Songs/store.js` — `warmStorageCachePromise`.

### Тесты

- `StorageCacheWarmTest` — `listToMap` (индекс по fileName; отсутствующий → null → exists=false).

### Документация

- `docs/features/storage-metadata-cache.md` (V2.12), `knowledge/domains/storage/domain.md`.

## Проверки

| Проверка | Результат |
|---|---|
| MinIO LIST отдаёт ETag/Size | подтверждено S3-запросом |
| `:karaoke-app:compileKotlin` | OK |
| `StorageCacheWarmTest` | 2/2 PASS |
| ktlint | OK |
| webvue3 lint / prettier / build | OK |
| knowledge-линтеры | OK |

## Замечания

- Оптимизация `listFilesInfo` (etag/size из LIST) ускоряет и существующий
  `syncRemotePicturesInStorage` (Utils.kt), который её использует.
- Именам в листинге соответствуют сырые ключи `StorageCacheReset` (сверка 1:1).
- Кнопка отдельная от backfill (разная семантика).

## Побочно найденная регрессия guard R-11 (исправлена здесь)

`no-mp4-mentions guard` был **красным на master** (не от этой ветки):
- PR #446 добавил параметр в конструктор `ApiController` → все строки сдвинулись
  на +1, а guard привязан к `file:line` → baseline устарел;
- тест `StorageCacheResetTest` (#446) содержал строку с `.mp4`.

Исправлено в этой ветке: baseline `ApiController` +1, `.mp4` убран из теста.
`bash tools/check-no-mp4-mentions.sh` → OK (130 in baseline).

## После merge

Пересборка/рестарт `karaoke-app` + `webvue3` (по согласию владельца) → кнопка.
