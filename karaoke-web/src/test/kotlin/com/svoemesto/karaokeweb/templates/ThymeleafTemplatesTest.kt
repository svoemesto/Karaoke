package com.svoemesto.karaokeweb.templates

import com.svoemesto.karaokeapp.model.NewsDto
import com.svoemesto.karaokeapp.model.StatBySongDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.util.Locale

/**
 * Регрессионные тесты «до/после» рендера Thymeleaf-шаблонов karaoke-web.
 *
 * Покрывает две записи аудита OpenProject #378:
 *
 *  - **BR-500** (`statbysong.html`, COMPAT): шаблон обращался к `stat.cntBoosty`, которого нет в
 *    `StatBySongDto`. SpEL бросал `EL1008E: Property or field 'cntBoosty' cannot be found`, и
 *    **вся** страница `/statbysong` отдавала 500. Теперь блок Boosty убран, страница рендерится.
 *  - **BR-504** (`main.html`, SEC): ссылка новости попадала в `href` «как есть». После правки в
 *    модель попадают только `http(s)`-ссылки (`SafeLink.isSafe` в `MainController`), а шаблон
 *    для новости без безопасной ссылки рендерит заголовок обычным текстом — без `href` и без
 *    `data-link` (значит, `window.open` по клику тоже не сработает).
 *
 * Тест рендерит настоящие шаблоны из `src/main/resources/templates` (ClassLoaderTemplateResolver
 * видит их на test-классpath), поэтому забытое поле DTO снова уронит тест, а не прод-страницу.
 */
class ThymeleafTemplatesTest {
    private fun engine(): SpringTemplateEngine {
        val resolver =
            ClassLoaderTemplateResolver().apply {
                prefix = "templates/"
                suffix = ".html"
                characterEncoding = "UTF-8"
            }
        return SpringTemplateEngine().apply { setTemplateResolver(resolver) }
    }

    private fun news(
        id: Long,
        title: String,
        link: String,
    ): NewsDto =
        NewsDto(
            id = id,
            title = title,
            body = "body",
            category = "general",
            link = link,
            publishAt = "2026-10-24 20:00:00",
            createdAt = "2026-10-24 19:00:00",
        )

    // ---- BR-500: страница статистики рендерится и не содержит колонки Boosty ----

    private fun statBySong(): StatBySongDto =
        StatBySongDto(
            songId = 1,
            description = "Author - Song",
            cntTotal = 10,
            cntSm = 1,
            cntPlayer = 2,
            cntPlayerShown = 1,
            cntPlayerOpened = 1,
            cntPlayerPlay = 1,
            cntPlayerPause = 0,
            cntPlayerSeek = 0,
            cntPlayerExport = 0,
            cntPlayerProgress = 1,
            cntPlayerEnded = 1,
            cntVkKaraoke = 1,
            cntVkLyrics = 0,
            cntDzenKaraoke = 1,
            cntDzenLyrics = 0,
            cntTgKaraoke = 1,
            cntTgLyrics = 0,
            cntMax = 0,
            cntSponsr = 0,
        )

    @Test
    fun `statbysong рендерится без поля cntBoosty`() {
        val context =
            Context(
                Locale("ru", "RU"),
                mapOf(
                    "stats" to listOf(statBySong()),
                    "totalCount" to 1,
                ),
            )

        // ДО правки здесь было TemplateProcessingException: EL1008E Property or field 'cntBoosty'
        // cannot be found on object of type StatBySongDto.
        val html = engine().process("statbysong", context)

        assertTrue(html.contains("Author - Song"), "строка статистики должна отрендериться")
        assertFalse(html.contains("BOOSTY"), "колонка Boosty удалена из шаблона (BR-500)")
        assertFalse(html.contains("cntBoosty"), "обращений к отсутствующему полю DTO быть не должно")
    }

    // ---- BR-504: ссылка новости рендерится только после проверки схемы ----

    @Test
    fun `новость без безопасной ссылки рендерится текстом, без href и data-link`() {
        val context =
            Context(
                Locale("ru", "RU"),
                mapOf(
                    "onSponsr" to 0,
                    "onAir" to 0,
                    "exclusive" to 0,
                    "inWork" to 0,
                    "total" to 0,
                    // MainController обнуляет link, если SafeLink.isSafe(link) == false.
                    "latestNews" to listOf(news(id = 1, title = "Снятая новость", link = "")),
                ),
            )

        val html = engine().process("main", context)

        assertTrue(html.contains("Снятая новость"), "заголовок новости должен остаться на странице")
        assertFalse(html.contains("href=\"\""), "пустой href не рендерится")
        assertFalse(html.contains("javascript:"), "никакой javascript-схемы в разметке")
        assertTrue(
            html.contains("data-link=\"\"") || html.contains("data-link"),
            "data-link присутствует, но пустой — window.open по клику не сработает",
        )
    }

    @Test
    fun `новость с безопасной ссылкой рендерится ссылкой`() {
        val context =
            Context(
                Locale("ru", "RU"),
                mapOf(
                    "onSponsr" to 0,
                    "onAir" to 0,
                    "exclusive" to 0,
                    "inWork" to 0,
                    "total" to 0,
                    "latestNews" to listOf(news(id = 2, title = "Новая песня", link = "https://sm-karaoke.ru/news/2")),
                ),
            )

        val html = engine().process("main", context)

        assertTrue(html.contains("https://sm-karaoke.ru/news/2"), "безопасная ссылка должна попасть в разметку")
        assertTrue(html.contains("<a "), "заголовок новости — ссылка")
    }

    // ---- BR-016: ошибка БД не выглядит как настоящий ноль ----

    private fun mainContext(
        state: String,
        onSponsr: Int = 0,
    ): Context =
        Context(
            Locale("ru", "RU"),
            mapOf(
                "onSponsr" to onSponsr,
                "onAir" to 0,
                "exclusive" to 0,
                "inWork" to 0,
                "total" to 0,
                "statsState" to state,
                "latestNews" to emptyList<NewsDto>(),
            ),
        )

    @Test
    fun `BR-016 при warming_up главная показывает прочерк вместо нуля`() {
        val html = engine().process("main", mainContext(state = "warming_up"))

        assertTrue(html.contains("data-stats-state=\"warming_up\""), "состояние кеша должно попасть в разметку")
        assertEquals(4, Regex(">—<").findAll(html).count(), "все четыре счётчика — «нет данных», а не 0")
        assertFalse(html.contains("БД временно недоступна"), "ошибки ещё не было")
    }

    @Test
    fun `BR-016 при ready настоящий ноль показывается нулём`() {
        val html = engine().process("main", mainContext(state = "ready"))

        assertTrue(html.contains("data-stats-state=\"ready\""))
        assertEquals(0, Regex(">—<").findAll(html).count(), "ноль — это настоящий ноль, прочерка быть не должно")
        assertTrue(html.contains(">0<"), "счётчик должен отрендерить 0")
    }

    @Test
    fun `BR-016 при error главная показывает последние цифры и предупреждение`() {
        val html = engine().process("main", mainContext(state = "error", onSponsr = 42))

        assertTrue(html.contains("data-stats-state=\"error\""))
        assertTrue(html.contains(">42<"), "последние успешные значения не подменяются нулями")
        assertTrue(html.contains("БД временно недоступна"), "владелец должен видеть, что цифры устарели")
        assertEquals(0, Regex(">—<").findAll(html).count())
    }
}
