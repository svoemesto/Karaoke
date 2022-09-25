package model

data class LyricLine(
    val text: String = "",
    var start: String = "",
    var end: String = "",
    val subtitles: List<Subtitle> = emptyList(),
    var startTp: TransformProperty? = null,
    var endTp: TransformProperty? = null,
    var isEmptyLine: Boolean = false,
    var isNeedCounter: Boolean = false,
    var isFadeLine: Boolean = false
)
