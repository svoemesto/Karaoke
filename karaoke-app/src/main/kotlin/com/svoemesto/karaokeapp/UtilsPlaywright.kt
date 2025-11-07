package com.svoemesto.karaokeapp

import com.microsoft.playwright.*

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
                .setExecutablePath(java.nio.file.Path.of(yandexBrowserPath))
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

fun main2() {
    Playwright.create().use { playwright ->
        val browser = playwright.chromium().launch(
            BrowserType.LaunchOptions()
                .setExecutablePath(java.nio.file.Path.of("/usr/bin/yandex-browser")) // Укажите путь к Яндекс.Браузеру
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
        context.storageState(BrowserContext.StorageStateOptions().setPath(java.nio.file.Path.of("/sm-karaoke/system/yandex_auth_state.json")))

        println("Состояние авторизации сохранено в '/sm-karaoke/system/yandex_auth_state.json'")
        browser.close()
    }
}

fun main3() {
    Playwright.create().use { playwright ->
        val browser = playwright.chromium().launch(
            BrowserType.LaunchOptions()
                .setExecutablePath(java.nio.file.Path.of("/usr/bin/yandex-browser")) // Укажите путь к Яндекс.Браузеру
                .setHeadless(false) // или true, если не нужно видеть
        )

        // Создаем контекст, используя сохраненное состояние авторизации
        val context = browser.newContext(
            Browser.NewContextOptions()
                .setStorageStatePath(java.nio.file.Path.of("/sm-karaoke/system/yandex_auth_state.json")) // <-- Используем сохраненное состояние
        )

        val page = context.newPage()
        page.navigate("https://music.yandex.ru/") // Откроется авторизованным

        // Дальнейшие действия на странице...
        Thread.sleep(50000) // Пример задержки
        browser.close()
    }
}