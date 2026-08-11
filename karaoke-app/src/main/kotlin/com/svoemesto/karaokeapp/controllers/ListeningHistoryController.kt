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
            // 1. Собираем whereList ТОЛЬКО на h.* (tbl_listening_history). SKIP-фильтр НЕ здесь —
            //    см. комментарий в header файла.
            val whereList = mutableListOf<String>()
            filterUserId?.let { whereList.add("site_user_id=$it") }
            filterSongId?.let { whereList.add("song_id=$it") }
            filterLastPlayedFrom?.takeIf { it.isNotBlank() }?.let { whereList.add("last_played_at >= '${it.replace("'", "''")}'") }
            filterLastPlayedTo?.takeIf { it.isNotBlank() }?.let { whereList.add("last_played_at <= '${it.replace("'", "''")}'") }

            // 2. Clamp пагинации (1..1000 для истории — большие страницы).
            val safePage = if (page < 1) 1 else page
            val safePageSize = pageSize.coerceIn(1, 1000)
            val offset = (safePage - 1) * safePageSize

            // 3. Загружаем всю порцию прослушиваний по WHERE (без SKIP-фильтра).
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

            // 4. Батч-JOIN к tbl_songs по всем songIds (включая не-SKIP, чтобы фильтрация работала).
            //    Делаем это ДО сортировки и пагинации, чтобы SKIP-песни были исключены из totalCount.
            val allSongIds = allLoaded.map { it.songId }.distinct()
            val allSongsById =
                if (allSongIds.isEmpty()) {
                    emptyMap()
                } else {
                    // NB: Song НЕ использует @KaraokeDbTableField-рефлексию (поля в `fields`-map),
                    // поэтому `KaraokeDbTable.loadByIds(Song::class, ...)` возвращает пустые сущности
                    // (songName = "", tags = "" — без `tags` мы ВСЕ записи фильтруем по SKIP).
                    // Используем Song.loadListFromDbByIds — кастомный SQL, populate'ит `tags`/`songName`.
                    Song.loadListFromDbByIds(allSongIds, db, KSS_APP, SAC_APP)
                }

            // 5. Фильтруем: оставляем только строки, где песня СУЩЕСТВУЕТ и НЕ имеет тег SKIP
            //    (та же логика, что и INNER JOIN + s.tags NOT SKIP в публичном `getForUser`).
            val filtered =
                allLoaded.filter { h ->
                    val song = allSongsById[h.songId]
                    song != null && !songHasSkipTag(song.tags)
                }

            // 6. Сортировка (whitelist + default).
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
                            filtered.sortedBy { it.playCount }
                        } else {
                            filtered.sortedByDescending { it.playCount }
                        }
                    else ->
                        if (sortDirection == "ASC") {
                            filtered.sortedBy { it.lastPlayedAt?.time ?: 0L }
                        } else {
                            filtered.sortedByDescending { it.lastPlayedAt?.time ?: 0L }
                        }
                }
            val totalCount = sorted.size
            val pageItems = sorted.drop(offset).take(safePageSize)

            // 7. Батч-JOIN к tbl_site_users для email пользователя (только по порции страницы).
            val userIds = pageItems.map { it.siteUserId }.distinct()
            val usersById =
                if (userIds.isEmpty()) {
                    emptyMap()
                } else {
                    KaraokeDbTable
                        .loadByIds(SiteUser::class, SiteUser.TABLE_NAME, userIds, db, KSS_APP, SAC_APP)
                        .map { it as SiteUser }
                        .associateBy { it.id }
                }

            // 8. Достаём песни порции из уже загруженного allSongsById — второй запрос не нужен.
            val list =
                pageItems.map { h ->
                    val song = allSongsById[h.songId]!!
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
}
