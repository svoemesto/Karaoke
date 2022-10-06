package model

enum class SongOutputFile(val extension: String) {
    PROJECT(extension = "kdenlive"),
    VIDEO(extension = "mp4"),
    PICTURE(extension = "png"),
    SUBTITLE(extension = "kdenlive.srt"),
    DESCRIPTION(extension = "txt"),
}