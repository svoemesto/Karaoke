# Отчёт по #158 — HealthReport кеширует getFileInfo/fileIsActual

> **Спека**: [spec.md](spec.md) | **Ветка**: `434-healthreport-cache-fileinfo` |
> **Дата**: 2026-09-22 | **Pass**: 434.

## Симптом / вопрос владельца

«Зачем HealthReport лезет в хранилище, если у всех песен всё закешировано?»
Разбор подтвердил: `StorageMetadataCache` кешировал только `fileExists`, а
`fileIsActual`/`getFileInfo` шли в MinIO (`statObject`) **каждый раз**, даже на
тёплом кеше.

## Root cause

4 места в `HealthReport`:
- `actionsLocalStorage` (было 736): `storageService.fileIsActual(path)` →
  `getFileInfo` → `statObject`.
- `actionsLocalStorage` (793): `storageService.getFileInfo`.
- `actionsRemoteStorage` (1081): `storageApiClient.fileIsActual(path)`.
- `actionsRemoteStorage` (1142): `storageService.getFileInfo` + сравнение с remote.

Плюс: мёртвый/семантически неверный `StorageMetadataCache.getFileIsActual` (читал
`exists`, не актуальность) и `selectFileInfo` возвращал `size=0` для строк,
созданных через `fileExists` (size NULL) → ложное «файл неактуальный».

## Что сделано

- **`HealthReport.cachedFileInfo(...)`** — кешированный `StorageFileInfo`
  (etag/size) через `StorageMetadataCache.getFileInfo`; hit → без MinIO, miss →
  loader. Circuit-aware (`isFastFail()` до cooldown → `null`, без MinIO) и
  `size >= 0` guard (не кешируем «-1»).
- **`fileIsActual` вычисляется из кеша**: сравнение `size` (диск vs хранилище;
  local vs remote) — без отдельного `statObject`.
- **`selectFileInfo`** — null-guard: `exists=false` или `size IS NULL` → `null`
  (miss), вынесено в чистый `buildFileInfoFromRow` (тестируемо).
- **Удалён** мёртвый `StorageMetadataCache.getFileIsActual`.
- **`StorageApiClientImpl.fileIsActual(storageFileInfo)`** обёрнут в circuit
  (`decorateOrEmpty`), как path-вариант.
- Knowledge SSoT + `docs/features` (V2.9).

## Что кешируется (наблюдение из БД)

`exists` заполнен почти для всех (~139k), а `etag`/`size` — были только у
**813 LOCAL / 4608 REMOTE**. После прогрева (`getFileInfo` при miss) кеш начнёт
хранить `size`/`etag`, и повторные проверки актуальности пойдут без MinIO.

## Тесты

- `StorageMetadataCacheFileInfoTest` (NEW, 4): complete row → info; `size IS NULL`
  → null (не `size=0`); `exists=false` → null; реальный `size=0` (пустой файл) → info.
- Все `StorageCircuitBreaker*` + `StorageTimeoutInterrupt*` — PASS.

## Проверки

| Проверка | Результат |
|---|---|
| `:karaoke-app:compileKotlin` / `:karaoke-web:compileKotlin` | OK |
| `:karaoke-app:ktlintCheck` | 0 violations |
| Storage-тесты | 36/36 PASS |
| `:karaoke-app:bootJar` | OK |
| knowledge 9/9, cross-links 631/631, R-11 OK | OK |

## Follow-up

- Требуется **рестарт `karaoke-app` владельцем** (nsa-i9).
- После рестарта: на повторной загрузке страницы Songs
  `grep cache:miss ... operation=getFileInfo` не должен расти (hit); число
  `statObject` к remote MinIO заметно падает.
