package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.Message
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.SseNotification
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Instant

// Автозапуск фонового потребителя апдейтов при старте приложения (если включён в свойствах) - не зависит
// от ручного нажатия кнопки в UI, в отличие от KaraokeProcessWorker.
@Component
class TelegramUpdatesConsumerStarter {
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (KaraokeProperties.getBoolean("telegramPollingEnabled")) {
            TelegramUpdatesConsumer.start()
        }
    }
}

/**
 * Фаза 1 автоматизации публикации в Telegram: фоновый демон-поток, long-polling getUpdates.
 * Ловит вышедшие channel_post канала @svoemestokaraoke, сопоставляет их с песней/версией по содержимому
 * (см. Settings.parseTelegramPostSongId/parseTelegramPostSongVersion) и записывает message_id через
 * штатный Settings.saveToDb() - чтобы сработали SSE recordChange и LOCAL/SERVER sync.
 *
 * Паттерн потока - по образцу KaraokeProcessWorker (isWork-флаг + companion start/stop), но запускается
 * в явном daemon-Thread, а не блокирует вызывающий (HTTP/event-listener) поток.
 */
object TelegramUpdatesConsumer {
    @Volatile var isWork: Boolean = false
    private val client = TelegramApiClient()

    fun start() {
        if (isWork) return
        isWork = true
        Thread(::doStart)
            .apply {
                isDaemon = true
                name = "telegram-updates-consumer"
            }.start()
    }

    fun stop() {
        isWork = false
    }

    private fun doStart() {
        println("[${Instant.now()}] TelegramUpdatesConsumer: старт")
        client.deleteWebhook()
        while (isWork) {
            try {
                val offset = KaraokeProperties.getLong("telegramUpdatesOffset")
                val response = client.getUpdates(offset = offset, timeoutSec = 25)
                if (!response.ok) {
                    println("[${Instant.now()}] TelegramUpdatesConsumer: getUpdates вернул ok=false (${response.description})")
                    Thread.sleep(5_000)
                    continue
                }
                response.result.forEach { update -> processUpdate(update.updateId, update.channelPost) }
            } catch (e: Exception) {
                println("[${Instant.now()}] TelegramUpdatesConsumer: ошибка long-poll: ${e.message}")
                Thread.sleep(5_000)
            }
        }
        println("[${Instant.now()}] TelegramUpdatesConsumer: остановлен")
    }

    private fun processUpdate(
        updateId: Long,
        channelPost: TelegramMessage?,
    ) {
        try {
            if (channelPost != null && isOurChannel(channelPost)) {
                handleChannelPost(channelPost)
            }
        } catch (e: Exception) {
            println("[${Instant.now()}] TelegramUpdatesConsumer: ошибка обработки update $updateId: ${e.message}")
        } finally {
            // offset сохраняется ПОСЛЕ обработки (успешной или нет), но обязательно для каждого апдейта -
            // иначе long-poll будет бесконечно возвращать один и тот же сбойный апдейт. Персистентность
            // курсора в KaraokeProperties даёт catch-up "бесплатно": апдейты Telegram хранит ~24 часа,
            // так что после простоя admin-машины они добираются при следующем старте.
            KaraokeProperties.set("telegramUpdatesOffset", updateId + 1)
        }
    }

    private fun isOurChannel(post: TelegramMessage): Boolean {
        val chatId = KaraokeProperties.getString("telegramChannelChatId")
        val username = KaraokeProperties.getString("telegramChannelUsername")
        if (chatId.isNotBlank() && post.chat.id.toString() == chatId) return true
        if (username.isNotBlank() && post.chat.username != null && post.chat.username.equals(username, ignoreCase = true)) return true
        return false
    }

    private fun handleChannelPost(post: TelegramMessage) {
        val text = post.text ?: post.caption ?: ""
        val songId = Settings.parseTelegramPostSongId(text)
        val songVersion = Settings.parseTelegramPostSongVersion(text)

        if (songId == null || songVersion == null) {
            notifyManualAttention(
                "Не удалось распознать вышедший пост Telegram (message_id=${post.messageId}) - проставьте ссылку в песне вручную.",
            )
            return
        }

        val sett = Settings.loadFromDbById(id = songId, database = WORKING_DATABASE, storageService = KSS_APP, storageApiClient = SAC_APP)
        if (sett == null) {
            notifyManualAttention(
                "Вышедший пост Telegram (message_id=${post.messageId}) ссылается на песню id=$songId, которая не найдена в LOCAL БД.",
            )
            return
        }

        val currentId = sett.idTelegramFor(songVersion)
        if (currentId.isNotBlank() && currentId != "-") {
            // Уже проставлено (повторный апдейт при рестарте long-poll, либо уже вписано вручную) -
            // идемпотентно ничего не делаем, чтобы не затирать реальный id.
            return
        }

        sett.fields[sett.telegramIdSettingField(songVersion)] = post.messageId.toString()
        sett.saveToDb()
        println(
            "[${Instant.now()}] TelegramUpdatesConsumer: записана ссылка Telegram (${songVersion.text}) для песни id=$songId, message_id=${post.messageId}",
        )
    }

    private fun notifyManualAttention(body: String) {
        try {
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "warning",
                        head = "Telegram: требуется ручная проверка",
                        body = body,
                    ),
                ),
            )
        } catch (e: Exception) {
            println(e.message)
        }
    }
}
