package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.Crypto
import com.svoemesto.karaokeapp.CryptoConfigurationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ключ шифрования служебных полей (`sqlToInsert`/`sqlToDelete`, кодовое слово) —
 * **только** из окружения, без hardcoded-значений в коде (ротация завершена 2026-10-26).
 *
 * **Что защищаем.**
 *  1. Fail-fast: без `CRYPTO_AES_KEY`/`CRYPTO_AES_IV` сервис не должен работать
 *     «наполовину» — раньше был тихий откат на ключ, зашитый в репозиторий, и
 *     шифрование на одном хосте разъезжалось с расшифровкой на другом молча.
 *  2. Секрет не возвращается в код: значения ключа не должны появляться ни
 *     в одном исходнике репозитория.
 */
class CryptoKeyConfigurationTest {
    @Test
    fun `без переменной окружения — исключение конфигурации, а не молчаливый откат`() {
        val error =
            assertThrows(CryptoConfigurationException::class.java) {
                Crypto.resolveOrThrow("CRYPTO_AES_KEY", null)
            }
        assertTrue(error.message!!.contains("CRYPTO_AES_KEY"), "в сообщении должно быть имя переменной: ${error.message}")
        assertTrue(
            error.message!!.contains("crypto-key-rotation.md"),
            "в сообщении должна быть ссылка на процедуру: ${error.message}",
        )
    }

    @Test
    fun `пустая переменная окружения — тоже исключение`() {
        assertThrows(CryptoConfigurationException::class.java) { Crypto.resolveOrThrow("CRYPTO_AES_IV", "") }
        assertThrows(CryptoConfigurationException::class.java) { Crypto.resolveOrThrow("CRYPTO_AES_IV", "   ") }
    }

    @Test
    fun `заданное значение возвращается как есть`() {
        assertEquals("0123456789abcdef", Crypto.resolveOrThrow("CRYPTO_AES_KEY", "0123456789abcdef"))
    }

    @Test
    fun `в исходниках нет значения ключа`() {
        // Проверка без зашитых в тест секретов: ищем ЛИТЕРАЛ, присвоенный
        // crypto-переменной, и любые legacy-имена констант ключа. Сами значения
        // ключа в репозитории лежать не должны — ни старые, ни новые.
        // Только production-код (src/main): в тестах строки-паттерны неизбежны,
        // а секрет в production-коде — то, что реально утекает в сборку.
        val roots = listOf("karaoke-app/src/main", "karaoke-web/src/main", "karaoke-public/src")
        val literalAssignment = Regex("CRYPTO_AES_(KEY|IV)\\s*=\\s*[\"'][^\"']+[\"']")
        val legacyNames = Regex("\\bLEGACY_(KEY|INIT_VECTOR)\\b")
        val offenders = mutableListOf<String>()
        for (root in roots) {
            val dir = TestRepoRoot.file(root)
            if (!dir.isDirectory) continue
            dir.walkTopDown().filter { it.isFile }.forEach { file ->
                val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return@forEach
                if (literalAssignment.containsMatchIn(text)) offenders += "${file.path}: литерал значения crypto-ключа"
                if (legacyNames.containsMatchIn(text)) offenders += "${file.path}: legacy-константа ключа"
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "в исходниках появилось значение ключа:\n${offenders.joinToString("\n")}",
        )
    }

    @Test
    fun `в исходнике Crypto нет констант с ключом`() {
        val cryptoSource = TestRepoRoot.file("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Crypto.kt")
        assertTrue(cryptoSource.isFile, "не найден Crypto.kt")
        val text = cryptoSource.readText(Charsets.UTF_8)
        assertTrue(
            !text.contains("LEGACY_KEY") && !text.contains("LEGACY_INIT_VECTOR"),
            "в Crypto.kt не должно быть legacy-констант ключа",
        )
        assertTrue(
            !Regex("CRYPTO_AES_KEY\\s*=\\s*[\"'][^\"']+[\"']").containsMatchIn(text),
            "в Crypto.kt не должно быть строкового присваивания значения ключа",
        )
    }
}
