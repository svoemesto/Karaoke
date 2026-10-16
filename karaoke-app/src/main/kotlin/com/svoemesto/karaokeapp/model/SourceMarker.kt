package com.svoemesto.karaokeapp.model

@kotlinx.serialization.Serializable
/**
 * Класс Source Marker.
 *
 * @see archive/docs/features/mlt-generator.md
 */
data class SourceMarker(
    var time: Double,
    var label: String = "",
    var note: String = "",
    var chord: String = "",
    var stringLad: String = "",
    var lockLad: String = "",
    var tag: String = "",
    var color: String,
    var position: String,
//    @JsonDeserialize(using = MarkertypeDeserializer::class)
    var markertype: String,
)

/**
 * Есть ли у песни аккорды — по маркерам разметки из БД (specs/544-song-artifact-cleanup, FR-007).
 *
 * Признак считается по данным разметки, а не по содержимому файлов субтитров: замер
 * 2026-10-16 показал, что ни один из 20 288 живых `.srt` не содержит маркера `[E]`, при
 * котором прежняя ветка наполняла `voices`, поэтому признаки аккордов у современных песен
 * всегда были ложными (research R4).
 *
 * Вынесено из `Song.hasChords` отдельной функцией, чтобы правило было проверяемо
 * unit-тестом без экземпляра `Song` (его конструктор требует БД и в тесте недоступен).
 *
 * @see specs/544-song-artifact-cleanup/spec.md
 */
fun hasChordsInMarkers(markers: List<SourceMarker>): Boolean =
    markers.any {
        it.markertype in
            listOf(Markertype.SYLLABLES.value, Markertype.CHORD.value) &&
            it.chord.isNotBlank()
    }
