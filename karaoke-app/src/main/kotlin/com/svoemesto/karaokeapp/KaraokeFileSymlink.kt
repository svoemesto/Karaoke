package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.SongVersion

data class KaraokeFileSymlink(
    val folder: String,     // имя папки внутри root_folder-а
    val name: String = "",   // имя файла ссылки. Если пустое - имя совпадает с файлом, на который указывает ссылка.
    val platforms: List<KaraokePlatform> = emptyList(), // Платформы, для которых актуально. Пустой список - для всех.
    val songVersions: List<SongVersion> = emptyList(), // Версии, для которых актуально. Пустой список - для всех.
)