package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.imageio.ImageIO

/**
 * Результат одного вызова прогрева превью перед публикацией в ВК
 * (specs/130-vk-preview-generation). Не персистится в БД — используется
 * только оркестратором [VkAutoPublishService] и логами.
 *
 * @property status SUCCESS / FAILED / BYPASS.
 * @property songId id песни, для которой выполнялся прогрев.
 * @property attempts Фактическое число HTTP-попыток (1..maxAttempts).
 * @property httpStatus Полученный HTTP-код (`null`, если ответ не дошёл).
 * @property contentType MIME-тип ответа (`null`, если ответ не дошёл).
 * @property bytes Размер тела ответа (0 при ошибке).
 * @property durationMs Длительность прогрева для диагностики гипотезы.
 * @error Краткая причина без токенов и путей к файлам.
 * @see docs/features/vk-news-auto-publish.md
 */
data class VkPreviewWarmupResult(
    val status: VkPreviewWarmupStatus,
    val songId: Long,
    val attempts: Int = 0,
    val httpStatus: Int? = null,
    val contentType: String? = null,
    val bytes: Int = 0,
    val durationMs: Long = 0,
    val error: String? = null,
    /**
     * PNG-байты успешно прогретого превью (только при [VkPreviewWarmupStatus.SUCCESS]).
     * Используется [VkPhotoUploadClient.uploadCover] для загрузки в VK (specs/138).
     * `null` при BYPASS/FAILED — загружать в VK нечего.
     */
    val pngBytes: ByteArray? = null,
)

/** Состояние одной попытки прогрева превью (specs/130-vk-preview-generation). */
enum class VkPreviewWarmupStatus {
    /** Прогрев отключён настройкой `vkPreviewWarmupEnabled=false` (аварийный откат). */
    BYPASS,

    /** Получен HTTP 200 и валидный PNG, готов к публикации. */
    SUCCESS,

    /** Любой сбой — тайм-аут, не-200, пустое тело, повреждённый PNG, сетевая ошибка. */
    FAILED,
}

/**
 * HTTP-помощник синхронного прогрева публичного изображения песни
 * (specs/130-vk-preview-generation, specs/138-vk-photo-preview-attachment).
 *
 * Вызывается из [VkAutoPublishService] непосредственно перед `wall.post` /
 * `sendPostWithVideo`. Успех (`VkPreviewWarmupStatus.SUCCESS`) подтверждает,
 * что `karaoke-web` завершил генерацию и сохранил PNG в `/tmp/vk_<id>.png`;
 * последующий запрос VK-бота к тому же URL попадает в быстрый путь чтения
 * кэша. Неудача (`FAILED`) блокирует публикацию и записывается через
 * существующий [VkAutoPublishState.SEND_FAILED] с префиксом
 * `preview prewarm failed:`.
 *
 * Начиная со specs/138 результат содержит `pngBytes` — готовый PNG (1200×630,
 * стандарт Open Graph) для немедленной загрузки в VK через [VkPhotoUploadClient]
 * без повторного GET к `/api/public/song-vk-image/{id}`. Размер 1200×630
 * контролируется на стороне `karaoke-web` через `vkPreviewImageWidth/Height`
 * (см. `PublicApiController.songVkImage`); здесь выполняется только проверка
 * PNG magic-signature через `ImageIO.read`.
 *
 * Конструктор принимает параметры напрямую — прод-значения по умолчанию
 * читаются из [KaraokeProperties] (для тестов удобно подменять).
 *
 * Особенности:
 * - `Redirect.NEVER` — 3xx считается ошибкой (fallback-логотип не должен
 *   маскироваться под превью песни).
 * - Retry только для transient-сетевых ошибок и HTTP 5xx; HTTP 4xx (кроме
 *   429) — non-retryable.
 * - PNG валидируется по `Content-Type: image/png` И `ImageIO.read`.
 * - Тело ответа и тела ошибок НЕ логируются (только метаданные).
 *
 * @see docs/features/vk-news-auto-publish.md
 */
class VkPreviewWarmupClient(
    private val baseUrl: String = KaraokeProperties.getString("vkPreviewWarmupUrl").trim().trimEnd('/'),
    private val timeoutMs: Long = KaraokeProperties.getLong("vkPreviewWarmupTimeoutMs").coerceAtLeast(1_000L),
    private val maxAttempts: Int = KaraokeProperties.getLong("vkPreviewWarmupMaxAttempts").toInt().coerceAtLeast(1),
    private val enabled: Boolean = KaraokeProperties.getBoolean("vkPreviewWarmupEnabled"),
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build(),
) {
    /**
     * Выполняет прогрев превью для [songId]. Возвращает [VkPreviewWarmupResult]
     * со статусом и диагностикой. При `enabled=false` возвращает
     * [VkPreviewWarmupStatus.BYPASS] без обращения к сети.
     *
     * @param songId Положительный id песни.
     * @return [VkPreviewWarmupResult] — никогда не null.
     */
    fun warmup(songId: Long): VkPreviewWarmupResult {
        if (!enabled) {
            return VkPreviewWarmupResult(
                status = VkPreviewWarmupStatus.BYPASS,
                songId = songId,
                error = "vkPreviewWarmupEnabled=false (bypass)",
            )
        }
        if (baseUrl.isBlank()) {
            return VkPreviewWarmupResult(
                status = VkPreviewWarmupStatus.FAILED,
                songId = songId,
                error = "vkPreviewWarmupUrl is empty",
            )
        }
        val url = "$baseUrl/$songId"
        val started = System.currentTimeMillis()
        var lastResult: VkPreviewWarmupResult =
            VkPreviewWarmupResult(
                status = VkPreviewWarmupStatus.FAILED,
                songId = songId,
                error = "no attempts made",
            )
        for (attempt in 1..maxAttempts) {
            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .GET()
                    .build()
            val outcome =
                try {
                    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
                    val bytes = response.body()
                    val contentType = response.headers().firstValue("content-type").orElse("")
                    classifyResponse(songId, attempt, response.statusCode(), contentType, bytes, started)
                } catch (e: java.net.http.HttpTimeoutException) {
                    VkPreviewWarmupResult(
                        status = VkPreviewWarmupStatus.FAILED,
                        songId = songId,
                        attempts = attempt,
                        durationMs = System.currentTimeMillis() - started,
                        error = "timeout after ${timeoutMs}ms",
                    )
                } catch (e: java.net.ConnectException) {
                    VkPreviewWarmupResult(
                        status = VkPreviewWarmupStatus.FAILED,
                        songId = songId,
                        attempts = attempt,
                        durationMs = System.currentTimeMillis() - started,
                        error = "connect: ${e.message ?: e.javaClass.simpleName}",
                    )
                } catch (e: java.io.IOException) {
                    VkPreviewWarmupResult(
                        status = VkPreviewWarmupStatus.FAILED,
                        songId = songId,
                        attempts = attempt,
                        durationMs = System.currentTimeMillis() - started,
                        error = "io: ${e.message ?: e.javaClass.simpleName}",
                    )
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return VkPreviewWarmupResult(
                        status = VkPreviewWarmupStatus.FAILED,
                        songId = songId,
                        attempts = attempt,
                        durationMs = System.currentTimeMillis() - started,
                        error = "interrupted",
                    )
                }
            lastResult = outcome
            if (outcome.status == VkPreviewWarmupStatus.SUCCESS) {
                return outcome
            }
            // 3xx/4xx (кроме 429) — non-retryable, выходим сразу
            val httpStatus = outcome.httpStatus
            if (httpStatus != null && httpStatus in 300..499 && httpStatus != 429) {
                return outcome
            }
        }
        return lastResult.copy(durationMs = System.currentTimeMillis() - started)
    }

    /**
     * Классифицирует HTTP-ответ: SUCCESS только при 200 + ненулевом теле +
     * `Content-Type: image/png` ИЛИ успешном декодировании через `ImageIO`.
     */
    private fun classifyResponse(
        songId: Long,
        attempt: Int,
        status: Int,
        contentType: String,
        bytes: ByteArray,
        started: Long,
    ): VkPreviewWarmupResult {
        if (status in 300..399) {
            return VkPreviewWarmupResult(
                status = VkPreviewWarmupStatus.FAILED,
                songId = songId,
                attempts = attempt,
                httpStatus = status,
                contentType = contentType,
                bytes = bytes.size,
                durationMs = System.currentTimeMillis() - started,
                error = "redirect (HTTP $status, no follow)",
            )
        }
        if (status != 200) {
            return VkPreviewWarmupResult(
                status = VkPreviewWarmupStatus.FAILED,
                songId = songId,
                attempts = attempt,
                httpStatus = status,
                contentType = contentType,
                bytes = bytes.size,
                durationMs = System.currentTimeMillis() - started,
                error = "HTTP $status",
            )
        }
        if (bytes.isEmpty()) {
            return VkPreviewWarmupResult(
                status = VkPreviewWarmupStatus.FAILED,
                songId = songId,
                attempts = attempt,
                httpStatus = status,
                contentType = contentType,
                bytes = 0,
                durationMs = System.currentTimeMillis() - started,
                error = "empty body",
            )
        }
        // Всегда декодируем PNG через ImageIO: Content-Type может быть подделан, а нам нужен
        // именно корректный файл изображения, который затем отдастся VK-боту.
        val decodesAsPng =
            try {
                val img: BufferedImage? = ImageIO.read(ByteArrayInputStream(bytes))
                img != null && img.width > 0 && img.height > 0
            } catch (_: Exception) {
                false
            }
        if (!decodesAsPng) {
            return VkPreviewWarmupResult(
                status = VkPreviewWarmupStatus.FAILED,
                songId = songId,
                attempts = attempt,
                httpStatus = status,
                contentType = contentType,
                bytes = bytes.size,
                durationMs = System.currentTimeMillis() - started,
                error = "not a valid PNG (content-type='$contentType')",
            )
        }
        return VkPreviewWarmupResult(
            status = VkPreviewWarmupStatus.SUCCESS,
            songId = songId,
            attempts = attempt,
            httpStatus = status,
            contentType = contentType,
            bytes = bytes.size,
            durationMs = System.currentTimeMillis() - started,
            pngBytes = bytes,
        )
    }
}
