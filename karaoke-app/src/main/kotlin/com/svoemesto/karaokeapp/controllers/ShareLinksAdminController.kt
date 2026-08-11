package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement

// Админский read-only просмотр ВСЕХ временных share-ссылок (`tbl_song_share_links`). Не путать
// с per-user `SiteShareLinksController` (там только ссылки одного пользователя). Этот
// контроллер даёт глобальный обзор с фильтрами (activeOnly/ownerId/songId/createdFrom/createdTo)
// и target-aware (local/remote). Действие «Отозвать» реализовано через существующий
// `POST /api/siteusers/share/links/revoke` в karaoke-web и НЕ дублируется здесь.

/**
 * Контроллер (HTTP/WebSocket endpoints) для глобального списка временных share-ссылок
 * из `tbl_song_share_links`.
 *
 * Эндпоинты:
 * - `POST /api/sharelinks/digest` — список всех share-ссылок (FR-015…FR-022).
 *
 * Поддерживает фильтры (activeOnly/ownerId/songId/диапазон дат), пагинацию (offset+limit),
 * target-aware выбор БД (local|remote). JOIN к tbl_songs / tbl_site_users делается одним
 * батчем через `KaraokeDbTable.loadByIds` (паттерн производительности — см. AGENTS.md).
 *
 * Действие «Отозвать» НЕ реализовано здесь — оно переиспользует существующий
 * `POST /api/siteusers/share/links/revoke` из `SiteShareLinksController.kt`
 * (см. `webvue3/src/components/SiteUsers/shareLinkStore.js:64` → `revokeSiteUserShareLink`).
 *
 * @see specs/171-admin-subscriptions-history/contracts/sharelinks-digest.md
 * @see AGENTS.md
 * @see docs/features/guest-share-link.md
 */
@Controller
@RequestMapping("/api/sharelinks")
class ShareLinksAdminController {
    private fun resolveDb(target: String?): KaraokeConnection = if (target == "remote") Connection.remote() else Connection.local()

    private fun <T> withDb(
        target: String?,
        block: (KaraokeConnection) -> T,
    ): T {
        val db = resolveDb(target)
        return try {
            block(db)
        } finally {
            try {
                db.getConnection()?.close()
            } catch (_: Exception) {
            }
        }
    }

    @PostMapping("/digest")
    @ResponseBody
    fun digest(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "25") pageSize: Int,
        @RequestParam(required = false, defaultValue = "false") filterActiveOnly: Boolean,
        @RequestParam(required = false) filterOwnerId: Long?,
        @RequestParam(required = false) filterSongId: Long?,
        @RequestParam(required = false) filterCreatedFrom: String?,
        @RequestParam(required = false) filterCreatedTo: String?,
        @RequestParam(required = false, defaultValue = "created_at") sortBy: String?,
        @RequestParam(required = false, defaultValue = "DESC") sortDir: String?,
    ): Map<String, Any> =
        withDb(target) { db ->
            // 1. Собираем WHERE-блок (фильтры на tbl_song_share_links, алиас l).
            val whereList = mutableListOf<String>()
            filterOwnerId?.let { whereList.add("l.owner_site_user_id=$it") }
            filterSongId?.let { whereList.add("l.song_id=$it") }
            filterCreatedFrom?.takeIf { it.isNotBlank() }?.let { whereList.add("l.created_at >= '${it.replace("'", "''")}'") }
            filterCreatedTo?.takeIf { it.isNotBlank() }?.let { whereList.add("l.created_at <= '${it.replace("'", "''")}'") }
            if (filterActiveOnly) {
                whereList.add("l.active=true")
                whereList.add("l.expires_at > now()")
            }
            val where = if (whereList.isEmpty()) "" else "WHERE " + whereList.joinToString(" AND ")

            // 2. Clamp пагинации.
            val safePage = if (page < 1) 1 else page
            val safePageSize = pageSize.coerceIn(1, 100)
            val offset = (safePage - 1) * safePageSize

            // 3. Whitelist сортировки.
            val sortColumn =
                when (sortBy) {
                    "created_at", "expires_at" -> sortBy
                    else -> "created_at"
                }
            val sortDirection = if (sortDir?.uppercase() == "ASC") "ASC" else "DESC"

            // 4. Прямой SQL — loadList не подходит из-за computed `has_active_session` (см. шаг 6).
            //    Подгружаем ВСЕ строки по WHERE и сортируем in-memory (как в SubscriptionsController).
            val sql =
                "SELECT l.id, l.song_id, l.owner_site_user_id, l.token_hash, " +
                    "l.created_at, l.expires_at, l.active, l.revoked_at, l.revoke_reason, " +
                    "(l.active_session_token_hash IS NOT NULL AND l.active_session_lease_until > now()) AS has_active_session, " +
                    "u.email, u.display_name, " +
                    "s.song_name, s.song_author, s.song_album, s.song_year " +
                    "FROM tbl_song_share_links l " +
                    "LEFT JOIN tbl_site_users u ON u.id = l.owner_site_user_id " +
                    "LEFT JOIN tbl_songs s ON s.id = l.song_id " +
                    (if (where.isEmpty()) "" else " $where ") +
                    "ORDER BY l.$sortColumn $sortDirection"

            val rows = mutableListOf<MutableMap<String, Any?>>()
            val connection = db.getConnection()
            if (connection != null) {
                var statement: Statement? = null
                var rs: ResultSet? = null
                try {
                    statement = connection.createStatement()
                    // 5. Пагинация — добавляем LIMIT/OFFSET в SQL.
                    val paginatedSql = "$sql LIMIT $safePageSize OFFSET $offset"
                    rs = statement.executeQuery(paginatedSql)
                    while (rs.next()) {
                        rows.add(
                            mutableMapOf(
                                "id" to rs.getLong("id"),
                                "songId" to rs.getLong("song_id"),
                                "songName" to (rs.getString("song_name") ?: ""),
                                "songAuthor" to (rs.getString("song_author") ?: ""),
                                "songAlbum" to (rs.getString("song_album") ?: ""),
                                "songYear" to (rs.getLong("song_year")),
                                "ownerSiteUserId" to rs.getLong("owner_site_user_id"),
                                "ownerEmail" to (rs.getString("email") ?: ""),
                                "ownerDisplayName" to (rs.getString("display_name") ?: ""),
                                "secret" to rs.getString("token_hash"),
                                "createdAt" to rs.getTimestamp("created_at")?.toString(),
                                "expiresAt" to rs.getTimestamp("expires_at")?.toString(),
                                "active" to rs.getBoolean("active"),
                                "revokedAt" to rs.getTimestamp("revoked_at")?.toString(),
                                "revokeReason" to (rs.getString("revoke_reason") ?: ""),
                                "hasActiveSession" to rs.getBoolean("has_active_session"),
                            ),
                        )
                    }
                } catch (e: SQLException) {
                    e.printStackTrace()
                } finally {
                    try {
                        rs?.close()
                        statement?.close()
                    } catch (_: SQLException) {
                    }
                }
            }

            // 6. Total count через отдельный SQL с теми же фильтрами (без JOIN к users/songs —
            //    count(*) считаем по l, чтобы не плодить JOIN-стоимость на 5k записей).
            var totalCount = 0
            val countConnection = db.getConnection()
            if (countConnection != null) {
                var st: Statement? = null
                var rs: ResultSet? = null
                try {
                    st = countConnection.createStatement()
                    val countSql =
                        "SELECT COUNT(*) FROM tbl_song_share_links l " +
                            (if (where.isEmpty()) "" else " $where")
                    rs = st.executeQuery(countSql)
                    if (rs.next()) totalCount = rs.getInt(1)
                } catch (e: SQLException) {
                    e.printStackTrace()
                } finally {
                    try {
                        rs?.close()
                        st?.close()
                    } catch (_: SQLException) {
                    }
                }
            }

            mapOf(
                "shareLinksDigest" to rows,
                "totalCount" to totalCount,
                "page" to safePage,
                "pageSize" to safePageSize,
            )
        }
}
