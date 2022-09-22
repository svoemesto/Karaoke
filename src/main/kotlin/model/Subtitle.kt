package model

data class Subtitle(
    val id: Long? = null,
    val start: String? = null,
    var end: String? = null,
    val text: String? = null,
    val isLineStart: Boolean? = null,
    val isLineEnd: Boolean? = null,
    val isBeat: Boolean = false
)