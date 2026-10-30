package com.svoemesto.karaokeapp.monitor

import com.svoemesto.karaokeapp.monitor.checks.HeapSample
import com.svoemesto.karaokeapp.monitor.checks.HeapThresholds
import com.svoemesto.karaokeapp.monitor.checks.HeapUsageCheck
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

/**
 * Тесты 8-й проверки мониторинга — загрузка heap JVM (спека 676, FR-014…FR-019).
 *
 * Проверяется чистая логика (пороги → severity, стабильность ключа алерта,
 * переход «алерт → норма», разбор конфигурации) — без БД, сети и файлов.
 *
 * @see com.svoemesto.karaokeapp.monitor.checks.HeapUsageCheck
 */
class HeapUsageCheckTest {
    /** Контекст проверки не используется (проверка читает только `Runtime`) — но контракт требует его. */
    private val ctx: MonitorContext =
        MonitorContext(
            localDb = org.mockito.Mockito.mock(com.svoemesto.karaokeapp.KaraokeConnection::class.java),
            storageService = org.mockito.Mockito.mock(com.svoemesto.karaokeapp.services.KaraokeStorageService::class.java),
            storageApiClient = org.mockito.Mockito.mock(com.svoemesto.karaokeapp.services.StorageApiClient::class.java),
        )

    @Test
    fun `FR-015 ниже порога алерта нет`() {
        val alerts = HeapUsageCheck.run(ctx, sampleOf(50.0))

        assertTrue(alerts.isEmpty(), "загрузка ниже порога — алерта нет")
    }

    @Test
    fun `FR-015 между порогами — WARNING`() {
        val alerts = HeapUsageCheck.run(ctx, sampleOf(80.0))

        assertEquals(1, alerts.size)
        assertEquals(MonitorSeverity.WARNING, alerts.first().severity)
        assertEquals(HeapUsageCheck.ALERT_KEY, alerts.first().key)
    }

    @Test
    fun `FR-015 выше критического порога — CRITICAL`() {
        val alerts = HeapUsageCheck.run(ctx, sampleOf(95.0))

        assertEquals(1, alerts.size)
        assertEquals(MonitorSeverity.CRITICAL, alerts.first().severity)
    }

    @Test
    fun `FR-014 ключ алерта стабилен между прогонами`() {
        val first = HeapUsageCheck.run(ctx, sampleOf(80.0)).first().key
        val second = HeapUsageCheck.run(ctx, sampleOf(85.0)).first().key

        assertEquals(first, second, "ключ алерта не должен меняться — по нему снапшот мониторинга связывает состояние")
    }

    @Test
    fun `FR-017 тело алерта стабильно — изменяющиеся числа живут в detail`() {
        val first = HeapUsageCheck.run(ctx, sampleOf(80.0)).first()
        val second = HeapUsageCheck.run(ctx, sampleOf(85.0)).first()

        assertEquals(first.body, second.body, "body входит в contentHash() — он обязан быть постоянным")
        assertTrue(first.contentHash() == second.contentHash(), "contentHash() не должен меняться из-за загрузки")
        assertTrue(first.detail!!.contains("percent="), "изменяющаяся загрузка — в detail")
    }

    @Test
    fun `FR-017 возвращение в норму снимает алерт, повторное превышение — снова алерт`() {
        HeapUsageCheck.run(ctx, sampleOf(80.0))
        assertEquals(1, HeapUsageCheck.run(ctx, sampleOf(80.0)).size, "превышение держит алерт")

        val recovered = HeapUsageCheck.run(ctx, sampleOf(10.0))
        assertTrue(recovered.isEmpty(), "после возврата в норму алерта нет")

        assertEquals(1, HeapUsageCheck.run(ctx, sampleOf(80.0)).size, "повторное превышение — снова алерт")
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun `FR-016 проверка укладывается в бюджет тика и не имеет побочных эффектов`() {
        val startedAt = System.nanoTime()

        HeapUsageCheck.run(ctx, sampleOf(80.0))

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        assertTrue(elapsedMs < 1000, "проверка заняла $elapsedMs мс — бюджет тика (< 5 с) исчерпан")
    }

    @Test
    fun `FR-015 пороги по умолчанию — 75 и 90 процентов`() {
        val thresholds = HeapThresholds.fromEnv(emptyMap())

        assertEquals(75, thresholds.warnPercent)
        assertEquals(90, thresholds.criticalPercent)
    }

    @Test
    fun `FR-015 пороги читаются из конфигурации`() {
        val thresholds = HeapThresholds.fromEnv(mapOf("MONITOR_HEAP_WARN_PERCENT" to "60", "MONITOR_HEAP_CRITICAL_PERCENT" to "80"))

        assertEquals(60, thresholds.warnPercent)
        assertEquals(80, thresholds.criticalPercent)
    }

    @Test
    fun `FR-015 некорректная конфигурация не ломает проверку`() {
        val broken = HeapThresholds.fromEnv(mapOf("MONITOR_HEAP_WARN_PERCENT" to "не число", "MONITOR_HEAP_CRITICAL_PERCENT" to "500"))

        assertEquals(75, broken.warnPercent, "нечисловое значение → дефолт")
        assertEquals(90, broken.criticalPercent, "значение вне диапазона → дефолт")
    }

    @Test
    fun `FR-015 несогласованная пара порогов возвращает дефолты`() {
        val broken = HeapThresholds.fromEnv(mapOf("MONITOR_HEAP_WARN_PERCENT" to "95", "MONITOR_HEAP_CRITICAL_PERCENT" to "80"))

        assertEquals(75, broken.warnPercent)
        assertEquals(90, broken.criticalPercent)
    }

    @Test
    fun `FR-016 нулевой потолок heap не даёт деления на ноль`() {
        val sample = HeapSample(usedBytes = 100L, maxBytes = 0L)

        assertEquals(0.0, sample.usedPercent)
        assertTrue(HeapUsageCheck.run(ctx, sample).isEmpty())
    }

    /** Снимок с заданной загрузкой в процентах (потолок 100 «условных» единиц). */
    private fun sampleOf(percent: Double): HeapSample =
        HeapSample(usedBytes = (percent * 1000).toLong(), maxBytes = 100_000L)
}
