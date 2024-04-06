package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.textfiledictionary.CensoredWordsDictionary
import java.awt.Color
import java.awt.Font
import java.io.File
import java.util.*

fun String.getWords(): List<String> {
    val result: MutableList<String> = mutableListOf()
    val regex = "([a-zA-Zа-яА-ЯёЁ\\-]+)".toRegex()
    val matchResults = regex.findAll(this)
    matchResults.forEach { matchResult ->
        matchResult.groupValues.forEach { word ->
            result.add(word.lowercase())
        }
    }
    return result.toSet().toList()
}

fun String.nullIfEmpty(): String? {
    return if (this.trim() == "" || this == "null") null else this
}
fun String.xmldata(): String {
    return "<?xml version=\"1.0\"?>${this}".replace("<","&lt;")
}
fun Font.weight(): Int {
    return if (isBold) 75 else 50
}
fun Font.mlt(): String {
    return "${name};${style};${size}"
}
fun Font.setting(): String {
    return "name=${name};style=${style};size=${size}"
}
fun Color.hexRGB(): String {
    return "0x${Integer.toHexString(rgb).substring(2)}${Integer.toHexString(rgb).substring(0,2)}"
}

fun Color.mlt(): String {
    return "${red},${green},${blue},${alpha}"
}

fun Color.setting(): String {
    return "r=${red};g=${green};b=${blue};a=${alpha}"
}
fun List<Color>.setting(): String {
    return joinToString("|") { it.setting() }
}

fun String.hashtag(): String {
    var result = "#"
    forEach { if (it.lowercase() in "qwertyuiopasdfghjklzxcvbnmйцукенгшщзхъёфывапролджэячсмитьбюѣ1234567890") result += it  }
    return result
}

fun String.getFirstVowelIndex(): Int {
    for (i in this.indices) {
        if (this[i] in LETTERS_VOWEL) {
            return i
        }
    }
    return 0
}

fun String.containThisSymbols(symbolString: String): Boolean {
    this.forEach { symbolInString ->
        if (symbolInString in symbolString) return true
    }
    return false
}

fun String.containOnlyThisSymbols(symbolString: String): Boolean {
    return this.deleteThisSymbols(symbolString).trim() == ""
}

fun String.deleteThisSymbols(symbolString: String): String {
    var txt = this
    symbolString.forEach { symbolInSymbolString ->
        txt = txt.replace(symbolInSymbolString.toString(),"")
    }
    return txt
}
fun String.uppercaseFirstLetter(): String {
    var txt = this
    var result = ""
    var flag = false
    txt.forEachIndexed { index, symbolInSymbolString ->
        if (!flag && symbolInSymbolString !in "-_,.!@#№$;%^:&?*()[]{}|/\\\"'`~ «»") {
            result += symbolInSymbolString.uppercase()
            flag = true
        } else {
            result += symbolInSymbolString //.lowercase()
        }
    }
    return result
}

fun String.cutByWords(delimiter: String = " ", maxLength: Int = 0, excludingWords: List<String> = listOf("for")): String {
    val listWords = this.split(delimiter)
    var result = ""
    var lastWord = ""
    listWords.forEach { word ->
        if (result.length + word.length + delimiter.length <= maxLength || maxLength == 0) {
            result += delimiter + word
            lastWord = word
        } else {
            if (lastWord in excludingWords) {
                result = result.substring(0, result.length - lastWord.length - delimiter.length)
            }
            return result.trim()
        }
    }
    return result.trim()
}

fun getCensoredPair(censored: String, charOpen: String = "[", charClose: String = "]", replacement: String = "█"): Pair<String, String> {
    val s1 = censored.replace(charOpen,"").replace(charClose,"")
    val s2 = censored.replace("(\\[.\\])".toRegex(), replacement)
    return Pair(s1, s2)
}
fun String.censored(): String {

    val censoredMap = CensoredWordsDictionary().dict.associate { getCensoredPair(it) }

    var result = this
    censoredMap.forEach { (uncensored, censored) ->
        val patt1 = "\\b$uncensored\\b".toRegex()
        result = result.replace(patt1, censored)
        val patt2 = "\\b${uncensored.uppercaseFirstLetter()}\\b".toRegex()
        result = result.replace(patt2, censored.uppercaseFirstLetter())
    }
    return result
}

fun String.addNewLinesByUpperCase(minNewLine: Int = 2): String {
    if (this.split("\n").size >=  minNewLine) return this
    var result = ""
    for (symbol in this) {
        result += if (symbol.isUpperCase()) "\n" else ""
        result += symbol.toString()
    }
    result = result.replace(" \n", "\n")
    return result
}

fun String.replaceQuotes(): String {
    var result = this
    val regex = "\"[\\s\\S]+?\"".toRegex()
    val matchResults = regex.findAll(result)
    matchResults.forEach { matchResult ->
        matchResult.groupValues.forEach { replaceFrom ->
            val replaceTo = "«" + replaceFrom.substring(1,replaceFrom.length-1) + "»"
            result = result.replace(replaceFrom, replaceTo)
        }
    }

    return result
}

fun String.base64ifFileExists(): String {
    val file = File(this)
    return if (file.exists()) {
        try {
            Base64.getEncoder().encodeToString(file.inputStream().readAllBytes())
        } catch (e: Exception) {
            this
        }
    } else {
        this
    }

}