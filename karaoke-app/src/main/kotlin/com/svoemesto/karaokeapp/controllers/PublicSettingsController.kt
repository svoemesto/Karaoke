package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

data class PublicSettingDto(val key: String, val value: String, val description: String)

// Небольшая key/value таблица tbl_public_settings — настройки, нужные сервисам, которые реально
// работают на боевом сервере (сейчас — karaoke-web, ключи Yandex SmartCaptcha). В отличие от
// ~150 файловых настроек KaraokeProperties (локальный файл, есть только на машине администратора —
// karaoke-app на сервере не разворачивается), эта таблица в Postgres, поэтому доступна и
// локально, и на сервере через тот же паттерн target=local|remote, что и SiteUsersController.
@Controller
@RequestMapping("/api/publicsettings")
class PublicSettingsController {

    private fun resolveDb(target: String?): KaraokeConnection =
        if (target == "remote") Connection.remote() else Connection.local()

    @PostMapping("/digest")
    @ResponseBody
    fun digest(@RequestParam(required = false) target: String?): Map<String, Any> {
        val connection = resolveDb(target).getConnection()
            ?: return mapOf("publicSettingsDigest" to emptyList<PublicSettingDto>())
        val list = mutableListOf<PublicSettingDto>()
        val ps = connection.prepareStatement("SELECT key, value, description FROM tbl_public_settings ORDER BY key")
        val rs = ps.executeQuery()
        while (rs.next()) {
            list.add(PublicSettingDto(rs.getString("key"), rs.getString("value"), rs.getString("description")))
        }
        rs.close()
        ps.close()
        return mapOf("publicSettingsDigest" to list)
    }

    @PostMapping("/update")
    @ResponseBody
    fun update(
        @RequestParam key: String,
        @RequestParam value: String,
        @RequestParam(required = false) target: String?,
    ): Boolean {
        val connection = resolveDb(target).getConnection() ?: return false
        val ps = connection.prepareStatement("UPDATE tbl_public_settings SET value = ?, last_update = now() WHERE key = ?")
        ps.setString(1, value)
        ps.setString(2, key)
        val updated = ps.executeUpdate()
        ps.close()
        return updated > 0
    }
}
