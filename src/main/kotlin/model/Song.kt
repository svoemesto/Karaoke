package model

class Song() {
    var settings: Settings = Settings()
    var subtitles: List<Subtitle> = emptyList()
    var endTimecode: String? = null
    var beatTimecode: String? = null
    var srtFileBody: String = ""
}
