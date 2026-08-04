package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties

/**
 * Результат одной попытки загрузки превью-обложки для ВК (specs/138-vk-photo-preview-attachment).
 * Не персистится в БД — используется только [VkAutoPublishService] и логами.
 *
 * @property method Способ, которым удалось загрузить: [PhotoUploadMethod.PHOTOS],
 *   [PhotoUploadMethod.DOCS] или [PhotoUploadMethod.NONE] (деградация).
 * @property attachment Готовая строка для `wall.post attachments` (например,
 *   `photo-123_456` или `doc-123_789`). `null` при деградации.
 * @property error Краткое описание ошибки (для логов), `null` при успехе.
 *
 * @see docs/features/vk-news-auto-publish.md
 */
data class PhotoUploadResult(
    val method: PhotoUploadMethod,
    val attachment: String?,
    val error: String? = null,
)

/**
 * HTTP-оркестратор загрузки PNG-обложки песни в группу ВКонтакте через `photos.*`
 * с fallback на `docs.*` (specs/138-vk-photo-preview-attachment).
 *
 * Вызывается из [VkAutoPublishService.publishFile] / [VkAutoPublishService.publishTextOnly]
 * непосредственно после [VkPreviewWarmupClient.warmup] (когда PNG уже сгенерирован и
 * сохранён в `/tmp/vk_<id>.png`) и до `video.save` + `wall.post`.
 *
 * Поток:
 * 1. Если `vkPhotoAttachEnabled=true` (default) — пробуем `photos.*`:
 *    `photos.getWallUploadServer` → POST multipart на `upload_url` → `photos.saveWallPhoto`.
 *    Использует **user-token** (`vkUserAccessToken`, scope `photos`).
 * 2. При ошибке авторизации `error_code in 27/15/5/29` — fallback на `docs.*`
 *    (если `vkDocAttachEnabled=true`): `docs.getWallUploadServer` → POST multipart
 *    → `docs.save`. Использует **community-token** (`vkAccessToken`).
 * 3. При полном сбое (оба метода не сработали) — бросает [VkBothAttachFailedException],
 *    который [VkAutoPublishService] обрабатывает как деградацию (пост без превью).
 *
 * При `error_code=100` (Invalid params) или transient-сетевых ошибках после retry —
 * бросает соответствующее исключение (`VkPhotoInvalidParamsException` /
 * `VkPhotoTransientException`), которое [VkAutoPublishService] обрабатывает как
 * `SEND_FAILED` с префиксом `photo upload failed:`.
 *
 * @see docs/features/vk-news-auto-publish.md
 * @see specs/138-vk-photo-preview-attachment/spec.md
 */
class VkPhotoUploadClient(
    private val apiClient: VkApiClient = VkApiClient(),
    private val userToken: String = KaraokeProperties.getString("vkUserAccessToken"),
    private val communityToken: String = KaraokeProperties.getString("vkAccessToken"),
    private val photoEnabled: Boolean = KaraokeProperties.getBoolean("vkPhotoAttachEnabled"),
    private val docEnabled: Boolean = KaraokeProperties.getBoolean("vkDocAttachEnabled"),
) {
    /** Число попыток transient-ошибок внутри одной PHOTOS/DOCS-цепочки. */
    private val transientAttempts: Int = 2

    /** Задержка между попытками transient-ошибок (мс). */
    private val transientBackoffMs: Long = 5_000L

    /**
     * Загружает PNG-обложку [pngBytes] для песни [songId] в группу [groupId].
     * Возвращает [PhotoUploadResult] при успехе. При полном сбое обоих методов бросает
     * [VkBothAttachFailedException]. При non-retryable / persistent transient — бросает
     * [VkPhotoInvalidParamsException] / [VkPhotoTransientException] / [VkPhotoUploadException].
     */
    fun uploadCover(
        songId: Long,
        pngBytes: ByteArray,
        groupId: String,
    ): PhotoUploadResult {
        // Если оба метода отключены — деградация без попыток.
        if (!photoEnabled && !docEnabled) {
            return PhotoUploadResult(
                method = PhotoUploadMethod.NONE,
                attachment = null,
                error = "both vkPhotoAttachEnabled and vkDocAttachEnabled are false",
            )
        }

        val photosError: VkPhotoUploadException? =
            if (photoEnabled) {
                try {
                    return tryPhotosPath(songId, pngBytes, groupId)
                } catch (e: VkPhotoAuthException) {
                    // 27/15/5/29 — fallback на docs.* (если разрешён)
                    println("VkPhotoUploadClient.uploadCover: photos.* auth error ${e.errorCode}: ${e.errorMsg}, falling back to docs.*")
                    e
                } catch (e: VkPhotoInvalidParamsException) {
                    // 100 — non-retryable, без fallback: это наша ошибка, не VK
                    throw e
                } catch (e: VkPhotoTransientException) {
                    // transient после retry — без fallback: скорее всего, VK недоступен
                    throw e
                } catch (e: VkPhotoUploadException) {
                    // прочее (например, upload_url empty / invalid JSON) — без fallback
                    throw e
                }
            }
        } else null

        // Fallback на docs.* (если включён и был photosError)
        if (!docEnabled) {
            // doc отключён: если photosError есть — пробросить его (VkAutoPublishService поставит SEND_FAILED);
            // если photos был отключён И doc отключён — выше уже вернули NONE.
            if (photosError != null) {
                throw photosError
            }
            return PhotoUploadResult(PhotoUploadMethod.NONE, null, "vkPhotoAttachEnabled=false, vkDocAttachEnabled=false (handled above)")
        }

        val docsError: VkPhotoUploadException? =
            try {
                return tryDocsPath(songId, pngBytes, groupId)
            } catch (e: VkPhotoAuthException) {
                println("VkPhotoUploadClient.uploadCover: docs.* auth error ${e.errorCode}: ${e.errorMsg}")
                e
            } catch (e: VkPhotoInvalidParamsException) {
                // 100 — non-retryable: наша ошибка
                throw e
            } catch (e: VkPhotoTransientException) {
                // transient после retry: скорее всего VK недоступен
                throw e
            } catch (e: VkPhotoUploadException) {
                e
            }

        // Оба метода не сработали
        if (photosError != null && docsError != null) {
            throw VkBothAttachFailedException(photosError, docsError)
        }
        // docEnabled=true, photosError=null, docsError=null — сюда не попадём (tryDocsPath вернёт PhotoUploadResult)
        // docEnabled=true, photosError=null (photos был выключен), docsError!=null — деградация
        return PhotoUploadResult(PhotoUploadMethod.NONE, null, "docs.* failed: ${docsError?.message}")
    }

    /** Попытка загрузки через `photos.*` (user-token). С retry для transient. */
    private fun tryPhotosPath(
        songId: Long,
        pngBytes: ByteArray,
        groupId: String,
    ): PhotoUploadResult {
        if (userToken.isBlank()) {
            throw VkPhotoAuthException(0, "vkUserAccessToken is empty — нужен user-token с правом photos (см. /api/utils/vkOAuthUrl)")
        }
        var lastTransient: VkPhotoTransientException? = null
        for (attempt in 1..transientAttempts) {
            try {
                val uploadUrl = apiClient.getWallUploadServer(groupId, userToken)
                val raw = apiClient.uploadPhotoFile(uploadUrl, pngBytes, "$songId.png")
                if (raw.server <= 0 || raw.photo.isBlank() || raw.hash.isBlank()) {
                    throw VkPhotoUploadException("upload_url returned invalid JSON: server=${raw.server}, photo.len=${raw.photo.length}, hash.len=${raw.hash.length}")
                }
                val photo = apiClient.saveWallPhoto(raw.server, raw.photo, raw.hash, groupId, userToken)
                return PhotoUploadResult(PhotoUploadMethod.PHOTOS, photo.attachment, null)
            } catch (e: VkPhotoTransientException) {
                lastTransient = e
                println("VkPhotoUploadClient.uploadCover: photos.* transient on attempt $attempt/$transientAttempts: ${e.errorCode} ${e.errorMsg}")
                if (attempt < transientAttempts) {
                    sleep(transientBackoffMs)
                }
            }
        }
        throw lastTransient ?: VkPhotoTransientException(0, "unknown transient failure")
    }

    /** Попытка загрузки через `docs.*` (community-token, fallback). С retry для transient. */
    private fun tryDocsPath(
        songId: Long,
        pngBytes: ByteArray,
        groupId: String,
    ): PhotoUploadResult {
        if (communityToken.isBlank()) {
            throw VkPhotoAuthException(0, "vkAccessToken (community) is empty — нужен для docs.* fallback")
        }
        var lastTransient: VkPhotoTransientException? = null
        for (attempt in 1..transientAttempts) {
            try {
                val uploadUrl = apiClient.getDocWallUploadServer(communityToken)
                val raw = apiClient.uploadDocFile(uploadUrl, pngBytes, "$songId.png")
                if (raw.file.isBlank()) {
                    throw VkPhotoUploadException("docs upload_url returned invalid JSON: file is blank")
                }
                val doc = apiClient.saveWallDoc(raw.file, "$songId.png", communityToken)
                return PhotoUploadResult(PhotoUploadMethod.DOCS, doc.attachment, null)
            } catch (e: VkPhotoTransientException) {
                lastTransient = e
                println("VkPhotoUploadClient.uploadCover: docs.* transient on attempt $attempt/$transientAttempts: ${e.errorCode} ${e.errorMsg}")
                if (attempt < transientAttempts) {
                    sleep(transientBackoffMs)
                }
            }
        }
        throw lastTransient ?: VkPhotoTransientException(0, "unknown transient failure")
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
