package com.svoemesto.karaokeweb.controllers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeweb.util.VkIdPkceUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Контроллер VK ID OAuth (Authorization Code Flow + PKCE) для разворачивания на проде
 * через `karaoke-web`.
 *
 * Заменяет [PublicVkAuthController] (старый `oauth.vk.ru` flow), который был заблокирован
 * VK (`Security Error`). Использует новый сервис авторизации VK ID (`id.vk.ru`).
 *
 * Почему здесь, а не в `karaoke-app`:
 * - VK ID редиректит на `redirect_uri` ДО того, как наш сервер сможет перехватить код —
 *   URL должен быть **публичным HTTPS** (т.е. на проде, а не на admin-машине `nsa-i9`).
 * - `karaoke-web` развёрнут на проде и доступен как `https://sm-karaoke.ru/api/public/...`.
 * - `karaoke-app` (на `nsa-i9`) не доступен снаружи по URL — не подходит для callback.
 *
 * Flow:
 * 1. Админ запрашивает `GET /api/public/utils/vkIdOAuthUrl` — получает URL авторизации.
 * 2. Открывает URL в браузере → VK ID показывает форму разрешений → пользователь подтверждает.
 * 3. VK ID редиректит на `https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback?code=XXX`.
 * 4. Этот endpoint проверяет `state` (CSRF), обменивает `code → tokens` через
 *    `POST https://id.vk.ru/oauth2/token` (с `client_secret` + `code_verifier` для PKCE).
 * 5. Отправляет токены POST-ом на admin-машину `POST $adminApiUrl/api/utils/vkIdSaveTokens`.
 * 6. Возвращает HTML-страницу с подтверждением (или curl-командой для ручного сохранения).
 *
 * Хранение `code_verifier` между `/authorize` и `/callback` — in-memory ConcurrentHashMap
 * с TTL 10 минут (см. [pendingAuths]). Достаточно для одной инстанции `karaoke-web`;
 * для нескольких инстансов потребуется distributed cache (отдельная задача).
 *
 * @see specs/151-vk-id-personal-token/spec.md
 * @see docs/features/vk-id-auth.md (FR-001, FR-002, FR-005)
 */
@RestController
class PublicVkIdAuthController {
    private val log = LoggerFactory.getLogger(PublicVkIdAuthController::class.java)

    @Value("\${vk.id.client-id:0}")
    private var clientId: Long = 0L

    @Value("\${vk.id.client-secret:}")
    private var clientSecret: String = ""

    @Value("\${vk.id.redirect-uri:}")
    private var redirectUri: String = ""

    @Value("\${vk.id.admin-api-url:http://nsa-i9:8898}")
    private var adminApiUrl: String = ""

    /** TTL для pending-значений PKCE (code_verifier + state), 10 минут. */
    private val pendingTtlSeconds = 600L

    /** In-memory хранилище code_verifier + state между /authorize и /callback. */
    private val pendingAuths = ConcurrentHashMap<String, PendingAuth>()

    private data class PendingAuth(
        val codeVerifier: String,
        val createdAt: Instant,
    )

    /**
     * Готовая ссылка для авторизации через VK ID Authorization Code Flow + PKCE.
     *
     * Генерирует случайные `code_verifier` и `state`, сохраняет `code_verifier` в
     * [pendingAuths] с ключом `state` (TTL 10 минут), возвращает URL для авторизации.
     *
     * @return JSON `{success, url, scopes, clientId, redirectUri, instructions}` или
     *   `{success: false, error}` если настройки не заданы.
     */
    @GetMapping("/api/public/utils/vkIdOAuthUrl", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getVkIdOAuthUrl(): Map<String, Any> {
        if (clientId <= 0) {
            return mapOf(
                "success" to false as Any,
                "error" to "vk.id.client-id is empty in application.yml — задайте VK_ID_CLIENT_ID env" as Any,
            )
        }
        if (redirectUri.isBlank()) {
            return mapOf(
                "success" to false as Any,
                "error" to "vk.id.redirect-uri is empty in application.yml" as Any,
            )
        }
        if (clientSecret.isBlank()) {
            return mapOf(
                "success" to false as Any,
                "error" to "vk.id.client-secret is empty — для Code Flow (Web-приложение) он обязателен" as Any,
            )
        }
        val codeVerifier = VkIdPkceUtils.generateCodeVerifier()
        val codeChallenge = VkIdPkceUtils.generateCodeChallenge(codeVerifier)
        val state = VkIdPkceUtils.generateState()

        // Сохраняем code_verifier по ключу state для последующей проверки в callback.
        // Чистим протухшие записи (lazy cleanup при следующем запросе).
        cleanExpiredPending()
        pendingAuths[state] = PendingAuth(codeVerifier, Instant.now())

        val scopes = "vkid.personal_info photos wall video"
        val encodedRedirect = URLEncoder.encode(redirectUri, "UTF-8")
        val encodedScopes = URLEncoder.encode(scopes, "UTF-8").replace("+", "%20")
        val url =
            "https://id.vk.ru/authorize?client_id=$clientId&redirect_uri=$encodedRedirect" +
                "&scope=$encodedScopes&response_type=code&state=$state" +
                "&code_challenge=$codeChallenge&code_challenge_method=S256"
        val instructions =
            listOf(
                "1. Endpoint /api/public/utils/vkIdOAuthCallback доступен на проде (sm-karaoke.ru).",
                "2. Откройте url в браузере от лица владельца группы svoemestokaraoke.",
                "3. Подтвердите все scopes (vkid.personal_info, photos, wall, video).",
                "4. VK ID редиректит на /api/public/utils/vkIdOAuthCallback?code=XXX — endpoint обменивает на токен и отправляет на admin-машину.",
                "5. Токены сохранятся в Karaoke.properties admin-машины автоматически (или HTML покажет curl для ручного сохранения).",
            )
        return mapOf(
            "success" to true as Any,
            "url" to url as Any,
            "scopes" to scopes as Any,
            "clientId" to clientId as Any,
            "redirectUri" to redirectUri as Any,
            "instructions" to instructions,
        )
    }

    /**
     * Callback от VK ID — обменивает `code` на `access_token` + `refresh_token`,
     * сохраняет на admin-машине, возвращает HTML с подтверждением.
     *
     * Проверяет `state` (CSRF): должен совпадать с ключом в [pendingAuths]. Получает
     * `code_verifier` из pending и использует его в `/oauth2/token` (PKCE).
     *
     * @param code authorization code от VK ID.
     * @param state CSRF state (должен совпадать с сохранённым).
     * @param error ошибка от VK ID (например, `access_denied`).
     * @return HTML-страница с подтверждением или описанием ошибки.
     */
    @GetMapping(
        "/api/public/utils/vkIdOAuthCallback",
        produces = [MediaType.TEXT_HTML_VALUE + "; charset=UTF-8"],
    )
    fun vkIdOAuthCallback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
    ): String {
        val htmlPrefix = "<html><body style=\"font-family:sans-serif;padding:40px;max-width:900px\">"
        if (!error.isNullOrBlank()) {
            log.warn("VK ID OAuth callback error: {}", error)
            return htmlPrefix +
                "<h2>❌ Ошибка авторизации VK ID</h2>" +
                "<p>VK ID вернул: <b>" + error.replace("<", "") + "</b></p>" +
                "<p>Закройте эту вкладку и попробуйте снова.</p>" +
                "</body></html>"
        }
        if (code.isNullOrBlank()) {
            return htmlPrefix +
                "<h2>❌ Не получен code от VK ID</h2>" +
                "<p>Откройте эту страницу через /api/public/utils/vkIdOAuthUrl, " +
                "а не напрямую.</p>" +
                "</body></html>"
        }
        if (state.isNullOrBlank()) {
            return htmlPrefix +
                "<h2>❌ Не получен state (CSRF)</h2>" +
                "<p>VK ID должен вернуть параметр state. Возможно, запрос был подделан.</p>" +
                "</body></html>"
        }
        cleanExpiredPending()
        val pending = pendingAuths.remove(state)
        if (pending == null) {
            log.warn("VK ID OAuth callback: state not found or expired (state={})", state.take(8))
            return htmlPrefix +
                "<h2>❌ Ошибка CSRF-защиты</h2>" +
                "<p>state не найден или истёк (TTL 10 минут).</p>" +
                "<p>Откройте эту страницу через /api/public/utils/vkIdOAuthUrl.</p>" +
                "</body></html>"
        }
        if (clientId <= 0 || redirectUri.isBlank() || clientSecret.isBlank()) {
            return htmlPrefix +
                "<h2>❌ Не настроены vk.id.client-id / vk.id.redirect-uri / vk.id.client-secret</h2>" +
                "<p>Задайте их в application.yml `karaoke-web` и пересоберите.</p>" +
                "</body></html>"
        }
        log.info("VK ID OAuth callback: exchanging code (length={}) for token...", code.length)
        val params =
            buildString {
                append("client_id=").append(clientId)
                append("&client_secret=").append(URLEncoder.encode(clientSecret, "UTF-8"))
                append("&redirect_uri=").append(URLEncoder.encode(redirectUri, "UTF-8"))
                append("&code=").append(URLEncoder.encode(code, "UTF-8"))
                append("&code_verifier=").append(URLEncoder.encode(pending.codeVerifier, "UTF-8"))
            }
        val responseJson: String =
            try {
                val conn = URL("https://id.vk.ru/oauth2/token").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(params.toByteArray(Charsets.UTF_8)) }
                conn.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                log.error("VK ID token POST failed", e)
                return htmlPrefix +
                    "<h2>❌ Ошибка обмена code → token</h2>" +
                    "<pre>" + (e.message ?: e.javaClass.simpleName) + "</pre>" +
                    "</body></html>"
            }
        log.info("VK ID token response: {}...", responseJson.take(200))
        val parsed: JsonNode =
            try {
                ObjectMapper().readTree(responseJson)
            } catch (e: Exception) {
                log.error("Cannot parse VK ID token response: {}", responseJson)
                return htmlPrefix +
                    "<h2>❌ Не удалось разобрать ответ VK ID</h2>" +
                    "<pre>" + responseJson.take(800) + "</pre>" +
                    "</body></html>"
            }
        val errorNode = parsed.get("error")?.asText()
        if (!errorNode.isNullOrBlank()) {
            val errorDescription = parsed.get("error_description")?.asText() ?: "?"
            log.error("VK ID OAuth error: {} {}", errorNode, errorDescription)
            return htmlPrefix +
                "<h2>❌ VK ID отверг code</h2>" +
                "<p>error: <b>" + errorNode + "</b></p>" +
                "<p>error_description: <b>" + errorDescription + "</b></p>" +
                "</body></html>"
        }
        val accessToken = parsed.get("access_token")?.asText()
        if (accessToken.isNullOrBlank()) {
            return htmlPrefix +
                "<h2>❌ VK ID не вернул access_token</h2>" +
                "<pre>" + responseJson.take(800) + "</pre>" +
                "</body></html>"
        }
        val refreshToken = parsed.get("refresh_token")?.asText() ?: ""
        val expiresIn = parsed.get("expires_in")?.asLong() ?: 3600L
        val idToken = parsed.get("id_token")?.asText() ?: ""
        val userId = parsed.get("user_id")?.asText() ?: "?"
        log.info(
            "VK ID OAuth SUCCESS: user_id={}, access_token length={}, refresh_token present={}",
            userId,
            accessToken.length,
            refreshToken.isNotBlank(),
        )

        val saveResult = saveTokensOnAdminMachine(accessToken, refreshToken, expiresIn, idToken)
        val autoSaved = saveResult.first
        val autoSaveMessage = saveResult.second
        val manualCurl =
            "# Скопируйте и выполните на admin-машине (nsa-i9):\n" +
                "curl -s -X POST \"$adminApiUrl/api/utils/vkIdSaveTokens\" \\\n" +
                "     --data-urlencode \"accessToken=" + escapeShellSingle(accessToken) + "\" \\\n" +
                "     --data-urlencode \"refreshToken=" + escapeShellSingle(refreshToken) + "\" \\\n" +
                "     --data-urlencode \"expiresIn=$expiresIn\" \\\n" +
                "     --data-urlencode \"idToken=" + escapeShellSingle(idToken) + "\"\n"
        val autoSavedBadge =
            if (autoSaved) {
                "<span style='background:#d4edda;padding:2px 8px;border-radius:4px'>" +
                    "✅ авто-сохранено в Karaoke.properties admin-машины</span>"
            } else {
                "<span style='background:#fff3cd;padding:2px 8px;border-radius:4px'>" +
                    "⚠️ admin-машина недоступна — сохраните вручную</span>"
            }
        val headingText = if (autoSaved) "Готово к публикации!" else "Шаг 2: сохраните токены на admin-машине"
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
            "<h2 style=\"color:green\">✅ Токен VK ID получен</h2>" +
            "<p><b>access_token</b> (первые 30 символов): <code>" +
            escapeHtml(accessToken.take(30)) + "...</code></p>" +
            "<p><b>user_id:</b> <code>" + escapeHtml(userId) + "</code></p>" +
            "<p><b>expires_in:</b> <code>$expiresIn</code> сек</p>" +
            "<p><b>refresh_token:</b> <code>" +
            (if (refreshToken.isBlank()) "не выдан" else "получен (" + refreshToken.length + " символов)") +
            "</code></p>" +
            "<p>Состояние: " + autoSavedBadge + "</p>" +
            "<hr>" +
            "<h3>" + headingText + "</h3>" +
            manualBlock +
            "</body></html>"
    }

    /**
     * Отправляет токены на admin-машину через HTTP POST. Если admin-машина доступна —
     * токен сохранится автоматически. Иначе вернёт `<false, errorMessage>`.
     */
    private fun saveTokensOnAdminMachine(
        accessToken: String,
        refreshToken: String,
        expiresIn: Long,
        idToken: String,
    ): Pair<Boolean, String> {
        if (adminApiUrl.isBlank()) {
            return Pair(false, "adminApiUrl пуст")
        }
        if (!isReachable(adminApiUrl)) {
            return Pair(false, "admin-машина недоступна по $adminApiUrl")
        }
        return try {
            val url = URL("$adminApiUrl/api/utils/vkIdSaveTokens")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body =
                buildString {
                    append("accessToken=").append(URLEncoder.encode(accessToken, "UTF-8"))
                    if (refreshToken.isNotBlank()) {
                        append("&refreshToken=").append(URLEncoder.encode(refreshToken, "UTF-8"))
                    }
                    append("&expiresIn=").append(expiresIn)
                    if (idToken.isNotBlank()) {
                        append("&idToken=").append(URLEncoder.encode(idToken, "UTF-8"))
                    }
                }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            log.info("admin save response: {}...", resp.take(100))
            val success =
                resp.contains("\"success\":true") ||
                    resp.contains("\"success\": true") ||
                    resp.contains("Токен")
            if (success) {
                Pair(true, "Токены сохранены в Karaoke.properties (vkIdAccessToken / vkIdRefreshToken).")
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

    /** Удаляет протухшие pending-записи (старше pendingTtlSeconds). */
    private fun cleanExpiredPending() {
        val cutoff = Instant.now().minusSeconds(pendingTtlSeconds)
        pendingAuths.entries.removeIf { it.value.createdAt.isBefore(cutoff) }
    }

    private fun escapeHtml(s: String): String =
        s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    /** Экранирует одиночные кавычки для shell single-quoted строк в curl. */
    private fun escapeShellSingle(s: String): String = s.replace("'", "'\\''")
}
