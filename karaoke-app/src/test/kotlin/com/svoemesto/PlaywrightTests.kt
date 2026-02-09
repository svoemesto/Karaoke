package com.svoemesto

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.LoadState
import com.svoemesto.karaokeapp.YANDEX_AUTH_STATE_PATH
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.nio.file.*
import kotlin.use

class PlaywrightTests() {

    @Disabled
    @Test
    fun saveYandexMusicAuthByChrome() {
        Playwright.create().use { playwright ->
            val browser = playwright.chromium().launch(
                BrowserType.LaunchOptions()
//                    .setExecutablePath(Path.of("/usr/bin/yandex-browser")) // Укажите путь к Яндекс.Браузеру
                    .setHeadless(false) // Оставляем видимым, чтобы вручную авторизоваться
            )

            // Создаем новый контекст браузера
            val context = browser.newContext()

            // Открываем новую страницу в этом контексте
            val page = context.newPage()

            // Переходим на сайт
            page.navigate("https://music.yandex.ru/")
            // Ждем, пока пользователь вручную авторизуется
            println("Пожалуйста, авторизуйтесь в браузере, затем нажмите Enter в консоли.")
            readLine() // Приостанавливаем выполнение, пока вы не авторизуетесь

            // После авторизации сохраняем состояние (cookies, localStorage и т.д.)
            context.storageState(BrowserContext.StorageStateOptions().setPath(Path.of(YANDEX_AUTH_STATE_PATH)))

            println("Состояние авторизации сохранено в '$YANDEX_AUTH_STATE_PATH'")
            browser.close()
        }
    }

    @Disabled
    @Test
    fun saveYandexMusicAuth() {
        Playwright.create().use { playwright ->
            val browser = playwright.chromium().launch(
                BrowserType.LaunchOptions()
                    .setExecutablePath(Path.of("/usr/bin/yandex-browser")) // Укажите путь к Яндекс.Браузеру
                    .setHeadless(false) // Оставляем видимым, чтобы вручную авторизоваться
            )

            // Создаем новый контекст браузера
            val context = browser.newContext()

            // Открываем новую страницу в этом контексте
            val page = context.newPage()

            // Переходим на сайт
            page.navigate("https://music.yandex.ru/")
            // Ждем, пока пользователь вручную авторизуется
            println("Пожалуйста, авторизуйтесь в браузере, затем нажмите Enter в консоли.")
            readLine() // Приостанавливаем выполнение, пока вы не авторизуетесь

            // После авторизации сохраняем состояние (cookies, localStorage и т.д.)
            context.storageState(BrowserContext.StorageStateOptions().setPath(Path.of(YANDEX_AUTH_STATE_PATH)))

            println("Состояние авторизации сохранено в '$YANDEX_AUTH_STATE_PATH'")
            browser.close()
        }
    }

    @Disabled
    @Test
    fun openYandexMusicWithAuth() {
        Playwright.create().use { playwright ->
            val browser = playwright.chromium().launch(
                BrowserType.LaunchOptions()
                    .setExecutablePath(Path.of("/usr/bin/yandex-browser")) // Укажите путь к Яндекс.Браузеру
                    .setHeadless(false) // или true, если не нужно видеть
            )

            // Создаем контекст, используя сохраненное состояние авторизации
            val context = browser.newContext(
                Browser.NewContextOptions()
                    .setStorageStatePath(Path.of(YANDEX_AUTH_STATE_PATH)) // <-- Используем сохраненное состояние
            )

            val page = context.newPage()
            page.navigate("https://music.yandex.ru/") // Откроется авторизованным

            // Дальнейшие действия на странице...
//            Thread.sleep(50000) // Пример задержки
            println("Пожалуйста, сделайте что нужно в браузере, затем нажмите Enter в консоли чтобы его закрыть.")
            readLine()

            browser.close()
        }
    }

    @Disabled
    @Test
    fun getYandexMusicWithAuthPageHtml() {
        Playwright.create().use { playwright ->
            val browser = playwright.chromium().launch(
                BrowserType.LaunchOptions()
                    .setExecutablePath(Path.of("/usr/bin/yandex-browser")) // Укажите путь к Яндекс.Браузеру
                    .setHeadless(true) // или true, если не нужно видеть
            )

            // Создаем контекст, используя сохраненное состояние авторизации
            val context = browser.newContext(
                Browser.NewContextOptions()
                    .setStorageStatePath(Path.of(YANDEX_AUTH_STATE_PATH)) // <-- Используем сохраненное состояние
            )

            val page = context.newPage()
            page.navigate("https://music.yandex.ru/artist/10385567/albums") // Откроется авторизованным

            page.waitForLoadState(LoadState.NETWORKIDLE)
            val htmlContent = page.content()
            println("Полный HTML страницы:")
            println(htmlContent)

            browser.close()
        }
    }

    @Disabled
    @Test
    fun getYandexMusicWithAuthPageHtml2() {
        Playwright.create().use { playwright ->
            val browser = playwright.chromium().launch(
                BrowserType.LaunchOptions()
//                    .setExecutablePath(Path.of("/usr/bin/yandex-browser")) // Укажите путь к Яндекс.Браузеру
                    .setHeadless(false) // или true, если не нужно видеть
            )

            // Создаем контекст, используя сохраненное состояние авторизации
            val context = browser.newContext(
                Browser.NewContextOptions()
                    .setStorageStatePath(Path.of(YANDEX_AUTH_STATE_PATH)) // <-- Используем сохраненное состояние
            )

            val page = context.newPage()
            page.navigate("https://music.yandex.ru/artist/10385567/albums") // Откроется авторизованным

            page.waitForLoadState(LoadState.NETWORKIDLE)
            val htmlContent = page.content()
            println("Полный HTML страницы:")
            println(htmlContent)
            Thread.sleep(50000)
            browser.close()
        }
    }

}