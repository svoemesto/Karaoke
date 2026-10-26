package com.svoemesto.karaokeapp.audit

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Регрессия: SQL-блок с висячей запятой перед `)` — PostgreSQL такой запрос не парсит.
 *
 * **Как вышло.** Пакет `PKG-S3-23` (BR-156) переписал выборку кандидатов air-новостей
 * в `VkAutoPublishScheduler.loadNewsCandidates` на один SQL с JOIN и LIMIT. В `COALESCE(...)`
 * осталась запятая после последнего аргумента — PostgreSQL запятую перед закрывающей скобкой
 * не принимает, и КАЖДЫЙ тик планировщика падал с `PSQLException: syntax error at or near ")"`.
 *
 * **Почему это не было замечено.** Сбой ловился только предупреждением в лог
 * (`sqlState=42601`), а поведение «кандидатов нет» и «не смогли прочитать» выглядят
 * одинаково — автопубликация в VK просто молча не работала. Тесты на этот SQL не смотрели.
 *
 * **Что проверяем.**
 *  1. Точечно — в SQL кандидатов air-новостей нет висячей запяты (и он по-прежнему
 *     берёт кандидатов одним JOIN+`-LIMIT` запросом — смысл BR-156 не потерян).
 *  2. Сквозным правилом — во ВСЕХ SQL-блоках прикладного кода (karaoke-app и karaoke-web)
 *     нет запятой перед закрывающей скобкой. Кусочки `${...}` (Kotlin-подстановки) из
 *     проверки исключены: там запятая — обычный аргумент Kotlin-вызова, а не SQL.
 *
 * Проверка работает по исходнику (как `PkgS210SqlShapeTest`): парсить SQL в тесте нечем,
 * а дефект — синтаксический, и ловится текстом.
 */
class SqlTrailingCommaTest {
    private fun sourceOf(relativePath: String): String {
        val file = TestRepoRoot.file(relativePath)
        assertTrue(file.isFile, "не найден исходник: $relativePath")
        return file.readText(Charsets.UTF_8)
    }

    /** Начало SQL-блока: SELECT/INSERT/UPDATE/DELETE/WITH (регистр не важен). */
    private val sqlStartRegex = Regex("(?i)^(SELECT|INSERT|UPDATE|DELETE|WITH)\\b", RegexOption.DOT_MATCHES_ALL)

    /** SQL-блоки в исходнике: тройные кавычки. Kotlin-подстановки `${...}` вырезаются. */
    private fun sqlBlocks(source: String): List<String> =
        Regex("\"\"\"(.*?)\"\"\"", RegexOption.DOT_MATCHES_ALL)
            .findAll(source)
            .map { it.groupValues[1] }
            .filter { sqlStartRegex.containsMatchIn(it.trimStart()) }
            .map { block -> block.replace(Regex("\\$\\{[^}]*\\}"), " ") }
            .toList()

    @Test
    fun `в SQL кандидатов air-новостей нет висячей запятой`() {
        val source = sourceOf("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishScheduler.kt")
        val candidates =
            sqlBlocks(source).filter { it.contains("tbl_news") && it.contains("LEFT JOIN tbl_songs") }
        assertTrue(candidates.isNotEmpty(), "не найден SQL выборки кандидатов air-новостей")

        val broken = candidates.filter { Regex(",\\s*\\)").containsMatchIn(it) }
        assertTrue(
            broken.isEmpty(),
            "SQL кандидатов air-новостей содержит запятую перед ')' — PostgreSQL такой запрос не парсит:\n${broken.joinToString("\n")}",
        )
    }

    @Test
    fun `SQL кандидатов air-новостей сохраняет JOIN и LIMIT из BR-156`() {
        val source = sourceOf("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishScheduler.kt")
        val sql = sqlBlocks(source).first { it.contains("tbl_news") && it.contains("LEFT JOIN tbl_songs") }
        assertTrue(sql.contains("LEFT JOIN tbl_songs"), "кандидаты должны браться одним JOIN с tbl_songs")
        assertTrue(sql.contains("LIMIT"), "выборка кандидатов должна быть ограничена LIMIT")
        assertTrue(sql.contains("id_vk"), "связанная песня подтягивается по id_vk")
    }

    @Test
    fun `во всех SQL-блоках прикладного кода нет запятой перед закрывающей скобкой`() {
        val roots = listOf("karaoke-app/src/main/kotlin", "karaoke-web/src/main/kotlin")
        val offenders = mutableListOf<String>()
        for (root in roots) {
            val dir = TestRepoRoot.file(root)
            assertTrue(dir.isDirectory, "не найден каталог исходников: $root")
            dir.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.forEach { file ->
                for (block in sqlBlocks(file.readText(Charsets.UTF_8))) {
                    if (Regex(",\\s*\\)").containsMatchIn(block)) {
                        offenders += "${file.path}: ${block.trim().lines().firstOrNull()?.take(90)}"
                    }
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "в SQL-блоках найдена запятая перед ')' — PostgreSQL такой запрос не парсит:\n${offenders.joinToString("\n")}",
        )
    }
}
