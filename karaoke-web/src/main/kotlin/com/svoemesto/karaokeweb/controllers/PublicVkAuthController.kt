package com.svoemesto.karaokeweb.controllers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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

    /** Готовая ссылка для авторизации через Authorization Code Flow. */
    @GetMapping("/api/public/utils/vkOAuthCodeUrl", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getVkOAuthCodeUrl(): Map<String, Any> {
        if (appId <= 0) {
            return mapOf(
                "success" to false as Any,
                "error" to "vk.app-id is empty in application.yml",
            )
        }
        if (redirectUri.isBlank()) {
            return mapOf(
                "success" to false as Any,
                "error" to "vk.redirect-uri is empty in application.yml",
            )
        }
        if (clientSecret.isBlank()) {
            return mapOf(
                "success" to false as Any,
                "error" to "vk.client-secret is empty in application.yml — для Code Flow (Web-приложение) он обязателен",
            )
        }
        val state = (System.currentTimeMillis() / 1_000_000).toString()
        val scopes = "video,photos,wall,offline"
        val encodedRedirect = URLEncoder.encode(redirectUri, "UTF-8")
        val url =
            "https://oauth.vk.ru/authorize?client_id=$appId&redirect_uri=$encodedRedirect" +
                "&scope=$scopes&response_type=code&state=$state"
        val instructions =
            listOf(
                "1. Endpoint /api/public/utils/vkOAuthCallback доступен на проде (sm-karaoke.ru).",
                "2. Откройте url в браузере от лица владельца группы svoemestokaraoke.",
                "3. Подтвердите все scopes (video, photos, wall, offline).",
                "4. VK редиректит на /api/public/utils/vkOAuthCallback?code=XXX — endpoint обменивает на токен и возвращает HTML-страницу с готовой curl-командой.",
                "5. Скопируйте curl с HTML-страницы и выполните на admin-машине (nsa-i9) — токен сохранится в Karaoke.properties.",
            )
        return mapOf(
            "success" to true as Any,
            "url" to url as Any,
            "redirectUri" to redirectUri as Any,
            "instructions" to instructions,
        )
    }

    /**
     * Callback от VK — обменивает code на access_token и возвращает HTML с готовой curl-командой.
     *
     * Если admin-машина доступна через `vk.admin-api-url` (например, `http://nsa-i9:8898`),
     * то автоматически POST-ит токен туда. Иначе возвращает HTML, чтобы пользователь
     * скопировал команду и выполнил сам.
     */
    @GetMapping(
        "/api/public/utils/vkOAuthCallback",
        produces = [MediaType.TEXT_HTML_VALUE + "; charset=UTF-8"],
    )
    fun vkOAuthCallback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
    ): String {
        val htmlPrefix = "<html><body style=\"font-family:sans-serif;padding:40px;max-width:900px\">"
        if (!error.isNullOrBlank()) {
            log.warn("VK OAuth callback error: {}", error)
            return htmlPrefix +
                "<h2>❌ Ошибка авторизации VK</h2>" +
                "<p>VK вернул: <b>" + error.replace("<", "") + "</b></p>" +
                "<p>Закройте вкладку и попробуйте снова.</p>" +
                "</body></html>"
        }
        if (code.isNullOrBlank()) {
            return htmlPrefix +
                "<h2>❌ Не получен code от VK</h2>" +
                "<p>Откройте эту страницу через /api/public/utils/vkOAuthCodeUrl, " +
                "а не напрямую.</p>" +
                "</body></html>"
        }
        if (appId <= 0 || redirectUri.isBlank() || clientSecret.isBlank()) {
            return htmlPrefix +
                "<h2>❌ Не настроены vk.app-id / vk.redirect-uri / vk.client-secret</h2>" +
                "<p>Задайте их в application.yml `karaoke-web` и пересоберите.</p>" +
                "</body></html>"
        }
        log.info("VK OAuth callback: exchanging code (length={}) for token...", code.length)
        val params =
            buildString {
                append("client_id=").append(appId)
                append("&client_secret=").append(URLEncoder.encode(clientSecret, "UTF-8"))
                append("&redirect_uri=").append(URLEncoder.encode(redirectUri, "UTF-8"))
                append("&code=").append(URLEncoder.encode(code, "UTF-8"))
            }
        val responseJson: String =
            try {
                val conn = URL("https://oauth.vk.ru/access_token").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(params.toByteArray(Charsets.UTF_8)) }
                conn.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                log.error("VK access_token POST failed", e)
                return htmlPrefix +
                    "<h2>❌ Ошибка обмена code → token</h2>" +
                    "<pre>" + (e.message ?: e.javaClass.simpleName) + "</pre>" +
                    "</body></html>"
            }
        log.info("VK access_token response: {}...", responseJson.take(200))
        val parsed: JsonNode =
            try {
                ObjectMapper().readTree(responseJson)
            } catch (e: Exception) {
                log.error("Cannot parse VK token response: {}", responseJson)
                return htmlPrefix +
                    "<h2>❌ Не удалось разобрать ответ VK</h2>" +
                    "<pre>" + responseJson.take(800) + "</pre>" +
                    "</body></html>"
            }
        val errorNode = parsed.get("error")?.asText()
        if (!errorNode.isNullOrBlank()) {
            val errorDescription = parsed.get("error_description")?.asText() ?: "?"
            log.error("VK OAuth error: {} {}", errorNode, errorDescription)
            return htmlPrefix +
                "<h2>❌ VK отверг code</h2>" +
                "<p>error: <b>" + errorNode + "</b></p>" +
                "<p>error_description: <b>" + errorDescription + "</b></p>" +
                "</body></html>"
        }
        val accessToken = parsed.get("access_token")?.asText()
        if (accessToken.isNullOrBlank()) {
            return htmlPrefix +
                "<h2>❌ VK не вернул access_token</h2>" +
                "<pre>" + responseJson.take(800) + "</pre>" +
                "</body></html>"
        }
        val userId = parsed.get("user_id")?.asText() ?: "?"
        val expiresIn = parsed.get("expires_in")?.asText() ?: "?"
        log.info(
            "VK OAuth SUCCESS: user_id={}, access_token length={}",
            userId,
            accessToken.length,
        )
        val adminSaveResult = saveTokenOnAdminMachine(accessToken)
        val autoSaved = adminSaveResult.first
        val autoSaveMessage = adminSaveResult.second
        val manualCurl =
            "# Скопируйте и выполните на admin-машине (nsa-i9):\n" +
                "TOKEN='" + escapeHtml(accessToken) + "'\n" +
                "curl -s -X POST \"$adminApiUrl/api/utils/vkSaveUserToken\" \\\n" +
                "     --data-urlencode \"token=\$TOKEN\"\n"
        val autoSavedBadge =
            if (autoSaved) {
                "<span style='background:#d4edda;padding:2px 8px;border-radius:4px'>" +
                    "✅ авто-сохранено в Karaoke.properties admin-машины</span>"
            } else {
                "<span style='background:#fff3cd;padding:2px 8px;border-radius:4px'>" +
                    "⚠️ admin-машина недоступна — сохраните вручную</span>"
            }
        val headingText = if (autoSaved) "Готово к публикации!" else "Шаг 2: сохраните токен на admin-машине"
        val manualBlock =
            if (!autoSaved) {
                "<p>Скопируйте эту команду и выполните на admin-машине <code>" +
                    escapeHtml(adminApiUrl) + "</code>:</p>" +
                    "<pre style=\"background:#f4f4f4;padding:12px;border-radius:4px;overflow:auto\">" +
                    escapeHtml(manualCurl) + "</pre>"
            } else {
                "<p>" + escapeHtml(autoSaveMessage) + " Можно закрыть эту вкладку.</p>"
            }
        return htmlPrefix +
            "<h2 style=\"color:green\">✅ Токен VK получен</h2>" +
            "<p><b>access_token</b> (первые 30 символов): <code>" +
            escapeHtml(accessToken.take(30)) + "...</code></p>" +
            "<p><b>user_id:</b> <code>" + escapeHtml(userId) + "</code></p>" +
            "<p><b>expires_in:</b> <code>" + escapeHtml(expiresIn) + "</code> сек</p>" +
            "<p>Состояние: " + autoSavedBadge + "</p>" +
            "<hr>" +
            "<h3>" + headingText + "</h3>" +
            manualBlock +
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
