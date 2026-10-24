package com.svoemesto.karaokeapp.services

import io.minio.errors.MinioException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pass 428 (#152): тесты interrupt-безопасной обработки блокирующих MinIO-вызовов
 * и отсутствия `onErrorDropped` при timeout-отмене (follow-up Pass 426).
 */
class StorageTimeoutInterruptTest {
    @Test
    fun `isInterruptWrapped detects direct InterruptedException`() {
        assertTrue(isInterruptWrapped(InterruptedException("stop")))
    }

    @Test
    fun `isInterruptWrapped detects RuntimeException cause chain`() {
        val wrapped = RuntimeException(InterruptedException("stop"))
        assertTrue(isInterruptWrapped(wrapped))
    }

    @Test
    fun `isInterruptWrapped detects nested cause`() {
        val nested = RuntimeException(RuntimeException(InterruptedException("stop")))
        assertTrue(isInterruptWrapped(nested))
    }

    @Test
    fun `isInterruptWrapped returns false for unrelated errors`() {
        assertFalse(isInterruptWrapped(IllegalStateException("boom")))
        assertFalse(isInterruptWrapped(RuntimeException("boom", java.net.SocketTimeoutException())))
    }

    @Test
    fun `runBlockingMinioOrNull converts interrupt-wrapper to null and restores flag`() {
        val oldFlag = Thread.currentThread().isInterrupted
        try {
            val result: String? =
                runBlockingMinioOrNull<String> {
                    throw RuntimeException(InterruptedException("cancel"))
                }
            assertEquals(null, result)
            assertTrue(Thread.currentThread().isInterrupted, "interrupt flag must be restored")
        } finally {
            Thread.interrupted() // clear for test isolation
            if (oldFlag) Thread.currentThread().interrupt()
        }
    }

    @Test
    fun `runBlockingMinioOrNull converts MinioException to null`() {
        val result: String? =
            runBlockingMinioOrNull<String> {
                throw MinioException("not found")
            }
        assertEquals(null, result)
    }

    @Test
    fun `runBlockingMinioOrNull rethrows unrelated RuntimeException`() {
        val thrown =
            org.junit.jupiter.api.assertThrows<IllegalStateException> {
                runBlockingMinioOrNull<String> { throw IllegalStateException("real bug") }
            }
        assertEquals("real bug", thrown.message)
    }

    @Test
    fun `runBlockingMinioOrNull passes through successful value`() {
        assertEquals("ok", runBlockingMinioOrNull { "ok" })
    }

    /**
     * Интеграционный (in-process) тест: timeout-отмена блокирующего вызова, который
     * при interrupt бросает `RuntimeException(InterruptedException)` (как MinIO SDK),
     * но обёрнут в [runBlockingMinioOrNull] — ошибка MUST NOT уйти в реактор как dropped.
     *
     * Проверка построена на конкретном результате, а не на отсутствии глобального
     * события:
     *  - прерывание действительно произошло (иначе тест проверял бы не тот сценарий);
     *  - прерванная задача ДОЖДАНА завершения (ожидание условия с таймаутом вместо
     *    фиксированного `Thread.sleep(500)` — «опоздавшая» ошибка приходит позже
     *    прежнего окна в 500 мс и при нём оставалась незамеченной);
     *  - в список dropped попадают ошибки ВСЕХ тестов этого JVM, поэтому проверяется
     *    только ошибка НАШЕГО теста (по маркеру в сообщении) — чужая ошибка не
     *    должна ронять этот тест.
     */
    @Test
    fun `decorate timeout does not produce onErrorDropped`() {
        val dropped = CopyOnWriteArrayList<Throwable>()
        // Маркер нашей ошибки: глобальный хук реактора срабатывает на ошибки всех
        // тестов в этом JVM, и ошибка чужого теста не должна считаться нашей.
        val marker = "storage-timeout-interrupt-probe"
        Hooks.onErrorDropped { t -> dropped.add(t) }
        val wasInterrupted = AtomicBoolean(false)
        val interruptLatch = CountDownLatch(1)
        try {
            val cb =
                StorageCircuitBreaker(
                    // Таймаут больше прежнего окна ожидания (500 мс): прерывание
                    // приходит позже, и «опоздавшая» ошибка видна только при
                    // ожидании завершения прерванной задачи.
                    timeoutSeconds = 2L,
                    threshold = 5,
                    cooldownSeconds = 30L,
                    watchdogEnabled = false,
                    watchdogBufferSeconds = 10L,
                    checkIntervalSeconds = 1L,
                )
            // Повторяет реальный путь getFileInfo -> statObjectOrNull -> runBlockingMinioOrNull.
            val blockingLoader = {
                Mono.fromCallable {
                    runBlockingMinioOrNull<String> {
                        try {
                            Thread.sleep(30_000)
                            "late"
                        } catch (e: InterruptedException) {
                            // Так ведёт себя MinIO: заворачивает interrupt в RuntimeException.
                            wasInterrupted.set(true)
                            throw RuntimeException(InterruptedException("$marker ${e.message}"))
                        } finally {
                            // Задача завершилась (успехом или прерыванием) — ожидание
                            // может продолжаться.
                            interruptLatch.countDown()
                        }
                    } ?: "empty"
                }
            }
            val result =
                cb.decorate("getFileInfo", blockingLoader, "fallback").block()
            assertEquals("fallback", result)
            // Даём реактору/воркеру время дойти до завершения прерванной задачи.
            assertTrue(
                interruptLatch.await(15, TimeUnit.SECONDS),
                "прерванная задача должна завершиться после отмены по таймауту",
            )
            assertTrue(
                wasInterrupted.get(),
                "блокирующий вызов должен быть прерван по таймауту",
            )
            assertTrue(
                dropped.none { it.containsMarker(marker) },
                "onErrorDropped must not fire on timeout-interrupt, dropped=$dropped",
            )
        } finally {
            Hooks.resetOnErrorDropped()
        }
    }
}

/** Есть ли маркер в сообщении исключения или в цепочке его причин. */
private fun Throwable.containsMarker(marker: String): Boolean {
    var t: Throwable? = this
    while (t != null) {
        if (t.message?.contains(marker) == true) return true
        t = t.cause
    }
    return false
}
