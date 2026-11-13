package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * ИНЦИДЕНТ 2026-11-13 («Концертные синглы», Король и Шут): у песен, добавленных из папки,
 * длительность (`song_ms`) оставалась нулевой.
 *
 * Причина: объект песни из импорта живёт в памяти десятки секунд (поиск текста), и за это время
 * параллельный кортеж (демукс → mp3 → загрузка) записывает в БД длительность и тональность/темп.
 * Правки применялись к УСТАРЕВШЕМУ объекту — `getDiff()` видел расхождение по этим полям и
 * `UPDATE` затирал их пустыми значениями. Тональность и темп восстанавливались пост-хуком
 * кортежа, а длительность — нет, поэтому в БД оставался `song_ms = 0`.
 *
 * Контракты:
 * 1. правки родителя/аудио-родителя применяются к СВЕЖЕЙ загрузке песни;
 * 2. измеренная длительность источника сохраняется в БД (а не только в sidecar).
 */
class ImportDurationOverwriteTest {
    private fun repoFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        val file = File(dir, relativePath)
        assertTrue(file.isFile, "файл не найден: $relativePath")
        return file
    }

    private val utils = repoFile("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt")
    private val song = repoFile("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt")

    private fun bodyOf(
        text: String,
        signature: String,
    ): String {
        val start = text.indexOf(signature)
        assertTrue(start >= 0, "не найдена функция: $signature")
        val end = text.indexOf("\n}", start)
        assertTrue(end > start, "не найден конец функции: $signature")
        return text.substring(start, end)
    }

    @Test
    fun `правки родителя применяются к свежей загрузке песни`() {
        val body = bodyOf(utils.readText(), "fun applyDuplicateOriginal(")
        assertTrue(
            body.contains("reloadForApply()"),
            "applyDuplicateOriginal должен перечитать песню из БД перед применением правок",
        )
        assertTrue(
            body.contains("target.saveToDbLocked()"),
            "сохраняться должен перечитанный объект, а не устаревший",
        )
        assertTrue(
            !body.contains("newSong.saveToDbLocked()"),
            "устаревший объект песни сохранять нельзя — он затирает значения кортежа",
        )
    }

    @Test
    fun `маркеры аудио-родителя применяются к свежей загрузке песни`() {
        val body = bodyOf(utils.readText(), "fun applyAudioParentMarkers(")
        assertTrue(
            body.contains("reloadForApply()"),
            "applyAudioParentMarkers должен перечитать песню из БД перед применением правок",
        )
        assertTrue(
            body.contains("target.saveToDbLocked()"),
            "сохраняться должен перечитанный объект, а не устаревший",
        )
        assertTrue(
            body.contains("shiftMarkersAndFixEnd(audioParent.sourceMarkers, deltaMs, target.ms)"),
            "сдвиг маркеров должен считаться от фактической длительности песни",
        )
    }

    @Test
    fun `измеренная длительность источника сохраняется в БД`() {
        val text = utils.readText()
        assertTrue(
            text.contains("song.fields[SongField.MS] = sourceDuration.toString()"),
            "шаг key/bpm должен класть измеренную длительность в объект песни — её запишет saveToDbLocked",
        )
    }

    @Test
    fun `длительность можно записать без повторного ffprobe`() {
        val text = song.readText()
        assertTrue(
            text.contains("fun refreshDurationMsFromAudioFile(measuredDurationMs: Long? = null)"),
            "финализатор демукса должен использовать уже измеренную длительность",
        )
        assertTrue(
            text.contains("val durationMs = measuredDurationMs ?: measureDurationMsFromAudioFile()"),
            "измерение должно выполняться только если длительность ещё не измерена",
        )
    }

    @Test
    fun `есть починка уже испорченных песен`() {
        val controller =
            repoFile("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ArtifactAdminController.kt")
        val service =
            repoFile("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/DurationBackfillService.kt")
        assertTrue(
            controller.readText().contains("@PostMapping(\"/backfill-duration\")"),
            "нужен админский маршрут починки длительности",
        )
        assertTrue(
            service.readText().contains("song.ms > 0L"),
            "починка не должна трогать песни, у которых длительность уже есть",
        )
        assertTrue(
            service.readText().contains("dryRun: Boolean = true"),
            "по умолчанию — сухой прогон",
        )
    }

    @Test
    fun `есть перечитка песни перед применением правок`() {
        val text = song.readText()
        assertTrue(
            text.contains("fun reloadForApply(): Song?"),
            "нужен единый помощник перечитки песни перед применением правок",
        )
        assertTrue(
            text.contains("Song.loadFromDbById("),
            "перечитка должна идти из БД",
        )
    }
}
