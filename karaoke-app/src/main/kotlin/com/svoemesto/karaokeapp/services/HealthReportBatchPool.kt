package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.HealthReport
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.HealthReportPoolCountMessage
import com.svoemesto.karaokeapp.model.SseNotification
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Асинхронный пул с приоритетной очередью для батч-запросов healthReportList
 * (OpenProject #128, specs/128-async-health-report-list).
 *
 * **Проблема**: при открытии страницы админки «Песни» фронт делал каскад
 * синхронных HTTP-запросов по одному на песню — 5-10 секунд блокировки UI.
 *
 * **Решение**: новый эндпоинт `POST /api/song/healthReportListBatch` принимает
 * `List<Long>` и **мгновенно** возвращает 202 Accepted. Этот сервис ставит
 * песни в приоритетную очередь, 10 worker-потоков исполняют
 * [HealthReport.recomputeAndBroadcast] для каждой песни параллельно, результаты
 * рассылаются через SSE-канал `HEALTH_REPORTS`.
 *
 * **Семантика приоритетной очереди** (требование #128):
 *  - При поступлении нового батча все его `songId` вставляются в **начало**.
 *  - Если `songId` уже в очереди и ещё не исполнен — он **перемещается в начало**
 *    (move-to-front).
 *  - Worker берёт `songId` из начала.
 *
 * **Single-flight** (паттерн из `race-fixed-65.md`):
 *  - `inFlight: ConcurrentHashMap<Long, AtomicBoolean>` защищает от
 *    повторного `recomputeAndBroadcast` для одной песни в разных worker'ах.
 *
 * **Не используется** [com.svoemesto.karaokeapp.KaraokeProcess] — нам не нужны
 * цепочки, персистентность across restart и UI-бейджи. Простой `ExecutorService`
 * достаточен.
 *
 * @see HealthReport.recomputeAndBroadcast единая точка пересчёта + SSE-рассылки.
 * @see com.svoemesto.karaokeapp.model.SseNotification.healthReports payload SSE.
 * @see knowledge/domains/health/components/health-report-batch-pool.md Living Docs.
 */
@Service
class HealthReportBatchPool(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
) {
    companion object {
        private const val POOL_SIZE = 10
        private const val WORKER_IDLE_SLEEP_MS = 50L
        private const val SHUTDOWN_TIMEOUT_SEC = 5L
        private const val LOG_CATEGORY = "infra.cache.hrpool"
        private val companionLog = LoggerFactory.getLogger(LOG_CATEGORY)

        /**
         * Парсит входную строку `songIds` (формат `"1;2;3"` — конвенция проекта,
         * см. `KaraokeProcessAdminController.kt:185` и `Processes/store.js`)
         * в список `Long`. Невалидные/пустые значения молча отбрасываются.
         */
        fun parseSongIds(raw: String?): List<Long> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(";").mapNotNull { it.trim().toLongOrNull() }
        }

        /**
         * Последнее значение `queueSize`, фактически отправленное в SSE-канал
         * через [sendPoolCountMessage]. Подавление дублей (FR-001): если новое
         * значение совпадает с последним отправленным — событие не рассылается.
         * `null` — ещё ни разу не отправляли (после рестарта бэкенда).
         *
         * `@Volatile` — пишется из любых worker-потоков и из HTTP-потока
         * (call-site `enqueue`), читается там же. Без `@Volatile` JMM не
         * гарантирует visibility (см. аналогичный `lastSentCountWaiting` в
         * `KaraokeProcessWorker`).
         *
         * @see sendPoolCountMessage
         */
        @Volatile
        private var lastSentQueueSize: Long? = null

        /**
         * Рассылает через SSE-канал `HEALTH_REPORT_POOL_COUNT` размер приоритетной
         * очереди [HealthReportBatchPool]. Подавляет дубли (если `count` совпадает
         * с `lastSentQueueSize` — событие не шлётся).
         *
         * Паттерн скопирован из `KaraokeProcessWorker.sendCountWaitingMessage`
         * (companion-object static helper + `@Volatile` поле).
         *
         * @see com.svoemesto.karaokeapp.model.SseNotificationType.HEALTH_REPORT_POOL_COUNT
         * @see com.svoemesto.karaokeapp.model.HealthReportPoolCountMessage
         * @see lastSentQueueSize
         */
        fun sendPoolCountMessage(count: Long) {
            val previous = lastSentQueueSize
            if (previous != null && previous == count) return
            lastSentQueueSize = count
            try {
                SNS.send(
                    SseNotification.healthReportPoolCount(
                        HealthReportPoolCountMessage(count = count),
                    ),
                )
            } catch (e: Exception) {
                companionLog.warn(
                    "failed to broadcast HEALTH_REPORT_POOL_COUNT (count=$count): ${e.message}",
                    e,
                )
            }
        }

        /**
         * Только для unit-тестов: сбрасывает [lastSentQueueSize] в `null`.
         * Companion-объект — статическое состояние, разделяемое между тестами,
         * поэтому нужно обнулять его в `@BeforeEach`/`@AfterEach`, чтобы
         * подавление дублей не «протекало» из одного теста в другой.
         *
         * **Никогда не вызывается из production кода** — только из `*Test.kt`.
         */
        @Suppress("unused")
        internal fun resetLastSentQueueSizeForTest() {
            lastSentQueueSize = null
        }
    }

    private val log = LoggerFactory.getLogger(LOG_CATEGORY)
    private val priorityLock = ReentrantLock()
    private val priorityQueue: MutableList<Long> = mutableListOf()
    private val inFlight: ConcurrentHashMap<Long, AtomicBoolean> = ConcurrentHashMap()

    /**
     * Executor для worker'ов. Инициализируется в [start] (production). В unit-тестах
     * может быть подменён напрямую (internal visibility).
     */
    @Suppress("MemberVisibilityCanBePrivate")
    internal var executor: ExecutorService = Executors.newFixedThreadPool(POOL_SIZE)

    /**
     * Добавить песни в приоритетную очередь.
     *
     * Алгоритм (под [priorityLock]):
     *  1. Идём по `songIds.asReversed()` — это гарантирует, что **первый id**
     *     батча окажется **на позиции 0** после серии `add(0, ...)`
     *     (каждый следующий `add(0, id)` сдвигает ранее вставленные вправо).
     *  2. Для каждого id: `remove(id)` (если был в очереди) + `add(0, id)`.
     *
     * Итог: батч целиком встаёт в начало очереди, а внутри батча сохраняется
     * исходный порядок — первый элемент батча становится самым первым в
     * под-блоке. Это требование #128 («при переходе между страницами „в работу“
     * в чанки будут браться песни с этой страницы в первую очередь»).
     *
     * После разблокировки — `wakeupWorkers()`: 10 новых submit'ов в executor.
     * Если worker'ы уже работают, дубликаты тасков безвредны: `takeNext()`
     * атомарно разбирает очередь, остальные таски делают одну итерацию
     * (взял null → sleep → выход).
     */
    fun enqueue(songIds: List<Long>) {
        if (songIds.isEmpty()) return

        priorityLock.withLock {
            for (id in songIds.asReversed()) {
                if (id <= 0L) continue
                priorityQueue.remove(id) // если есть — удаляем (move-to-front)
                priorityQueue.add(0, id) // вставляем в начало
            }
        }
        log.debug("enqueued batch of ${songIds.size} (queueSize=${queueSize()})")
        // specs/129-hrpool-badge: рассылаем актуальный размер пула в SSE.
        // Дубликаты подавляются в sendPoolCountMessage.
        sendPoolCountMessage(queueSize().toLong())
        if (workersEnabled) wakeupWorkers()
    }

    /**
     * Флаг, контролирующий автозапуск worker'ов при [enqueue]. В production — `true`.
     * В unit-тестах ставится в `false`, чтобы worker не пытался выполнить реальный
     * `HealthReport.recomputeAndBroadcast` (зависит от БД/MinIO).
     */
    @Suppress("MemberVisibilityCanBePrivate")
    internal var workersEnabled: Boolean = true

    /** Текущий размер очереди. Под [priorityLock]. */
    fun queueSize(): Int = priorityLock.withLock { priorityQueue.size }

    @PostConstruct
    fun start() {
        repeat(POOL_SIZE) {
            executor.submit { workerLoop() }
        }
        log.info("started with $POOL_SIZE workers")
    }

    @PreDestroy
    fun stop() {
        executor.shutdown()
        if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }
        log.info("stopped")
    }

    private fun wakeupWorkers() {
        // 10 новых submit'ов: если worker уже работает — он возьмёт следующую
        // песню сам; если простаивает — выполнит одну итерацию и снова уснёт.
        repeat(POOL_SIZE) { executor.submit { workerLoop() } }
    }

    private fun workerLoop() {
        while (!Thread.currentThread().isInterrupted) {
            val id = takeNext()
            if (id == null) {
                Thread.sleep(WORKER_IDLE_SLEEP_MS)
                continue
            }
            // specs/129-hrpool-badge: размер пула уменьшился — рассылаем.
            // Дубликаты подавляются в sendPoolCountMessage.
            sendPoolCountMessage(queueSize().toLong())
            if (!tryEnter(id)) {
                // Single-flight: другой worker уже считает эту песню.
                continue
            }
            try {
                HealthReport.recomputeAndBroadcast(
                    songId = id,
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                )
            } catch (e: Exception) {
                // Одна проблемная песня не должна валить worker. Логируем через
                // SLF4J-категорию `infra.cache.hrpool` (Pass 128).
                log.warn("recomputeAndBroadcast failed for songId=$id: ${e.message}", e)
            } finally {
                exit(id)
            }
        }
    }

    private fun takeNext(): Long? =
        priorityLock.withLock {
            if (priorityQueue.isEmpty()) null else priorityQueue.removeAt(0)
        }

    private fun tryEnter(songId: Long): Boolean =
        inFlight
            .computeIfAbsent(songId) { AtomicBoolean(false) }
            .compareAndSet(false, true)

    private fun exit(songId: Long) {
        inFlight[songId]?.set(false)
        // NB: ключ НЕ удаляем из map (избегаем memory churn на горячих
        // путях — паттерн из race-fixed-65.md).
    }
}
