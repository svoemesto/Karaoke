package com.svoemesto.karaokeapp.model

@kotlinx.serialization.Serializable
data class SourceMarker(
    var time: Double,
    var label: String = "",
    var note: String = "",
    var chord: String = "",
    var stringlad: String = "",
    var locklad: String = "",
    var tag: String = "",
    var color: String,
    var position: String,
//    @JsonDeserialize(using = MarkertypeDeserializer::class)
    var markertype: String
) {

}