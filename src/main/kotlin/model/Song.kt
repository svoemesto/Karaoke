package model

class Song() {
    var settings: Settings = Settings()
    var subtitles: MutableMap<Long, List<Subtitle>> = mutableMapOf(Pair(0,emptyList()))
    var endTimecode: String = ""
    var beatTimecode: String = ""
    var srtFileBody: MutableMap<Long, String> = mutableMapOf(Pair(0,""))
    var maxLengthLine: MutableMap<Long, Long> = mutableMapOf(Pair(0,0))
}
