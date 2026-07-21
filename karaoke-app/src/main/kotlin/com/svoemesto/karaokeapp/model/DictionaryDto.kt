package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable

/**
 * DTO для dictionary: сериализуемое представление для API/UI.
 *
 * @see docs/features/dual-db-sync.md
 */
data class DictionaryDto(
    val id: Long,
    val dictName: String,
    val dictValue: String,
) : Serializable,
    Comparable<DictionaryDto>,
    KaraokeDbTableDto {
    override fun compareTo(other: DictionaryDto): Int {
        val byName = dictName.compareTo(other.dictName)
        return if (byName != 0) byName else dictValue.compareTo(other.dictValue)
    }

    override fun fromDto(database: KaraokeConnection): Dictionary {
        val entity = Dictionary(database = database)
        entity.id = id
        entity.dictName = dictName
        entity.dictValue = dictValue
        return entity
    }
}
