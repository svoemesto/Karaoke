package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.HealthReport
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.HealthReportPoolCountMessage
import com.svoemesto.karaokeapp.model.HealthReportWaitingPoolSizeMessage
import com.svoemesto.karaokeapp.model.SseNotification
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
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
 * ## Два независимых пула (OpenProject #132, specs/132-hrwaiting-pool)
 *
 * Первый пул (`priorityQueue`, 10 worker'ов) — это **песни**, которые надо
 * пересчитать (одна задача = песня). Второй пул (`waitingQueue`, 20 worker'ов)
 * — это **WAITING-задания на обновление кеша хранилища**: одно задание = один
 * файл ([WaitingFileTask]). Когда `HealthReport.actionsRemoteStorage` обнаруживает
 * cache miss для файла, он кладёт задачу в `waitingQueue`. 20 worker'ов
 * **синхронно** проверяют файл в хранилище и заполняют
 * `StorageMetadataCache` — именно они, а не `cacheFillerExecutor`, дают
 * параллелизм 20. Взяли задачу — размер пула уменьшился — фронт получает SSE
 * `HEALTH_REPORT_WAITING_POOL_SIZE` и обновляет голубой бейдж. Одна и та же
 * задача не берётся двумя worker'ами (single-flight).
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
 *  - `waitingInFlight: ConcurrentHashMap<WaitingFileTask, AtomicBoolean>` —
 *    то же самое для WAITING-задач.
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
    /**
     * Одно WAITING-задание = **один файл** (OpenProject #132). Владелец: «Одно
     * задание = один файл».
     *
     * Содержит всё, что нужно worker'у для блокирующей проверки и заполнения
     * кеша: хранилище ([source]), bucket и имя файла. `songId` — для
     * последующего `recomputeAndBroadcast` (обновить HealthReport и разослать SSE).
     */
    data class WaitingFileTask(
        val songId: Long,
        val source: String,
        val bucket: String,
        val fileName: String,
    )

    companion object {
        private const val POOL_SIZE = 10

        /**
         * Число worker-потоков второго пула (WAITING-задачи), OpenProject #132.
         *
         * Владелец: «Пусть этих воркеров будет 20 (конкретное количество предложи —
         * должно работать в параллели быстро и не исчерпывать коннекшены к базе)».
         * Фиксированное число (не `cachedThreadPool`), чтобы не исчерпать Postgres
         * connection pool при массовом cold-start.
         */
        private const val POOL_SIZE_WAITING = 20
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
         * Последнее значение размера [waitingQueue], фактически отправленное в
         * SSE-канал `HEALTH_REPORT_WAITING_POOL_SIZE` (OpenProject #132).
         * Подавление дублей — тот же паттерн, что у [lastSentQueueSize].
         */
        @Volatile
        private var lastSentWaitingPoolSize: Long? = null

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

        /**
         * Рассылает через SSE-канал `HEALTH_REPORT_WAITING_POOL_SIZE` размер
         * второго пула WAITING-задач (OpenProject #132). Подавляет дубли.
         *
         * @see com.svoemesto.karaokeapp.model.SseNotificationType.HEALTH_REPORT_WAITING_POOL_SIZE
         * @see com.svoemesto.karaokeapp.model.HealthReportWaitingPoolSizeMessage
         * @see lastSentWaitingPoolSize
         */
        fun sendWaitingPoolSizeMessage(count: Long) {
            val previous = lastSentWaitingPoolSize
            if (previous != null && previous == count) return
            lastSentWaitingPoolSize = count
            try {
                SNS.send(
                    SseNotification.healthReportWaitingPoolSize(
                        HealthReportWaitingPoolSizeMessage(count = count),
                    ),
                )
            } catch (e: Exception) {
                companionLog.warn(
                    "failed to broadcast HEALTH_REPORT_WAITING_POOL_SIZE (count=$count): ${e.message}",
                    e,
                )
            }
        }

        /**
         * Только для unit-тестов: сбрасывает [lastSentWaitingPoolSize] в `null`.
         *
         * **Никогда не вызывается из production кода** — только из `*Test.kt`.
         */
        @Suppress("unused")
        internal fun resetLastSentWaitingPoolSizeForTest() {
            lastSentWaitingPoolSize = null
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
     * Второй пул — очередь WAITING-задач (OpenProject #132). Элемент — один файл
     * ([WaitingFileTask]). `LinkedBlockingDeque` thread-safe сам по себе
     * (не требует внешнего lock, в отличие от [priorityQueue]).
     */
    @Suppress("MemberVisibilityCanBePrivate")
    internal var waitingQueue: LinkedBlockingDeque<WaitingFileTask> = LinkedBlockingDeque()

    /**
     * Executor второго пула (20 worker'ов). Подменяется в unit-тестах.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    internal var waitingExecutor: ExecutorService = Executors.newFixedThreadPool(POOL_SIZE_WAITING)

    /**
     * Single-flight для WAITING-задач: один и тот же файл не обрабатывается двумя
     * worker'ами одновременно.
     */
    private val waitingInFlight: ConcurrentHashMap<WaitingFileTask, AtomicBoolean> = ConcurrentHashMap()

    /**
     * Флаг, контролирующий автозапуск worker'ов при [enqueue]. В production — `true`.
     * В unit-тестах ставится в `false`, чтобы worker не пытался выполнить реальный
     * `HealthReport.recomputeAndBroadcast` (зависит от БД/MinIO).
     */
    @Suppress("MemberVisibilityCanBePrivate")
    internal var workersEnabled: Boolean = true

    /**
     * Флаг для worker'ов второго пула (WAITING). В production — `true`.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    internal var waitingWorkersEnabled: Boolean = true

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
     * Добавить WAITING-задачи (каждая — один файл, [WaitingFileTask]) во второй
     * пул (OpenProject #132).
     *
     * Семантика:
     *  - **move-to-front**: задача, уже стоящая в [waitingQueue], удаляется и
     *    кладётся в голову — при возврате на уже посещённую страницу задания
     *    песен этой страницы «всплывают» (механизм тот же, что у [enqueue]).
     *  - **дедуп**: каждая задача встречается в очереди ровно один раз.
     *  - **single-flight**: задачи, которые прямо сейчас обрабатываются worker'ом
     *    (`waitingInFlight[task] == true`), не добавляются повторно — иначе
     *    `recomputeAndBroadcast`, вызванный изнутри worker'а, немедленно вернул бы
     *    ту же задачу в очередь и вызвал busy-loop.
     */
    fun enqueueWaiting(tasks: List<WaitingFileTask>) {
        if (tasks.isEmpty()) return
        var added = 0
        for (task in tasks.asReversed()) {
            if (task.songId <= 0L) continue
            if (waitingInFlight[task]?.get() == true) continue
            waitingQueue.remove(task)
            waitingQueue.addFirst(task)
            added++
        }
        if (added == 0) return
        log.debug("enqueued $added waiting task(s) (waitingQueueSize=${waitingQueueSize()})")
        sendWaitingPoolSizeMessage(waitingQueueSize().toLong())
        if (waitingWorkersEnabled) wakeupWaitingWorkers()
    }

    /** Текущий размер очереди. Под [priorityLock]. */
    fun queueSize(): Int = priorityLock.withLock { priorityQueue.size }

    /** Текущий размер очереди WAITING-задач (thread-safe). */
    fun waitingQueueSize(): Int = waitingQueue.size

    @PostConstruct
    fun start() {
        // OpenProject #132: привязываем себя к companion-объекту HealthReport,
        // чтобы recomputeAndBroadcast мог ставить WAITING-задачи в waitingQueue
        // (паттерн attach* из StorageMetadataCacheWiring / StorageCircuitBreakerWiring).
        HealthReport.attachHealthReportBatchPool(this)
        repeat(POOL_SIZE) {
            executor.submit { workerLoop() }
        }
        repeat(POOL_SIZE_WAITING) {
            waitingExecutor.submit { waitingWorkerLoop() }
        }
        log.info("started with $POOL_SIZE workers + $POOL_SIZE_WAITING waiting-workers")
    }

    @PreDestroy
    fun stop() {
        // OpenProject #132: shutdown ОБА executor'а. Раньше (Pass 128) второй пул
        // отсутствовал — после его добавления забыть про waitingExecutor означало
        // бы executor leak (потоки живут после @PreDestroy).
        waitingExecutor.shutdown()
        executor.shutdown()
        val bothTerminated =
            executor.awaitTermination(SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS) and
                waitingExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)
        if (!bothTerminated) {
            executor.shutdownNow()
            waitingExecutor.shutdownNow()
        }
        log.info("stopped")
    }

    private fun wakeupWorkers() {
        // 10 новых submit'ов: если worker уже работает — он возьмёт следующую
        // песню сам; если простаивает — выполнит одну итерацию и снова уснёт.
        repeat(POOL_SIZE) { executor.submit { workerLoop() } }
    }

    private fun wakeupWaitingWorkers() {
        repeat(POOL_SIZE_WAITING) { waitingExecutor.submit { waitingWorkerLoop() } }
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

    /**
     * Worker-loop второго пула (OpenProject #132). Берёт WAITING-задачу из головы
     * [waitingQueue] и **сам** выполняет блокирующую проверку файла в хранилище
     * ([HealthReport.cachedFileExists]), заполняя `StorageMetadataCache` в этом
     * worker-потоке. Именно поэтому 20 worker'ов дают 20 параллельных проверок —
     * в отличие от fire-and-forget в `cacheFillerExecutor`, который с unbounded
     * очередью и `corePoolSize=0` создаёт лишь один поток.
     *
     * После заполнения кеша песня ставится в [priorityQueue] (`enqueue`):
     * пересчёт HR и SSE-рассылку делает существующий song-пул (10 worker'ов) —
     * не дублируем путь `recomputeAndBroadcast`, не грузим БД лишний раз и
     * получаем автоматический дедуп через `inFlight`.
     *
     * Размер очереди рассылается через SSE сразу после взятия задачи — фронт
     * видит, как голубой бейдж уменьшается.
     */
    private fun waitingWorkerLoop() {
        while (!Thread.currentThread().isInterrupted) {
            val task = takeWaitingNext()
            if (task == null) {
                Thread.sleep(WORKER_IDLE_SLEEP_MS)
                continue
            }
            // Взяли задачу — пул уменьшился, обновляем бейдж.
            sendWaitingPoolSizeMessage(waitingQueueSize().toLong())
            if (!tryEnterWaiting(task)) {
                // Single-flight: другой worker уже обрабатывает этот файл.
                continue
            }
            try {
                // Блокирующая проверка файла + заполнение кеша в ЭТОМ потоке.
                HealthReport.cachedFileExists(
                    source = task.source,
                    bucket = task.bucket,
                    fileName = task.fileName,
                    loader = {
                        storageApiClient.fileExists(bucketName = task.bucket, fileName = task.fileName)
                    },
                )
                // Кеш заполнен — просим song-пул пересчитать HR (WAITING → OK/ERROR)
                // и разослать SSE. inFlight в song-пуле дедуплицирует повторные
                // запросы по одной песне.
                enqueue(listOf(task.songId))
            } catch (e: Exception) {
                log.warn("waiting task failed for $task: ${e.message}", e)
            } finally {
                exitWaiting(task)
            }
        }
    }

    private fun takeNext(): Long? =
        priorityLock.withLock {
            if (priorityQueue.isEmpty()) null else priorityQueue.removeAt(0)
        }

    private fun takeWaitingNext(): WaitingFileTask? = waitingQueue.pollFirst()

    private fun tryEnter(songId: Long): Boolean =
        inFlight
            .computeIfAbsent(songId) { AtomicBoolean(false) }
            .compareAndSet(false, true)

    private fun exit(songId: Long) {
        inFlight[songId]?.set(false)
        // NB: ключ НЕ удаляем из map (избегаем memory churn на горячих
        // путях — паттерн из race-fixed-65.md).
    }

    private fun tryEnterWaiting(task: WaitingFileTask): Boolean =
        waitingInFlight
            .computeIfAbsent(task) { AtomicBoolean(false) }
            .compareAndSet(false, true)

    private fun exitWaiting(task: WaitingFileTask) {
        waitingInFlight[task]?.set(false)
        // NB: ключ НЕ удаляем из map — тот же паттерн, что в exit().
    }
}
