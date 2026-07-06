package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.SitePlaylist
import com.svoemesto.karaokeapp.model.SitePlaylistItem
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

// Админский просмотр «Избранного»/плейлистов пользователей сайта (read-only). Тот же паттерн, что и
// SiteUsersController: target=local|remote выбирается явно клиентом (webvue3), т.к. реальные плейлисты
// создаются на боевой БД. withDb закрывает per-request соединение (иначе "too many clients").
@Controller
@RequestMapping("/api/siteplaylists")
class SitePlaylistsController {

    private fun resolveDb(target: String?): KaraokeConnection =
        if (target == "remote") Connection.remote() else Connection.local()

    private fun <T> withDb(target: String?, block: (KaraokeConnection) -> T): T {
        val db = resolveDb(target)
        return try {
            block(db)
        } finally {
            try { db.getConnection()?.close() } catch (_: Exception) {}
        }
    }

    @PostMapping("/digest")
    @ResponseBody
    fun digest(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false) filterOwnerId: Long?,
    ): Map<String, Any> = withDb(target) { db ->
        var playlists = SitePlaylist.loadAll(db, KSS_APP, SAC_APP)
        filterOwnerId?.let { owner -> playlists = playlists.filter { it.ownerId == owner } }
        // Резолвим владельцев одним проходом (email/имя для отображения).
        val owners = playlists.map { it.ownerId }.distinct()
            .associateWith { SiteUser.getSiteUserById(it, db, KSS_APP, SAC_APP) }
        val list = playlists.map { pl ->
            val owner = owners[pl.ownerId]
            mapOf(
                "id" to pl.id,
                "name" to pl.name,
                "favorites" to pl.isFavorites,
                "ownerId" to pl.ownerId,
                "ownerEmail" to (owner?.email ?: ""),
                "ownerName" to (owner?.displayName ?: ""),
                "itemsCount" to SitePlaylistItem.countItems(pl.id, db),
                "continuous" to pl.continuous,
                "repeatMode" to pl.repeatMode,
                "shuffle" to pl.shuffle,
            )
        }
        mapOf("sitePlaylistsDigest" to list)
    }

    @PostMapping("/byId")
    @ResponseBody
    fun byId(@RequestParam id: Long, @RequestParam(required = false) target: String?): Any? =
        withDb(target) { db ->
            val pl = SitePlaylist.getById(id, db, KSS_APP, SAC_APP) ?: return@withDb null
            val owner = SiteUser.getSiteUserById(pl.ownerId, db, KSS_APP, SAC_APP)
            val items = SitePlaylistItem.loadItems(pl.id, db, KSS_APP, SAC_APP)
            val songs = if (items.isEmpty()) emptyMap()
            else Settings.loadListFromDbByIds(items.map { it.songId }.distinct(), db, KSS_APP, SAC_APP)
            mapOf(
                "id" to pl.id,
                "name" to pl.name,
                "favorites" to pl.isFavorites,
                "ownerId" to pl.ownerId,
                "ownerEmail" to (owner?.email ?: ""),
                "ownerName" to (owner?.displayName ?: ""),
                "continuous" to pl.continuous,
                "repeatMode" to pl.repeatMode,
                "shuffle" to pl.shuffle,
                "items" to items.map { item ->
                    val s = songs[item.songId]
                    mapOf(
                        "songId" to item.songId,
                        "position" to item.position,
                        "muted" to item.muted,
                        "songName" to (s?.songName ?: ""),
                        "author" to (s?.author ?: ""),
                        "album" to (s?.album ?: ""),
                        "year" to (s?.year ?: 0),
                    )
                },
            )
        }
}
