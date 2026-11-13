package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Формат объёма освобождённого места — человекочитаемые единицы (KARAOKE-429).
 *
 * **Пожелание владельца на живой приёмке 2026-11-13:** в уведомлении об уборке
 * песни было «освобождено: 95234560 байт» — это неинформативно. Песня занимает
 * десятки-сотни мегабайт, альбом — гигабайты, поэтому объём показывается в МБ/ГБ.
 *
 * Тест закрывает два случая:
 * - формат чисел (0, доли мегабайта, мегабайты, гигабайты);
 * - в уведомлении не осталось сырых байт (защита от возврата «N байт»).
 */
class SongCleanupBytesFormatTest {
    private val executorSource =
        File("src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongCleanupExecutor.kt")

    @Test
    fun `ноль и отрицательное значение дают 0 МБ`() {
        assertEquals("0 МБ", SongCleanupExecutor.formatBytes(0L))
        assertEquals("0 МБ", SongCleanupExecutor.formatBytes(-1L))
    }

    @Test
    fun `малый объём показывается в мегабайтах`() {
        assertEquals("0.5 МБ", SongCleanupExecutor.formatBytes(512L * 1024L))
        assertEquals("11.8 МБ", SongCleanupExecutor.formatBytes(12_345_678L))
    }

    @Test
    fun `большой объём показывается в гигабайтах`() {
        assertEquals("1.00 ГБ", SongCleanupExecutor.formatBytes(1024L * 1024L * 1024L))
        assertEquals("1.02 ГБ", SongCleanupExecutor.formatBytes(1_100_000_000L))
    }

    @Test
    fun `в уведомлении об уборке объём в человекочитаемых единицах`() {
        assertTrue(executorSource.isFile, "исходник уборки не найден: ${executorSource.path}")
        val text = executorSource.readText()
        assertTrue(
            text.contains("formatBytes(counters.bytesDeleted)"),
            "уведомление должно печатать объём через formatBytes — сырые байты неинформативны",
        )
        assertFalse(
            text.contains("байт.\""),
            "в уведомлении не должно оставаться «байт» — объём показывается в МБ/ГБ",
        )
    }
}
