package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Разовая уборка: устаревшая копия — только если автор и год совпадают (KARAOKE-429).
 *
 * ИНЦИДЕНТ 2026-11-14 (замер после импорта): перечень разовой уборки предложил
 * удалить 19 ЧУЖИХ объектов. Причина — объекты разных исполнителей с одинаковым
 * названием альбома: «Призраки Завтра» есть у «Мумий Тролль», «Мумий Тролль, T-Fest»
 * и «Мумий Тролль feat. Скриптонит». Их объекты — РАЗНЫЕ записи, а не копии одной.
 *
 * Ключ объекта — `$автор/$год - $альбом/$имя песни`. Переименование **альбома** меняет
 * только часть пути после `$автор/$год - ` — такие объекты действительно устаревшие
 * копии (актуальный объект лежит в канонической папке). Поэтому сверять надо
 * автора и год, а не альбом.
 *
 * Офлайн: живая БД не нужна — проверяется чистая функция сверки.
 */
class OneTimeCleanupStaleCopyAuthorYearTest {
    @Test
    fun `тот же автор и год — устаревшая копия`() {
        // Переименование альбома: путь изменился, автор и год те же.
        assertTrue(
            SongOneTimeCleanupPlanner.isSameAuthorAndYear(
                author = "Мумий Тролль",
                year = "2016",
                oldPrefix = "Мумий Тролль/2016 - Призраки Завтра (старое название)",
            ),
            "переименование альбома оставляет объект устаревшей копией",
        )
    }

    @Test
    fun `другой автор при том же названии альбома — не копия`() {
        assertFalse(
            SongOneTimeCleanupPlanner.isSameAuthorAndYear(
                author = "Мумий Тролль feat. Скриптонит",
                year = "2016",
                oldPrefix = "Мумий Тролль/2016 - Призраки Завтра",
            ),
            "объект другого исполнителя с тем же названием альбома — не устаревшая копия",
        )
    }

    @Test
    fun `другой год при том же авторе — не копия`() {
        assertFalse(
            SongOneTimeCleanupPlanner.isSameAuthorAndYear(
                author = "Мумий Тролль",
                year = "2016",
                oldPrefix = "Мумий Тролль/2017 - Призраки Завтра",
            ),
            "объект того же автора, но другого года — не устаревшая копия",
        )
    }

    @Test
    fun `совпадение требует разделителя после года`() {
        // «Мумий Тролль/20161 - …» — другой год, не «2016».
        assertFalse(
            SongOneTimeCleanupPlanner.isSameAuthorAndYear(
                author = "Мумий Тролль",
                year = "2016",
                oldPrefix = "Мумий Тролль/20161 - Призраки Завтра",
            ),
            "год сравнивается целиком, а не по префиксу цифр",
        )
        assertFalse(
            SongOneTimeCleanupPlanner.isSameAuthorAndYear(
                author = "Мумий Тролль",
                year = "2016",
                oldPrefix = "Мумий Тролль/2016Призраки Завтра",
            ),
            "после года требуется разделитель « - »",
        )
    }

    @Test
    fun `пустой префикс не совпадает ни с одной песней`() {
        assertFalse(
            SongOneTimeCleanupPlanner.isSameAuthorAndYear(
                author = "Мумий Тролль",
                year = "2016",
                oldPrefix = "",
            ),
            "пустой путь не может быть путём живой песни",
        )
    }

    @Test
    fun `сверка автора и года применена и к живым, и к историческим песням`() {
        val planner =
            repoFile(
                "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongOneTimeCleanupPlanner.kt",
            ).readText()

        assertTrue(
            planner.contains(".filter { song -> sameAuthorAndYear(song, oldPrefix) }"),
            "объекты живых песен отбираются по автору и году",
        )
        assertTrue(
            planner.contains("if (!sameAuthorAndYear(historical, oldPrefix)) return emptyList()"),
            "объекты исторических песен тоже отбираются по автору и году",
        )
        assertTrue(
            planner.contains("val oldPrefix = key.substringBeforeLast('/', \"\")"),
            "префикс пути (автор/год - альбом) берётся из ключа объекта",
        )
    }

    private fun repoFile(relativePath: String): java.io.File {
        var dir = java.io.File(System.getProperty("user.dir")).absoluteFile
        while (!java.io.File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        val file = java.io.File(dir, relativePath)
        assertTrue(file.isFile, "файл не найден: $relativePath")
        return file
    }
}
