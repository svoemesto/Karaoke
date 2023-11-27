package com.svoemesto.karaokeapp.textfiledictionary

import com.svoemesto.karaokeapp.TEXT_FILE_DICTS
import java.io.File

interface TextFileDictionary {

    companion object {

        fun doAction(dictName: String, dictAction: String, dictValues: List<String>): Boolean {
            val tfd = TEXT_FILE_DICTS[dictName] ?: return false
            val tfdInstance = tfd.getDeclaredConstructor().newInstance()
            val func = tfdInstance.javaClass.declaredMethods.firstOrNull() { it.name == dictAction } ?: return false
            func.invoke(tfdInstance, dictValues)
            return true
        }

    }

    fun pathToFile(): String

    var dict: MutableList<String>

    fun save() {
        File(pathToFile()).writeText(dict.joinToString("\n"))
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

    fun edit(elements: List<String>) {
        for (i in 0 .. elements.size / 2 step 2) {
            editOne(elements[i], elements[i+1])
        }
    }

    fun editOne(oldElement: String, newElement: String) {
        if (!dict.contains(oldElement) || oldElement == "" || newElement == "") return
        dict.remove(oldElement)
        dict.add(newElement)
        save()
    }

    fun have(element: String) = dict.contains(element)

    fun loadList(): MutableList<String> {
        val list = try {
            File(pathToFile())
                .readText()
                .split("\n")
                .filter { it != "" }
                .sortedBy { it }
        } catch (e: Exception) {
            return mutableListOf()
        }
        return list.toMutableList()
    }

}