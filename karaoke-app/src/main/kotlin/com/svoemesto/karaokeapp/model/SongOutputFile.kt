package com.svoemesto.karaokeapp.model

import java.io.Serializable

enum class SongOutputFile(val extension: String) : Serializable {
    PROJECT(extension = "kdenlive"),
    VIDEO(extension = "mp4"),
    PICTURE(extension = "png"),
    PICTURECHORDS(extension = "png"),
    PICTUREBOOSTY(extension = "png"),
    PICTUREVK(extension = "png"),
    SUBTITLE(extension = "kdenlive.srt"),
    DESCRIPTION(extension = "txt"),
    VK(extension = "txt"),
    TEXT(extension = "txt"),
    RUN(extension = "run"),
    RUNALL(extension = "run"),
    MLT(extension = "mlt"),
}