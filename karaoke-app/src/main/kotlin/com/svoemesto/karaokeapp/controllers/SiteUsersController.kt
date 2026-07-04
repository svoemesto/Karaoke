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

// Админка пользователей ПУБЛИЧНОГО САЙТА (tbl_site_users) — не путать с UsersController (tbl_users,
// админские логины webvue3). Ключевое отличие от всех остальных ручных CRUD-контроллеров в проекте:
// параметр target выбирается явно клиентом (webvue3), а не берётся из compile-time WORKING_DATABASE —
// реальные посетители сайта регистрируются на боевой БД сервера, а не в локальной dev-БД, поэтому
// админке нужно уметь смотреть и туда, и туда.
@Controller
@RequestMapping("/api/siteusers")
class SiteUsersController {

    private fun resolveDb(target: String?): KaraokeConnection =
        if (target == "remote") Connection.remote() else Connection.local()

    @PostMapping("/digest")
    @ResponseBody
    fun digest(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false) filterEmail: String?,
        @RequestParam(required = false) filterDisplayName: String?,
        @RequestParam(required = false) filterIsBanned: String?,
    ): Map<String, Any> {
        val db = resolveDb(target)
        val args: MutableMap<String, String> = mutableMapOf()
        filterEmail?.let { if (it != "") args["email"] = it }
        filterDisplayName?.let { if (it != "") args["displayName"] = it }
        filterIsBanned?.let { if (it != "") args["isBanned"] = it }

        val list = SiteUser.loadList(
            whereArgs = args,
            database = db,
            storageService = KSS_APP,
            storageApiClient = SAC_APP,
        ).map { it.toDTO() }
        return mapOf("siteUsersDigest" to list)
    }

    @PostMapping("/byId")
    @ResponseBody
    fun byId(@RequestParam id: Long, @RequestParam(required = false) target: String?): Any? {
        val db = resolveDb(target)
        return SiteUser.getSiteUserById(id, db, KSS_APP, SAC_APP)?.toDTO()
    }

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
    ): Long {
        val db = resolveDb(target)
        SiteUser.getSiteUserById(id, db, KSS_APP, SAC_APP)?.let { user ->
            displayName?.let { user.displayName = it }
            sponsrUid?.let { user.sponsrUid = it }
            isPremium?.let { user.isPremium = it }
            isPermanentPremium?.let { user.isPermanentPremium = it }
            user.save()
            return user.id
        }
        return 0L
    }

    @PostMapping("/ban")
    @ResponseBody
    fun ban(
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
        @RequestParam reason: String,
    ): Boolean {
        val db = resolveDb(target)
        return SiteUser.getSiteUserById(id, db, KSS_APP, SAC_APP)?.let { user ->
            user.isBanned = true
            user.banReason = reason
            user.save()
            true
        } ?: false
    }

    @PostMapping("/unban")
    @ResponseBody
    fun unban(@RequestParam id: Long, @RequestParam(required = false) target: String?): Boolean {
        val db = resolveDb(target)
        return SiteUser.getSiteUserById(id, db, KSS_APP, SAC_APP)?.let { user ->
            user.isBanned = false
            user.banReason = ""
            user.save()
            true
        } ?: false
    }

    @PostMapping("/delete")
    @ResponseBody
    fun delete(@RequestParam id: Long, @RequestParam(required = false) target: String?): Boolean {
        val db = resolveDb(target)
        return SiteUser.deleteSiteUser(id, db)
    }
}
