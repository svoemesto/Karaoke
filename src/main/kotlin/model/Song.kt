package model

class Song() {
    var settings: Settings = Settings()
    var subtitles: List<Subtitle> = emptyList()
    var end: String? = null
    var beat: String? = null
    var body: String = ""
}
