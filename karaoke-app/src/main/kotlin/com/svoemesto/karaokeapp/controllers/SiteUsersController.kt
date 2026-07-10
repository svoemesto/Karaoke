package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

// Админка пользователей ПУБЛИЧНОГО САЙТА (tbl_site_users). Ключевое отличие от всех остальных ручных
// CRUD-контроллеров в проекте:
// параметр target выбирается явно клиентом (webvue3), а не берётся из compile-time WORKING_DATABASE —
// реальные посетители сайта регистрируются на боевой БД сервера, а не в локальной dev-БД, поэтому
// админке нужно уметь смотреть и туда, и туда.
@Controller
@RequestMapping("/api/siteusers")
class SiteUsersController {

    private fun resolveDb(target: String?): KaraokeConnection =
        if (target == "remote") Connection.remote() else Connection.local()

    // resolveDb() создаёт НОВЫЙ объект Connection.local()/remote() на каждый вызов, открывающий
    // собственное физическое JDBC-соединение и кэширующий его в себе; без явного close() оно висит
    // до обрыва и постепенно исчерпывает пул Postgres ("too many clients already"). withDb закрывает
    // соединение сразу после использования. То же самое сделано в StatsController/PublicSettingsController.
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
        // Валидируется до попадания в args["id"] — SiteUser.getWhereList() интерполирует его в SQL
        // без экранирования (числовое сравнение), toLongOrNull() не даёт произвольной строке попасть в запрос.
        @RequestParam(required = false) filterId: String?,
        @RequestParam(required = false) filterEmail: String?,
        @RequestParam(required = false) filterDisplayName: String?,
        @RequestParam(required = false) filterSponsrUid: String?,
        @RequestParam(required = false) filterIsPremium: String?,
        @RequestParam(required = false) filterIsPermanentPremium: String?,
        @RequestParam(required = false) filterIsEffectivePremium: String?,
        @RequestParam(required = false) filterIsBanned: String?,
        @RequestParam(required = false) filterIsEditor: String?,
    ): Map<String, Any> = withDb(target) { db ->
        val args: MutableMap<String, String> = mutableMapOf()
        filterId?.toLongOrNull()?.let { args["id"] = it.toString() }
        filterEmail?.let { if (it != "") args["email"] = it }
        filterDisplayName?.let { if (it != "") args["displayName"] = it }
        filterSponsrUid?.let { if (it != "") args["sponsrUid"] = it }
        filterIsPremium?.let { if (it != "") args["isPremium"] = it }
        filterIsPermanentPremium?.let { if (it != "") args["isPermanentPremium"] = it }
        filterIsEffectivePremium?.let { if (it != "") args["isEffectivePremium"] = it }
        filterIsBanned?.let { if (it != "") args["isBanned"] = it }
        filterIsEditor?.let { if (it != "") args["isEditor"] = it }

        val list = SiteUser.loadList(
            whereArgs = args,
            database = db,
            storageService = KSS_APP,
            storageApiClient = SAC_APP,
        ).map { it.toDTO() }
        mapOf("siteUsersDigest" to list)
    }

    @PostMapping("/byId")
    @ResponseBody
    fun byId(@RequestParam id: Long, @RequestParam(required = false) target: String?): Any? =
        withDb(target) { db -> SiteUser.getSiteUserById(id, db, KSS_APP, SAC_APP)?.toDTO() }

    @PostMapping("/update")
    @ResponseBody
    fun update(
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false) displayName: String?,
        @RequestParam(required = false) sponsrUid: String?,
        // Ручная простановка премиум-статуса — временная замена автоматической Sponsr-сверки
        // (см. раздел 7 плана: пока не реализован импорт Excel-выгрузки, админ выставляет вручную).
        @RequestParam(required = false) isPremium: Boolean?,
        // Независимый флаг "вечного" премиума — делает пользователя премиумным, даже если isPremium
        // не выставлен (например, автоматическая Sponsr-сверка сбросит isPremium в будущем).
        @RequestParam(required = false) isPermanentPremium: Boolean?,
        // Роль "редактор" — доступ к онлайн-редактору разметки (/account/editor) на публичном сайте.
        @RequestParam(required = false) isEditor: Boolean?,
        // Персональные лимиты (0 = дефолт). Перекрывают дефолты в PublicPlaylistController.
        @RequestParam(required = false) maxFavorites: Int?,
        @RequestParam(required = false) maxPlaylists: Int?,
        @RequestParam(required = false) maxPlaylistItems: Int?,
        // Постоянная скидка (%) — вручную, суммируется поверх любой акции (PriceService).
        @RequestParam(required = false) personalDiscountPercent: Double?,
    ): Long = withDb(target) { db ->
        SiteUser.getSiteUserById(id, db, KSS_APP, SAC_APP)?.let { user ->
            displayName?.let { user.displayName = it }
            sponsrUid?.let { user.sponsrUid = it }
            isPremium?.let { user.isPremium = it }
            isPermanentPremium?.let { user.isPermanentPremium = it }
            isEditor?.let { user.isEditor = it }
            maxFavorites?.let { user.maxFavorites = it }
            maxPlaylists?.let { user.maxPlaylists = it }
            maxPlaylistItems?.let { user.maxPlaylistItems = it }
            personalDiscountPercent?.let { user.personalDiscountPercent = it.coerceIn(0.0, 100.0) }
            user.save()
            user.id
        } ?: 0L
    }

    @PostMapping("/ban")
    @ResponseBody
    fun ban(
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
        @RequestParam reason: String,
    ): Boolean = withDb(target) { db ->
        SiteUser.getSiteUserById(id, db, KSS_APP, SAC_APP)?.let { user ->
            user.isBanned = true
            user.banReason = reason
            user.save()
            true
        } ?: false
    }

    @PostMapping("/unban")
    @ResponseBody
    fun unban(@RequestParam id: Long, @RequestParam(required = false) target: String?): Boolean =
        withDb(target) { db ->
            SiteUser.getSiteUserById(id, db, KSS_APP, SAC_APP)?.let { user ->
                user.isBanned = false
                user.banReason = ""
                user.save()
                true
            } ?: false
        }

    @PostMapping("/delete")
    @ResponseBody
    fun delete(@RequestParam id: Long, @RequestParam(required = false) target: String?): Boolean =
        withDb(target) { db -> SiteUser.deleteSiteUser(id, db) }
}
