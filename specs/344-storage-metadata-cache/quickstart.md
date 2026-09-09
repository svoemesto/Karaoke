# Quickstart: Storage metadata cache (OpenProject #69)

**Phase**: 1 — Design & Contracts
**Date**: 2026-09-09
**Spec**: [spec.md](./spec.md)
**Contract**: [contracts/cache-stats-api.md](./contracts/cache-stats-api.md)
**Data model**: [data-model.md](./data-model.md)
**Research**: [research.md](./research.md)

> Validation guide. Это **НЕ** полная реализация, а набор **запускаемых сценариев**, которые доказывают, что фича работает end-to-end.

## Prerequisites

1. **karaoke-app скомпилирован** с новым бином `StorageMetadataCache`:
   ```bash
   GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin --parallel
   ```

2. **Юнит-тесты** `PollingCacheTest` PASS:
   ```bash
   GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests "PollingCacheTest"
   ```
   Ожидаемо: **5/5 tests passed** (см. [data-model.md § Тесты](./data-model.md#4-pollingcachetest-юнит-тесты)).

3. **Локальный MinIO** запущен (`karaoke-storage:9000`, см. `deploy/docker-compose.yml`).

4. **`/api/health/*` permitAll** (это политика Karaoke, см. Constitution V).

## Scenario 1: Cold start → первый cache miss

**Goal**: проверить, что первый `fileExists` через кеш идёт в реальный MinIO, и счётчик `misses` инкрементируется.

### Steps

1. **Перезапустить** `karaoke-app` (cold start cache = empty).

2. **Открыть webvue3**, страница Songs → ожидается полная загрузка (~минута в первый раз, как было ДО спеки). Это подтверждает: cold-start не сломан.

3. **Подождать окончания** загрузки.

4. **Проверить** `/api/health/cacheStats`:
   ```bash
   curl -s http://localhost:18080/api/health/cacheStats | jq .
   ```
   Ожидаемо:
   ```json
   {
     "local": {
       "entries": <N>,
       "hits": 0,
       "misses": <M>,
       "hitRatio": 0.0,
       "ttlSeconds": 300,
       "evictions": 0
     },
     "remote": { ... }
   }
   ```
   `misses > 0` — означает, что loader был вызван N раз. `hits = 0` — первый раз все запросы — miss.

5. **Проверить логи**:
   ```bash
   grep "infra.cache.storage" /var/log/karaoke-app.log | wc -l
   ```
   Ожидаемо: > 0 (каждый miss + hit логируется на INFO).

## Scenario 2: Reload (F5) → cache hit

**Goal**: доказать, что второй запрос в течение TTL возвращает данные из кеша без HTTP round-trip.

### Steps

1. **Из сценария 1**, НЕ перезапускать `karaoke-app`.

2. **Обновить страницу** Songs (F5) в течение 5 минут после первого открытия.

3. **Замерить** время загрузки: ожидаемо **< 5 секунд** (SC-001).

4. **Проверить** `/api/health/cacheStats` снова:
   ```bash
   curl -s http://localhost:18080/api/health/cacheStats | jq .
   ```
   Ожидаемо:
   ```json
   {
     "local": {
       "entries": <N unchanged>,
       "hits": <новая сумма, >> M from step 1>,
       "misses": <M from step 1 — НЕ должно расти>,
       "hitRatio": >0.9,
       ...
     },
     ...
   }
   ```
   `misses` НЕ растёт (кешированные ключи не вызывают loader). `hits` существенно растёт.

5. **Опционально** для верификации latency: подключить `tcpdump` или `kdig` к nginx path-proxy (`minio-proxy`) — НЕ должно быть запросов на те же `(bucket, fileName)` в течение 5 минут.

## Scenario 3: TTL expiry → miss снова

**Goal**: доказать, что по истечении TTL кеш «протухает» и loader вызывается снова.

### Steps

1. **Из сценария 2**, **подождать 6 минут** (TTL=300s + 60s buffer).

2. **Снова обновить** Songs page.

3. **Проверить** `/api/health/cacheStats`:
   - `entries` — возможно уменьшилось (lazy cleanup, N*500th call).
   - `misses` — должно вырасти (TTL истёк, снова ходим в MinIO).
   - `hits` — НЕ выросло (или минимально).
   - `evictions` — выросло (cleanup сработал).

4. **Проверить логи**:
   ```bash
   grep "infra.cache.storage" /var/log/karaoke-app.log | grep cache:evicted | wc -l
   ```
   Должны быть строки с `cache:evicted reason=ttl`.

## Scenario 4: cacheStats после сценариев 1-3

### Final check

```bash
curl -s http://localhost:18080/api/health/cacheStats | jq .local.hitRatio
# Ожидаемо: >0.9 на стороне local (SC-002)

grep "ERROR" /var/log/karaoke-app.log | grep "infra.cache.storage" | wc -l
# Ожидаемо: 0 (никаких ERROR для кеша — exceptions НЕ кешируются FR-006)
```

## Negative scenarios (НЕ должны происходить)

### N1: MinIO недоступен — loaders падают

**Setup**: симулировать — остановить `karaoke-storage` контейнер:
```bash
docker stop karaoke-storage
```

**Expected**:
- `fileExists` → бросает exception.
- `getOrCompute` НЕ ловит exception (FR-006) — exception пробрасывается caller'у.
- `HealthReport.getHealthReportList` покажет ошибки.
- НО кеш НЕ сохраняет `false` / `null` (только успешные `Boolean`/`StorageFileInfo`, FR-006).

**Verify**:
```bash
curl -s http://localhost:18080/api/health/cacheStats | jq .local.entries
# Должно остаться = последнему успешному значению, НЕ 0
```

### N2: URL-encoded имя файла

**Setup**: создать файл с `дерево%20любви.flac` в MinIO через `mc`, потом HealthReport запрос на эту песню.

**Expected**:
- Cache key строится ПОСЛЕ `decodeFileNameIfEncoded` (FR-012), т.е. от `дерево любви.flac`, не от encoded.
- Если тот же файл будет закодирован в nginx → cache key одинаковый → cache hit.

**Verify**: тот же сценарий дважды (один раз encoded, второй decoded) → `entries` НЕ растёт (один и тот же ключ).

## Cleanup

После quickstart-проверки:
```bash
# Удалить тестовые файлы из MinIO
mc rm --recursive --force local/karaoke/test-quickstart-*

# Кеш сбросится автоматически через TTL (5 минут)
# Или вручную: рестарт karaoke-app
```

## Связь с задачами (Tasks.md)

Этот quickstart покрывает acceptance критерии для User Stories 1-3. User Story 4 (persistence, P3) и edge cases N1/N2 покрываются отдельными tasks в `/speckit.tasks`.

## Готовность к реализации

Все design-артефакты (`research.md`, `data-model.md`, `contracts/cache-stats-api.md`, `quickstart.md`) написаны. Constitution Check пройден (см. `checklists/requirements.md`). Можно переходить к `/speckit.tasks`.
