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
import java.nio.file.Files
import java.time.Duration

// Минимальные DTO Telegram Bot API (getUpdates) - только поля, реально используемые
// TelegramUpdatesConsumer для отлова вышедшего channel_post. Формат ключей - snake_case (как у Telegram).

/**
 * Класс Telegram Chat.
 *
 * @see archive/docs/features/async-process-queue.md
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
 * @see archive/docs/features/async-process-queue.md
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
 * @see archive/docs/features/async-process-queue.md
 */
@Serializable
data class TelegramUpdate(
    @SerialName("update_id") val updateId: Long,
    @SerialName("channel_post") val channelPost: TelegramMessage? = null,
)

/**
 * Класс Telegram Updates Response.
 *
 * @see archive/docs/features/async-process-queue.md
 */
@Serializable
data class TelegramUpdatesResponse(
    val ok: Boolean = false,
    val result: List<TelegramUpdate> = emptyList(),
    @SerialName("error_code") val errorCode: Int? = null,
    val description: String? = null,
)

/**
 * Результат `sendVideo` Telegram Bot API: только поля, используемые Фазой 2
 * (specs/113-telegram-demo-publish). `ok=true` → success, `result.message_id`
 * записывается в `Song.idTelegramDemo`. `ok=false` → `errorCode` определяет
 * retryable (429/5xx) vs non-retryable (400/403/404) — см. retry-цикл в
 * [TelegramApiClient.sendVideo].
 *
 * @see archive/docs/features/telegram-auto-publish.md
 */
@Serializable
data class TelegramSendVideoResponse(
    val ok: Boolean = false,
    @SerialName("error_code") val errorCode: Int? = null,
    val description: String? = null,
    val result: TelegramSendVideoResult? = null,
)

/**
 * Внутренний `result` sendVideo — нужен только `message_id`.
 *
 * @see archive/docs/features/telegram-auto-publish.md
 */
@Serializable
data class TelegramSendVideoResult(
    @SerialName("message_id") val messageId: Long? = null,
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
 * @see archive/docs/features/async-process-queue.md
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

    /**
     * Фаза 2 (specs/113-telegram-demo-publish): отправляет демо-MP4 в канал через Telegram Bot API
     * `sendVideo` с подписью (caption). Реализует retry-цикл FR-010: до [maxAttempts] попыток с
     * экспоненциальным backoff [backoffScheduleMs] (по умолчанию 30с / 2м / 5м). Каждая попытка
     * использует существующий прокси-fallback [send] (тот же паттерн, что в Фазе 1 для getUpdates).
     *
     * Перед каждой попыткой проверяет `videoFile.length() <= maxFileSizeBytes` — если файл
     * превышает лимит, сразу возвращает `SEND_FAILED` без сетевой попытки (FR-004: вызывающий
     * код ставит перерендер с уменьшенными параметрами вместо отправки заведомо слишком большого
     * файла).
     *
     * @param channelId ID/username канала (значение `telegramAutoPublishChannelId`)
     * @param videoFile готовый MP4-файл демо-версии
     * @param caption подпись к видео (≤1024 символов, FR-005)
     * @param maxFileSizeBytes лимит размера файла в байтах (значение
     *   `telegramAutoPublishMaxFileSizeMb * 1024 * 1024`); файл больше лимита → `SEND_FAILED`
     * @param maxAttempts число попыток (по умолчанию 3, FR-010)
     * @param backoffScheduleMs интервалы между попытками в миллисекундах (по умолчанию
     *   `[30_000, 120_000, 300_000]` — 30с / 2м / 5м); длина должна быть >= `maxAttempts - 1`
     * @return `PUBLISHED` с `messageId` при успехе; `SEND_FAILED` с `error` при исчерпании ретраев
     *   или превышении размера файла
     *
     * @see archive/docs/features/telegram-auto-publish.md
     */
    fun sendVideo(
        channelId: String,
        videoFile: java.io.File,
        caption: String,
        maxFileSizeBytes: Long,
        maxAttempts: Int = 3,
        backoffScheduleMs: List<Long> = listOf(30_000L, 120_000L, 300_000L),
    ): TelegramAutoPublishResult {
        // FR-004: проверка размера перед сетевой попыткой — отправлять заведомо слишком большой
        // файл впустую расходует квоту и время.
        if (videoFile.length() > maxFileSizeBytes) {
            return TelegramAutoPublishResult(
                state = TelegramAutoPublishState.SEND_FAILED,
                error = "file size ${videoFile.length()} exceeds limit $maxFileSizeBytes bytes (render with smaller params first)",
            )
        }
        if (channelId.isBlank()) {
            return TelegramAutoPublishResult(
                state = TelegramAutoPublishState.SEND_FAILED,
                error = "channelId is empty (telegramAutoPublishChannelId not configured)",
            )
        }

        val boundary = "karaoke-${System.currentTimeMillis()}"
        val bytes = Files.readAllBytes(videoFile.toPath())
        var lastError: String? = null

        for (attempt in 1..maxAttempts) {
            val result = trySendVideo(channelId, bytes, videoFile.name, caption, boundary)
            if (result.ok && result.result?.messageId != null) {
                val messageId = result.result.messageId.toString()
                println("TelegramApiClient.sendVideo: success on attempt $attempt, message_id=$messageId")
                return TelegramAutoPublishResult(
                    state = TelegramAutoPublishState.PUBLISHED,
                    messageId = messageId,
                )
            }
            // Non-retryable: 400/403/404 — сразу выходим без backoff (FR-010).
            val code = result.errorCode
            if (code != null && code in NON_RETRYABLE_ERROR_CODES) {
                lastError = "non-retryable ($code): ${result.description ?: "no description"}"
                println("TelegramApiClient.sendVideo: non-retryable error $code on attempt $attempt: ${result.description}")
                break
            }
            lastError = "attempt $attempt failed (${code ?: "no code"}): ${result.description ?: "no description"}"
            println("TelegramApiClient.sendVideo: attempt $attempt failed: $lastError")
            if (attempt < maxAttempts) {
                val delayMs = backoffScheduleMs.getOrElse(attempt - 1) { backoffScheduleMs.last() }
                try {
                    Thread.sleep(delayMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return TelegramAutoPublishResult(
                        state = TelegramAutoPublishState.SEND_FAILED,
                        error = "interrupted during backoff on attempt $attempt",
                    )
                }
            }
        }
        return TelegramAutoPublishResult(
            state = TelegramAutoPublishState.SEND_FAILED,
            error = "retries exhausted: $lastError",
        )
    }

    // Одна попытка sendVideo — multipart/form-data сборка и отправка через [send] (с прокси-fallback).
    private fun trySendVideo(
        channelId: String,
        videoBytes: ByteArray,
        videoFileName: String,
        caption: String,
        boundary: String,
    ): TelegramSendVideoResponse {
        val body = buildSendVideoMultipartBody(channelId, videoBytes, videoFileName, caption, boundary)
        val uri = URI("${baseUrl()}/sendVideo")
        val request =
            HttpRequest
                .newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build()
        val response = send(request)
        return json.decodeFromString(TelegramSendVideoResponse.serializer(), response.body())
    }

    // Сборка multipart/form-data тела запроса sendVideo вручную (JDK HttpClient не имеет
    // встроенного multipart-билдера). Поля: chat_id, video (файл), caption, parse_mode=HTML,
    // disable_notification=false. Бинарное содержимое видео пишется как есть между boundary-рами.
    private fun buildSendVideoMultipartBody(
        channelId: String,
        videoBytes: ByteArray,
        videoFileName: String,
        caption: String,
        boundary: String,
    ): ByteArray {
        val crlf = "\r\n"
        val out = java.io.ByteArrayOutputStream()

        fun field(name: String, value: String) {
            out.write("--$boundary$crlf".toByteArray(Charsets.UTF_8))
            out.write("Content-Disposition: form-data; name=\"$name\"$crlf$crlf".toByteArray(Charsets.UTF_8))
            out.write("$value$crlf".toByteArray(Charsets.UTF_8))
        }

        fun fileField(name: String, fileName: String, contentType: String, data: ByteArray) {
            out.write("--$boundary$crlf".toByteArray(Charsets.UTF_8))
            out.write(
                "Content-Disposition: form-data; name=\"$name\"; filename=\"$fileName\"$crlf".toByteArray(Charsets.UTF_8),
            )
            out.write("Content-Type: $contentType$crlf$crlf".toByteArray(Charsets.UTF_8))
            out.write(data)
            out.write(crlf.toByteArray(Charsets.UTF_8))
        }
        field("chat_id", channelId)
        fileField("video", videoFileName, "video/mp4", videoBytes)
        field("caption", caption)
        field("parse_mode", "HTML")
        field("disable_notification", "false")
        out.write("--$boundary--$crlf".toByteArray(Charsets.UTF_8))
        return out.toByteArray()
    }

    companion object {
        // HTTP-коды Telegram, при которых retry бесполезен (FR-010: non-retryable).
        private val NON_RETRYABLE_ERROR_CODES = setOf(400, 403, 404)
    }
}
