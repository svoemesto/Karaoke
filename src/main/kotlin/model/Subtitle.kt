package model

data class Subtitle(
    val startTimecode: String = "",
    var endTimecode: String = "",
    val text: String = "",
    val isLineStart: Boolean = false,
    val isLineEnd: Boolean = false,
    var isBeat: Boolean = false,
    var group: Long = 0L,
    var indexFirstSymbolInLine: Int = 0
)