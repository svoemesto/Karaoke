# Отчёт по #159 — Backfill etag/size в tbl_storage_metadata_cache

> **Спека**: [spec.md](spec.md) | **Ветка**: `435-cache-etag-size-backfill` |
> **Дата**: 2026-09-22 | **Pass**: 435.

## Задача

Кастом-функция, которая для всех записей `tbl_storage_metadata_cache` с флагом
`exists`, но пустыми `etag`/`size`, заполняет эти поля. Кнопка вызова — на главном
экране админки; прогресс — в логах.

## Контекст

В БД: **56531 LOCAL + 52679 REMOTE** строк `exists=true` с пустыми `etag`/`size`
(созданы через `fileExists`, где info = NULL). Именно поэтому Pass 434 не мог
отвечать `fileIsActual` из кеша — нужен разовый backfill.

## Что сделано

- **`CacheEtagSizeBackfill.kt`** (NEW):
  - `backfillCacheEtagSize(storageService, storageApiClient, remoteBreaker)` —
    фоновый проход по строкам `exists=true AND (size IS NULL OR etag IS NULL OR etag='')`.
  - LOCAL → `KaraokeStorageService.getFileInfo`; REMOTE → `StorageApiClient`
    **circuit-aware** (`isFastFail()` → пропуск строки).
  - `size = -1` (файл не найден) → `exists = false` (самокоррекция) + WARN.
  - Прогресс каждые 500 строк в `infra.cache.storage` + `println`; итог — SSE.
  - single-flight (`AtomicBoolean`) → `ALREADY_RUNNING`.
  - `decideBackfillAction(info)` — чистое тестируемое решение.
- **`StorageMetadataCache`**: `updateFileInfo(...)`, `markNotExists(...)`.
- **Endpoints**: `POST /api/utils/backfillcacheetagsize` (ApiController) +
  `GET /utils/backfillcacheetagsize` (MainController, Thymeleaf-зеркало).
- **Frontend**: кнопка «Заполнить etag/size кеша хранилища» на `HomeView.vue` с
  подтверждением; `backfillCacheEtagSizePromise` в `Songs/store.js`.
- Knowledge SSoT + `docs/features` (V2.10); R-11 baseline перенумерован.

## Поведение

| Ситуация | Действие |
|---|---|
| info получен (size ≥ 0) | `etag`/`size` обновляются |
| файл не найден (size = -1) | `exists = false` + WARN |
| remote circuit OPEN | строка пропускается (повторный запуск доберёт) |
| ошибка на строке | SKIP + счётчик (прогон не падает) |
| повторный запуск | обрабатывает только оставшиеся строки |

## Тесты

- `CacheEtagSizeBackfillTest` (NEW, 4): `null`→SKIP, `size<0`→MARK_MISSING,
  `size=0`/`size>0`→UPDATE.
- Storage-тесты — PASS.

## Проверки

| Проверка | Результат |
|---|---|
| `:karaoke-app:compileKotlin` / `:karaoke-web:compileKotlin` | OK |
| `:karaoke-app:ktlintCheck` | 0 violations |
| `CacheEtagSizeBackfillTest` | 4/4 PASS |
| `:karaoke-app:bootJar` | OK |
| `webvue3 npm run lint` | OK |
| knowledge 9/9, cross-links 631/631, R-11 OK | OK |

## Follow-up

- Требуется **рестарт `karaoke-app` владельцем**, затем нажать кнопку на главной.
- Прогресс: `docker logs -f karaoke-app | grep cache:backfill`.
- После прогона `SELECT COUNT(*) ... WHERE exists AND size IS NULL` должен
  упасть почти до нуля (кроме пропущенных при OPEN remote — добить повторным запуском).
