package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.KaraokeDbTable
import com.svoemesto.karaokeapp.model.KaraokeDbTableDto
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.KaraokeStorageServiceImpl
import java.io.Serializable
import java.sql.Timestamp

data class KaraokeProcessDTO(
    val id: Long,
    val name: String,
    val status: String,
    val order: Int,
    val priority: Int,
    val command: String,
    val args: List<List<String>>,
    val envs: Map<String, String>,
    val argsDescription: List<String>,
    val description: String,
    val settingsId: Int,
    val type: String,
    val start: Timestamp?,
    val end: Timestamp?,
    val prioritet: Int,
    val startStr: String,
    val endStr: String,
    val percentage: Double,
    val percentageStr: String,
    val timePassedMs: Long,
    val timePassedStr: String,
    val timeLeftMs: Long,
    val timeLeftStr: String,
    val withoutControl: Boolean,
    val threadId: Int
) : Serializable, Comparable<KaraokeProcessDTO>, KaraokeDbTableDto {

    override fun compareTo(other: KaraokeProcessDTO): Int {
        var result = priority.compareTo(other.priority)
        if (result != 0) return result
        result = order.compareTo(other.order)
        if (result != 0) return result
        return id.compareTo(other.id)

    }

    override fun isValid(): Boolean {
        return true
    }

    override fun validationErrors(): List<String> {
        TODO("Not yet implemented")
    }

    override fun fromDto(database: KaraokeConnection): KaraokeDbTable {
        TODO("Not yet implemented")
    }
}