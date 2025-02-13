package com.svoemesto.karaokeapp.textfilehistory

import com.svoemesto.karaokeapp.COUNT_HISTORY_LINES
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.*
import java.util.*

@Serializable
data class HistoryMap(val args: Map<String, String>)

interface TextFileHistory {

    fun pathToFile(): String
    var history: MutableList<Map<String, String>>

    fun save() {
        File(pathToFile()).writeText(history.joinToString("\n") {
            Base64.getEncoder().encodeToString(Json.encodeToString(HistoryMap.serializer(), HistoryMap(it)).toByteArray())
        })
    }

    fun add(element: Map<String, String>) {
        if (history.contains(element)) history.remove(element)
        history.add(0, element)
        while (history.size > COUNT_HISTORY_LINES) {
            history.removeAt(history.size - 1)
        }
        save()
    }

    fun loadList(): MutableList<Map<String, String>> {
        val list = try {
            File(pathToFile())
                .readText()
                .split("\n")
                .filter { it != "" }
                .map {
                    Json.decodeFromStream( HistoryMap.serializer(), ByteArrayInputStream(Base64.getDecoder().decode(it)) ).args
                }
        } catch (e: Exception) {
            return mutableListOf()
        }
        return list.toMutableList()
    }

}