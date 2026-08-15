package com.svoemesto.karaokeweb

/**
 * Локальная копия логики `replaceSymbolsInSong` + вспомогательных String-extensions.
 *
 * Зачем существует в `karaoke-web`, а не переиспользуется из `karaoke-app`:
 * оригинальная функция в `karaoke-app` (`Utils.replaceSymbolsInSong`, `Extentions.kt`) — pure
 * с виду, но при первом обращении JVM инициализирует `com.svoemesto.karaokeapp.ConstantsKt`,
 * а тот при class init собирает `mapOf(ProducerType.X to MkoY::class.java, ...)` —
 * загружаются ВСЕ MLT-классы (`com.svoemesto.karaokeapp.mlt.mko.*`), часть которых при
 * инициализации обращается к БД/дисковым путям, настроенным только в `karaoke-app` (на проде
 * `karaoke-app` не развёрнут, переменные `APP_WORK_ON_SERVER`/`WORKING_DATABASE` для MLT
 * не инициализированы). Результат — `NoClassDefFoundError: Could not initialize class
 * com.svoemesto.karaokeapp.ConstantsKt` на первом POST `/api/replacesymbolsinsong` после
 * деплоя PR #205.
 *
 * Чтобы разорвать эту зависимость, логика скопирована сюда. Поведение и набор правил
 * идентичны `karaoke-app` (это и есть смысл «та же кнопка, что в SubsEdit» из спеки 155).
 * При изменении правил в `karaoke-app` — синхронизировать вручную (см. tasks.md
 * спецификации, `update karaoke-web TypographUtils.kt`).
 *
 * @see archive/docs/features/editor-tasks.md
 */

const val RUSSIAN_LETTERS_WEB =
    "ЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮЁйцукенгшщзхъфывапролджэячсмитьбюё"

const val CHORDS_LETTERS_WEB = "ABCDEFGH#bMminaj-+/augd7659o13s4"

fun String.containThisSymbols(symbolString: String): Boolean {
    this.forEach { symbolInString ->
        if (symbolInString in symbolString) return true
    }
    return false
}

fun String.deleteThisSymbols(symbolString: String): String {
    var txt = this
    symbolString.forEach { symbolInSymbolString ->
        txt = txt.replace(symbolInSymbolString.toString(), "")
    }
    return txt
}

fun String.containOnlyThisSymbols(symbolString: String): Boolean =
    symbolString.trim() != "" && this.deleteThisSymbols(symbolString).trim() == ""

fun String.uppercaseFirstLetter(): String {
    val txt = this
    var result = ""
    var flag = false
    txt.forEachIndexed { index, symbolInSymbolString ->
        if (!flag && symbolInSymbolString !in "-_,.!@#№$;%^:&?*()[]{}|/\\\"'`~ «»") {
            result += symbolInSymbolString.uppercase()
            flag = true
        } else {
            result += symbolInSymbolString
        }
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

/**
 * Загрузить список значений словаря из `tbl_dictionaries` прямым SQL — без обращения к
 * karaoke-app-модели `Dictionary` (та тянет за собой `KaraokeDbTable`/`KaraokeStorageService`/
 * `StorageApiClient`, инициализация которых в `karaoke-web` падает с тем же `NoClassDefFoundError`).
 *
 * @param dictName значение колонки `dict_name` (например, `"Слова с Ё"`).
 * @return список значений; пустой при ошибке или отсутствии словаря.
 */
private fun loadDictionaryValues(dictName: String): List<String> {
    val result = mutableListOf<String>()
    val connection =
        try {
            WORKING_DATABASE.getConnection() ?: return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
    try {
        connection
            .prepareStatement("SELECT dict_value FROM tbl_dictionaries WHERE dict_name = ? ORDER BY id")
            .use { ps ->
                ps.setString(1, dictName)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        rs.getString("dict_value")?.takeIf { it.isNotEmpty() }?.let { result.add(it) }
                    }
                }
            }
    } catch (e: Exception) {
        println("TypographUtils.loadDictionaryValues('$dictName') error: ${e.message}")
        return emptyList()
    } finally {
        try {
            connection.close()
        } catch (_: Exception) {
        }
    }
    return result
}

/**
 * Применить набор типографских правил к произвольной строке — локальная копия
 * `com.svoemesto.karaokeapp.replaceSymbolsInSong(sourceText)`.
 *
 * Правила (идентично karaoke-app, см. комментарий к файлу):
 *  1. `addNewLinesByUpperCase` — перенос строки перед каждой заглавной буквой.
 *  2. Ё-словарь — замена `е` → `ё` в словах из `tbl_dictionaries WHERE dict_name='Слова с Ё'`.
 *  3. `replaceQuotes` — `"..."` → `«...»`.
 *  4. `_` → пробел, `,` → `, `, `--`/`—`/`–`/`−` → `-`, ` : ` → `: `.
 *  5. Если в исходном тексте есть русские буквы: удалить строки, состоящие только из
 *     аккордовых символов, и заменить похожие латинские буквы на кириллические
 *     (p→р, y→у, e→е, o→о, a→а, x→х, c→с, и заглавные аналоги).
 *
 * @param sourceText исходный текст.
 * @return типографически нормализованный текст.
 */
fun replaceSymbolsInSong(sourceText: String): String {
    var result = sourceText.addNewLinesByUpperCase()

    val yo = loadDictionaryValues("Слова с Ё")
    val sourceTextContainsRussianLetters = sourceText.containThisSymbols(RUSSIAN_LETTERS_WEB)
    yo.forEach { wordWithYO ->
        val replacedWord = wordWithYO.replace("ё", "е")
        val patt1 = "\\b$replacedWord\\b".toRegex()
        result = result.replace(patt1, wordWithYO)
        val capWordWithYO = wordWithYO.uppercaseFirstLetter()
        val capReplacedWord = capWordWithYO.replace("ё", "е")
        val patt2 = "\\b$capReplacedWord\\b".toRegex()
        result = result.replace(patt2, capWordWithYO)
    }

    result = result.replaceQuotes()

    result = result.replace("_", " ")
    result = result.replace(",", ", ")
    result = result.replace(",  ", ", ")
    result = result.replace("--", "-")
    result = result.replace("—", "-")
    result = result.replace("–", "-")
    result = result.replace("−", "-")
    result = result.replace(" : ", ": ")
    result = result.replace(" :\n", ":\n")

    if (sourceTextContainsRussianLetters) {
        val lines = result.split("\n")
        val linesWithoutChords: MutableList<String> = mutableListOf()
        lines.forEach { line ->
            val lineIsEmpty = line.trim() == ""
            val lineHaveOnlyChordsLetters = line.containOnlyThisSymbols(CHORDS_LETTERS_WEB) && !lineIsEmpty
            if (!lineHaveOnlyChordsLetters) {
                linesWithoutChords.add(line.trimEnd())
            }
        }
        result = linesWithoutChords.joinToString("\n")

        result = result.replace("p", "р")
        result = result.replace("y", "у")
        result = result.replace("e", "е")
        result = result.replace("o", "о")
        result = result.replace("a", "а")
        result = result.replace("x", "х")
        result = result.replace("c", "с")
        result = result.replace("A", "А")
        result = result.replace("T", "Т")
        result = result.replace("O", "О")
        result = result.replace("P", "Р")
        result = result.replace("H", "Н")
        result = result.replace("K", "К")
        result = result.replace("X", "Х")
        result = result.replace("C", "С")
        result = result.replace("B", "В")
        result = result.replace("M", "М")
    }

    return result
}
