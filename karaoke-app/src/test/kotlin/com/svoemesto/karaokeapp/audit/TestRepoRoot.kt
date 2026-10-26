package com.svoemesto.karaokeapp.audit

import java.io.File

/**
 * Корень репозитория для тестов, которые читают исходники.
 *
 * Рабочий каталог Gradle-задачи тестов — каталог модуля, а не корень репозитория,
 * поэтому пути вида `karaoke-app/src/main/kotlin/...` оттуда не резолвятся. Ищем
 * корень вверх по дереву — признак `settings.gradle.kts`.
 *
 * Вынесено отдельным объектом, чтобы несколько тестов искали корень одинаково
 * (R-48: правило, повторяющееся в компонентах, живёт в общем модуле).
 */
internal object TestRepoRoot {
    /** Каталог с `settings.gradle.kts`. */
    fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        return dir
    }

    /** Файл по пути относительно корня репозитория. */
    fun file(relativePath: String): File = File(repoRoot(), relativePath)
}
