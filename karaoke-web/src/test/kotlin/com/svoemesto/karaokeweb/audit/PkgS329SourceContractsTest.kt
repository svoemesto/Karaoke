package com.svoemesto.karaokeweb.audit

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import com.svoemesto.karaokeweb.util.TestRepoRoot

/**
 * Контрактные проверки исходников пакета `PKG-S3-29` (аудит #336, OpenProject #401).
 *
 * Эти записи нельзя проверить поведенческим тестом без БД / браузера / MinIO:
 *  - BR-010 (SQL собирается конкатенацией) — нужен настоящий `tbl_events`;
 *  - BR-017 (метрики debug-endpoint'а) — нужен живой Tomcat и БД;
 *  - BR-020/BR-024 (транзакция и статус-guard) — нужен `tbl_song_assignments`;
 *  - BR-501…BR-506 (шаблоны Thymeleaf) — нужен рендер страницы в браузере;
 *  - BR-008 (логирование) — нужен перехват логгера.
 *
 * Для них проверяется **контракт исходника**: требуемый тип/вызов/лог присутствует,
 * а запрещённая конструкция (глотание ошибки, `println` в catch, конкатенация SQL,
 * `innerText`-round-trip) — отсутствует. Класс компилируется и на старом коде,
 * поэтому служит настоящим доказательством «до/после»: после `git stash push -- <исходники>`
 * тесты падают с AssertionError, а не «класс не найден».
 *
 * @see specs/401-pkg-s3-29/report.md
 */
class PkgS329SourceContractsTest {
    private fun repoFile(relativePath: String): File = TestRepoRoot.file(relativePath)

    private fun read(relativePath: String): String = repoFile(relativePath).readText()

    /**
     * Исходник БЕЗ комментариев — иначе утверждение «в файле нет X» ловило бы само себя.
     *
     * Убираются: HTML-комментарии `<!-- … -->`, блочные `/* … */` и строковые `// …`
     * (кроме `://` внутри URL — иначе порвались бы строки вроде `https://…`).
     */
    private fun readCode(relativePath: String): String =
        read(relativePath)
            .replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""(?<!:)//[^\n]*"""), " ")

    /** Строгий фрагмент исходника: если маркер не найден — тест ПАДАЕТ. */
    private fun section(
        source: String,
        from: String,
        to: String,
    ): String {
        val start = source.indexOf(from)
        assertTrue(start >= 0, "в исходнике не найден фрагмент: $from")
        val end = source.indexOf(to, start + from.length).takeIf { it > 0 } ?: source.length
        return source.substring(start, end)
    }

    private val siteShareLinks =
        "karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/SiteShareLinksController.kt"
    private val paymentService = "karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/PaymentService.kt"
    private val eventsBuffer = "karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/EventsBuffer.kt"
    private val debugDbController = "karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/DebugDbController.kt"
    private val songEditorController =
        "karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSongEditorController.kt"
    private val ogSongController = "karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicOgSongController.kt"
    private val shareController = "karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicShareController.kt"
    private val mainController = "karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt"
    private val shareLinkService = "karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt"
    private val cleanupScheduler = "karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/StemJobTempCleanupScheduler.kt"
    private val captchaService = "karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/YandexCaptchaValidationService.kt"

    private val templates = "karaoke-web/src/main/resources/templates/"

    // ------------------------------------------------------------------ BR-007

    @Test
    fun `BR-007 отказ в правах помечен 403 и разбирается в JSON-обработчике`() {
        val code = readCode(siteShareLinks)

        assertTrue(
            code.contains("@ResponseStatus(HttpStatus.FORBIDDEN)"),
            "исключению отказа в правах нужен @ResponseStatus(FORBIDDEN)",
        )
        assertTrue(
            code.contains("@RestControllerAdvice(assignableTypes = [SiteShareLinksController::class])"),
            "нужен обработчик исключений, ограниченный этим контроллером",
        )
        assertTrue(code.contains("\"share.notEditor\""), "код отказа должен быть share.notEditor")
        assertTrue(code.contains("\"share.internal\""), "IllegalStateException должен давать 500 share.internal")
        assertFalse(
            code.contains("private class ShareAccessDeniedException"),
            "исключение должно быть видно обработчику исключений",
        )
    }

    // ------------------------------------------------------------------ BR-008

    @Test
    fun `BR-008 в PaymentService нет println ошибки логируются через SLF4J`() {
        val code = readCode(paymentService)

        assertFalse(
            code.contains("println("),
            "println в PaymentService заменён на логгер со стеком",
        )
        assertTrue(code.contains("LoggerFactory.getLogger"), "нужен логгер SLF4J")
        assertTrue(
            code.contains("""log.warn("payment:create:failed sub={} status={}", sub.id"""),
            "ошибка создания платежа должна логироваться структурированно и со стеком",
        )
    }

    // ------------------------------------------------------------------ BR-009

    @Test
    fun `BR-009 ключи ЮKassa инжектятся через Spring-а не читаются из окружения`() {
        val code = readCode(paymentService)

        val valueLines = code.lines().filter { it.contains("@Value(") }
        assertTrue(
            valueLines.any { it.contains("yookassa.shop-id") },
            "shop-id должен инжектиться из Spring-конфига: $valueLines",
        )
        assertTrue(
            valueLines.any { it.contains("yookassa.secret-key") },
            "secret-key должен инжектиться из Spring-конфига: $valueLines",
        )
        assertFalse(
            code.contains("System.getenv(\"YOOKASSA_SHOP_ID\")"),
            "чтение env в обход Spring-конфига было причиной тихой деградации",
        )
        assertFalse(
            code.contains("System.getProperty(\"yookassa.shop-id\")"),
            "чтение -D в обход Spring-конфига было причиной тихой деградации",
        )
        assertTrue(
            code.contains("shopId.isNotBlank() && secretKey.isNotBlank()"),
            "проверяться должны оба ключа",
        )
    }

    // ------------------------------------------------------------------ BR-010

    @Test
    fun `BR-010 события пишутся параметризованным запросом без rightFileName`() {
        val code = readCode(eventsBuffer)

        assertFalse(
            code.contains("rightFileName"),
            "rightFileName — санитайзер имён файлов, его нельзя применять к значениям событий",
        )
        assertFalse(
            code.contains("import com.svoemesto.karaokeapp.rightFileName"),
            "импорт rightFileName должен быть убран",
        )
        assertTrue(
            code.contains("ps.setString("),
            "значения должны биндиться параметрами PreparedStatement",
        )
        assertTrue(
            code.contains("prepareStatement(insert.sql)"),
            "INSERT должен выполняться подготовленным запросом",
        )
    }

    // ------------------------------------------------------------------ BR-017

    @Test
    fun `BR-017 debug-метрики соответствуют своему контракту`() {
        val code = readCode(debugDbController)

        assertFalse(
            code.contains("peakThreadCount"),
            "пик потоков JVM — это не максимум потоков Tomcat",
        )
        assertTrue(
            code.contains("SHOW max_connections"),
            "pgMaxConnections должен быть реальным лимитом PostgreSQL, а не константой 100",
        )
        assertFalse(
            code.contains("var max = 100"),
            "захардкоженное значение лимита соединений — ложная метрика",
        )
        assertTrue(
            code.contains("maximumPoolSize"),
            "currentTomcatMaxThreads должен браться из maximumPoolSize пула Tomcat",
        )
    }

    // ------------------------------------------------------------------ BR-024

    @Test
    fun `BR-024 refuse проверяет статус задания и не трогает одобренные`() {
        val code = readCode(songEditorController)
        val refuse = section(code, "fun refuse(", """@PostMapping("/tasks/{id}/delete")""")

        assertTrue(
            refuse.contains("REFUSABLE_STATUSES"),
            "refuse должен проверять статус задания перед удалением",
        )
        assertTrue(
            refuse.contains("HttpStatus.CONFLICT"),
            "для неразрешённого статуса — 409",
        )
        assertTrue(
            refuse.contains("not_refusable"),
            "ответ должен объяснять причину отказа",
        )
    }

    // ------------------------------------------------------------------ BR-025

    @Test
    fun `BR-025 OG-страница ошибки отдаёт 400 404 и noindex`() {
        val code = readCode(ogSongController)

        assertTrue(
            code.contains("fun ogSongHtml(") && code.contains("): ResponseEntity<String>"),
            "метод должен возвращать ResponseEntity, а не String",
        )
        assertTrue(code.contains("HttpStatus.BAD_REQUEST"), "нет id — 400")
        assertTrue(code.contains("HttpStatus.NOT_FOUND"), "песня не найдена — 404")
        assertTrue(
            code.contains("""header(HttpHeaders.CACHE_CONTROL, "noindex, nofollow")"""),
            "страница ошибки не должна индексироваться",
        )
        assertTrue(
            code.contains("""<meta name=\"robots\" content=\"noindex, nofollow\">"""),
            "нужен meta robots noindex в теле страницы ошибки",
        )
    }

    // ------------------------------------------------------------------ BR-026

    @Test
    fun `BR-026 heartbeat не кеширует внутренние ошибки`() {
        val code = readCode(shareController)
        val heartbeat = section(code, "fun heartbeat(", """@PostMapping("/release")""")

        assertTrue(
            heartbeat.contains("shouldCache"),
            "нужен явный предикат кеширования",
        )
        assertTrue(
            heartbeat.contains("it.statusCode.is2xxSuccessful || it.statusCode.value() == 410"),
            "кешировать можно только успех и «lease истёк», но не 500",
        )
    }

    // ------------------------------------------------------------------ BR-501

    @Test
    fun `BR-501 страница песни переживает null и отдаёт 404`() {
        val template = read(templates + "song.html")

        val guardIf = "th:if=" + '"' + '$' + "{song != null}" + '"'
        val guardUnless = "th:unless=" + '"' + '$' + "{song != null}" + '"'
        assertTrue(template.contains(guardIf), "нужна ветка «песня есть»")
        assertTrue(template.contains(guardUnless), "нужна ветка «песня не найдена»")
        val title = section(template, "<title", "</title>")
        assertTrue(
            title.contains(guardIf),
            "чтение song.songName должно быть защищено проверкой song != null",
        )
        assertTrue(
            template.contains("Песня не найдена"),
            "нужна видимая ветка «песня не найдена»",
        )

        val code = readCode(mainController)
        val songHandler = section(code, "fun song(", """@GetMapping("/statbysong")""")
        assertTrue(
            songHandler.contains("HttpServletResponse.SC_NOT_FOUND"),
            "контроллер должен отдавать 404 при song == null",
        )
    }

    // ------------------------------------------------------------------ BR-502

    @Test
    fun `BR-502 мёртвый блок field_author удалён из filter`() {
        val code = readCode(templates + "filter.html")

        assertFalse(
            code.contains("getElementById('field_author')"),
            "элемента field_author на странице /filter нет — обращение к нему роняло ready-обработчик",
        )
        assertFalse(
            code.contains("""document.getElementById('field_author')"""),
            "элемента field_author на странице /filter нет",
        )
        assertTrue(
            code.contains("getElementsByName('link')"),
            "обвязка ссылок (регистрация клика в аналитике) должна остаться",
        )
    }

    // ------------------------------------------------------------------ BR-503

    @Test
    fun `BR-503 параметры запроса кодируются encodeURIComponent`() {
        for (file in listOf("filter.html", "zakroma.html")) {
            val code = readCode(templates + file)
            assertFalse(
                code.contains("replaceAll('&', '%26')"),
                "$file: ручное экранирование только '&' ломает 'C++', '#' и '%'",
            )
            assertTrue(
                code.contains("encodeURIComponent("),
                "$file: параметры должны кодироваться через encodeURIComponent",
            )
        }
    }

    // ------------------------------------------------------------------ BR-505

    /**
     * BR-505: пагинированная выгрузка статистики должна существовать ТАМ, ЖЕ страница
     * `/statbysong`, и её путь НЕ должен попадать под `/api/`.
     *
     * Осознанное отклонение от буквального ТЗ пакета («продублировать выгрузку в
     * karaoke-web»): прод-nginx маршрутизирует `/api/` на karaoke-web
     * (`deploy/prod-single-host/80to8897`), поэтому эндпоинт под `/api/` стал бы
     * ПУБЛИЧНЫМ на sm-karaoke.ru — выгрузка статистики по всем песням утекла бы наружу.
     * Поэтому выгрузка живёт по пути `/statbysong-export` (вне `/api/`): на проде этот
     * путь отдаёт публичный SPA, локально — доступен, как и сама страница.
     */
    @Test
    fun `BR-505 пагинированная выгрузка статистики есть в karaoke-web и не торчит в api`() {
        val code = readCode(mainController)

        assertTrue(
            code.contains("""@GetMapping("/statbysong-export")"""),
            "эндпоинт, на который ссылается страница /statbysong, должен существовать в karaoke-web",
        )
        assertFalse(
            code.contains("""@GetMapping("/api/stats/by-song")"""),
            "выгрузка статистики не должна попадать под /api/ — этот префикс на проде публичный",
        )
        assertTrue(
            code.contains("getStatBySong(") && code.contains("offset = offset"),
            "выгрузка должна использовать тот же источник данных, что и страница",
        )

        val template = read(templates + "statbysong.html")
        assertTrue(
            template.contains("/statbysong-export?page=1"),
            "ссылка в баннере должна вести на существующий эндпоинт",
        )
        assertFalse(
            template.contains("/api/stats/by-song"),
            "ссылка не должна вести на путь, которого на хосте karaoke-web нет",
        )
    }

    // ------------------------------------------------------------------ BR-506

    @Test
    fun `BR-506 форматированный текст разбирается безопасно`() {
        val code = readCode(templates + "testpage.html")

        assertFalse(
            code.contains("innerText"),
            "innerText снимает экранирование Thymeleaf — round-trip больше не используется",
        )
        assertFalse(
            code.contains("textFormatted.innerHTML ="),
            "прямая вставкаinnerHTML без санитайзера — источник XSS",
        )
        assertTrue(
            code.contains("DOMParser"),
            "разбор должен идти в инертном документе DOMParser",
        )
        assertTrue(
            code.contains("createTextNode"),
            "пользовательский текст вставляется только как текст",
        )
        assertTrue(
            code.contains("ALLOWED_TAGS"),
            "нужен allow-list тегов",
        )
        assertTrue(
            code.contains("ALLOWED_CSS_PROPERTIES"),
            "нужен allow-list CSS-свойств",
        )
        assertTrue(
            code.contains("""<textarea hidden="hidden" id="text_formatted_html""""),
            "носитель разметки должен быть инертным (textarea), а не div",
        )
    }

    // ------------------------------------------------------------------ BR-519 / BR-520

    @Test
    fun `BR-519 лимит перевыпуска считается без ссылки на ту же песню`() {
        val code = readCode(shareLinkService)
        val counter = section(code, "private fun countActiveForUser(", "private fun countGenerationsLast24h")

        assertTrue(
            counter.contains("song_id <> ?"),
            "ссылка на заменяемую песню не должна учитываться в лимите",
        )
        assertTrue(
            counter.contains("expires_at > now()"),
            "истёкшие, но ещё не отозванные sweeper'ом ссылки не должны учитываться",
        )
    }

    @Test
    fun `BR-520 отзыв и вставка новой ссылки идут в одной транзакции`() {
        val code = readCode(shareLinkService)
        val createLink = section(code, "fun createLink(", "fun revokeLink(")

        assertTrue(
            createLink.contains("conn.autoCommit = false"),
            "отзыв и вставка должны идти в одной транзакции",
        )
        assertTrue(createLink.contains("conn.commit()"), "нужен commit")
        assertTrue(createLink.contains("conn.rollback()"), "нужен rollback при ошибке INSERT")
        assertTrue(
            createLink.indexOf("conn.commit()") < createLink.indexOf("return CreateResult("),
            "commit должен происходить до возврата результата",
        )
    }

    // ------------------------------------------------------------------ BR-521

    @Test
    fun `BR-521 результат удаления проверяется а неудачи логируются`() {
        val code = readCode(cleanupScheduler)

        assertTrue(
            code.contains(".getOrDefault(false)"),
            "результат delete() должен проверяться, а не проглатываться",
        )
        assertTrue(
            code.contains("delete-returned-false"),
            "неудача удаления должна попадать в лог",
        )
        assertTrue(
            code.contains("failedDeletions"),
            "неудачные удаления должны агрегироваться в счётчик",
        )
        assertTrue(
            code.contains("fun failedDeletionCount()"),
            "счётчик неудач должен отдаваться наружу как метрика",
        )
        assertTrue(
            code.contains("""LoggerFactory.getLogger("infra.stemjobs.cleanup")"""),
            "нужна своя категория журнала для чистки",
        )
    }

    // ------------------------------------------------------------------ BR-522

    @Test
    fun `BR-522 ошибка внешнего сервиса логируется со стеком`() {
        val code = readCode(captchaService)

        assertTrue(
            code.contains("""log.warn("captcha:validation:unavailable", e)"""),
            "в лог должно уходить исключение целиком (со стеком), а не только e.message",
        )
        assertFalse(
            code.contains("""log.warn("captcha:validation:unavailable error={}", e.message)"""),
            "лог без стека теряет причину сбоя",
        )
    }
}
