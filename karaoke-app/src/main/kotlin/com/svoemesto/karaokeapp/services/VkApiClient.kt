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
 *  * Ответ POST https://oauth.vk.ru/access_token (Authorization Code Flow, обмен code → token).
 *  * Поля: `access_token`, `expires_in`, `user_id`, `error`.
 *  * Парсится в [ApiController.vkOAuthCallback].
 */
@Serializable
data class VkCodeTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("user_id") val userId: Long? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

// ====================== photos.* / docs.* (specs/138-vk-photo-preview-attachment) ======================

/** Ответ `photos.getWallUploadServer`: `response.upload_url`. */
@Serializable
data class VkPhotosGetWallUploadServerResponse(
    val response: VkPhotosGetWallUploadServerResult? = null,
    val error: VkApiError? = null,
)

@Serializable
data class VkPhotosGetWallUploadServerResult(
    @SerialName("upload_url") val uploadUrl: String = "",
    @SerialName("album_id") val albumId: Long = 0,
    @SerialName("user_id") val userId: Long = 0,
)

/** Ответ загрузки фото на `upload_url` (поле `photo` — JSON-строка с сервера VK). */
@Serializable
data class VkPhotoUploadResponseRaw(
    val server: Long = 0,
    val photo: String = "",
    val hash: String = "",
)

/** Ответ `photos.saveWallPhoto`: массив сохранённых фото (обычно один элемент). */
@Serializable
data class VkPhotosSaveWallPhotoResponse(
    val response: List<VkPhotoSavedEntry>? = null,
    val error: VkApiError? = null,
)

@Serializable
data class VkPhotoSavedEntry(
    val id: Long = 0,
    @SerialName("owner_id") val ownerId: Long = 0,
)

/** Прикреплённое фото в VK (transient, не персистится в БД). */
data class PhotoAttachment(
    val id: Long,
    val ownerId: Long,
    val attachment: String,
    val loadMethod: PhotoUploadMethod,
)

/** Ответ `docs.getWallUploadServer`: `response.upload_url`. */
@Serializable
data class VkDocsGetWallUploadServerResponse(
    val response: VkDocsGetWallUploadServerResult? = null,
    val error: VkApiError? = null,
)

@Serializable
data class VkDocsGetWallUploadServerResult(
    @SerialName("upload_url") val uploadUrl: String = "",
)

/** Ответ загрузки документа на `upload_url` (поле `file` — JSON-строка с сервера VK). */
@Serializable
data class VkDocUploadResponseRaw(
    val file: String = "",
)

/** Ответ `docs.save`: `response` с одним сохранённым документом. */
@Serializable
data class VkDocsSaveResponse(
    val response: VkDocSavedEntry? = null,
    val error: VkApiError? = null,
)

@Serializable
data class VkDocSavedEntry(
    val id: Long = 0,
    @SerialName("owner_id") val ownerId: Long = 0,
    val title: String = "",
    val url: String = "",
)

/** Прикреплённый документ-картинка в VK (transient, fallback-метод). */
data class DocAttachment(
    val id: Long,
    val ownerId: Long,
    val attachment: String,
)

/** Каким способом загружено превью: `photos.*` (основной), `docs.*` (fallback), `NONE` (деградация). */
enum class PhotoUploadMethod { PHOTOS, DOCS, NONE }

/** Базовое исключение при ошибке загрузки превью для ВК (specs/138). */
sealed class VkPhotoUploadException(
    message: String,
) : RuntimeException(message)

/** Авторизационная ошибка (`error_code in 27/15/5`) — триггер fallback на `docs.*`. */
class VkPhotoAuthException(
    val errorCode: Int,
    val errorMsg: String,
) : VkPhotoUploadException("photo auth error $errorCode: $errorMsg")

/** Transient-ошибка (5xx, 429, тайм-аут) — retry внутри метода. */
class VkPhotoTransientException(
    val errorCode: Int = 0,
    val errorMsg: String = "",
) : VkPhotoUploadException("photo transient error $errorCode: $errorMsg")

/** Invalid params (`error_code=100`) — non-retryable, без fallback. */
class VkPhotoInvalidParamsException(
    val errorCode: Int = 100,
    val errorMsg: String,
) : VkPhotoUploadException("photo invalid params $errorCode: $errorMsg")

/** Оба метода (`photos.*` + `docs.*`) не сработали → деградация (пост без превью). */
class VkBothAttachFailedException(
    val photosError: VkPhotoUploadException,
    val docsError: VkPhotoUploadException,
) : VkPhotoUploadException("both photo+docs attach failed: photos=${photosError.message}; docs=${docsError.message}")

/**
 * Результат refresh VK ID access_token (specs/151-vk-id-personal-token, FR-005).
 *
 * @property accessToken новый access_token для VK API.
 * @property refreshToken новый refresh_token (VK ID ротирует его при каждом refresh).
 * @property expiresIn срок жизни нового access_token в секундах (обычно 3600).
 * @property idToken новый id_token (JWT) — опционально, может отсутствовать.
 */
data class VkIdTokenRefreshResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val idToken: String? = null,
)

/**
 * Ошибка при refresh VK ID access_token (specs/151-vk-id-personal-token, FR-005).
 *
 * Типичные причины:
 * - `error=invalid_grant` — refresh_token истёк или отозван → нужен повторный OAuth flow.
 * - VK ID недоступен (5xx, тайм-аут) — retry с backoff в [com.svoemesto.karaokeapp.services.VkIdTokenRefreshScheduler].
 *
 * @property errorCode код ошибки VK ID (`invalid_grant`, `5xx`, и т.п.).
 * @property errorMsg человекочитаемое описание ошибки.
 */
class VkIdRefreshFailedException(
    val errorCode: String,
    val errorMsg: String,
) : RuntimeException("VK ID refresh failed ($errorCode): $errorMsg")

/**
 * Тонкий клиент VK API поверх JDK HttpClient (specs/121-vk-news-auto-publish).
 *
 * Реализует методы:
 * - `wall.post` (создание поста в группе, FR-001)
 * - `video.save` + загрузка видеофайла (прикрепление демо-MP4, FR-019)
 * - `photos.getWallUploadServer` + `photos.saveWallPhoto` (прикрепление превью-фото, specs/138)
 * - `docs.getWallUploadServer` + `docs.save` (fallback для превью через документ-картинку, specs/138)
 *
 * Аутентификация — Community access token (`vkAccessToken`) для большинства методов,
 * user-token (`vkUserAccessToken`) для `video.save` / `photos.*`. Версия API —
 * `vkApiVersion` (дефолт `5.199`).
 *
 * Прокси-fallback — по образцу [TelegramApiClient]: каждый запрос сначала пробует
 * напрямую, при сетевой ошибке переключается на HTTP-прокси (`vkProxyUrl`) на
 * TTL-окно (`vkProxyModeTtlMs`). Если `vkProxyUrl` не задан — ошибка пробрасывается.
 *
 * Retry/backoff (FR-009): 3 попытки с backoff `30с→2мин→5мин` (по образцу
 * [TelegramApiClient.sendVideo]). Non-retryable коды VK API: `4`, `5`, `15`,
 * `27`, `29`, `100` (см. research.md §5).
 *
 * @see docs/features/vk-news-auto-publish.md
 * @see specs/138-vk-photo-preview-attachment/spec.md
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
     * User access token для VK API (specs/151).
     *
     * Приоритет:
     * 1. `vkIdAccessToken` (новый, через VK ID Authorization Code Flow + PKCE, scopes
     *    `vkid.personal_info+photos+wall+video`, живёт ~1 час, обновляется по
     *    `refresh_token` через `VkIdTokenRefreshScheduler`).
     * 2. `vkUserAccessToken` (старый, через Implicit Flow Standalone-приложения, scopes
     *    `video,photos,wall,offline`) — fallback на переходный период.
     *
     * Используется для методов, требующих user scope:
     * - `video.save` — загрузка видео в группу (право `video`).
     * - `photos.getWallUploadServer` / `photos.saveWallPhoto` — загрузка фото на стену группы
     *   (право `photos`, specs/138).
     *
     * Возвращает пустую строку, если user-token не задан — в этом случае `video.save`
     * вернёт понятную ошибку.
     *
     * @see specs/151-vk-id-personal-token/spec.md (FR-007)
     */
    private fun userAccessToken(): String {
        val idToken = KaraokeProperties.getString("vkIdAccessToken")
        return idToken.ifBlank { KaraokeProperties.getString("vkUserAccessToken") }
    }

    /**
     * Обновляет VK ID access_token через refresh_token (specs/151-vk-id-personal-token, FR-005).
     *
     * Вызывается из [com.svoemesto.karaokeapp.services.VkIdTokenRefreshScheduler]
     * (каждый час) и из `/api/utils/vkIdRefreshNow` (ручной refresh через admin API).
     *
     * Flow (RFC 6749, секция 6):
     * 1. Читает `vkIdClientId`, `vkIdClientSecret`, `vkIdRefreshToken` из KaraokeProperties.
     * 2. Проверяет, что все 3 настройки заполнены (иначе `VkIdRefreshFailedException`).
     * 3. POST `https://oauth.vk.ru/access_token` с `grant_type=refresh_token`
     *    (VK ID использует старый endpoint для token-фазы; новый только для authorize),
     *    `client_id`, `client_secret`, `refresh_token`.
     * 4. Парсит response: `access_token`, `refresh_token`, `expires_in`, `id_token?`.
     * 5. При `error=invalid_grant` или другом non-recoverable — бросает
     *    [VkIdRefreshFailedException].
     *
     * **Не сохраняет** токены в KaraokeProperties — это делает вызывающий код
     * (scheduler / endpoint).
     *
     * @return [VkIdTokenRefreshResult] с новыми токенами.
     * @throws VkIdRefreshFailedException если refresh не удался.
     * @throws IllegalStateException если настройки не заданы.
     */
    fun refreshVkIdAccessToken(): VkIdTokenRefreshResult {
        val clientId = KaraokeProperties.getLong("vkIdClientId")
        val clientSecret = KaraokeProperties.getString("vkIdClientSecret")
        val refreshToken = KaraokeProperties.getString("vkIdRefreshToken")
        if (clientId <= 0) {
            throw IllegalStateException("vkIdClientId is empty — задайте в Karaoke.properties")
        }
        if (clientSecret.isBlank()) {
            throw IllegalStateException("vkIdClientSecret is empty — задайте в Karaoke.properties")
        }
        if (refreshToken.isBlank()) {
            throw VkIdRefreshFailedException(
                errorCode = "missing_refresh_token",
                errorMsg = "vkIdRefreshToken is empty — нужен повторный OAuth flow",
            )
        }
        val params =
            buildString {
                append("grant_type=refresh_token")
                append("&client_id=").append(clientId)
                append("&client_secret=").append(java.net.URLEncoder.encode(clientSecret, "UTF-8"))
                append("&refresh_token=").append(java.net.URLEncoder.encode(refreshToken, "UTF-8"))
            }
        val uri = URI("https://oauth.vk.ru/access_token")
        val request =
            HttpRequest
                .newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(params))
                .build()
        val response = send(request)
        val bodyText = response.body()
        if (response.statusCode() !in 200..299) {
            throw VkIdRefreshFailedException(
                errorCode = "http_${response.statusCode()}",
                errorMsg = "VK ID вернул HTTP ${response.statusCode()}: ${bodyText.take(300)}",
            )
        }
        val parsed =
            try {
                json.decodeFromString(VkIdTokenRefreshResponse.serializer(), bodyText)
            } catch (e: Exception) {
                throw VkIdRefreshFailedException(
                    errorCode = "invalid_response",
                    errorMsg = "Не удалось разобрать ответ VK ID: ${e.message}",
                )
            }
        if (parsed.error != null) {
            throw VkIdRefreshFailedException(
                errorCode = parsed.error,
                errorMsg = parsed.errorDescription ?: "no description",
            )
        }
        val newAccess = parsed.accessToken
        val newRefresh = parsed.refreshToken
        if (newAccess.isNullOrBlank() || newRefresh.isNullOrBlank()) {
            throw VkIdRefreshFailedException(
                errorCode = "missing_tokens_in_response",
                errorMsg = "VK ID не вернул access_token или refresh_token",
            )
        }
        return VkIdTokenRefreshResult(
            accessToken = newAccess,
            refreshToken = newRefresh,
            expiresIn = parsed.expiresIn ?: 3600L,
            idToken = parsed.idToken,
        )
    }

    /** Ответ `/oauth2/token` для refresh (или auth-code) flow. */
    @Serializable
    private data class VkIdTokenRefreshResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("id_token") val idToken: String? = null,
        @SerialName("user_id") val userId: String? = null,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
    )

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
     * @param attachments Строка прикреплений через запятую (например, `photo-123_456,video-123_789`)
     *   или null. Первое прикрепление — превью поста (specs/138).
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
                // specs/138: логируем сценарий attachments для диагностики превью.
                val attachSummary =
                    when {
                        attachments.isNullOrBlank() -> "text-only"
                        attachments.contains("photo") && attachments.contains("video") -> "photo+video"
                        attachments.contains("photo") -> "photo-only"
                        else -> "video-only"
                    }
                println("VkApiClient.wallPost: success on attempt $attempt, post_id=$postId, attachments=$attachSummary")
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
     * Опционально — прикрепление фото-превью через `photoAttachment` (specs/138). Порядок:
     * сначала `photo`, потом `video` (VK берёт первое как превью).
     *
     * @param groupId ID группы без минуса.
     * @param message Текст поста.
     * @param videoFile Демо-MP4 файл (проверка размера перед отправкой, FR-020).
     * @param songId id песни (для имени video).
     * @param photoAttachment Опциональное прикрепление фото в формате `photo<owner>_<id>`
     *   (для `wall.post attachments`). Если `null` — только видео.
     * @return [VkAutoPublishResult] с `postId` при успехе.
     */
    fun sendPostWithVideo(
        groupId: String,
        message: String,
        videoFile: java.io.File,
        songId: Long,
        photoAttachment: String? = null,
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
        // Шаг 3: wall.post с attachments (photo первым, video вторым — VK берёт первое как превью)
        val videoAttachment = "video${save.ownerId}_${save.videoId}"
        val attachments =
            if (!photoAttachment.isNullOrBlank()) "$photoAttachment,$videoAttachment" else videoAttachment
        return wallPost(groupId, message, attachments = attachments)
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
    ): ByteArray =
        buildMultipartBody(
            fieldName = "video_file",
            fileName = videoFileName,
            contentType = "video/mp4",
            bytes = videoBytes,
            boundary = boundary,
        )

    /**
     * Общий helper для multipart/form-data с одним полем (используется для
     * `photos.*`, `docs.*` и `video.save`). Формат — стандартный RFC 7578.
     *
     * @see docs/features/vk-news-auto-publish.md
     */
    private fun buildMultipartBody(
        fieldName: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
        boundary: String,
    ): ByteArray {
        val crlf = "\r\n"
        val out = ByteArrayOutputStream()
        out.write("--$boundary$crlf".toByteArray(Charsets.UTF_8))
        out.write(
            "Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"$crlf".toByteArray(Charsets.UTF_8),
        )
        out.write("Content-Type: $contentType$crlf$crlf".toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.write(crlf.toByteArray(Charsets.UTF_8))
        out.write("--$boundary--$crlf".toByteArray(Charsets.UTF_8))
        return out.toByteArray()
    }

    // ============== photos.* / docs.* методы (specs/138) ==============

    /**
     * Шаг 1 для `photos.*`: получить URL загрузки PNG-обложки.
     * Требует user-token с scope `photos`. При `error_code in 27/15/5/29` —
     * бросает [VkPhotoAuthException] (триггер fallback на `docs.*`).
     *
     * @see docs/features/vk-news-auto-publish.md
     */
    fun getWallUploadServer(
        groupId: String,
        userToken: String,
    ): String {
        if (userToken.isBlank()) {
            throw VkPhotoAuthException(0, "vkUserAccessToken is empty — нужен user-token с правом photos")
        }
        val params =
            buildString {
                append("group_id=")
                append(groupId)
                append("&access_token=")
                append(userToken)
                append("&v=")
                append(apiVersion())
            }
        val uri = URI("${baseUrl()}/photos.getWallUploadServer")
        val request =
            HttpRequest
                .newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(params))
                .build()
        val response = send(request)
        val parsed = json.decodeFromString(VkPhotosGetWallUploadServerResponse.serializer(), response.body())
        if (parsed.error != null) {
            val code = parsed.error.errorCode
            val msg = parsed.error.errorMsg
            throw when (code) {
                in setOf(27, 15, 5, 29) -> VkPhotoAuthException(code, msg)
                100 -> VkPhotoInvalidParamsException(code, msg)
                else -> VkPhotoTransientException(code, msg)
            }
        }
        val uploadUrl = parsed.response?.uploadUrl ?: ""
        if (uploadUrl.isBlank()) {
            throw VkPhotoTransientException(0, "photos.getWallUploadServer empty response.upload_url")
        }
        return uploadUrl
    }

    /**
     * Шаг 2 для `photos.*`: POST multipart на `upload_url` с PNG (поле `photo`).
     * Возвращает сырой JSON-ответ сервера (`server`, `photo`, `hash`) для
     * следующего шага `photos.saveWallPhoto`.
     */
    fun uploadPhotoFile(
        uploadUrl: String,
        pngBytes: ByteArray,
        fileName: String = "cover.png",
    ): VkPhotoUploadResponseRaw {
        val boundary = "karaoke-${System.currentTimeMillis()}"
        val body =
            buildMultipartBody(
                fieldName = "photo",
                fileName = fileName,
                contentType = "image/png",
                bytes = pngBytes,
                boundary = boundary,
            )
        val uri = URI(uploadUrl)
        val request =
            HttpRequest
                .newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build()
        val response = send(request)
        if (response.statusCode() !in 200..299) {
            throw VkPhotoTransientException(response.statusCode(), "upload_url returned HTTP ${response.statusCode()}")
        }
        val bodyText = response.body()
        if (bodyText.isBlank()) {
            throw VkPhotoTransientException(0, "upload_url returned empty body")
        }
        return try {
            json.decodeFromString(VkPhotoUploadResponseRaw.serializer(), bodyText)
        } catch (e: Exception) {
            throw VkPhotoTransientException(0, "upload_url returned invalid JSON: ${e.message}")
        }
    }

    /**
     * Шаг 3 для `photos.*`: сохранить загруженный файл как фото на стене группы.
     * Требует user-token с scope `photos`. Возвращает [PhotoAttachment].
     */
    fun saveWallPhoto(
        server: Long,
        photoJson: String,
        hash: String,
        groupId: String,
        userToken: String,
    ): PhotoAttachment {
        if (userToken.isBlank()) {
            throw VkPhotoAuthException(0, "vkUserAccessToken is empty — нужен user-token с правом photos")
        }
        val params =
            buildString {
                append("server=")
                append(server)
                append("&photo=")
                append(java.net.URLEncoder.encode(photoJson, "UTF-8"))
                append("&hash=")
                append(java.net.URLEncoder.encode(hash, "UTF-8"))
                append("&group_id=")
                append(groupId)
                append("&access_token=")
                append(userToken)
                append("&v=")
                append(apiVersion())
            }
        val uri = URI("${baseUrl()}/photos.saveWallPhoto")
        val request =
            HttpRequest
                .newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(params))
                .build()
        val response = send(request)
        val parsed = json.decodeFromString(VkPhotosSaveWallPhotoResponse.serializer(), response.body())
        if (parsed.error != null) {
            val code = parsed.error.errorCode
            val msg = parsed.error.errorMsg
            throw when (code) {
                in setOf(27, 15, 5, 29) -> VkPhotoAuthException(code, msg)
                100 -> VkPhotoInvalidParamsException(code, msg)
                else -> VkPhotoTransientException(code, msg)
            }
        }
        val first =
            parsed.response?.firstOrNull()
                ?: throw VkPhotoTransientException(0, "photos.saveWallPhoto empty response")
        return PhotoAttachment(
            id = first.id,
            ownerId = first.ownerId,
            attachment = "photo${first.ownerId}_${first.id}",
            loadMethod = PhotoUploadMethod.PHOTOS,
        )
    }

    /**
     * Fallback Шаг 1: получить URL для загрузки документа-картинки через `docs.*`.
     * Использует **community-token** (`vkAccessToken`), т.к. право `docs` доступно
     * сообществам по умолчанию.
     */
    fun getDocWallUploadServer(communityToken: String): String {
        if (communityToken.isBlank()) {
            throw VkPhotoAuthException(0, "vkAccessToken (community) is empty")
        }
        val params =
            buildString {
                append("access_token=")
                append(communityToken)
                append("&v=")
                append(apiVersion())
            }
        val uri = URI("${baseUrl()}/docs.getWallUploadServer")
        val request =
            HttpRequest
                .newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(params))
                .build()
        val response = send(request)
        val parsed = json.decodeFromString(VkDocsGetWallUploadServerResponse.serializer(), response.body())
        if (parsed.error != null) {
            val code = parsed.error.errorCode
            val msg = parsed.error.errorMsg
            throw when (code) {
                in setOf(27, 15, 5, 29) -> VkPhotoAuthException(code, msg)
                100 -> VkPhotoInvalidParamsException(code, msg)
                else -> VkPhotoTransientException(code, msg)
            }
        }
        val uploadUrl = parsed.response?.uploadUrl ?: ""
        if (uploadUrl.isBlank()) {
            throw VkPhotoTransientException(0, "docs.getWallUploadServer empty response.upload_url")
        }
        return uploadUrl
    }

    /**
     * Fallback Шаг 2: POST multipart на `upload_url` с PNG (поле `file`).
     * Возвращает сырой JSON-ответ (`file` — JSON-строка) для следующего шага.
     */
    fun uploadDocFile(
        uploadUrl: String,
        pngBytes: ByteArray,
        fileName: String = "cover.png",
    ): VkDocUploadResponseRaw {
        val boundary = "karaoke-${System.currentTimeMillis()}"
        val body =
            buildMultipartBody(
                fieldName = "file",
                fileName = fileName,
                contentType = "image/png",
                bytes = pngBytes,
                boundary = boundary,
            )
        val uri = URI(uploadUrl)
        val request =
            HttpRequest
                .newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build()
        val response = send(request)
        if (response.statusCode() !in 200..299) {
            throw VkPhotoTransientException(response.statusCode(), "docs upload_url returned HTTP ${response.statusCode()}")
        }
        val bodyText = response.body()
        if (bodyText.isBlank()) {
            throw VkPhotoTransientException(0, "docs upload_url returned empty body")
        }
        return try {
            json.decodeFromString(VkDocUploadResponseRaw.serializer(), bodyText)
        } catch (e: Exception) {
            throw VkPhotoTransientException(0, "docs upload_url returned invalid JSON: ${e.message}")
        }
    }

    /**
     * Fallback Шаг 3: сохранить документ-картинку через `docs.save`.
     * Возвращает [DocAttachment] для прикрепления через `attachments=doc<owner>_<id>`.
     */
    fun saveWallDoc(
        fileJson: String,
        title: String,
        communityToken: String,
    ): DocAttachment {
        if (communityToken.isBlank()) {
            throw VkPhotoAuthException(0, "vkAccessToken (community) is empty")
        }
        val params =
            buildString {
                append("file=")
                append(java.net.URLEncoder.encode(fileJson, "UTF-8"))
                append("&title=")
                append(java.net.URLEncoder.encode(title, "UTF-8"))
                append("&access_token=")
                append(communityToken)
                append("&v=")
                append(apiVersion())
            }
        val uri = URI("${baseUrl()}/docs.save")
        val request =
            HttpRequest
                .newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(params))
                .build()
        val response = send(request)
        val parsed = json.decodeFromString(VkDocsSaveResponse.serializer(), response.body())
        if (parsed.error != null) {
            val code = parsed.error.errorCode
            val msg = parsed.error.errorMsg
            throw when (code) {
                in setOf(27, 15, 5, 29) -> VkPhotoAuthException(code, msg)
                100 -> VkPhotoInvalidParamsException(code, msg)
                else -> VkPhotoTransientException(code, msg)
            }
        }
        val saved =
            parsed.response
                ?: throw VkPhotoTransientException(0, "docs.save empty response")
        return DocAttachment(
            id = saved.id,
            ownerId = saved.ownerId,
            attachment = "doc${saved.ownerId}_${saved.id}",
        )
    }

    companion object {
        // VK API error_code, при которых retry бесполезен (FR-009 non-retryable).
        // 4 — Incorrect signature; 5 — User authorization failed; 15 — Access denied;
        // 27 — Group authorization failed (для photos.* / docs.* — триггер fallback);
        // 100 — One of the parameters is missing or invalid.
        // 29 — Rate limit (для photos.* — non-retryable внутри метода, fallback на docs.*).
        private val NON_RETRYABLE_ERROR_CODES = setOf(4, 5, 15, 27, 100, 29)

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
