package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.Connection
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Persistent (eternal) cache для метаданных MinIO — спека #348 (Pass 345), supersede #344.
 *
 * **TTL = ∞**. Кеш НЕ протухает сам по себе.
 *
 * **Single source of truth**: таблица `tbl_storage_metadata_cache` в LOCAL Postgres
 * (миграция `deploy/karaoke-db/48_storage_metadata_cache.sql`). Переживает
 * restart/rebuild `karaoke-app` и `webvue3`.
 *
 * **Invalidation**: 3 источника, write-through:
 *   1. **Hooks**: `StorageApiClient.uploadFile/deleteFile` +
 *      `KaraokeStorageService.uploadFile/deleteFile` вызывают
 *      `recordUpload` / `recordDelete` после успеха.
 *   2. **Manual refresh**: `POST /api/health/cache/refresh` (см. `CacheAdminController`).
 *   3. **No auto-invalidation** (сознательное решение): stale-данные после прямых
 *      `mc rm` мимо Karaoke остаются stale пока кто-то явно не нажмёт refresh.
 *      Это и есть "вечный" кеш — пользователь контролирует.
 *
 * **Контракт**: НЕ МЕНЯЕТ публичные сигнатуры `KaraokeStorageService` /
 * `StorageApiClient` (FR-011 спеки #344). Только добавляет хуки в их implementation.
 *
 * **Что кешируется**: результаты `fileExists` (Boolean), `fileIsActual` (Boolean)
 * и `getFileInfo` (StorageFileInfo). URL-decoded имя файла — caller'а
 * ответственность (`HealthReport.cachedFileExists` в companion object).
 *
 * **Multi-replica** (если несколько `karaoke-app`): PG обеспечивает консистентность
 * через UNIQUE constraint + ON CONFLICT. В проекте сейчас 1 admin-машина →
 * проблема не возникает, но архитектурно поддерживается.
 *
 * @see specs/348-storage-cache-eternal/spec.md
 * @see docs/features/storage-metadata-cache.md
 * @see knowledge/domains/caching/components/web-caches.md
 */
@Component
class StorageMetadataCache {
    // specs/118 #397: инициализируем companion object при создании Spring bean,
    // чтобы cacheFillerExecutor был готов принимать задачи сразу. Без этого
    // executor создаётся лениво при первом обращении к companion, и первые запросы
    // кэша могут зависнуть (worker'ы ещё не запущены).
    init {
        // Доступ к Companion-членам для их инициализации.
        SOURCE_LOCAL // touch any companion member to force class init
        // Явное обращение к executor для его eager initialization.
        cacheFillerExecutor
    }

    companion object {
        const val SOURCE_LOCAL = "LOCAL"
        const val SOURCE_REMOTE = "REMOTE"

        /**
         * String logger `infra.cache.storage` (см. ADR `local-0005`).
         */
        private val log = LoggerFactory.getLogger("infra.cache.storage")

        /**
         * String logger `infra.cache.storage.waiting` (спека #368) — события fill
         * с durationMs (для hit rate анализа). Отдельная категория от `infra.cache.storage`,
         * чтобы не дублировать `cache:miss`/`cache:write-through` шум.
         */
        private val waitingLog = LoggerFactory.getLogger("infra.cache.storage.waiting")

        /**
         * Shared thread pool for async cache fill (US2, spec #364).
         *
         * Жёстко ограничен `maxPoolSize=16` (см. OP #83): раньше использовался
         * `Executors.newCachedThreadPool()` без аргументов, что фактически означало
         * `maxPoolSize = Integer.MAX_VALUE`. Каждый REMOTE cache miss создавал НОВЫЙ
         * поток + НОВЫЙ ThreadLocal JDBC-connection через `Connection.local().getConnection()`,
         * и при быстром переключении страниц в SongsTable.vue это превышало
         * Postgres `max_connections=100` за минуты.
         *
         * Семантика сохранена: `corePoolSize=0` + `keepAliveTime=60s` ведут себя как
         * cached pool — потоки умирают после простоя. `LinkedBlockingDeque` без
         * bound обеспечивает unbounded двустороннюю очередь submit'ов; рост упирается
         * в maxPoolSize.
         *
         * specs/118 #397: двусторонняя очередь (вместо LinkedBlockingQueue) нужна для
         * реализации LIFO/всплытия задач активной страницы — submit() кладёт в конец,
         * submitFirst() в начало. setActiveSongIds() переупорядочивает: задачи с активными
         * songId извлекаются и кладутся в начало.
         *
         * Подробнее: `research/83-db-pool-root-cause/REPORT.md`.
         */
        private val cacheFillerExecutor =
            ThreadPoolExecutor(
                0,
                16,
                60L,
                TimeUnit.SECONDS,
                LinkedBlockingDeque(),
            ).also { executor ->
                Runtime.getRuntime().addShutdownHook(Thread { executor.shutdown() })
            }

        /**
         * specs/118 #397: множество активных songId (песен текущей страницы админки).
         * Задачи cache-fill с этими songId должны обрабатываться в приоритете —
         * вставляться в начало deque при submit и всплывать при смене страницы.
         *
         * `volatile` — пишется из HTTP-потока (контроллер), читается из executor'а
         * (cache fill submit) и из reorder-логики. Без `@Volatile` запись могла быть
         * не видна другому потоку вовремя (JMM не гарантирует visibility для обычного var).
         */
        @Volatile private var activeSongIds: Set<Long> = emptySet()

        /**
         * specs/118 #397: Runnable-обёртка с пометкой songId, чтобы reorder мог
         * извлекать задачи по типу из deque.
         */
        private class SongIdTaggedRunnable(
            val songId: Long,
            private val delegate: Runnable,
        ) : Runnable {
            override fun run() = delegate.run()
        }

        /**
         * specs/118 #397: установить множество активных songId (песен текущей страницы).
         * Вызывается из `POST /api/health/activeSongIds` при смене страницы в SongsTable.
         *
         * После установки — переупорядочивает очередь: все Runnable-обёртки с активными
         * songId, ещё ожидающие в deque, извлекаются и кладутся в начало (всплытие).
         *
         * Синхронизировано через synchronized — одновременно с submit'ами не должно быть
         * гонки. Сами submit'ы (в `getFileExistsAsync`) тоже используют synchronized на этом
         * lock'е через `submitFrontSafe` / `submitBackSafe`.
         */
        @Synchronized
        fun setActiveSongIds(newActiveSongIds: Set<Long>) {
            val previous = activeSongIds
            activeSongIds = newActiveSongIds.toSet()
            if (previous == newActiveSongIds) return
            // Переупорядочиваем очередь: задачи с новыми activeSongIds → в начало
            // (всплытие). Используем pollFirst/addLast в цикле — это единственный безопасный
            // способ изменить LinkedBlockingDeque, не повреждая внутреннее состояние
            // ThreadPoolExecutor (worker'ы). drainTo вызывает конфликт с getTask().
            //
            // Для правильного порядка в голове: итерируем [max, mid, min] и addFirst
            // каждого → конечный порядок в голове [min, mid, max]. Worker берёт из
            // головы → сначала min → обработка сверху вниз по songId.
            val queue = cacheFillerExecutor.queue as LinkedBlockingDeque<Runnable>
            val active = mutableListOf<Runnable>()
            val notActive = mutableListOf<Runnable>()
            // Извлекаем все задачи, разделяем на активные/неактивные.
            while (true) {
                val r = queue.pollFirst() ?: break
                if (r is SongIdTaggedRunnable && r.songId in activeSongIds) {
                    active.add(r)
                } else {
                    notActive.add(r)
                }
            }
            // Возвращаем: сначала активные в начало (в порядке убывания songId →
            // в голове окажется возрастание), затем неактивные в конец.
            for (r in active.sortedByDescending { (it as SongIdTaggedRunnable).songId }) {
                queue.addFirst(r)
            }
            for (r in notActive) {
                queue.addLast(r)
            }
            log.info(
                "activeSongIds changed: prev={} new={} reordered={}",
                previous.size, newActiveSongIds.size, active.size,
            )
        }

        /**
         * specs/118 #397: submit в КОНЕЦ deque (обычное FIFO поведение). Используется
         * по умолчанию для неактивных songId. Без @Synchronized — cacheFillerExecutor.execute
         * сам по себе потокобезопасен (LinkedBlockingDeque.take блокирует worker, не наш монитор).
         */
        fun submitBack(task: Runnable) {
            cacheFillerExecutor.execute(task)
        }

        /**
         * specs/118 #397: submit в НАЧАЛО deque (LIFO). Используется для активных songId —
         * задача будет обработана раньше FIFO-задач.
         *
         * Проблема: `ThreadPoolExecutor.execute()` всегда добавляет в очередь через
         * `BlockingQueue.offer()`. Для LinkedBlockingDeque offer() = offerLast() — конец.
         * Чтобы добавить в начало, нужно использовать `addFirst()` напрямую.
         *
         * Реализация: добавляем task в начало deque вручную. Worker'ы (если они
         * заблокированы на `BlockingQueue.take()`) будут разбужены автоматически.
         * Если worker'ов нет и corePoolSize=0, новый worker не создастся — но
         * следующий submit() создаст worker, который возьмёт нашу задачу из головы.
         *
         * Потокобезопасность: synchronized — одновременно с `setActiveSongIds` не должно
         * быть гонки. Сам `LinkedBlockingDeque.addFirst` потокобезопасен.
         */
        fun submitFront(task: Runnable) {
            // Добавляем в начало deque через cast (LinkedBlockingDeque.addFirst thread-safe).
            // Без @Synchronized — addFirst сам по себе атомарен.
            (cacheFillerExecutor.queue as LinkedBlockingDeque<Runnable>).addFirst(task)
        }

        /**
         * specs/118 #397: текущий размер очереди cache-fill (не считая выполняющихся).
         * Используется для бейджа в UI (правый верхний угол кнопки Старт/Стоп).
         *
         * `@JvmStatic` — чтобы можно было вызывать через экземпляр Spring-bean
         * (иначе только через companion).
         */
        fun cacheQueueSize(): Int = cacheFillerExecutor.queue.size

        /**
         * specs/118 #397: получить текущее множество активных songId (read-only).
         */
        fun getActiveSongIds(): Set<Long> = activeSongIds
    }

    /**
     * Метрики для `/api/health/cacheStats`. `entries` — COUNT(*) по таблице,
     * `hits`/`misses` — локальные счётчики процесса (НЕ across-restart).
     */
    data class CacheStatsDto(
        val local: StatsBucket,
        val remote: StatsBucket,
    )

    data class StatsBucket(
        val entries: Long,
        val hits: Long,
        val misses: Long,
        val hitRatio: Double,
        val evictions: Long,
    )

    private val localHits =
        java.util.concurrent.atomic
            .LongAdder()
    private val localMisses =
        java.util.concurrent.atomic
            .LongAdder()
    private val localDeletes =
        java.util.concurrent.atomic
            .LongAdder()
    private val remoteHits =
        java.util.concurrent.atomic
            .LongAdder()
    private val remoteMisses =
        java.util.concurrent.atomic
            .LongAdder()
    private val remoteDeletes =
        java.util.concurrent.atomic
            .LongAdder()

    /**
     * Cache key: `"$source:$bucket/$fileName"`.
     */
    private fun buildKey(source: String, bucket: String, fileName: String): String =
        "$source:$bucket/$fileName"

    /**
     * SELECT из кеша, fallback на [loader] при miss.
     *
     * @param source LOCAL или REMOTE.
     * @param bucket bucket name (NOT hardcoded; per Constitution).
     * @param fileName file name (URL-decoded caller'ом).
     * @param loader blocking `() -> Boolean` — вызывается только при cache miss.
     * @param onFillComplete callback, вызываемый ПОСЛЕ успешного fill (спека #368).
     *   Если кеш уже заполнен (hit) — callback НЕ вызывается. Используется для
     *   recompute+SSE после cold-start (см. HealthReport.cachedFileExists).
     */
    fun getFileExists(
        source: String,
        bucket: String,
        fileName: String,
        loader: () -> Boolean,
        onFillComplete: (() -> Unit)? = null,
    ): Boolean {
        validate(source, bucket, fileName)
        val cached = selectExists(source, bucket, fileName)
        if (cached != null) {
            hit(source)
            return cached
        }
        miss(source)
        val startedAt = System.currentTimeMillis()
        val value = loader()
        upsert(source, bucket, fileName, exists = value, etag = null, sizeBytes = null)
        val durationMs = System.currentTimeMillis() - startedAt
        log.info(
            "cache:miss key={} bucket={} fileName={} source={} operation=fileExists value={}",
            buildKey(source, bucket, fileName), bucket, fileName, source, value,
        )
        waitingLog.info(
            "cache:filled source={} bucket={} fileName={} operation=fileExists status=FILLED durationMs={}",
            source, bucket, fileName, durationMs,
        )
        onFillComplete?.invoke()
        return value
    }

    /**
     * SELECT из кеша, fallback на [loader] при miss.
     */
    fun getFileIsActual(source: String, bucket: String, fileName: String, loader: () -> Boolean): Boolean {
        validate(source, bucket, fileName)
        val cached = selectExists(source, bucket, fileName)
        if (cached != null) {
            hit(source)
            return cached
        }
        miss(source)
        val value = loader()
        upsert(source, bucket, fileName, exists = value, etag = null, sizeBytes = null)
        log.info(
            "cache:miss key={} bucket={} fileName={} source={} operation=fileIsActual value={}",
            buildKey(source, bucket, fileName), bucket, fileName, source, value,
        )
        return value
    }

    /**
     * SELECT с загрузкой StorageFileInfo. Loader MUST blocking.
     */
    fun getFileInfo(source: String, bucket: String, fileName: String, loader: () -> StorageFileInfo): StorageFileInfo {
        validate(source, bucket, fileName)
        val cached = selectFileInfo(source, bucket, fileName)
        if (cached != null) {
            hit(source)
            return cached
        }
        miss(source)
        val value = loader()
        upsert(source, bucket, fileName, exists = true, etag = value.etag, sizeBytes = value.size)
        log.info(
            "cache:miss key={} bucket={} fileName={} source={} operation=getFileInfo etag={} size={}",
            buildKey(source, bucket, fileName), bucket, fileName, source, value.etag, value.size,
        )
        return value
    }

    /**
     * Write-through hook: вызывается из `StorageApiClient.uploadFile` и
     * `KaraokeStorageService.uploadFile` после успешного MinIO upload.
     */
    fun recordUpload(source: String, bucket: String, fileName: String, etag: String?, sizeBytes: Long?) {
        validate(source, bucket, fileName)
        upsert(source, bucket, fileName, exists = true, etag = etag, sizeBytes = sizeBytes)
        log.info(
            "cache:write-through bucket={} fileName={} source={} operation=upload etag={} size={}",
            bucket, fileName, source, etag, sizeBytes,
        )
    }

    /**
     * Write-through hook: вызывается из `deleteFile` impls после успешного MinIO delete.
     */
    fun recordDelete(source: String, bucket: String, fileName: String) {
        validate(source, bucket, fileName)
        delete(source, bucket, fileName)
        if (source == SOURCE_LOCAL) localDeletes.increment() else remoteDeletes.increment()
        log.info(
            "cache:write-through bucket={} fileName={} source={} operation=delete",
            bucket, fileName, source,
        )
    }

    /**
     * Manual refresh: точечный DELETE строки. Следующий read пойдёт в MinIO.
     */
    fun refresh(source: String, bucket: String, fileName: String): Int {
        validate(source, bucket, fileName)
        val deleted = delete(source, bucket, fileName)
        if (source == SOURCE_LOCAL) localDeletes.increment() else remoteDeletes.increment()
        log.info(
            "cache:refresh bucket={} fileName={} source={} rowsDeleted={}",
            bucket, fileName, source, deleted,
        )
        return deleted
    }

    /**
     * Manual refresh: bulk DELETE всех строк (по источнику).
     */
    fun refreshAll(source: String): Int {
        require(source == SOURCE_LOCAL || source == SOURCE_REMOTE) { "source must be LOCAL or REMOTE" }
        val conn: java.sql.Connection =
            Connection.local().getConnection()
                ?: error("Cannot get local connection")
        return try {
            conn
                .prepareStatement("DELETE FROM tbl_storage_metadata_cache WHERE source = ?")
                .use { ps ->
                    ps.setString(1, source)
                    ps.executeUpdate()
                }.also { count ->
                    if (source == SOURCE_LOCAL) {
                        localDeletes.add(count.toLong())
                    } else {
                        remoteDeletes.add(count.toLong())
                    }
                    log.info("cache:refresh-all source={} rowsDeleted={}", source, count)
                }
        } finally {
            conn.close()
        }
    }

    /**
     * Stats для endpoint `/api/health/cacheStats`. `entries` — COUNT(*),
     * `hits/misses/evictions` — локальные in-process счётчики.
     */
    fun stats(): CacheStatsDto =
        CacheStatsDto(
            local =
                StatsBucket(
                    entries = countEntries(SOURCE_LOCAL),
                    hits = localHits.sum(),
                    misses = localMisses.sum(),
                    hitRatio = hitRatio(localHits.sum(), localMisses.sum()),
                    evictions = localDeletes.sum(),
                ),
            remote =
                StatsBucket(
                    entries = countEntries(SOURCE_REMOTE),
                    hits = remoteHits.sum(),
                    misses = remoteMisses.sum(),
                    hitRatio = hitRatio(remoteHits.sum(), remoteMisses.sum()),
                    evictions = remoteDeletes.sum(),
                ),
        )

    /**
     * Утилита: открыть локальное JDBC-соединение и выполнить блок. Закрывает
     * соединение через `use { conn -> ... }` — гарантия от утечки JDBC.
     */
    private inline fun <T> withConn(block: (java.sql.Connection) -> T): T {
        val conn: java.sql.Connection =
            Connection.local().getConnection()
                ?: error("Cannot get LOCAL JDBC connection")
        return conn.use(block)
    }

    // ---- Internals ------------------------------------------------

    private fun selectExists(source: String, bucket: String, fileName: String): Boolean? =
        withConn { conn ->
            conn
                .prepareStatement(
                    "SELECT exists FROM tbl_storage_metadata_cache WHERE source = ? AND bucket = ? AND file_name = ?",
                ).use { ps ->
                    ps.setString(1, source)
                    ps.setString(2, bucket)
                    ps.setString(3, fileName)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getBoolean(1) else null }
                }
        }

    private fun selectFileInfo(source: String, bucket: String, fileName: String): StorageFileInfo? =
        withConn { conn ->
            conn
                .prepareStatement(
                    "SELECT exists, etag, size FROM tbl_storage_metadata_cache WHERE source = ? AND bucket = ? AND file_name = ?",
                ).use { ps ->
                    ps.setString(1, source)
                    ps.setString(2, bucket)
                    ps.setString(3, fileName)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) {
                            null
                        } else {
                            val exists = rs.getBoolean(1)
                            val etag = rs.getString(2) ?: ""
                            val size = rs.getLong(3)
                            if (!exists) null else StorageFileInfo(bucket, fileName, etag, size)
                        }
                    }
                }
        }

    private fun upsert(
        source: String,
        bucket: String,
        fileName: String,
        exists: Boolean,
        etag: String?,
        sizeBytes: Long?,
    ) = withConn { conn ->
        conn
            .prepareStatement(
                """
                INSERT INTO tbl_storage_metadata_cache (source, bucket, file_name, exists, etag, size, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (source, bucket, file_name) DO UPDATE
                SET exists = EXCLUDED.exists, etag = EXCLUDED.etag, size = EXCLUDED.size, updated_at = NOW()
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, source)
                ps.setString(2, bucket)
                ps.setString(3, fileName)
                ps.setBoolean(4, exists)
                ps.setString(5, etag)
                if (sizeBytes != null) ps.setLong(6, sizeBytes) else ps.setNull(6, java.sql.Types.BIGINT)
                ps.executeUpdate()
            }
    }

    private fun delete(source: String, bucket: String, fileName: String): Int =
        withConn { conn ->
            conn
                .prepareStatement(
                    "DELETE FROM tbl_storage_metadata_cache WHERE source = ? AND bucket = ? AND file_name = ?",
                ).use { ps ->
                    ps.setString(1, source)
                    ps.setString(2, bucket)
                    ps.setString(3, fileName)
                    ps.executeUpdate()
                }
        }

    private fun countEntries(source: String): Long =
        withConn { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM tbl_storage_metadata_cache WHERE source = ?").use { ps ->
                ps.setString(1, source)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
            }
        }

    @Suppress("MagicNumber")
    private fun hitRatio(hits: Long, misses: Long): Double {
        val total = hits + misses
        return if (total == 0L) {
            1.0
        } else {
            Math.round(hits.toDouble() / total * 10_000.0) / 10_000.0
        }
    }

    private fun hit(source: String) {
        (if (source == SOURCE_LOCAL) localHits else remoteHits).increment()
    }

    private fun miss(source: String) {
        (if (source == SOURCE_LOCAL) localMisses else remoteMisses).increment()
    }

    private fun validate(source: String, bucket: String, fileName: String) {
        require(source == SOURCE_LOCAL || source == SOURCE_REMOTE) {
            "source must be LOCAL or REMOTE, got: $source"
        }
        require(bucket.isNotBlank()) { "bucket must not be blank" }
        require(fileName.isNotBlank()) { "fileName must not be blank" }
    }

    /**
     * Async version of [getFileExists] — spec #364, US2 (FR-007).
     *
     * **Behavior on cache HIT**: returns immediately with cached value (same as sync).
     * **Behavior on cache MISS**: fires `loader()` asynchronously in a background thread,
     * immediately returns `null`. When the async loader completes, result is upserted
     * into cache for future requests.
     *
     * **Use case**: non-blocking cold-start for HealthReport — the caller receives `null`
     * on cache miss and treats it as `IN_PROGRESS`, while the cache is filled in background.
     *
     * **Спека #368 (Pass 368)**: после успешного background fill вызывается
     * `onFillComplete` callback. Используется для recompute+SSE после cold-start.
     * На cache hit callback НЕ вызывается.
     *
     * @return `CompletableFuture<Boolean?>` — null means "unknown (async fill in progress)".
     */
    fun getFileExistsAsync(
        source: String,
        bucket: String,
        fileName: String,
        loader: () -> Boolean,
        onFillComplete: (() -> Unit)? = null,
        songId: Long = 0,
    ): CompletableFuture<Boolean?> {
        validate(source, bucket, fileName)
        val cached = selectExists(source, bucket, fileName)
        if (cached != null) {
            hit(source)
            return CompletableFuture.completedFuture(cached)
        }
        miss(source)
        // Fire-and-forget: fill cache in background
        val delegate =
            Runnable {
                val startedAt = System.currentTimeMillis()
                try {
                    val value = loader()
                    upsert(source, bucket, fileName, exists = value, etag = null, sizeBytes = null)
                    val durationMs = System.currentTimeMillis() - startedAt
                    log.info(
                        "cache:miss:async key={} bucket={} fileName={} source={} operation=fileExists value={}",
                        buildKey(source, bucket, fileName), bucket, fileName, source, value,
                    )
                    waitingLog.info(
                        "cache:filled source={} bucket={} fileName={} operation=fileExists status=FILLED durationMs={}",
                        source, bucket, fileName, durationMs,
                    )
                    onFillComplete?.invoke()
                } catch (e: Exception) {
                    val durationMs = System.currentTimeMillis() - startedAt
                    log.warn(
                        "cache:miss:async:error key={} bucket={} fileName={} source={} operation=fileExists error={}",
                        buildKey(source, bucket, fileName), bucket, fileName, source, e.message,
                    )
                    waitingLog.warn(
                        "cache:fillFailed source={} bucket={} fileName={} operation=fileExists status=FAILED durationMs={} error={}",
                        source, bucket, fileName, durationMs, e.message,
                    )
                    // onFillComplete НЕ вызывается — US2/AC3 спеки: остаёмся в WAITING
                }
            }
        // specs/118 #397: если songId активен (на текущей странице) — submit в начало deque.
        // Иначе — submit в конец (FIFO).
        if (songId != 0L && songId in activeSongIds) {
            submitFront(SongIdTaggedRunnable(songId, delegate))
        } else {
            submitBack(delegate)
        }
        return CompletableFuture.completedFuture(null)
    }
}
