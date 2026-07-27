package com.svoemesto.karaokeapp.model

import java.io.Serializable

// Тип альбома (студийный/концертный/сборник/бутлег). Хранится в tbl_albums.album_type в
// lowercase-форме dbValue, по образцу SongType — enum только типобезопасная обёртка над уже
// сохранённой строкой (не .name/.ordinal), чтобы переименование констант в коде не требовало
// миграции данных. Значение по умолчанию — STUDIO (dbValue "studio").

/**
 * Перечисление возможных значений для album type.
 *
 * @see docs/features/dual-db-sync.md
 */
@Suppress("unused")
enum class AlbumType(
    val dbValue: String,
    val description: String,
) : Serializable {
    STUDIO(dbValue = "studio", description = "Студийный альбом"),
    LIVE(dbValue = "live", description = "Концертный альбом"),
    COMPILATION(dbValue = "compilation", description = "Сборник"),
    BOOTLEG(dbValue = "bootleg", description = "Бутлег"),
    SINGLE(dbValue = "single", description = "Сингл"),
    ;

    companion object {
        fun fromDb(value: String?): AlbumType = entries.find { it.dbValue == value } ?: STUDIO
    }
}
