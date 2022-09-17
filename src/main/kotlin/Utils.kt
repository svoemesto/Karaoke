fun convertTimecodeToMilliseconds(timecode: String): Long {
    val hhmmssmm = timecode.split(":")
    val hours = hhmmssmm[0].toLong()
    val minutes = hhmmssmm[1].toLong()
    val ssmm = hhmmssmm[2].split(",")
    val seconds = ssmm[0].toLong()
    val milliseconds = ssmm[1].toLong()
    val result = milliseconds + seconds*1000 + minutes*1000*60 + hours*1000*60*60
    return result
}

fun convertMillisecondsToTimecode(milliseconds: Long): String {
    val hours = milliseconds / (1000*60*60)
    val minutes = (milliseconds - hours*1000*60*60) / (1000*60)
    val seconds = (milliseconds - hours*1000*60*60 - minutes*1000*60) / 1000
    val ms = milliseconds - hours*1000*60*60 - minutes*1000*60 - seconds*1000
    return "%02d:%02d:%02d,%03d".format(hours,minutes,seconds,ms)
}

fun getDurationInMilliseconds(start: String, end: String): Long {
    return convertTimecodeToMilliseconds(end) - convertTimecodeToMilliseconds(start)
}

fun getPixels(pt: Double): Double {
    return pt * 96 / 72
}

fun getPoints(pixel: Double): Double {
    return pixel * 72 / 96
}