package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

data class PublicSettingDto(
    val key: String,
    val value: String,
    val description: String,
)

// Небольшая key/value таблица tbl_public_settings — настройки, нужные сервисам, которые реально
// работают на боевом сервере (сейчас — karaoke-web, ключи Yandex SmartCaptcha). В отличие от
// ~150 файловых настроек KaraokeProperties (локальный файл, есть только на машине администратора —
// karaoke-app на сервере не разворачивается), эта таблица в Postgres, поэтому доступна и
// локально, и на сервере через тот же паттерн target=local|remote, что и SiteUsersController.
@Controller
@RequestMapping("/api/publicsettings")
class PublicSettingsController {
    private fun resolveDb(target: String?): KaraokeConnection = if (target == "remote") Connection.remote() else Connection.local()

    // resolveDb() создаёт НОВЫЙ объект Connection.local()/remote() на каждый вызов, открывающий
    // собственное физическое JDBC-соединение и кэширующий его в себе; без явного close() оно висит
    // до обрыва и постепенно исчерпывает пул Postgres ("too many clients already"). withDb закрывает
    // соединение сразу после использования. То же самое сделано в StatsController/SiteUsersController.
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
    ): Map<String, Any> =
        withDb(target) { db ->
            val connection =
                db.getConnection()
                    ?: return@withDb mapOf("publicSettingsDigest" to emptyList<PublicSettingDto>())
            val list = mutableListOf<PublicSettingDto>()
            val ps = connection.prepareStatement("SELECT key, value, description FROM tbl_public_settings ORDER BY key")
            val rs = ps.executeQuery()
            while (rs.next()) {
                list.add(PublicSettingDto(rs.getString("key"), rs.getString("value"), rs.getString("description")))
            }
            rs.close()
            ps.close()
            mapOf("publicSettingsDigest" to list)
        }

    @PostMapping("/update")
    @ResponseBody
    fun update(
        @RequestParam key: String,
        @RequestParam value: String,
        @RequestParam(required = false) target: String?,
    ): Boolean =
        withDb(target) { db ->
            val connection = db.getConnection() ?: return@withDb false
            val ps = connection.prepareStatement("UPDATE tbl_public_settings SET value = ?, last_update = now() WHERE key = ?")
            ps.setString(1, value)
            ps.setString(2, key)
            val updated = ps.executeUpdate()
            ps.close()
            updated > 0
        }
}
