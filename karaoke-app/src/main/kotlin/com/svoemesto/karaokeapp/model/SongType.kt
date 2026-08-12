package com.svoemesto.karaokeapp.model

import java.io.Serializable

// Тип песни (отличает её по составу: песня = вокал + музыка, инструментал = только музыка без вокала,
// стихи = только вокал без музыки). Хранится в tbl_songs.song_type в lowercase-форме dbValue.
// Значение по умолчанию — SONG (dbValue "song") — применяется для всех существующих песен
// через DEFAULT в миграции БД; новые песни тоже получают это значение по умолчанию.

/**
 * Перечисление возможных значений для song type.
 *
 * @see docs/features/mlt-generator.md
 */
@Suppress("unused")
enum class SongType(
    val dbValue: String,
    val description: String,
    val caption: String,
) : Serializable {
    SONG(dbValue = "song", description = "Песня (вокал + музыка)", caption = "Песня"),
    INSTRUMENTAL(dbValue = "instrumental", description = "Инструментал (только музыка)", caption = "Инструментальная композиция"),
    POETRY(dbValue = "poetry", description = "Стихи (только вокал)", caption = "Поэзия (без музыки)"),
}
