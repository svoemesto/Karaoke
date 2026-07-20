package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.sql.Timestamp

// Персистентные токены сессии сайта. Сознательно НЕ JWT — баны/logout должны действовать мгновенно,
// а не только после истечения TTL подписанного токена. Чистый JDBC поверх tbl_site_user_tokens,
// без reflection-машинерии KaraokeDbTable — это самая горячая таблица (на каждый защищённый запрос).
@Service
class SiteUserTokenService(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
) {
    private val random = SecureRandom()

    fun issueToken(
        siteUserId: Long,
        database: KaraokeConnection,
        ttlDays: Long = 30,
    ): String? {
        val connection = database.getConnection() ?: return null
        val token = ByteArray(32).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val expiresAt = Timestamp(System.currentTimeMillis() + ttlDays * 24 * 60 * 60 * 1000)

        val ps =
            connection.prepareStatement(
                "INSERT INTO tbl_site_user_tokens (site_user_id, token, expires_at) VALUES (?, ?, ?)",
            )
        ps.setLong(1, siteUserId)
        ps.setString(2, token)
        ps.setTimestamp(3, expiresAt)
        ps.executeUpdate()
        ps.close()
        return token
    }

    fun resolveToken(
        token: String,
        database: KaraokeConnection,
    ): SiteUser? {
        val connection = database.getConnection() ?: return null

        val ps =
            connection.prepareStatement(
                "SELECT site_user_id FROM tbl_site_user_tokens WHERE token = ? AND revoked = false AND expires_at > now()",
            )
        ps.setString(1, token)
        val rs = ps.executeQuery()
        val siteUserId = if (rs.next()) rs.getLong("site_user_id") else null
        rs.close()
        ps.close()
        if (siteUserId == null) return null

        touchLastUsed(token, database)

        val user = SiteUser.getSiteUserById(siteUserId, database, storageService, storageApiClient)
        return if (user == null || user.isBanned) null else user
    }

    fun revokeToken(
        token: String,
        database: KaraokeConnection,
    ) {
        val connection = database.getConnection() ?: return
        val ps = connection.prepareStatement("UPDATE tbl_site_user_tokens SET revoked = true WHERE token = ?")
        ps.setString(1, token)
        ps.executeUpdate()
        ps.close()
    }

    private fun touchLastUsed(
        token: String,
        database: KaraokeConnection,
    ) {
        val connection = database.getConnection() ?: return
        val ps = connection.prepareStatement("UPDATE tbl_site_user_tokens SET last_used_at = now() WHERE token = ?")
        ps.setString(1, token)
        ps.executeUpdate()
        ps.close()
    }
}
