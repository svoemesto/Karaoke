package com.svoemesto.karaokeapp.services

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Circuit breaker для file-* методов `StorageApiClientImpl` (Pass 351, #71).
 *
 * **Ответственность**: защита caller thread от блокировки на `readTimeout=60s`
 * (MinIO SDK) при network outage. Каждый `file*` вызов оборачивается через
 * [decorate] — single-call timeout + circuit breaker (CLOSED / HALF_OPEN / OPEN).
 *
 * **In-memory only** (per Q1, Pass 351 clarification): AtomicReference + AtomicLong,
 * без Postgres. При рестарте `karaoke-app` state сбрасывается в CLOSED.
 *
 * **Half-open probe** (per Q3): после cooldown — single probe call; success → CLOSED,
 * failure → OPEN (reset openedAtMs). Не ломает state одной новой ошибкой.
 *
 * **Watchdog (Pass 372, #131, спека #405)**: если state=HALF_OPEN дольше
 * `timeoutSeconds + watchdogBufferSeconds` без `recordSuccess`/`recordFailure`
 * (т.е. probe завис — Mono.timeout race / GC pause / thread death),
 * watchdog принудительно переводит state в OPEN с обновлённым `openedAtMs`,
 * чтобы дать circuit следующий шанс на probe через `cooldownSeconds`.
 *
 * **Manual reset (Pass 372, FR-007 спеки #352)**: [reset] сбрасывает state в CLOSED.
 * Endpoint `POST /api/health/circuit-breaker/reset` в [CircuitBreakerController].
 *
 * **Blocking loader timeout (Pass 426, спека #426, OpenProject #150)**: [decorate] и
 * [decorateOrEmpty] выполняют `loader()` на [Schedulers.boundedElastic] (`subscribeOn`),
 * чтобы оператор `.timeout(timeoutSeconds)` имел реальную силу. Без этого блокирующий
 * MinIO-вызов (OkHttp `connectTimeout`) выполнялся на вызывающем потоке и не прерывался
 * таймаутом — фактическое ожидание было временем блокировки (15s), что больше
 * `timeoutSeconds + watchdogBufferSeconds` (15s), и circuit навсегда залипал в
 * HALF_OPEN→OPEN (watchdog «probe stuck»).
 *
 * **SLF4J** (per FR-004, Pass 351): категория `infra.cache.storage` (existing, Pass 344).
 * Events:
 * - `cache:network:failure` (WARN per fail)
 * - `cache:circuit:state` (INFO per transition CLOSED↔OPEN↔HALF_OPEN)
 * - `cache:circuit:watchdog` (WARN, Pass 372) — watchdog перевёл HALF_OPEN→OPEN (probe stuck)
 * - `cache:circuit:reset` (INFO, Pass 372) — manual reset endpoint вызван
 *
 * @see specs/352-storage-graceful-degradation/spec.md (FR-002, FR-004, NFR-002)
 * @see specs/405-storage-circuit-breaker-watchdog/spec.md (Pass 372, #131)
 * @see docs/features/storage-metadata-cache.md
 */
@Component
class StorageCircuitBreaker(
    @Value("\${storage.file-exists-timeout-seconds:5}") private val timeoutSeconds: Long,
    @Value("\${storage.circuit-breaker-threshold:5}") private val threshold: Int,
    @Value("\${storage.circuit-breaker-cooldown-seconds:30}") private val cooldownSeconds: Long,
    @Value("\${storage.circuit-breaker-watchdog-enabled:true}") private val watchdogEnabled: Boolean,
    @Value("\${storage.circuit-breaker-watchdog-buffer-seconds:10}") private val watchdogBufferSeconds: Long,
    @Value("\${storage.circuit-breaker-watchdog-check-interval-seconds:1}") private val checkIntervalSeconds: Long,
) {
    enum class State { CLOSED, HALF_OPEN, OPEN }

    /**
     * Решение, выдаваемое [acquire]:
     * - [Allow] — CLOSED state, вызываем MinIO напрямую.
     * - [Probe] — OPEN+HALF_OPEN edge (после cooldown), single probe call.
     * - [FastFail] — OPEN не на cooldown, или HALF_OPEN после первого probe winner'а.
     */
    sealed class Decision {
        object Allow : Decision()

        object Probe : Decision()

        object FastFail : Decision()
    }

    data class Metrics(
        val state: State,
        val failureCount: Long,
        val lastFailureAt: Long?,
        val totalSuccesses: Long,
        val totalNetworkFailures: Long,
        val threshold: Int,
        val cooldownSeconds: Long,
        val timeoutSeconds: Long,
        val watchdogEnabled: Boolean,
        val watchdogBufferSeconds: Long,
        val checkIntervalSeconds: Long,
    )

    private val state = AtomicReference(State.CLOSED)
    private val failureCount = AtomicLong(0)
    private val openedAtMs = AtomicLong(0L)
    private val totalSuccesses = AtomicLong(0L)
    private val totalNetworkFailures = AtomicLong(0L)
    private val halfOpenSinceMs = AtomicLong(0L)
    private val log = LoggerFactory.getLogger("infra.cache.storage")

    /**
     * Pass 426 (#150): scheduler для блокирующих loader'ов. `loader()` обычно —
     * `Mono.fromCallable { blocking MinIO call }`. На вызывающем потоке `.timeout()`
     * не мог его прервать (см. KDoc класса). `boundedElastic` — глобальный
     * синглтон reactor, отдельного dispose не требует.
     */
    private val blockingScheduler: Scheduler = Schedulers.boundedElastic()

    @Volatile
    private var watchdogExecutor: ScheduledExecutorService? = null

    /**
     * Запуск watchdog (Pass 372, FR-001).
     * ScheduledExecutorService с single daemon thread, не блокирует Spring shutdown.
     */
    @PostConstruct
    fun initWatchdog() {
        if (!watchdogEnabled) {
            log.info("cache:circuit:watchdog state=DISABLED reason=watchdog_disabled_in_config")
            return
        }
        val executor =
            Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "storage-circuit-breaker-watchdog").apply { isDaemon = true }
            }
        executor.scheduleAtFixedRate(
            { watchdogTick() },
            checkIntervalSeconds,
            checkIntervalSeconds,
            TimeUnit.SECONDS,
        )
        watchdogExecutor = executor
        log.info(
            "cache:circuit:watchdog state=STARTED checkIntervalSeconds={} bufferSeconds={} timeoutSeconds={}",
            checkIntervalSeconds, watchdogBufferSeconds, timeoutSeconds,
        )
    }

    /**
     * Остановка watchdog (Pass 372, FR-006). Graceful shutdown.
     */
    @PreDestroy
    fun destroyWatchdog() {
        watchdogExecutor?.let { executor ->
            executor.shutdown()
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("cache:circuit:watchdog state=SHUTDOWN_TIMEOUT")
                } else {
                    log.info("cache:circuit:watchdog state=STOPPED")
                }
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            watchdogExecutor = null
        }
    }

    /**
     * Watchdog tick (Pass 372, FR-002/FR-003). Если state=HALF_OPEN дольше
     * `timeoutSeconds + watchdogBufferSeconds` — принудительно OPEN.
     */
    private fun watchdogTick() {
        if (state.get() != State.HALF_OPEN) return
        val sinceMs = halfOpenSinceMs.get()
        if (sinceMs == 0L) return
        val now = System.currentTimeMillis()
        val deadline = timeoutSeconds * 1000L + watchdogBufferSeconds * 1000L
        val elapsed = now - sinceMs
        if (elapsed > deadline && state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
            openedAtMs.set(now)
            halfOpenSinceMs.set(0L)
            log.warn(
                "cache:circuit:watchdog state=HALF_OPEN->OPEN (probe stuck) durationMs={} openedAtMs={}",
                elapsed, now,
            )
        }
    }

    /**
     * Acquire decision. Single-thread-CAS по state.
     */
    fun acquire(): Decision {
        val current = state.get()
        return when (current) {
            State.CLOSED -> Decision.Allow
            State.OPEN -> {
                val cooldown = (System.currentTimeMillis() - openedAtMs.get()) > cooldownSeconds * 1000L
                if (cooldown && state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    val now = System.currentTimeMillis()
                    halfOpenSinceMs.set(now)
                    log.info(
                        "cache:circuit:state from={} to={} failureCount={} openedAtMs={}",
                        State.OPEN, State.HALF_OPEN, failureCount.get(), openedAtMs.get(),
                    )
                    Decision.Probe
                } else {
                    Decision.FastFail
                }
            }
            State.HALF_OPEN -> Decision.FastFail
        }
    }

    /**
     * Запись success. Переход HALF_OPEN→CLOSED (probe success).
     */
    fun recordSuccess() {
        totalSuccesses.incrementAndGet()
        if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
            failureCount.set(0)
            halfOpenSinceMs.set(0L)
            log.info(
                "cache:circuit:state from={} to={} failureCount=0",
                State.HALF_OPEN, State.CLOSED,
            )
        }
    }

    /**
     * Запись failure. CLOSED→OPEN (если threshold reached) или HALF_OPEN→OPEN.
     */
    fun recordFailure(error: Throwable) {
        totalNetworkFailures.incrementAndGet()
        val current = state.get()
        when (current) {
            State.CLOSED -> {
                val newCount = failureCount.incrementAndGet()
                log.warn(
                    "cache:network:failure error=\"{}\" failureCount={} threshold={}",
                    error.javaClass.simpleName, newCount, threshold,
                )
                if (newCount >= threshold && state.compareAndSet(State.CLOSED, State.OPEN)) {
                    openedAtMs.set(System.currentTimeMillis())
                    log.warn(
                        "cache:circuit:state from={} to={} failureCount={} openedAtMs={}",
                        State.CLOSED, State.OPEN, newCount, openedAtMs.get(),
                    )
                }
            }
            State.HALF_OPEN -> {
                log.warn(
                    "cache:network:failure error=\"{}\" state=HALF_OPEN->OPEN (probe failed)",
                    error.javaClass.simpleName,
                )
                if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                    val now = System.currentTimeMillis()
                    openedAtMs.set(now)
                    halfOpenSinceMs.set(0L)
                    log.warn(
                        "cache:circuit:state from={} to={} failureCount={} openedAtMs={}",
                        State.HALF_OPEN, State.OPEN, failureCount.get(), now,
                    )
                }
            }
            State.OPEN -> {
                // already OPEN, no-op (но log для diagnostics)
                log.warn(
                    "cache:network:failure error=\"{}\" state=OPEN (still open)",
                    error.javaClass.simpleName,
                )
            }
        }
    }

    /**
     * Manual reset (Pass 372, FR-005). Сбрасывает state в CLOSED + обнуляет counters.
     * Endpoint `POST /api/health/circuit-breaker/reset` в [CircuitBreakerController].
     * Идемпотентен — повторный вызов в CLOSED no-op.
     */
    fun reset(): Metrics {
        val previousState = state.get()
        state.set(State.CLOSED)
        failureCount.set(0)
        openedAtMs.set(0L)
        halfOpenSinceMs.set(0L)
        log.info(
            "cache:circuit:reset reason=manual_request previousState={} currentState={}",
            previousState, State.CLOSED,
        )
        return metrics()
    }

    /**
     * Decorate: обёртка loader в circuit + timeout. Возвращает `emptyValue` при
     * circuit open ИЛИ timeout ИЛИ exception. Используется caller'ом:
     * `circuit.decorate("fileExists", { storageApiClient.checkIfExists(...) }, false).block()`.
     */
    fun <T : Any> decorate(operation: String, loader: () -> Mono<T>, emptyValue: T): Mono<T> =
        Mono.defer {
            val decision = acquire()
            when (decision) {
                Decision.FastFail -> {
                    log.warn(
                        "cache:network:failure operation={} decision=FastFail (circuit open)",
                        operation,
                    )
                    Mono.just(emptyValue)
                }
                Decision.Allow, Decision.Probe -> {
                    loader()
                        .subscribeOn(blockingScheduler)
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .doOnSuccess { recordSuccess() }
                        .doOnError { recordFailure(it) }
                        .onErrorReturn(emptyValue)
                }
            }
        }

    /**
     * Variant для nullable result (например, [StorageFileInfo]?).
     * Loader возвращает `Mono<T>` (non-null), но мы можем вернуть empty.
     */
    fun <T : Any> decorateOrEmpty(operation: String, loader: () -> Mono<T>): Mono<T> =
        Mono.defer {
            val decision = acquire()
            when (decision) {
                Decision.FastFail -> {
                    log.warn(
                        "cache:network:failure operation={} decision=FastFail (circuit open)",
                        operation,
                    )
                    Mono.empty()
                }
                Decision.Allow, Decision.Probe -> {
                    loader()
                        .subscribeOn(blockingScheduler)
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .doOnSuccess { recordSuccess() }
                        .doOnError { recordFailure(it) }
                        .onErrorResume { Mono.empty() }
                }
            }
        }

    fun state(): State = state.get()

    fun metrics(): Metrics =
        Metrics(
            state = state.get(),
            failureCount = failureCount.get(),
            lastFailureAt = if (openedAtMs.get() == 0L) null else openedAtMs.get(),
            totalSuccesses = totalSuccesses.get(),
            totalNetworkFailures = totalNetworkFailures.get(),
            threshold = threshold,
            cooldownSeconds = cooldownSeconds,
            timeoutSeconds = timeoutSeconds,
            watchdogEnabled = watchdogEnabled,
            watchdogBufferSeconds = watchdogBufferSeconds,
            checkIntervalSeconds = checkIntervalSeconds,
        )
}
