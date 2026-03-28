package com.svoemesto.karaokeapp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.time.LocalDate

@Serializable
data class OllamaRequest(
    val model: String,
    val prompt: String,
    val system: String? = null, // Добавлен системный промт
    val stream: Boolean = false // Отключаем streaming для простого ответа
)

@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class OllamaStreamResponse(
    @SerialName("model")
    val model: String,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("response")
    val response: String,

    @SerialName("done")
    val done: Boolean?,

    @SerialName("done_reason")
    val doneReason: String? = null,

    @SerialName("context")
    val context: List<Int>? = null,

    @SerialName("total_duration")
    val totalDuration: Long? = null,

    @SerialName("load_duration")
    val loadDuration: Long? = null,

    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,

    @SerialName("prompt_eval_duration")
    val promptEvalDuration: Long? = null,

    @SerialName("eval_count")
    val evalCount: Int? = null,

    @SerialName("eval_duration")
    val evalDuration: Long? = null
)


class AIAssistant(
//    private val model: String = "llama3",
//    private val model: String = "qwen3:8b",
//    private val model: String = "qwen2.5-coder:7b",
    private val model: String = "qwen2.5-coder:1.5b",
    private val baseUrl: String = "http://localhost:11434"
) {
    private val client = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateStreaming(prompt: String, systemPrompt: String? = null): Flow<String> {
        return flow {
            val request = OllamaRequest(model, prompt, systemPrompt,  true)
            val body = json.encodeToString(request)

            val httpRequest = HttpRequest.newBuilder()
                .uri(URI("$baseUrl/api/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

            val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream())

            // Обрабатываем поток данных построчно
            response.body().bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        try {
                            val streamResponse = json.decodeFromString<OllamaStreamResponse>(line)
                            if (streamResponse.response.isNotEmpty()) {
                                emit(streamResponse.response)
                            }
                        } catch (e: Exception) {
                            // Игнорируем строки, которые не являются JSON
                            println("Skipping non-JSON line: $line")
                        }
                    }
                }
            }
        }
    }

    suspend fun generateSync(prompt: String, systemPrompt: String? = null): String {
        val request = OllamaRequest(model, prompt, systemPrompt,  false)
        val body = json.encodeToString(request)

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI("$baseUrl/api/generate"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream())

        // Собираем все строки и объединяем response
        val stringBuilder = StringBuilder()
        val reader = BufferedReader(InputStreamReader(httpResponse.body(), StandardCharsets.UTF_8))

        reader.useLines { lines ->
            lines.forEach { line ->
                if (line.isNotBlank()) {
                    try {
                        val streamResponse = json.decodeFromString<OllamaStreamResponse>(line)
                        if (streamResponse.response.isNotEmpty()) {
                            stringBuilder.append(streamResponse.response)
                        }
                        // Прерываем при завершении
                        if (streamResponse.done == true) {
                            return@forEach
                        }
                    } catch (e: Exception) {
                        println("Skipping non-JSON line: $line")
                    }
                }
            }
        }

        return stringBuilder.toString()
    }
}


suspend fun main2222() {
    val assistant = AIAssistant()

    // Для синхронного вызова
    val syncResult = assistant.generateSync(
        prompt = "${LocalDate.now()}. Выведи эту дату в формате YYYY-MM-DD",
        systemPrompt = "Ты - ассистент по календарю. Отвечай кратко и по существу."
    )
    println("Sync result: $syncResult")

    val syncResult2 = assistant.generateSync(
        prompt = "Перечисли основные ингредиенты традиционного узбекского плова.",
        systemPrompt = "Ты - ассистент по кулинарии. Отвечай кратко и по существу."
    )
    println("Sync result2: $syncResult2")

    // Для потокового вызова
//    assistant.generateStreaming(
//        prompt = "Расскажи о Kotlin",
//        systemPrompt = "Отвечай кратко и по существу"
//    ).collect { response ->
//        print(response)
//    }
}