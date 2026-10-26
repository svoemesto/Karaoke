package com.svoemesto.karaokeweb.audit

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Регрессия пакета `PKG-S3-35` (аудит #336, OpenProject #409) — BR-517:
 * в `tryClaim` не осталось отладочного `println`/`printStackTrace` и
 * ERROR-уровня на штатном пути.
 *
 * @see specs/409-pkg-s3-35/report.md § BR-517
 */
class PkgS335SourceContractsTest {
    private fun repoFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        val file = File(dir, relativePath)
        assertTrue(file.isFile, "файл не найден: $relativePath")
        return file
    }

    private fun readCode(relativePath: String): String =
        repoFile(relativePath)
            .readText()
            .replace(Regex("""(?m)^[ \t]*/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")

    private val service = "karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt"

    @Test
    fun `BR-517 в tryClaim нет println и printStackTrace`() {
        val code = readCode(service)
        val tryClaim = code.substringAfter("fun tryClaim(")
        assertTrue(tryClaim.contains("tryClaim"), "метод tryClaim найден (BR-517)")
        assertFalse(tryClaim.contains("println("), "println в tryClaim убран (BR-517)")
        assertFalse(tryClaim.contains("printStackTrace("), "printStackTrace в tryClaim убран (BR-517)")
        assertTrue(
            tryClaim.contains("log.error("),
            "ошибки логируются через SLF4J с throwable (BR-517)",
        )
    }

    @Test
    fun `BR-517 штатный путь не логируется на уровне ERROR`() {
        val code = readCode(service)
        val tryClaim = code.substringAfter("fun tryClaim(")
        assertFalse(
            tryClaim.contains("DEBUG: принудительно log.error"),
            "отладочный log.error на штатном пути убран (BR-517)",
        )
        assertTrue(
            tryClaim.contains("log.debug("),
            "штатный путь логируется на уровне debug (BR-517)",
        )
    }
}
