package com.svoemesto.karaokeapp

import java.awt.Color
import java.awt.Font

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
    forEach { if (it.lowercase() in "qwertyuiopasdfghjklzxcvbnmйцукенгшщзхъёфывапролджэячсмитьбю1234567890") result += it  }
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

fun String.cutByWords(delimiter: String = " ", maxLength: Long = 100, excludingWords: List<String> = listOf("for")): String {
    val listWords = this.split(delimiter)
    var result = ""
    var lastWord = ""
    listWords.forEach { word ->
        if (result.length + word.length + delimiter.length <= maxLength) {
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