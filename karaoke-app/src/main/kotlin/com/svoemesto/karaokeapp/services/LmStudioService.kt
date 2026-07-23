package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class LmStudioMessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class LmStudioChatRequestDto(
    val model: String,
    val messages: List<LmStudioMessageDto>,
    val temperature: Double = 0.1,
    val stream: Boolean = false,
)

@Serializable
data class LmStudioChoiceMessageDto(
    val content: String = "",
)

@Serializable
data class LmStudioChoiceDto(
    val message: LmStudioChoiceMessageDto = LmStudioChoiceMessageDto(),
)

@Serializable
data class LmStudioChatResponseDto(
    val choices: List<LmStudioChoiceDto> = emptyList(),
)

/**
 * Тонкий клиент над LM Studio (OpenAI-совместимый `/v1/chat/completions`), поднятым на
 * хост-машине админа отдельно от контейнеров этого проекта (доступен с хоста по
 * http://127.0.0.1:1234, из контейнера — через host.docker.internal). Тот же паттерн тонкого
 * клиента, что и WhisperAsrService/AlignmentServiceClient, только с JSON-body вместо multipart.
 */
object LmStudioService {
    private val json = Json { ignoreUnknownKeys = true }

    fun chat(
        systemPrompt: String,
        userText: String,
    ): String? {
        val url = KaraokeProperties.getString("lmStudioUrl")
        if (url.isBlank()) return null

        val model = KaraokeProperties.getString("lmStudioModel")
        val timeoutMs = KaraokeProperties.getLong("lmStudioTimeoutMs").takeIf { it > 0 } ?: 120_000L
        val apiKey = KaraokeProperties.getString("lmStudioApiKey")

        val client =
            OkHttpClient
                .Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()

        val requestDto =
            LmStudioChatRequestDto(
                model = model,
                messages =
                    listOf(
                        LmStudioMessageDto(role = "system", content = systemPrompt),
                        LmStudioMessageDto(role = "user", content = userText),
                    ),
            )
        val requestBody =
            json
                .encodeToString(LmStudioChatRequestDto.serializer(), requestDto)
                .toRequestBody("application/json".toMediaType())

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
                if (!response.isSuccessful) return null
                val bodyString = response.body?.string() ?: return null
                val parsed = json.decodeFromString(LmStudioChatResponseDto.serializer(), bodyString)
                parsed.choices
                    .firstOrNull()
                    ?.message
                    ?.content
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }
}
