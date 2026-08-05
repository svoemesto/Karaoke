package com.svoemesto.karaokeweb.controllers

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Контроллер VK OAuth (Authorization Code Flow) для разворачивания на проде через
 * `karaoke-web`. Почему здесь, а не в `karaoke-app`:
 *
 * - VK редиректит на `Redirect URI` ДО того, как наш сервер сможет перехватить код —
 *   URL должен быть **публичным HTTPS** (т.е. на проде, а не на admin-машине `nsa-i9`).
 * - `karaoke-web` развёрнут на проде и доступен как `https://sm-karaoke.ru/api/public/...`,
 *   значит он подходит для OAuth callback.
 * - `karaoke-app` (на `nsa-i9`) не доступен снаружи по URL, поэтому не подходит.
 *
 * Flow:
 * 1. Админ запрашивает `GET /api/public/utils/vkOAuthCodeUrl` — получает URL.
 * 2. Открывает URL в браузере → VK показывает форму разрешений → пользователь подтверждает.
 * 3. VK редиректит на `https://sm-karaoke.ru/api/public/utils/vkOAuthCallback?code=XXX`.
 * 4. Этот endpoint обменивает code → access_token через
 *    `POST https://oauth.vk.ru/access_token` с client_secret (server-side).
 * 5. Возвращает HTML-страницу с **готовым токеном + curl-командой** для его сохранения
 *    на admin-машине `karaoke-app` через `POST /api/utils/vkSaveUserToken`.
 *
 * Почему HTML с curl, а не автоматический POST:
 * - `karaoke-web` (прод) не имеет прямого доступа к admin-машине `nsa-i9` (firewall).
 * - Пользователь вручную копирует curl-команду из HTML → админ-машина сохранит токен.
 * - Это безопасный fallback для сегрегированных сред.
 */
@RestController
class PublicVkAuthController {
    private val log = LoggerFactory.getLogger(PublicVkAuthController::class.java)

    @Value("\${vk.app-id:0}")
    private var appId: Long = 0L

    @Value("\${vk.redirect-uri:}")
    private var redirectUri: String = ""

    @Value("\${vk.client-secret:}")
    private var clientSecret: String = ""

    @Value("\${vk.admin-api-url:http://nsa-i9:8898}")
    private var adminApiUrl: String = ""

    /**
     * DEPRECATED (specs/151-vk-id-personal-token). Используйте `/api/public/utils/vkIdOAuthUrl`.
     *
     * Старый `oauth.vk.ru` flow заблокирован VK (05.08.2026 — все варианты
     * `/oauth.vk.ru/authorize` возвращают `Security Error`). Возвращаем HTTP 410 Gone.
     */
    @GetMapping("/api/public/utils/vkOAuthCodeUrl", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getVkOAuthCodeUrl(): Map<String, Any> {
        log.warn("DEPRECATED endpoint /api/public/utils/vkOAuthCodeUrl — use /api/public/utils/vkIdOAuthUrl instead")
        return mapOf(
            "deprecated" to true as Any,
            "use" to "/api/public/utils/vkIdOAuthUrl" as Any,
            "message" to "Этот endpoint устарел (oauth.vk.ru заблокирован). Используйте /api/public/utils/vkIdOAuthUrl (VK ID)." as Any,
        )
    }

    /**
     * DEPRECATED (specs/151-vk-id-personal-token). Используйте `/api/public/utils/vkIdOAuthCallback`.
     *
     * Старый `oauth.vk.ru` callback заблокирован VK (05.08.2026). Возвращаем HTTP 410 Gone.
     */
    @GetMapping(
        "/api/public/utils/vkOAuthCallback",
        produces = [MediaType.TEXT_HTML_VALUE + "; charset=UTF-8"],
    )
    fun vkOAuthCallback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
    ): String {
        log.warn("DEPRECATED endpoint /api/public/utils/vkOAuthCallback — use /api/public/utils/vkIdOAuthCallback instead")
        return "<html><body style=\"font-family:sans-serif;padding:40px;max-width:900px\">" +
            "<h2>DEPRECATED</h2>" +
            "<p>Этот endpoint устарел (oauth.vk.ru заблокирован).</p>" +
            "<p>Используйте <code>/api/public/utils/vkIdOAuthUrl</code> для получения нового токена через VK ID.</p>" +
            "<p>Подробнее — <code>specs/151-vk-id-personal-token</code>.</p>" +
            "</body></html>"
    }

    /**
     * Пытается отправить токен на admin-машину через HTTP POST. Если
     * `adminApiUrl` указывает на доступный сервис — токен сохранится
     * автоматически. Иначе вернёт `<false, errorMessage>`.
     */
    private fun saveTokenOnAdminMachine(accessToken: String): Pair<Boolean, String> {
        if (adminApiUrl.isBlank()) {
            return Pair(false, "adminApiUrl пуст")
        }
        val reachable = isReachable(adminApiUrl)
        if (!reachable) {
            return Pair(false, "admin-машина недоступна по $adminApiUrl")
        }
        return try {
            val url = URL("$adminApiUrl/api/utils/vkSaveUserToken")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body = "token=" + URLEncoder.encode(accessToken, "UTF-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            log.info("admin save response: {}...", resp.take(100))
            val success =
                resp.contains("\"success\":true") ||
                    resp.contains("\"success\": true") ||
                    resp.contains("Токен сохранён")
            if (success) {
                Pair(true, "Токен сохранён в Karaoke.properties")
            } else {
                Pair(false, "admin endpoint вернул: " + resp.take(200))
            }
        } catch (e: Exception) {
            Pair(false, "admin машина недоступна: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    private fun isReachable(baseUrl: String): Boolean =
        try {
            val conn = URL(baseUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 2_000
            conn.readTimeout = 2_000
            conn.requestMethod = "GET"
            conn.connect()
            conn.disconnect()
            true
        } catch (_: Exception) {
            false
        }

    private fun escapeHtml(s: String): String =
        s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
