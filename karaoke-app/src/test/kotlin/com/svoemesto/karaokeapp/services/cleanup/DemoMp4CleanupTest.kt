package com.svoemesto.karaokeapp.services.cleanup

import com.svoemesto.karaokeapp.model.SongOutputFile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Юнит-тесты удаления в конце шага публикации (specs/544-song-artifact-cleanup, T010/T011).
 *
 * Покрывает FR-001 (демо удаляется и при успехе, и при провале), FR-005 (картинки —
 * только при успехе обеих площадок), FR-011 (уборка выключена по умолчанию, сухой
 * прогон) и FR-012 (каждое удаление в журнале).
 *
 * `closeIfBothChannelsDone` сама по себе требует Song + БД + оба хранилища и юнит-тестом
 * не покрывается (как и остальные интеграционные пути проекта, Constitution § «Рабочий
 * процесс»); здесь проверяются её чистые части: решение о наборе классов и само удаление.
 *
 * @see specs/544-song-artifact-cleanup/data-model.md
 */
class DemoMp4CleanupTest {
    private val videoExt = "." + SongOutputFile.VIDEO.extension

    @TempDir
    lateinit var tempDir: Path

    private fun policy(
        enabled: Boolean = true,
        dryRun: Boolean = false,
    ) = CleanupPolicy(enabled = enabled, dryRun = dryRun, journalDir = tempDir.resolve("logs").toString())

    private fun demoFile(): Path {
        val f = tempDir.resolve("done_files/song [demo]" + videoExt)
        Files.createDirectories(f.parent)
        Files.writeString(f, "x".repeat(10))
        return f
    }

    @Test
    fun `демо удаляется при успехе обеих площадок`() {
        val file = demoFile()

        val outcome =
            ArtifactCleanupAutomation.deleteAfterStage(
                classId = ArtifactCleanupClass.DEMO_MP4,
                path = file.toString(),
                policy = policy(),
            )

        assertEquals(1, outcome.deleted)
        assertEquals(10L, outcome.freedBytes)
        assertFalse(Files.exists(file), "демо-ролик должен быть удалён")
    }

    @Test
    fun `демо удаляется, а картинки остаются, когда публикация провалилась`() {
        // FR-001: демо-ролик удаляется независимо от исхода публикации — оба канала
        // закрыты, значит файл больше не нужен (повторная публикация отрендерит заново, FR-002).
        // FR-005: картинки при провале нужны повторной попытке — остаются.
        //
        // Сценарий моделируется целиком: набор классов выбирает pipeline
        // (stageClassesAfterPublish), и удаляются только те артефакты, чей класс
        // в этот набор попал. Прежний вариант теста был дословным дублем теста
        // «демо удаляется при успехе обеих площадок» — признак исхода публикации
        // в нём не моделировался вовсе, поэтому FR-001/FR-005 не проверялись.
        val demo = demoFile()
        val png = tempDir.resolve("done_files/song [sponsr].png")
        Files.createDirectories(png.parent)
        Files.writeString(png, "x".repeat(5))

        // Оба канала провалились.
        val classes = ArtifactCleanupAutomation.stageClassesAfterPublish(tgSuccess = false, vkSuccess = false)
        assertTrue(
            classes.contains(ArtifactCleanupClass.DEMO_MP4),
            "демо-ролик чистится независимо от исхода публикации (FR-001)",
        )
        assertFalse(
            classes.contains(ArtifactCleanupClass.PUBLICATION_PNG),
            "картинки при провале публикации не чистятся — они нужны повторной попытке (FR-005)",
        )

        // Удаляем ровно те артефакты, чей класс попал в набор pipeline.
        val artifactsByClass =
            mapOf(
                ArtifactCleanupClass.DEMO_MP4 to demo,
                ArtifactCleanupClass.PUBLICATION_PNG to png,
            )
        classes.forEach { cls ->
            artifactsByClass[cls]?.let { path ->
                ArtifactCleanupAutomation.deleteAfterStage(
                    classId = cls,
                    path = path.toString(),
                    policy = policy(),
                )
            }
        }

        assertFalse(Files.exists(demo), "демо-ролик должен быть удалён даже при провале публикации")
        assertTrue(Files.exists(png), "картинки при провале публикации должны остаться для повторной попытки")
    }

    @Test
    fun `удаление демо фиксируется в журнале`() {
        val file = demoFile()

        ArtifactCleanupAutomation.deleteAfterStage(
            classId = ArtifactCleanupClass.DEMO_MP4,
            path = file.toString(),
            policy = policy(),
        )

        val journalFile = Files.list(tempDir.resolve("logs")).use { it.toList() }.single()
        val rows = Files.readAllLines(journalFile)
        assertEquals(CleanupJournal.HEADER, rows[0])
        assertEquals("deleted", rows[1].split("\t")[3])
        assertEquals(file.toString(), rows[1].split("\t")[0])
        assertEquals("DEMO_MP4", rows[1].split("\t")[2])
        assertEquals(ArtifactCleanupAutomation.AUTO_APPROVER, rows[1].split("\t")[5])
    }

    @Test
    fun `при выключенной уборке файл остаётся`() {
        // FR-011: cleanupEnabled=false (дефолт) — поведение идентично прежнему
        val file = demoFile()

        val outcome =
            ArtifactCleanupAutomation.deleteAfterStage(
                classId = ArtifactCleanupClass.DEMO_MP4,
                path = file.toString(),
                policy = policy(enabled = false),
            )

        assertTrue(Files.exists(file), "при выключенной уборке демо-ролик остаётся на месте")
        assertEquals(0, outcome.deleted)
        assertEquals(1, outcome.skippedByPolicy)
        assertFalse(Files.exists(tempDir.resolve("logs")), "журнал не пишется, если удаления не было")
    }

    @Test
    fun `сухой прогон ничего не удаляет, но сообщает о путях`() {
        // FR-011: cleanupDryRun=true (дефолт) — ни одного фактического удаления
        val file = demoFile()

        val outcome =
            ArtifactCleanupAutomation.deleteAfterStage(
                classId = ArtifactCleanupClass.DEMO_MP4,
                path = file.toString(),
                policy = policy(dryRun = true),
            )

        assertTrue(Files.exists(file), "сухой прогон не удаляет файлы")
        assertTrue(outcome.dryRun)
        assertEquals(0, outcome.deleted)
        assertEquals(1, outcome.planned)
    }

    @Test
    fun `защищённый класс не удаляется даже при включённой уборке`() {
        // FR-010: flac-стемы неприкосновенны
        val file = tempDir.resolve("hdemucs_mmi/song-vocals.flac")
        Files.createDirectories(file.parent)
        Files.writeString(file, "x")

        val outcome =
            ArtifactCleanupAutomation.deleteAfterStage(
                classId = ArtifactCleanupClass.STEMS_FLAC,
                path = file.toString(),
                policy = policy(),
            )

        assertTrue(Files.exists(file), "flac-стем не должен быть удалён")
        assertEquals(0, outcome.deleted)
    }

    @Test
    fun `недоступный журнал блокирует удаление`() {
        // FR-012/data-model §4: журнал не должен оказаться беднее факта — без журнала не удаляем
        val file = demoFile()
        val notADir = tempDir.resolve("blocker")
        Files.writeString(notADir, "x")

        val outcome =
            ArtifactCleanupAutomation.deleteAfterStage(
                classId = ArtifactCleanupClass.DEMO_MP4,
                path = file.toString(),
                policy = CleanupPolicy(enabled = true, dryRun = false, journalDir = notADir.resolve("logs").toString()),
            )

        assertTrue(Files.exists(file), "без журнала удаление не выполняется")
        assertEquals(1, outcome.skippedByPolicy)
    }

    @Test
    fun `отсутствующий файл не считается ошибкой`() {
        val outcome =
            ArtifactCleanupAutomation.deleteAfterStage(
                classId = ArtifactCleanupClass.DEMO_MP4,
                path = tempDir.resolve("done_files/нет-такого [demo]" + videoExt).toString(),
                policy = policy(),
            )

        assertEquals(0, outcome.planned)
        assertEquals(0, outcome.failed)
    }

    @Test
    fun `картинки удаляются только при успехе обеих площадок`() {
        // FR-005: при неудаче любой из площадок картинки нужны повторной попытке
        assertEquals(
            listOf(ArtifactCleanupClass.DEMO_MP4),
            ArtifactCleanupAutomation.stageClassesAfterPublish(tgSuccess = false, vkSuccess = false),
        )
        assertEquals(
            listOf(ArtifactCleanupClass.DEMO_MP4),
            ArtifactCleanupAutomation.stageClassesAfterPublish(tgSuccess = true, vkSuccess = false),
        )
        assertEquals(
            listOf(ArtifactCleanupClass.DEMO_MP4),
            ArtifactCleanupAutomation.stageClassesAfterPublish(tgSuccess = false, vkSuccess = true),
        )
        assertEquals(
            listOf(ArtifactCleanupClass.DEMO_MP4, ArtifactCleanupClass.PUBLICATION_PNG),
            ArtifactCleanupAutomation.stageClassesAfterPublish(tgSuccess = true, vkSuccess = true),
        )
    }

    @Test
    fun `картинки удаляются и попадают в журнал при двойном успехе`() {
        val png = tempDir.resolve("done_files/song [sponsr].png")
        Files.createDirectories(png.parent)
        Files.writeString(png, "x".repeat(5))

        val outcome =
            ArtifactCleanupAutomation.deleteAfterStage(
                classId = ArtifactCleanupClass.PUBLICATION_PNG,
                paths = listOf(png.toString(), tempDir.resolve("done_files/нет.png").toString()),
                policy = policy(),
            )

        assertEquals(1, outcome.deleted, "удаляется только существующий файл")
        assertEquals(0, outcome.failed)
        assertFalse(Files.exists(png))

        // FR-012: удаление обязано быть зафиксировано в журнале — «журнал не должен
        // оказаться беднее факта». Без этой проверки регрессия «файл удаляется, но
        // запись не пишется» (journal.appendDeleted вызывается без проверки
        // результата) осталась бы незамеченной.
        val journalFile = Files.list(tempDir.resolve("logs")).use { it.toList() }.single()
        val rows = Files.readAllLines(journalFile)
        assertEquals(CleanupJournal.HEADER, rows[0])
        assertEquals(2, rows.size, "в журнале должна быть ровно одна запись об удалении: $rows")
        val columns = rows[1].split("\t")
        assertEquals(png.toString(), columns[0], "в журнале должен быть путь удалённого файла")
        assertEquals("5", columns[1], "в журнале должен быть размер удалённого файла")
        assertEquals("PUBLICATION_PNG", columns[2], "в журнале должен быть класс артефакта")
        assertEquals("deleted", columns[3], "в журнале должен быть результат удаления")
        assertEquals(ArtifactCleanupAutomation.AUTO_APPROVER, columns[5], "в журнале должен быть утвердивший")
    }
}
