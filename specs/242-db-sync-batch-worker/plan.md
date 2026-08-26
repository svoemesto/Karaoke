# Implementation Plan: Батч-синхронизация sync-записей в KaraokeProcessWorker

**Branch**: `242-db-sync-batch-worker` | **Date**: 2026-08-26 | **Spec**: [spec.md](spec.md)
**Parent**: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md) — Tier-1 / FR-101

**Input**: Feature specification from `/specs/242-db-sync-batch-worker/spec.md`

## Summary

Заменить N+1 в sync-цикле `KaraokeProcessWorker.doStart()` (строки 998–1106): сейчас 1 SELECT sync + N SELECT к LOCAL + N DELETE к REMOTE = `1 + 2N` SQL-запросов на каждые 24 сек при N sync-записях. После рефакторинга — пакетные `loadListFromDbByIds` (chunk=25) + батч-Delete `id = ANY(?)` = `1 + 2*(N/25)` SQL-запросов. На 100 записях: 201 → ≤11 SQL (снижение в 18×). Сохраняет side-effect для `tags = "RENDER"` (per-song операция) и существующий контракт синхронизации.

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17), Spring Boot 2.x/3.x (karaoke-app, admin-машина)
**Primary Dependencies**: `KaraokeConnection` (raw JDBC), `Connection.local()`/`remote()` (singleton фабрики, см. specs/234-db-sync-connection-leak), `Song.loadListFromDbByIds` (generic helper из `KaraokeDbTable`), `SyncRegistry.DELETE_CHUNK_SIZE`
**Storage**: PostgreSQL (через сырой JDBC) — таблицы `tbl_songs_sync` (REMOTE), `tbl_songs` (LOCAL); запись `tags` колонки определяет side-effect
**Testing**: ручное на admin-машине (см. Constitution § Тесты — `@Disabled`); замер эффекта через `pg_log` 24 ч до/после деплоя
**Target Platform**: Linux server (admin-машина с `karaoke-app`, prod karaoke-web НЕ затронут)
**Project Type**: library/multi-module Gradle (`karaoke-app` + `karaoke-web` + `karaoke-db`)
**Performance Goals**: ≤11 SQL при N=100 sync-записях (SC-001), ≤82 SQL при N=1000 (SC-002), ≥30% снижение SQL от `KaraokeProcessWorker` в `pg_log` (SC-003)
**Constraints**: сохранить Constitutional § II «Сырой JDBC + дифф по хэшам» (никакого JPA/Hibernate); сохранить side-effect `tags = "RENDER"` (per-song); chunk_size=25 для тяжёлых строк Song (validated by `SongSyncTarget`)
**Scale/Scope**: до 1000+ sync-записей в одном окне (после большого push LOCAL→SERVER), цикл раз в 24 сек на admin-машине (вне prod-трафика)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- ✅ **Principle I (Self-contained автопайплайн)**: не затрагивается — это рефакторинг существующего sync-цикла, не вводит внешних SaaS-зависимостей.
- ✅ **Principle II (Сырой JDBC + дифф по хэшам)**: сохраняется. `Song.loadListFromDbByIds` использует сырой JDBC + `WHERE id IN (...)` — точно соответствует пункту «Загрузка записей для diff — пакетно `WHERE id IN (..)`, не по одной в цикле». Эта фича ИСПРАВЛЯЕТ нарушение (существующий код использует `forEach { loadFromDbById }`).
- ✅ **Principle III (SyncRegistry)**: не затрагивается — `SongSyncTarget` уже использует `chunked(rowChunkSize)`. Наша фича — в sync-цикле `KaraokeProcessWorker`, не в `SyncRegistry`.
- ✅ **Principle IV (Async-очередь)**: не затрагивается — sync-цикл не меняет структуру очереди.
- ✅ **Principle V (Двух-фронтенд)**: не затрагивается — это backend-логика.
- ✅ **Principle VI (Code Standards)**: сохраняется. KDoc обязателен для нового helper'а (FR-006 spec.md). Code-style — стандартный Kotlin. Каждая фича — отдельный PR (это уже так).
- ✅ **Principle VII (Cross-Machine)**: не затрагивается.
- ✅ **Principle VIII (Секреты)**: не затрагивается — нет секрет-файлов.

**Constitution Check: PASS** — фича полностью соответствует всем принципам.

## Project Structure

### Documentation (this feature)

```text
specs/242-db-sync-batch-worker/
├── plan.md              # Этот файл
├── spec.md              # Feature specification (FR-101 из parent)
├── checklists/
│   └── requirements.md  # 16/16 ✅
└── tasks.md             # Phase 2 (через /speckit.tasks)
```

### Source Code (changes)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── KaraokeProcessWorker.kt        # ИЗМЕНЕНИЕ: doStart() sync-блок (998–1106)
└── model/
    ├── KaraokeDbTable.kt          # Возможно: добавить deleteBatch helper
    └── Song.kt                    # Без изменений (loadListFromDbByIds уже есть)
```

**Structure Decision**: Single project (Option 1). Изменения — точечные, в существующих файлах. Никаких новых модулей.

## Implementation Approach

### Phase 1: Подготовка (verify preconditions)

**Перед началом** проверить:
1. `Song.loadListFromDbByIds` — существует и использует `chunked(rowChunkSize)` (parent спека, A.4, `SongSyncTarget.loadByIds:241`).
2. `KaraokeDbTable.deleteBatch` / эквивалент — проверить наличие. Если отсутствует → создать как companion-метод.
3. `SyncRegistry.DELETE_CHUNK_SIZE` — проверить значение (parent спека, A.1, комментарий в `SyncTarget.kt:99`).
4. `SongSyncTarget.rowChunkSize = 25` — использовать как `CHUNK_SIZE` для SELECT к LOCAL.

### Phase 2: Извлечение helper'а (refactor)

Создать приватный метод `KaraokeProcessWorker.processRemoteSongsSyncBatch(database, storageService, storageApiClient): SyncBatchResult`:

```kotlin
private data class SyncBatchResult(
    val localUpdates: Int,
    val localInserts: Int,
    val renderSideEffects: Int,
    val sqlQueryCount: Int,
)

private fun processRemoteSongsSyncBatch(
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): SyncBatchResult {
    // 1 SELECT: все sync-записи с REMOTE
    val listSongsSync = Song.loadListFromDb(
        database = Connection.remote(),
        sync = true,
        storageService = storageService,
        storageApiClient = storageApiClient,
    )
    if (listSongsSync.isEmpty()) return SyncBatchResult(0, 0, 0, 1)

    // 1 SELECT (chunked): загрузка local по всем ID разом
    val localIds = listSongsSync.map { it.id }
    val localSongs = localIds.chunked(SELECT_CHUNK_SIZE).flatMap { chunk ->
        Song.loadListFromDbByIds(ids = chunk, database = database, ...)
    }.associateBy { it.id }

    var updates = 0
    var inserts = 0
    var renderSideEffects = 0

    listSongsSync.forEach { songSync ->
        val songLocal = localSongs[songSync.id]
        if (songLocal != null) {
            // UPDATE — существующая логика diff
            val diff = Song.getDiff(songSync, songLocal)
            val setStr = diff.filter { it.recordDiffRealField }
                .joinToString(", ") { "${it.recordDiffName} = ?" }
            if (setStr != "") {
                // existing UPDATE SQL — без изменений
                updates++
            }
        } else {
            // INSERT — существующая логика
            inserts++
        }

        // Side-effect для tags = "RENDER" — БЕЗ изменений (per-song)
        if (songSync.tags == "RENDER") {
            songLocal?.let { local ->
                // existing render logic
                renderSideEffects++
            }
        }
    }

    // 1 DELETE (chunked): удаление всех sync-записей
    val deletedIds = listSongsSync.map { it.id }.chunked(DELETE_CHUNK_SIZE)
    deletedIds.forEach { chunk ->
        KaraokeDbTable.deleteBatch(
            tableName = Song.TABLE_NAME,
            ids = chunk,
            database = Connection.remote(),
            sync = true,
        )
    }

    val totalSql = 1 + localIds.chunked(SELECT_CHUNK_SIZE).size + deletedIds.size
    return SyncBatchResult(updates, inserts, renderSideEffects, totalSql)
}
```

### Phase 3: Интеграция в `doStart()`

В `KaraokeProcessWorker.doStart()` строки 994-1106 заменить на:

```kotlin
if (Karaoke.monitoringRemoteSettingsSync) {
    val syncResult = processRemoteSongsSyncBatch(database, storageService, storageApiClient)
    if (syncResult.localUpdates + syncResult.localInserts > 0) {
        println("[${Timestamp.from(Instant.now())}] ProcessWorker: Sync-batch обработан: " +
            "updates=${syncResult.localUpdates}, inserts=${syncResult.localInserts}, " +
            "renderSideEffects=${syncResult.renderSideEffects}, sql=${syncResult.sqlQueryCount}")
    }
}
```

### Phase 4: Утилита `deleteBatch` (если отсутствует)

Добавить в `KaraokeDbTable.Companion` (если ещё нет, проверить grep):

```kotlin
fun deleteBatch(
    tableName: String,
    ids: List<Long>,
    database: KaraokeConnection,
    sync: Boolean = false,
): Int {
    if (ids.isEmpty()) return 0
    val connection = database.getConnection() ?: return 0
    val sql = "DELETE FROM $tableName${if (sync) "_sync" else ""} WHERE id = ANY(?)"
    return try {
        val pgConnection = connection.unwrap(org.postgresql.PGConnection::class.java)
        val array = pgConnection.createArrayOf("BIGINT", ids.toTypedArray())
        connection.prepareStatement(sql).use { ps ->
            ps.setArray(1, array)
            ps.executeUpdate()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        0
    }
}
```

**Замечание**: `connection.unwrap(PGConnection::class.java)` — специфично для PostgreSQL JDBC. Альтернатива — текстовая подстановка `id IN (1,2,3,...)` (менее безопасно). Рекомендуется — через `Array`.

## Risks & Mitigations

| Риск | Вероятность | Митигация |
|------|-------------|-----------|
| `Song.loadListFromDbByIds` имеет другую сигнатуру | Низкая | Проверить в Phase 1 (preconditions). |
| `deleteBatch` через `Array` ломается на некоторых JDBC-драйверах | Средняя | Fallback: текстовая подстановка `id IN (1,2,...)`. Проверить в karaoke-app на admin-машине перед мержем. |
| Chunk_size=25 выбран неоптимально | Низкая | Это уже проверенное значение для Song (см. `SongSyncTarget.rowChunkSize`). |
| Side-effect для `tags = "RENDER"` ломается | Средняя | SC-005 regression-тест: после деплоя сделать push с RENDER-тегом, убедиться что процессы создаются. |
| Скрытая зависимость от thread-local connection | Низкая | `Connection.local()/remote()` singleton с ThreadLocal (см. specs/087-fix-shared-db-connection) — корректно работает. |

## Out-of-Scope (напоминание)

- Рефакторинг всего `KaraokeProcessWorker.doStart()` (1264 строк) — только sync-блок.
- Оптимизация per-song INSERT (строки ~1056–1065) — Tier-3.
- Изменение `intervalCheckFiles = 24_000` — константа.
- Изменение side-effect для `tags = "RENDER"` — только проверка, что сохранился.
- Tier-2/Tier-3 из parent спеки.

## Complexity Tracking

*Нет нарушений Constitution Check — таблица пуста.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет) | — | — |

## Verification Plan

### До деплоя (baseline)

1. Сделать `pg_log` снэпшот за 24 часа: `SELECT count(*), substring(query, 1, 80) FROM pg_log WHERE query LIKE '%tbl_songs%' GROUP BY substring(query, 1, 80) ORDER BY count(*) DESC LIMIT 20`.
2. Замерить p95 latency `KaraokeProcessWorker.doStart()` через JMX-метрики (если есть) или через визуальное наблюдение за docker logs.

### После деплоя

1. Снять `pg_log` за 24 часа после деплоя, сравнить с baseline.
2. Сделать **искусственный sync-сценарий**:
   - Вставить 100 записей в `tbl_songs_sync` REMOTE-БД (или LOCAL через `Connection.remote()`).
   - Дождаться 24 сек (`intervalCheckFiles`).
   - Замерить количество SQL в docker logs `KaraokeProcessWorker` (должно быть ≤ 11, не 201).
3. Regression: пуш с `tags = "RENDER"` — убедиться что karaoke-процессы создаются (SC-005).
4. Проверить, что в docker logs нет `ERROR` или `Exception` (особенно `SQLException`, `MinioException`, `ClassCastException` для PGConnection).

### Acceptance (mapping)

- **SC-001** (≤11 SQL при N=100): измерение в docker logs.
- **SC-002** (≤82 SQL при N=1000): аналогично.
- **SC-003** (≥30% снижение в `pg_log`): сравнение baseline/post-deploy.
- **SC-004** (цикломатическая ≤8, ≤60 строк): code-review.
- **SC-005** (RENDER сохранилось): regression-тест вручную.
- **SC-006** (переиспользование helpers): code-review.

## Timeline Estimate

- Phase 1 (preconditions за 5 мин: проверить helper'ы).
- Phase 2 (refactor в helper): 30–45 мин.
- Phase 3 (интеграция в doStart): 15 мин.
- Phase 4 (deleteBatch): 15–30 мин (если нужен).
- **Итого: ~1–2 часа кодинга**.
- Тестирование на admin-машине: 30 мин.
- Deploy + 24 ч наблюдения + verification: 1 день.

## Definition of Done

- [ ] Все 6 FR из spec.md реализованы.
- [ ] Все 6 SC из spec.md измеримы и подтверждены (через `pg_log` + regression-тест RENDER).
- [ ] ktlintCheck + compile проходит (см. AGENTS.md, «Обязательная проверка после правок»).
- [ ] Per-feature документ в `archive/docs/features/` обновлён (если есть — sync-feature; если нет — создать).
- [ ] PR создан через `gh pr create --base master` (см. AGENTS.md, «CI-gate для master»).
- [ ] CI (lint.yml) — 8/8 PASS.
- [ ] 1 PR → 1 merge в master → 1 деплой на admin-машину (НЕ на прод — это admin-only).

## Next Step

→ `/speckit.tasks specs/242-db-sync-batch-worker` для генерации декомпозированных задач.
