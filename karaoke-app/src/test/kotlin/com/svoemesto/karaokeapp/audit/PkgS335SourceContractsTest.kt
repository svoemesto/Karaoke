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
    fun `BR-216 realtime-контракт SPA совпадает с сервером`() {
        // Легаси-страница songs2 удалена 2026-10-26. Live-обновления приходят в
        // SPA по SSE: сервер шлёт `recordChange` (recordId / tableName / diffs),
        // клиент читает recordId и подменяет песню по record.
        val app = read("webvue3/src/App.vue")
        assertTrue(app.contains("new EventSourcePolyfill("), "нужен SSE-канал (BR-216)")
        assertTrue(app.contains("/api/subscribe?tabId="), "SSE-эндпоинт — /api/subscribe (BR-216)")
        assertTrue(app.contains("case 'RECORD_CHANGE'"), "обработчик RECORD_CHANGE обязателен (BR-216)")
        assertTrue(app.contains("case 'tbl_songs'"), "имя таблицы песен — tbl_songs (BR-216)")
        assertFalse(app.contains("'" + legacyTable + "'"), "старое имя таблицы убрано (BR-216)")
        val store = read("webvue3/src/components/Songs/store.js")
        assertTrue(store.contains("let songId = userEventData.recordId"), "нужен ключ recordId (BR-216)")
        assertTrue(
            store.contains("state.songsDigest.splice(i, 1, userEventData.record)"),
            "песня подменяется по record (BR-216)",
        )
        // Разбор сообщения не должен ронять обработчик.
        assertTrue(app.contains("try {"), "JSON.parse должен быть в try/catch (BR-216)")
    }

    // ---------------------------------------------------------------- BR-217

    @Test
    fun `BR-217 ajax-ошибки не игнорируются`() {
        // Легаси-страница songs2 удалена 2026-10-26. Современный носитель —
        // SPA: общий XHR-helper отклоняет промис на не-2xx и на сетевой ошибке,
        // вызывающий обязан обработать отказ.
        val utils = read("webvue3/src/lib/utils.js")
        assertTrue(utils.contains("export function promisedXMLHttpRequest("), "нужен общий XHR-helper (BR-217)")
        assertTrue(utils.contains("xhr.onerror = () => {"), "сетевая ошибка обязана отклонять промис (BR-217)")
        assertTrue(utils.contains("reject(err)"), "ошибка пробрасывается вызывающему (BR-217)")
        val store = read("webvue3/src/components/Songs/store.js")
        assertTrue(store.contains(".catch((error) => {"), "вызывающий обрабатывает отказ (BR-217)")
        assertTrue(
            store.contains("console.error('Ошибка сохранения:'"),
            "ошибка сохранения не остаётся без отчёта (BR-217)",
        )
    }

    // ---------------------------------------------------------------- BR-218

    @Test
    fun `BR-218 нет синхронных XHR`() {
        // Легаси-страница songs2 удалена 2026-10-26; в SPA все запросы асинхронные.
        val utils = read("webvue3/src/lib/utils.js")
        assertFalse(
            Regex("""xhr\.open\([^)]*,\s*false\s*\)""").containsMatchIn(utils),
            "синхронный XHR блокирует главный поток (BR-218)",
        )
        assertTrue(
            utils.contains("xhr.open(obj.method || 'GET', obj.url, true)"),
            "helper делает асинхронный запрос (BR-218)",
        )
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
