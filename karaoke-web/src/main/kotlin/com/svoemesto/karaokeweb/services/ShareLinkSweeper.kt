package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.WORKING_DATABASE
import com.svoemesto.karaokeweb.config.WebShareProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Фоновый sweeper share-ссылок (Spring `@Scheduled`). Каждые `karaoke.share.sweep-interval-seconds`
 * секунд (по умолчанию 60) проходит по активным ссылкам и отзывает те, которые должны быть
 * отозваны: истёк lease / истёк expires_at / владелец потерял премиум / у песни SKIP или будущий
 * publish_date. Подробности — см. archive/docs/features/guest-share-link.md, spec.md FR-040…FR-042.
 */
@Component
class ShareLinkSweeper(
    private val shareService: SongShareLinkService,
    private val webShareProperties: WebShareProperties,
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
) {
    private val log = LoggerFactory.getLogger(ShareLinkSweeper::class.java)

    @Scheduled(fixedDelayString = "\${karaoke.share.sweep-interval-seconds:60}000")
    fun sweep() {
        val database = WORKING_DATABASE
        try {
            sweepLeaseTimeouts(database)
            sweepExpired(database)
            sweepPremiumLost(database)
            sweepSongUnavailable(database)
        } catch (e: Exception) {
            // Сварм не должен ронять процесс — логируем и продолжаем. Следующий тик повторит.
            log.warn("ShareLinkSweeper: ошибка на тике", e)
        }
    }

    /**
     * Lease истёк (`active_session_lease_until < now()`), а в `tbl_song_share_sessions` осталась
     * запись с `finished_at IS NULL` → закрыть её как `result='timeout'`, обнулить `active_session_*`.
     */
    private fun sweepLeaseTimeouts(database: KaraokeConnection) {
        val conn = database.getConnection() ?: return
        // Закрываем висящие сессии.
        var closedSessions = 0
        conn
            .prepareStatement(
                "UPDATE tbl_song_share_sessions s SET finished_at=l.active_session_lease_until, result='timeout' " +
                    "FROM tbl_song_share_links l " +
                    "WHERE s.share_link_id=l.id AND s.finished_at IS NULL " +
                    "AND l.active_session_lease_until IS NOT NULL AND l.active_session_lease_until<now()",
            ).use { ps ->
                closedSessions = ps.executeUpdate()
            }
        // Обнуляем active_session_* на самой ссылке.
        var clearedLinks = 0
        conn
            .prepareStatement(
                "UPDATE tbl_song_share_links SET active_session_token_hash=NULL, " +
                    "active_session_browser_hash=NULL, active_session_lease_until=NULL " +
                    "WHERE active AND active_session_lease_until IS NOT NULL AND active_session_lease_until<now()",
            ).use { ps ->
                clearedLinks = ps.executeUpdate()
            }
        if (closedSessions > 0 || clearedLinks > 0) {
            log.info("ShareLinkSweeper.leaseTimeouts: closedSessions={}, clearedLinks={}", closedSessions, clearedLinks)
        }
    }

    /**
     * `expires_at < now()` И `active=true` — sweep фиксирует забытые «истёкшие, но не отозванные»
     * ссылки (`revoke_reason='expired'`).
     */
    private fun sweepExpired(database: KaraokeConnection) {
        val conn = database.getConnection() ?: return
        val count =
            conn
                .prepareStatement(
                    "UPDATE tbl_song_share_links SET active=false, revoked_at=now(), revoke_reason='expired' " +
                        "WHERE active AND expires_at<now()",
                ).use { ps -> ps.executeUpdate() }
        if (count > 0) log.info("ShareLinkSweeper.expired: revoked={}", count)
    }

    /**
     * Владелец потерял премиум (`SiteUser.isEffectivePremium == false`) — отзываем все его активные
     * ссылки. Грузим владельцев порциями по [BATCH_SIZE], чтобы не уронить БД на 10k ссылок.
     */
    private fun sweepPremiumLost(database: KaraokeConnection) {
        val conn = database.getConnection() ?: return
        // Берём пачку owner_site_user_id с активными ссылками, резолвим isEffectivePremium через
        // SiteUser (он уже учитывает бан/конец подписки/истечение).
        val ownerIds = mutableListOf<Long>()
        conn
            .prepareStatement(
                "SELECT DISTINCT owner_site_user_id FROM tbl_song_share_links WHERE active",
            ).use { ps ->
                val rs = ps.executeQuery()
                while (rs.next()) ownerIds.add(rs.getLong(1))
            }
        var revoked = 0
        for (ownerId in ownerIds) {
            val owner =
                try {
                    SiteUser.getSiteUserById(ownerId, database, storageService, storageApiClient)
                } catch (e: Exception) {
                    null
                }
            if (owner != null && !owner.isEffectivePremium) {
                val n =
                    conn
                        .prepareStatement(
                            "UPDATE tbl_song_share_links SET active=false, revoked_at=now(), revoke_reason='premium_lost' " +
                                "WHERE owner_site_user_id=? AND active",
                        ).use { ps ->
                            ps.setLong(1, ownerId)
                            ps.executeUpdate()
                        }
                revoked += n
            }
        }
        if (revoked > 0) log.info("ShareLinkSweeper.premiumLost: revoked={}", revoked)
    }

    /**
     * У песни появился SKIP-тег или будущий `publish_date` (`tbl_songs`) — отзываем активные
     * ссылки на эту песню (`revoke_reason='song_unavailable'`).
     */
    private fun sweepSongUnavailable(database: KaraokeConnection) {
        val conn = database.getConnection() ?: return
        // Кандидаты: пары (link_id, song_id) с активной ссылкой. Загружаем порциями, проверяем
        // SKIP/id_status/source_markers/readiness через существующий [songIsShareablePublic].
        val candidates = mutableListOf<Pair<Long, Long>>()
        conn
            .prepareStatement(
                "SELECT id, song_id FROM tbl_song_share_links WHERE active ORDER BY id",
            ).use { ps ->
                val rs = ps.executeQuery()
                while (rs.next()) candidates.add(rs.getLong("id") to rs.getLong("song_id"))
            }
        var revoked = 0
        for (startIdx in candidates.indices step BATCH_SIZE) {
            val end = minOf(startIdx + BATCH_SIZE, candidates.size)
            for (i in startIdx until end) {
                val (linkId, songId) = candidates[i]
                if (!shareService.songIsShareablePublic(songId, database)) {
                    val n =
                        conn
                            .prepareStatement(
                                "UPDATE tbl_song_share_links SET active=false, revoked_at=now(), " +
                                    "revoke_reason='song_unavailable' WHERE id=? AND active",
                            ).use { ps ->
                                ps.setLong(1, linkId)
                                ps.executeUpdate()
                            }
                    revoked += n
                }
            }
        }
        if (revoked > 0) log.info("ShareLinkSweeper.songUnavailable: revoked={}", revoked)
    }

    companion object {
        private const val BATCH_SIZE = 100
    }
}
