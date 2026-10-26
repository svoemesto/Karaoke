package com.svoemesto.karaokeapp.audit

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Регрессия пакета `PKG-S3-35` (аудит #336, OpenProject #409) — «остаток»:
 * BR-216, BR-217, BR-218, BR-220, BR-221, BR-222, BR-223, BR-224, BR-225,
 * BR-226, BR-507, BR-509, BR-510.
 *
 * Класс компилируется и на коде «до» правки: проверяются тексты исходников и
 * шаблонов, поэтому «до» — это падение утверждений, а не ошибка компиляции.
 *
 * @see specs/409-pkg-s3-35/report.md
 */
class PkgS335SourceContractsTest {
    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        return dir
    }

    private fun repoFile(relativePath: String): File {
        val file = File(repoRoot(), relativePath)
        assertTrue(file.isFile, "файл не найден: $relativePath")
        return file
    }

    private fun read(relativePath: String): String = repoFile(relativePath).readText()

    /** Текст без комментариев (Kotlin `//`, `/* */`, HTML `<!-- -->`). */
    private fun readCode(relativePath: String): String =
        read(relativePath)
            .replace(Regex("""(?m)^[ \t]*/\*.*?\*/"""), " ")
            .replace(Regex("""//[^\n]*"""), " ")
            .replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), " ")

    /**
     * Литерал устаревшего имени таблицы собирается из двух частей — иначе
     * guard `tools/check-no-legacy-tbl-settings.sh` принимает сам тест за
     * «использование» старого имени (в baseline пришлось бы вести строки
     * по номерам — они сдвигаются при любой правке файла).
     */
    private val legacyTable = "tbl_" + "settings"

    private val tpl = "karaoke-app/src/main/resources/templates/"
    private val ctrl = "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/"
    private val model = "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/"
    private val svc = "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/"

    // ---------------------------------------------------------------- BR-216

    @Test
    fun `BR-216 realtime-контракт songs2 совпадает с сервером`() {
        val songs2 = readCode("$tpl/songs2.html")
        assertFalse(
            songs2.contains("recordchange.recordChangeId"),
            "ключ recordChangeId не совпадает с DTO RecordChangeMessage (BR-216)",
        )
        assertFalse(songs2.contains("recordchange.recordChangeTableName"), "recordChangeTableName (BR-216)")
        assertFalse(songs2.contains("recordchange.recordChangeDiffs"), "recordChangeDiffs (BR-216)")
        assertTrue(songs2.contains("recordchange.recordId"), "нужен ключ recordId (BR-216)")
        assertTrue(songs2.contains("recordchange.tableName"), "нужен ключ tableName (BR-216)")
        assertTrue(songs2.contains("recordchange.diffs"), "нужен ключ diffs (BR-216)")
        assertTrue(songs2.contains("'tbl_songs'"), "имя таблицы песен — tbl_songs (BR-216)")
        assertFalse(songs2.contains("'" + legacyTable + "'"), "старое имя таблицы убрано (BR-216)")
        // Транспорт: STOMP-эндпоинта `/api/message` в karaoke-app нет — нужен SSE.
        assertFalse(songs2.contains("new SockJS('/api/message')"), "мёртвый STOMP-эндпоинт убран (BR-216)")
        assertTrue(songs2.contains("new EventSource('/api/subscribe"), "нужен SSE-канал (BR-216)")
        // Разбор сообщения не должен ронять обработчик.
        assertTrue(songs2.contains("try {"), "JSON.parse должен быть в try/catch (BR-216)")
    }

    // ---------------------------------------------------------------- BR-217

    @Test
    fun `BR-217 ajax-ошибки songs2 не игнорируются`() {
        val songs2 = read("$tpl/songs2.html")
        val ajaxCount = songs2.split("$.ajax({").size - 1
        val errCount = songs2.split("error: function (xhr, status, error)").size - 1
        assertTrue(ajaxCount > 0, "в шаблоне есть ajax-вызовы (BR-217)")
        assertTrue(
            errCount >= ajaxCount,
            "у всех ajax-вызовов должен быть error-обработчик: $errCount из $ajaxCount (BR-217)",
        )
        assertTrue(songs2.contains("function reportAjaxError("), "нужен общий обработчик ошибки (BR-217)")
        // Индикатор «запущено» ставится только после успешного ответа.
        val doProcess = songs2.substringAfter("function doProcess(link)").substringBefore("function doPostForm")
        assertTrue(
            doProcess.indexOf("getText(") < doProcess.indexOf("#FFFF00"),
            "индикатор ставится только после успешного ответа (BR-217)",
        )
        // Удаление: сервер отдаёт 0, когда песни нет.
        assertTrue(songs2.contains("Песня не найдена"), "ответ 0 при удалении обрабатывается (BR-217)")
    }

    // ---------------------------------------------------------------- BR-218

    @Test
    fun `BR-218 в songs2 нет синхронных XHR`() {
        val songs2 = readCode("$tpl/songs2.html")
        assertFalse(
            Regex("""request\.open\([^)]*,\s*false\s*\)""").containsMatchIn(songs2),
            "синхронный XHR блокирует главный поток (BR-218)",
        )
        assertTrue(songs2.contains("function getText("), "нужен асинхронный helper (BR-218)")
        assertTrue(songs2.contains("request.open('GET', url, true)"), "helper делает асинхронный GET (BR-218)")
    }

    // ---------------------------------------------------------------- BR-220

    @Test
    fun `BR-220 подсчёт элементов плейлистов батчем`() {
        val controller = readCode("$ctrl/SitePlaylistsController.kt")
        assertFalse(
            controller.contains("SitePlaylistItem.countItems(pl.id, db)"),
            "COUNT(*) на каждый плейлист — N+1 (BR-220)",
        )
        assertTrue(
            controller.contains("SitePlaylistItem.countItemsByPlaylistIds("),
            "нужен один агрегатный запрос (BR-220)",
        )
        val item = readCode("$model/SitePlaylistItem.kt")
        assertTrue(item.contains("GROUP BY playlist_id"), "агрегатный запрос с GROUP BY (BR-220)")
        // Владельцы — одним запросом, а не по SELECT на каждого.
        assertFalse(
            controller.contains("associateWith { SiteUser.getSiteUserById("),
            "владельцы грузились по одному SELECT'у (BR-220)",
        )
        assertTrue(controller.contains("SiteUser.loadByIds("), "владельцы батчем (BR-220)")
    }

    // ---------------------------------------------------------------- BR-221

    @Test
    fun `BR-221 диджесты поддерживают серверную пагинацию и SQL-фильтры`() {
        val users = readCode("$ctrl/SiteUsersController.kt")
        assertTrue(users.contains("perPage"), "нужен параметр perPage (BR-221)")
        assertTrue(users.contains("totalCount"), "нужен totalCount (BR-221)")
        assertTrue(users.contains("SiteUser.countList("), "totalCount — отдельный COUNT(*) (BR-221)")
        val user = readCode("$model/SiteUser.kt")
        assertTrue(user.contains("fun countList("), "нужен countList в модели (BR-221)")
        val editor = readCode("$ctrl/SongEditorController.kt")
        assertFalse(
            editor.contains("assignments = assignments.filter { it.assigneeId == a }"),
            "фильтр по assigneeId применялся в памяти (BR-221)",
        )
        assertTrue(editor.contains("SongAssignment.loadList("), "фильтры применяются в SQL (BR-221)")
    }

    // ---------------------------------------------------------------- BR-222

    @Test
    fun `BR-222 валидация idStatus до мутаций`() {
        val editor = readCode("$ctrl/SongEditorController.kt")
        val validation = editor.indexOf("invalid_idstatus")
        val mutation = editor.indexOf("song.setAllVoices(")
        assertTrue(validation > 0, "валидация idStatus есть (BR-222)")
        assertTrue(mutation > 0, "вызов setAllVoices есть (BR-222)")
        assertTrue(
            validation < mutation,
            "валидация idStatus должна стоять ДО применения разметки (BR-222)",
        )
    }

    // ---------------------------------------------------------------- BR-223

    @Test
    fun `BR-223 переход статуса задания атомарный`() {
        val model = readCode("$model/SongAssignment.kt")
        assertTrue(
            model.contains("fun markApprovedIfNotApproved("),
            "нужен атомарный переход статуса (BR-223)",
        )
        assertTrue(
            model.contains("WHERE id = ? AND admin_status <> ?"),
            "условие перехода должно быть условным UPDATE (BR-223)",
        )
        val editor = readCode("$ctrl/SongEditorController.kt")
        assertTrue(
            editor.contains("SongAssignment.markApprovedIfNotApproved("),
            "контроллер обязан использовать атомарный переход (BR-223)",
        )
        assertFalse(
            editor.contains("aRead.adminStatus = SongAssignmentStatus.ADMIN_APPROVED"),
            "check-then-act запись статуса заменена атомарной (BR-223)",
        )
    }

    // ---------------------------------------------------------------- BR-224

    @Test
    fun `BR-224 ошибки апрува логируются через SLF4J со стеком`() {
        val editor = readCode("$ctrl/SongEditorController.kt")
        assertTrue(
            editor.contains("LoggerFactory.getLogger(SongEditorController::class.java)"),
            "нужен SLF4J-логгер контроллера (BR-224)",
        )
        assertFalse(
            editor.contains("[SongEditorController.approve] push на SERVER не удался"),
            "сбой push логируется не через println (BR-224)",
        )
        assertFalse(
            editor.contains("[approve/sync-related] ошибка sync related"),
            "сбой sync-related логируется не через println (BR-224)",
        )
        assertTrue(editor.contains("log.error("), "ошибки логируются через log.error (BR-224)")
    }

    // ---------------------------------------------------------------- BR-225

    @Test
    fun `BR-225 легаси-документация использует действующую схему`() {
        val dir = File(repoRoot(), "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/docs")
        val sqlFiles = dir.listFiles { f -> f.isFile && f.name.endsWith(".sql") } ?: emptyArray()
        assertTrue(sqlFiles.isNotEmpty(), "легаси-документация найдена (BR-225)")
        sqlFiles.forEach { f ->
            val text = f.readText()
            assertFalse(
                text.contains(legacyTable),
                "${f.name} использует устаревшее имя таблицы (BR-225)",
            )
        }
    }

    // ---------------------------------------------------------------- BR-226

    @Test
    fun `BR-226 кеш статистики работает с single-flight`() {
        val cache = readCode("$svc/StatsCache.kt")
        assertTrue(cache.contains("fun <T> getOrCompute("), "нужен getOrCompute (BR-226)")
        assertTrue(cache.contains("inflight.putIfAbsent("), "нужен single-flight guard (BR-226)")
        val controller = readCode("$ctrl/StatsController.kt")
        assertTrue(
            controller.contains("StatsCache.getOrCompute("),
            "контроллер обязан использовать single-flight (BR-226)",
        )
        assertFalse(
            controller.contains("StatsCache.put(key, body as Any)"),
            "check-then-act заменён на single-flight (BR-226)",
        )
    }

    // ---------------------------------------------------------------- BR-507

    @Test
    fun `BR-507 CI собирает бэкенд и фронтенд и гоняет тесты`() {
        val ci = readCode(".github/workflows/lint.yml")
        assertTrue(ci.contains("backend-build:"), "джоб сборки бэкенда (BR-507)")
        assertTrue(ci.contains(":karaoke-app:compileKotlin"), "компиляция Kotlin в CI (BR-507)")
        assertTrue(ci.contains(":karaoke-app:test"), "unit-тесты бэкенда в CI (BR-507)")
        assertTrue(ci.contains("frontend-build:"), "джоб сборки фронтенда (BR-507)")
        assertTrue(ci.contains("npm run build"), "сборка SPA в CI (BR-507)")
    }

    // ---------------------------------------------------------------- BR-509

    @Test
    fun `BR-509 guard прямых коммитов подключён к CI и имеет негативный тест`() {
        val ci = readCode(".github/workflows/lint.yml")
        assertTrue(ci.contains("bash tools/check-direct-commits.sh"), "guard подключён к CI (BR-509)")
        assertTrue(
            ci.contains("bash tools/check-direct-commits.sh --self-test"),
            "негативный тест guard'а подключён к CI (BR-509)",
        )
        assertFalse(
            ci.contains("""git log --first-parent origin/master --since=24.hours --no-merges --pretty=format:"%H %s" | wc -l"""),
            "старая проверка с off-by-one удалена (BR-509)",
        )
        val guard = readCode("tools/check-direct-commits.sh")
        assertTrue(guard.contains("--self-test"), "у guard'а есть режим --self-test (BR-509)")
        assertTrue(guard.contains("exit 2"), "код 2 = «проверка не выполнена» (BR-509)")
        assertTrue(
            guard.contains("git rev-list --count") || guard.contains("--pretty=format"),
            "подсчёт без off-by-one (BR-509)",
        )
    }

    // ---------------------------------------------------------------- BR-510

    @Test
    fun `BR-510 per-feature документы проверяются в обоих каталогах`() {
        val ci = readCode(".github/workflows/lint.yml")
        assertTrue(
            ci.contains("bash tools/check-feature-doc.sh docs/features/*.md archive/docs/features/*.md"),
            "CI проверяет оба каталога per-feature документов (BR-510)",
        )
        val lycheeArgs = ci.substringAfter("lychee-action@").substringBefore("CONTRIBUTING.md")
        val lycheeTargets = lycheeArgs.lines().map { it.trim() }
        assertTrue(
            lycheeTargets.contains("docs/features/"),
            "lychee (offline-ссылки) проверяет живые per-feature документы (BR-510)",
        )
        val hook = readCode(".pre-commit-config.yaml")
        assertTrue(
            hook.contains("docs/features/*.md archive/docs/features/*.md"),
            "pre-commit-хук проверяет оба каталога (BR-510)",
        )
    }
}
