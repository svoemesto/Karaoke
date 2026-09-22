package com.svoemesto.karaokeapp.services

import io.minio.errors.MinioException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import java.util.concurrent.CopyOnWriteArrayList
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
     */
    @Test
    fun `decorate timeout does not produce onErrorDropped`() {
        val dropped = CopyOnWriteArrayList<Throwable>()
        val sawDropped = AtomicBoolean(false)
        Hooks.onErrorDropped { t ->
            sawDropped.set(true)
            dropped.add(t)
        }
        try {
            val cb =
                StorageCircuitBreaker(
                    timeoutSeconds = 1L,
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
                            Thread.sleep(5_000)
                            "late"
                        } catch (e: InterruptedException) {
                            // Так ведёт себя MinIO: заворачивает interrupt в RuntimeException.
                            throw RuntimeException(e)
                        }
                    } ?: "empty"
                }
            }
            val result =
                cb.decorate("getFileInfo", blockingLoader, "fallback").block()
            assertEquals("fallback", result)
            // Даём реактору/воркеру время дойти до завершения прерванной задачи.
            Thread.sleep(500)
            assertFalse(
                sawDropped.get(),
                "onErrorDropped must not fire on timeout-interrupt, dropped=$dropped",
            )
        } finally {
            Hooks.resetOnErrorDropped()
        }
    }
}
