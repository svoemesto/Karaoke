package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import com.svoemesto.karaokeapp.services.APP_WORK_ON_SERVER
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_PASSWORD
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_USER
import com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_PASSWORD
import com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_USER
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

/**
 * Ручная интеграционная проверка: ходит в живой VK и поднимает браузер через Playwright.
 *
 * Живёт в source set `manualTest` и НЕ входит в `test`/`check`/`build`: сценарий
 * требует сети, браузера Playwright и доступа к VK. Раньше он лежал в общем
 * наборе `src/test` под `@Disabled` — но без единого утверждения и с затиранием
 * глобальных настроек БД, которые не восстанавливались: после снятия пометки
 * конфигурация БД менялась для всего остального test-JVM.
 *
 * Запуск (с подготовленным окружением — браузеры Playwright + сеть):
 * ```
 * ./gradlew :karaoke-app:manualTest --tests "com.svoemesto.karaokeapp.SearchLastAlbumVkTest"
 * ```
 *
 * @see specs/336-code-audit/findings/P-31.md (BR-587)
 */
class SearchLastAlbumVkTest {
    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    fun searchLastAlbumVk() {
        // Глобальные настройки приложения подменяются на время сценария и
        // восстанавливаются в finally — иначе они «протекают» в остальной JVM.
        val savedInContainer = readIfInitialized { APP_WORK_IN_CONTAINER }
        val savedOnServer = readIfInitialized { APP_WORK_ON_SERVER }
        val savedLocalUser = readIfInitialized { DB_LOCAL_POSTGRES_USER }
        val savedLocalPassword = readIfInitialized { DB_LOCAL_POSTGRES_PASSWORD }
        val savedServerUser = readIfInitialized { DB_SERVER_POSTGRES_USER }
        val savedServerPassword = readIfInitialized { DB_SERVER_POSTGRES_PASSWORD }
        try {
            APP_WORK_ON_SERVER = false
            APP_WORK_IN_CONTAINER = false
            DB_LOCAL_POSTGRES_USER = ""
            DB_LOCAL_POSTGRES_PASSWORD = ""
            DB_SERVER_POSTGRES_USER = ""
            DB_SERVER_POSTGRES_PASSWORD = ""

            val vkId = "piknik"
            val result = searchLastAlbumVk(vkId)

            // Прежний сценарий заканчивался на println(result) — без единого
            // утверждения, то есть не мог упасть ни при каком результате.
            assertNotNull(result, "поиск последнего альбома VK обязан вернуть результат")
            assertTrue(result.isNotBlank(), "результат поиска последнего альбома VK не должен быть пустым")
            println(result)
        } finally {
            savedInContainer?.let { APP_WORK_IN_CONTAINER = it }
            savedOnServer?.let { APP_WORK_ON_SERVER = it }
            savedLocalUser?.let { DB_LOCAL_POSTGRES_USER = it }
            savedLocalPassword?.let { DB_LOCAL_POSTGRES_PASSWORD = it }
            savedServerUser?.let { DB_SERVER_POSTGRES_USER = it }
            savedServerPassword?.let { DB_SERVER_POSTGRES_PASSWORD = it }
        }
    }
}

/** Значение глобальной настройки либо `null`, если она ещё не инициализирована. */
private fun <T> readIfInitialized(read: () -> T): T? =
    try {
        read()
    } catch (_: RuntimeException) {
        null
    }
