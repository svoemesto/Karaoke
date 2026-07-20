package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeweb.WORKING_DATABASE
import org.springframework.stereotype.Service

// Ключи Yandex SmartCaptcha живут в tbl_public_settings (Postgres), не в файловых KaraokeProperties
// karaoke-app — karaoke-app на боевом сервере вообще не разворачивается (только karaoke-web,
// karaoke-public, storage и БД), поэтому HTTP-поход за настройками на karaoke-app в проде был бы
// обращением в никуда. tbl_public_settings, напротив, лежит в той же Postgres, к которой
// karaoke-web и так подключён через WORKING_DATABASE (на сервере это сервер­ная БД, локально —
// локальная), поэтому читаем настройки прямым JDBC-запросом.
@Service
class CaptchaConfigService {
    private data class CachedKeys(
        val clientKey: String,
        val serverKey: String,
        val fetchedAt: Long,
    )

    private val cacheTtlMs = 5 * 60 * 1000L

    @Volatile
    private var cache: CachedKeys? = null

    private fun fetchValue(key: String): String {
        return try {
            val connection = WORKING_DATABASE.getConnection() ?: return ""
            val ps = connection.prepareStatement("SELECT value FROM tbl_public_settings WHERE key = ?")
            ps.setString(1, key)
            val rs = ps.executeQuery()
            val value = if (rs.next()) rs.getString("value") ?: "" else ""
            rs.close()
            ps.close()
            value
        } catch (e: Exception) {
            println("Не удалось прочитать настройку «$key» из tbl_public_settings: ${e.message}")
            ""
        }
    }

    private fun refreshIfNeeded(): CachedKeys {
        val current = cache
        val now = System.currentTimeMillis()
        if (current != null && now - current.fetchedAt < cacheTtlMs) return current
        val fresh =
            CachedKeys(
                clientKey = fetchValue("yandexSmartCaptchaClientKey"),
                serverKey = fetchValue("yandexSmartCaptchaServerKey"),
                fetchedAt = now,
            )
        cache = fresh
        return fresh
    }

    fun getClientKey(): String = refreshIfNeeded().clientKey

    fun getServerKey(): String = refreshIfNeeded().serverKey
}
