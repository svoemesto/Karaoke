package com.svoemesto.karaokeapp.tuple

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * ИНЦИДЕНТ 2026-11-03 (песня 30428): в цепочке демукса шаги переноса (`mv`/`chmod`)
 * стояли ПОСЛЕ `docker run`. Когда demucs ничего не создавал — типичный случай «GPU
 * занят, `torch.cuda.OutOfMemoryError`» — цепочка рвалась на первом же `mv`, и
 * финальный шаг `finalizeDemucs` (повтор без GPU по спеке 674 FR-009 + запись
 * длительности песни) не доходил никогда.
 *
 * Инвариант: между шагом `docker run` и `finalizeDemucs` в цепочке демукса не должно
 * быть шагов, падающих из-за отсутствующих файлов результата.
 */
class DemucsChainShapeTest {
    private val songSource: File =
        File("src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt")

    private fun demuxFunctionBody(name: String): String {
        assertTrue(songSource.isFile, "не найден исходник Song.kt: ${songSource.absolutePath}")
        val text = songSource.readText()
        val start = text.indexOf("fun $name(")
        assertTrue(start >= 0, "не найдена функция $name")
        // Тело функции — до следующей «fun » на том же уровне.
        val next = text.indexOf("\n    fun ", start + 1)
        return if (next > 0) text.substring(start, next) else text.substring(start)
    }

    @Test
    fun `в цепочке демукса нет шагов переноса файлов`() {
        listOf(
            "argsDemucs2",
            "argsDemucs5",
            "argsDemucs2RetryCpu",
            "argsDemucs5RetryCpu",
        ).forEach { fn ->
            val body = demuxFunctionBody(fn)
            assertTrue(
                !body.contains("listOf(\"mv\""),
                "$fn: шага `mv` в цепочке быть не должно — перенос делает finalizeDemucs",
            )
            assertTrue(
                !body.contains("listOf(\"chmod\", \"666\""),
                "$fn: шага `chmod 666` в цепочке быть не должно — перенос делает finalizeDemucs",
            )
        }
    }

    @Test
    fun `финальная проверка демукса идёт сразу после запуска контейнера`() {
        listOf("argsDemucs2", "argsDemucs5", "argsDemucs2RetryCpu", "argsDemucs5RetryCpu").forEach { fn ->
            val body = demuxFunctionBody(fn)
            val dockerAt = body.indexOf("\"docker\"")
            val finalizeAt = body.indexOf("\"finalizeDemucs\"")
            assertTrue(dockerAt >= 0, "$fn: нет шага docker run")
            assertTrue(finalizeAt > dockerAt, "$fn: finalizeDemucs должен идти после docker run")
            val between = body.substring(dockerAt, finalizeAt)
            assertTrue(
                !between.contains("listOf(\"mv\"") && !between.contains("listOf(\"chmod\", \"666\""),
                "$fn: между docker run и finalizeDemucs не должно быть шагов, зависящих от наличия файлов",
            )
        }
    }
}
