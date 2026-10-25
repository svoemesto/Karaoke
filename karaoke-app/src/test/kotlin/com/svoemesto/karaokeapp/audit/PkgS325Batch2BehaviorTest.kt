package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.htmlToPlainTextWithNewlines
import com.svoemesto.karaokeapp.mlt.mko.emptyKdenliveTitle
import com.svoemesto.karaokeapp.shellEscape
import com.svoemesto.karaokeapp.buildHostScript
import com.svoemesto.karaokeapp.StemJobCleanup
import com.svoemesto.karaokeapp.listKaraokeProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Регрессия пакета `PKG-S3-25` (аудит #336, OpenProject #397), партии 2–3 —
 * ПОВЕДЕНЧЕСКИЕ проверки чистых функций.
 *
 * Эти функции добавлены вместе с правкой — на коде «до» они не существуют,
 * поэтому сам факт компиляции теста доказывает «до/после».
 *
 * @see specs/397-pkg-s3-25/report.md
 */
class PkgS325Batch2BehaviorTest {
    // ---------------------------------------------------------------- BR-257

    @Test
    fun `BR-257 HTML-фрагмент превращается в текст без разметки`() {
        val html = "<div class=\"song\">Первый куплет<br>Второй куплет</div>"
        val text = htmlToPlainTextWithNewlines(html)
        assertFalse(text.contains("<"), "BR-257: в тексте песни не должно быть тегов: $text")
        assertFalse(text.contains(">"), "BR-257: в тексте песни не должно быть тегов: $text")
        assertFalse(text.contains("class="), "BR-257: в тексте песни не должно быть атрибутов: $text")
        assertTrue(text.contains("Первый куплет"), "BR-257: текст должен сохраниться: $text")
        assertTrue(text.contains("Второй куплет"), "BR-257: текст должен сохраниться: $text")
    }

    @Test
    fun `BR-257 переводы строк сохраняются, а блочные теги дают перенос`() {
        val text = htmlToPlainTextWithNewlines("<p>Строка один</p><p>Строка два</p>")
        assertTrue(text.contains("\n"), "BR-257: блочные теги должны давать перевод строки: $text")
        assertTrue(text.contains("Строка один"), "BR-257: первый абзац: $text")
        assertTrue(text.contains("Строка два"), "BR-257: второй абзац: $text")
    }

    @Test
    fun `BR-257 script-теги не попадают в текст песни`() {
        val text = htmlToPlainTextWithNewlines("<div>Текст<script>alert(1)</script></div>")
        assertFalse(text.contains("script"), "BR-257: содержимое script не должно попасть в текст: $text")
        assertFalse(text.contains("alert"), "BR-257: содержимое script не должно попасть в текст: $text")
        assertTrue(text.contains("Текст"), "BR-257: обычный текст сохраняется: $text")
    }

    // ---------------------------------------------------------------- BR-271

    @Test
    fun `BR-271 апостроф в значении экранируется`() {
        assertEquals("'Guns N'\\'' Roses'", shellEscape("Guns N' Roses"))
        assertEquals("''", shellEscape(""))
        assertEquals("'обычный'", shellEscape("обычный"))
    }

    @Test
    fun `BR-271 скрипт передаёт значения позиционными параметрами`() {
        val script = buildHostScript(listOf("smplayer", "/music/Guns N' Roses/song.video"))
        assertTrue(script.contains("set --"), "BR-271: значения должны передаваться позиционными параметрами: $script")
        assertTrue(script.contains("exec 'smplayer' \"\$@\""), "BR-271: команда вызывается с \"\$@\": $script")
        // Значение НЕ склеено с командой — оно отдельный экранированный аргумент.
        assertFalse(
            script.contains("smplayer '/music/Guns"),
            "BR-271: значение не должно склеиваться с командой: $script",
        )
        assertTrue(script.contains("'\\''"), "BR-271: апостроф внутри значения должен быть экранирован: $script")
    }

    @Test
    fun `BR-271 команда без аргументов не получает set`() {
        val script = buildHostScript(listOf("smplayer"))
        assertFalse(script.contains("set --"), "BR-271: без аргументов `set --` не нужен: $script")
        assertTrue(script.contains("exec 'smplayer'"), "BR-271: команда вызывается: $script")
    }

    // ---------------------------------------------------------------- BR-259

    @Test
    fun `BR-259 описание шага CPU-повтора относится к своему заданию`() {
        assertTrue(
            StemJobCleanup.isStemJobDescription("Демукс премиум-задания #42", 42L),
            "BR-259: основной шаг должен совпадать",
        )
        assertTrue(
            StemJobCleanup.isStemJobDescription("Демукс премиум-задания #42 (повтор без GPU)", 42L),
            "BR-259: CPU-повтор должен совпадать по префиксу",
        )
        assertFalse(
            StemJobCleanup.isStemJobDescription("Демукс премиум-задания #420", 42L),
            "BR-259: чужое задание #420 не должно совпадать с #42",
        )
        assertFalse(
            StemJobCleanup.isStemJobDescription("Демукс премиум-задания #43", 42L),
            "BR-259: другое задание не должно совпадать",
        )
        assertFalse(
            StemJobCleanup.isStemJobDescription(null, 42L),
            "BR-259: null-описание не должно совпадать",
        )
    }

    // ---------------------------------------------------------------- BR-241 (повторно, чистая функция)

    @Test
    fun `BR-241 пустой титр — валидный XML`() {
        val node = emptyKdenliveTitle()
        assertEquals("kdenlivetitle", node.name)
        assertNotNull(node.toString())
        assertTrue(node.toString().contains("</kdenlivetitle>"))
    }

    // ---------------------------------------------------------------- BR-246 (регистрация ключей)

    @Test
    fun `BR-246 ключи karaoke-web зарегистрированы с дефолтами из спецификаций`() {
        val byKey = listKaraokeProperties.associateBy { it.key }
        assertNotNull(
            byKey["karaoke.public.public-settings-cache.enabled"],
            "BR-246: ключ публичного кеша настроек должен быть зарегистрирован",
        )
        assertEquals(
            true,
            byKey["karaoke.public.public-settings-cache.enabled"]!!.defaultValue,
            "BR-246: дефолт публичного кеша — true (specs/249 FR-003)",
        )
        assertNotNull(byKey["karaoke.web.events.batch-enabled"], "BR-246: ключ батчинга должен быть зарегистрирован")
        assertEquals(
            false,
            byKey["karaoke.web.events.batch-enabled"]!!.defaultValue,
            "BR-246: дефолт батчинга — false (opt-in, specs/274 FR-002)",
        )
        assertNotNull(
            byKey["karaoke.web.events.batch-max-buffer-size"],
            "BR-246: ключ размера буфера должен быть зарегистрирован",
        )
        assertEquals(
            500,
            byKey["karaoke.web.events.batch-max-buffer-size"]!!.defaultValue,
            "BR-246: дефолт размера буфера — 500 (specs/274)",
        )
    }

    // ---------------------------------------------------------------- BR-250 (построчный разбор)

    @Test
    fun `BR-250 битая строка файла свойств не отбрасывает остальные`() {
        val dir =
            java.nio.file.Files
                .createTempDirectory("karaoke-props-tolerant")
                .toFile()
        val file = java.io.File(dir, "Karaoke.properties")
        val good1 = com.svoemesto.karaokeapp.encodePropertiesMap(mapOf("checkSearchAsync" to false))
        val good2 = com.svoemesto.karaokeapp.encodePropertiesMap(mapOf("lyricsSearchMinResults" to 7))
        file.writeText("$good1\nЭТО НЕ BASE64\n$good2\n")
        val parsed = com.svoemesto.karaokeapp.readPropertiesFileTolerant(file)
        assertEquals(1, parsed.brokenCount, "BR-250: битая строка должна быть посчитана: ${parsed.problems}")
        assertEquals(2, parsed.values.size, "BR-250: рабочие строки должны быть загружены: ${parsed.values}")
        assertEquals(false, parsed.values["checkSearchAsync"])
        assertEquals(7, parsed.values["lyricsSearchMinResults"])
    }

    // ---------------------------------------------------------------- BR-274 (валидация соединения)

    @Test
    fun `BR-274 интервал валидации соединения задан`() {
        assertTrue(
            com.svoemesto.karaokeapp.CONNECTION_VALIDATION_IDLE_MS in 1..(60 * 60 * 1000),
            "BR-274: интервал валидации должен быть разумным (минуты-час)",
        )
    }

    // ---------------------------------------------------------------- BR-270 (таймаут runCommand)

    @Test
    fun `BR-270 у runCommand есть таймаут и лимит вывода`() {
        assertTrue(
            com.svoemesto.karaokeapp.DEFAULT_RUN_COMMAND_TIMEOUT_MS > 0,
            "BR-270: таймаут по умолчанию должен быть задан",
        )
        assertTrue(
            com.svoemesto.karaokeapp.MAX_RUN_COMMAND_OUTPUT_CHARS > 0,
            "BR-270: лимит накопления вывода должен быть задан",
        )
    }
}
