package com.svoemesto.karaokeapp.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Юнит-тесты нормализации голосов песни (specs/547-drop-empty-song-voices, T006).
 *
 * Проверяют чистые функции [normalizeVoices], [parseVoiceTexts], [parseVoiceMarkers],
 * [canNormalize], [isVoiceEmpty], [nonEmptyVoiceIndices] — без БД и без экземпляра `Song`
 * (его конструктор требует окружения).
 *
 * Контракт: specs/547-drop-empty-song-voices/contracts/voice-normalization.md §4.
 *
 * @see specs/547-drop-empty-song-voices/data-model.md
 * @see docs/features/drop-empty-song-voices.md
 */
class VoiceNormalizationTest {
    private fun marker(label: String = "Привет"): SourceMarker =
        SourceMarker(
            time = 1.0,
            label = label,
            color = "#FFFFFF",
            position = "0;0",
            markertype = Markertype.SYLLABLES.value,
        )

    private fun emptyVoice(): List<SourceMarker> = emptyList()

    private fun voice(): List<SourceMarker> = listOf(marker())

    // --- базовые кейсы контракта -------------------------------------------------------------

    @Test
    fun `один непустой голос не меняется`() {
        val result = normalizeVoices(listOf("A"), listOf(voice()))
        assertFalse(result.changed)
        assertEquals(listOf("A"), result.texts)
        assertEquals(1, result.markers.size)
        assertTrue(result.removedIndices.isEmpty())
    }

    @Test
    fun `единственный пустой голос не удаляется`() {
        val result = normalizeVoices(listOf(""), listOf(emptyVoice()))
        assertFalse(result.changed)
        assertEquals(1, result.count) // первый голос сохраняется всегда
        assertEquals(0, result.mapVoice(0))
    }

    @Test
    fun `пустой второй голос удаляется`() {
        val result = normalizeVoices(listOf("A", ""), listOf(voice(), emptyVoice()))
        assertTrue(result.changed)
        assertEquals(listOf("A"), result.texts)
        assertEquals(1, result.markers.size)
        assertEquals(listOf(1), result.removedIndices)
        assertEquals(0, result.mapVoice(0))
        assertNull(result.mapVoice(1))
    }

    @Test
    fun `пустой средний голос удаляется со сдвигом следующего`() {
        val result = normalizeVoices(listOf("A", "", "B"), listOf(voice(), emptyVoice(), listOf(marker("B"))))
        assertTrue(result.changed)
        assertEquals(listOf("A", "B"), result.texts)
        assertEquals(2, result.count)
        assertEquals(listOf(1), result.removedIndices)
        assertEquals(1, result.mapVoice(2)) // третий стал вторым
        assertEquals(0, result.mapVoice(0))
    }

    @Test
    fun `текст из пробелов считается пустым`() {
        val result = normalizeVoices(listOf("A", "   \n\t"), listOf(voice(), emptyVoice()))
        assertTrue(result.changed)
        assertEquals(listOf("A"), result.texts)
    }

    @Test
    fun `голос с текстом но без маркеров не удаляется`() {
        val result = normalizeVoices(listOf("A", "text"), listOf(voice(), emptyVoice()))
        assertFalse(result.changed)
        assertEquals(2, result.count)
    }

    @Test
    fun `голос с маркером но без текста не удаляется`() {
        val result = normalizeVoices(listOf("A", ""), listOf(voice(), voice()))
        assertFalse(result.changed)
        assertEquals(2, result.count)
    }

    @Test
    fun `удаляются все пустые кроме первого`() {
        val result =
            normalizeVoices(
                listOf("A", "", "B", ""),
                listOf(voice(), emptyVoice(), listOf(marker("B")), emptyVoice()),
            )
        assertTrue(result.changed)
        assertEquals(listOf("A", "B"), result.texts)
        assertEquals(listOf(1, 3), result.removedIndices)
        assertEquals(2, result.count)
    }

    @Test
    fun `текстов меньше чем голосов по маркерам`() {
        val result = normalizeVoices(listOf("A"), listOf(voice(), emptyVoice()))
        assertTrue(result.changed) // второй: текста нет, маркеров нет
        assertEquals(listOf("A"), result.texts)
    }

    @Test
    fun `маркеров больше и второй голос не пуст`() {
        val result = normalizeVoices(listOf("A"), listOf(voice(), voice()))
        assertFalse(result.changed)
        assertEquals(2, result.count)
    }

    @Test
    fun `повторная нормализация идемпотентна`() {
        val first = normalizeVoices(listOf("A", "", "B"), listOf(voice(), emptyVoice(), listOf(marker("B"))))
        val second = normalizeVoices(first.texts, first.markers)
        assertFalse(second.changed)
        assertEquals(first.texts, second.texts)
        assertEquals(first.markers.size, second.markers.size)
    }

    // --- парсеры ------------------------------------------------------------------------------

    @Test
    fun `парсер текстов читает JSON-массив`() {
        assertEquals(listOf("A", ""), parseVoiceTexts("[\"A\",\"\"]"))
    }

    @Test
    fun `парсер текстов на пустом значении даёт пустой список`() {
        assertEquals(emptyList<String>(), parseVoiceTexts(null))
        assertEquals(emptyList<String>(), parseVoiceTexts(""))
    }

    @Test
    fun `парсер текстов на битом JSON даёт null`() {
        assertNull(parseVoiceTexts("[\"A\","))
    }

    @Test
    fun `парсер маркеров читает вложенный массив`() {
        val raw = "[[{\"time\":1.0,\"label\":\"A\",\"color\":\"#FFF\",\"position\":\"0;0\",\"markertype\":\"syllables\"}],[]]"
        val parsed = parseVoiceMarkers(raw)
        assertEquals(2, parsed?.size)
        assertEquals(1, parsed?.get(0)?.size)
        assertEquals(0, parsed?.get(1)?.size)
    }

    @Test
    fun `парсер маркеров понимает легаси-плоский массив`() {
        val raw = "[{\"time\":1.0,\"label\":\"A\",\"color\":\"#FFF\",\"position\":\"0;0\",\"markertype\":\"syllables\"}]"
        val parsed = parseVoiceMarkers(raw)
        assertEquals(1, parsed?.size) // один голос
        assertEquals(1, parsed?.get(0)?.size)
    }

    @Test
    fun `парсер маркеров на битом JSON даёт null`() {
        assertNull(parseVoiceMarkers("[{"))
    }

    // --- гард и индексы рендера ---------------------------------------------------------------

    @Test
    fun `нормализация разрешена только при обоих полях`() {
        assertTrue(canNormalize(hasMarkersField = true, hasTextField = true))
        assertFalse(canNormalize(hasMarkersField = true, hasTextField = false))
        assertFalse(canNormalize(hasMarkersField = false, hasTextField = true))
        assertFalse(canNormalize(hasMarkersField = false, hasTextField = false))
    }

    @Test
    fun `пустота голоса`() {
        assertTrue(isVoiceEmpty("", emptyVoice()))
        assertTrue(isVoiceEmpty("  ", emptyVoice()))
        assertTrue(isVoiceEmpty(null, null))
        assertFalse(isVoiceEmpty("A", emptyVoice()))
        assertFalse(isVoiceEmpty("", voice()))
    }

    @Test
    fun `индексы непустых голосов пропускают пустые`() {
        assertEquals(listOf(0, 2), nonEmptyVoiceIndices(listOf(voice(), emptyVoice(), voice())))
        assertEquals(listOf(0), nonEmptyVoiceIndices(listOf(voice())))
        assertEquals(emptyList<Int>(), nonEmptyVoiceIndices(emptyList()))
    }
}
