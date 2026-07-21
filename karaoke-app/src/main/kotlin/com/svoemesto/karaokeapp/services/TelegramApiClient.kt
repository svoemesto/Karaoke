package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// Минимальные DTO Telegram Bot API (getUpdates) - только поля, реально используемые
// TelegramUpdatesConsumer для отлова вышедшего channel_post. Формат ключей - snake_case (как у Telegram).

/**
 * Класс Telegram Chat.
 *
 * @see docs/features/async-process-queue.md
 */
@Serializable
data class TelegramChat(
    val id: Long,
    val username: String? = null,
    val title: String? = null,
)

/**
 * Класс Telegram Message.
 *
 * @see docs/features/async-process-queue.md
 */
@Serializable
data class TelegramMessage(
    @SerialName("message_id") val messageId: Long,
    val date: Long,
    val chat: TelegramChat,
    val text: String? = null,
    val caption: String? = null,
)

/**
 * Класс Telegram Update.
 *
 * @see docs/features/async-process-queue.md
 */
@Serializable
data class TelegramUpdate(
    @SerialName("update_id") val updateId: Long,
    @SerialName("channel_post") val channelPost: TelegramMessage? = null,
)

/**
 * Класс Telegram Updates Response.
 *
 * @see docs/features/async-process-queue.md
 */
@Serializable
data class TelegramUpdatesResponse(
    val ok: Boolean = false,
    val result: List<TelegramUpdate> = emptyList(),
    @SerialName("error_code") val errorCode: Int? = null,
    val description: String? = null,
)

/**
 * Тонкий клиент Telegram Bot API поверх JDK HttpClient (паттерн - как в AIAssistant.kt).
 *
 * Доступ к Telegram из России периодически недоступен без VPN (см. DEVELOPMENT.md/архив). Реализован
 * авто-fallback: каждый запрос сначала пробует идти напрямую; при сетевой ошибке переключается на
 * HTTP-прокси (VLESS/xray, KaraokeProperties.telegramProxyUrl) и остаётся на нём, периодически (раз в
 * telegramProxyModeTtlMs) пробуя вернуться на прямой путь. Если telegramProxyUrl не задан - прокси
 * недоступен, ошибка пробрасывается наверх без изменений.
 */

/**
 * Класс Telegram Api Client.
 *
 * @see docs/features/async-process-queue.md
 */
class TelegramApiClient {
    private val json = Json { ignoreUnknownKeys = true }

    private val directClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    // Кэш текущего режима: true = сейчас ходим через прокси. modeSetAtMs - когда режим был определён/
    // подтверждён последний раз (используется для решения "пора ли снова попробовать напрямую").
    @Volatile private var useProxy = false

    @Volatile private var modeSetAtMs = 0L

    private fun proxyClient(): HttpClient? {
        val proxyUrl = KaraokeProperties.getString("telegramProxyUrl")
        if (proxyUrl.isBlank()) return null
        val uri = URI(proxyUrl)
        if (uri.host == null || uri.port <= 0) return null
        return HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .proxy(ProxySelector.of(InetSocketAddress(uri.host, uri.port)))
            .build()
    }

    private fun baseUrl(): String {
        val base = KaraokeProperties.getString("telegramBotApiBaseUrl").ifBlank { "https://api.telegram.org" }
        val token = KaraokeProperties.getString("telegramBotToken")
        return "${base.trimEnd('/')}/bot$token"
    }

    // Отправка запроса с авто-fallback напрямую -> прокси. Один и тот же HttpRequest безопасно передать
    // в оба HttpClient.send(...) - JDK HttpRequest иммутабелен и не привязан к конкретному клиенту.
    private fun send(request: HttpRequest): HttpResponse<String> {
        val ttl = KaraokeProperties.getLong("telegramProxyModeTtlMs").let { if (it <= 0) 60_000L else it }
        val now = System.currentTimeMillis()
        val shouldTryDirect = !useProxy || (now - modeSetAtMs > ttl)

        if (shouldTryDirect) {
            try {
                val response = directClient.send(request, HttpResponse.BodyHandlers.ofString())
                if (useProxy) {
                    useProxy = false
                    modeSetAtMs = now
                    println("TelegramApiClient: прямой доступ к Telegram восстановлен, прокси больше не используется")
                }
                return response
            } catch (e: Exception) {
                if (!useProxy) println("TelegramApiClient: прямой доступ к Telegram недоступен (${e.message}), переключение на прокси")
                useProxy = true
                modeSetAtMs = now
                // падаем ниже - пробуем через прокси
            }
        }

        val proxy =
            proxyClient()
                ?: throw IllegalStateException("Telegram недоступен напрямую, а telegramProxyUrl не задан")
        return proxy.send(request, HttpResponse.BodyHandlers.ofString())
    }

    fun getUpdates(
        offset: Long,
        timeoutSec: Int = 25,
    ): TelegramUpdatesResponse {
        val uri = URI("${baseUrl()}/getUpdates?offset=$offset&timeout=$timeoutSec&allowed_updates=%5B%22channel_post%22%5D")
        val request =
            HttpRequest
                .newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(timeoutSec + 10L))
                .GET()
                .build()
        val response = send(request)
        return json.decodeFromString(TelegramUpdatesResponse.serializer(), response.body())
    }

    // Разовая подчистка перед стартом long-polling: если у бота когда-либо был выставлен webhook,
    // getUpdates будет отвечать 409. Ошибки игнорируются - это защитная мера, а не обязательный шаг.
    fun deleteWebhook() {
        try {
            val uri = URI("${baseUrl()}/deleteWebhook")
            val request =
                HttpRequest
                    .newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build()
            send(request)
        } catch (_: Exception) {
        }
    }
}
