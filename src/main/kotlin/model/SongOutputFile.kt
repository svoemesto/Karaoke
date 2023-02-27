package model

enum class SongOutputFile(val extension: String) {
    PROJECT(extension = "kdenlive"),
    VIDEO(extension = "mp4"),
    PICTURE(extension = "png"),
    PICTURECHORDS(extension = "png"),
    PICTUREBOOSTY(extension = "png"),
    SUBTITLE(extension = "kdenlive.srt"),
    DESCRIPTION(extension = "txt"),
    TEXT(extension = "txt"),
}