package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import com.svoemesto.karaokeapp.services.APP_WORK_ON_SERVER
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_PASSWORD
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_USER
import com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_PASSWORD
import com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_USER
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Ручная интеграционная проверка: ходит в живой VK и поднимает браузер через Playwright.
 *
 * `@Disabled` — по конвенции проекта для таких тестов (см. `PlaywrightTests`, где так
 * помечены все): DEVELOPMENT.md прямо относит эти тесты к «manual integration checks
 * that hit live network services / require a real browser and credentials — not a CI
 * safe-guard suite». Без пометки `./gradlew karaoke-app:test` падает на любой машине,
 * где не установлен драйвер Playwright, и это приучает игнорировать падения тестов.
 *
 * Для ручного прогона пометку снять (или запустить тест из IDE) при подготовленном
 * окружении: браузеры Playwright + доступ в сеть.
 */
class SearchLastAlbumVkTest {
    @Disabled
    @Test
    fun searchLastAlbumVk() {
        APP_WORK_ON_SERVER = false
        APP_WORK_IN_CONTAINER = false
        DB_LOCAL_POSTGRES_USER = ""
        DB_LOCAL_POSTGRES_PASSWORD = ""
        DB_SERVER_POSTGRES_USER = ""
        DB_SERVER_POSTGRES_PASSWORD = ""

        val vkId = "piknik"
        val result = searchLastAlbumVk(vkId)
        println(result)
    }
}
