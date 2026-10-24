package com.svoemesto

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.LoadState
import com.svoemesto.karaokeapp.YANDEX_AUTH_STATE_PATH
import com.svoemesto.manual.AuthStateFile
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Ручные сценарии авторизации Яндекс.Музыки (Playwright).
 *
 * Живут в отдельном source set `manualTest` и НЕ входят в `test`/`check`/`build`:
 * им нужен браузер, сеть и ручное действие человека. Раньше они лежали в общем
 * наборе `src/test` под защитой `@Disabled` — при снятии пометки (или при переносе
 * класса в другой модуль) `gradle test` на машине без браузера упирался в
 * `readLine()` и висел до таймаута раннера, а «зелёный» прогон учил игнорировать
 * падения тестов.
 *
 * Запуск (с подготовленным окружением — браузеры Playwright, сеть, ручная
 * авторизация):
 * ```
 * ./gradlew :karaoke-app:manualTest
 * ```
 *
 * Отличия от прежней версии:
 *  - нет интерактивного `readLine()` — вместо него ожидание условия с таймаутом;
 *  - путь к браузеру не зашит в код (свойство `karaoke.playwright.browserPath`
 *    или переменная окружения `PLAYWRIGHT_BROWSER_PATH`), по умолчанию —
 *    штатный Chromium из состава Playwright;
 *  - файл состояния авторизации получает права `600` явно (см. [AuthStateFile]).
 *
 * @see specs/336-code-audit/findings/P-30.md (BR-532)
 */
class PlaywrightTests {
    /** Куда складывается состояние авторизации (тот же путь, что и у приложения). */
    private val authStatePath: Path = Path.of(YANDEX_AUTH_STATE_PATH)

    /** Сколько ждать ручную авторизацию в браузере. */
    private val authTimeout: Duration = Duration.ofMinutes(5)

    /** Сколько ждать появления файла состояния авторизации. */
    private val stateFileTimeout: Duration = Duration.ofMinutes(5)

    /**
     * Путь к исполняемому файлу браузера.
     *
     * Раньше был зашит `/usr/bin/yandex-browser` — путь, которого нет ни в CI, ни
     * в образе. Теперь путь задаётся окружением; пустое значение означает
     * «использовать браузер из состава Playwright».
     */
    private fun browserExecutablePath(): String? {
        val configured =
            System.getProperty("karaoke.playwright.browserPath")
                ?: System.getenv("PLAYWRIGHT_BROWSER_PATH")
        return configured?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Опции запуска браузера: headful + (опционально) исполняемый файл. */
    private fun launchOptions(headless: Boolean): BrowserType.LaunchOptions {
        val options = BrowserType.LaunchOptions().setHeadless(headless)
        browserExecutablePath()?.let { options.setExecutablePath(Path.of(it)) }
        return options
    }

    /**
     * Дождаться, пока пользователь авторизуется в открытом браузере.
     *
     * Вместо `readLine()` (интерактивного ввода, блокирующего раннер) ждём
     * появления cookie авторизации Яндекс.Музыки — с таймаутом.
     */
    private fun awaitAuthorization(context: BrowserContext) {
        val deadline = System.nanoTime() + authTimeout.toNanos()
        while (System.nanoTime() < deadline) {
            val authorized =
                context.cookies().any { cookie ->
                    cookie.domain.contains("yandex") &&
                        cookie.name.contains("Session", ignoreCase = true)
                }
            if (authorized) return
            Thread.sleep(500)
        }
        throw AssertionError("Авторизация в Яндекс.Музыке не завершилась за $authTimeout")
    }

    /** Сохранить состояние авторизации в файл с правами `600`. */
    private fun saveAuthState(context: BrowserContext) {
        val target = Path.of(YANDEX_AUTH_STATE_PATH)
        Files.createDirectories(target.parent)
        // storageState пишет файл сам — права выставляем сразу после записи.
        context.storageState(BrowserContext.StorageStateOptions().setPath(target))
        AuthStateFile.setOwnerOnlyPermissions(target)
        println("Состояние авторизации сохранено в '$YANDEX_AUTH_STATE_PATH'")
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    fun saveYandexMusicAuthByChrome() {
        Playwright.create().use { playwright ->
            val browser = playwright.chromium().launch(launchOptions(headless = false))
            try {
                val context = browser.newContext()
                val page = context.newPage()
                page.navigate("https://music.yandex.ru/")

                println("Авторизуйтесь в браузере — сценарий дождётся сам (таймаут $authTimeout).")
                awaitAuthorization(context)
                saveAuthState(context)
            } finally {
                browser.close()
            }
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    fun saveYandexMusicAuth() {
        Playwright.create().use { playwright ->
            val browser = playwright.chromium().launch(launchOptions(headless = false))
            try {
                val context = browser.newContext()
                val page = context.newPage()
                page.navigate("https://music.yandex.ru/")

                println("Авторизуйтесь в браузере — сценарий дождётся сам (таймаут $authTimeout).")
                awaitAuthorization(context)
                saveAuthState(context)
            } finally {
                browser.close()
            }
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    fun openYandexMusicWithAuth() {
        Playwright.create().use { playwright ->
            // Файл состояния — предусловие сценария: ждём его появления с таймаутом.
            assertTrue(
                AuthStateFile.awaitExists(authStatePath, stateFileTimeout),
                "Файл состояния авторизации '$YANDEX_AUTH_STATE_PATH' не появился за $stateFileTimeout",
            )
            val browser = playwright.chromium().launch(launchOptions(headless = false))
            try {
                val context =
                    browser.newContext(
                        Browser
                            .NewContextOptions()
                            .setStorageStatePath(authStatePath),
                    )
                val page = context.newPage()
                page.navigate("https://music.yandex.ru/") // Откроется авторизованным

                println("Браузер открыт авторизованным. Закройте вкладку — сценарий завершится.")
                // Ждём закрытия вкладки человеком (с таймаутом) вместо readLine().
                page.waitForCondition { page.isClosed }
            } finally {
                browser.close()
            }
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    fun getYandexMusicWithAuthPageHtml() {
        Playwright.create().use { playwright ->
            assertTrue(
                AuthStateFile.awaitExists(authStatePath, stateFileTimeout),
                "Файл состояния авторизации '$YANDEX_AUTH_STATE_PATH' не появился за $stateFileTimeout",
            )
            val browser = playwright.chromium().launch(launchOptions(headless = true))
            try {
                val context =
                    browser.newContext(
                        Browser
                            .NewContextOptions()
                            .setStorageStatePath(authStatePath),
                    )
                val page = context.newPage()
                page.navigate("https://music.yandex.ru/artist/10385567/albums")

                page.waitForLoadState(LoadState.NETWORKIDLE)
                val htmlContent = page.content()
                println("Полный HTML страницы:")
                println(htmlContent)
                assertTrue(htmlContent.isNotBlank(), "HTML страницы не должен быть пустым")
            } finally {
                browser.close()
            }
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    fun getYandexMusicWithAuthPageHtml2() {
        Playwright.create().use { playwright ->
            assertTrue(
                AuthStateFile.awaitExists(authStatePath, stateFileTimeout),
                "Файл состояния авторизации '$YANDEX_AUTH_STATE_PATH' не появился за $stateFileTimeout",
            )
            val browser = playwright.chromium().launch(launchOptions(headless = false))
            try {
                val context =
                    browser.newContext(
                        Browser
                            .NewContextOptions()
                            .setStorageStatePath(authStatePath),
                    )
                val page = context.newPage()
                page.navigate("https://music.yandex.ru/artist/10385567/albums")

                page.waitForLoadState(LoadState.NETWORKIDLE)
                val htmlContent = page.content()
                println("Полный HTML страницы:")
                println(htmlContent)
                assertTrue(htmlContent.isNotBlank(), "HTML страницы не должен быть пустым")
                // Вместо Thread.sleep(50000) — ожидание закрытия вкладки человеком.
                page.waitForCondition { page.isClosed }
            } finally {
                browser.close()
            }
        }
    }
}
