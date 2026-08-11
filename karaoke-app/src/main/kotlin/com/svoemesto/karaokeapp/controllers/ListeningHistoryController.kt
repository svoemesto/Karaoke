package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.KaraokeDbTable
import com.svoemesto.karaokeapp.model.ListeningHistory
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

// Админский read-only просмотр истории прослушиваний (`tbl_listening_history`). Тот же паттерн,
// что и SitePlaylistsController / SubscriptionsController. ОБЯЗАТЕЛЬНО фильтруем SKIP-помеченные
// песни на чтении — наследуем логику публичного `ListeningHistory.getForUser`. JOIN к tbl_songs /
// tbl_site_users — батчем через associateBy. Сортировка по last_played_at DESC.

/**
 * Контроллер (HTTP/WebSocket endpoints) для глобального списка истории прослушиваний
 * из `tbl_listening_history`.
 *
 * Эндпоинты:
 * - `POST /api/listeninghistory/digest` — список истории (FR-008…FR-014).
 *
 * ОБЯЗАТЕЛЬНО фильтрует песни с тегом `SKIP` на чтении (та же логика, что и в публичном
 * `ListeningHistory.getForUser`, см. спеку 009-listening-history). JOIN к tbl_songs и
 * LEFT JOIN к tbl_site_users (для отображения email пользователя) делается на бэкенде одним
 * батчем через `KaraokeDbTable.loadByIds` (паттерн производительности — см. AGENTS.md).
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
            // 1. Собираем whereList из непустых фильтров. NB: tbl_listening_history — это h, songs — s.
            //    WHERE-предикаты на h.*, кроме SKIP-фильтра (на s.tags).
            val whereList = mutableListOf<String>()
            whereList.add("(s.tags IS NULL OR NOT ('SKIP' = ANY(string_to_array(upper(coalesce(s.tags,'')), ' '))))")
            filterUserId?.let { whereList.add("h.site_user_id=$it") }
            filterSongId?.let { whereList.add("h.song_id=$it") }
            filterLastPlayedFrom?.takeIf { it.isNotBlank() }?.let { whereList.add("h.last_played_at >= '${it.replace("'", "''")}'") }
            filterLastPlayedTo?.takeIf { it.isNotBlank() }?.let { whereList.add("h.last_played_at <= '${it.replace("'", "''")}'") }

            // 2. Clamp пагинации (1..1000 для истории — большие страницы).
            val safePage = if (page < 1) 1 else page
            val safePageSize = pageSize.coerceIn(1, 1000)
            val offset = (safePage - 1) * safePageSize

            // 3. Загружаем всю порцию (loadList уже применяет WHERE), фильтруем in-memory для
            //    дополнительной защиты от SKIP-тегов и сортируем.
            val allLoaded =
                KaraokeDbTable
                    .loadList(
                        clazz = ListeningHistory::class,
                        tableName = ListeningHistory.TABLE_NAME,
                        whereList = whereList,
                        database = db,
                        storageService = KSS_APP,
                        storageApiClient = SAC_APP,
                    ).map { it as ListeningHistory }

            // 4. Сортировка (whitelist + default).
            val sortColumn =
                when (sortBy) {
                    "last_played_at", "play_count" -> sortBy
                    else -> "last_played_at"
                }
            val sortDirection = if (sortDir?.uppercase() == "ASC") "ASC" else "DESC"
            val sorted =
                when (sortColumn) {
                    "play_count" ->
                        if (sortDirection == "ASC") {
                            allLoaded.sortedBy { it.playCount }
                        } else {
                            allLoaded.sortedByDescending { it.playCount }
                        }
                    else ->
                        if (sortDirection == "ASC") {
                            allLoaded.sortedBy { it.lastPlayedAt?.time ?: 0L }
                        } else {
                            allLoaded.sortedByDescending { it.lastPlayedAt?.time ?: 0L }
                        }
                }
            val totalCount = sorted.size
            val pageItems = sorted.drop(offset).take(safePageSize)

            // 5. Батч-JOIN к tbl_songs и tbl_site_users.
            val songIds = pageItems.map { it.songId }.distinct()
            val userIds = pageItems.map { it.siteUserId }.distinct()
            val songsById =
                if (songIds.isEmpty()) {
                    emptyMap()
                } else {
                    KaraokeDbTable
                        .loadByIds(Song::class, Song.TABLE_NAME, songIds, db, KSS_APP, SAC_APP)
                        .map { it as Song }
                        .associateBy { it.id }
                }
            val usersById =
                if (userIds.isEmpty()) {
                    emptyMap()
                } else {
                    KaraokeDbTable
                        .loadByIds(SiteUser::class, SiteUser.TABLE_NAME, userIds, db, KSS_APP, SAC_APP)
                        .map { it as SiteUser }
                        .associateBy { it.id }
                }

            val list =
                pageItems.map { h ->
                    val song = songsById[h.songId]
                    val user = usersById[h.siteUserId]
                    mapOf(
                        "id" to h.id,
                        "siteUserId" to h.siteUserId,
                        "userEmail" to (user?.email ?: ""),
                        "userDisplayName" to (user?.displayName ?: ""),
                        "songId" to h.songId,
                        "songName" to (song?.songName ?: ""),
                        "songAuthor" to (song?.author ?: ""),
                        "songAlbum" to (song?.album ?: ""),
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
}
