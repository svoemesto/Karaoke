package com.svoemesto.karaokeapp.audit

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Регрессия пакета `PKG-S3-24` (аудит #336, OpenProject #396), партия 3 —
 * BR-202, BR-203, BR-204, BR-207, BR-208, BR-209, BR-210, BR-211, BR-212, BR-215.
 *
 * Класс компилируется и на коде «до» правки: проверяются тексты шаблонов, поэтому
 * «до» — это падение утверждений, а не ошибка компиляции.
 *
 * @see specs/396-pkg-s3-24/report.md
 */
class PkgS324Batch3SourceContractsTest {
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

    private fun readCode(relativePath: String): String =
        read(relativePath)
            .replace(Regex("""(?m)^[ \t]*/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")
            // HTML-комментарии Thymeleaf-шаблонов — тоже пояснения к правке,
            // а не код (иначе проверка «в шаблоне нет `songAuthors`» ловила бы себя).
            .replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), " ")

    private val tpl = "karaoke-app/src/main/resources/templates/"

    // ---------------------------------------------------------------- BR-202

    @Test
    fun `BR-202 datalist авторов на главной читает модель контроллера`() {
        val main = readCode("$tpl/main.html")
        assertFalse(main.contains("songAuthors"), "songAuthors в модели нет (BR-202)")
        val authorsLoop = "th:each=\"author:" + '$' + "{authors}\""
        assertTrue(main.contains(authorsLoop), "нужен список authors (BR-202)")
    }

    // ---------------------------------------------------------------- BR-203

    @Test
    fun `BR-203 пользовательский текст экранируется перед вставкой`() {
        val subs = readCode("$tpl/editsubs.html")
        assertTrue(subs.contains("function escapeHtmlText("), "нужен экранировщик (BR-203)")
        assertTrue(
            subs.contains("escapeHtmlText(currMarker.label"),
            "label маркера должен экранироваться (BR-203)",
        )
        assertFalse(
            subs.contains("""document.getElementById("next_syllable_text").innerHTML = getTailText();"""),
            "хвост текста вставляется без экранирования (BR-203)",
        )
        assertFalse(
            subs.contains("sourceTextElement.innerHTML = sourceTextValue"),
            "текст песни в textarea ставится через .value (BR-203)",
        )
    }

    // ---------------------------------------------------------------- BR-204

    @Test
    fun `BR-204 скорость редактора берётся у активной кнопки`() {
        val subs = readCode("$tpl/editsubs.html")
        assertTrue(
            subs.contains("btn_speed_edit") && subs.contains("classList.contains('active')"),
            "признак выбора — класс active (BR-204)",
        )
        assertFalse(
            subs.contains("if (button.checked)"),
            "несуществующее свойство checked у <button> больше не используется (BR-204)",
        )
    }

    // ---------------------------------------------------------------- BR-207

    @Test
    fun `BR-207 сохранение песни проверяет строку и отчёт об ошибке`() {
        val songs = readCode("$tpl/songs.html")
        val body = songs.substringAfter("function doPostForm(").substringBefore("function doFilterForm(")
        assertTrue(body.contains("if (!id || !currentRow)"), "нужна проверка выбранной строки (BR-207)")
        assertTrue(body.contains("error: function"), "нужен обработчик ошибки (BR-207)")
        assertTrue(
            body.indexOf("success: function()") < body.indexOf("updateTableRowFromFormElements()"),
            "DOM меняется только после 2xx (BR-207)",
        )
        assertTrue(
            songs.contains("if (!currentRow) return;"),
            "обновление строки без выбранной строки не должно падать (BR-207)",
        )
    }

    // ---------------------------------------------------------------- BR-208

    @Test
    fun `BR-208 удаление песни не трогает выбранную заново строку`() {
        val songs = readCode("$tpl/songs.html")
        val body = songs.substringAfter("function doDelete()").substringBefore("function doSymlink(")
        assertTrue(body.contains("var row = currentRow;"), "строка запоминается до запроса (BR-208)")
        assertTrue(body.contains("row.remove()"), "удаляется именно запомненная строка (BR-208)")
        assertFalse(body.contains("currentRow.remove()"), "глобальный currentRow больше не удаляется (BR-208)")
        assertTrue(body.contains("error: function"), "нужен обработчик ошибки (BR-208)")
        val controller = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt")
        assertTrue(
            controller.contains("if (song == null) return 0"),
            "сервер возвращает число удалённых строк (BR-208)",
        )
    }

    // ---------------------------------------------------------------- BR-209

    @Test
    fun `BR-209 datalist авторов и альбомов на странице песен читают модель`() {
        val songs = readCode("$tpl/songs.html")
        assertFalse(songs.contains("songAuthors"), "songAuthors в модели нет (BR-209)")
        assertFalse(songs.contains("songAlbums"), "songAlbums в модели нет (BR-209)")
        val authorsLoop = "th:each=\"author:" + '$' + "{authors}\""
        assertTrue(songs.contains(authorsLoop), "нужен список authors (BR-209)")
        val albumsLoop = "th:each=\"album:" + '$' + "{albums}\""
        assertTrue(songs.contains(albumsLoop), "нужен список albums (BR-209)")
    }

    // ---------------------------------------------------------------- BR-210

    @Test
    fun `BR-210 telegram-фильтры попадают в URL страницы песен`() {
        val songs = readCode("$tpl/songs.html")
        listOf("filter_flag_tl", "filter_flag_tk", "filter_flag_tc").forEach { id ->
            assertTrue(
                songs.contains("""document.getElementById("$id").value"""),
                "$id должен читаться из формы (BR-210)",
            )
        }
        listOf("flag_telegram_lyrics", "flag_telegram_karaoke", "flag_telegram_chords").forEach { key ->
            assertTrue(
                songs.contains("\"&$key=\" +"),
                "$key должен попадать в URL (BR-210)",
            )
        }
    }

    // ---------------------------------------------------------------- BR-211

    @Test
    fun `BR-211 живое обновление идёт по SSE и читает верные ключи`() {
        val songs = readCode("$tpl/songs.html")
        assertFalse(songs.contains("new SockJS('/api/message')"), "мёртвый STOMP-эндпоинт убран (BR-211)")
        assertTrue(songs.contains("new EventSource('/api/subscribe"), "нужен SSE-канал (BR-211)")
        assertTrue(songs.contains("recordchange.recordId"), "ключ recordId (BR-211)")
        assertTrue(songs.contains("recordchange.tableName"), "ключ tableName (BR-211)")
        assertTrue(songs.contains("recordchange.diffs"), "ключ diffs (BR-211)")
        assertTrue(songs.contains("'tbl_songs'"), "имя таблицы песен — tbl_songs (BR-211)")
        assertFalse(songs.contains("'tbl_settings'"), "старое имя таблицы убрано (BR-211)")
    }

    // ---------------------------------------------------------------- BR-212

    @Test
    fun `BR-212 копирование работает без Clipboard API`() {
        val songs = readCode("$tpl/songs.html")
        assertTrue(songs.contains("function copyToClipboardSafely("), "нужен fallback (BR-212)")
        assertTrue(songs.contains("copyToClipboard("), "используется существующий copyToClipboard (BR-212)")
        assertFalse(
            songs.contains("navigator.clipboard.writeText(el_"),
            "прямые вызовы Clipboard API убраны (BR-212)",
        )
    }

    // ---------------------------------------------------------------- BR-215

    @Test
    fun `BR-215 telegram-фильтры попадают в URL страницы songs2`() {
        val songs2 = readCode("$tpl/songs2.html")
        listOf("filter_flag_tl", "filter_flag_tk", "filter_flag_tc").forEach { id ->
            assertTrue(
                songs2.contains("""document.getElementById("$id").value"""),
                "$id должен читаться из формы (BR-215)",
            )
        }
        listOf("flag_telegram_lyrics", "flag_telegram_karaoke", "flag_telegram_chords").forEach { key ->
            assertTrue(
                songs2.contains("\"&$key=\" +"),
                "$key должен попадать в URL (BR-215)",
            )
        }
    }
}
