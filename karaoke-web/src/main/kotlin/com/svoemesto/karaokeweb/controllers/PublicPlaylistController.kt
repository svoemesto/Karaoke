package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.SitePlaylist
import com.svoemesto.karaokeapp.model.SitePlaylistDto
import com.svoemesto.karaokeapp.model.SitePlaylistItem
import com.svoemesto.karaokeapp.model.SitePlaylistItemDto
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.WORKING_DATABASE
import com.svoemesto.karaokeweb.config.SiteAuthInterceptor
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * «Избранное» и «Плейлисты» пользователя публичного сайта. Весь класс под путём account,
 * поэтому проходит через SiteAuthInterceptor (WebMvcConfig) — "siteUser" в request всегда установлен
 * и не забанен. Данные пишутся в WORKING_DATABASE (боевая БД), синхронизируются на LOCAL отдельно
 * (SyncRegistry: siteplaylists / siteplaylistitems).
 *
 * Лимиты: не-премиум — только «Избранное» (до 100 песен); премиум — до 50 плейлистов (включая
 * «Избранное») × до 500 песен в каждом.
 */
@RestController
@RequestMapping("/api/public/account")
class PublicPlaylistController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
) {
    private val db get() = WORKING_DATABASE

    private fun currentUser(request: HttpServletRequest): SiteUser =
        request.getAttribute(SiteAuthInterceptor.SITE_USER_ATTR) as SiteUser

    private fun loadPlaylists(ownerId: Long) =
        SitePlaylist.loadByUser(ownerId, db, storageService, storageApiClient)

    private fun loadOwnedPlaylist(id: Long, ownerId: Long): SitePlaylist? =
        SitePlaylist.getById(id, db, storageService, storageApiClient)?.takeIf { it.ownerId == ownerId }

    private fun itemLimitFor(user: SiteUser, playlist: SitePlaylist): Int =
        if (playlist.isFavorites && !user.isEffectivePremium) FREE_FAVORITES_LIMIT else PREMIUM_ITEMS_LIMIT

    private fun limitReached(limit: Int) = mapOf(
        "error" to "limit_reached",
        "limit" to limit,
        "benefits" to PREMIUM_BENEFITS,
    )

    private fun premiumRequired() = mapOf(
        "error" to "premium_required",
        "benefits" to PREMIUM_BENEFITS,
    )

    // ---- Список плейлистов (с числом песен) --------------------------------------------------

    @GetMapping("/playlists")
    fun playlists(request: HttpServletRequest): List<SitePlaylistDto> {
        val user = currentUser(request)
        return loadPlaylists(user.id).map { pl ->
            pl.toDTO().copy(itemsCount = SitePlaylistItem.countItems(pl.id, db))
        }
    }

    // ---- Детали плейлиста + песни (с метаданными) --------------------------------------------

    @GetMapping("/playlists/{id}")
    fun playlistDetail(@PathVariable id: Long, request: HttpServletRequest): ResponseEntity<Any> {
        val user = currentUser(request)
        val playlist = loadOwnedPlaylist(id, user.id) ?: return ResponseEntity.notFound().build()
        val items = SitePlaylistItem.loadItems(playlist.id, db, storageService, storageApiClient)
        val songs = if (items.isEmpty()) emptyMap()
        else Settings.loadListFromDbByIds(items.map { it.songId }.distinct(), db, storageService, storageApiClient)
        val itemsDto = items.map { item ->
            val s = songs[item.songId]
            item.toDTO().copy(
                songName = s?.songName ?: "",
                author = s?.author ?: "",
                album = s?.album ?: "",
                year = s?.year ?: 0,
            )
        }
        return ResponseEntity.ok(mapOf(
            "playlist" to playlist.toDTO().copy(itemsCount = itemsDto.size),
            "items" to itemsDto,
        ))
    }

    // ---- Создание / переименование / удаление / настройки ------------------------------------

    @PostMapping("/playlists/create")
    fun createPlaylist(
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) songId: Long?,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val user = currentUser(request)
        if (!user.isEffectivePremium) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(premiumRequired())
        val existing = loadPlaylists(user.id)
        if (existing.size >= PREMIUM_PLAYLIST_LIMIT)
            return ResponseEntity.status(HttpStatus.CONFLICT).body(limitReached(PREMIUM_PLAYLIST_LIMIT))

        val pl = SitePlaylist(database = db, storageService = storageService, storageApiClient = storageApiClient)
        pl.ownerId = user.id
        pl.name = name?.trim()?.takeIf { it.isNotBlank() }
            ?: SitePlaylist.nextDefaultName(user.id, db, storageService, storageApiClient)
        pl.isFavorites = false
        pl.sortOrder = ((existing.maxOfOrNull { it.sortOrder } ?: 0) + 1)
        val created = com.svoemesto.karaokeapp.model.KaraokeDbTable
            .createDbInstance(entity = pl, database = db) as? SitePlaylist
            ?: return ResponseEntity.internalServerError().body(mapOf("error" to "create_failed"))

        if (songId != null) addSongInternal(user, created, songId)
        return ResponseEntity.ok(created.toDTO().copy(itemsCount = SitePlaylistItem.countItems(created.id, db)))
    }

    @PostMapping("/playlists/{id}/rename")
    fun renamePlaylist(@PathVariable id: Long, @RequestParam name: String, request: HttpServletRequest): ResponseEntity<Any> {
        val user = currentUser(request)
        val pl = loadOwnedPlaylist(id, user.id) ?: return ResponseEntity.notFound().build()
        if (pl.isFavorites) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "favorites_readonly"))
        if (name.isBlank()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "name_required"))
        pl.name = name.trim()
        pl.save()
        return ResponseEntity.ok(pl.toDTO())
    }

    @PostMapping("/playlists/{id}/delete")
    fun deletePlaylist(@PathVariable id: Long, request: HttpServletRequest): ResponseEntity<Any> {
        val user = currentUser(request)
        val pl = loadOwnedPlaylist(id, user.id) ?: return ResponseEntity.notFound().build()
        if (pl.isFavorites) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "favorites_readonly"))
        // Элементы удалятся каскадом (ON DELETE CASCADE), но зеркальный sync на LOCAL требует явного
        // удаления строк items — поэтому удаляем их поштучно перед удалением плейлиста (SSE recordDelete).
        SitePlaylistItem.loadItems(pl.id, db, storageService, storageApiClient).forEach {
            SitePlaylistItem.delete(it.id, db)
        }
        SitePlaylist.delete(pl.id, db)
        return ResponseEntity.ok(mapOf("ok" to true))
    }

    @PostMapping("/playlists/{id}/settings")
    fun updateSettings(
        @PathVariable id: Long,
        @RequestParam(required = false) continuous: Boolean?,
        @RequestParam(required = false) repeatMode: String?,
        @RequestParam(required = false) shuffle: Boolean?,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val user = currentUser(request)
        val pl = loadOwnedPlaylist(id, user.id) ?: return ResponseEntity.notFound().build()
        continuous?.let { pl.continuous = it }
        repeatMode?.let { if (it in setOf("none", "one", "all")) pl.repeatMode = it }
        shuffle?.let { pl.shuffle = it }
        pl.save()
        return ResponseEntity.ok(pl.toDTO())
    }

    // ---- Песни в плейлисте: добавить / удалить / порядок / mute ------------------------------

    // Общая логика добавления (переиспользуется createPlaylist с первой песней). Возвращает
    // null при успехе или тело-ошибку (limit_reached).
    private fun addSongInternal(user: SiteUser, playlist: SitePlaylist, songId: Long): Map<String, Any?>? {
        if (SitePlaylistItem.findItem(playlist.id, songId, db, storageService, storageApiClient) != null) return null
        val count = SitePlaylistItem.countItems(playlist.id, db)
        val limit = itemLimitFor(user, playlist)
        if (count >= limit) return limitReached(limit)
        val item = SitePlaylistItem(database = db, storageService = storageService, storageApiClient = storageApiClient)
        item.playlistId = playlist.id
        item.songId = songId
        item.muted = false
        val maxPos = SitePlaylistItem.loadItems(playlist.id, db, storageService, storageApiClient).maxOfOrNull { it.position } ?: -1
        item.position = maxPos + 1
        com.svoemesto.karaokeapp.model.KaraokeDbTable.createDbInstance(entity = item, database = db)
        return null
    }

    @PostMapping("/playlists/{id}/addsong")
    fun addSong(@PathVariable id: Long, @RequestParam songId: Long, request: HttpServletRequest): ResponseEntity<Any> {
        val user = currentUser(request)
        val pl = loadOwnedPlaylist(id, user.id) ?: return ResponseEntity.notFound().build()
        if (!pl.isFavorites && !user.isEffectivePremium)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(premiumRequired())
        val err = addSongInternal(user, pl, songId)
        if (err != null) return ResponseEntity.status(HttpStatus.CONFLICT).body(err)
        return ResponseEntity.ok(mapOf("ok" to true, "count" to SitePlaylistItem.countItems(pl.id, db)))
    }

    @PostMapping("/playlists/{id}/removesong")
    fun removeSong(@PathVariable id: Long, @RequestParam songId: Long, request: HttpServletRequest): ResponseEntity<Any> {
        val user = currentUser(request)
        val pl = loadOwnedPlaylist(id, user.id) ?: return ResponseEntity.notFound().build()
        SitePlaylistItem.findItem(pl.id, songId, db, storageService, storageApiClient)?.let {
            SitePlaylistItem.delete(it.id, db)
        }
        return ResponseEntity.ok(mapOf("ok" to true, "count" to SitePlaylistItem.countItems(pl.id, db)))
    }

    // Новый порядок задаётся CSV song_id в нужной последовательности.
    @PostMapping("/playlists/{id}/reorder")
    fun reorder(@PathVariable id: Long, @RequestParam order: String, request: HttpServletRequest): ResponseEntity<Any> {
        val user = currentUser(request)
        val pl = loadOwnedPlaylist(id, user.id) ?: return ResponseEntity.notFound().build()
        val orderedSongIds = order.split(",").mapNotNull { it.trim().toLongOrNull() }
        val items = SitePlaylistItem.loadItems(pl.id, db, storageService, storageApiClient).associateBy { it.songId }
        orderedSongIds.forEachIndexed { index, songId ->
            items[songId]?.let { item ->
                if (item.position != index.toLong()) {
                    item.position = index.toLong()
                    item.save()
                }
            }
        }
        return ResponseEntity.ok(mapOf("ok" to true))
    }

    @PostMapping("/playlists/{id}/mute")
    fun mute(@PathVariable id: Long, @RequestParam songId: Long, @RequestParam muted: Boolean, request: HttpServletRequest): ResponseEntity<Any> {
        val user = currentUser(request)
        val pl = loadOwnedPlaylist(id, user.id) ?: return ResponseEntity.notFound().build()
        SitePlaylistItem.findItem(pl.id, songId, db, storageService, storageApiClient)?.let {
            it.muted = muted
            it.save()
        }
        return ResponseEntity.ok(mapOf("ok" to true))
    }

    // ---- Избранное: быстрый toggle ------------------------------------------------------------

    @PostMapping("/favorites/toggle")
    fun toggleFavorite(@RequestParam songId: Long, request: HttpServletRequest): ResponseEntity<Any> {
        val user = currentUser(request)
        val fav = SitePlaylist.getOrCreateFavorites(user.id, db, storageService, storageApiClient)
            ?: return ResponseEntity.internalServerError().body(mapOf("error" to "favorites_failed"))
        val existing = SitePlaylistItem.findItem(fav.id, songId, db, storageService, storageApiClient)
        if (existing != null) {
            SitePlaylistItem.delete(existing.id, db)
            return ResponseEntity.ok(mapOf("favorited" to false, "count" to SitePlaylistItem.countItems(fav.id, db)))
        }
        val limit = itemLimitFor(user, fav)
        if (SitePlaylistItem.countItems(fav.id, db) >= limit) {
            return ResponseEntity.ok(mapOf(
                "favorited" to false,
                "limitReached" to true,
                "limit" to limit,
                "benefits" to PREMIUM_BENEFITS,
                "count" to SitePlaylistItem.countItems(fav.id, db),
            ))
        }
        addSongInternal(user, fav, songId)
        return ResponseEntity.ok(mapOf("favorited" to true, "count" to SitePlaylistItem.countItems(fav.id, db)))
    }

    // ---- Батч-членство для иконок в таблицах «Закрома»/«Поиск» --------------------------------

    // На каждую запрошенную песню: favorited (в «Избранном») + playlistIds (все плейлисты с этой
    // песней, включая «Избранное»). Фронт сам решает, что показывать для красной/синей иконки.
    @GetMapping("/playlists/membership")
    fun membership(@RequestParam ids: String, request: HttpServletRequest): ResponseEntity<Any> {
        val user = currentUser(request)
        val songIds = ids.split(",").mapNotNull { it.trim().toLongOrNull() }.distinct()
        val playlists = loadPlaylists(user.id)
        val favId = playlists.firstOrNull { it.isFavorites }?.id
        val songToPlaylists = SitePlaylistItem.songIdsInPlaylists(playlists.map { it.id }, db)
        val items = songIds.associate { songId ->
            val pls = songToPlaylists[songId] ?: emptyList()
            songId.toString() to mapOf(
                "favorited" to (favId != null && favId in pls),
                "playlistIds" to pls.filter { it != favId },
            )
        }
        return ResponseEntity.ok(mapOf("items" to items))
    }

    companion object {
        const val FREE_FAVORITES_LIMIT = 100
        const val PREMIUM_PLAYLIST_LIMIT = 50
        const val PREMIUM_ITEMS_LIMIT = 500

        val PREMIUM_BENEFITS = listOf(
            "Безлимитное «Избранное» — сохраняйте сколько угодно песен",
            "Свои плейлисты: создавайте, переименовывайте, меняйте порядок песен",
            "Онлайн-плеер для всех песен, а не только «в эфире»",
            "Экспорт аудио-дорожек и файла .smkaraoke",
            "Непрерывное воспроизведение, повтор и случайный порядок",
        )
    }
}
