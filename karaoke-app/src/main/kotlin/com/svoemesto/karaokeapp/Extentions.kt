package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.textfiledictionary.CensoredWordsDictionary
import java.awt.Color
import java.awt.Font
import java.io.File
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

fun String.wrapInApostraf(): String = "'$this'"

fun String.wrapInQuotes(): String {
    return this
//    return "\"\"" + this + "\"\""
}

/**
 * Санитайзирует символы, проблемные для файловых операций/команд, в строке.
 * ВАЖНО: вызывается и на "голых" именах файлов, и на уже собранных абсолютных
 * путях (см. [rightFileName] и его 24 вызывающих места, например
 * `StemJobProcessing.kt`/`KaraokeProcess.kt` — они передают сюда полный путь
 * вида `"$tempFolder/upload.ext"`). Поэтому здесь НЕЛЬЗЯ удалять `/`/`\` —
 * это разделители пути, а не проблемные символы имени файла. Санитайзинг
 * именно "голого" фрагмента имени файла песни (без пути) — см.
 * [sanitizeSongFileName].
 *
 * @see docs/features/async-process-queue.md
 */
fun String.rightFileNameSymbols(): String =
    this
        .replace("'", "''")
        .replace("?", "")
        .replace(":", "-")
        .replace("!", "")
        .replace("$", "s")
        .replace("*", "x")

/**
 * Санитайзирует "голый" фрагмент имени файла песни (без пути, до объединения
 * с `rootFolder`) — используется при импорте ([Song.createFromPath]) и при
 * ручном переименовании через SongEdit (`ApiController.songs2Update`).
 * В отличие от [rightFileNameSymbols]/[rightFileName] (которые применяются и
 * к целым путям и потому не трогают `/`), здесь дополнительно убираются
 * разделители пути `/`/`\` (защита от выхода за пределы папки песни, FR-001)
 * и двойные кавычки `"`. Скобки `(` `)` `[` `]` и не-ASCII буквы (кириллица
 * и т.п.) НЕ трогаются — они либо структурно значимы для парсинга шаблона
 * имени файла (FR-003), либо не являются проблемными символами (FR-012).
 *
 * @see docs/features/async-process-queue.md
 */
fun String.sanitizeSongFileName(): String =
    this
        .replace("/", "")
        .replace("\\", "")
        .replace("\"", "")
        .rightFileNameSymbols()

fun String.rightFileName(): String {
    return this.rightFileNameSymbols()
    // Если имя у файла очень длинное - его надо обрезать до размера 200 байт
    // Имя может быть в формате /путь/до/файла/2024 (14) [Автор] - Длинное название трека может с точками в конце... [кое-что в квадратных скобках].расширение
//    var result = this.rightFileNameSymbols()
//    val file = File(result)
//    val filePath = file.parent // путь - /путь/до/файла
//    var fileName: String   // Имя - 2024 (14) [Автор] - Длинное название трека может с точками в конце... [кое-что в квадратных скобках].расширение
//    var fileNameWithoutExtension = file.nameWithoutExtension // Имя без расширения - 2024 (14) [Автор] - Длинное название трека может с точками в конце... [кое-что в квадратных скобках]
//    val fileNameExtension = file.extension // расширение - расширение
//    if (fileNameWithoutExtension.endsWith("]")) {
//        while ("$fileNameWithoutExtension${if (fileNameExtension == "") "" else ".$fileNameExtension"}".toByteArray(Charsets.UTF_8).size > 200) {
//            val parts = fileNameWithoutExtension.split("[")
//
//            val partsReversed = parts.asReversed().toMutableList()
//            val index = 1.coerceAtMost((parts.size - 1))
//            partsReversed[index] = partsReversed[index].substring(0, partsReversed[index].length-1)
//            fileNameWithoutExtension = partsReversed.asReversed().joinToString("[")
//        }
//    } else {
//        while ("$fileNameWithoutExtension${if (fileNameExtension == "") "" else ".$fileNameExtension"}".toByteArray(Charsets.UTF_8).size > 200) {
//            fileNameWithoutExtension = fileNameWithoutExtension.substring(0, fileNameWithoutExtension.length-1)
//        }
//    }
//    fileName = "$fileNameWithoutExtension${if (fileNameExtension == "") "" else ".$fileNameExtension"}"
//    result = "${if (filePath == null) "" else "$filePath/" }$fileName"
//    return result
}

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

fun String.nullIfEmpty(): String? = if (this.trim() == "" || this == "null") null else this

fun String.xmldata(): String = "<?xml version=\"1.0\"?>$this".replace("<", "&lt;")

fun Font.weight(): Int = if (isBold) 75 else 50

fun Font.mlt(): String = "$name;$style;$size"

fun Font.setting(): String = "name=$name;style=$style;size=$size"

fun Color.hexRGB(): String = "0x${Integer.toHexString(rgb).substring(2)}${Integer.toHexString(rgb).substring(0,2)}"

fun Color.mlt(): String = "$red,$green,$blue,$alpha"

fun Color.setting(): String = "r=$red;g=$green;b=$blue;a=$alpha"

fun List<Color>.setting(): String = joinToString("|") { it.setting() }

fun String.hashtag(): String {
    var result = "#"
    forEach { if (it.lowercase() in "qwertyuiopasdfghjklzxcvbnmйцукенгшщзхъёфывапролджэячсмитьбюѣ1234567890") result += it }
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

fun String.haveVowel(): Boolean = this.containThisSymbols(LETTERS_VOWEL)

fun String.containOnlyThisSymbols(symbolString: String): Boolean =
    symbolString.trim() != "" && this.deleteThisSymbols(symbolString).trim() == ""

fun String.deleteThisSymbols(symbolString: String): String {
    var txt = this
    symbolString.forEach { symbolInSymbolString ->
        txt = txt.replace(symbolInSymbolString.toString(), "")
    }
    return txt
}

fun String.uppercaseFirstLetter(): String {
    val txt = this
    var result = ""
    var flag = false
    txt.forEachIndexed { index, symbolInSymbolString ->
        if (!flag && symbolInSymbolString !in "-_,.!@#№$;%^:&?*()[]{}|/\\\"'`~ «»") {
            result += symbolInSymbolString.uppercase()
            flag = true
        } else {
            result += symbolInSymbolString // .lowercase()
        }
    }
    return result
}

fun String.cutByWords(
    delimiter: String = " ",
    maxLength: Int = 0,
    excludingWords: List<String> = listOf("for"),
): String {
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

fun getCensoredPair(
    censored: String,
    charOpen: String = "[",
    charClose: String = "]",
    replacement: String = "█",
): Pair<String, String> {
    val s1 = censored.replace(charOpen, "").replace(charClose, "")
    val s2 = censored.replace("(\\[.])".toRegex(), replacement)
    return Pair(s1, s2)
}

/**
 * Заменяет слова из словаря «Censored» на маскированную форму (см. [getCensoredPair]).
 *
 * @param database Соединение, из которого читать словарь «Censored». Параметр обязателен —
 *   в karaoke-web и karaoke-app определены разные `WORKING_DATABASE` (с разными credentials),
 *   и обращение через karaoke-app-глобал из karaoke-web инициализирует `Connection` →
 *   читает `APP_WORK_ON_SERVER` (инициализируется только в `KaraokeAppService` модуля
 *   karaoke-app, в karaoke-web его нет) → падает с `IllegalStateException`. Дефолт
 *   был убран в specs/140 после регрессии PR #139, чтобы компилятор ловил такие места.
 *   @see specs/140-fix-zakroma-censored-database
 */
fun String.censored(database: KaraokeConnection): String {
    val censoredMap = CensoredWordsDictionary(database = database).dict.associate { getCensoredPair(it) }

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
    if (this.split("\n").size >= minNewLine) return this
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
            val replaceTo = "«" + replaceFrom.substring(1, replaceFrom.length - 1) + "»"
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
        } catch (_: Exception) {
            this
        }
    } else {
        this
    }
}

@Suppress("unused")
fun String.stripToNumeric(): String = this.replace("\\D+".toRegex(), "")

@Suppress("unused")
fun Song.karaokePlatformPublications(): List<KaraokePlatformPublication> = KaraokePlatformPublication.getList(song = this)

fun List<HealthReport>.errorsOnly(): List<HealthReport> = this.filter { it.healthReportStatus != HealthReportStatus.OK }

fun LocalDateTime.toTimestamp(): Timestamp = Timestamp.from(this.atZone(ZoneId.systemDefault()).toInstant())

fun LocalDateTime.toEpochMillis(): Long = this.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
