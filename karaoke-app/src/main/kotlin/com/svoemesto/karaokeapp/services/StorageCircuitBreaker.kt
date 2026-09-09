package com.svoemesto.karaokeapp.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration
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
 * **SLF4J** (per FR-004, Pass 351): категория `infra.cache.storage` (existing, Pass 344).
 * Events: `cache:network:failure` (WARN per fail), `cache:circuit:state` (INFO per transition).
 *
 * @see specs/352-storage-graceful-degradation/spec.md (FR-002, FR-004, NFR-002)
 * @see docs/features/storage-metadata-cache.md
 */
@Component
class StorageCircuitBreaker(
    @Value("\${storage.file-exists-timeout-seconds:5}") private val timeoutSeconds: Long,
    @Value("\${storage.circuit-breaker-threshold:5}") private val threshold: Int,
    @Value("\${storage.circuit-breaker-cooldown-seconds:30}") private val cooldownSeconds: Long,
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
    )

    private val state = AtomicReference(State.CLOSED)
    private val failureCount = AtomicLong(0)
    private val openedAtMs = AtomicLong(0L)
    private val totalSuccesses = AtomicLong(0L)
    private val totalNetworkFailures = AtomicLong(0L)
    private val log = LoggerFactory.getLogger("infra.cache.storage")

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
                    openedAtMs.set(System.currentTimeMillis())
                    log.warn(
                        "cache:circuit:state from={} to={} failureCount={} openedAtMs={}",
                        State.HALF_OPEN, State.OPEN, failureCount.get(), openedAtMs.get(),
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
        )
}
