package com.svoemesto.karaokeapp

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("unused")
fun main1() {
    Playwright.create().use { playwright ->
        // Запуск браузера в видимом режиме
//        val browser = playwright.chromium().launch(
//            BrowserType.LaunchOptions()
//                .setHeadless(false) // <-- Делает браузер видимым
//        )
        // Укажите путь к исполняемому файлу Яндекс.Браузера
        val yandexBrowserPath = "/usr/bin/yandex-browser" // <-- Укажите актуальный путь

        val browser = playwright.chromium().launch(
            BrowserType.LaunchOptions()
                .setExecutablePath(Path.of(yandexBrowserPath))
                .setHeadless(false) // Чтобы браузер был виден
        )
        val page = browser.newPage()
        page.navigate("https://music.yandex.ru/")
        println(page.title())
        // Можно добавить задержку, чтобы увидеть страницу перед закрытием
        Thread.sleep(5000) // Задержка 5 секунд
        browser.close()
    }
}

var authLatch = CountDownLatch(1)

// Не даёт запустить второй auth-flow, пока не завершён предыдущий — иначе второй вызов
// переписывает authLatch, и поток первого flow блокируется на уже "осиротевшем" latch навсегда.
val authInProgress = AtomicBoolean(false)

fun completeAuth() {
    authLatch.countDown()
}

// Общий каркас обоих auth-flow: гард от параллельного запуска, сброс latch, ожидание
// completeAuth() и гарантированное закрытие браузера/контекста. createContext() возвращает
// созданный контекст вместе с функцией его закрытия (разной для обычного и persistent-контекста).
private fun runAuthFlow(
    startMessage: String,
    createContext: (Playwright) -> Pair<BrowserContext, () -> Unit>,
    onAuthorized: (BrowserContext) -> Unit
) {
    if (!authInProgress.compareAndSet(false, true)) {
        println("Авторизация уже выполняется, дождитесь её завершения (completeAuth()) перед повторным запуском")
        return
    }

    try {
        authLatch = CountDownLatch(1) // Сбрасываем latch

        Playwright.create().use { playwright ->
            val (context, closeContext) = createContext(playwright)
            val page = context.pages().firstOrNull() ?: context.newPage()

            try {
                page.navigate("https://music.yandex.ru/")
                println(startMessage)

                authLatch.await() // Блокируется до вызова countDown()

                onAuthorized(context)
            } catch (e: Exception) {
                System.err.println("Ошибка при работе с браузером: ${e.message}")
                e.printStackTrace()
            } finally {
                closeContext()
            }
        }
    } finally {
        authInProgress.set(false)
    }
}

fun createNewAuthContext() {
    runAuthFlow(
        startMessage = "Авторизуйтесь в браузере, затем вызовите completeAuth()",
        createContext = { playwright ->
            val browser = playwright.chromium().launch(
                BrowserType.LaunchOptions()
                    .setHeadless(false)
//                .setChannel("chrome") // Запуск системного Chrome
            )
            val context = browser.newContext(
                Browser.NewContextOptions()
                    .setLocale("ru-RU")
                    .setTimezoneId("Europe/Moscow")
            )
            context to { browser.close() }
        },
        onAuthorized = { context ->
            context.storageState(
                BrowserContext.StorageStateOptions().setPath(Path.of(YANDEX_AUTH_STATE_PATH))
            )
            println("Состояние сохранено в '$YANDEX_AUTH_STATE_PATH'")
        }
    )
}

// Путь к папке, где будет храниться профиль (создастся автоматически)
val USER_DATA_DIR = Path.of("./yandex-browser-profile")

fun createNewAuthContext2() {
    runAuthFlow(
        startMessage = "Авторизуйтесь в браузере (не забудьте 'Запомнить меня'), затем вызовите completeAuth()",
        createContext = { playwright ->
            // Запускаем браузер сразу с привязкой к папке профиля
            val context = playwright.chromium().launchPersistentContext(
                USER_DATA_DIR,
                BrowserType.LaunchPersistentContextOptions()
                    .setHeadless(false)
                    .setLocale("ru-RU")
                    .setTimezoneId("Europe/Moscow")
                    .setArgs(listOf("--disable-blink-features=AutomationControlled")) // Скрываем признаки бота
            )
            context to { context.close() } // При закрытии профиль автоматически сохраняется на диск
        },
        onAuthorized = {
            println("Авторизация успешна! Профиль сохранен в папке: $USER_DATA_DIR")
        }
    )
}

@Suppress("unused")
fun main222() {
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

@Suppress("unused")
fun main333() {
    Playwright.create().use { playwright ->
        val browser = playwright.chromium().launch(
            BrowserType.LaunchOptions()
//                .setExecutablePath(java.nio.file.Path.of("/usr/bin/yandex-browser")) // Укажите путь к Яндекс.Браузеру
                .setHeadless(false) // или true, если не нужно видеть
        )

        // Создаем контекст, используя сохраненное состояние авторизации
        val context = browser.newContext(
            Browser.NewContextOptions()
                .setStorageStatePath(Path.of(YANDEX_AUTH_STATE_PATH)) // <-- Используем сохраненное состояние
        )

        val page = context.newPage()
        page.navigate("https://music.yandex.ru/artist/41055/albums") // Откроется авторизованным

//        page.waitForLoadState(LoadState.NETWORKIDLE)
        val html = page.content()
        println("html: '$html'")

        // Дальнейшие действия на странице...
        Thread.sleep(50000) // Пример задержки
        browser.close()
    }
}