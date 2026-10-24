package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.KaraokeDbTable
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.sql.PreparedStatement
import java.sql.Timestamp

// Админский read-only просмотр истории прослушиваний (`tbl_listening_history`). Тот же паттерн,
// что и SitePlaylistsController / SubscriptionsController. ОБЯЗАТЕЛЬНО фильтруем SKIP-помеченные
// песни и удалённые песни на чтении — наследуем логику публичного `ListeningHistory.getForUser`.
// JOIN к tbl_songs / tbl_site_users — батчем через associateBy. Сортировка по last_played_at DESC.
//
// NB: SKIP-фильтр НЕЛЬЗЯ добавить в `whereList` для `KaraokeDbTable.loadList` — он генерирует
// однотабличный SQL без JOIN к `tbl_songs`, предикат `s.tags IS NULL ...` упадёт с
// "missing FROM-clause entry for table s" (Pass 56 hotfix). Поэтому SKIP-фильтр идёт после
// JOIN-обогащения в Kotlin — загружаем все песни по songIds и фильтруем на стороне JVM.

/**
 * Контроллер (HTTP/WebSocket endpoints) для глобального списка истории прослушиваний
 * из `tbl_listening_history`.
 *
 * Эндпоинты:
 * - `POST /api/listeninghistory/digest` — список истории (FR-008…FR-014).
 *
 * ОБЯЗАТЕЛЬНО фильтрует песни с тегом `SKIP` и удалённые песни на чтении (та же логика, что и в
 * публичном `ListeningHistory.getForUser`, см. спеку 009-listening-history). JOIN к tbl_songs и
 * tbl_site_users (для отображения email пользователя) делается на бэкенде одним батчем через
 * `KaraokeDbTable.loadByIds` (паттерн производительности — см. AGENTS.md).
 *
 * @see specs/171-admin-subscriptions-history/contracts/listeninghistory-digest.md
 * @see AGENTS.md
 */
@Controller
@RequestMapping("/api/listeninghistory")
class ListeningHistoryController {
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
        @RequestParam(required = false, defaultValue = "500") pageSize: Int,
        @RequestParam(required = false) filterUserId: Long?,
        @RequestParam(required = false) filterSongId: Long?,
        @RequestParam(required = false) filterLastPlayedFrom: String?,
        @RequestParam(required = false) filterLastPlayedTo: String?,
        @RequestParam(required = false, defaultValue = "last_played_at") sortBy: String?,
        @RequestParam(required = false, defaultValue = "DESC") sortDir: String?,
    ): Map<String, Any> =
        withDb(target) { db ->
            // 1. Clamp пагинации (1..1000 для истории — большие страницы).
            val safePage = if (page < 1) 1 else page
            val safePageSize = pageSize.coerceIn(1, 1000)
            val offset = (safePage - 1) * safePageSize

            // 2. BR-112 (аудит #336): фильтр/сортировка/пагинация — в SQL.
            //    Раньше endpoint читал ВСЮ tbl_listening_history (loadList без LIMIT),
            //    грузил все песни по всем songId и только потом сортировал/резал страницу
            //    в памяти. Теперь: EXISTS-проверка существования песни (эквивалент старого
            //    фильтра `allSongsById[h.songId] != null`), ORDER BY + LIMIT/OFFSET,
            //    отдельный COUNT(*); песни грузятся только для страницы.
            val sortColumn = if (sortBy == "play_count") "h.play_count" else "h.last_played_at"
            val sortDirection = if (sortDir?.uppercase() == "ASC") "ASC" else "DESC"
            val query =
                buildListeningHistoryQuery(
                    filterUserId = filterUserId,
                    filterSongId = filterSongId,
                    filterLastPlayedFrom = filterLastPlayedFrom,
                    filterLastPlayedTo = filterLastPlayedTo,
                    sortColumn = sortColumn,
                    sortDirection = sortDirection,
                )

            val conn =
                db.getConnection()
                    ?: return@withDb mapOf(
                        "listeningHistoryDigest" to emptyList<Any>(),
                        "totalCount" to 0,
                        "page" to safePage,
                        "pageSize" to safePageSize,
                    )

            val totalCount =
                conn.prepareStatement(query.countSql).use { ps ->
                    bindHistoryParams(ps, query.params, 1)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
                }

            val pageItems = mutableListOf<ListeningHistoryRow>()
            conn.prepareStatement(query.pageSql).use { ps ->
                val next = bindHistoryParams(ps, query.params, 1)
                ps.setInt(next, safePageSize)
                ps.setInt(next + 1, offset)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        pageItems +=
                            ListeningHistoryRow(
                                id = rs.getLong("id"),
                                siteUserId = rs.getLong("site_user_id"),
                                songId = rs.getLong("song_id"),
                                playCount = rs.getLong("play_count"),
                                lastPlayedAt = rs.getTimestamp("last_played_at"),
                            )
                    }
                }
            }

            // 3. Песни — только для страницы (а не для всей истории).
            val pageSongIds = pageItems.map { it.songId }.distinct()
            val songsById: Map<Long, Song> =
                if (pageSongIds.isEmpty()) {
                    emptyMap()
                } else {
                    Song.loadListFromDbByIds(pageSongIds, db, KSS_APP, SAC_APP)
                }

            // 4. Пользователи — только для страницы.
            val userIds = pageItems.map { it.siteUserId }.distinct()
            val usersById: Map<Long, SiteUser> =
                if (userIds.isEmpty()) {
                    emptyMap()
                } else {
                    KaraokeDbTable
                        .loadByIds(SiteUser::class, SiteUser.TABLE_NAME, userIds, db, KSS_APP, SAC_APP)
                        .map { it as SiteUser }
                        .associateBy { it.id }
                }

            val list =
                pageItems.mapNotNull { h ->
                    val song = songsById[h.songId] ?: return@mapNotNull null
                    val user = usersById[h.siteUserId]
                    mapOf(
                        "id" to h.id,
                        "siteUserId" to h.siteUserId,
                        "userEmail" to (user?.email ?: ""),
                        "userDisplayName" to (user?.displayName ?: ""),
                        "songId" to h.songId,
                        "songName" to song.songName,
                        "songAuthor" to song.author,
                        "songAlbum" to song.album,
                        "songYear" to song.year,
                        "playCount" to h.playCount,
                        "lastPlayedAt" to h.lastPlayedAt?.toString(),
                    )
                }
            mapOf(
                "listeningHistoryDigest" to list,
                "totalCount" to totalCount,
                "page" to safePage,
                "pageSize" to safePageSize,
            )
        }

    // Проверяет наличие слова-маркера SKIP в tags (через split по пробелам, case-insensitive).
    // Копия логики из SongShareLinkService.songHasSkipTag — разные модули, чтобы не тянуть
    // karaoke-web в karaoke-app через dependency.
    private fun songHasSkipTag(tags: String?): Boolean =
        (tags ?: "").split(" ").any { it.trim().equals("SKIP", ignoreCase = true) }

    private fun bindHistoryParams(
        ps: PreparedStatement,
        params: List<Any>,
        startIndex: Int,
    ): Int {
        var index = startIndex
        params.forEach { ps.setObject(index++, it) }
        return index
    }
}

/** Строка страницы истории (BR-112): только поля, нужные ответу. */
private data class ListeningHistoryRow(
    val id: Long,
    val siteUserId: Long,
    val songId: Long,
    val playCount: Long,
    val lastPlayedAt: Timestamp?,
)

/**
 * SQL страницы истории прослушиваний (BR-112, аудит #336).
 *
 * Фильтры и пагинация выполняются в БД; `EXISTS` заменяет прежний JVM-фильтр
 * «песня существует» (после JOIN-обогащения). Параметры фильтров биндятся
 * (`?`), значения не склеиваются в SQL.
 */
internal data class ListeningHistoryQuery(
    val countSql: String,
    val pageSql: String,
    val params: List<Any>,
)

internal fun buildListeningHistoryQuery(
    filterUserId: Long?,
    filterSongId: Long?,
    filterLastPlayedFrom: String?,
    filterLastPlayedTo: String?,
    sortColumn: String,
    sortDirection: String,
): ListeningHistoryQuery {
    val predicates = mutableListOf<String>()
    val params = mutableListOf<Any>()
    filterUserId?.let {
        predicates += "h.site_user_id = ?"
        params += it
    }
    filterSongId?.let {
        predicates += "h.song_id = ?"
        params += it
    }
    filterLastPlayedFrom?.takeIf { it.isNotBlank() }?.let {
        predicates += "h.last_played_at >= ?::timestamp"
        params += it
    }
    filterLastPlayedTo?.takeIf { it.isNotBlank() }?.let {
        predicates += "h.last_played_at <= ?::timestamp"
        params += it
    }
    predicates += "EXISTS (SELECT 1 FROM tbl_songs s WHERE s.id = h.song_id)"
    val where = "WHERE " + predicates.joinToString(" AND ")
    val pageSql =
        "SELECT h.id, h.site_user_id, h.song_id, h.play_count, h.last_played_at " +
            "FROM tbl_listening_history h $where " +
            "ORDER BY $sortColumn $sortDirection, h.id $sortDirection LIMIT ? OFFSET ?"
    return ListeningHistoryQuery(
        countSql = "SELECT count(*) FROM tbl_listening_history h $where",
        pageSql = pageSql,
        params = params,
    )
}
