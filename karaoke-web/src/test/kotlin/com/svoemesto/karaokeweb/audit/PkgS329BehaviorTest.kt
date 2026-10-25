package com.svoemesto.karaokeweb.audit

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeweb.WORKING_DATABASE
import com.svoemesto.karaokeweb.controllers.PublicShareController
import com.svoemesto.karaokeweb.services.CaptchaConfigService
import com.svoemesto.karaokeweb.services.CaptchaValidationResult
import com.svoemesto.karaokeweb.services.EventsBuffer
import com.svoemesto.karaokeweb.services.KaraokeWebTestEnv
import com.svoemesto.karaokeweb.services.SiteUserResolver
import com.svoemesto.karaokeweb.services.SongShareLinkService
import com.svoemesto.karaokeweb.services.YandexCaptchaValidationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.Locale

/**
 * Поведенческие регрессионные тесты «до/после» пакета `PKG-S3-29`
 * (аудит #336, OpenProject #401) — модуль `karaoke-web`.
 *
 * **Важное свойство этих тестов: они компилируются и на СТАРОМ коде** (не используют
 * новых API), поэтому `git stash push -- <исходники>` даёт честное падение тестов, а не
 * «класс не найден». Остальные записи пакета закрыты контрактными проверками исходников
 * в [PkgS329SourceContractsTest].
 *
 * @see specs/401-pkg-s3-29/report.md
 */
class PkgS329BehaviorTest {
    companion object {
        /**
         * `WORKING_DATABASE` (= `Connection.local()`) инициализируется лениво и читает глобалы
         * karaoke-web (`WEB_WORK_ON_SERVER` и др.) — без них `IllegalStateException`. Само
         * подключение к БД остаётся ленивым, тесты БД не касаются.
         */
        @JvmStatic
        @BeforeAll
        fun setUpEnv() {
            KaraokeWebTestEnv.init()
        }
    }

    // ------------------------------------------------------------------ BR-010

    /**
     * Поддельное соединение: отдаёт заранее настроенное JDBC-соединение.
     * Метод `KaraokeConnection.getConnection` — `open` ради таких подмен (см. KDoc
     * `KaraokeConnection`).
     */
    private class FakeConnection(
        private val jdbc: Connection,
    ) : KaraokeConnection("jdbc:fake:events-buffer-test", "test", "test", "FAKE") {
        override fun getConnection(): Connection? = jdbc
    }

    @Test
    fun `BR-010 значения события биндятся параметрами а не подставляются в SQL`() {
        val jdbc = Mockito.mock(Connection::class.java)
        val statement = Mockito.mock(PreparedStatement::class.java)
        val capturedSql = mutableListOf<String>()
        val boundValues = mutableListOf<String>()
        Mockito.`when`(jdbc.prepareStatement(Mockito.anyString())).thenAnswer { invocation ->
            capturedSql.add(invocation.getArgument(0, String::class.java))
            statement
        }
        Mockito.`when`(statement.executeUpdate()).thenReturn(1)
        // setString возвращает Unit — ставим ответ через doAnswer, а не when().
        Mockito
            .doAnswer { invocation ->
                boundValues.add(invocation.getArgument(1, String::class.java))
                null
            }.`when`(statement)
            .setString(Mockito.anyInt(), Mockito.anyString())

        val record =
            EventsBuffer.EventRecord(
                fieldsValues =
                    listOf(
                        "event_type" to "clickToLink",
                        "link_name" to "https://example.com/a?b=1&c=2",
                    ),
                eventType = "clickToLink",
                clientIp = "10.0.0.1",
                userAgent = "test-agent",
                anonId = null,
                siteUserId = 0,
            )

        val inserted = EventsBuffer().executeSingle(record, FakeConnection(jdbc))

        assertTrue(inserted, "INSERT должен выполняться")
        val sql = capturedSql.single()
        assertTrue(sql.contains("?"), "в SQL должны быть JDBC-плейсхолдеры, а не значения: $sql")
        assertFalse(
            sql.contains("example.com"),
            "пользовательское значение не должно подставляться в SQL: $sql",
        )
        assertFalse(
            sql.contains("&c=2"),
            "значение с '&' не должно попадать в SQL (иначе запрос разваливается): $sql",
        )
        assertEquals(
            listOf("clickToLink", "https://example.com/a?b=1&c=2", "10.0.0.1", "test-agent"),
            boundValues,
            "значения должны биндиться параметрами — без искажения и без подстановки в SQL",
        )
    }

    // ------------------------------------------------------------------ BR-026

    @Test
    fun `BR-026 внутренняя ошибка heartbeat не кешируется`() {
        val shareService = Mockito.mock(SongShareLinkService::class.java)
        // heartbeat возвращает Unit — для void-метода нужен doThrow, а не when().
        // Аргументы — конкретные значения (Mockito не смешивает matcher и обычное значение,
        // а any() вернул бы null → null-check не-null параметра упал бы на стадии стаббинга).
        Mockito
            .doThrow(SongShareLinkService.InternalError(RuntimeException("db is down")))
            .`when`(shareService)
            .heartbeat("hash-1", WORKING_DATABASE)
        val controller =
            PublicShareController(
                shareService = shareService,
                siteUserResolver = Mockito.mock(SiteUserResolver::class.java),
                publicSiteUrl = "https://sm-karaoke.ru",
            )

        val first = controller.heartbeat(mapOf("sessionTokenHash" to "hash-1"))
        val second = controller.heartbeat(mapOf("sessionTokenHash" to "hash-1"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, first.statusCode)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, second.statusCode)
        // ДО правки второй вызов отдавался из кеша (15 сек TTL) — сервис звали один раз.
        Mockito
            .verify(shareService, Mockito.times(2))
            .heartbeat("hash-1", WORKING_DATABASE)
    }

    @Test
    fun `BR-026 успешный heartbeat кешируется`() {
        val shareService = Mockito.mock(SongShareLinkService::class.java)
        val controller =
            PublicShareController(
                shareService = shareService,
                siteUserResolver = Mockito.mock(SiteUserResolver::class.java),
                publicSiteUrl = "https://sm-karaoke.ru",
            )

        val first = controller.heartbeat(mapOf("sessionTokenHash" to "hash-2"))
        val second = controller.heartbeat(mapOf("sessionTokenHash" to "hash-2"))

        assertEquals(HttpStatus.OK, first.statusCode)
        assertEquals(HttpStatus.OK, second.statusCode)
        // Успешный ответ кешируется (в этом и смысл кеша) — сервис звали один раз.
        Mockito
            .verify(shareService, Mockito.times(1))
            .heartbeat("hash-2", WORKING_DATABASE)
    }

    // ------------------------------------------------------------------ BR-501

    private fun templateEngine(): SpringTemplateEngine {
        val resolver =
            ClassLoaderTemplateResolver().apply {
                prefix = "templates/"
                suffix = ".html"
                characterEncoding = "UTF-8"
            }
        return SpringTemplateEngine().apply { setTemplateResolver(resolver) }
    }

    @Test
    fun `BR-501 страница песни при song=null рендерится а не падает`() {
        // Атрибута `song` в модели нет → в SpEL это null.
        val context = Context(Locale("ru", "RU"), emptyMap<String, Any>())

        // ДО правки здесь было TemplateProcessingException:
        // EL1007E Property or field 'songName' cannot be found on null → HTTP 500 вместо 404.
        val html = templateEngine().process("song", context)

        assertTrue(
            html.contains("Песня не найдена"),
            "нужна видимая ветка «песня не найдена» вместо падения SpEL",
        )
    }

    // ------------------------------------------------------------------ BR-522

    @Test
    fun `BR-522 недоступность внешнего сервиса отличается от непройденной капчи`() {
        assertEquals("captcha", CaptchaValidationResult.FAILED.errorCode)
        assertEquals("captcha_unavailable", CaptchaValidationResult.UNAVAILABLE.errorCode)
        assertFalse(
            CaptchaValidationResult.FAILED.errorCode == CaptchaValidationResult.UNAVAILABLE.errorCode,
            "«сервис недоступен» и «капча не пройдена» — разные исходы",
        )
    }

    @Test
    fun `BR-522 исключение внешнего сервиса даёт UNAVAILABLE а не FAILED`() {
        val service =
            YandexCaptchaValidationService(
                webClient = Mockito.mock(WebClient::class.java),
                captchaConfigService = CaptchaConfigService("server-key"),
            )

        val result = service.validateWith("token", "10.0.0.1") { _, _, _ -> throw RuntimeException("timeout") }

        assertEquals(CaptchaValidationResult.UNAVAILABLE, result)
    }
}
