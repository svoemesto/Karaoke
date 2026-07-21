package com.svoemesto.karaokeapp.textfilehistory

import com.svoemesto.karaokeapp.SONGS_HISTORY_FILE_PATH

/**
 * Класс Songs History.
 *
 * @see AGENTS.md
 */
class SongsHistory : TextFileHistory {
    override var history = loadList()

    override fun pathToFile() = SONGS_HISTORY_FILE_PATH

    fun toDTO(): List<SongHistoryDTO> {
        val result: MutableList<SongHistoryDTO> = mutableListOf()
        history.forEach { line ->
            val name =
                line
                    .map { (fldName, fldValue) ->
                        val rusName =
                            when (fldName) {
                                "id" -> "ID"
                                "song_name" -> "Название"
                                "song_author" -> "Автор"
                                "song_album" -> "Альбом"
                                "publish_date" -> "Дата"
                                "publish_time" -> "Время"
                                "id_status" -> "Статус"
                                "tags" -> "Тэги"
                                "filter_result_version" -> "Версия"
                                else -> fldName
                            }

                        "$rusName: $fldValue"
                    }.joinToString(", ")
            result.add(SongHistoryDTO(historyName = name, historyArgs = line))
        }
        return result
    }
}

/**
 * DTO для song history: сериализуемое представление для API/UI.
 *
 * @see AGENTS.md
 */
data class SongHistoryDTO(
    val historyName: String,
    val historyArgs: Map<String, String>,
) : java.io.Serializable
