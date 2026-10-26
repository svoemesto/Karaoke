package com.svoemesto.karaokeweb.util

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Корень репозитория для тестов, читающих исходники.
 *
 * Рабочий каталог Gradle-задачи тестов — каталог модуля, а не корень репозитория,
 * поэтому пути вида `karaoke-web/src/main/kotlin/...` оттуда не резолвятся. Ищем
 * корень вверх по дереву — признак `settings.gradle.kts`.
 *
 * Вынесено отдельным объектом, чтобы несколько тестов искали корень одинаково
 * (R-48: правило, повторяющееся в компонентах, живёт в общем модуле).
 */
object TestRepoRoot {
    /** Каталог с `settings.gradle.kts`. */
    fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        return dir
    }

    /** Файл по пути относительно корня репозитория. */
    fun file(relativePath: String): File {
        val file = File(repoRoot(), relativePath)
        assertTrue(file.isFile, "файл не найден: $relativePath")
        return file
    }
}
