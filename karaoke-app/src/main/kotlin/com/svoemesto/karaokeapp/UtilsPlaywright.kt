package com.svoemesto.karaokeapp

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("UtilsPlaywright")

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

        val browser =
            playwright.chromium().launch(
                BrowserType
                    .LaunchOptions()
                    .setExecutablePath(Path.of(yandexBrowserPath))
                    .setHeadless(false), // Чтобы браузер был виден
            )
        val page = browser.newPage()
        page.navigate("https://music.yandex.ru/")
        println(page.title())
        // Можно добавить задержку, чтобы увидеть страницу перед закрытием
        Thread.sleep(5000) // Задержка 5 секунд
        browser.close()
    }
}

/**
 * Таймаут интерактивного auth-flow (BR-113, аудит #336).
 *
 * 10 минут — с запасом на ручной вход (SMS/2FA), но конечное значение: раньше
 * `authLatch.await()` без таймаута навсегда занимал HTTP-поток Tomcat и оставлял
 * живым видимый Chromium, если оператор закрыл вкладку и не вызвал `completeAuth()`.
 */
private const val AUTH_FLOW_TIMEOUT_MS = 10 * 60 * 1000L

/**
 * Состояние одного auth-flow (BR-113).
 *
 * Заменяет глобальный `var authLatch`, который `runAuthFlow` переприсваивал уже
 * после `compareAndSet(false, true)`: `completeAuth()` из другого потока мог
 * прочитать старый latch и отсчитать «не тот» объект. Теперь latch создаётся
 * локально для запуска, а `complete()` всегда отсчитывает текущий.
 */
internal class AuthFlowGate(
    private val timeoutMs: Long = AUTH_FLOW_TIMEOUT_MS,
) {
    private val inProgress = AtomicBoolean(false)
    private val current = AtomicReference<CountDownLatch?>(null)

    /** `null` — flow уже идёт; иначе latch этого запуска. */
    fun tryStart(): CountDownLatch? {
        if (!inProgress.compareAndSet(false, true)) return null
        val latch = CountDownLatch(1)
        current.set(latch)
        return latch
    }

    /** Отпускает текущий flow (идемпотентно). */
    fun complete() {
        current.get()?.countDown()
    }

    /** Ожидание `complete()` с таймаутом; `false` — истёк таймаут/поток прерван. */
    fun await(latch: CountDownLatch): Boolean =
        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

    fun finish() {
        current.set(null)
        inProgress.set(false)
    }
}

private val authGate = AuthFlowGate()

fun completeAuth() {
    authGate.complete()
}

// Общий каркас обоих auth-flow: гард от параллельного запуска, сброс latch, ожидание
// completeAuth() и гарантированное закрытие браузера/контекста. createContext() возвращает
// созданный контекст вместе с функцией его закрытия (разной для обычного и persistent-контекста).
private fun runAuthFlow(
    startMessage: String,
    startUrl: String = "https://music.yandex.ru/",
    createContext: (Playwright) -> Pair<BrowserContext, () -> Unit>,
    onAuthorized: (BrowserContext) -> Unit,
) {
    val latch =
        authGate.tryStart()
            ?: run {
                println("Авторизация уже выполняется, дождитесь её завершения (completeAuth()) перед повторным запуском")
                return
            }

    try {
        Playwright.create().use { playwright ->
            val (context, closeContext) = createContext(playwright)
            val page = context.pages().firstOrNull() ?: context.newPage()

            try {
                page.navigate(startUrl)
                println(startMessage)

                // BR-113: конечное ожидание. По таймауту контекст закрывается в finally,
                // HTTP-поток возвращается, следующий auth-flow снова доступен.
                if (!authGate.await(latch)) {
                    log.warn(
                        "auth-flow: истёк таймаут {} мс — браузер закрывается, авторизация не завершена; " +
                            "повторите запуск и вызовите completeAuth()",
                        AUTH_FLOW_TIMEOUT_MS,
                    )
                    return@use
                }

                onAuthorized(context)
            } catch (e: Exception) {
                log.error("Ошибка при работе с браузером: ${e.message}", e)
            } finally {
                closeContext()
            }
        }
    } finally {
        authGate.finish()
    }
}

fun createNewAuthContext() {
    runAuthFlow(
        startMessage = "Авторизуйтесь в браузере, затем вызовите completeAuth()",
        createContext = { playwright ->
            val browser =
                playwright.chromium().launch(
                    BrowserType
                        .LaunchOptions()
                        .setHeadless(false),
//                .setChannel("chrome") // Запуск системного Chrome
                )
            val context =
                browser.newContext(
                    Browser
                        .NewContextOptions()
                        .setLocale("ru-RU")
                        .setTimezoneId("Europe/Moscow"),
                )
            context to { browser.close() }
        },
        onAuthorized = { context ->
            context.storageState(
                BrowserContext.StorageStateOptions().setPath(Path.of(YANDEX_AUTH_STATE_PATH)),
            )
            println("Состояние сохранено в '$YANDEX_AUTH_STATE_PATH'")
        },
    )
}

// Интерактивный одноразовый вход в Sponsr — вызывать вручную с консоли/REPL admin-машины: залогиниться
// в открывшемся браузере, затем вызвать completeAuth(). Сохранённая сессия переиспользуется headless
// в SponsrSyncService.syncViaScraping() (см. SPONSR_AUTH_STATE_PATH). Полный аналог createNewAuthContext()
// для Яндекс.Музыки, только домен и путь сохранения другие.
fun createNewSponsrAuthContext() {
    runAuthFlow(
        startMessage = "Авторизуйтесь в браузере на sponsr.ru, затем вызовите completeAuth()",
        startUrl = "https://sponsr.ru/",
        createContext = { playwright ->
            val browser =
                playwright.chromium().launch(
                    BrowserType.LaunchOptions().setHeadless(false),
                )
            val context =
                browser.newContext(
                    Browser
                        .NewContextOptions()
                        .setLocale("ru-RU")
                        .setTimezoneId("Europe/Moscow"),
                )
            context to { browser.close() }
        },
        onAuthorized = { context ->
            context.storageState(
                BrowserContext.StorageStateOptions().setPath(Path.of(SPONSR_AUTH_STATE_PATH)),
            )
            println("Состояние сохранено в '$SPONSR_AUTH_STATE_PATH'")
        },
    )
}

// Путь к папке, где будет храниться профиль (создастся автоматически)
val USER_DATA_DIR = Path.of("./yandex-browser-profile")

/**
 * BR-121 (аудит #336, пакет `PKG-S3-23`, OpenProject #395): сериализация доступа к
 * persistent-профилю Яндекс.Музыки.
 *
 * Chromium держит эксклюзивную блокировку на каталоге профиля: второй
 * `launchPersistentContext` на том же `USER_DATA_DIR` не стартует. Поэтому все
 * АВТОМАТИЧЕСКИЕ операции с профилем (поиск текста песни, поиск обложек/альбомов)
 * выполняются под этим локом — иначе параллельный импорт падал с «не удалось
 * запустить Chromium», и текст песни просто не находился.
 *
 * Ручная авторизация ([createNewAuthContext2]) под лок НЕ берётся намеренно: она
 * ждёт человека (минуты) и заблокировала бы автоматические операции на всё это время.
 * Её правило — «не запускать автоматику, пока идёт авторизация» — живёт в
 * [UtilsPlaywright] (см. `authInProgress`).
 */
val YANDEX_PROFILE_LOCK = ReentrantLock()

fun createNewAuthContext2() {
    runAuthFlow(
        startMessage = "Авторизуйтесь в браузере (не забудьте 'Запомнить меня'), затем вызовите completeAuth()",
        createContext = { playwright ->
            // Запускаем браузер сразу с привязкой к папке профиля
            val context =
                playwright.chromium().launchPersistentContext(
                    USER_DATA_DIR,
                    BrowserType
                        .LaunchPersistentContextOptions()
                        .setHeadless(false)
                        .setLocale("ru-RU")
                        .setTimezoneId("Europe/Moscow")
                        .setArgs(listOf("--disable-blink-features=AutomationControlled")), // Скрываем признаки бота
                )
            context to { context.close() } // При закрытии профиль автоматически сохраняется на диск
        },
        onAuthorized = {
            println("Авторизация успешна! Профиль сохранен в папке: $USER_DATA_DIR")
        },
    )
}

@Suppress("unused")
fun main222() {
    Playwright.create().use { playwright ->
        val browser =
            playwright.chromium().launch(
                BrowserType
                    .LaunchOptions()
//                    .setExecutablePath(Path.of("/usr/bin/yandex-browser")) // Укажите путь к Яндекс.Браузеру
                    .setHeadless(false), // Оставляем видимым, чтобы вручную авторизоваться
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
        val browser =
            playwright.chromium().launch(
                BrowserType
                    .LaunchOptions()
//                .setExecutablePath(java.nio.file.Path.of("/usr/bin/yandex-browser")) // Укажите путь к Яндекс.Браузеру
                    .setHeadless(false), // или true, если не нужно видеть
            )

        // Создаем контекст, используя сохраненное состояние авторизации
        val context =
            browser.newContext(
                Browser
                    .NewContextOptions()
                    .setStorageStatePath(Path.of(YANDEX_AUTH_STATE_PATH)), // <-- Используем сохраненное состояние
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
