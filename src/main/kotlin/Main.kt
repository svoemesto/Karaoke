fun main(args: Array<String>) {

    Liric.createLiric(Subtitles.getSubtitles())

    println(convertTimecodeToMilliseconds("00:00:39,133"))
    println(convertMillisecondsToTimecode(39133))
    println(getDurationInMilliseconds("00:00:39,833", "00:00:40,116"))
    println(convertMillisecondsToTimecode(getDurationInMilliseconds("00:00:39,833", "00:00:40,116")))
}