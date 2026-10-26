package com.svoemesto.karaokeapp.services

import java.io.File

/**
 * Детектор сырых фоновых потоков в файлах прикладного кода, обращающихся к БД
 * (аудит #336, OpenProject #410, BR-627; спека `specs/645-fix-connection-leak`).
 *
 * Зачем отдельный объект, а не регулярка прямо в тесте: проверка должна
 * **различать** нарушение (сырой поток в файле, который ходит в БД) от
 * допустимого случая (тот же приём в файле без обращений к БД) — FR-004, — и
 * должна уметь показать, где именно нарушение (файл + строка) — US2.
 *
 * **Что считается нарушением:**
 *
 * 1. `kotlin.concurrent.thread` в любой форме: `thread { … }`, `thread(…)`,
 *    `kotlin.concurrent.thread { … }`. Это ровно та конструкция, которую запрещает
 *    FR-002: она создаёт одноразовый поток и **не** закрывает его thread-local
 *    соединение — физический канал к PostgreSQL остаётся висеть до
 *    недетерминированной финализации сборщиком мусора.
 * 2. `java.lang.Thread { … }` (SAM-конструктор) в файле, который ходит в БД, —
 *    с одной исключением: `addShutdownHook(Thread { … })`. Shutdown hook — не
 *    фоновый рабочий поток и в БД не ходит; в прикладном коде он встречается
 *    ровно в двух местах (`HealthReport.kt`, `StorageMetadataCache.kt`).
 *
 * **Что НЕ считается нарушением** (и почему):
 *
 * - `Thread(…)` с non-SAM-аргументами — фабрика потока пула
 *   (`Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "…") }`) или
 *   долгоживущий consumer-поток (`Thread(::doStart)`). Это переиспользуемые
 *   потоки: по контракту ADR `local-0009` их соединение остаётся в кеше.
 * - канонический `Thread(` внутри `services/OneShotThread.kt` — это и есть
 *   реализация разрешённого способа запуска.
 * - `Thread({ … }, "name")` (SAM-лямбда в скобках) — **известная граница**
 *   статического анализа: отличить одноразовый рабочий поток от фабрики потока
 *   пула без разбора тела нельзя. В прикладном коде таких мест нет.
 * - любой поток в файле, который **не обращается к БД** (US2, сценарий 2).
 *
 * @see OneShotThreadWiringTest проверка, которая использует этот детектор.
 * @see com.svoemesto.karaokeapp.services.startOneShotThread канонический способ запуска.
 */
internal object RawThreadDbGuard {
    /**
     * Корни исходников прикладного кода, в которых ищутся нарушения.
     *
     * `karaoke-db` — legacy-модуль, в продакшене не используется (Constitution,
     * «Технологический стек»), поэтому в охват не входит.
     */
    val appSourceRoots: List<String> =
        listOf(
            "karaoke-app/src/main/kotlin",
            "karaoke-web/src/main/kotlin",
        )

    /**
     * Маркеры обращения к БД: если код файла содержит хотя бы один — файл
     * «ходит в БД» и сырой поток в нём является нарушением.
     *
     * Список покрывает прямые обращения (`getConnection()`, прямой SQL,
     * фабрики соединений, синглтон `WORKING_DATABASE`) и косвенные (тип
     * `KaraokeConnection`, базовый класс сущностей `KaraokeDbTable`, передача
     * экземпляра БД в вызов). Последняя пара маркеров нужна, чтобы ловить файлы,
     * которые только передают БД дальше, не трогая JDBC напрямую.
     *
     * Lookbehind `(?<![\w])` (а НЕ `(?<![\w.])`): точка перед именем — это
     * вызов метода (`db.getConnection()`), и такой вызов ОБЯЗАН считаться
     * обращением к БД.
     */
    private val dbMarkers: List<Pair<String, Regex>> =
        listOf(
            "getConnection()" to Regex("""(?<![\w])getConnection\s*\("""),
            "prepareStatement()" to Regex("""(?<![\w])prepareStatement\s*\("""),
            "createStatement()" to Regex("""(?<![\w])createStatement\s*\("""),
            "executeQuery()" to Regex("""(?<![\w])executeQuery\s*\("""),
            "executeUpdate()" to Regex("""(?<![\w])executeUpdate\s*\("""),
            "Connection.local()/remote()/virtual()" to Regex("""(?<![\w])Connection\.(local|remote|virtual)\s*\("""),
            "WORKING_DATABASE" to Regex("""(?<![\w])WORKING_DATABASE(?!\w)"""),
            "KaraokeConnection" to Regex("""(?<![\w])KaraokeConnection(?!\w)"""),
            "KaraokeDbTable" to Regex("""(?<![\w])KaraokeDbTable(?!\w)"""),
            "database =" to Regex("""(?<![\w])database\s*="""),
            "database: KaraokeConnection" to Regex("""(?<![\w])database\s*:\s*KaraokeConnection"""),
        )

    /**
     * `kotlin.concurrent.thread` — запрещённая конструкция (FR-002).
     *
     * Регистрозависимо: `thread` (нижний) — stdlib-функция, `Thread` (верхний) —
     * `java.lang.Thread`. Lookbehind `(?<![\w])` отсекает `startOneShotThread`
     * (перед «thread» буква), но НЕ отсекает `kotlin.concurrent.thread { … }` —
     * такой вызов ловится отдельной веткой ниже.
     */
    private val kotlinThreadRegex =
        Regex(
            """(?<![\w])thread\s*\{|(?<![\w])thread\s*\((?!\s*\))|(?<![\w])kotlin\.concurrent\.thread\s*[({]""",
        )

    /** `java.lang.Thread { … }` — SAM-конструктор одноразового потока. */
    private val javaThreadSamRegex = Regex("""(?<![\w])Thread\s*\{""")

    /** JVM shutdown hook — не фоновый рабочий поток, исключение из проверки. */
    private val shutdownHookRegex = Regex("""addShutdownHook\s*\(""")

    /** Тип найденного сырого фонового потока. */
    internal enum class ThreadKind {
        /** `kotlin.concurrent.thread` — прямо запрещён FR-002. */
        KOTLIN_THREAD,

        /** `java.lang.Thread { … }` — одноразовый поток в файле с обращением к БД. */
        JAVA_THREAD_SAM,
    }

    /**
     * Найденный сырой фоновый поток.
     *
     * @param line номер строки (1-based) в исходном файле.
     * @param kind тип конструкции.
     * @param snippet строка-источник (усечённая) — для сообщения об ошибке.
     */
    internal data class RawThreadHit(
        val line: Int,
        val kind: ThreadKind,
        val snippet: String,
    )

    /**
     * Файл прикладного кода, обращающийся к БД.
     *
     * @param path путь файла (как вернул обход).
     * @param dbMarkers сработавшие маркеры обращения к БД (диагностика).
     */
    internal data class DbSourceFile(
        val path: String,
        val dbMarkers: List<String>,
    )

    /**
     * Нарушение: сырой фоновый поток в файле, обращающемся к БД.
     *
     * @param path путь к файлу.
     * @param line номер строки.
     * @param kind тип конструкции.
     * @param snippet строка-источник (усечённая).
     */
    internal data class Violation(
        val path: String,
        val line: Int,
        val kind: ThreadKind,
        val snippet: String,
    )

    /**
     * Результат обхода дерева исходников.
     *
     * @param dbFiles все найденные файлы, обращающиеся к БД.
     * @param violations нарушения (сырые потоки в файлах с обращением к БД).
     */
    internal data class ScanResult(
        val dbFiles: List<DbSourceFile>,
        val violations: List<Violation>,
    )

    /**
     * Возвращает текст, в котором комментарии и содержимое строковых литералов
     * заменены пробелами (переводы строк сохраняются — номера строк не «плывут»).
     *
     * Отдельно обрабатываются: `//`-комментарии, `/* … */`-комментарии (в том числе
     * KDoc), обычные строки, raw-строки (`"""…"""`), символьные литералы и
     * интерполяция `${…}` внутри строк (код внутри интерполяции **сохраняется** —
     * иначе, например, `getConnection()` внутри шаблона строки не был бы замечен).
     */
    fun stripNonCode(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        val n = text.length
        val modes = ArrayDeque<Int>()
        modes.addLast(CODE)
        val templateDepths = ArrayDeque<Int>()
        while (i < n) {
            val mode = modes.last()
            val c = text[i]
            val next = if (i + 1 < n) text[i + 1] else ' '
            when (mode) {
                CODE -> {
                    when {
                        c == '/' && next == '/' -> {
                            out.append("  ")
                            i += 2
                            modes.addLast(LINE_COMMENT)
                        }

                        c == '/' && next == '*' -> {
                            out.append("  ")
                            i += 2
                            modes.addLast(BLOCK_COMMENT)
                        }

                        c == '"' && text.startsWith("\"\"\"", i) -> {
                            out.append("   ")
                            i += 3
                            modes.addLast(RAW_STRING)
                        }

                        c == '"' -> {
                            out.append(' ')
                            i++
                            modes.addLast(STRING)
                        }

                        c == '\'' -> {
                            out.append(' ')
                            i++
                            modes.addLast(CHAR)
                        }

                        else -> {
                            out.append(c)
                            i++
                        }
                    }
                }

                LINE_COMMENT -> {
                    if (c == '\n') {
                        out.append('\n')
                        modes.removeLast()
                    } else {
                        out.append(' ')
                    }
                    i++
                }

                BLOCK_COMMENT -> {
                    if (c == '*' && next == '/') {
                        out.append("  ")
                        i += 2
                        modes.removeLast()
                    } else {
                        out.append(if (c == '\n') '\n' else ' ')
                        i++
                    }
                }

                STRING, RAW_STRING -> {
                    val isRaw = mode == RAW_STRING
                    when {
                        !isRaw && c == '\\' -> {
                            out.append("  ")
                            i += 2
                        }

                        isRaw && text.startsWith("\"\"\"", i) -> {
                            out.append("   ")
                            i += 3
                            modes.removeLast()
                        }

                        !isRaw && c == '"' -> {
                            out.append(' ')
                            i++
                            modes.removeLast()
                        }

                        c == '$' && next == '{' -> {
                            out.append("  ")
                            i += 2
                            modes.addLast(TEMPLATE_CODE)
                            templateDepths.addLast(0)
                        }

                        else -> {
                            out.append(if (c == '\n') '\n' else ' ')
                            i++
                        }
                    }
                }

                CHAR -> {
                    when {
                        c == '\\' -> {
                            out.append("  ")
                            i += 2
                        }

                        c == '\'' -> {
                            out.append(' ')
                            i++
                            modes.removeLast()
                        }

                        else -> {
                            out.append(if (c == '\n') '\n' else ' ')
                            i++
                        }
                    }
                }

                TEMPLATE_CODE -> {
                    val templateDepth = templateDepths.lastOrNull() ?: 0
                    when {
                        c == '{' -> {
                            templateDepths.removeLast()
                            templateDepths.addLast(templateDepth + 1)
                            out.append(c)
                            i++
                        }

                        c == '}' && templateDepth == 0 -> {
                            out.append(' ')
                            i++
                            modes.removeLast()
                            templateDepths.removeLast()
                        }

                        c == '}' -> {
                            templateDepths.removeLast()
                            templateDepths.addLast(templateDepth - 1)
                            out.append(c)
                            i++
                        }

                        c == '/' && next == '/' -> {
                            out.append("  ")
                            i += 2
                            modes.addLast(LINE_COMMENT)
                        }

                        c == '/' && next == '*' -> {
                            out.append("  ")
                            i += 2
                            modes.addLast(BLOCK_COMMENT)
                        }

                        c == '"' && text.startsWith("\"\"\"", i) -> {
                            out.append("   ")
                            i += 3
                            modes.addLast(RAW_STRING)
                        }

                        c == '"' -> {
                            out.append(' ')
                            i++
                            modes.addLast(STRING)
                        }

                        else -> {
                            out.append(c)
                            i++
                        }
                    }
                }
            }
        }
        return out.toString()
    }

    /**
     * Сработавшие маркеры обращения к БД для кода файла.
     *
     * @param code текст файла, уже очищенный от комментариев и строковых литералов.
     */
    fun detectDbMarkers(code: String): List<String> = dbMarkers.filter { (_, regex) -> regex.containsMatchIn(code) }.map { it.first }

    /**
     * Ищет сырые фоновые потоки в коде файла.
     *
     * @param code текст файла, уже очищенный от комментариев и строковых литералов.
     * @return список найденных конструкций с номерами строк.
     */
    fun findRawThreads(code: String): List<RawThreadHit> {
        val hits = mutableListOf<RawThreadHit>()
        code.lines().forEachIndexed { index, line ->
            when {
                kotlinThreadRegex.containsMatchIn(line) ->
                    hits.add(RawThreadHit(index + 1, ThreadKind.KOTLIN_THREAD, line.trim().take(120)))

                javaThreadSamRegex.containsMatchIn(line) && !shutdownHookRegex.containsMatchIn(line) ->
                    hits.add(RawThreadHit(index + 1, ThreadKind.JAVA_THREAD_SAM, line.trim().take(120)))
            }
        }
        return hits
    }

    /**
     * Обходит дерево исходников и находит файлы, обращающиеся к БД, и нарушения.
     *
     * @param roots корни, в которых ищутся `.kt`-файлы.
     * @return результат обхода: список DB-файлов и список нарушений.
     */
    fun scan(roots: List<File>): ScanResult {
        val dbFiles = mutableListOf<DbSourceFile>()
        val violations = mutableListOf<Violation>()
        roots.forEach { root ->
            if (!root.isDirectory) return@forEach
            val ktFiles = root.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.sortedBy { it.path }
            ktFiles.forEach { file ->
                val raw = file.readText()
                val code = stripNonCode(raw)
                val markers = detectDbMarkers(code)
                if (markers.isEmpty()) return@forEach
                dbFiles.add(DbSourceFile(file.path, markers))
                findRawThreads(code).forEach { hit ->
                    violations.add(Violation(file.path, hit.line, hit.kind, hit.snippet))
                }
            }
        }
        return ScanResult(dbFiles, violations)
    }

    private const val CODE = 0
    private const val LINE_COMMENT = 1
    private const val BLOCK_COMMENT = 2
    private const val STRING = 3
    private const val RAW_STRING = 4
    private const val CHAR = 5
    private const val TEMPLATE_CODE = 6
}
