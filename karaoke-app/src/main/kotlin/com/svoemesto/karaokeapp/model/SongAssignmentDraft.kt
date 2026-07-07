package com.svoemesto.karaokeapp.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import java.io.Serializable
import java.sql.Timestamp

// Рабочая копия пользователя для назначения (SongAssignment): отредактированный текст + маркеры
// ОДНОГО голоса + флаг отправки на проверку. Пишет ТОЛЬКО пользователь (SERVER), направление sync
// SERVER_TO_LOCAL (SyncRegistry: songassignmentdrafts) — как siteusers/siteplaylists. На апруве
// karaoke-app применяет edited_markers в Settings.setSourceMarkers(voice, ...) (только на LOCAL).
@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class SongAssignmentDraft(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable, Comparable<SongAssignmentDraft>, KaraokeDbTable {

    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "assignment_id")
    var assignmentId: Long = 0

    @KaraokeDbTableField(name = "assignee_id")
    var assigneeId: Long = 0

    @KaraokeDbTableField(name = "edited_source_text")
    var editedSourceText: String = ""

    @KaraokeDbTableField(name = "edited_markers")
    var editedMarkers: String = "[]"

    @KaraokeDbTableField(name = "user_status")
    var userStatus: String = SongAssignmentStatus.USER_IN_PROGRESS

    @KaraokeDbTableField(name = "submitted_at")
    var submittedAt: Timestamp? = null

    // Вне recordhash ⇒ вне diff синхронизации — см. SitePlaylist.lastUpdate / WebEvent.kt.
    @KaraokeDbTableField(name = "last_update", useInDiff = false)
    var lastUpdate: Timestamp? = null

    override fun compareTo(other: SongAssignmentDraft): Int =
        compareValuesBy(this, other, { it.id })

    override fun toDTO(): SongAssignmentDraftDto = SongAssignmentDraftDto(
        id = id,
        assignmentId = assignmentId,
        assigneeId = assigneeId,
        editedSourceText = editedSourceText,
        editedMarkers = editedMarkers,
        userStatus = userStatus,
    )

    companion object {

        const val TABLE_NAME = "tbl_song_assignment_drafts"

        fun getByAssignment(
            assignmentId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): SongAssignmentDraft? {
            return KaraokeDbTable.loadList(
                clazz = SongAssignmentDraft::class,
                tableName = TABLE_NAME,
                whereList = listOf("assignment_id=$assignmentId"),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ).firstOrNull() as? SongAssignmentDraft?
        }

        // Черновики для набора назначений (батч, чтобы посчитать композитный статус без N+1).
        // Возврат: assignmentId -> draft.
        fun loadByAssignments(
            assignmentIds: List<Long>,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Map<Long, SongAssignmentDraft> {
            if (assignmentIds.isEmpty()) return emptyMap()
            return KaraokeDbTable.loadList(
                clazz = SongAssignmentDraft::class,
                tableName = TABLE_NAME,
                whereList = listOf("assignment_id IN (${assignmentIds.joinToString(",")})"),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ).map { it as SongAssignmentDraft }.associateBy { it.assignmentId }
        }

        fun delete(id: Long, database: KaraokeConnection): Boolean =
            KaraokeDbTable.delete(tableName = TABLE_NAME, id = id, database = database)
    }
}
