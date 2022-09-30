package model

data class Subtitle(
    val startTimecode: String = "",
    var endTimecode: String = "",
    val text: String = "",
    val isLineStart: Boolean = false,
    val isLineEnd: Boolean = false,
    var isBeat: Boolean = false,
    var group: Int = 0,
    var indexFirstSymbolInLine: Int = 0
)