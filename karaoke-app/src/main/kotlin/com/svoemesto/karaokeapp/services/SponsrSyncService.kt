package com.svoemesto.karaokeapp.services

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.SPONSR_AUTH_STATE_PATH
import com.svoemesto.karaokeapp.model.SiteUser
import java.io.File
import java.nio.file.Path
import java.sql.Timestamp

data class SponsrSyncResult(
    val ok: Boolean,
    val foundIdentifiers: Int,
    val matchedUsers: Int,
    val messages: List<String>,
)

/**
 * Синхронизация премиума с активными подписчиками Sponsr (см. план монетизации, раздел
 * Sponsr-sync). Публичного API/вебхуков у Sponsr не найдено — источник данных ДВА:
 *  1) [importFromList] — ручной импорт списка email/ников, вставленного админом в webvue3
 *     (панель Sponsr-sync). Надёжный путь, работает уже сейчас.
 *  2) [syncViaScraping] — Playwright-скрейп кабинета автора Sponsr. ЧЕСТНО: это ЛУЧШЕЕ, что можно
 *     построить без доступа к реальной вёрстке кабинета — эвристика "вытащить все email-подобные
 *     подстроки со страницы". Как только [Karaoke.sponsrSubscribersUrl] будет заполнен (после
 *     проверки реального кабинета) и появится сохранённая сессия ([SPONSR_AUTH_STATE_PATH], через
 *     createNewSponsrAuthContext() в UtilsPlaywright.kt), стоит заменить textExtraction на точные
 *     селекторы структуры реальной страницы.
 *
 * Сопоставление гибкое: по email (LOWER=LOWER) и/или по введённому пользователем sponsr_uid
 * (SiteUser.sponsrUid, привязка на сайте). На каждого найденного активного подписчика —
 * sponsr_premium_until = now + sponsrSyncWindowDays (скользящее окно; выпал из списка Sponsr в
 * следующий раз — лапснется само по истечении окна, без явного удаления/сброса).
 */
object SponsrSyncService {

    private val EMAIL_REGEX = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")

    fun importFromList(
        identifiers: List<String>,
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
    ): SponsrSyncResult {
        val cleaned = identifiers.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return applyToUsers(cleaned, database, storageService, storageApiClient)
    }

    // Экспериментально — см. class-комментарий. Возвращает ok=false с понятным сообщением, если
    // не настроено (URL пуст) или нет сохранённой сессии, вместо тихого no-op.
    fun syncViaScraping(
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
    ): SponsrSyncResult {
        val url = Karaoke.sponsrSubscribersUrl.trim()
        if (url.isBlank()) {
            return SponsrSyncResult(false, 0, 0, listOf(
                "sponsrSubscribersUrl не настроен (Свойства/KaraokeProperties) — сначала проверьте " +
                "реальный кабинет автора Sponsr и укажите URL страницы подписчиков."
            ))
        }
        if (!File(SPONSR_AUTH_STATE_PATH).exists()) {
            return SponsrSyncResult(false, 0, 0, listOf(
                "Нет сохранённой сессии Sponsr ($SPONSR_AUTH_STATE_PATH) — выполните once " +
                "createNewSponsrAuthContext() (UtilsPlaywright.kt) и войдите в аккаунт вручную."
            ))
        }
        val identifiers = mutableListOf<String>()
        val messages = mutableListOf<String>()
        try {
            Playwright.create().use { playwright ->
                val browser = playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true))
                val context = browser.newContext(
                    Browser.NewContextOptions()
                        .setStorageStatePath(Path.of(SPONSR_AUTH_STATE_PATH))
                        .setLocale("ru-RU")
                )
                val page = context.newPage()
                page.navigate(url)
                val html = page.content()
                EMAIL_REGEX.findAll(html).forEach { identifiers.add(it.value) }
                // Обновляем сессию на диске (cookies могли обновиться) — тот же приём, что и
                // searchLastAlbumYm2 для Яндекс.Музыки.
                context.storageState(com.microsoft.playwright.BrowserContext.StorageStateOptions().setPath(Path.of(SPONSR_AUTH_STATE_PATH)))
                browser.close()
            }
        } catch (e: Exception) {
            messages.add("Ошибка при скрейпинге: ${e.message}")
            return SponsrSyncResult(false, identifiers.size, 0, messages)
        }
        if (identifiers.isEmpty()) {
            messages.add("На странице не найдено ни одного email-подобного идентификатора — вероятно, страница устроена не так, как ожидалось (нужна калибровка селекторов под реальную вёрстку).")
            return SponsrSyncResult(false, 0, 0, messages)
        }
        val result = applyToUsers(identifiers.distinct(), database, storageService, storageApiClient)
        return result.copy(messages = messages + result.messages)
    }

    private fun applyToUsers(
        identifiers: List<String>,
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
    ): SponsrSyncResult {
        if (identifiers.isEmpty()) return SponsrSyncResult(true, 0, 0, listOf("Список пуст — нечего синхронизировать."))
        val until = Timestamp(System.currentTimeMillis() + Karaoke.sponsrSyncWindowDays * 24L * 3600_000L)
        val lowerIdentifiers = identifiers.map { it.lowercase() }.toSet()
        var matched = 0
        val messages = mutableListOf<String>()

        // N+1 по всем site-users неизбежен без сырого SQL "IN (...)" по двум разным колонкам сразу —
        // при разумном числе пользователей сайта (десятки-сотни) это не проблема; синк не горячий путь.
        val allUsers = SiteUser.loadList(emptyMap(), database = database, storageService = storageService, storageApiClient = storageApiClient)
        for (user in allUsers) {
            val emailMatch = user.email.isNotBlank() && user.email.lowercase() in lowerIdentifiers
            val uidMatch = user.sponsrUid.isNotBlank() && user.sponsrUid.lowercase() in lowerIdentifiers
            if (emailMatch || uidMatch) {
                user.sponsrPremiumUntil = until
                user.save()
                user.sendWelcomePremiumMessageIfNeeded()
                matched++
            }
        }
        messages.add("Найдено идентификаторов: ${identifiers.size}, сопоставлено пользователей сайта: $matched.")
        return SponsrSyncResult(true, identifiers.size, matched, messages)
    }
}
