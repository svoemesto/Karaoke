package com.svoemesto.karaokeapp.services

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Регрессионный инвариант для BR-038 + BR-060 (аудит #336, OpenProject #368):
 * в аудируемых файлах не должно оставаться СЫРЫХ одноразовых потоков
 * (`kotlin.concurrent.thread`).
 *
 * Почему инвариант, а не поведение: `startOneShotThread` (см. [OneShotThreadTest])
 * закрывает thread-local соединение потока в `finally` — но только если через него
 * запущен поток. Сырой `thread { … }` этот контракт обходит: соединение потока
 * остаётся в `ThreadLocal` мёртвого потока до недетерминированной финализации
 * сборщиком мусора (утечка канала к PostgreSQL).
 *
 * **До правки** проверка падала: в перечисленных файлах были сырые `thread { … }`
 * без `closeThreadConnection()`. **После правки** — все одноразовые фоновые потоки
 * идут через `startOneShotThread`.
 *
 * @see OneShotThreadTest поведенческая проверка закрытия соединения.
 */
class OneShotThreadWiringTest {
    /**
     * Файлы, в которых одноразовые фоновые потоки обязаны идти через
     * [startOneShotThread] (BR-038: `KaraokeProcessWorker`; BR-060: `ApiController`
     * + связанные локации `SyncAudioDescendants`/`Utils`).
     */
    private val auditedFiles =
        listOf(
            "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt",
            "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt",
            "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SyncAudioDescendants.kt",
            "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt",
        )

    /**
     * Сырой одноразовый поток: `thread { … }` / `thread( … )` / `Thread { … }`.
     *
     * Lookbehind `(?<![\w.])` отсекает `startOneShotThread(` (перед «Thread» стоит
     * буква) и `kotlin.concurrent.thread` (перед «thread» стоит точка). Ветка
     * `thread\((?!\s*\))` отсекает суперкласс `) : Thread() {` — это объявление класса,
     * а не запуск потока.
     */
    private val rawThread =
        Regex(
            """(?<![\w.])thread\s*\{|(?<![\w.])thread\s*\((?!\s*\))""",
            RegexOption.IGNORE_CASE,
        )

    @Test
    fun `в аудируемых файлах нет сырых одноразовых потоков`() {
        val offenders =
            auditedFiles.mapNotNull { path ->
                val text = repoRootFile(path).readText()
                val lines = text.lines()
                val hits = lines.filter { rawThread.containsMatchIn(it) }
                if (hits.isEmpty()) null else path to hits
            }

        assertTrue(
            offenders.isEmpty(),
            "в этих файлах найдены сырые одноразовые потоки (утечка thread-local соединения): " +
                offenders.joinToString("; ") { (path, hits) -> "$path → ${hits.joinToString(" | ")}" },
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

    /** Файл по пути относительно корня репозитория. */
    private fun repoRootFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        val file = File(dir, relativePath)
        assertTrue(file.isFile, "файл не найден: $relativePath")
        return file
    }
}
