package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable
import java.sql.Timestamp

// availableStems — НЕ БД-поле, выводится детерминированно из mode (StemJobMode.stemNames), нужен
// фронту (webvue3 Processes / будущая admin-панель), чтобы не дублировать список режимов.
data class StemJobDto(
    val id: Long = 0,
    val siteUserId: Long = 0,
    val mode: String = StemJobMode.DEMUCS2,
    val status: String = StemJobStatus.WAITING,
    val originalFileName: String = "",
    val originalExt: String = "",
    val fileSizeBytes: Long = 0,
    val errorMessage: String = "",
    val createdAt: Timestamp? = null,
    val startedAt: Timestamp? = null,
    val finishedAt: Timestamp? = null,
    val expiresAt: Timestamp? = null,
    val deleteRequested: Boolean = false,
    val availableStems: List<String> = StemJobMode.stemNames(mode),
) : Serializable, KaraokeDbTableDto {

    override fun fromDto(database: KaraokeConnection): StemJob {
        val entity = StemJob(database = database)
        entity.id = id
        entity.siteUserId = siteUserId
        entity.mode = mode
        entity.status = status
        entity.originalFileName = originalFileName
        entity.originalExt = originalExt
        entity.fileSizeBytes = fileSizeBytes
        entity.errorMessage = errorMessage
        entity.deleteRequested = deleteRequested
        return entity
    }
}
