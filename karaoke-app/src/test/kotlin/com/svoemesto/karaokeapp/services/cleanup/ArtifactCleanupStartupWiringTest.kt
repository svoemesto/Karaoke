package com.svoemesto.karaokeapp.services.cleanup

import com.svoemesto.karaokeapp.controllers.CleanupController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Проверка порядка инициализации: бины чистки не должны трогать глобалы приложения
 * в конструкторе (specs/544-song-artifact-cleanup).
 *
 * **Прецедент (P0, 2026-10-16).** `ArtifactCleanupPlanner` вычислял корни обхода
 * значением по умолчанию в конструкторе (`roots = PROJECT_ROOT_FOLDERS`). Это тянуло
 * `Constants.<clinit>` → `Connection` → `APP_WORK_ON_SERVER`, который выставляет
 * `KaraokeAppService` позже по жизненному циклу Spring. Итог: `CleanupController`
 * не создавался, приложение падало на старте:
 * `IllegalStateException: Property APP_WORK_ON_SERVER should be initialized before get`.
 *
 * Контейнер при этом успешно собирался, ktlint и все doc-гейты были зелёными — дефект
 * виден только при старте приложения. Поэтому здесь фиксируется инвариант кода, а не
 * поведение отдельного метода.
 *
 * @see specs/544-song-artifact-cleanup/report.md
 */
class ArtifactCleanupStartupWiringTest {
    /**
     * Бин контроллера чистки конструируется без инициализации глобалов приложения.
     *
     * Если этот тест падает с `IllegalStateException: Property ... should be initialized
     * before get` или `ExceptionInInitializerError` — значит в конструктор снова просочилось
     * обращение к `Constants`/`Connection`/глобалам Spring-инициализации.
     */
    @Test
    fun `controller constructs without app globals`() {
        val controller = CleanupController()
        assertNotNull(controller)
    }

    /** Сервис с параметрами по умолчанию (планировщик + журнал) конструируется без глобалов. */
    @Test
    fun `service with defaults constructs without app globals`() {
        val service = ArtifactCleanupService()
        assertTrue(service.classes().isNotEmpty(), "реестр классов должен читаться без инициализации приложения")
    }

    /**
     * Корни обхода не вычисляются в конструкторе: поле остаётся пустым до первого `plan()`.
     *
     * Именно это гарантирует, что `PROJECT_ROOT_FOLDERS` (а с ним `Constants`) не тянется
     * при создании Spring-бина. Проверка детерминированная: не зависит от того, инициализировал
     * ли глобалы другой тест в том же JVM.
     */
    @Test
    fun `planner defers root resolution until first use`() {
        val planner = ArtifactCleanupPlanner()
        val field = ArtifactCleanupPlanner::class.java.getDeclaredField("roots")
        field.isAccessible = true
        assertNull(
            field.get(planner),
            "корни обхода должны вычисляться лениво (в effectiveRoots()), а не в конструкторе — " +
                "иначе падает старт приложения (P0 2026-10-16)",
        )
    }

    /** Явно переданные корни не подменяются ленивым значением по умолчанию. */
    @Test
    fun `explicit roots override stays honored`() {
        val planner = ArtifactCleanupPlanner(roots = listOf("/tmp/cleanup-test-roots"))
        val field = ArtifactCleanupPlanner::class.java.getDeclaredField("roots")
        field.isAccessible = true
        assertEquals(listOf("/tmp/cleanup-test-roots"), field.get(planner))
    }
}
