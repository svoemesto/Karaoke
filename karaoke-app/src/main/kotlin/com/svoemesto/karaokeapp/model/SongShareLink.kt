package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import java.io.Serializable
import java.sql.Timestamp

/**
 * Временная ссылка на песню, синхронизируемая между LOCAL и SERVER.
 *
 * @see docs/features/dual-db-sync.md
 */
class SongShareLink(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable,
    KaraokeDbTable {
    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "owner_site_user_id")
    var ownerSiteUserId: Long = 0

    @KaraokeDbTableField(name = "song_id")
    var songId: Long = 0

    @KaraokeDbTableField(name = "token_hash")
    var tokenHash: String = ""

    @KaraokeDbTableField(name = "active")
    var active: Boolean = true

    @KaraokeDbTableField(name = "expires_at")
    var expiresAt: Timestamp? = null

    @KaraokeDbTableField(name = "created_at", useInDiff = false)
    var createdAt: Timestamp? = null

    @KaraokeDbTableField(name = "revoked_at")
    var revokedAt: Timestamp? = null

    @KaraokeDbTableField(name = "revoke_reason")
    var revokeReason: String = ""

    @KaraokeDbTableField(name = "first_used_at")
    var firstUsedAt: Timestamp? = null

    @KaraokeDbTableField(name = "last_used_at")
    var lastUsedAt: Timestamp? = null

    @KaraokeDbTableField(name = "active_session_token_hash")
    var activeSessionTokenHash: String? = null

    @KaraokeDbTableField(name = "active_session_lease_until")
    var activeSessionLeaseUntil: Timestamp? = null

    @KaraokeDbTableField(name = "active_session_browser_hash")
    var activeSessionBrowserHash: String? = null

    @KaraokeDbTableField(name = "sessions_total")
    var sessionsTotal: Int = 0

    @KaraokeDbTableField(name = "rejected_concurrent")
    var rejectedConcurrent: Int = 0

    @KaraokeDbTableField(name = "last_update", useInDiff = false)
    var lastUpdate: Timestamp? = null

    @KaraokeDbTableField(name = "recordhash", useInDiff = false)
    var recordhash: String? = null

    override fun toDTO(): SongShareLinkDto =
        SongShareLinkDto(
            id = id,
            ownerSiteUserId = ownerSiteUserId,
            songId = songId,
            active = active,
            expiresAt = expiresAt?.toString() ?: "",
            revokedAt = revokedAt?.toString() ?: "",
            revokeReason = revokeReason,
            sessionsTotal = sessionsTotal,
            rejectedConcurrent = rejectedConcurrent,
        )

    companion object {
        const val TABLE_NAME = "tbl_song_share_links"
    }
}

data class SongShareLinkDto(
    val id: Long = 0,
    val ownerSiteUserId: Long = 0,
    val songId: Long = 0,
    val active: Boolean = true,
    val expiresAt: String = "",
    val revokedAt: String = "",
    val revokeReason: String = "",
    val sessionsTotal: Int = 0,
    val rejectedConcurrent: Int = 0,
) : Serializable,
    KaraokeDbTableDto {
    override fun fromDto(database: KaraokeConnection): SongShareLink {
        val entity = SongShareLink(database = database)
        entity.id = id
        entity.ownerSiteUserId = ownerSiteUserId
        entity.songId = songId
        entity.active = active
        entity.expiresAt = expiresAt.takeIf { it.isNotEmpty() }?.let(Timestamp::valueOf)
        entity.revokedAt = revokedAt.takeIf { it.isNotEmpty() }?.let(Timestamp::valueOf)
        entity.revokeReason = revokeReason
        entity.sessionsTotal = sessionsTotal
        entity.rejectedConcurrent = rejectedConcurrent
        return entity
    }
}
