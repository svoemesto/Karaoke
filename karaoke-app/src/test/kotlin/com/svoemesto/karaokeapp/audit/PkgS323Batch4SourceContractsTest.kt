package com.svoemesto.karaokeapp.audit

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Регрессия пакета `PKG-S3-23` (аудит #336, OpenProject #395), партия 4 —
 * BR-167, BR-168, BR-169, BR-170, BR-171, BR-172, BR-173.
 *
 * Класс компилируется и на коде «до» правки, поэтому служит доказательством «до/после»:
 * на старом коде утверждения падают, на текущем — проходят.
 *
 * @see specs/395-pkg-s3-23/report.md
 */
class PkgS323Batch4SourceContractsTest {
    private fun repoFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        val file = File(dir, relativePath)
        assertTrue(file.isFile, "файл не найден: $relativePath")
        return file
    }

    private fun read(relativePath: String): String = repoFile(relativePath).readText()

    /**
     * Исходник БЕЗ комментариев — иначе утверждение вида «в файле нет `X`» ловило бы
     * само себя в тексте пояснения к правке.
     */
    private fun readCode(relativePath: String): String =
        read(relativePath)
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")

    private val appMain = "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp"

    // ---------------------------------------------------------------- BR-167

    @Test
    fun `BR-167 сбой на одном подписчике не обрывает батч Sponsr`() {
        val source = readCode("$appMain/services/SponsrSyncService.kt")
        assertTrue(
            source.contains("failed.add(reason)"),
            "неудача по пользователю должна попадать в результат (BR-167)",
        )
        assertTrue(
            source.contains("catch (e: Exception)"),
            "обработка одного пользователя должна быть изолирована (BR-167)",
        )
        assertTrue(source.contains("infra.sponsr.sync"))
        assertTrue(
            source.contains("try {\n                    user.sponsrPremiumUntil = until"),
            "save/send должны быть ВНУТРИ try — иначе сбой обрывал весь цикл (BR-167)",
        )
    }

    // ---------------------------------------------------------------- BR-168

    @Test
    fun `BR-168 изменение процесса и его audit пишутся одной транзакцией`() {
        val source = readCode("$appMain/services/KaraokeProcessAdminService.kt")
        assertTrue(
            source.contains("private fun <T> inTransaction("),
            "нужен хелпер транзакции (BR-168)",
        )
        assertTrue(
            source.contains("connection.autoCommit = false"),
            "транзакция должна выключать autocommit (BR-168)",
        )
        assertTrue(
            source.contains("connection.rollback()"),
            "при сбое нужна откат (BR-168)",
        )
        assertTrue(
            source.contains("connection.commit()"),
            "при успехе нужен commit (BR-168)",
        )
        assertTrue(
            source.contains("connection.autoCommit = previousAutoCommit"),
            "режим autocommit возвращается — соединение кэшируется в потоке (BR-168)",
        )
        // UPDATE и insertAudit должны быть внутри одного вызова inTransaction.
        val txBlocks = Regex("inTransaction\\(connection\\) \\{").findAll(source).count()
        assertTrue(
            txBlocks >= 3,
            "edit/delete/bulk должны быть в транзакции (BR-168), найдено блоков: $txBlocks",
        )
    }

    // ---------------------------------------------------------------- BR-169

    @Test
    fun `BR-169 прерванный обход попадает в отчёт и запрещает apply`() {
        val planner = readCode("$appMain/services/cleanup/ArtifactCleanupPlanner.kt")
        assertTrue(
            planner.contains("WALK_INTERRUPTED_MARKER"),
            "нужен маркер предупреждения о прерванном обходе (BR-169)",
        )
        assertTrue(
            planner.contains("warnings.add(warning)"),
            "факт прерывания должен попасть в warnings отчёта (BR-169)",
        )
        assertFalse(
            planner.contains("println(\"[cleanup] план"),
            "сбой обхода не должен уходить только в stdout (BR-169)",
        )

        val service = readCode("$appMain/services/cleanup/ArtifactCleanupService.kt")
        assertTrue(
            service.contains("incomplete_plan"),
            "apply должен отклонять неполный отчёт (BR-169)",
        )
        assertTrue(
            service.contains("WALK_INTERRUPTED_MARKER"),
            "apply должен проверять маркер прерванного обхода (BR-169)",
        )
    }

    // ---------------------------------------------------------------- BR-170

    @Test
    fun `BR-170 незаписанный след удаления прерывает прогон уборки`() {
        val source = readCode("$appMain/services/cleanup/ArtifactCleanupService.kt")
        assertTrue(
            source.contains("journalBroken"),
            "нужен флаг «журнал не записался» (BR-170)",
        )
        assertTrue(
            source.contains("journalBroken -> CleanupRunState.FAILED"),
            "прогон без следа в журнале — FAILED, а не DONE (BR-170)",
        )
        assertFalse(
            source.contains("journal.appendDeleted(entry.path, size, classId.name, approvedBy, approved.runId)\n            } else {"),
            "результат appendDeleted больше не игнорируется (BR-170)",
        )
    }

    // ---------------------------------------------------------------- BR-171

    @Test
    fun `BR-171 идентификатор прогона уникален`() {
        val source = readCode("$appMain/services/cleanup/ArtifactCleanupService.kt")
        assertTrue(
            source.contains("UUID.randomUUID()"),
            "runId должен быть уникален на прогон (BR-171)",
        )
        assertFalse(
            source.contains("val runId = CleanupJournal.newRunId(classId.name)\n"),
            "прежний runId совпадал у всех прогонов одного класса за день (BR-171)",
        )
    }

    // ---------------------------------------------------------------- BR-172

    @Test
    fun `BR-172 действие со словарём выбирается явно, а не через reflection`() {
        val source = readCode("$appMain/textfiledictionary/TextFileDictionary.kt")
        assertFalse(
            source.contains("declaredMethods.firstOrNull { it.name == dictAction }"),
            "reflection по имени метода позволял вызвать любой метод с чужой арностью (BR-172)",
        )
        assertTrue(
            source.contains("ALLOWED_ACTIONS"),
            "должен быть фиксированный набор разрешённых операций (BR-172)",
        )
        assertTrue(
            source.contains("\"add\" -> tfdInstance.add(") && source.contains("\"remove\" -> tfdInstance.remove("),
            "операции должны вызываться явно (BR-172)",
        )
    }

    // ---------------------------------------------------------------- BR-173

    @Test
    fun `BR-173 мёртвый блок app убран из конфигурации karaoke-app`() {
        val yml = read("karaoke-app/src/main/resources/application.yml")
        assertFalse(
            yml.contains("app:\n  max-file-size:") && yml.contains("max-request-size: 100MB"),
            "мёртвый блок app.max-file-size/max-request-size должен быть удалён (BR-173)",
        )
        assertTrue(
            yml.contains("spring.servlet.multipart") || yml.contains("max-request-size: 110MB"),
            "настоящий лимит задаёт spring.servlet.multipart — он должен остаться (BR-173)",
        )
    }
}
