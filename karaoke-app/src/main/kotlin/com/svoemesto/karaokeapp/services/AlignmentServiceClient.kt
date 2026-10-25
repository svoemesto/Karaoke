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
import org.slf4j.LoggerFactory
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

    /**
     * BR-137 (аудит #336, пакет `PKG-S3-23`, OpenProject #395): категория логов клиента
     * forced-alignment.
     */
    private val log = LoggerFactory.getLogger("infra.alignment")

    /**
     * BR-137: ОДИН `OkHttpClient` на сервис, а не новый на каждый вызов.
     *
     * Раньше клиент создавался внутри `align()` — со своим пулом соединений и dispatcher'ом.
     * На серию вызовов (цикл по голосам в `Utils.executeForcedAlignMarkers`) это давало
     * churn потоков и сокетов. Теперь клиент кэшируется; при смене `callTimeout` в
     * настройках он пересоздаётся (таймаут задаётся конфигурацией, а не константой).
     */
    @Volatile private var cachedClient: OkHttpClient? = null

    @Volatile private var cachedTimeoutMs: Long = -1L

    private val clientLock = Any()

    internal fun httpClient(timeoutMs: Long): OkHttpClient {
        val existing = cachedClient
        if (existing != null && cachedTimeoutMs == timeoutMs) return existing
        synchronized(clientLock) {
            val current = cachedClient
            if (current != null && cachedTimeoutMs == timeoutMs) return current
            val built =
                OkHttpClient
                    .Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .build()
            cachedClient = built
            cachedTimeoutMs = timeoutMs
            return built
        }
    }

    fun align(
        audioFile: File,
        text: String,
        useFinetuned: Boolean = KaraokeProperties.getBoolean("alignmentUseFinetunedModel"),
    ): AlignResponseDto? {
        val url = KaraokeProperties.getString("alignmentServiceUrl")
        if (url.isBlank()) {
            log.warn("align: сервис forced-alignment не настроен (alignmentServiceUrl пуст) — выравнивание пропущено")
            return null
        }
        if (!audioFile.exists()) {
            log.warn("align: аудиофайл '{}' не найден — выравнивание пропущено", audioFile.name)
            return null
        }

        val timeoutMs = KaraokeProperties.getLong("alignmentServiceTimeoutMs").takeIf { it > 0 } ?: 300_000L

        val client = httpClient(timeoutMs)

        val mediaType = "audio/flac".toMediaTypeOrNull()
        val requestBody =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(mediaType))
                .addFormDataPart("text", text)
                .addFormDataPart("use_finetuned", useFinetuned.toString())
                .build()

        val request =
            Request
                .Builder()
                .url(url)
                .post(requestBody)
                .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // BR-137: неуспешный HTTP-ответ — это тоже диагностируемая причина,
                    // раньше она молча превращалась в `null` → «alignment_service_unavailable».
                    log.error("align: сервис forced-alignment ответил кодом {} (файл='{}')", response.code, audioFile.name)
                    return null
                }
                val bodyString = response.body?.string() ?: return null
                json.decodeFromString(AlignResponseDto.serializer(), bodyString)
            }
        } catch (e: Exception) {
            // BR-137: сбой (DNS, таймаут, HTTP 5xx, битый JSON) больше не глотается
            // молча — причина попадает в лог (категория infra.alignment).
            log.error("align: сбой обращения к сервису forced-alignment (файл='{}', тип={})", audioFile.name, e::class.java.simpleName, e)
            null
        }
    }
}
