package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.textfiledictionary.CensoredWordsDictionary
import java.awt.Color
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
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
 * @see archive/docs/features/async-process-queue.md
 */
fun String.rightFileNameSymbols(): String = SanitizePath.run { sanitizePathSegment() }

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
 * @see archive/docs/features/async-process-queue.md
 */
fun String.sanitizeSongFileName(): String = SanitizePath.run { sanitizePathSegment() }

fun String.rightFileName(): String = SanitizePath.run { sanitizePath() }

/**
 * Права на файл, который создаёт приложение: владелец читает и пишет, остальные —
 * только читают. Справо: тикет #213.
 *
 * Раньше здесь был `runCommand(listOf("chmod", "666", pathToFile))`, и у него
 * три проблемы:
 *
 * 1. **Файл доступен на запись ЛЮБОМУ пользователю системы** (666), хотя пишет
 *    эти файлы только приложение — контейнер работает под root, и он же
 *    единственный писатель. Право на запись «для всех» не нужно никому.
 * 2. **Внешний процесс ради одной операции.** Список аргументов передаётся
 *    без shell, поэтому инъекции нет, но подпроцесс — это лишний запуск
 *    на каждый сохранённый субтитр.
 * 3. **Отказ ломал сохранение.** На одном из сайтов `chmod` стоял ВНЕ
 *    try/catch, уже после того, как маркеры были приняты в память через
 *    `setSourceMarkers()`: запрос возвращал ошибку при фактически применённых
 *    правках.
 *
 * Права выставляются в процессе JVM, атомарно, без подпроцесса. Ошибка
 * логируется и НЕ прерывает сохранение: файл уже создан, отсутствие прав на
 * чтение для сторонних инструментов — повод разобраться, но не причина
 * объявлять правку несохранённой.
 */
fun setSongFilePermissions(pathToFile: String) {
    applyPosixPermissions(pathToFile, PUBLISHED_FILE_PERMISSIONS, "файл")
}

/** Права опубликованного файла: чтение всем, запись — только владельцу (`644`). */
private val PUBLISHED_FILE_PERMISSIONS: Set<PosixFilePermission> =
    setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.OTHERS_READ,
    )

/** Права каталога публикации: чтение и обход всем, запись — только владельцу (`755`). */
private val PUBLISHED_DIRECTORY_PERMISSIONS: Set<PosixFilePermission> =
    setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE,
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.GROUP_EXECUTE,
        PosixFilePermission.OTHERS_READ,
        PosixFilePermission.OTHERS_EXECUTE,
    )

/**
 * Права файла с секретами: доступ только владельцу (`600`).
 *
 * BR-247 (#407): `Karaoke.properties` (и его `.bak`) содержит токены
 * VK/Telegram/Whisper и internal-secret в открытом виде (Base64 — кодирование,
 * а не шифрование). `666` делал файл читаемым и перезаписываемым любым
 * локальным пользователем и любым контейнером с тем же монтированием.
 */
private val SECRET_FILE_PERMISSIONS: Set<PosixFilePermission> =
    setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
    )

/**
 * Выставляет POSIX-права без внешнего процесса (`chmod`).
 *
 * Ошибка логируется и НЕ прерывает вызывающий код: файл уже создан, а сбой
 * прав — повод разобраться, но не причина объявлять операцию несохранённой.
 */
private fun applyPosixPermissions(
    path: String,
    permissions: Set<PosixFilePermission>,
    kind: String,
) {
    try {
        Files.setPosixFilePermissions(Path.of(path), permissions)
    } catch (e: Exception) {
        println("Не удалось выставить права на $kind '$path': ${e.message}")
    }
}

/**
 * BR-115 (#407): опубликованный файл (картинка/превью) — `644`.
 * Раньше здесь был `chmod 666`: мировое право ЗАПИСИ позволяло подменить
 * опубликованный артефакт из любого контейнера с тем же томом.
 */
fun setPublishedFilePermissions(pathToFile: String) {
    applyPosixPermissions(pathToFile, PUBLISHED_FILE_PERMISSIONS, "опубликованный файл")
}

/**
 * BR-115 (#407): каталог публикации — `755`. `chmod 777` на каталог позволял
 * любому пользователю/контейнеру добавлять и подменять файлы в нём.
 */
fun setPublishedDirectoryPermissions(pathToFolder: String) {
    applyPosixPermissions(pathToFolder, PUBLISHED_DIRECTORY_PERMISSIONS, "каталог публикации")
}

/** BR-247 (#407): файл с секретами (в т.ч. резервная копия) — `600`. */
fun setSecretFilePermissions(pathToFile: String) {
    applyPosixPermissions(pathToFile, SECRET_FILE_PERMISSIONS, "файл с секретами")
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

/**
 * Обёртка XML-данных для свойства MLT (`xmldata`).
 *
 * BR-183 (аудит #336, пакет `PKG-S3-24`): раньше здесь вручную экранировался
 * только `<` (`.replace("<", "&lt;")`), а `&` оставался — вложенный XML с `&`
 * (например, `&amp;` из имени песни «Sex & Violence») давал невалидный XML.
 * Теперь экранирование делается ОДНО место — при сериализации узла
 * ([com.svoemesto.karaokeapp.model.MltNode.toString]), поэтому здесь остаётся
 * только префикс XML-документа.
 */
fun String.xmldata(): String = "<?xml version=\"1.0\"?>$this"

/**
 * Экранирование для вставки текста в XML — атрибут или содержимое элемента.
 *
 * BR-183 (аудит #336, пакет `PKG-S3-24`): XML собирается в одном месте
 * ([com.svoemesto.karaokeapp.model.MltNode.toString]), поэтому и экранирование
 * должно быть там же. Набор символов тот же, что и в [escapeHtml]
 * (`&`, `<`, `>`, `"`, `'`), но назначение другое: XML, а не HTML.
 *
 * Порядок замен важен: сначала `&`, иначе амперсанд из вставленных сущностей
 * (`&lt;`) экранировался бы повторно и получилось бы `&amp;lt;`.
 */
fun String.escapeXml(): String =
    this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

/**
 * Экранирование для вставки пользовательского текста в HTML.
 *
 * Применять ко ВСЕМ данным, которые приходят от пользователя и уходят в строку
 * HTML, отдаваемую под `v-html`. Спецсимволов текста песни (слоги, комментарии,
 * ноты, аккорды) билдеры раньше склеивали сырым — см. #215.
 *
 * Порядок замен важен: сначала `&`, иначе амперсанд из вставленных сущностей
 * (`&lt;`) экранировался бы повторно и получилось бы `&amp;lt;`.
 */
fun String.escapeHtml(): String =
    this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

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
 * BR-289/BR-294 (аудит #336, пакет PKG-S3-26): правила цензурирования, собранные ОДИН раз.
 *
 * Раньше [String.censored] на каждый вызов:
 *  - читал весь словарь «Censored» из БД (SELECT по `tbl_dictionaries`) — в пакетном
 *    `rescanAllCensoredNames` это N песен → N SELECT'ов (BR-294);
 *  - компилировал по два regex на каждое слово — N×M компиляций;
 *  - подставлял значение словаря в шаблон regex КАК ЕСТЬ (без `Regex.escape`): значение
 *    с `*`, `[]`, `()` молча превращалось в шаблон и маскировало не то, а значение с
 *    незакрытой скобкой роняло `toRegex()` (`PatternSyntaxException`) — то есть падало
 *    сохранение песни (BR-289).
 *
 * Теперь значение словаря **экранируется** (`Regex.escape`), а шаблоны компилируются
 * один раз на весь пакет.
 */
class CensoredDictionaryRules private constructor(
    private val rules: List<Rule>,
) {
    /** Одно правило: исходное слово, маска и два готовых шаблона. */
    class Rule(
        val uncensored: String,
        val censored: String,
        val pattern: Regex,
        val patternUpperFirst: Regex,
    )

    /** Применяет правила к тексту. */
    fun apply(text: String): String {
        var result = text
        rules.forEach { rule ->
            result = result.replace(rule.pattern, rule.censored)
            result = result.replace(rule.patternUpperFirst, rule.censored.uppercaseFirstLetter())
        }
        return result
    }

    /** Число применимых правил (для диагностики). */
    val size: Int get() = rules.size

    companion object {
        /**
         * Читает словарь «Censored» и собирает правила.
         *
         * BR-289: пустые и заведомо неразбираемые значения отбрасываются (с записью
         * в лог) — они не маскировали ничего, но ломали либо сохранение песни
         * (`PatternSyntaxException`), либо маску.
         */
        fun load(database: KaraokeConnection): CensoredDictionaryRules =
            buildRules(CensoredWordsDictionary(database = database).dict)

        /**
         * BR-289: сборка правил из готового списка значений словаря.
         *
         * Вынесено отдельно от [load], чтобы правила можно было проверить БЕЗ БД
         * (регрессионный тест пакета `PKG-S3-26`).
         */
        internal fun buildRules(values: List<String>): CensoredDictionaryRules {
            val rules = mutableListOf<Rule>()
            values.forEach { value ->
                val (uncensored, censored) = getCensoredPair(value)
                if (uncensored.isBlank()) {
                    censoredLog.warn(
                        "censor: значение словаря без слова — пропущено (длина значения={})",
                        value.length,
                    )
                    return@forEach
                }
                // BR-289: значение словаря — литеральный текст, а не regex-шаблон.
                val escaped = Regex.escape(uncensored)
                val upperFirst = uncensored.uppercaseFirstLetter()
                val pattern =
                    try {
                        Regex("(?<![\\p{L}\\p{N}_])$escaped(?![\\p{L}\\p{N}_])")
                    } catch (e: Exception) {
                        censoredLog.warn(
                            "censor: не удалось собрать шаблон для значения словаря — значение пропущено",
                            e,
                        )
                        return@forEach
                    }
                val patternUpperFirst =
                    try {
                        Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(upperFirst)}(?![\\p{L}\\p{N}_])")
                    } catch (e: Exception) {
                        censoredLog.warn(
                            "censor: не удалось собрать шаблон для значения словаря с заглавной буквы — значение пропущено",
                            e,
                        )
                        return@forEach
                    }
                rules.add(
                    Rule(
                        uncensored = uncensored,
                        censored = censored,
                        pattern = pattern,
                        patternUpperFirst = patternUpperFirst,
                    ),
                )
            }
            return CensoredDictionaryRules(rules)
        }
    }
}

/** BR-289/BR-294: логгер сборки правил цензурирования. */
private val censoredLog = org.slf4j.LoggerFactory.getLogger("infra.song.voices")

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
 *
 * BR-294: для пакетных операций используйте перегрузку с готовыми
 * [CensoredDictionaryRules] — словарь читается и шаблоны компилируются один раз
 * на пакет, а не на каждую песню.
 */
fun String.censored(database: KaraokeConnection): String = censored(CensoredDictionaryRules.load(database))

/**
 * BR-294 (аудит #336, пакет PKG-S3-26): применение заранее собранных правил.
 *
 * Словарь не читается заново, шаблоны не компилируются заново.
 */
fun String.censored(rules: CensoredDictionaryRules): String = rules.apply(this)

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
