package com.svoemesto.karaokeapp.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Контракт сериализации [SourceMarker.lockLad] (тикет #201 карты wayfinder #186).
 *
 * ИСТОРИЯ. Фронты долгое время отправляли ключ `locklad` — с прописной «л» в начале
 * только у первого слова: `locklad` вместо `lockLad`. Оба контроллера, принимающих
 * маркеры, используют `Json { ignoreUnknownKeys = true }`, поэтому значение МОЛЧА
 * выбрасывалось: без ошибки, без лога, без исключения.
 *
 * Поле функциональное: [Song.getTextFormatted] и родственные методы проверяют
 * `marker.lockLad.toBoolean()`, чтобы «прибить ноту к конкретной струне и ладу»
 * вместо автоназначения из последовательности нот. Пока имя ключа расходилось,
 * фича не работала ни разу за всё время существования проекта, при том что
 * кнопка в админке работала и подсвечивалась.
 *
 * Тест фиксирует канон, чтобы регрессия не вернулась молча.
 */
class SourceMarkerLockLadTest {
    private val lenient = Json { ignoreUnknownKeys = true }

    private val payload =
        """
        {
          "time": 1.5,
          "label": "сло_во",
          "note": "A|440",
          "chord": "Am",
          "stringLad": "3|5",
          "lockLad": "true",
          "color": "#ff0000",
          "position": "bottom",
          "markertype": "syllables"
        }
        """.trimIndent()

    @Test
    @DisplayName("Ключ lockLad десериализуется и значение сохраняется")
    fun lockLadIsDeserialized() {
        val marker = lenient.decodeFromString<SourceMarker>(payload)
        assertEquals("true", marker.lockLad, "lockLad должен читаться из payload")
        assertEquals("3|5", marker.stringLad)
        assertEquals("A|440", marker.note)
    }

    @Test
    @DisplayName("lockLad=true активирует ветку «прибить ноту к струне/ладу»")
    fun lockLadDrivesTheFeatureBranch() {
        val marker = lenient.decodeFromString<SourceMarker>(payload)
        // Ровно та проверка, которую делает Song.kt: if (marker.lockLad.toBoolean()).
        assertTrue(marker.lockLad.toBoolean(), "фича должна включаться по lockLad=true")
    }

    @Test
    @DisplayName("Опечатка locklad по-прежнему молча теряется — это задокументированное поведение ignoreUnknownKeys")
    fun typoKeyIsSilentlyDropped() {
        val withTypo = payload.replace("\"lockLad\"", "\"locklad\"")
        val marker = lenient.decodeFromString<SourceMarker>(withTypo)
        assertEquals(
            "",
            marker.lockLad,
            "locklad не совпадает с lockLad и отбрасывается ignoreUnknownKeys — " +
                "именно так ошибка жила незамеченной. Каноническое имя — lockLad.",
        )
    }

    @Test
    @DisplayName("Сериализация обратно пишет lockLad, а не locklad")
    fun serializationUsesCanonicalName() {
        val marker = lenient.decodeFromString<SourceMarker>(payload)
        val json = lenient.encodeToString(SourceMarker.serializer(), marker)
        assertTrue(json.contains("\"lockLad\""), "сериализация должна писать lockLad")
        assertTrue(!json.contains("\"locklad\""), "опечатка locklad не должна появляться в выводе")
    }
}
