package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.SPONSR_AUTH_STATE_PATH
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.SponsrSyncService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.io.File

// Админка Sponsr-синхронизации (webvue3, панель Sponsr-sync) — ручной импорт списка подписчиков и
// запуск экспериментального скрейпинга (см. SponsrSyncService). Target-aware, как SiteUsersController —
// реальные site-users живут на прод-БД (target=remote).
@Controller
@RequestMapping("/api/sponsrsync")
class SponsrSyncController {
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

    @PostMapping("/status")
    @ResponseBody
    fun status(): Map<String, Any> =
        mapOf(
            "subscribersUrlConfigured" to Karaoke.sponsrSubscribersUrl.isNotBlank(),
            "sessionSaved" to File(SPONSR_AUTH_STATE_PATH).exists(),
            "syncWindowDays" to Karaoke.sponsrSyncWindowDays,
        )

    // identifiers — список email/sponsr_uid, по одному на строку (или через запятую).
    @PostMapping("/import")
    @ResponseBody
    fun import(
        @RequestParam identifiers: String,
        @RequestParam(required = false) target: String?,
    ): Map<String, Any?> =
        withDb(target) { db ->
            val list = identifiers.split(Regex("[\\n,;]")).map { it.trim() }.filter { it.isNotBlank() }
            val result = SponsrSyncService.importFromList(list, db, KSS_APP, SAC_APP)
            mapOf(
                "ok" to result.ok,
                "foundIdentifiers" to result.foundIdentifiers,
                "matchedUsers" to result.matchedUsers,
                "messages" to result.messages,
            )
        }

    @PostMapping("/run")
    @ResponseBody
    fun run(
        @RequestParam(required = false) target: String?,
    ): Map<String, Any?> =
        withDb(target) { db ->
            val result = SponsrSyncService.syncViaScraping(db, KSS_APP, SAC_APP)
            mapOf(
                "ok" to result.ok,
                "foundIdentifiers" to result.foundIdentifiers,
                "matchedUsers" to result.matchedUsers,
                "messages" to result.messages,
            )
        }
}
