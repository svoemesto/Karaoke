package model

class Song() {
    var settings: Settings = Settings()
    var subtitles: List<Subtitle> = emptyList()
    var endTimecode: String = ""
    var beatTimecode: String = ""
    var srtFileBody: String = ""
}
