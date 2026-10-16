package com.svoemesto.karaokeapp.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Юнит-тесты признака аккордов по маркерам БД (specs/544-song-artifact-cleanup, T023).
 *
 * Покрывает FR-007: признак «у песни есть аккорды» вычисляется по данным разметки,
 * хранящимся в БД, а не чтением файлов субтитров.
 *
 * Проверяется чистая функция [hasChordsInMarkers], которую использует `Song.hasChords`
 * (сам `Song` в юнит-тесте не инстанцируется — конструктор требует БД/окружения).
 *
 * @see specs/544-song-artifact-cleanup/data-model.md
 */
class HasChordsFromDbTest {
    private fun marker(
        markertype: Markertype,
        chord: String = "",
        label: String = "",
    ) = SourceMarker(
        time = 1.0,
        label = label,
        chord = chord,
        color = "#FFFFFF",
        position = "0;0",
        markertype = markertype.value,
    )

    @Test
    fun `аккорд в маркере слога даёт истину`() {
        val markers = listOf(marker(Markertype.SYLLABLES, chord = "Am", label = "Привет"))
        assertTrue(hasChordsInMarkers(markers))
    }

    @Test
    fun `отдельный маркер аккорда даёт истину`() {
        val markers = listOf(marker(Markertype.CHORD, chord = "F#m"))
        assertTrue(hasChordsInMarkers(markers))
    }

    @Test
    fun `маркеры без аккордов дают ложь`() {
        val markers =
            listOf(
                marker(Markertype.SYLLABLES, label = "Привет"),
                marker(Markertype.ENDOFLINE),
                marker(Markertype.SETTING, label = "GROUP|0"),
            )
        assertFalse(hasChordsInMarkers(markers))
    }

    @Test
    fun `пустой или пробельный аккорд не считается аккордом`() {
        assertFalse(hasChordsInMarkers(listOf(marker(Markertype.SYLLABLES, chord = "   "))))
        assertFalse(hasChordsInMarkers(listOf(marker(Markertype.CHORD, chord = ""))))
    }

    @Test
    fun `аккорд вне слогов и аккордовых маркеров не учитывается`() {
        // Логика прежнего Song.hasChords: учитываются только SYLLABLES и CHORD —
        // прочие типы (NOTE/SETTING/ENDOFLINE) с непустым полем chord признак не поднимают
        val markers =
            listOf(
                marker(Markertype.NOTE, chord = "Am"),
                marker(Markertype.SETTING, chord = "Am"),
                marker(Markertype.ENDOFLINE, chord = "Am"),
            )
        assertFalse(hasChordsInMarkers(markers))
    }

    @Test
    fun `пустой список маркеров даёт ложь`() {
        assertFalse(hasChordsInMarkers(emptyList()))
    }

    @Test
    fun `достаточно одного аккорда среди многих маркеров`() {
        val markers =
            listOf(
                marker(Markertype.SYLLABLES, label = "а"),
                marker(Markertype.SYLLABLES, label = "б"),
                marker(Markertype.SYLLABLES, chord = "G", label = "в"),
                marker(Markertype.ENDOFLINE),
            )
        assertTrue(hasChordsInMarkers(markers))
    }

    @Test
    fun `разные голоса объединяются перед проверкой`() {
        // Song.hasChords вызывает предикат на sourceMarkersList.flatten() — аккорд во
        // втором голосе тоже должен поднимать признак песни
        val firstVoice = listOf(marker(Markertype.SYLLABLES, label = "без аккорда"))
        val secondVoice = listOf(marker(Markertype.SYLLABLES, chord = "Dm", label = "с аккордом"))
        assertTrue(hasChordsInMarkers(firstVoice + secondVoice))
    }
}
