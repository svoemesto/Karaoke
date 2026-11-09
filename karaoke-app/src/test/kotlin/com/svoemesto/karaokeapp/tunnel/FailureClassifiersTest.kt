package com.svoemesto.karaokeapp.tunnel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.SQLException

/**
 * Спека 733 (FR-016, FR-028, FR-033): различение причин отказа.
 *
 * Отказ по сертификату должен быть опознан (с причиной), канальный отказ —
 * отличаться от логического (задание возвращается в очередь, а не падает).
 */
class FailureClassifiersTest {
    @Test
    fun `просроченный сертификат опознаётся`() {
        val error = SQLException("FATAL: certificate has expired")
        assertEquals(CertFailureReason.EXPIRED, CertFailureClassifier.classify(error))
    }

    @Test
    fun `отозванный сертификат опознаётся`() {
        val error = SQLException("FATAL: certificate revoked by the issuer")
        assertEquals(CertFailureReason.REVOKED, CertFailureClassifier.classify(error))
    }

    @Test
    fun `недоверенный сертификат опознаётся`() {
        val error = SQLException("FATAL: certificate verify failed")
        assertEquals(CertFailureReason.UNTRUSTED, CertFailureClassifier.classify(error))
    }

    @Test
    fun `ошибка в цепочке причин тоже опознаётся`() {
        val error =
            RuntimeException(
                "connect failed",
                SQLException("FATAL: SSL error: certificate has expired"),
            )
        assertEquals(CertFailureReason.EXPIRED, CertFailureClassifier.classify(error))
        assertTrue(CertFailureClassifier.isCertificateFailure(error))
    }

    @Test
    fun `обычная ошибка соединения не считается отказом по сертификату`() {
        val error = SQLException("Connection refused")
        assertEquals(CertFailureReason.UNKNOWN, CertFailureClassifier.classify(error))
        assertFalse(CertFailureClassifier.isCertificateFailure(error))
    }

    @Test
    fun `обрыв сети опознаётся как канальный отказ`() {
        val error = SQLException("Connection to 10.77.0.2:5433 refused. The connection attempt failed.")
        assertEquals(FailureKind.CHANNEL, ChannelFailureClassifier.classify(error))
        assertTrue(ChannelFailureClassifier.isChannelFailure(error))
    }

    @Test
    fun `таймаут опознаётся как канальный отказ`() {
        val error = java.net.SocketTimeoutException("Read timed out")
        assertEquals(FailureKind.CHANNEL, ChannelFailureClassifier.classify(error))
    }

    @Test
    fun `отказ по сертификату — тоже канальный (сертификат можно перевыпустить)`() {
        val error = SQLException("FATAL: certificate has expired")
        assertEquals(FailureKind.CERTIFICATE, ChannelFailureClassifier.classify(error))
        assertTrue(ChannelFailureClassifier.isChannelFailure(error))
    }

    @Test
    fun `логическая ошибка не является канальной`() {
        val error = IllegalStateException("Данные не найдены")
        assertEquals(FailureKind.LOGICAL, ChannelFailureClassifier.classify(error))
        assertFalse(ChannelFailureClassifier.isChannelFailure(error))
    }

    @Test
    fun `неизвестная ошибка не является канальной`() {
        val error = IllegalArgumentException("что-то странное")
        assertEquals(FailureKind.UNKNOWN, ChannelFailureClassifier.classify(error))
        assertFalse(ChannelFailureClassifier.isChannelFailure(error))
    }
}
