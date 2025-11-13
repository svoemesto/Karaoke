package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.SongVersion

data class KaraokeFile(
    val type: KaraokeFileType
) {
    companion object {
        fun getList(settings: Settings): List<KaraokeFile> {
            val result: MutableList<KaraokeFile> = mutableListOf()
            KaraokeFileType.entries.filter { it.forAllVersions }.forEach { karaokeFileType ->
                val type = karaokeFileType

                result.add(
                    KaraokeFile(
                        type = type
                    )
                )
            }
            return result
        }
        fun getList(settings: Settings, songVersion: SongVersion): List<KaraokeFile> {
            val result: MutableList<KaraokeFile> = mutableListOf()
            KaraokeFileType.entries.filter { !it.forAllVersions }.forEach { karaokeFileType ->
                val type = karaokeFileType

                result.add(
                    KaraokeFile(
                        type = type
                    )
                )
            }
            return result
        }
    }
}