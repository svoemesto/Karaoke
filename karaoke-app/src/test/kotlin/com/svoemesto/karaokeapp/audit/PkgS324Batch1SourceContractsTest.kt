package com.svoemesto.karaokeapp.audit

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Регрессия пакета `PKG-S3-24` (аудит #336, OpenProject #396), партия 1 —
 * BR-174, BR-177, BR-178, BR-179, BR-180, BR-181, BR-183, BR-184, BR-186, BR-187.
 *
 * Класс компилируется и на коде «до» правки, поэтому служит доказательством «до/после»:
 * на старом коде утверждения падают, на текущем — проходят.
 *
 * @see specs/396-pkg-s3-24/report.md
 */
class PkgS324Batch1SourceContractsTest {
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
     *
     * Блочные комментарии снимаются только с начала строки: иначе маркер блочного
     * комментария внутри строкового литерала («*.txt» в строке) «съедал» бы кусок
     * кода до следующего закрывающего маркера.
     */
    private fun readCode(relativePath: String): String =
        read(relativePath)
            .replace(Regex("""(?m)^[ \t]*/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")

    private val appMain = "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp"

    // ---------------------------------------------------------------- BR-174

    @Test
    fun `BR-174 отчёт чистки строится вне HTTP-потока`() {
        val service = readCode("$appMain/services/cleanup/ArtifactCleanupService.kt")
        val controller = readCode("$appMain/controllers/CleanupController.kt")

        assertTrue(
            service.contains("fun planAsync("),
            "нужен асинхронный построитель отчёта (BR-174)",
        )
        assertTrue(
            service.contains("executor.submit {"),
            "обход каталогов должен идти в executor, а не в поток HTTP-запроса (BR-174)",
        )
        assertTrue(
            controller.contains("accepted()"),
            "HTTP-ответ на plan — 202 (BR-174)",
        )
        assertTrue(
            controller.contains("CleanupPlanState.READY"),
            "нужен опрос состояния отчёта (BR-174)",
        )
        // Сверка состава (полный обход) — тоже вне HTTP-потока.
        val runBody = service.substringAfter("private fun run(")
        assertTrue(
            runBody.contains("planner.plan(classId)"),
            "пересчёт состава должен происходить в фоновом прогоне (BR-174)",
        )
        val applyBody = service.substringAfter("fun apply(").substringBefore("private fun run(")
        assertFalse(
            applyBody.contains("planner.plan(classId)"),
            "в HTTP-потоке apply не должен делать полный обход каталогов (BR-174)",
        )
    }

    @Test
    fun `BR-174 размер файла в отчёте считается один раз`() {
        val planner = readCode("$appMain/services/cleanup/ArtifactCleanupPlanner.kt")
        assertTrue(
            planner.contains("fun matchSize("),
            "подбор класса должен возвращать размер (BR-174)",
        )
        assertFalse(
            Regex("""sizeBytes = sizeOf\(path\)""").containsMatchIn(planner),
            "второй sizeOf на файл убран (BR-174)",
        )
    }

    // ---------------------------------------------------------------- BR-177

    @Test
    fun `BR-177 курсор новостей согласован с сортировкой`() {
        val news = readCode("$appMain/model/News.kt")
        assertTrue(
            news.contains("ORDER BY id ASC"),
            "порядок выдачи /since должен совпадать с курсором по id (BR-177)",
        )
        assertFalse(
            news.contains("ORDER BY publish_at DESC\$limitClause"),
            "сортировка по publish_at при курсоре по id теряла новости (BR-177)",
        )
    }

    // ---------------------------------------------------------------- BR-178

    @Test
    fun `BR-178 сбой чтения новостей не выглядит как пустая лента`() {
        val news = readCode("$appMain/model/News.kt")
        assertFalse(
            news.contains("println("),
            "println в модели новостей заменён на SLF4J (BR-178)",
        )
        assertTrue(
            news.contains("newsLog.error("),
            "ошибка чтения логируется через SLF4J с объектом исключения (BR-178)",
        )
        listOf(
            "loadPublished",
            "countPublished",
            "loadPublishedSince",
            "existsAnnouncement",
        ).forEach { method ->
            val body = news.substringAfter("fun $method(")
            assertTrue(
                body.contains("throw e"),
                "$method должен пробрасывать SQLException вместо «пустого успеха» (BR-178)",
            )
        }
        assertTrue(
            news.contains("news.list.unavailable"),
            "нет соединения — тоже ошибка (BR-178)",
        )
    }

    // ---------------------------------------------------------------- BR-179

    @Test
    fun `BR-179 путь к картинке проверяется явно`() {
        val picture = readCode("$appMain/model/Picture.kt")
        assertTrue(
            picture.contains("fun readImageOrThrow("),
            "нужен явный разбор пути к картинке (BR-179)",
        )
        assertTrue(
            picture.contains("picture.image_path_blank"),
            "пустой путь — понятная ошибка (BR-179)",
        )
        assertTrue(
            picture.contains("picture.image_file_missing"),
            "отсутствующий файл — понятная ошибка (BR-179)",
        )
        assertTrue(
            picture.contains("picture.image_unreadable"),
            "нечитаемый формат — понятная ошибка (BR-179)",
        )
        assertFalse(
            picture.contains("resizeBufferedImage(ImageIO.read(File(params.pathToFile))"),
            "чтение без проверок больше не используется (BR-179)",
        )
    }

    // ---------------------------------------------------------------- BR-180

    @Test
    fun `BR-180 перебор разбиений заменён динамикой`() {
        val picture = readCode("$appMain/model/Picture.kt")
        assertFalse(
            picture.contains("getVariantsOfLines"),
            "экспоненциальный перебор разбиений удалён (BR-180)",
        )
        assertTrue(
            picture.contains("fun selectBestSplit("),
            "нужен выбор разбиения динамикой по позициям разрыва (BR-180)",
        )
        assertTrue(
            picture.contains("const val MAX_SPLIT_WORDS"),
            "нужен предел числа слов (BR-180)",
        )
        assertTrue(
            picture.contains("const val MAX_SPLIT_LINES"),
            "нужен предел числа строк (BR-180)",
        )
        // Graphics2D освобождается в multiLines() — был утечкой нативного ресурса.
        assertTrue(
            picture.contains("graphics2D.dispose()"),
            "Graphics2D должен освобождаться (BR-180)",
        )
    }

    // ---------------------------------------------------------------- BR-181

    @Test
    fun `BR-181 проигравший гонку за картинкой перечитывает запись по имени`() {
        val pictures = readCode("$appMain/model/Pictures.kt")
        val body = pictures.substringAfter("fun createNewPicture(").substringBefore("fun getPictureById(")
        val reads = Regex("""getPictureByName\(""").findAll(body).count()
        assertTrue(
            reads >= 2,
            "повторное чтение по имени должно быть и в ветке catch, и после null (BR-181)",
        )
        assertTrue(
            body.contains("picturesLog.error("),
            "«не создано» должно попадать в журнал, а не быть тихим null (BR-181)",
        )
    }

    @Test
    fun `BR-181 createDbInstance различает гонку и ошибку`() {
        val table = readCode("$appMain/model/KaraokeDbTable.kt")
        assertTrue(
            table.contains("UNIQUE_VIOLATION_SQL_STATE"),
            "SQLState нарушения уникальности должен быть известен (BR-181)",
        )
        assertTrue(
            table.contains("sqlState"),
            "SQLState попадает в журнал (BR-181)",
        )
    }

    // ---------------------------------------------------------------- BR-183

    @Test
    fun `BR-183 XML экранируется в одном месте`() {
        val mltNode = readCode("$appMain/model/MltNode.kt")
        assertTrue(
            mltNode.contains("escapeXml()"),
            "значения атрибутов экранируются при сериализации (BR-183)",
        )
        assertTrue(
            mltNode.contains("bodyToXml("),
            "текстовое содержимое экранируется при сериализации (BR-183)",
        )
        val extensions = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt")
        assertTrue(
            extensions.contains("fun String.escapeXml():"),
            "нужен единый экранировщик XML (BR-183)",
        )
        assertFalse(
            extensions.contains("""fun String.xmldata(): String = "<?xml version=\"1.0\"?>$this".replace("<", "&lt;")"""),
            "ручное экранирование в xmldata убрано — экранирование одно (BR-183)",
        )
    }

    @Test
    fun `BR-183 ручные замены амперсанда убраны из вызывающих`() {
        listOf(
            "$appMain/mlt/mko/MkoScroller.kt",
            "$appMain/mlt/mko/MkoSplashStart.kt",
            "$appMain/mlt/mko/MkoSongText.kt",
            "$appMain/model/SongVoiceLineElement.kt",
            "$appMain/model/SongVoiceLineElementSyllable.kt",
        ).forEach { path ->
            assertFalse(
                readCode(path).contains("""replace("&", "&amp"""),
                "ручная подмена амперсанда осталась в $path (BR-183)",
            )
        }
        val song = readCode("$appMain/model/Song.kt")
        assertFalse(
            song.contains("""setSongName(songName.replace("&", "&amp"""),
            "значения MltProp больше не экранируются вручную (BR-183)",
        )
    }

    // ---------------------------------------------------------------- BR-184

    @Test
    fun `BR-184 сбой чтения отличается от отсутствия данных`() {
        val table = readCode("$appMain/model/KaraokeDbTable.kt")
        assertTrue(
            table.contains("throwOnError: Boolean = false"),
            "нужен строгий режим загрузки (BR-184)",
        )
        assertTrue(
            table.contains("db.unavailable"),
            "нет соединения — ошибка, а не пустой список (BR-184)",
        )
        val author = readCode("$appMain/model/Author.kt")
        assertTrue(
            author.contains("throwOnError: Boolean = false"),
            "getAuthorByName должен уметь различать сбой чтения (BR-184)",
        )
        val album = readCode("$appMain/model/Album.kt")
        assertTrue(
            album.contains("throwOnError = true"),
            "create-or-get читает автора строго (BR-184)",
        )
        assertTrue(
            album.contains("log.error("),
            "сбой чтения логируется (BR-184)",
        )
        val utils = readCode("$appMain/Utils.kt")
        assertTrue(
            utils.contains("throwOnError = true"),
            "create-or-get в Utils читает автора строго (BR-184)",
        )
    }

    // ---------------------------------------------------------------- BR-186

    @Test
    fun `BR-186 картинки авторов грузятся одним запросом`() {
        val author = readCode("$appMain/model/Author.kt")
        assertTrue(
            author.contains("fun toDTOsWithPictures("),
            "нужен пакетный построитель DTO (BR-186)",
        )
        assertTrue(
            author.contains("Pictures.getPicturesByNames("),
            "картинки читаются одним запросом (BR-186)",
        )
        val controller = readCode("$appMain/controllers/ApiController.kt")
        assertTrue(
            controller.contains("Author.toDTOsWithPictures("),
            "дайджест авторов использует пакетную загрузку (BR-186)",
        )
        assertFalse(
            controller.contains(
                """ignoreUseInList = true,
                ).map { it.toDTO() }""",
            ),
            "прежний N+1 (toDTO на каждого автора) убран (BR-186)",
        )
    }

    // ---------------------------------------------------------------- BR-187

    @Test
    fun `BR-187 базовый слой БД логирует через SLF4J`() {
        val table = readCode("$appMain/model/KaraokeDbTable.kt")
        assertFalse(
            table.contains("printStackTrace()"),
            "printStackTrace заменён на SLF4J (BR-187)",
        )
        assertFalse(
            table.contains("println("),
            "println заменён на SLF4J (BR-187)",
        )
        assertTrue(
            table.contains("log.warn("),
            "ошибки чтения/закрытия ресурсов логируются (BR-187)",
        )
        assertTrue(
            table.contains("log.error("),
            "недоступность БД логируется как ошибка (BR-187)",
        )
    }
}
