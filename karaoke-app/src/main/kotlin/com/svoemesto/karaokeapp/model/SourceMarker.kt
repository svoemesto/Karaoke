package com.svoemesto.karaokeapp.model

@kotlinx.serialization.Serializable
/**
 * Класс Source Marker.
 *
 * @see docs/features/mlt-generator.md
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
