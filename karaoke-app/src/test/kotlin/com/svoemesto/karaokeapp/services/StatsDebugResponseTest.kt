package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.model.StatsDebugDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Регрессия BR-143 (аудит #336, PKG-S2-11): DTO отдаётся без приведения к Map.
 *
 * До правки endpoint делал `dto as Map<String, Any>`; `StatsDebugDto` — обычный
 * data class, поэтому каждый вызов падал с `ClassCastException` и endpoint
 * стабильно отдавал 500 (именно тогда, когда нужен для диагностики).
 */
class StatsDebugResponseTest {
    @Test
    fun `debug response body is the dto itself`() {
        val dto =
            StatsDebugDto(
                cacheSize = 0,
                cacheKeys = emptyList(),
                pgActiveConnections = 1,
                pgMaxConnections = 100,
                timestamp = "2026-10-24T00:00:00Z",
            )

        val response = StatsDebugController().debugResponse(dto)
        assertEquals(200, response.statusCode.value())
        assertSame(dto, response.body)
        assertTrue(response.body is StatsDebugDto)
    }
}
