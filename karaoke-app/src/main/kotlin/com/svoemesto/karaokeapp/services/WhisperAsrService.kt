package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties
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
data class WhisperWordDto(
    val word: String,
    val start: Double,
    val end: Double,
    val confidence: Double = 1.0,
)

@Serializable
data class WhisperSegmentDto(
    val text: String = "",
    val start: Double = 0.0,
    val end: Double = 0.0,
    val words: List<WhisperWordDto> = emptyList(),
)

@Serializable
data class WhisperTranscriptionDto(
    val text: String = "",
    val segments: List<WhisperSegmentDto> = emptyList(),
    val words: List<WhisperWordDto> = emptyList(),
)

/**
 * Тонкий клиент над OpenAI-совместимым эндпоинтом Whisper (faster-whisper, hwdsl2/whisper-server),
 * поднятым на машине админа отдельно от контейнеров этого проекта. Даёт только word-level тайминги —
 * слоговую точность из этого API получить нельзя (см. WhisperMarkerAligner).
 */
object WhisperAsrService {
    private val json = Json { ignoreUnknownKeys = true }

    // BR-159 (аудит #336, пакет `PKG-S3-23`): категория логов ASR.
    private val log = LoggerFactory.getLogger("infra.whisper.asr")

    /**
     * BR-159: результат выравнивания ASR с причиной.
     *
     * `null` — сервис не настроен (нет URL или нет файла) — это НЕ ошибка.
     * [Failed] — сервис ответил ошибкой/таймаутом/битым JSON — раньше всё это сводилось
     * к `null`, и вызывающий отдавал `whisper_unavailable`, неотличимо от «ASR не настроен».
     */
    sealed class Outcome {
        data class Transcribed(
            val dto: WhisperTranscriptionDto,
        ) : Outcome()

        data class Failed(
            val reason: String,
        ) : Outcome()
    }

    // Сегмент-фолбэк: если сервер не отдал word-level (ни верхнеуровнево, ни внутри segments),
    // синтезируем слова из текста сегмента, распределяя время линейно по длине символов слова
    // внутри длительности сегмента. Грубее настоящих word-таймкодов, но лучше, чем ничего.
    private fun synthesizeWordsFromSegment(segment: WhisperSegmentDto): List<WhisperWordDto> {
        val words =
            segment.text
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val totalChars = words.sumOf { it.length }.coerceAtLeast(1)
        val duration = (segment.end - segment.start).coerceAtLeast(0.0)
        var cursor = segment.start
        return words.map { w ->
            val share = duration * w.length / totalChars
            val start = cursor
            val end = cursor + share
            cursor = end
            WhisperWordDto(word = w, start = start, end = end, confidence = 1.0)
        }
    }

    fun flatWords(transcription: WhisperTranscriptionDto): List<WhisperWordDto> {
        if (transcription.words.isNotEmpty()) return transcription.words
        val fromSegments = transcription.segments.flatMap { it.words }
        if (fromSegments.isNotEmpty()) return fromSegments
        return transcription.segments.flatMap { synthesizeWordsFromSegment(it) }
    }

    fun transcribe(audioFile: File): WhisperTranscriptionDto? = transcribeDetailed(audioFile).transcriptionOrNull()

    /**
     * BR-159: `transcribe` с различием «не настроено» и «ошибка».
     *
     * Раньше любой сбой (HTTP-код не 2xx, пустое тело, невалидный JSON, сетевая ошибка,
     * таймаут) возвращал `null`, а вызывающий отдавал `whisper_unavailable` — неотличимо
     * от «сервис не настроен». Теперь причина видна в логе и в результате.
     */
    fun transcribeDetailed(audioFile: File): Outcome {
        val url = KaraokeProperties.getString("whisperAsrUrl")
        if (url.isBlank()) {
            log.info("transcribe: сервис ASR не настроен (whisperAsrUrl пуст) — выравнивание пропущено")
            return Outcome.Failed("asr.not_configured")
        }
        if (!audioFile.exists()) {
            log.warn("transcribe: аудиофайл '{}' не найден — выравнивание пропущено", audioFile.name)
            return Outcome.Failed("asr.file_not_found")
        }

        val timeoutMs = KaraokeProperties.getLong("whisperTimeoutMs").takeIf { it > 0 } ?: 300_000L
        val apiKey = KaraokeProperties.getString("whisperApiKey")
        val language = KaraokeProperties.getString("whisperLanguage").ifBlank { "ru" }

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
                .addFormDataPart("model", "medium")
                .addFormDataPart("language", language)
                .addFormDataPart("response_format", "verbose_json")
                .addFormDataPart("timestamp_granularities[]", "word")
                .build()

        val requestBuilder =
            Request
                .Builder()
                .url(url)
                .post(requestBody)
        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    // BR-159: HTTP-код и тело ошибки — в лог, чтобы было видно, был ли 500
                    // от whisper-server или иной ответ.
                    val body =
                        runCatching {
                            response.body
                                ?.string()
                                .orEmpty()
                                .take(500)
                        }.getOrDefault("")
                    log.error(
                        "transcribe: сервис ASR ответил кодом {} (файл='{}', тело='{}')",
                        response.code,
                        audioFile.name,
                        body,
                    )
                    return Outcome.Failed("asr.http_${response.code}")
                }
                val bodyString =
                    response.body?.string()
                        ?: run {
                            log.error("transcribe: сервис ASR вернул пустое тело (файл='{}')", audioFile.name)
                            return Outcome.Failed("asr.empty_body")
                        }
                try {
                    Outcome.Transcribed(json.decodeFromString(WhisperTranscriptionDto.serializer(), bodyString))
                } catch (e: Exception) {
                    log.error("transcribe: не удалось разобрать ответ сервиса ASR (файл='{}')", audioFile.name, e)
                    Outcome.Failed("asr.invalid_response")
                }
            }
        } catch (e: InterruptedException) {
            // BR-159: прерывание НЕ глотается — поток должен завершиться, а не продолжить
            // работу в «полупрерванном» состоянии.
            log.warn("transcribe: прервано во время обращения к сервису ASR (файл='{}')", audioFile.name)
            Thread.currentThread().interrupt()
            Outcome.Failed("asr.interrupted")
        } catch (e: Exception) {
            log.error("transcribe: сбой обращения к сервису ASR (файл='{}')", audioFile.name, e)
            Outcome.Failed("asr.unavailable")
        }
    }

    /** Удобный доступ к DTO из [Outcome]. */
    private fun Outcome.transcriptionOrNull(): WhisperTranscriptionDto? = (this as? Outcome.Transcribed)?.dto
}
