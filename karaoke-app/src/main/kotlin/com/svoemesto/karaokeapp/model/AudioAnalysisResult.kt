package com.svoemesto.karaokeapp.model

@kotlinx.serialization.Serializable
data class AudioAnalysisResult(
    val bpm: Int? = null, // Используем Int, так как в скрипте bpm округляется до целого
    val key: String? = null,
    val error: String? = null // На случай, если анализ завершился с ошибкой
)
