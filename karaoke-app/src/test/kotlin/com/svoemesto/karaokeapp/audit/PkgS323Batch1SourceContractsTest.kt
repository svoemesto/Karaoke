package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.model.News
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.sql.SQLException

/**
 * Регрессия пакета `PKG-S3-23` (аудит #336, OpenProject #395), партия 1 —
 * КОНТРАКТНЫЕ проверки по исходникам.
 *
 * Класс компилируется и на коде «до» правки, поэтому служит настоящим доказательством
 * «до/после»: на старом коде каждое утверждение падает, на текущем — проходит.
 *
 * @see specs/395-pkg-s3-23/report.md
 */
class PkgS323Batch1SourceContractsTest {
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

    @Test
    fun `BR-117 в UtilsPictures больше нет println в catch записи картинки`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPictures.kt")
        assertTrue(
            source.contains("writePictureOrThrow("),
            "createSongPicture должен идти через writePictureOrThrow",
        )
        assertTrue(
            source.contains("class PictureWriteException"),
            "должен быть тип исключения PictureWriteException",
        )
    }

    // ---------------------------------------------------------------- BR-118

    @Test
    fun `BR-118 в дайджесте share-ссылок не осталось printStackTrace`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ShareLinksAdminController.kt")
        assertFalse(
            source.contains("printStackTrace"),
            "SQL-ошибки дайджеста должны идти через SLF4J, а не в stdout",
        )
        assertTrue(source.contains("infra.admin.sharelinks"))
    }

    // ---------------------------------------------------------------- BR-119

    @Test
    fun `BR-119 сбой БД в списке новостей не выглядит как пустой список`() {
        val db = FakeKaraokeConnection(jdbcConnection = null)
        // ДО: `database.getConnection() ?: return 0L` — клиент получал 200 с total=0
        // при живой базе, т.е. сбой БД выглядел как «новостей нет».
        val error =
            assertThrows<SQLException> {
                News.countAll(db)
            }
        assertTrue(
            error.message.orEmpty().contains("news.count.unavailable"),
            "сбой БД должен быть различим, а не выглядеть как «новостей нет»: ${error.message}",
        )
    }

    @Test
    fun `BR-119 в контроллере новостей пагинация клампится`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/NewsController.kt")
        assertTrue(source.contains("page.coerceAtLeast(0)"), "page должен быть неотрицательным")
        assertTrue(source.contains("pageSize.coerceIn(1, MAX_PAGE_SIZE)"), "pageSize должен быть ограничен сверху")
        assertTrue(source.contains("MAX_PAGE_SIZE = 200"))
    }

    @Test
    fun `BR-119 в News больше нет println про SQLException`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/News.kt")
        assertFalse(
            source.contains("News.loadAll SQLException") || source.contains("News.countAll SQLException"),
            "SQL-ошибки списка новостей не должны глотаться",
        )
    }

    // ---------------------------------------------------------------- BR-120

    @Test
    fun `BR-121 поиск текста запускает Chromium ОДИН раз`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/YandexLyricsSearchOutcome.kt")
        val launches = Regex("launchPersistentContext").findAll(source).count()
        assertEquals(
            1,
            launches,
            "BR-121: поиск текста должен запускать Chromium один раз, а не два (прогрев + поиск)",
        )
        assertTrue(
            source.contains("YANDEX_PROFILE_LOCK.withLock"),
            "доступ к persistent-профилю должен быть сериализирован",
        )
    }

    @Test
    fun `BR-121 профиль Яндекс Музыки сериализирован общим локом`() {
        val lock = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPlaywright.kt")
        assertTrue(lock.contains("YANDEX_PROFILE_LOCK"), "лог профиля должен быть объявлен рядом с USER_DATA_DIR")
        val album = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/AlbumCoverFinder.kt")
        assertTrue(
            album.contains("YANDEX_PROFILE_LOCK.withLock"),
            "автоматический поиск обложек тоже должен идти под локом профиля",
        )
    }

    // ---------------------------------------------------------------- BR-122

    @Test
    fun `BR-122 ffmpeg запускается с таймаутом и без вечного readBytes`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/WaveformCompare.kt")
        assertTrue(
            source.contains("waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS)"),
            "ffmpeg должен ждать с таймаутом, а не бесконечно",
        )
        assertTrue(source.contains("destroyForcibly"), "при таймауте процесс надо принудительно убить")
        assertFalse(
            source.contains("process.inputStream.readBytes()"),
            "чтение stdout в основном потоке блокировалось навсегда (BR-122)",
        )
        assertTrue(source.contains("procLog.delete()"), "временный файл лога ffmpeg тоже удаляется")
    }

    // ---------------------------------------------------------------- BR-129

    @Test
    fun `BR-129 очистка черновика закрывает statement и логирует ошибку`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongAssignmentDraft.kt")
        assertTrue(
            source.contains("prepareStatement(\"DELETE FROM \$TABLE_NAME WHERE assignment_id = ?\").use {"),
            "statement должен закрываться через use { } на любой ветке",
        )
        assertFalse(
            source.contains("println(\"[SongAssignmentDraft.deleteByAssignment]"),
            "ошибка должна идти через SLF4J с категорией, а не в stdout",
        )
        assertTrue(source.contains("infra.editor.draft"))
    }

    @Test
    fun `BR-129 вызывающий не удаляет назначение, если черновик не очищен`() {
        val admin = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt")
        assertTrue(
            admin.contains("draft_cleanup_failed"),
            "при неудачной очистке черновика назначение не должно удаляться",
        )
        val public = readCode("karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSongEditorController.kt")
        assertTrue(
            public.contains("draft_cleanup_failed"),
            "public-контроллер тоже не должен удалять назначение при неудачной очистке",
        )
    }

    // ---------------------------------------------------------------- BR-130

    @Test
    fun `BR-130 пустой голос не роняет раскладку`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongVoice.kt")
        assertTrue(
            source.contains("if (getLines().isEmpty())"),
            "actuateChilds должен выходить рано при пустом списке строк",
        )
        assertFalse(source.contains("getLines().maxOf {"), "maxOf на пустом списке бросает исключение")
        assertFalse(source.contains("getLines().last()"), "last() на пустом списке бросает исключение")
        assertTrue(source.contains("maxByOrNull"), "maxBy на пустом списке бросает исключение")
    }

    // ---------------------------------------------------------------- BR-131

    @Test
    fun `BR-131 неудача переименования артефакта попадает в результат`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongArtifactRenamer.kt")
        assertTrue(source.contains("data class RenameFailure"), "нужен тип неудачи RenameFailure")
        assertTrue(source.contains("failures.add(RenameFailure("), "неудача renameTo=false должна собираться")
        assertTrue(source.contains("infra.catalog.rename"))
        val mapper = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongUpdateMapper.kt")
        assertTrue(
            mapper.contains("renameFailures"),
            "SongUpdateMapper должен получать неудачи переименования и сообщать о них",
        )
    }

    // ---------------------------------------------------------------- BR-134

    @Test
    fun `BR-134 проверка мониторинга не подменяет ошибку нулём`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/StemJobsStuckCheck.kt")
        assertFalse(
            source.contains("println(\"StemJobsStuckCheck:"),
            "ошибка не должна глотаться с подменой на 0",
        )
        assertTrue(source.contains("throw e"), "исключение должно доходить до тика (WARNING check.<Name>.failure)")
        assertTrue(source.contains("infra.monitor.check"))
    }

    // ---------------------------------------------------------------- BR-135

    @Test
    fun `BR-135 тик мониторинга не пересекается с самим собой`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/MonitoringService.kt")
        assertTrue(
            source.contains("fixedDelay = 60_000L"),
            "нужен fixedDelay вместо fixedRate (BR-135)",
        )
        assertFalse(source.contains("fixedRate = 60_000L"), "fixedRate запускает тики параллельно")
        assertTrue(source.contains("tickRunning.compareAndSet(false, true)"), "нужен single-flight guard")
        assertTrue(source.contains("предыдущий тик ещё выполняется"))
    }

    // ---------------------------------------------------------------- BR-136

    @Test
    fun `BR-136 реестр admin-задач ограничен по TTL, пул закрывается`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/AdminTaskService.kt")
        assertTrue(source.contains("TASK_TTL_MS"), "нужен TTL для завершённых задач")
        assertTrue(source.contains("purgeExpiredTasks"), "нужна очистка истёкших задач")
        assertTrue(source.contains("@PreDestroy"), "пул потоков должен закрываться при остановке приложения")
        assertTrue(source.contains("executor.shutdown()"))
    }

    // ---------------------------------------------------------------- BR-137

    @Test
    fun `BR-137 клиент forced-alignment переиспользуется, ошибка логируется`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/AlignmentServiceClient.kt")
        assertTrue(
            source.contains("cachedClient"),
            "OkHttpClient должен кэшироваться, а не создаваться на каждый вызов",
        )
        assertTrue(
            source.contains("val client = httpClient(timeoutMs)"),
            "align() должен брать кэшированный клиент, а не создавать новый",
        )
        assertTrue(source.contains("infra.alignment"))
        assertFalse(
            source.contains("catch (_: Exception) {\n            null"),
            "исключение не должно глотаться молча",
        )
    }
}
