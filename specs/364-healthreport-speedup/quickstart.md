# Quickstart: HealthReport Speedup Validation

## Prerequisites

- `karaoke-app` запущен локально (port 8899)
- MinIO local доступен (port 9000)
- webvue3 запущен (port 5173)
- Тестовая песня с известным ID (можно взять из таблицы `tbl_songs`)

## How to Validate

### 1. Measure single-song HealthReport latency

```bash
# Вызвать HealthReport для одной песни
curl -s "http://localhost:8899/api/health/getHealthReportList?songId=TEST_ID" | jq '{durationMs, healthReportsCount: .healthReports | length, error}'
```

**Expected**: `durationMs ≤ 200` для полностью «OK» песни.

### 2. Check detailed timing logs

```bash
# Смотреть логи HealthReport
docker logs karaoke-app --tail 500 2>&1 | grep 'infra.health.report.duration'
```

**Expected**: строки формата:
```
type=MLT_LOCAL_STORAGE operation=fileExists durationMs=12
type=COVER_LOCAL_STORAGE operation=fileExists durationMs=8
```

### 3. Validate circuit breaker

```bash
# Остановить MinIO
docker stop karaoke-minio

# Вызвать HealthReport (должен вернуть ошибку быстро)
time curl -s "http://localhost:8899/api/health/getHealthReportList?songId=TEST_ID" | jq '.error, .durationMs'

# Запустить MinIO обратно
docker start karaoke-minio
```

**Expected**: `error = "Storage unavailable"`, `durationMs ≤ 10` (fail fast, без retry).

### 4. Check circuit breaker state transitions in logs

```bash
docker logs karaoke-app --tail 200 2>&1 | grep 'infra.health.circuit'
```

**Expected**:
```
state=OPEN storage=local reason="Connection refused"
state=HALF_OPEN storage=local
state=CLOSED storage=local
```

### 5. Validate cold-start (cache miss)

1. Открыть страницу Songs в webvue3 (`http://localhost:5173`)
2. Очистить `StorageMetadataCache` (подождать TTL 300s или перезапустить `karaoke-app`)
3. Замерить время до появления данных в колонке HealthReport

**Expected**: UI показывает `IN_PROGRESS` placeholder немедленно (≤100 мс), данные появляются после cache fill (≤1 сек для одной песни).

### 6. Measure page load (20 songs)

```javascript
// В DevTools Network (F12)
1. Открыть страницу Songs (20+ песен)
2. Замерить время полной загрузки (все HealthReport колонки заполнены)
// Expected: ≤3 секунды
```

## How to Enable Detailed Logging

В `karaoke-app/src/main/kotlin/.../HealthReport.kt` добавить:

```kotlin
companion object {
    private val log = LoggerFactory.getLogger("infra.health.report.duration")
    private val circuitLog = LoggerFactory.getLogger("infra.health.circuit")
}
```

После ревью и мерджа — **выключить** детальное логирование (удалить или понизить до DEBUG).

## How to Check Cache Hit Rate

```bash
# Страница Songs загружается 2 раза подряд
# Первый раз: cache miss (все songId уникальны)
# Второй раз: cache hit (те же songId)

docker logs karaoke-app --tail 1000 2>&1 | grep 'infra.cache.storage' | grep -E 'hit|miss'
```

**Expected** (вторая загрузка): `hit` >> `miss`, ratio ≥80%.
