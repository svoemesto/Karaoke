# Report: Сброс кеша хранилища для песни и страницы (Pass 446, #180)

**Issue**: OpenProject #180
**Branch**: `446-storage-cache-reset-button`
**Дата**: 2026-09-24

## Проблема

После переезда (#165) persistent-кеш `tbl_storage_metadata_cache` (TTL=∞) хранил
устаревшие `exists=true` от старого MinIO. HealthReport доверяет кешу
(`peekFileExists`) → показывал «0 ошибок» при реально отсутствующих файлах
(картинка альбома «Бог кастрирует слона» не отображалась, плеер отказывал).
Сброс кеша требовал ручного `curl` с admin-машины.

## Решение

UI-кнопки сброса кеша:

1. **Одна песня** — `HealthReportTableHeader.vue` → «Сбросить кеш».
2. **Страница песен** — футер `SongsTable.vue` → «Сбросить кеш хранилища»
   (рядом с «Repair All»).

Backend: `POST /api/song/resetStorageCache?ids=1;2;3` → `{songs, keys, rowsDeleted}`.

## Изменения

### Backend (`karaoke-app`)

- **NEW** `StorageCacheReset.kt` — единый источник формул имён файлов песни:
  `storageFileNameFor(...)` (примитивы) + `storageFileNamesForSong(...)`; только
  `KaraokeFileTypeFor.SONG` с `LOCAL_STORAGE`/`REMOTE_STORAGE` (5 MP3-стемов +
  4 картинки = 9 имён).
- `StorageMetadataCache.refreshKeys(keys)` — batch `DELETE ... WHERE
  (source,bucket,file_name) IN (...)`, группировка по source, идемпотентно.
- `ApiController` — inject `StorageMetadataCache` + endpoint.

### Frontend (`webvue3`)

- `Songs/store.js` — `resetStorageCachePromise(ids)` (JSON.parse ответа).
- `SongsTable.vue` — кнопка `icon_erase.svg` + confirm + `sendBatchHealthReports`
  после сброса (обновление бейджей через SSE).
- `HealthReportTableHeader.vue` — кнопка «Сбросить кеш» → `resetStorageCachePromise([songId])`
  → `loadHealthReportList(songId)`.

### Тесты

- `StorageCacheResetTest` — 4 теста: аудио-имя, картинка альбома, картинка автора,
  состав/уникальность (9 имён; нет `.flac`/`.mp4`).

### Документация

- `knowledge/domains/storage/domain.md` — раздел «Ручной сброс кеша (UI)» + ловушка.
- `docs/features/storage-metadata-cache.md` — раздел UI-сброса + V2.7.

## Проверки

| Проверка | Результат |
|---|---|
| `:karaoke-app:compileKotlin` | OK |
| `:karaoke-app:test --tests StorageCacheResetTest` | 4/4 PASS |
| `:karaoke-app:ktlintCheck` | OK |
| `webvue3 npm run lint` | OK |
| `webvue3 prettier --check` (изменённые) | OK |
| `webvue3 npm run build` | OK |

## Замечания

- Сброс затрагивает **оба** источника (LOCAL+REMOTE) — рассинхрон бывает с обеих сторон.
- Формулы имён теперь в одном месте; при добавлении нового `KaraokeFileType` со
  storage нужно править `StorageCacheReset` (покрыто тестом).
- Мультивыбор чекбоксами в таблице песен — вне scope; «страница» = песни текущей
  страницы (как у «Repair All»).

## После merge

Требуется пересборка/рестарт `karaoke-app` (новый endpoint) и `webvue3` (кнопки) —
по согласию владельца.
