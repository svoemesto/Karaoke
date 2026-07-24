package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class AlignSyllableDto(
    val label: String,
    @SerialName("start_ms") val startMs: Long,
    @SerialName("end_ms") val endMs: Long,
)

@Serializable
data class AlignResponseDto(
    val ok: Boolean = false,
    val syllables: List<AlignSyllableDto> = emptyList(),
)

/**
 * Тонкий клиент над forced-alignment сервисом (alignment-ml/serve.py, FastAPI) — точная
 * расстановка маркеров по уже ИЗВЕСТНОМУ тексту (в отличие от Whisper ASR, который транскрибирует
 * вслепую, см. WhisperAsrService). Тот же паттерн клиента (OkHttp, multipart), другой контракт.
 */
object AlignmentServiceClient {
    private val json = Json { ignoreUnknownKeys = true }

    fun align(
        audioFile: File,
        text: String,
    ): AlignResponseDto? {
        val url = KaraokeProperties.getString("alignmentServiceUrl")
        if (url.isBlank()) return null
        if (!audioFile.exists()) return null

        val timeoutMs = KaraokeProperties.getLong("alignmentServiceTimeoutMs").takeIf { it > 0 } ?: 300_000L

        val client =
            OkHttpClient
                .Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()

        val mediaType = "audio/flac".toMediaTypeOrNull()
        val requestBody =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(mediaType))
                .addFormDataPart("text", text)
                .build()

        val request =
            Request
                .Builder()
                .url(url)
                .post(requestBody)
                .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyString = response.body?.string() ?: return null
                json.decodeFromString(AlignResponseDto.serializer(), bodyString)
            }
        } catch (_: Exception) {
            null
        }
    }
}
