package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Регрессия BR-110 (аудит #336, PKG-S2-11): кэш огибающих не растёт безгранично.
 *
 * До правки `envelopeCache` был `ConcurrentHashMap` без вытеснения — запись на
 * каждый новый файл/версию стема оставалась в памяти до OOM. Тест кладёт 200
 * записей напрямую в приватный кэш (reflection — чтобы не запускать ffmpeg) и
 * требует, чтобы размер не превышал `ENVELOPE_CACHE_MAX_ENTRIES`.
 *
 * На старом коде тест падает: 200 > 64.
 */
class WaveformCompareCacheTest {
    @Suppress("UNCHECKED_CAST")
    private fun cache(): MutableMap<String, FloatArray> {
        val field = WaveformCompare::class.java.getDeclaredField("envelopeCache")
        field.isAccessible = true
        return field.get(WaveformCompare) as MutableMap<String, FloatArray>
    }

    @Test
    fun `cache evicts oldest entries and stays bounded`() {
        val cache = cache()
        repeat(200) { i ->
            cache["/tmp/song-$i.flac|$i|100"] = FloatArray(1)
        }
        assertTrue(
            cache.size <= WaveformCompare.ENVELOPE_CACHE_MAX_ENTRIES,
            "кэш огибающих вырос до ${cache.size} записей при лимите " +
                "${WaveformCompare.ENVELOPE_CACHE_MAX_ENTRIES} (BR-110)",
        )
    }
}
