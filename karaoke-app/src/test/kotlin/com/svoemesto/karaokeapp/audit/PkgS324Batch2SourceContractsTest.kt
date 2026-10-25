package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.model.Publication
import com.svoemesto.karaokeapp.model.SiteChatMessage
import com.svoemesto.karaokeapp.model.StatsByEvents
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Регрессия пакета `PKG-S3-24` (аудит #336, OpenProject #396), партия 2 —
 * BR-189, BR-190, BR-191, BR-192, BR-193, BR-194, BR-196, BR-197, BR-198, BR-199.
 *
 * Класс компилируется и на коде «до» правки: новые API проверяются через рефлексию
 * (иначе тест не собрался бы на старом коде), поэтому «до» — это падение, а не
 * ошибка компиляции.
 *
 * @see specs/396-pkg-s3-24/report.md
 */
class PkgS324Batch2SourceContractsTest {
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
     * комментария внутри строкового литерала «съедал» бы кусок кода до следующего
     * закрывающего маркера.
     */
    private fun readCode(relativePath: String): String =
        read(relativePath)
            .replace(Regex("""(?m)^[ \t]*/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")

    private val appMain = "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp"

    // ---------------------------------------------------------------- BR-189

    @Test
    fun `BR-189 конфликт публикаций не затирается молча`() {
        val publication = readCode("$appMain/model/Publication.kt")
        assertTrue(
            publication.contains("конфликт публикаций"),
            "конфликт двух песен на один слот должен попадать в журнал (BR-189)",
        )
        assertTrue(
            publication.contains("вне сетки"),
            "время вне сетки 10:00–23:00 не должно теряться молча (BR-189)",
        )
        assertFalse(
            publication.contains("-> publicationInList.publish10 = song"),
            "прямое присваивание в слот без проверки конфликта убрано (BR-189)",
        )
        assertTrue(
            publication.contains("Publication::publish10"),
            "слот выбирается через свойство — конфликт можно проверить до записи (BR-189)",
        )
    }

    // ---------------------------------------------------------------- BR-190

    @Test
    fun `BR-190 ключ фильтра даты совпадает с контроллерами`() {
        val publication = readCode("$appMain/model/Publication.kt")
        assertTrue(
            publication.contains("""args["filter_date_from"] ?: args["publish_date_from"]"""),
            "модель должна читать оба ключа даты «с» (BR-190)",
        )
        assertTrue(
            publication.contains("""args["filter_date_to"] ?: args["publish_date_to"]"""),
            "модель должна читать оба ключа даты «по» (BR-190)",
        )
    }

    // ---------------------------------------------------------------- BR-191

    @Test
    fun `BR-191 дата публикации разбирается безопасно`() {
        val publication = readCode("$appMain/model/Publication.kt")
        assertTrue(
            publication.contains("fun parsePublishDate("),
            "нужен безопасный разбор даты публикации (BR-191)",
        )
        assertTrue(
            publication.contains("fun publicationIdFromDate("),
            "идентификатор строки — через безопасный разбор даты (BR-191)",
        )
        assertTrue(
            publication.contains("fun validatePublishDateTime("),
            "на записи формат должен проверяться (BR-191)",
        )
        assertFalse(
            publication.contains("""SimpleDateFormat("dd.MM.yy").parse(it.date)"""),
            "небезопасный разбор даты в фильтре убран (BR-191)",
        )
        assertFalse(
            publication.contains("""song.date.split(".")[2]"""),
            "идентификатор без проверки длины больше не вычисляется (BR-191)",
        )
        val controller = readCode("$appMain/controllers/MainController.kt")
        assertTrue(
            controller.contains("Publication.validatePublishDateTime("),
            "сохранение песни должно проверять дату/время публикации (BR-191)",
        )
    }

    @Test
    fun `BR-191 неверная дата не разбирается`() {
        // Внутренние функции Kotlin получают mangled-имя в JVM (`parsePublishDate$<module>`),
        // поэтому ищем по префиксу.
        val method =
            Publication.Companion::class.java.methods.firstOrNull { it.name.startsWith("parsePublishDate") }
        assertNotNull(method, "BR-191: нужен публичный разбор даты публикации")
        assertNull(method!!.invoke(Publication.Companion, "31.02.26"), "несуществующая дата не должна разбираться")
        assertNull(method.invoke(Publication.Companion, "2025-10-23"), "формат не dd.MM.yy не должен разбираться")
        assertNull(method.invoke(Publication.Companion, ""), "пустая дата не должна разбираться")
        assertNotNull(method.invoke(Publication.Companion, "23.10.25"), "корректная дата должна разбираться")
    }

    // ---------------------------------------------------------------- BR-192

    @Test
    fun `BR-192 результаты поиска удаляются одним запросом`() {
        listOf("SearchResult.kt", "SearchAsync.kt").forEach { name ->
            val source = readCode("$appMain/model/$name")
            assertTrue(
                source.contains("KaraokeDbTable.deleteIn("),
                "$name: удаление должно быть батчевым (BR-192)",
            )
            assertFalse(
                source.contains(".forEach { delete(it.id, database) }"),
                "$name: N+1 DELETE убран (BR-192)",
            )
        }
    }

    // ---------------------------------------------------------------- BR-193

    @Test
    fun `BR-193 сбой чата не выглядит как пустой чат`() {
        val chat = readCode("$appMain/model/SiteChatMessage.kt")
        assertFalse(
            chat.contains("println("),
            "println в модели чата заменён на SLF4J (BR-193)",
        )
        assertTrue(
            chat.contains("chatLog.warn("),
            "ошибка чтения логируется через SLF4J с объектом исключения (BR-193)",
        )
        assertTrue(
            chat.contains("const val COUNT_ERROR = -1"),
            "счётчики должны отличать сбой БД от «сообщений нет» (BR-193)",
        )
        assertTrue(
            chat.contains("throw e"),
            "чтение истории/тредов пробрасывает сбой (BR-193)",
        )
        listOf(
            "$appMain/controllers/ChatController.kt",
            "karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicChatController.kt",
        ).forEach { path ->
            val controller = readCode(path)
            assertTrue(
                controller.contains("SERVICE_UNAVAILABLE"),
                "$path: сбой БД должен отдавать 5xx, а не пустой ответ (BR-193)",
            )
            assertTrue(
                controller.contains("SiteChatMessage.COUNT_ERROR"),
                "$path: контроллер должен различать сбой счётчика (BR-193)",
            )
        }
    }

    @Test
    fun `BR-193 счётчик чата помечает ошибку отдельным значением`() {
        val field = SiteChatMessage::class.java.declaredFields.firstOrNull { it.name == "COUNT_ERROR" }
        assertNotNull(field, "BR-193: нужен признак ошибки счётчика")
        assertEquals(-1, field!!.get(null))
    }

    // ---------------------------------------------------------------- BR-194

    @Test
    fun `BR-194 флаг приветствия ставится только после успеха`() {
        val siteUser = readCode("$appMain/model/SiteUser.kt")
        val body = siteUser.substringAfter("fun sendWelcomePremiumMessageIfNeeded(").substringBefore("var maxFavorites")
        val createAt = body.indexOf("SiteChatMessage.createNew(")
        val checkAt = body.indexOf("if (created == null)")
        val flagAt = body.indexOf("welcomeMessageSent = true")
        val saveAt = body.indexOf("save()")
        assertTrue(createAt >= 0 && checkAt >= 0 && flagAt >= 0 && saveAt >= 0, "BR-194: ожидаются все шаги")
        assertTrue(createAt < checkAt, "сначала создание сообщения, потом проверка результата (BR-194)")
        assertTrue(checkAt < flagAt, "флаг ставится только после успешного создания (BR-194)")
        assertTrue(flagAt < saveAt, "флаг сохраняется после его установки (BR-194)")
    }

    // ---------------------------------------------------------------- BR-196

    @Test
    fun `BR-196 пагинация статистики нормализована`() {
        val stat = readCode("$appMain/model/StatBySong.kt")
        assertTrue(
            stat.contains("fun normalizeStatsPaging("),
            "нужен единый helper нормализации пагинации (BR-196)",
        )
        assertFalse(
            stat.contains("limit \$limit offset \$offset"),
            "неограниченные limit/offset убраны (BR-196)",
        )
        val method = StatsByEvents::class.java.methods.firstOrNull { it.name.startsWith("normalizeStatsPaging") }
        assertNotNull(method, "BR-196: helper должен быть доступен")
        @Suppress("UNCHECKED_CAST")
        val normalized = method!!.invoke(StatsByEvents, 2_000_000_000, -5) as Pair<Int, Int>
        assertEquals(1000, normalized.first, "limit ограничен сверху (BR-196)")
        assertEquals(0, normalized.second, "отрицательный offset не допускается (BR-196)")
    }

    // ---------------------------------------------------------------- BR-197

    @Test
    fun `BR-197 uuid читаются пакетно`() {
        val uuids = readCode("$appMain/model/Uuids.kt")
        assertTrue(
            uuids.contains("fun loadAll("),
            "нужна пакетная загрузка пар (id, uuid) (BR-197)",
        )
        assertTrue(
            uuids.contains("uuidCache"),
            "нужен кеш пар (id, uuid) (BR-197)",
        )
        val top = readCode("$appMain/Uuids.kt")
        assertTrue(
            top.contains("Uuids.cachedUuid("),
            "getStoredUuid должен идти через кеш, а не по одному SELECT (BR-197)",
        )
        assertTrue(
            top.contains("Uuids.loadAll("),
            "кеш заполняется одним запросом (BR-197)",
        )
    }

    // ---------------------------------------------------------------- BR-198

    @Test
    fun `BR-198 лимит очереди заданий атомарен`() {
        val stemJob = readCode("$appMain/model/StemJob.kt")
        assertTrue(
            stemJob.contains("fun createIfUnderLimit("),
            "нужна атомарная проверка лимита вместе с вставкой (BR-198)",
        )
        assertTrue(
            stemJob.contains("pg_advisory_xact_lock("),
            "параллельные запросы должны сериализоваться блокировкой (BR-198)",
        )
        assertTrue(
            stemJob.contains("const val COUNT_ERROR = -1"),
            "сбой подсчёта не подменяется нулём (BR-198)",
        )
        val controller = readCode("karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicStemJobController.kt")
        assertTrue(
            controller.contains("StemJob.createIfUnderLimit("),
            "контроллер должен использовать атомарное создание (BR-198)",
        )
        assertFalse(
            controller.contains("StemJob.countActiveByUser(user.id, db)"),
            "check-then-act в контроллере убран (BR-198)",
        )
    }

    // ---------------------------------------------------------------- BR-199

    @Test
    fun `BR-199 update uuid синтаксически валиден`() {
        val uuids = readCode("$appMain/model/Uuids.kt")
        assertTrue(
            uuids.contains("UPDATE tbl_uuids SET uuid = ? WHERE id = ?"),
            "лишняя запятая в SQL убрана (BR-199)",
        )
        assertFalse(
            uuids.contains("""uuid = ?, """),
            "прежний невалидный SQL больше не используется (BR-199)",
        )
        assertFalse(
            uuids.contains("println("),
            "ошибки uuid логируются через SLF4J (BR-199)",
        )
    }
}
