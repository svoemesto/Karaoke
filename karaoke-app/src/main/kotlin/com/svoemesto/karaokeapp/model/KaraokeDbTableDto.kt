package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection

interface KaraokeDbTableDto {
    fun isValid(): Boolean = true
    fun validationErrors(): List<String> = emptyList()
    fun fromDto(database: KaraokeConnection): KaraokeDbTable
}