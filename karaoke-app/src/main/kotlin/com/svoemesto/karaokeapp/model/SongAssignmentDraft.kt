package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.Serializable
import java.sql.Timestamp

// Рабочая копия пользователя для назначения (SongAssignment): отредактированный текст + маркеры
// ВСЕЙ песни (все голоса, не один) + флаг отправки на проверку. Пишет ТОЛЬКО пользователь (SERVER),
// направление sync SERVER_TO_LOCAL (SyncRegistry: songassignmentdrafts) — как siteusers/siteplaylists.
// На апруве karaoke-app применяет edited_markers в Settings.setSourceMarkers(voice, ...) для КАЖДОГО
// голоса (только на LOCAL).
//
// Формат editedSourceText/editedMarkers — JSON-массив ПО ГОЛОСАМ (тот же формат, что
// Settings.sourceTextList/sourceMarkersList): editedMarkers = List<List<SourceMarker>>,
// editedSourceText = List<String>. editedMarkers{Per}/editedTextsPerVoice() терпимы к СТАРОМУ
// одноголосому формату (голая строка / плоский List<SourceMarker>) — черновики, сохранённые до
// перехода на multi-voice, читаются как один голос (voice 0), не ломаются.

/**
 * Черновик (промежуточное состояние) для `SongAssignment`.
 *
 * Хранит незакоммиченные изменения правок до того, как пользователь нажмёт
 * «Отправить на ревью». Используется для восстановления при сбое и
 * для версионирования правок.
 *
 * Поля аналогичны `SongAssignment` плюс `payload` (JSON с diff'ом).
 *
 * @see docs/features/async-process-queue.md
 */
class SongAssignmentDraft(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable,
    Comparable<SongAssignmentDraft>,
    KaraokeDbTable {
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
    var editedMarkers: String = "[[]]"

    @KaraokeDbTableField(name = "user_status")
    var userStatus: String = SongAssignmentStatus.USER_IN_PROGRESS

    @KaraokeDbTableField(name = "submitted_at")
    var submittedAt: Timestamp? = null

    // Вне recordhash ⇒ вне diff синхронизации — см. SitePlaylist.lastUpdate / WebEvent.kt.
    @KaraokeDbTableField(name = "last_update", useInDiff = false)
    var lastUpdate: Timestamp? = null

    override fun compareTo(other: SongAssignmentDraft): Int = compareValuesBy(this, other, { it.id })

    override fun toDTO(): SongAssignmentDraftDto =
        SongAssignmentDraftDto(
            id = id,
            assignmentId = assignmentId,
            assigneeId = assigneeId,
            editedSourceText = editedSourceText,
            editedMarkers = editedMarkers,
            userStatus = userStatus,
        )

    // Черновик по голосам — с откатом на старый одноголосый формат (голая строка / плоский список
    // маркеров), сохранённый до перехода на multi-voice.
    fun editedMarkersPerVoice(json: Json): List<List<SourceMarker>> =
        try {
            json.decodeFromString(ListSerializer(ListSerializer(SourceMarker.serializer())), editedMarkers)
        } catch (_: Exception) {
            try {
                listOf(json.decodeFromString(ListSerializer(SourceMarker.serializer()), editedMarkers))
            } catch (_: Exception) {
                emptyList()
            }
        }

    fun editedTextsPerVoice(json: Json): List<String> =
        try {
            json.decodeFromString(ListSerializer(String.serializer()), editedSourceText)
        } catch (_: Exception) {
            listOf(editedSourceText)
        }

    companion object {
        fun encodeMarkersPerVoice(markersPerVoice: List<List<SourceMarker>>): String = Json.encodeToString(markersPerVoice)

        fun encodeTextsPerVoice(textsPerVoice: List<String>): String = Json.encodeToString(textsPerVoice)

        const val TABLE_NAME = "tbl_song_assignment_drafts"

        fun getByAssignment(
            assignmentId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): SongAssignmentDraft? =
            KaraokeDbTable
                .loadList(
                    clazz = SongAssignmentDraft::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("assignment_id=$assignmentId"),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).firstOrNull() as? SongAssignmentDraft?

        // Черновики для набора назначений (батч, чтобы посчитать композитный статус без N+1).
        // Возврат: assignmentId -> draft.
        fun loadByAssignments(
            assignmentIds: List<Long>,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Map<Long, SongAssignmentDraft> {
            if (assignmentIds.isEmpty()) return emptyMap()
            return KaraokeDbTable
                .loadList(
                    clazz = SongAssignmentDraft::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("assignment_id IN (${assignmentIds.joinToString(",")})"),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as SongAssignmentDraft }
                .associateBy { it.assignmentId }
        }

        fun delete(
            id: Long,
            database: KaraokeConnection,
        ): Boolean = KaraokeDbTable.delete(tableName = TABLE_NAME, id = id, database = database)

        // Удалить черновик, привязанный к заданию — нужно при revoke()/delete() назначения, чтобы в
        // tbl_song_assignment_drafts не оставалось orphan-записи на УЖЕ не существующее назначение.
        // UNIQUE INDEX на assignment_id гарантирует, что строка тут одна (или ни одной).
        fun deleteByAssignment(
            assignmentId: Long,
            database: KaraokeConnection,
        ): Boolean {
            val connection = database.getConnection() ?: return false
            return try {
                val ps = connection.prepareStatement("DELETE FROM $TABLE_NAME WHERE assignment_id = ?")
                ps.setLong(1, assignmentId)
                ps.executeUpdate()
                ps.close()
                true
            } catch (e: Exception) {
                println("[SongAssignmentDraft.deleteByAssignment] ${e.message}")
                false
            }
        }
    }
}
