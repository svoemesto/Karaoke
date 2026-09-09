# Feature Specification: Persistent storage metadata cache (Pass 345)

**Feature Branch**: `[348-storage-cache-eternal]`
**Created**: 2026-09-09
**Status**: Draft
**Input**: Owner feedback to spec #344 (Pass 344) — "кеш должен быть 'вечный' — не 5 минут, а пока не принудительно обновлён или не произведена процедура изменения файла".

**Supersedes**: spec #344 (Pass 344, sha `a1ac507f`) — TTL polling cache заменён на persistent write-through.

## Контекст

Спека #344 (Pass 344) реализовала in-memory TTL-кеш на основе готового `PollingCache<V>` из `karaoke-web`. После merge owner сказал: «TTL=300s недостаточно — мне нужен "вечный" кеш, который обновляется только по событию». Это требует:

1. **Persistent storage**: переживает restart/rebuild `karaoke-app` и `webvue3`.
2. **TTL = ∞**: нет auto-expiration. Кеш живёт, пока кто-то явно его не инвалидирует.
3. **3 источника invalidation**:
   - Write-through hooks в upload/delete.
   - Manual refresh endpoint для admin UI.
   - (сознательно НЕТ auto-invalidation по TTL — это и есть "вечный" режим).

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

> Эта секция REQUIRED для каждой спецификации. Без неё спека НЕ ДОЛЖНА
> переходить в `/speckit.plan`. CI gate `tools/check-spec-issue-link.py`
> валидирует наличие всех полей. Workflow `claim → report → mark-review →
> close` — обязателен (см. tools/tracker.sh и AGENTS.md).
>
> **Governance note (Pass 349)**: данная спека изначально была создана БЕЗ
> этой секции (pre-Amendment). Section добавлена при governance amendment
> задним числом, после того как owner указал на governance failure для #69
> (отчёт записан после merge, а не до). См. `specs/344-storage-metadata-cache/report.md`.

### Идентификация

- **Issue ID**: `#69` (OpenProject work package id).
- **Title**: Persistent storage metadata cache (supersede #344).
- **Created in OpenProject**: открыта (см. `bash tools/tracker.sh get-issue 69`).

### Workflow (NON-NEGOTIABLE)

| Шаг | Команда | Когда | Кто |
|---|---|---|---|
| 1. **Claim** | `source .env.local-tracker && bash tools/tracker.sh claim-issue 69` | ПЕРЕД первой строкой кода спеки. Переводит `New` → `In progress`, assignee=ai-agent. | Agent |
| 2. **Pre-flight Knowledge** | `spec.md § Knowledge References` | Согласно Constitution Principle IX. | Agent |
| 3. **Work** | код, tests, knowledge updates | `/speckit.implement` | Agent |
| 4. **Add comment с отчётом** | `bash tools/tracker.sh add-comment 69 --file specs/348-storage-cache-eternal/report.md` | После merge. Файл `report.md` — REQUIRED. | Agent |
| 5. **Mark review** | `bash tools/tracker.sh mark-review 69` | После публикации комментария. Переводит `In progress` → `In review`. | Agent |
| 6. **Close** | `bash tools/tracker.sh close-issue 69` | После ревью владельцем. | Agent или Owner |

### Фактический статус (для этой спецификации)

| Шаг | Фактическое состояние |
|---|---|
| Claim | ПЕРЕД merge PR #448 (задним числом): выполнен `tracker.sh claim-issue 69` |
| Add comment | Задним числом, после слияния PR #448: добавлен `report.md` (10133 chars) |
| Mark review | Задним числом, после `add-comment`: `tracker.sh mark-review 69` → status `In review` |
| Close | НЕ выполнено — ждёт ревью владельца |

### Прецедент

2026-09-09, issue #69 (Pass 344/345): work выполнен через `/speckit-full 69` БЕЗ
governance enforcement данной секции. Отчёт опубликован задним числом после merge.
Governance failure исправлен в спеке #349 (Pass 349).


## Knowledge References

> Без Knowledge-first pre-flight спека не была бы возможна. Использовано из спеки #344:
> - `PollingCache` (который мы УДАЛИЛИ как концепцию — он для TTL).
> - `infra.cache.storage` SLF4J-категория (СОХРАНЯЕМ).
> - `HealthReport.cachedFileExists` companion object helper (СОХРАНЯЕМ).
>
> Новое из спеки #348:
> - Postgres (`deploy/karaoke-db/48_storage_metadata_cache.sql`) — Single Source of Truth.

### Pre-flight log

- **Дата pre-flight**: 2026-09-09
- **Grep-запросы**:
  1. `grep -ri "PollingCache" knowledge/` → `caching/components/web-caches.md` — для понимания, что удаляем.
  2. `grep -ri "infra.cache" knowledge/` → `monitoring/components/log-categories.md` — категория сохраняется.
  3. `grep -ri "tbl_storage" knowledge/ deploy/` → 0 hits (новая таблица).
  4. `grep -ri "write-through" knowledge/ deploy/` → 0 hits (новый паттерн).
  5. `grep -nE "INSERT INTO" deploy/karaoke-db/47_admin_process_bulk_actions.sql` → формат миграций проекта.

### Knowledge files consulted (selected)

- **`knowledge/domains/caching/components/web-caches.md`** — почему НЕ in-memory TTL.
- **`knowledge/domains/storage/components/karaoke-storage-service.md`** — `KaraokeStorageServiceImpl.uploadFile/deleteFile` для hook-точек.
- **`knowledge/domains/storage/components/storage-api-client.md`** — `StorageApiClientImpl.uploadFile/deleteFile` для hook-точек.
- **`knowledge/domains/monitoring/components/log-categories.md`** — категория `infra.cache.storage` уже зарегистрирована в Pass 344.
- **`knowledge/domains/caching/components/caching-patterns.md`** — Pass 343 FIFO cap pattern (НЕ применён — нет TTL).
- **`deploy/karaoke-db/47_admin_process_bulk_actions.sql`** — формат миграций (CREATE TABLE IF NOT EXISTS + COMMENT).

## User Scenarios & Testing

### User Story 1 — Ускорение загрузки Songs (Priority: P1)

**Описание**: HealthReport дёргает `fileExists` для каждой песни. После первого заполнения кеша (cold start admin открыл Songs page) — все последующие запросы ~5-20 ms (PG SELECT), а не 50-100 ms (MinIO HTTP). Кеш переживает рестарт `karaoke-app`.

**Why P1**: основная боль (72k HTTP round-trip) всё ещё актуальна, кеш реально persistent.

**Independent Test**: открыть Songs page, измерить latency, рестартовать `karaoke-app`, открыть снова — те же данные из кеша.

**Acceptance Scenarios**:

1. **Given** cold start `karaoke-app`, **When** admin открывает Songs page, **Then** все `fileExists` идут в MinIO → закэшированы в PG (~5-20 ms hit на каждый).
2. **Given** Songs page открыт и кеш заполнен, **When** admin выполняет `docker restart karaoke-app`, **Then** cache переживает рестарт — следующее открытие Songs page возвращает данные за ~5-20 ms на запрос (PG), а не 50-100 ms (MinIO).

### User Story 2 — Write-through invalidation (Priority: P1)

**Описание**: Когда `KaraokeProcess.upload*` или `delete*` пишет в MinIO, кеш автоматически обновляется (upload) или удаляется (delete). Следующий read видит актуальное состояние.

**Why P1**: иначе кеш был бы "вечно stale" — после любой записи показывал бы устаревший результат.

**Independent Test**: upload файл → проверить, что в `tbl_storage_metadata_cache` появилась строка с `exists=true` и актуальным etag/size. Удалить файл → проверить, что строка удалена.

**Acceptance Scenarios**:

1. **Given** `KaraokeStorageService.uploadFile(success=true)`, **When** hook срабатывает, **Then** `tbl_storage_metadata_cache` имеет row `(LOCAL, bucket, name)` с `exists=true, etag=..., size=...`.
2. **Given** hook сработал, **When** HealthReport читает `fileExists` для того же `(bucket, name)`, **Then** возвращает `true` без обращения к MinIO (SELECT из PG).
3. **Given** `deleteFile` удалил файл в MinIO, **When** hook сработал, **Then** строка в `tbl_storage_metadata_cache` УДАЛЕНА.
4. **Given** hook НЕ сработал (исключение в MinIO), **When** MinIO выбросил `MinioException`, **Then** строка в кеше НЕ меняется (existing state сохранён), операция bubbling exception.

### User Story 3 — Manual refresh (Priority: P2)

**Описание**: Admin может сбросить кеш для конкретного файла или всех файлов источника — через `DELETE /api/health/cache/refresh`. Это нужно когда файл изменён мимо Karaoke (через `mc`, прямой SQL) и кеш показывает stale.

**Why P2**: edge case, но без него кеш теряет смысл (накопительный stale).

**Acceptance Scenarios**:

1. **Given** кеш содержит `(LOCAL, song-1.mp4) → exists=true`, **When** файл удалён через `mc` мимо Karaoke, **Then** UI показывает stale true. После `DELETE /api/health/cache/refresh?source=LOCAL&bucket=...&fileName=song-1.mp4` UI покажет актуальный false (MinIO stat → PG upsert).
2. **Given** массовое изменение в bucket, **When** `DELETE /api/health/cache/refresh-all?source=LOCAL`, **Then** все строки источника удалены, следующие reads пойдут в MinIO.

### Edge Cases

- **MinIO down во время cold-start**: loader бросает exception → пробрасывается caller'у, **НО в кеш НЕ пишется** (как и в V1).
- **Manual refresh на отсутствующую строку**: 0 rows deleted → 200 OK (no-op).
- **Concurrent writes**: ON CONFLICT в upsert — последний writer выигрывает. Multi-replica если есть — аналогично.
- **TTL-staleness (manual fix)**: stale данные через `mc rm` мимо Karaoke **сохраняются** в кеше до явного refresh. Документировано в FR/Assumptions.

## Requirements

### Functional

- **FR-001**: System MUST кешировать in **Postgres** результаты `fileExists/bucket/name`, `fileIsActual/bucket/name`, `getFileInfo/bucket/name`. Single source of truth: `tbl_storage_metadata_cache`.
- **FR-002**: System MUST НЕ протухать кеш по TTL. Записи живут, пока явно не invalid'нуты (write-through или refresh).
- **FR-003**: System MUST update кеш при успешном `uploadFile` в `KaraokeStorageServiceImpl.uploadFile` и `StorageApiClientImpl.uploadFile` (write-through) — INSERT/UPDATE строки `(source, bucket, name)` с актуальными `exists=true, etag, size`.
- **FR-004**: System MUST DELETE строки из кеша при успешном `deleteFile` в обоих impls.
- **FR-005**: System MUST expose `DELETE /api/health/cache/refresh` с параметрами `source/bucket/fileName` (точечный) и `DELETE /api/health/cache/refresh-all?source=...` (bulk).
- **FR-006**: System MUST сохранять URL-decoded format cache key: `"$source:$bucket/$fileName"`. Caller (`HealthReport.cachedFileExists`) делает URL-decode ДО вызова.
- **FR-007**: System MUST экспонировать метрики через `GET /api/health/cacheStats`: `entries` (COUNT(*)), `hits`/`misses`/`evictions` (in-process counters).
- **FR-008**: System MUST логировать cache events через SLF4J-категорию `infra.cache.storage` (`cache:miss`, `cache:write-through`, `cache:refresh`).
- **FR-009**: System MUST НЕ менять публичные контракты `KaraokeStorageService` / `StorageApiClient` (см. spec #344 FR-011 — инвариант).
- **FR-010**: System MUST использовать `Connection.local()` (raw JDBC, не JPA/Hibernate) per Constitution Principle II.
- **FR-011**: System MUST НЕ генерировать валидации или пост-проверки — `mono.block()` запрещён (см. spec #344 FR-013).

### Non-Functional

- **NFR-001**: read latency — SELECT из PG ≤ 20 ms p99 (с индексом по PK `(source, bucket, file_name)`).
- **NFR-002**: cache persistence — `tbl_storage_metadata_cache` переживает рестарт `karaoke-app`, rebuild контейнера, restart Postgres (при наличии backup, отдельный вопрос).
- **NFR-003**: write-through latency overhead ≤ 5 ms на `upload/delete` (1 дополнительный INSERT/DELETE).
- **NFR-004**: observability — SLF4J `infra.cache.storage` INFO, без WARN/ERROR (как и V1).

## Success Criteria

- **SC-001**: F5 Songs page после первой загрузки — задержка идентична cold start (из PG). В ДО спеки — 50-100 ms × N файлов × 4 типов = минуты.
- **SC-002**: после `docker restart karaoke-app` данные из кеша доступны без обращения к MinIO (verified via `cache:miss` events count = 0 immediately after restart).
- **SC-003**: после `uploadFile` через `KaraokeStorageServiceImpl.uploadFile` — следующий `getFileExists` для того же `(bucket, name)` возвращает cached value БЕЗ обращения к MinIO (verified via отсутствие `cache:miss`).
- **SC-004**: после `DELETE /api/health/cache/refresh?bucket=X&name=Y` — следующий read идёт в MinIO (verified via `cache:miss` event).
- **SC-005**: метрики `/api/health/cacheStats` показывают `entries > 0` после cold start и продолжают расти по мере заполнения.

## Assumptions

1. **Postgres на той же машине, что и `karaoke-app`** (admin-машина). На проде — remote-БД `pg.sm-karaoke.ru` через VPN/SSH — отдельный вопрос, не в этой спеке.
2. **Multi-replica `karaoke-app` НЕТ** (только 1 admin-машина). Если появится — потребуется per-replica invalidation events через LISTEN/NOTIFY, отдельная спек.
3. **`uploadFile` / `deleteFile` остаются block'ирующими**, как в V1 и до. Write-through hook не делает асинхронных операций.
4. **Stale-after-external-mutation** — допустимое поведение для `mc rm`/`mc cp` мимо Karaoke. Documented в Edge Cases + User Story 3.

## Migration & Rollback

### Initial migration

```bash
# На LOCAL-БД:
psql -f deploy/karaoke-db/48_storage_metadata_cache.sql
```

Идемпотентно (`CREATE TABLE IF NOT EXISTS`).

### Rollback

`git revert <merge-commit>`. Удалить таблицу `DROP TABLE tbl_storage_metadata_cache`. Кеш отключится, `HealthReport` упадёт обратно на прямые MinIO round-trip (как до спеки #344).

### Supersedes

Спека #344 (Pass 344, commit `a1ac507f`) — **superseded**. Файлы удалены:
- `karaoke-app/.../services/PollingCache.kt`
- `karaoke-app/.../test/.../services/PollingCacheTest.kt`

Файлы модифицированы (cache V2 поверх V1 wiring):
- `karaoke-app/.../services/StorageMetadataCache.kt` (полностью переписан — Postgres DAO).
- `karaoke-app/.../services/KaraokeStorageService.kt` (`uploadFile`/`deleteFile` hooks).
- `karaoke-app/.../services/StorageApiClient.kt` (`uploadFile`/`deleteFile` hooks).
- `karaoke-app/.../controllers/CacheStatsController.kt` (description updated).

Файлы созданы:
- `deploy/karaoke-db/48_storage_metadata_cache.sql`
- `karaoke-app/.../controllers/CacheAdminController.kt`

## Связь с #344

| Аспект | #344 (V1) | #348 (V2) |
|---|---|---|
| Хранение | in-memory `ConcurrentHashMap` | Postgres `tbl_storage_metadata_cache` |
| TTL | 300s с auto-expiration | ∞ (eternal) |
| Persistence | нет (теряется на рестарте) | да (PG) |
| Write-through | нет (PollingCache lazy fill) | да (hooks в upload/delete) |
| Manual refresh | нет | `DELETE /api/health/cache/refresh` |
| Migration | `PollingCache` (80 строк copy) | `48_storage_metadata_cache.sql` |
| Endpoint cacheStats | `entries` = in-memory map.size | `entries` = COUNT(*) |
