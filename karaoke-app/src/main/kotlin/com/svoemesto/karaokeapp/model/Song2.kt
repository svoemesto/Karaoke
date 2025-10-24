package com.svoemesto.karaokeapp.model

import java.io.Serializable

@Suppress("unused")
data class Song2(
    val settings: Settings,
    val songVersion: SongVersion,
    val woInit: Boolean = false
) : Serializable {

    fun getOutputFilename(songOutputFile: SongOutputFile): String {
        val folderName = when (songOutputFile) {
            SongOutputFile.PROJECT,
            SongOutputFile.SUBTITLE,
            SongOutputFile.MLT,
            SongOutputFile.RUN,
            SongOutputFile.RUNALL,
            SongOutputFile.TEXT -> "done_projects"
            SongOutputFile.PICTURECHORDS -> "done_chords"
            else -> "done_files"
        }

        val fileName = "${settings.rightSettingFileName}${songVersion.suffix}"
        val fileNameSuffix = when (songOutputFile) {
            SongOutputFile.PICTURECHORDS -> " chords"
            SongOutputFile.PICTUREBOOSTY -> " boosty"
            SongOutputFile.PICTUREVK -> " VK"
            SongOutputFile.VK -> " [VK]"
            else -> ""
        }

        return "${settings.rootFolder}/$folderName/$fileName$fileNameSuffix.${songOutputFile.extension}"
    }

}
