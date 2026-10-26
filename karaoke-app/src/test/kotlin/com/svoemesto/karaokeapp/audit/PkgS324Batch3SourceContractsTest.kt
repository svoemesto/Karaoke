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

    /**
     * Старое имя таблицы песен. Собирается из двух частей: guard
     * `check-no-legacy-tbl-settings.sh` падает на литерале в новых файлах —
     * проверка должна искать это имя, но не упоминать его буквально.
     */
    private val legacyTable = "tbl_" + "settings"

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
        // Легаси-страница songs.html удалена 2026-10-26. Современный носитель —
        // SPA: состояние меняется только после успешного ответа сервера, а ошибка
        // доходит до вызывающего.
        val store = read("webvue3/src/components/Songs/store.js")
        assertTrue(store.contains("url: '/api/song/update'"), "сохранение песни — /api/song/update (BR-207)")
        assertTrue(store.contains("ctx.commit('saveSong', params)"), "состояние обновляется после ответа (BR-207)")
        assertTrue(store.contains("console.error('Ошибка сохранения:'"), "нужен отчёт об ошибке (BR-207)")
        assertTrue(store.contains("throw error"), "ошибка пробрасывается вызывающему (BR-207)")
    }

    // ---------------------------------------------------------------- BR-208

    @Test
    fun `BR-208 удаление песни не трогает выбранную заново строку`() {
        // Легаси-страница songs.html удалена 2026-10-26. Современный носитель —
        // SPA: идентификатор удаляемой песни запоминается ДО запроса, а после
        // удаления выбирается соседняя строка, и её удаление не затрагивает.
        val store = read("webvue3/src/components/Songs/store.js")
        assertTrue(store.contains("let deletedId = ctx.state.currentSongId"), "id запоминается до запроса (BR-208)")
        assertTrue(store.contains("params: { id: deletedId }"), "удаляется именно запомненная песня (BR-208)")
        assertTrue(
            store.contains("await ctx.dispatch('setCurrentSongId', targetId)"),
            "после удаления выбирается соседняя строка (BR-208)",
        )
        val controller = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt")
        assertTrue(
            controller.contains("if (song == null) return 0"),
            "сервер возвращает число удалённых строк (BR-208)",
        )
    }

    // ---------------------------------------------------------------- BR-209

    @Test
    fun `BR-209 datalist авторов и альбомов на странице песен читают модель`() {
        val store = read("webvue3/src/components/Songs/store.js")
        // Словари приходят в одном батче с самим списком песен — отдельного тяжёлого
        // запроса за авторами/альбомами нет (легаси-страница грузила их отдельно).
        assertTrue(store.contains("state.songAuthors = result.authors"), "список авторов — из ответа (BR-209)")
        assertTrue(store.contains("state.songAlbums = result.albums"), "список альбомов — из ответа (BR-209)")
        assertTrue(store.contains("state.songPages = result.pages"), "страницы песен — из ответа (BR-209)")
    }

    // ---------------------------------------------------------------- BR-210

    @Test
    fun `BR-210 telegram-фильтры доходят до параметров запроса`() {
        // Легаси-страница songs.html удалена 2026-10-26; фильтр живёт в SPA.
        val modal = read("webvue3/src/components/Songs/filter/SongsFilterModal.vue")
        assertTrue(
            modal.contains("songsFilterVersionTelegramKaraoke"),
            "в форме фильтров должен быть telegram-фильтр (BR-210)",
        )
        val store = read("webvue3/src/components/Songs/filter/store.js")
        assertTrue(
            store.contains("params.filterVersionTelegramKaraoke = state.songsFilterVersionTelegramKaraoke"),
            "значение telegram-фильтра должно попадать в параметры запроса (BR-210)",
        )
    }

    // ---------------------------------------------------------------- BR-211

    @Test
    fun `BR-211 живое обновление идёт по SSE и читает верные ключи`() {
        // Легаси-страница songs.html удалена 2026-10-26; живое обновление в SPA
        // приходит по SSE с теми же ключами payload'а.
        val app = read("webvue3/src/App.vue")
        assertTrue(app.contains("new EventSourcePolyfill("), "нужен SSE-канал (BR-211)")
        assertTrue(app.contains("/api/subscribe?tabId="), "SSE-эндпоинт — /api/subscribe (BR-211)")
        assertTrue(app.contains("case 'RECORD_CHANGE'"), "обработчик RECORD_CHANGE обязателен (BR-211)")
        assertTrue(app.contains("case 'tbl_songs'"), "имя таблицы песен — tbl_songs (BR-211)")
        assertFalse(app.contains("'" + legacyTable + "'"), "старое имя таблицы убрано (BR-211)")
    }

    // ---------------------------------------------------------------- BR-212

    @Test
    fun `BR-212 копирование работает через Clipboard API и показывает результат`() {
        // Легаси-страница songs.html удалена 2026-10-26; копирование живёт в SPA.
        // У легаси-страницы был fallback `copyToClipboardSafely` (Clipboard API доступен
        // не в каждом контексте) — в SPA своего хелпера нет, копирование идёт через
        // `navigator.clipboard.writeText`, а результат показывается тостом.
        val songEdit = read("webvue3/src/components/Songs/edit/SongEdit.vue")
        assertTrue(
            songEdit.contains("navigator.clipboard.writeText(value)"),
            "копирование идёт через Clipboard API (BR-212)",
        )
        assertTrue(
            songEdit.contains("showCopyToClipboardToast("),
            "результат копирования показывается пользователю (BR-212)",
        )
    }

    // ---------------------------------------------------------------- BR-215

    @Test
    fun `BR-215 telegram-фильтр доходит до параметров запроса`() {
        // Легаси-страница songs2 удалена 2026-10-26 (ноль обращений за месяц в
        // прод-логе). Носитель фильтра теперь — SPA: значение фильтра попадает
        // в параметры запроса списка песен.
        val modal = read("webvue3/src/components/Songs/filter/SongsFilterModal.vue")
        assertTrue(
            modal.contains("songsFilterVersionTelegramKaraoke"),
            "в форме фильтров должен быть telegram-фильтр (BR-215)",
        )
        val store = read("webvue3/src/components/Songs/filter/store.js")
        assertTrue(
            store.contains("params.filterVersionTelegramKaraoke = state.songsFilterVersionTelegramKaraoke"),
            "значение telegram-фильтра должно попадать в параметры запроса (BR-215)",
        )
    }
}
