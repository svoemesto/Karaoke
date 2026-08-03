package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration

// VK API DTO (минимальные, только используемые поля). Формат ключей — snake_case (как у VK).

/**
 * Ответ `wall.post` (VK API): `response.post_id` при успехе, `error` при неудаче.
 *
 * @see docs/features/vk-news-auto-publish.md
 */
@Serializable
data class VkWallPostResponse(
    val response: VkWallPostResult? = null,
    val error: VkApiError? = null,
)

/** Внутренний `response` wall.post — нужен только `post_id`. */
@Serializable
data class VkWallPostResult(
    @SerialName("post_id") val postId: Long = 0,
)

/**
 * Ответ `video.save` (VK API): `response` с `owner_id`, `video_id`, `upload_url`.
 *
 * @see docs/features/vk-news-auto-publish.md
 */
@Serializable
data class VkVideoSaveResponse(
    val response: VkVideoSaveResult? = null,
    val error: VkApiError? = null,
)

@Serializable
data class VkVideoSaveResult(
    @SerialName("owner_id") val ownerId: Long = 0,
    @SerialName("video_id") val videoId: Long = 0,
    @SerialName("upload_url") val uploadUrl: String = "",
)

/** Ответ загрузки видеофайла на `upload_url` (подтверждение). */
@Serializable
data class VkVideoUploadResponse(
    val size: Long = 0,
    @SerialName("video_id") val videoId: String? = null,
)

/** Стандартная VK API ошибка. */
@Serializable
data class VkApiError(
    @SerialName("error_code") val errorCode: Int = 0,
    @SerialName("error_msg") val errorMsg: String = "",
)

/**
 * Ответ `users.get` (минимальный, только для проверки user-token в [ApiController.saveVkUserToken]).
 * Не используется в основной публикации — нужны только id + имя для логов.
 */
@Serializable
data class VkUserCheckResponse(
    val response: List<VkUserCheckEntry>? = null,
    val error: VkApiError? = null,
)

@Serializable
data class VkUserCheckEntry(
    val id: Long = 0,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
)

/**
 * Ответ POST https://oauth.vk.ru/access_token (Authorization Code Flow, обмен code → token).
 * Поля: `access_token`, `expires_in`, `user_id`, `error`.
 * Парсится в [ApiController.vkOAuthCallback].
 */
@Serializable
data class VkCodeTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("user_id") val userId: Long? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

/**
 * Тонкий клиент VK API поверх JDK HttpClient (specs/121-vk-news-auto-publish).
 *
 * Реализует методы `wall.post` (создание поста в группе) и `video.save` + загрузка
 * видеофайла (прикрепление демо-MP4). Аутентификация — Community access token
 * (`vkAccessToken`), версия API — `vkApiVersion` (дефолт `5.199`).
 *
 * Прокси-fallback — по образцу [TelegramApiClient]: каждый запрос сначала пробует
 * напрямую, при сетевой ошибке переключается на HTTP-прокси (`vkProxyUrl`) на
 * TTL-окно (`vkProxyModeTtlMs`). Если `vkProxyUrl` не задан — ошибка пробрасывается.
 *
 * Retry/backoff (FR-009): 3 попытки с backoff `30с→2мин→5мин` (по образцу
 * [TelegramApiClient.sendVideo]). Non-retryable коды VK API: `4`, `5`, `15`,
 * `100` (см. research.md §5).
 *
 * @see docs/features/vk-news-auto-publish.md
 */
class VkApiClient {
    private val json = Json { ignoreUnknownKeys = true }

    private val directClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    @Volatile private var useProxy = false

    @Volatile private var modeSetAtMs = 0L

    private fun proxyClient(): HttpClient? {
        val proxyUrl = KaraokeProperties.getString("vkProxyUrl")
        if (proxyUrl.isBlank()) return null
        val uri = URI(proxyUrl)
        if (uri.host == null || uri.port <= 0) return null
        return HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .proxy(ProxySelector.of(InetSocketAddress(uri.host, uri.port)))
            .build()
    }

    private fun baseUrl(): String = "https://api.vk.ru/method"

    /**
     * Community access token (`vkAccessToken`) — выдаётся в настройках группы ВК.
     * Используется для методов, доступных сообществам: `wall.post` (от имени группы, FR-006).
     * **Не работает** для `video.save`, `photos.*`, `docs.*` — для них нужен user-token
     * (см. [userAccessToken], specs/121 fix 02.08.2026).
     */
    private fun accessToken(): String = KaraokeProperties.getString("vkAccessToken")

    /**
     * User access token с scopes `video,photos,wall,offline` (`vkUserAccessToken`).
     * Получается через Implicit Flow Standalone-приложения VK — см.
     * `/api/utils/vkOAuthUrl` (формирование URL) + `/api/utils/vkSaveUserToken` (сохранение).
     * Используется ТОЛЬКО для методов, требующих user scope:
     * - `video.save` — загрузка видео в группу (право `video`)
     * - `photos.getWallUploadServer` / `photos.saveWallPhoto` — загрузка фото на стену группы (право `photos`, FR-022 в новой редакции)
     * Возвращает пустую строку, если user-token не задан — в этом случае `video.save`
     * вернёт понятную ошибку.
     */
    private fun userAccessToken(): String = KaraokeProperties.getString("vkUserAccessToken")

    private fun apiVersion(): String = KaraokeProperties.getString("vkApiVersion").ifBlank { "5.199" }

    // Отправка запроса с авто-fallback напрямую → прокси (по образцу TelegramApiClient.send).
    private fun send(request: HttpRequest): HttpResponse<String> {
        val ttl = KaraokeProperties.getLong("vkProxyModeTtlMs").let { if (it <= 0) 60_000L else it }
        val now = System.currentTimeMillis()
        val shouldTryDirect = !useProxy || (now - modeSetAtMs > ttl)

        if (shouldTryDirect) {
            try {
                val response = directClient.send(request, HttpResponse.BodyHandlers.ofString())
                if (useProxy) {
                    useProxy = false
                    modeSetAtMs = now
                    println("VkApiClient: прямой доступ к VK восстановлен, прокси больше не используется")
                }
                return response
            } catch (e: Exception) {
                if (!useProxy) println("VkApiClient: прямой доступ недоступен (${e.message}), переключение на прокси")
                useProxy = true
                modeSetAtMs = now
            }
        }

        val proxy =
            proxyClient()
                ?: throw IllegalStateException("VK недоступен напрямую, а vkProxyUrl не задан")
        return proxy.send(request, HttpResponse.BodyHandlers.ofString())
    }

    /**
     * Создаёт пост в группе ВК через `wall.post` (FR-001, FR-003, FR-006) с ретраями FR-009.
     *
     * @param groupId ID группы без минуса (бот добавляет `-` для `owner_id`).
     * @param message Текст поста (FR-003, FR-005 — лимит 10 000 символов).
     * @param attachments Строка прикреплений через запятую (например, `video-123_456`) или null.
     * @return [VkAutoPublishResult] с `postId` в формате `-<groupId>_<postId>` при успехе.
     */
    fun wallPost(
        groupId: String,
        message: String,
        attachments: String? = null,
        maxAttempts: Int = 3,
        backoffScheduleMs: List<Long> = listOf(30_000L, 120_000L, 300_000L),
    ): VkAutoPublishResult {
        if (groupId.isBlank()) {
            return VkAutoPublishResult(
                state = VkAutoPublishState.SEND_FAILED,
                error = "groupId is empty (vkGroupId not configured)",
            )
        }
        if (accessToken().isBlank()) {
            return VkAutoPublishResult(
                state = VkAutoPublishState.SEND_FAILED,
                error = "vkAccessToken is empty (not configured)",
            )
        }
        val ownerId = "-$groupId"
        val params =
            buildString {
                append("owner_id=")
                append(ownerId)
                append("&from_group=1&message=")
                append(java.net.URLEncoder.encode(message, "UTF-8"))
                if (!attachments.isNullOrBlank()) {
                    append("&attachments=")
                    append(java.net.URLEncoder.encode(attachments, "UTF-8"))
                }
                append("&access_token=")
                append(accessToken())
                append("&v=")
                append(apiVersion())
            }
        var lastError: String? = null
        for (attempt in 1..maxAttempts) {
            val result = tryWallPost(params)
            if (result.response != null && result.response.postId > 0) {
                val postId = "-${groupId}_${result.response.postId}"
                println("VkApiClient.wallPost: success on attempt $attempt, post_id=$postId")
                return VkAutoPublishResult(
                    state = VkAutoPublishState.PUBLISHED,
                    postId = postId,
                )
            }
            val code = result.error?.errorCode ?: 0
            if (code in NON_RETRYABLE_ERROR_CODES) {
                lastError = "non-retryable ($code): ${result.error?.errorMsg ?: "no description"}"
                println("VkApiClient.wallPost: non-retryable error $code on attempt $attempt: ${result.error?.errorMsg}")
                break
            }
            lastError = "attempt $attempt failed (code=$code): ${result.error?.errorMsg ?: "no description"}"
            println("VkApiClient.wallPost: attempt $attempt failed: $lastError")
            if (attempt < maxAttempts) {
                val delayMs = backoffScheduleMs.getOrElse(attempt - 1) { backoffScheduleMs.last() }
                try {
                    Thread.sleep(delayMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return VkAutoPublishResult(
                        state = VkAutoPublishState.SEND_FAILED,
                        error = "interrupted during backoff on attempt $attempt",
                    )
                }
            }
        }
        return VkAutoPublishResult(
            state = VkAutoPublishState.SEND_FAILED,
            error = "retries exhausted: $lastError",
        )
    }

    private fun tryWallPost(params: String): VkWallPostResponse {
        val uri = URI("${baseUrl()}/wall.post")
        val request =
            HttpRequest
                .newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(params))
                .build()
        val response = send(request)
        return json.decodeFromString(VkWallPostResponse.serializer(), response.body())
    }

    /**
     * Комбинированный flow `video.save` → загрузка → `wall.post` с `attachments=video...` (FR-019).
     *
     * @param groupId ID группы без минуса.
     * @param message Текст поста.
     * @param videoFile Демо-MP4 файл (проверка размера перед отправкой, FR-020).
     * @return [VkAutoPublishResult] с `postId` при успехе.
     */
    fun sendPostWithVideo(
        groupId: String,
        message: String,
        videoFile: java.io.File,
        songId: Long,
    ): VkAutoPublishResult {
        val maxVideoBytes =
            KaraokeProperties.getLong("vkAutoPublishMaxVideoSizeMb").let { if (it <= 0) 50L else it } * 1024 * 1024
        if (videoFile.length() > maxVideoBytes) {
            return VkAutoPublishResult(
                state = VkAutoPublishState.SEND_FAILED,
                error = "video size ${videoFile.length()} exceeds limit $maxVideoBytes bytes (render with smaller params first)",
            )
        }
        // Шаг 1: video.save
        val videoSaveResult = videoSave(groupId, "$songId demo", "")
        if (videoSaveResult.state != VkAutoPublishState.PUBLISHED) {
            return VkAutoPublishResult(
                state = videoSaveResult.state,
                error = videoSaveResult.error ?: "video.save failed",
            )
        }
        val save =
            videoSaveResult.savedVideo ?: return VkAutoPublishResult(
                state = VkAutoPublishState.SEND_FAILED,
                error = "video.save returned no upload_url",
            )
        // Шаг 2: загрузка файла
        val uploadOk = uploadVideoFile(save.uploadUrl, videoFile)
        if (!uploadOk) {
            return VkAutoPublishResult(
                state = VkAutoPublishState.SEND_FAILED,
                error = "video upload failed (upload_url=${save.uploadUrl.take(80)}...)",
            )
        }
        // Шаг 3: wall.post с attachments=video<owner_id>_<video_id>
        val attachment = "video${save.ownerId}_${save.videoId}"
        return wallPost(groupId, message, attachments = attachment)
    }

    /** Шаг 1: `video.save` — резервирование видео-записи (FR-019). Использует user-token. */
    private fun videoSave(
        groupId: String,
        name: String,
        description: String,
    ): VkSaveVideoResult {
        // specs/121 fix 02.08.2026: video.save требует USER-token с правом video (community-token
        // не подходит, см. /api/utils/vkOAuthUrl для получения). Возвращаем понятную ошибку,
        // если user-token не настроен.
        val token = userAccessToken()
        if (token.isBlank()) {
            return VkSaveVideoResult(
                state = VkAutoPublishState.SEND_FAILED,
                error = "vkUserAccessToken is empty — нужен user-token с правом video (см. /api/utils/vkOAuthUrl)",
            )
        }
        if (accessToken().isBlank()) {
            return VkSaveVideoResult(
                state = VkAutoPublishState.SEND_FAILED,
                error = "vkAccessToken (community) is empty",
            )
        }
        val params =
            buildString {
                append("group_id=")
                append(groupId)
                append("&name=")
                append(java.net.URLEncoder.encode(name, "UTF-8"))
                append("&description=")
                append(java.net.URLEncoder.encode(description, "UTF-8"))
                append("&access_token=")
                append(token)
                append("&v=")
                append(apiVersion())
            }
        val uri = URI("${baseUrl()}/video.save")
        val request =
            HttpRequest
                .newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(params))
                .build()
        return try {
            val response = send(request)
            val parsed = json.decodeFromString(VkVideoSaveResponse.serializer(), response.body())
            if (parsed.error != null) {
                VkSaveVideoResult(
                    state = VkAutoPublishState.SEND_FAILED,
                    error = "video.save error ${parsed.error.errorCode}: ${parsed.error.errorMsg}",
                )
            } else if (parsed.response != null && parsed.response.uploadUrl.isNotBlank()) {
                VkSaveVideoResult(state = VkAutoPublishState.PUBLISHED, savedVideo = parsed.response)
            } else {
                VkSaveVideoResult(state = VkAutoPublishState.SEND_FAILED, error = "video.save empty response")
            }
        } catch (e: Exception) {
            VkSaveVideoResult(state = VkAutoPublishState.SEND_FAILED, error = "video.save exception: ${e.message}")
        }
    }

    /** Шаг 2: загрузка видеофайла на `upload_url` (multipart/form-data, поле `video_file`). */
    private fun uploadVideoFile(
        uploadUrl: String,
        videoFile: java.io.File,
    ): Boolean {
        val boundary = "karaoke-${System.currentTimeMillis()}"
        val bytes = Files.readAllBytes(videoFile.toPath())
        val body = buildVideoMultipartBody(bytes, videoFile.name, boundary)
        val uri = URI(uploadUrl)
        val request =
            HttpRequest
                .newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build()
        return try {
            val response = send(request)
            response.statusCode() in 200..299
        } catch (e: Exception) {
            println("VkApiClient.uploadVideoFile exception: ${e.message}")
            false
        }
    }

    private fun buildVideoMultipartBody(
        videoBytes: ByteArray,
        videoFileName: String,
        boundary: String,
    ): ByteArray {
        val crlf = "\r\n"
        val out = ByteArrayOutputStream()
        out.write("--$boundary$crlf".toByteArray(Charsets.UTF_8))
        out.write(
            "Content-Disposition: form-data; name=\"video_file\"; filename=\"$videoFileName\"$crlf".toByteArray(Charsets.UTF_8),
        )
        out.write("Content-Type: video/mp4$crlf$crlf".toByteArray(Charsets.UTF_8))
        out.write(videoBytes)
        out.write(crlf.toByteArray(Charsets.UTF_8))
        out.write("--$boundary--$crlf".toByteArray(Charsets.UTF_8))
        return out.toByteArray()
    }

    companion object {
        // VK API error_code, при которых retry бесполезен (FR-009 non-retryable).
        // 4 — Incorrect signature; 5 — User authorization failed; 15 — Access denied;
        // 100 — One of the parameters is missing or invalid.
        private val NON_RETRYABLE_ERROR_CODES = setOf(4, 5, 15, 100)

        // Парсер JSON для дешифровки ответа users.get при проверке user-token в
        // ApiController.saveVkUserToken. ignoreUnknownKeys=true — VK может добавлять новые
        // поля без сюрпризов.
        private val checkerJson = Json { ignoreUnknownKeys = true }

        @JvmStatic
        fun decodeUserCheck(body: String): VkUserCheckResponse =
            checkerJson.decodeFromString(VkUserCheckResponse.serializer(), body)

        // Парсер ответа POST /access_token (Authorization Code Flow обмен).
        @JvmStatic
        fun decodeTokenResponse(body: String): VkCodeTokenResponse =
            checkerJson.decodeFromString(VkCodeTokenResponse.serializer(), body)
    }
}

/** Внутренний результат `video.save` для комбинированного flow. */
internal data class VkSaveVideoResult(
    val state: VkAutoPublishState,
    val savedVideo: VkVideoSaveResult? = null,
    val error: String? = null,
)
