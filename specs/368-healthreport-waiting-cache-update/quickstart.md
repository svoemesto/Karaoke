# Quickstart: HealthReport WAITING status + SSE re-compute

> **Прецедент**: задача #80 «Ускорение HealtReport-2».
> Продолжение #75 (Pass 364, спека #364).

## Цель

Проверить, что после реализации спеки #368:

1. **Cold start**: при первом открытии страницы Songs UI отображает `WAITING` (`#FFCCFF`)
   вместо ложных `ERROR` для песен, чьи файлы ещё не проверены.
2. **Прогрев через SSE**: после заполнения `StorageMetadataCache` UI автоматически
   перерисовывается без F5 (через SSE `HEALTH_REPORTS`).
3. **Warm cache**: повторное открытие страницы не порождает лишних SSE.
4. **Регрессии**: `IN_PROGRESS` (repair-loop), `ERROR` (реальные проблемы) работают
   как раньше.

## Prerequisites

- Контейнер `karaoke-app` запущен с новым `bootJar` после реализации спеки.
- webvue3 (Vue 3) открыт в браузере с DevTools (вкладка Network + Vue devtools).
- Логи `karaoke-app` доступны: `docker logs karaoke-app --tail 1000 2>&1 | grep 'infra.cache.storage.waiting'`.
- MinIO (local) запущен и доступен.
- Минимум 1 песня в БД (для локальной проверки достаточно одной).

## Сценарий 1: Cold start с пустым кешем → WAITING → SSE → OK

### Шаг 1.1: очистить кеш

```bash
# Через UI (webvue3): Settings → Admin → "Очистить весь кеш метаданных"
# Или через прямой endpoint:
curl -X POST http://localhost:8899/api/health/cache/refresh-all
```

Проверить, что таблица пуста:

```bash
docker exec karaoke-postgres psql -U postgres -d karaoke \
  -c "SELECT COUNT(*) FROM tbl_storage_metadata_cache;"
# Ожидаем: 0
```

### Шаг 1.2: открыть страницу Songs

Открыть в webvue3 страницу Songs. Наблюдать за колонкой HealthReport.

**Ожидаемое поведение** (SC-001 спеки):

- HTTP-ответ приходит за **≤500 мс**.
- Все песни показывают 3 записи HealthReport со статусом **`WAITING`** (цвет `#FFCCFF`).
- В DevTools Network видно **ровно 1 запрос** `/api/health/getHealthReportList`.

### Шаг 1.3: наблюдать прогрев через SSE

В DevTools Network фильтровать `EventStream` (или `/api/subscribe`).

**Ожидаемое поведение** (SC-002 спеки):

- В течение **≤3 секунд** приходят SSE-события `HEALTH_REPORTS` для песен по мере заполнения их кеша.
- UI перерисовывается: записи меняют цвет с `#FFCCFF` (WAITING) на `#99FF99` (OK) **без F5**.
- В DevTools Vue devtools видно, что Vuex-стор `healthReport/store.js` обновляется через `SSE → mutation`.

### Шаг 1.4: проверить логи `infra.cache.storage.waiting`

```bash
docker logs karaoke-app --tail 1000 2>&1 | grep 'infra.cache.storage.waiting' | tail -20
```

**Ожидаемый формат**:

```
INFO  [infra.cache.storage.waiting] source=LOCAL bucket=karaoke fileName=song-12345.mp4 status=FILLED durationMs=42 songId=12345
INFO  [infra.cache.storage.waiting] source=LOCAL bucket=karaoke fileName=song-12345.mp4 status=FILLED durationMs=38 songId=12346
...
```

**SC-004 спеки**: через 60 секунд после cold start hit rate ≥80%. Измерить:

```bash
docker logs karaoke-app --tail 1000 2>&1 | grep 'infra.cache.storage.waiting' | wc -l
# Должно быть > 0 (события были)

curl -s http://localhost:8899/api/health/cacheStats | jq '.remote.hitRatio'
# Ожидаем: ≥0.8
```

## Сценарий 2: Warm cache → нет лишних SSE (SC-003 спеки)

### Шаг 2.1: убедиться, что кеш прогрелся

После Сценария 1 подождать ещё 10 секунд.

```bash
curl -s http://localhost:8899/api/health/cacheStats | jq '.local.hitRatio, .remote.hitRatio'
# Ожидаем: оба ≥0.95
```

### Шаг 2.2: перезагрузить страницу Songs

Открыть страницу Songs снова. В DevTools Network открыть EventStream.

**Ожидаемое поведение** (SC-003 спеки):

- HTTP-ответ `/api/health/getHealthReportList` — все записи `OK` (зелёные), **≤200 мс**.
- **Нет** новых SSE-событий `HEALTH_REPORTS` для уже прогретых песен.

## Сценарий 3: регрессия — repair-loop продолжает работать

### Шаг 3.1: создать условие для repair

Остановить MinIO на 5 секунд (см. как это делать в `specs/364-healthreport-speedup/quickstart.md`).

```bash
docker stop karaoke-minio
sleep 5
docker start karaoke-minio
```

### Шаг 3.2: открыть страницу Songs

**Ожидаемое поведение**:

- HTTP-ответ за ≤500 мс (circuit breaker Pass 364 → `FATAL_ERROR` для storage, не ERROR).
- Через 30 секунд (circuit breaker HALF_OPEN) — проба → если OK → переход в `OK`.

### Шаг 3.3: запустить repair для одной песни

В webvue3 на странице Songs кликнуть на "Починить всё" (bulk repair).

**Ожидаемое поведение**:

- Записи, для которых запущен `KaraokeProcess`, переходят в `IN_PROGRESS` (цвет `#FFFF99`, **НЕ** `#FFCCFF`).
- Это regression-test: `IN_PROGRESS` (repair-loop) **не должен** быть заменён на `WAITING`.

## Сценарий 4: регрессия — реальная ERROR показывается как ERROR

### Шаг 4.1: удалить файл из MinIO вручную

```bash
# Найти файл COVER для какой-нибудь песни:
docker exec karaoke-minio mc ls karaoke/cover/
# Удалить один файл (НЕ в production-like БД, только в dev):
docker exec karaoke-minio mc rm karaoke/cover/song-XYZ.jpg

# Также очистить кеш (Pass 348 не подхватит ручное удаление):
curl -X POST "http://localhost:8899/api/health/cache/refresh?bucket=karaoke&fileName=cover/song-XYZ.jpg"
```

### Шаг 4.2: открыть страницу Songs, найти эту песню

**Ожидаемое поведение**:

- Запись `COVER/LOCAL_STORAGE` для этой песни показывает **`ERROR`** (цвет `#FF9999`), **НЕ** `WAITING`.
- Это regression-test: реальная ошибка должна показываться как `ERROR`, а не как `WAITING`.

## Метрики для проверки

### После всех сценариев

```bash
# Hit rate StorageMetadataCache (SC-004):
curl -s http://localhost:8899/api/health/cacheStats | jq '{local: .local.hitRatio, remote: .remote.hitRatio}'
# Ожидаем: оба ≥0.8

# Количество SSE-событий HEALTH_REPORTS за последнюю минуту:
docker logs karaoke-app --since 1m 2>&1 | grep -c 'healthReports.*songId'
# Ожидаем: 0 (warm cache) или <5 (cold start)

# Количество новых cache entries за последний час:
docker logs karaoke-app --since 1h 2>&1 | grep -c 'infra.cache.storage.waiting.*FILLED'
# Ожидаем: >0 после cold start, ~0 на warm cache
```

### Логи `infra.health.circuit` (Pass 364)

Если в Сценарии 3 использовался circuit breaker:

```bash
docker logs karaoke-app --tail 200 2>&1 | grep 'infra.health.circuit'
# Ожидаем: state=OPEN, потом state=HALF_OPEN, потом state=CLOSED
```

## Что считать failure

| Симптом | Скорее всего причина |
|---------|---------------------|
| Цвет остаётся `#FF9999` (ERROR) после fill | Callback не вызывается или `recomputeAndBroadcast` падает. Проверить `docker logs karaoke-app | grep 'ERROR.*recompute'` |
| Цвет остаётся `#FFCCFF` (WAITING) более 30 секунд | `cacheFillerExecutor` не запускается или MinIO недоступен. Проверить `docker logs karaoke-app | grep 'cache:miss:async:error'` |
| Много лишних SSE-событий на warm cache | Callback срабатывает на cache hit. Проверить условие `if (cached == null)` в `StorageMetadataCache.getFileExistsAsync` |
| `IN_PROGRESS` (repair-loop) стал `WAITING` | Логика `actionsLocalStorage` / `actionsRemoteStorage` заменена некорректно. Проверить git diff `HealthReport.kt` |
| `ERROR` (реальная проблема) стал `WAITING` | Логика `cachedFileExists` возвращает `WAITING` без проверки результата loader'а. Проверить что `onFillComplete` НЕ подменяет return value |

## Сводка успешного теста

| Сценарий | Ожидание | SC |
|----------|-----------|-----|
| 1.1 Cold start HTTP | ≤500 мс, 3 WAITING-записи | SC-001 |
| 1.2 SSE-прогрев | ≤3 сек, все OK | SC-002 |
| 1.3 Hit rate | ≥80% через 60 сек | SC-004 |
| 2.1 Warm cache перезагрузка | Нет лишних SSE | SC-003 |
| 3.1 Repair-loop | `IN_PROGRESS`, не `WAITING` | SC-005 |
| 4.1 Реальная ERROR | `ERROR`, не `WAITING` | SC-005 |