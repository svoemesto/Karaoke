package com.svoemesto.karaokeapp.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Результат нормализации голосов песни: набор голосов без пустых (кроме первого)
 * и отображение старых индексов на новые.
 *
 * Голос — пара «текст + маркеры», адресуемая индексом с нуля (см. `tbl_songs.source_text` и
 * `tbl_songs.source_markers`). Правило нормализации: голос с индексом больше нуля удаляется,
 * если его текст пуст (в том числе только пробелы) **и** маркеров нет. Первый голос
 * сохраняется всегда.
 *
 * @property texts тексты оставшихся голосов (длина равна [markers] при [changed])
 * @property markers маркеры оставшихся голосов
 * @property removedIndices индексы исходного списка, которые удалены (по возрастанию)
 *
 * @see knowledge/product/features/drop-empty-song-voices.md
 * @see specs/547-drop-empty-song-voices/data-model.md
 */
data class VoiceNormalization(
    val texts: List<String>,
    val markers: List<List<SourceMarker>>,
    val removedIndices: List<Int>,
) {
    /** true, если хотя бы один голос удалён. */
    val changed: Boolean get() = removedIndices.isNotEmpty()

    /** Число голосов после нормализации (по списку маркеров — он первичен). */
    val count: Int get() = markers.size

    /**
     * Отображение индекса голоса: старый индекс → новый.
     *
     * @return новый индекс или `null`, если голос удалён либо индекса не было
     */
    fun mapVoice(index: Int): Int? {
        if (index < 0) return null
        if (removedIndices.contains(index)) return null
        return index - removedIndices.count { it < index }
    }
}

/**
 * Разбор `source_text` песни: JSON-массив строк по голосам.
 *
 * `null`/пустая строка → пустой список (данных нет), битый JSON → `null` (нормализация
 * пропускается — fail-safe, FR-012).
 *
 * @see knowledge/product/features/drop-empty-song-voices.md
 */
fun parseVoiceTexts(raw: String?): List<String>? {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        Json.decodeFromString(ListSerializer(String.serializer()), raw)
    } catch (_: Exception) {
        null
    }
}

/**
 * Разбор `source_markers` песни: JSON-массив массивов [SourceMarker] по голосам.
 *
 * Поддерживается легаси-формат — плоский массив маркеров (один голос). Битый JSON → `null`
 * (нормализация пропускается — fail-safe, FR-012).
 *
 * Отличие от [Song.sourceMarkersList]: не имеет побочных эффектов (не пишет поле и не вызывает
 * сохранение), поэтому безопасен внутри `saveToDb()`.
 *
 * @see knowledge/product/features/drop-empty-song-voices.md
 */
fun parseVoiceMarkers(raw: String?): List<List<SourceMarker>>? {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        Json.decodeFromString(ListSerializer(ListSerializer(SourceMarker.serializer())), raw)
    } catch (_: Exception) {
        try {
            listOf(Json.decodeFromString(ListSerializer(SourceMarker.serializer()), raw))
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Признак «голос пуст»: текст пуст (или только пробелы) и маркеров нет.
 *
 * @see knowledge/product/features/drop-empty-song-voices.md
 */
fun isVoiceEmpty(
    text: String?,
    markers: List<SourceMarker>?,
): Boolean = text.orEmpty().isBlank() && markers.orEmpty().isEmpty()

/**
 * Гард нормализации: работает только если у песни в памяти есть **оба** поля голосов.
 *
 * Песня, загруженная с `withoutMarkersAndText = true` (списки песен, фильтры), не содержит
 * `source_text`/`source_markers`; нормализация такой песни записала бы пустой текст и стёрла бы
 * данные из БД. Поэтому при отсутствии любого из полей нормализация запрещена.
 *
 * @see knowledge/product/features/drop-empty-song-voices.md
 */
fun canNormalize(
    hasMarkersField: Boolean,
    hasTextField: Boolean,
): Boolean = hasMarkersField && hasTextField

/**
 * Нормализация голосов: удаляет пустые голоса с индексом больше нуля, сохраняя порядок.
 *
 * Чистая функция: не читает и не пишет БД, не меняет аргументы, не бросает исключений.
 * Если удалять нечего — возвращает входные списки как есть (без выравнивания длин), чтобы
 * повторное сохранение было идемпотентным (FR-009).
 *
 * @return результат нормализации; при `changed = true` длины [VoiceNormalization.texts] и
 *         [VoiceNormalization.markers] равны
 *
 * @see knowledge/product/features/drop-empty-song-voices.md
 * @see specs/547-drop-empty-song-voices/contracts/voice-normalization.md
 */
fun normalizeVoices(
    texts: List<String>,
    markers: List<List<SourceMarker>>,
): VoiceNormalization {
    val size = maxOf(texts.size, markers.size)
    if (size <= 1) return VoiceNormalization(texts, markers, emptyList())

    val removed = mutableListOf<Int>()
    val kept = mutableListOf<Int>()
    for (index in 0 until size) {
        val empty = isVoiceEmpty(texts.getOrNull(index), markers.getOrNull(index))
        if (index > 0 && empty) removed.add(index) else kept.add(index)
    }
    if (removed.isEmpty()) return VoiceNormalization(texts, markers, emptyList())

    return VoiceNormalization(
        texts = kept.map { texts.getOrNull(it).orEmpty() },
        markers = kept.map { markers.getOrNull(it).orEmpty() },
        removedIndices = removed,
    )
}

/**
 * Индексы непустых голосов — для рендера, который обходит голоса по позиции.
 *
 * @see knowledge/product/features/drop-empty-song-voices.md
 */
fun nonEmptyVoiceIndices(markers: List<List<SourceMarker>>): List<Int> =
    markers.indices.filter { markers[it].isNotEmpty() }
