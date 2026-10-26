package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.audit.TestRepoRoot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Регрессионный инвариант для BR-038 + BR-060 + BR-627 (аудит #336, OpenProject #368
 * и #410): в файлах прикладного кода, обращающихся к БД, не должно оставаться
 * СЫРЫХ одноразовых потоков (`kotlin.concurrent.thread`, `java.lang.Thread { … }`).
 *
 * Почему инвариант, а не поведение: `startOneShotThread` (см. [OneShotThreadTest])
 * закрывает thread-local соединение потока в `finally` — но только если через него
 * запущен поток. Сырой `thread { … }` этот контракт обходит: соединение потока
 * остаётся в `ThreadLocal` мёртвого потока до недетерминированной финализации
 * сборщиком мусора (утечка канала к PostgreSQL → `sorry, too many clients already`).
 *
 * **До правки** проверка падала: в четырёх файлах с обращением к БД были сырые
 * `thread { … }` без закрытия соединения:
 * `ExportAlignmentDataset.kt`, `StorageCacheWarm.kt`, `CacheEtagSizeBackfill.kt`,
 * `controllers/SongEditorController.kt`. Старая версия проверки смотрела на
 * фиксированный перечень из двух других файлов — поэтому эти четыре места
 * никем не проверялись. **После правки** проверка охватывает **все** файлы
 * прикладного кода, обращающиеся к БД (FR-003), и требует, чтобы одноразовые
 * фоновые потоки шли через [startOneShotThread].
 *
 * @see OneShotThreadTest поведенческая проверка закрытия соединения.
 * @see RawThreadDbGuard детектор (комментарии, маркеры обращения к БД, сырые потоки).
 */
class OneShotThreadWiringTest {
    /**
     * Файлы, в которых одноразовые фоновые потоки обязаны идти через
     * [startOneShotThread] (BR-038: `KaraokeProcessWorker`; BR-060: `ApiController`
     * + связанные локации `SyncAudioDescendants`/`Utils`).
     *
     * Это адресный (точечный) слой проверки. Широкий слой — обход всех файлов
     * прикладного кода, обращающихся к БД, — см. тест ниже.
     */
    private val auditedFiles =
        listOf(
            "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt",
            "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt",
            "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SyncAudioDescendants.kt",
            "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt",
        )

    /**
     * Файлы с обращением к БД, которые **не проверялись** до правки (FR-003) и в
     * которых были сырые фоновые потоки (FR-002).
     *
     * Список нужен как «до/после»-якорь: проверка обязана видеть эти файлы как
     * файлы, обращающиеся к БД, и требовать в них канонического способа запуска.
     */
    private val previouslyUncoveredDbFiles =
        listOf(
            "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/ExportAlignmentDataset.kt",
            "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/StorageCacheWarm.kt",
            "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/CacheEtagSizeBackfill.kt",
            "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt",
        )

    @Test
    fun `в аудируемых файлах нет сырых одноразовых потоков`() {
        val offenders =
            auditedFiles.mapNotNull { path ->
                val code = RawThreadDbGuard.stripNonCode(repoRootFile(path).readText())
                val hits = RawThreadDbGuard.findRawThreads(code)
                if (hits.isEmpty()) null else path to hits
            }

        assertTrue(
            offenders.isEmpty(),
            "в этих файлах найдены сырые одноразовые потоки (утечка thread-local соединения): " +
                offenders.joinToString("; ") { (path, hits) ->
                    "$path → ${hits.joinToString(" | ") { "${it.line}: ${it.snippet}" }}"
                },
        )
    }

    @Test
    fun `поток очереди запускается через startOneShotThread`() {
        val text = repoRootFile(auditedFiles.first()).readText()

        assertTrue(
            text.contains("startOneShotThread"),
            "поток главного цикла очереди обязан идти через startOneShotThread " +
                "(BR-038: иначе соединение одноразового потока не закрывается)",
        )
    }

    /**
     * Широкий слой проверки (FR-002 + FR-003 + SC-003): обходятся **все** файлы
     * прикладного кода, обращающиеся к БД, и в каждом запрещены сырые фоновые
     * потоки.
     */
    @Test
    fun `во всех файлах прикладного кода с обращением к БД нет сырых фоновых потоков`() {
        val repoRoot = repoRoot()
        val result =
            RawThreadDbGuard.scan(
                RawThreadDbGuard.appSourceRoots.map { File(repoRoot, it) },
            )

        val dbFilesRelative =
            result.dbFiles.map { relativeToRepo(repoRoot, it.path) }.toSet()

        // Сводка по охвату — в лог теста: видно, сколько файлов прикладного кода
        // обращается к БД и подпадает под проверку (SC-003).
        println(
            "[OneShotThreadWiringTest] охват: файлов прикладного кода, обращающихся к БД — " +
                "${result.dbFiles.size}; сырых фоновых потоков — ${result.violations.size}",
        )

        assertTrue(
            result.violations.isEmpty(),
            "в файлах прикладного кода, обращающихся к БД, найдены сырые фоновые потоки " +
                "(утечка thread-local соединения потока): " +
                result.violations.joinToString("; ") { "${it.path}:${it.line} [${it.kind}] ${it.snippet}" },
        )

        assertTrue(
            result.dbFiles.size >= 100,
            "проверка должна охватывать все файлы прикладного кода, обращающиеся к БД, " +
                "а не фиксированный перечень: обнаружено всего ${result.dbFiles.size}",
        )

        // «До/после»: файлы, которые раньше не проверялись, теперь обязаны быть в
        // охвате — иначе утечка в них снова проскочит незамеченной.
        val missing =
            previouslyUncoveredDbFiles.filter { relativePath ->
                dbFilesRelative.none { it.endsWith(relativePath) }
            }

        assertTrue(
            missing.isEmpty(),
            "эти файлы обращаются к БД, но не попали в охват проверки: ${missing.joinToString(", ")}",
        )
    }

    /**
     * Негативная фикстура «до/после» (FR-006): та же проверка на копии **реального**
     * файла с обращением к БД, в который внедрён сырой поток, обязана упасть и
     * показать, где именно нарушение.
     */
    @Test
    fun `негативная фикстура — внедрённый сырой поток в файле с обращением к БД обнаруживается`() {
        val source = repoRootFile(previouslyUncoveredDbFiles[1])
        val dir = Files.createTempDirectory("karaoke-raw-thread-fixture").toFile()
        try {
            val copy = File(dir, source.name)
            source.copyTo(copy, overwrite = true)
            copy.writeText(
                copy.readText() +
                    "\n\n// Фикстура «до правки»: сырой одноразовый поток, ходящий в БД.\n" +
                    "fun fixtureRawThread() {\n" +
                    "    thread {\n" +
                    "        val c = WORKING_DATABASE.getConnection()\n" +
                    "        println(c)\n" +
                    "    }\n" +
                    "}\n",
            )

            val result = RawThreadDbGuard.scan(listOf(dir))

            assertEquals(
                1,
                result.dbFiles.size,
                "копия файла с обращением к БД должна распознаваться как файл, ходящий в БД",
            )
            assertEquals(
                1,
                result.violations.size,
                "внедрённый сырой поток обязан быть найден: ${result.violations}",
            )
            val violation = result.violations.first()
            assertEquals(RawThreadDbGuard.ThreadKind.KOTLIN_THREAD, violation.kind)
            assertTrue(violation.line > 0, "нарушение должно указывать номер строки")
            assertTrue(
                violation.snippet.contains("thread {"),
                "нарушение должно указывать строку-источник, а не просто номер",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * Различение нарушения и допустимого случая (FR-004): сырой поток в файле,
     * который **не обращается к БД**, нарушением не считается.
     */
    @Test
    fun `сырой поток в файле без обращений к БД нарушением не считается`() {
        val dir = Files.createTempDirectory("karaoke-no-db-fixture").toFile()
        try {
            File(dir, "NoDatabase.kt").writeText(
                "package fixture\n\n" +
                    "fun noDatabaseThread() {\n" +
                    "    thread {\n" +
                    "        println(\"просто поток без БД\")\n" +
                    "    }\n" +
                    "}\n",
            )

            val result = RawThreadDbGuard.scan(listOf(dir))

            assertTrue(
                result.dbFiles.isEmpty(),
                "файл без обращений к БД не должен считаться файлом, ходящим в БД: ${result.dbFiles}",
            )
            assertTrue(
                result.violations.isEmpty(),
                "сырой поток в файле без обращений к БД — не нарушение: ${result.violations}",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * Обратная сторона различения: тот же файл, но с обращением к БД, нарушением
     * **является** — иначе проверка была бы «зелёной» всегда.
     */
    @Test
    fun `сырой поток в файле с обращением к БД нарушением является`() {
        val dir = Files.createTempDirectory("karaoke-with-db-fixture").toFile()
        try {
            File(dir, "WithDatabase.kt").writeText(
                "package fixture\n\n" +
                    "fun withDatabaseThread(database: KaraokeConnection) {\n" +
                    "    thread {\n" +
                    "        val c = database.getConnection()\n" +
                    "        println(c)\n" +
                    "    }\n" +
                    "}\n",
            )

            val result = RawThreadDbGuard.scan(listOf(dir))

            assertEquals(1, result.dbFiles.size, "файл с обращением к БД должен быть распознан")
            assertEquals(1, result.violations.size, "сырой поток в таком файле — нарушение")
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Корень репозитория (каталог с `settings.gradle.kts`). */
    private fun repoRoot(): File = TestRepoRoot.repoRoot()

    /** Файл по пути относительно корня репозитория. */
    private fun repoRootFile(relativePath: String): File {
        val file = TestRepoRoot.file(relativePath)
        assertTrue(file.isFile, "файл не найден: $relativePath")
        return file
    }

    /** Путь файла относительно корня репозитория (для стабильных сообщений). */
    private fun relativeToRepo(
        repoRoot: File,
        path: String,
    ): String =
        runCatching { File(path).relativeTo(repoRoot).path }.getOrDefault(path)
}
