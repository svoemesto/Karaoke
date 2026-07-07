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

// Назначение песни (голоса) пользователю публичного сайта на разметку в онлайн-редакторе + вердикт
// админа. Пишет ТОЛЬКО админ (LOCAL), направление sync LOCAL_TO_SERVER (SyncRegistry: songassignments).
// Рабочая копия пользователя — в отдельной таблице SongAssignmentDraft (пишет пользователь, обратное
// направление), см. 10_song_assignments.sql про причину разделения.
@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class SongAssignment(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable, Comparable<SongAssignment>, KaraokeDbTable {

    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "assignee_id")
    var assigneeId: Long = 0

    @KaraokeDbTableField(name = "song_id")
    var songId: Long = 0

    @KaraokeDbTableField(name = "voice")
    var voice: Long = 0

    @KaraokeDbTableField(name = "admin_status")
    var adminStatus: String = SongAssignmentStatus.ADMIN_OPEN

    @KaraokeDbTableField(name = "review_comment")
    var reviewComment: String = ""

    @KaraokeDbTableField(name = "assigned_by")
    var assignedBy: Long = 0

    // Nullable-в-БД timestamp: поле обязано быть Timestamp? (иначе NPE в reflection-loader на SQL NULL —
    // см. SitePlaylist.lastUpdate / CLAUDE.md про reflection loader nullable columns).
    @KaraokeDbTableField(name = "assigned_at")
    var assignedAt: Timestamp? = null

    @KaraokeDbTableField(name = "reviewed_at")
    var reviewedAt: Timestamp? = null

    // Вне recordhash ⇒ вне diff синхронизации — см. SitePlaylist.lastUpdate / WebEvent.kt.
    @KaraokeDbTableField(name = "last_update", useInDiff = false)
    var lastUpdate: Timestamp? = null

    override fun compareTo(other: SongAssignment): Int =
        compareValuesBy(this, other, { it.id })

    override fun toDTO(): SongAssignmentDto = SongAssignmentDto(
        id = id,
        assigneeId = assigneeId,
        songId = songId,
        voice = voice,
        adminStatus = adminStatus,
        reviewComment = reviewComment,
        assignedBy = assignedBy,
    )

    companion object {

        const val TABLE_NAME = "tbl_song_assignments"

        fun loadByAssignee(
            assigneeId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<SongAssignment> {
            return KaraokeDbTable.loadList(
                clazz = SongAssignment::class,
                tableName = TABLE_NAME,
                whereList = listOf("assignee_id=$assigneeId"),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ).map { it as SongAssignment }.sorted()
        }

        fun loadBySong(
            songId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<SongAssignment> {
            return KaraokeDbTable.loadList(
                clazz = SongAssignment::class,
                tableName = TABLE_NAME,
                whereList = listOf("song_id=$songId"),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ).map { it as SongAssignment }.sorted()
        }

        fun loadAll(
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<SongAssignment> {
            return KaraokeDbTable.loadList(
                clazz = SongAssignment::class,
                tableName = TABLE_NAME,
                whereList = emptyList(),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ).map { it as SongAssignment }.sorted()
        }

        fun getById(
            id: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): SongAssignment? {
            return KaraokeDbTable.loadById(
                clazz = SongAssignment::class,
                tableName = TABLE_NAME,
                id = id,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) as? SongAssignment?
        }

        // Существующее назначение этой песни этому пользователю (защита от дубля перед assign).
        fun findExisting(
            songId: Long,
            assigneeId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): SongAssignment? {
            return KaraokeDbTable.loadList(
                clazz = SongAssignment::class,
                tableName = TABLE_NAME,
                whereList = listOf("song_id=$songId", "assignee_id=$assigneeId"),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ).firstOrNull() as? SongAssignment?
        }

        fun delete(id: Long, database: KaraokeConnection): Boolean =
            KaraokeDbTable.delete(tableName = TABLE_NAME, id = id, database = database)
    }
}
