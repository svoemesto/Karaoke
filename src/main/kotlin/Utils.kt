fun main() {
    val ms = 41100L
    val fps = 50L
    val crop = 1000.0 / fps
    val frames = convertMillisecondsToFrames(ms, fps)
    println(crop)
    println(Math.round(ms / crop))
    println(frames)
    println(convertFramesToMilliseconds(frames, fps))

    println(convertTimecodeToMilliseconds("00:00:41.100"))
    println(convertMillisecondsToTimecode(ms))
}

fun convertMillisecondsToFrames(milliseconds: Long, fps:Long = FRAME_FPS): Long {
    val frameLength = 1000.0 / fps
    return Math.round(milliseconds / frameLength)
}

fun convertMillisecondsToFramesDouble(milliseconds: Long, fps:Long = FRAME_FPS): Double {
    val frameLength = 1000.0 / fps
    return milliseconds / frameLength
}

fun convertFramesToMilliseconds(frames: Long, fps:Long = FRAME_FPS): Long {
    val frameLength = 1000.0 / fps
    return Math.round(frames * frameLength)
}

fun convertFramesToTimecode(frames: Long, fps:Long = FRAME_FPS): String {
    return convertMillisecondsToTimecode(convertFramesToMilliseconds(frames,fps))
}
fun convertTimecodeToFrames(timecode: String, fps:Long = FRAME_FPS): Long {
    return convertMillisecondsToFrames(convertTimecodeToMilliseconds(timecode), fps)
}
fun convertTimecodeToMilliseconds(timecode: String): Long {
    val hhmmssmm = timecode.split(":")
    val hours = hhmmssmm[0].toLong()
    val minutes = hhmmssmm[1].toLong()
    val ssmm = hhmmssmm[2].replace(",",".").split(".")
    val seconds = ssmm[0].toLong()
    val milliseconds = ssmm[1].toLong()
    val result = milliseconds + seconds*1000 + minutes*1000*60 + hours*1000*60*60
    return result
}

fun convertMillisecondsToTimecode(milliseconds: Long, fps:Long = 60): String {
//    val frames = convertMillisecondsToFrames(milliseconds, fps)
//    val croppedMs = convertFramesToMilliseconds(frames, fps)
    val hours = milliseconds / (1000*60*60)
    val minutes = (milliseconds - hours*1000*60*60) / (1000*60)
    val seconds = (milliseconds - hours*1000*60*60 - minutes*1000*60) / 1000
    val ms = milliseconds - hours*1000*60*60 - minutes*1000*60 - seconds*1000
    return "%02d:%02d:%02d.%03d".format(hours,minutes,seconds,ms)
}

fun getTactByMilliseconds(timeInMilliseconds: Long, bpm: Long = 90, firstTactTimecode: String = "00:00:00.00"): Long {
    val taktMs = (60000.0 / bpm)
    return  0L

}

fun getDurationInMilliseconds(start: String, end: String): Long {
    return convertTimecodeToMilliseconds(end) - convertTimecodeToMilliseconds(start)
}

fun getDiffInMilliseconds(firstTimecode: String, secondTimecode: String): Long {
    return convertTimecodeToMilliseconds(firstTimecode) - convertTimecodeToMilliseconds(secondTimecode)
}

fun getSymbolWidth(fontSizePt: Int): Double {
    // Получение ширины символа (в пикселях) для размера шрифта (в пунктах)
    return fontSizePt*0.6
}

fun getSymbolHeight(fontSizePt: Int): Int {
    // Получение высоты символа (в пикселях) для размера шрифта (в пунктах)
    return if (fontSizePt in 8 until POINT_TO_PIXEL.size) POINT_TO_PIXEL[fontSizePt] else 0
}

fun getFontSizeBySymbolWidth(symbolWidthPx: Double): Int {
    // Получение размера шрифта (в пунктах) для ширины символа (в пикселах)
    return (symbolWidthPx/0.6).toInt()
}

fun getFontSizeBySymbolHeight(symbolHeightPx: Int): Int {
    // Получение размера шрифта (в пунктах) для высоты символа (в пикселах)
    return POINT_TO_PIXEL.firstOrNull { it in symbolHeightPx..symbolHeightPx } ?:0
}

fun getPixels(pt: Double): Double {
    return pt * 96 / 72
}

fun getPoints(pixel: Double): Double {
    return pixel * 72 / 96
}