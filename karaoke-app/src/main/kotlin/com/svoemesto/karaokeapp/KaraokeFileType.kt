package com.svoemesto.karaokeapp

enum class KaraokeFileType(
    val description: String,
    val forAllVersions: Boolean,
    val extention: String,
    val prefix: String,
    val suffix: String
) {
    AUDIO_SONG (
        description = "Исходный аудио файл",
        forAllVersions = true,
        extention = "flac",
        prefix = "",
        suffix = ".song"
    ),
    AUDIO_ACCOMPANIMENT (
        description = "Минусовка",
        forAllVersions = true,
        extention = "flac",
        prefix = "",
        suffix = ".accompaniment"
    ),
    AUDIO_VOICE (
        description = "Чистый голос",
        forAllVersions = true,
        extention = "flac",
        prefix = "",
        suffix = ".vocals"
    ),
    AUDIO_BASS (
        description = "Бас",
        forAllVersions = true,
        extention = "flac",
        prefix = "",
        suffix = ".bass"
    ),
    AUDIO_DRUMS (
        description = "Ударные",
        forAllVersions = true,
        extention = "flac",
        prefix = "",
        suffix = ".drums"
    ),
    AUDIO_OTHER (
        description = "Мелодия без баса и ударных",
        forAllVersions = true,
        extention = "flac",
        prefix = "",
        suffix = ".other"
    ),
    PICTURE_ALBUM (
        description = "Картинка альбома",
        forAllVersions = true,
        extention = "png",
        prefix = "",
        suffix = ".album"
    ),
    PICTURE_AUTHOR (
        description = "Картинка автора",
        forAllVersions = true,
        extention = "png",
        prefix = "",
        suffix = ".author"
    ),
    PICTURE_PUBLICATION (
        description = "Картинка публикации",
        forAllVersions = true,
        extention = "png",
        prefix = "",
        suffix = ".publish"
    ),
    PICTURE_SONGVERSION (
        description = "Картинка для видео конкретной версии песни",
        forAllVersions = false,
        extention = "png",
        prefix = "",
        suffix = ".preview"
    ),
}