package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection

/**
 * DTO для karaoke db table: сериализуемое представление для API/UI.
 *
 * @see docs/features/dual-db-sync.md
 */
interface KaraokeDbTableDto {
    fun isValid(): Boolean = true

    fun validationErrors(): List<String> = emptyList()

    fun fromDto(database: KaraokeConnection): KaraokeDbTable
}
