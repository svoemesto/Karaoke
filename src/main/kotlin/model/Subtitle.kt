package model

data class Subtitle(
    val startTimecode: String? = null,
    var endTimecode: String? = null,
    val text: String? = null,
    val isLineStart: Boolean? = null,
    val isLineEnd: Boolean? = null,
    var isBeat: Boolean = false
)