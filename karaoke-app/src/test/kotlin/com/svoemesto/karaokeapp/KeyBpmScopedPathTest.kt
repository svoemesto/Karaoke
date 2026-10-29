package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 674: пути key-bpm-кортежа — в scoped-каталоге.
 *
 * Регресс, поймавшийся на стенде: `mkdir`/`cp`/`docker -v` уже шли в
 * `<root>/<scope>/`, а `mv result_key_bpm_finder.json` читал `<root>/` —
 * контейнер писал результат в scope, `mv` его не находил, кортеж падал на
 * шаге 4 (`cannot stat`), key/bpm не применялись.
 */
class KeyBpmScopedPathTest {
    private val songSource = File("src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt")

    @Test
    fun `key-bpm result is moved from the scoped directory`() {
        val source = songSource.readText()
        assertTrue(
            source.contains("\$PATH_TO_TEMP_KEYBPMFINDER_FOLDER/\${TupleScope.TOKEN}/result_key_bpm_finder.json"),
            "mv результата key-bpm должен читать scoped-каталог",
        )
    }

    @Test
    fun `key-bpm result is not read from the shared root`() {
        val source = songSource.readText()
        assertFalse(
            source.contains("\"\$PATH_TO_TEMP_KEYBPMFINDER_FOLDER/result_key_bpm_finder.json\""),
            "mv результата key-bpm не должен читать общий корень",
        )
    }
}
