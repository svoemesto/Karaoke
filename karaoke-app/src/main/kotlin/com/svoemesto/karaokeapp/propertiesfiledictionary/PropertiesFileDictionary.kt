package com.svoemesto.karaokeapp.propertiesfiledictionary

import com.svoemesto.karaokeapp.runCommand
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.ByteArrayInputStream
import java.io.File
import java.util.*

interface PropertiesFileDictionary {

    fun separator() = "{WEBVUE_PROPERTIES_SEPARATOR}"
    fun pathToFile(): String
    var props: MutableMap<String, String>

    fun get(key: String, default: String): String = props[key] ?: default
    fun get(key: String): String = get(key = key, default = "")

    fun set(key: String, value: String) {
        props[key] = value
        save()
    }
    fun loadMap(): MutableMap<String, String> {
        val result: MutableMap<String, String> = mutableMapOf()
        val list = try {
            File(pathToFile())
                    .readText()
                    .split("\n")
                    .filter { it != "" }
        } catch (e: Exception) {
//            println("Ошибка считывания файла, возвращаем пустую мапу")
            return result
        }

        (list.indices).forEach { i ->
            val (key, value) = list[i].split(separator())
            result[key] = value
        }
//        println("result = $result")
        return result
    }

    private fun save() {
//        val list = props.map { (key, value) -> "$key\n${if (value == "") value else Base64.getEncoder().encodeToString(value.toByteArray())}" }
        val list = props.map { (key, value) -> "$key${separator()}$value" }
//        println("list to save = $list")
        File(pathToFile()).writeText(list.joinToString("\n"))
        runCommand(listOf("chmod", "666", pathToFile()))
    }

}