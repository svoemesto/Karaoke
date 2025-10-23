package com.svoemesto.karaokeapp.model

interface KaraokeDbTableDto {
    fun isValid(): Boolean
    fun validationErrors(): List<String>
    fun fromDto(): KaraokeDbTable
}