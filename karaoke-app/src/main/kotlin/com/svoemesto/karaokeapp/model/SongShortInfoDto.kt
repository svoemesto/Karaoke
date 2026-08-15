package com.svoemesto.karaokeapp.model

import java.io.Serializable

/**
 * Минимальная информация о песне для тултипов в admin SPA.
 *
 * Используется для отображения автор/год/альбом/название при наведении
 * на ячейки `root` и `A-root` в таблице песен `webvue3`.
 *
 * @see archive/docs/features/songs-table.md
 * @see specs/023-songs-audio-root-column/contracts/song-shortinfo.md
 */
data class SongShortInfoDto(
    val id: Long,
    val author: String,
    val year: Long,
    val album: String,
    val songName: String,
) : Serializable
