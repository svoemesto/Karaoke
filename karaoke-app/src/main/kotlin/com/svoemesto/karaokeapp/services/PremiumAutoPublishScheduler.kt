package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.PublicationType
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SongOutputFile
import com.svoemesto.karaokeapp.model.SongVersion
import com.svoemesto.karaokeapp.services.cleanup.ArtifactCleanupAutomation
import com.svoemesto.karaokeapp.services.cleanup.ArtifactCleanupClass
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.sql.SQLException

/**
 * Плановый бот премиум-публикации демо-версий песен в Telegram + VK
 * (specs/122-premium-auto-publish, FR-001).
 *
 * Триггер — `@Scheduled`-тик каждые 30 секунд (дефолт, FR-013). Каждый тик состоит из
 * двух фаз:
 *
 *  **Фаза 1 — [resumeRenderingSongs]**: продолжает премиум-публикации, которые ждут
 *  завершения асинхронного рендера `RENDER_MP4_DEMO` (например, Telegram-премиум всегда
 *  требует готовое видео — `sendVideo` не имеет текстового fallback, в отличие от
 *  ВК-премиум, который сегодня публикует только текст и потому никогда не «зависает» в
 *  рендере). Эта фаза — собственная, **не зависящая** от `telegramAutoPublishEnabled`/
 *  `vkAutoPublishEnabled` (флагов ДРУГИХ фич — `specs/113-telegram-demo-publish`/
 *  `specs/121-vk-news-auto-publish`), в отличие от предыдущей версии этого класса, где
 *  завершение отложенного премиум-рендера целиком зависело от побочного эффекта
 *  `TelegramAutoPublishScheduler.resumeRenderingSongs()`/`VkAutoPublishScheduler`-аналога —
 *  и потому силентно ломалось, если администратор выключал одну из AIR-фич, оставляя
 *  включённой только премиум (FR-003 spec.md).
 *
 *  **Фаза 2 — [publishPendingSongs]**:
 *  1. Если `premiumAutoPublishEnabled=false` — весь тик no-op (проверяется в [tick]).
 *  2. Cheap SELECT id песен с `newsPremiumPublishPending=true` в `playerReadinessFlags`.
 *  3. Для каждой — загружает полный `Song`, пропускает если оба канала уже «закрыты»
 *     (см. [closeIfBothChannelsDone] — закрыт значит «успешно опубликован» ИЛИ
 *     «исчерпал собственный лимит попыток», раздельно по каналам, FR-010).
 *  4. Последовательно публикует в Telegram (PREMIUM, `persistMessageId=false`) и VK
 *     (PREMIUM, `persistPostId=false`) — но только в канал, который ещё не закрыт. Если
 *     канал уже в RENDERING/PUBLISHING — пропускаем весь тик для этой песни до следующего.
 *  5. После успеха в обоих каналах — `newsPremiumPublishPending=false`,
 *     `premiumAutoPublishState=COMPLETE`.
 *  6. При `SEND_FAILED` — счётчик попыток **того канала, где произошёл сбой**
 *     инкрементируется независимо (FR-010): `premiumAttemptCountTelegram` или
 *     `premiumAttemptCountVk`, не общий `premiumAttemptCount` (deprecated, см. `Song.kt`).
 *     При достижении лимита этим каналом — он считается «закрытым по неудаче»; когда
 *     ОБА канала закрыты и хотя бы один — по неудаче, итоговое
 *     `premiumAutoPublishState=FAILED`.
 *
 * Идемпотентность:
 * - Поля `newsPremiumTelegramSent` и `newsPremiumVkSent` (независимо в каждом канале).
 * - Проверки `song.idTelegramDemo.isNotEmpty()` и `song.idVk.isNotEmpty()` — если air-публикация
 *   уже произошла до премиум-тика, skip.
 *
 * Состояние:
 * - В JSON-блобе `playerReadinessFlags`: `newsPremiumPublishPending` (Boolean),
 *   `newsPremiumTelegramSent`/`newsPremiumVkSent` (Boolean), `premiumAttemptCountTelegram`/
 *   `premiumAttemptCountVk` (int-as-string, раздельно по каналам — FR-010),
 *   `premiumAutoPublishState` (string: "RUNNING"/"COMPLETE"/"FAILED"/""),
 *   `premiumAutoPublishLastError` (string).
 *
 * @see archive/docs/features/telegram-auto-publish.md
 * @see archive/docs/features/vk-news-auto-publish.md
 */
@Component
class PremiumAutoPublishScheduler {
    // BR-165/BR-166 (аудит #336, пакет `PKG-S3-23`): категория логов премиум-публикации.
    private val premiumLog = org.slf4j.LoggerFactory.getLogger("infra.premium.publish")

    // periodicFixedDelay читается из KaraokeProperties (premiumAutoPublishTickFixedDelayMs, default 30000).
    // Через @Scheduled-механизм SpEL-${} не работает (KaraokeProperties — наш собственный base64,
    // а не Spring application.properties), поэтому используем дефолт 30000 и при необходимости
    // админ может рестартовать контейнер или подождать следующего тика. Если нужно — добавим
    // рестарт-планировщик (аналог TelegramAutoPublishSchedulerStarter) в следующем PR.
    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    fun tick() {
        if (!KaraokeProperties.getBoolean("premiumAutoPublishEnabled")) {
            return
        }

        // specs/122 fix (FR-003): resumeRenderingSongs() и publishPendingSongs() — в одном
        // try/catch (по образцу TelegramAutoPublishScheduler.tick()), чтобы сбой одной фазы не
        // "терял" другую полностью — на следующем тике обе фазы попробуют снова.
        try {
            // BR-165 (аудит #336, пакет `PKG-S3-23`): сбой БД в загрузчике кандидатов
            // прерывает тик (false), а не выглядит как «песен в очереди нет».
            if (!resumeRenderingSongs()) return
            if (!publishPendingSongs()) return
        } catch (e: Exception) {
            // BR-165: `printStackTrace()` уходил в stderr, мимо структурированных логов.
            premiumLog.error("PremiumAutoPublishScheduler.tick failed", e)
        }
    }

    // ---- Фаза 1: продолжить премиум-публикации, ждущие завершения RENDER_MP4_DEMO ----------

    /**
     * Фаза 1 тика (FR-003 spec.md): песни, у которых `newsPremiumPublishPending=true` и хотя бы
     * один канал (`telegramAutoPublishState`/`vkAutoPublishState`) в состоянии `rendering`,
     * проверяются на завершение их `RENDER_MP4_DEMO`-задачи; при терминальном статусе
     * (DONE/ERROR) вызывается `onRenderCompleted(..., publicationType/type=PREMIUM,
     * persistMessageId/persistPostId=false)` соответствующего сервиса — **независимо** от
     * `telegramAutoPublishEnabled`/`vkAutoPublishEnabled`. Это устраняет root cause бага
     * (research.md R1 `specs/122-premium-auto-publish`): раньше это делал только
     * `TelegramAutoPublishScheduler`/`VkAutoPublishScheduler`, гейтящиеся чужими флагами.
     */
    private fun resumeRenderingSongs(): Boolean {
        val ids = loadRenderingCandidateIds() ?: return false
        for (songId in ids) {
            try {
                resumeRenderingSong(songId)
            } catch (e: Exception) {
                premiumLog.warn("PremiumAutoPublishScheduler: не удалось продолжить рендер песни {}", songId, e)
            }
        }
        return true
    }

    private fun resumeRenderingSong(songId: Long) {
        var song =
            Song.loadFromDbById(id = songId, database = WORKING_DATABASE, storageService = KSS_APP, storageApiClient = SAC_APP)
                ?: return
        if (!song.newsPremiumPublishPending) return

        if (song.telegramAutoPublishState == "rendering" &&
            !song.newsPremiumTelegramSent &&
            song.idTelegramDemo.isEmpty()
        ) {
            val process = findRenderDemoProcess(songId)
            if (process != null && (process.status == "DONE" || process.status == "ERROR")) {
                val result =
                    TelegramAutoPublishService.onRenderCompleted(
                        songId = songId,
                        publicationType = PublicationType.PREMIUM,
                        persistMessageId = false,
                        success = process.status == "DONE",
                        error = if (process.status == "ERROR") "RENDER_MP4_DEMO failed (status=ERROR)" else null,
                    )
                if (result?.state?.code == "send_failed") {
                    val reloaded =
                        Song.loadFromDbById(id = songId, database = WORKING_DATABASE, storageService = KSS_APP, storageApiClient = SAC_APP)
                    if (reloaded != null) handleFailure(reloaded, "telegram", result.error ?: "render/send failed")
                }
            }
        }

        song =
            Song.loadFromDbById(id = songId, database = WORKING_DATABASE, storageService = KSS_APP, storageApiClient = SAC_APP)
                ?: return
        if (!song.newsPremiumPublishPending) return

        if (song.vkAutoPublishState == "rendering" &&
            !song.newsPremiumVkSent &&
            song.idVk.isEmpty()
        ) {
            val process = findRenderDemoProcess(songId)
            if (process != null && (process.status == "DONE" || process.status == "ERROR")) {
                val result =
                    VkAutoPublishService.onRenderCompleted(
                        songId = songId,
                        type = PublicationType.PREMIUM,
                        persistPostId = false,
                        success = process.status == "DONE",
                        error = if (process.status == "ERROR") "RENDER_MP4_DEMO failed (status=ERROR)" else null,
                    )
                if (result?.state?.code == "send_failed") {
                    val reloaded =
                        Song.loadFromDbById(id = songId, database = WORKING_DATABASE, storageService = KSS_APP, storageApiClient = SAC_APP)
                    if (reloaded != null) handleFailure(reloaded, "vk", result.error ?: "render/send failed")
                }
            }
        }

        val finalCheck =
            Song.loadFromDbById(id = songId, database = WORKING_DATABASE, storageService = KSS_APP, storageApiClient = SAC_APP)
                ?: return
        closeIfBothChannelsDone(finalCheck)
    }

    // ---- Фаза 2: обычный цикл публикации (уже существовавший, уточнён per-channel) --------

    private fun publishPendingSongs(): Boolean {
        // BR-165: сбой БД в загрузчике очереди возвращает null — тик прерывается.
        val candidateIds = loadPendingIds() ?: return false
        if (candidateIds.isEmpty()) return true
        for (songId in candidateIds) {
            try {
                processSong(songId)
            } catch (e: Exception) {
                premiumLog.warn("PremiumAutoPublishScheduler: не удалось обработать песню {}", songId, e)
            }
        }
        return true
    }

    private fun processSong(songId: Long) {
        val song =
            Song.loadFromDbById(
                id = songId,
                database = WORKING_DATABASE,
                storageService = KSS_APP,
                storageApiClient = SAC_APP,
            ) ?: run {
                return
            }

        // Если флаг исчез между SELECT и загрузкой — skip.
        if (!song.newsPremiumPublishPending) {
            return
        }

        // Если хотя бы один канал уже занят предыдущей публикацией (RENDERING/PUBLISHING) — пропускаем,
        // чтобы не запускать параллельные публикации. Rendering-канал продолжится в resumeRenderingSongs().
        val tgBusy = song.telegramAutoPublishState in setOf("rendering", "publishing")
        val vkBusy = song.vkAutoPublishState in setOf("rendering", "publishing")
        if (tgBusy || vkBusy) {
            return
        }

        // Оба канала уже закрыты (успех ИЛИ исчерпание попыток, FR-010) — завершаем задачу.
        if (closeIfBothChannelsDone(song)) {
            return
        }

        val max = maxAttempts()

        // Телеграм — пропускаем, если канал уже закрыт (успешно опубликован ИЛИ исчерпал лимит попыток).
        val tgClosed =
            song.idTelegramDemo.isNotEmpty() || song.newsPremiumTelegramSent || song.premiumAttemptCountTelegram >= max
        if (!tgClosed) {
            val tgResult =
                TelegramAutoPublishService.publishToTelegram(
                    song = song,
                    allowPastDate = true,
                    publicationType = PublicationType.PREMIUM,
                    persistMessageId = false,
                )
            // BR-166 (аудит #336, пакет `PKG-S3-23`): нетерминальные коды обрабатываются
            // ЯВНО. Раньше тик реагировал только на `send_failed`, а `SCHEDULED`
            // («not content-ready») игнорировался: счётчик попыток не рос, флаг
            // `newsPremiumPublishPending` не сбрасывался, состояние оставалось RUNNING —
            // и песня НАВСЕГДА оставалась в очереди: каждые 30 с полная загрузка песни и
            // попытка публикации, без лога и без терминального FAILED.
            handleChannelOutcome(song, "telegram", tgResult.state.code, tgResult.error)
        }

        // Перезагрузка после publishToTelegram (он мог изменить telegramAutoPublishLastAttemptAt
        // и установить newsPremiumTelegramSent). Это важно для корректной проверки ниже.
        val reloaded =
            Song.loadFromDbById(
                id = songId,
                database = WORKING_DATABASE,
                storageService = KSS_APP,
                storageApiClient = SAC_APP,
            ) ?: return
        if (!reloaded.newsPremiumPublishPending) return // Между тиками могли уже закрыть задачу.
        if (closeIfBothChannelsDone(reloaded)) return

        // VK — аналогично, пропускаем закрытый канал.
        val vkClosed =
            reloaded.idVk.isNotEmpty() || reloaded.newsPremiumVkSent || reloaded.premiumAttemptCountVk >= max
        if (!vkClosed) {
            val vkResult =
                VkAutoPublishService.publishToVk(
                    song = reloaded,
                    type = PublicationType.PREMIUM,
                    persistPostId = false,
                )
            // BR-166: тот же явный разбор нетерминальных кодов для канала VK.
            handleChannelOutcome(reloaded, "vk", vkResult.state.code, vkResult.error)
        }

        // Финальная проверка: оба канала закрыты?
        val finalCheck =
            Song.loadFromDbById(
                id = songId,
                database = WORKING_DATABASE,
                storageService = KSS_APP,
                storageApiClient = SAC_APP,
            ) ?: return
        closeIfBothChannelsDone(finalCheck)
    }

    /**
     * BR-166: единый разбор результата публикации по каналу.
     *
     * - `send_failed` — попытка неудачная: инкрементируем счётчик канала (как раньше).
     * - `scheduled` (контент не готов) — попытка тоже неудачная в смысле «публикации не
     *   было»: инкрементируем счётчик, пишем причину. Иначе песня навсегда остаётся в
     *   очереди с бесконечными тяжёлыми попытками.
     * - `published` — успех, счётчик не трогаем.
     * - прочие коды (`rendering`/`publishing` и т.п.) — это нормальный «в процессе»,
     *   попытку не засчитываем, но причину пишем в `premiumAutoPublishLastError`.
     */
    private fun handleChannelOutcome(
        song: Song,
        channel: String,
        stateCode: String,
        error: String?,
    ) {
        when (stateCode) {
            "send_failed" -> handleFailure(song, channel, error ?: "sendVideo failed")
            "scheduled" -> handleFailure(song, channel, error ?: "not content-ready")
            else -> {
                if (stateCode != "published") {
                    premiumLog.warn(
                        "PremiumAutoPublishScheduler: канал {} вернул неожиданный код '{}' (songId={}, error={})",
                        channel,
                        stateCode,
                        song.id,
                        error,
                    )
                }
            }
        }
    }

    /**
     * Помечает попытку конкретного канала как неуспешную (FR-010 spec.md): инкрементирует
     * `premiumAttemptCountTelegram`/`premiumAttemptCountVk` (раздельно, НЕ общий deprecated
     * `premiumAttemptCount`) в зависимости от [channel] (`"telegram"` или `"vk"`), записывает
     * [error] в `premiumAutoPublishLastError`, затем делегирует [closeIfBothChannelsDone] —
     * итоговое закрытие задачи (и `saveToDb()`) происходит там, если оба канала уже закрыты;
     * иначе сохраняет счётчик/ошибку сам.
     */
    private fun handleFailure(
        song: Song,
        channel: String,
        error: String,
    ) {
        when (channel) {
            "telegram" -> song.premiumAttemptCountTelegram = song.premiumAttemptCountTelegram + 1
            "vk" -> song.premiumAttemptCountVk = song.premiumAttemptCountVk + 1
        }
        song.premiumAutoPublishLastError = error
        if (!closeIfBothChannelsDone(song)) {
            // specs/299: race с SongEdit — saveToDbLocked.
            song.saveToDbLocked()
        }
    }

    /**
     * Проверяет, закрыты ли ОБА канала — «закрыт» означает «успешно опубликован» ИЛИ
     * «исчерпал собственный лимит попыток» (FR-010 spec.md, раздельно по каналам). Если да —
     * `newsPremiumPublishPending=false`, `premiumAutoPublishState` = `"FAILED"` (если хотя бы
     * один канал закрылся НЕ через успех) или `"COMPLETE"` (если оба — через успех), и
     * сохраняет через `saveToDb()`.
     *
     * @return `true`, если задача была закрыта этим вызовом (сохранение уже выполнено).
     */
    private fun closeIfBothChannelsDone(song: Song): Boolean {
        val max = maxAttempts()
        val tgSuccess = song.idTelegramDemo.isNotEmpty() || song.newsPremiumTelegramSent
        val vkSuccess = song.idVk.isNotEmpty() || song.newsPremiumVkSent
        val tgClosed = tgSuccess || song.premiumAttemptCountTelegram >= max
        val vkClosed = vkSuccess || song.premiumAttemptCountVk >= max
        if (tgClosed && vkClosed) {
            song.newsPremiumPublishPending = false
            song.premiumAutoPublishState = if (!tgSuccess || !vkSuccess) "FAILED" else "COMPLETE"
            // specs/357-folder-import-overwrite (FR-140): PremiumAutoPublishScheduler — scheduled task, объект song может жить минуты.
            song.saveToDbLocked()
            // specs/544-song-artifact-cleanup (FR-001): демо-ролик не хранится после шага
            // публикации — удаляем и при успехе, и при исчерпании попыток. Повторная
            // публикация отрендерит его заново (FR-002), ручных шагов не требуется.
            ArtifactCleanupAutomation.deleteAfterStage(
                classId = ArtifactCleanupClass.DEMO_MP4,
                path = song.pathToFileRenderMp4ForVersion(RenderVersion.DEMO),
            )
            // FR-005: публикационные картинки удаляем только когда обе площадки получили
            // успех — при неудаче они ещё нужны повторной попытке. Решение о наборе классов
            // вынесено в ArtifactCleanupAutomation.stageClassesAfterPublish (проверяемо тестом).
            if (ArtifactCleanupClass.PUBLICATION_PNG in
                ArtifactCleanupAutomation.stageClassesAfterPublish(tgSuccess = tgSuccess, vkSuccess = vkSuccess)
            ) {
                ArtifactCleanupAutomation.deleteAfterStage(
                    classId = ArtifactCleanupClass.PUBLICATION_PNG,
                    paths = publicationPicturePaths(song),
                )
            }
            return true
        }
        return false
    }

    /**
     * Пути публикационных картинок песни (specs/544, FR-005).
     *
     * Считаются теми же геттерами, которыми картинки создаются
     * (`UtilsPictures.kt`: `createSongPicture`, `createVKLinkPicture`,
     * `createSponsrTeaserPicture`, `createBoostyTeaserPicture`,
     * `createBoostyFilesPicture`), чтобы удалялось ровно созданное и ничего лишнего.
     * Картинки воссоздаются из данных песни по запросу (FR-005).
     */
    private fun publicationPicturePaths(song: Song): List<String> {
        val vkBase = song.getOutputFilename(SongOutputFile.PICTUREVK, SongVersion.LYRICS)
        return listOf(
            song.getOutputFilename(SongOutputFile.PICTURE, SongVersion.LYRICS),
            song.getOutputFilename(SongOutputFile.PICTURE, SongVersion.KARAOKE),
            song.getOutputFilename(SongOutputFile.PICTURE, SongVersion.CHORDS),
            song.getOutputFilename(SongOutputFile.PICTURE, SongVersion.TABS),
            vkBase.replace(" [lyrics] VK", " [VKlink]"),
            vkBase.replace(" [lyrics] VK", " [VK]"),
            song.getOutputFilename(SongOutputFile.PICTURESPONSRTEASER),
            song.getOutputFilename(SongOutputFile.PICTUREBOOSTYTEASER),
            song.getOutputFilename(SongOutputFile.PICTUREBOOSTYFILES),
        )
    }

    private fun maxAttempts(): Long = KaraokeProperties.getLong("premiumAutoPublishMaxAttempts").let { if (it <= 0) 3L else it }

    /**
     * Cheap SELECT id песен с newsPremiumPublishPending=true в player_readiness_flags.
     * Без загрузки полного Song. Этот же паттерн, что в VkAutoPublishScheduler.loadRenderingCandidateIds.
     */
    private fun loadPendingIds(): List<Long>? {
        val connection =
            WORKING_DATABASE.getConnection()
                ?: run {
                    premiumLog.warn("PremiumAutoPublishScheduler: нет соединения с БД — очередь премиум-публикаций не прочитана")
                    return null
                }
        val result = mutableListOf<Long>()
        try {
            connection.createStatement().use { st ->
                val rs =
                    st.executeQuery(
                        """
                        SELECT id, player_readiness_flags
                        FROM tbl_songs
                        WHERE player_readiness_flags LIKE '%newsPremiumPublishPending%'
                        ORDER BY id ASC
                        """.trimIndent(),
                    )
                rs.use {
                    while (rs.next()) {
                        val id = rs.getLong("id")
                        val flags = rs.getString("player_readiness_flags") ?: ""
                        if (flags.contains("\"newsPremiumPublishPending\":true") ||
                            flags.contains("\"newsPremiumPublishPending\" : true")
                        ) {
                            result.add(id)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            // BR-165: сбой БД раньше был неотличим от «очередь пуста».
            premiumLog.warn(
                "PremiumAutoPublishScheduler: не удалось прочитать очередь премиум-публикаций (sqlState={})",
                e.sqlState,
                e,
            )
            return null
        }
        return result
    }

    /**
     * Cheap SELECT id песен с `newsPremiumPublishPending=true`, у которых хотя бы один канал
     * (`telegramAutoPublishState`/`vkAutoPublishState`) в состоянии `rendering` — кандидаты для
     * [resumeRenderingSongs]. Без загрузки полного `Song`.
     */
    private fun loadRenderingCandidateIds(): List<Long>? {
        val connection =
            WORKING_DATABASE.getConnection()
                ?: run {
                    premiumLog.warn("PremiumAutoPublishScheduler: нет соединения с БД — кандидаты рендера не прочитаны")
                    return null
                }
        val result = mutableListOf<Long>()
        try {
            connection.createStatement().use { st ->
                val rs =
                    st.executeQuery(
                        """
                        SELECT id, player_readiness_flags
                        FROM tbl_songs
                        WHERE player_readiness_flags LIKE '%newsPremiumPublishPending%'
                          AND (player_readiness_flags LIKE '%"telegramAutoPublishState":"rendering"%'
                               OR player_readiness_flags LIKE '%"vkAutoPublishState":"rendering"%')
                        ORDER BY id ASC
                        """.trimIndent(),
                    )
                rs.use {
                    while (rs.next()) {
                        val id = rs.getLong("id")
                        val flags = rs.getString("player_readiness_flags") ?: ""
                        if (flags.contains("\"newsPremiumPublishPending\":true") ||
                            flags.contains("\"newsPremiumPublishPending\" : true")
                        ) {
                            result.add(id)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            // BR-165: сбой БД раньше был неотличим от «кандидатов нет».
            premiumLog.warn(
                "PremiumAutoPublishScheduler: не удалось прочитать кандидатов рендера (sqlState={})",
                e.sqlState,
                e,
            )
            return null
        }
        return result
    }

    /** Находит терминальную render-задачу `RENDER_MP4_DEMO` для песни (по образцу
     * `TelegramAutoPublishScheduler.findRenderDemoProcess`). Возвращает `null`, если задача
     * ещё `WORKING`/`WAITING` или не найдена. */
    private fun findRenderDemoProcess(songId: Long): RenderProcessInfo? {
        val connection =
            WORKING_DATABASE.getConnection()
                ?: run {
                    premiumLog.warn("PremiumAutoPublishScheduler: нет соединения с БД — render-задача не найдена (songId={})", songId)
                    return null
                }
        try {
            connection.createStatement().use { st ->
                val rs =
                    st.executeQuery(
                        """
                        SELECT process_status, id
                        FROM tbl_processes
                        WHERE song_id = $songId AND process_type = 'RENDER_MP4_DEMO'
                        ORDER BY id DESC LIMIT 1
                        """.trimIndent(),
                    )
                rs.use {
                    if (rs.next()) {
                        return RenderProcessInfo(rs.getString("process_status"), rs.getLong("id"))
                    }
                }
            }
        } catch (e: SQLException) {
            // BR-165: сбой БД раньше был неотличим от «задачи нет».
            premiumLog.warn(
                "PremiumAutoPublishScheduler: не удалось найти render-задачу (songId={}, sqlState={})",
                songId,
                e.sqlState,
                e,
            )
            return null
        }
        return null
    }

    private data class RenderProcessInfo(
        val status: String,
        val id: Long,
    )
}
