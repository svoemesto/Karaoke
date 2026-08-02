package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.PublicationType
import com.svoemesto.karaokeapp.model.Song
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.sql.SQLException

/**
 * Плановый бот премиум-публикации демо-версий песен в Telegram + VK
 * (specs/122-premium-auto-publish, FR-001).
 *
 * Триггер — `@Scheduled`-тик каждые 30 секунд (дефолт, FR-013). Каждый тик:
 *
 *  1. Если `premiumAutoPublishEnabled=false` — no-op.
 *  2. Cheap SELECT id песен с `newsPremiumPublishPending=true` в `playerReadinessFlags`.
 *  3. Для каждой — загружает полный `Song`, пропускает если уже неактуально (idTelegramDemo/idVk
 *     заполнен, или newsPremiumTelegramSent && newsPremiumVkSent — оба канала закрыты).
 *  4. Последовательно публикует в Telegram (PREMIUM, persistMessageId=false) и VK
 *     (PREMIUM, persistPostId=false). Если песня уже в RENDERING — пропускаем до следующего тика.
 *  5. После успеха в обоих каналах — newsPremiumPublishPending=false, premiumAutoPublishState=COMPLETE.
 *  6. При SEND_FAILED — premiumAttemptCount++, при достижении лимита — newsPremiumPublishPending=false
 *     и premiumAutoPublishState=FAILED.
 *
 * Идемпотентность:
 * - Поля `newsPremiumTelegramSent` и `newsPremiumVkSent` (независимо в каждом канале).
 * - Проверки `song.idTelegramDemo.isNotEmpty()` и `song.idVk.isNotEmpty()` — если air-публикация
 *   уже произошла до премиум-тики, skip.
 *
 * Состояние:
 * - В JSON-блобе `playerReadinessFlags`: newsPremiumPublishPending (Boolean), newsPremiumTelegramSent
 *   (Boolean), newsPremiumVkSent (Boolean), premiumAttemptCount (int-as-string), premiumAutoPublishState
 *   (string: "RUNNING"/"COMPLETE"/"FAILED"/""), premiumAutoPublishLastError (string).
 *
 * @see docs/features/telegram-auto-publish.md
 * @see docs/features/vk-news-auto-publish.md
 */
@Component
class PremiumAutoPublishScheduler {
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

        try {
            publishPendingSongs()
        } catch (e: Exception) {
            println("PremiumAutoPublishScheduler.tick error: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun publishPendingSongs() {
        val candidateIds = loadPendingIds()
        if (candidateIds.isEmpty()) return
        println("PremiumAutoPublishScheduler: candidates.size=${candidateIds.size}, ids=$candidateIds")
        for (songId in candidateIds) {
            try {
                processSong(songId)
            } catch (e: Exception) {
                println("PremiumAutoPublishScheduler: song id=$songId unexpected error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun processSong(songId: Long) {
        val song =
            Song.loadFromDbById(
                id = songId,
                database = WORKING_DATABASE,
                storageService = KSS_APP,
                storageApiClient = SAC_APP,
            ) ?: run {
                println("PremiumAutoPublishScheduler: song id=$songId loadFromDbById returned null, skipping")
                return
            }

        // Если флаг исчез между SELECT и загрузкой — skip.
        if (!song.newsPremiumPublishPending) {
            return
        }

        // Если хотя бы один канал уже занят предыдущей публикацией (RENDERING/PUBLISHING) — пропускаем,
        // чтобы не запускать параллельные публикации. На следующем тике продолжим.
        val tgBusy = song.telegramAutoPublishState in setOf("rendering", "publishing")
        val vkBusy = song.vkAutoPublishState in setOf("rendering", "publishing")
        if (tgBusy || vkBusy) {
            println("PremiumAutoPublishScheduler: song id=${song.id} busy: tgState=${song.telegramAutoPublishState} vkState=${song.vkAutoPublishState}, skip")
            return
        }

        // Если воздух-публикация уже произошла (id заполнены) — закрываем задачу как выполненную.
        // Это редкий случай: песня попала в эфир до того, как премиум-тик её обработал.
        val tgDone = song.idTelegramDemo.isNotEmpty() || song.newsPremiumTelegramSent
        val vkDone = song.idVk.isNotEmpty() || song.newsPremiumVkSent
        if (tgDone && vkDone) {
            song.newsPremiumPublishPending = false
            if (song.premiumAutoPublishState != "FAILED") {
                song.premiumAutoPublishState = "COMPLETE"
            }
            song.saveToDb()
            println("PremiumAutoPublishScheduler: song id=${song.id} → both channels done, newsPremiumPublishPending=false")
            return
        }

        // Телеграм
        if (!tgDone) {
            val tgResult =
                TelegramAutoPublishService.publishToTelegram(
                    song = song,
                    allowPastDate = true,
                    publicationType = PublicationType.PREMIUM,
                    persistMessageId = false,
                )
            println("PremiumAutoPublishScheduler: song id=${song.id} telegram result: state=${tgResult.state.code} messageId=${tgResult.messageId} error=${tgResult.error}")
            if (tgResult.state.code == "send_failed") {
                handleFailure(song, "telegram: ${tgResult.error}")
            }
        }

        // Перезагрузка после publishToTelegram (он мог изменить telegramAutoPublishLastAttemptAt
        // и установить newsPremiumTelegramSent). Это важно для корректной проверки vkDone ниже.
        val reloaded =
            Song.loadFromDbById(
                id = songId,
                database = WORKING_DATABASE,
                storageService = KSS_APP,
                storageApiClient = SAC_APP,
            ) ?: return
        if (!reloaded.newsPremiumPublishPending) return // Между тиками могли уже закрыть задачу.

        // VK
        if (!reloaded.idVk.isNotEmpty() && !reloaded.newsPremiumVkSent) {
            val vkResult =
                VkAutoPublishService.publishToVk(
                    song = reloaded,
                    type = PublicationType.PREMIUM,
                    persistPostId = false,
                )
            println("PremiumAutoPublishScheduler: song id=${reloaded.id} vk result: state=${vkResult.state.code} postId=${vkResult.postId} error=${vkResult.error}")
            if (vkResult.state.code == "send_failed") {
                handleFailure(reloaded, "vk: ${vkResult.error}")
            }
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
     * Помечает попытку как неуспешную: инкрементирует premiumAttemptCount, при достижении лимита —
     * newsPremiumPublishPending=false и premiumAutoPublishState=FAILED. После FAILED админ видит
     * проблему в UI и может сбросить через ручной endpoint.
     */
    private fun handleFailure(
        song: Song,
        error: String,
    ) {
        song.premiumAttemptCount = song.premiumAttemptCount + 1
        song.premiumAutoPublishLastError = error
        val maxAttempts = KaraokeProperties.getLong("premiumAutoPublishMaxAttempts").let { if (it <= 0) 3L else it }
        if (song.premiumAttemptCount >= maxAttempts) {
            song.newsPremiumPublishPending = false
            song.premiumAutoPublishState = "FAILED"
        }
        song.saveToDb()
    }

    private fun closeIfBothChannelsDone(song: Song) {
        val tgDone = song.idTelegramDemo.isNotEmpty() || song.newsPremiumTelegramSent
        val vkDone = song.idVk.isNotEmpty() || song.newsPremiumVkSent
        if (tgDone && vkDone) {
            song.newsPremiumPublishPending = false
            if (song.premiumAutoPublishState != "FAILED") {
                song.premiumAutoPublishState = "COMPLETE"
            }
            song.saveToDb()
            println("PremiumAutoPublishScheduler: song id=${song.id} → COMPLETE (tgDone=$tgDone vkDone=$vkDone)")
        }
    }

    /**
     * Cheap SELECT id песен с newsPremiumPublishPending=true в player_readiness_flags.
     * Без загрузки полного Song. Этот же паттерн, что в VkAutoPublishScheduler.loadRenderingCandidateIds.
     */
    private fun loadPendingIds(): List<Long> {
        val connection = WORKING_DATABASE.getConnection() ?: return emptyList()
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
            println("PremiumAutoPublishScheduler.loadPendingIds SQLException: ${e.message}")
        }
        return result
    }
}
