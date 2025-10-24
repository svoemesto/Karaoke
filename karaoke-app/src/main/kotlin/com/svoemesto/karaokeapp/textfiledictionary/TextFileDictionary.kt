package com.svoemesto.karaokeapp.textfiledictionary

import com.svoemesto.karaokeapp.TEXT_FILE_DICTS
import com.svoemesto.karaokeapp.runCommand
import java.io.File

interface TextFileDictionary {

    companion object {

        fun doAction(dictName: String, dictAction: String, dictValues: List<String>): Boolean {
            val tfd = TEXT_FILE_DICTS[dictName] ?: return false
            val tfdInstance = tfd.getDeclaredConstructor().newInstance()
            val func = tfdInstance.javaClass.declaredMethods.firstOrNull { it.name == dictAction } ?: return false
            func.invoke(tfdInstance, dictValues)
            return true
        }

        @Suppress("UNCHECKED_CAST")
        fun loadList(dictName: String): List<String> {
            val tfd = TEXT_FILE_DICTS[dictName] ?: return emptyList()
            val tfdInstance = tfd.getDeclaredConstructor().newInstance()
            val func = tfdInstance.javaClass.declaredMethods.firstOrNull { it.name == "loadList" } ?: return emptyList()
            val result = func.invoke(tfdInstance)
            return if (result is List<*>) result as List<String> else emptyList()
        }

    }

    fun pathToFile(): String

    var dict: MutableList<String>

    fun clear() {
        dict.clear()
        save()
    }

    fun save() {
        File(pathToFile()).writeText(dict.joinToString("\n"))
        runCommand(listOf("chmod", "666", pathToFile()))
    }

    fun add(elements: List<String>) {
        for (element in elements) {
            addOne(element)
        }
    }

    fun addOne(element: String) {
        if (dict.contains(element)) return
        dict.add(element)
        save()
    }

    fun remove(elements: List<String>) {
        for (element in elements) {
            removeOne(element)
        }
    }

    fun removeOne(element: String) {
        if (!dict.contains(element)) return
        dict.remove(element)
        save()
    }

    @Suppress("unused")
    fun editOne(oldElement: String, newElement: String) {
        if (!dict.contains(oldElement) || oldElement == "" || newElement == "") return
        dict.remove(oldElement)
        dict.add(newElement)
        save()
    }

    @Suppress("unused")
    fun have(element: String) = dict.contains(element)

    fun loadList(): MutableList<String> {
        val list = try {
            File(pathToFile())
                .readText()
                .split("\n")
                .filter { it != "" }
                .sortedBy { it }
        } catch (_: Exception) {
            return mutableListOf()
        }
        return list.toMutableList()
    }

}