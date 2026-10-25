package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.Message
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SseNotification
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

// Автозапуск фонового потребителя апдейтов при старте приложения (если включён в свойствах) - не зависит
// от ручного нажатия кнопки в UI, в отличие от KaraokeProcessWorker.

/**
 * Класс Telegram Updates Consumer Starter.
 *
 * @see archive/docs/features/async-process-queue.md
 */
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
 * (см. Song.parseTelegramPostSongId/parseTelegramPostSongVersion) и записывает message_id через
 * штатный Song.saveToDb() - чтобы сработали SSE recordChange и LOCAL/SERVER sync.
 *
 * Паттерн потока - по образцу KaraokeProcessWorker (isWork-флаг + companion start/stop), но запускается
 * в явном daemon-Thread, а не блокирует вызывающий (HTTP/event-listener) поток.
 */

/**
 * Singleton-объект Telegram Updates Consumer.
 *
 * @see archive/docs/features/async-process-queue.md
 */
object TelegramUpdatesConsumer {
    @Volatile var isWork: Boolean = false

    // BR-158 (аудит #336, пакет `PKG-S3-23`): `start()` был незащищённым check-then-act
    // (`if (isWork) return; isWork = true`) — одновременный вызов из `ApplicationReadyEvent`
    // и из монитора поднимал ДВА long-poll потока. Теперь старт — атомарный
    // compareAndSet, и поток поднимается ровно один.
    private val started = AtomicBoolean(false)

    private val client = TelegramApiClient()

    // BR-158: категория логов long-poll Telegram.
    private val consumerLog = LoggerFactory.getLogger("infra.telegram.polling")

    fun start() {
        if (!started.compareAndSet(false, true)) return
        isWork = true
        Thread(::doStart)
            .apply {
                isDaemon = true
                name = "telegram-updates-consumer"
            }.start()
    }

    fun stop() {
        isWork = false
        // BR-158: после явной остановки поток можно поднять снова (иначе `started`
        // навсегда остался бы true и повторный `start()` ничего не делал).
        started.set(false)
    }

    private fun doStart() {
        consumerLog.info("[{}] TelegramUpdatesConsumer: старт", Instant.now())
        // BR-158: `deleteWebhook()` стоял ВНЕ try — сетевая ошибка на старте убивала
        // поток, но `isWork` оставался true, и повторный `start()` (в т.ч. одноклик-fix
        // из `TelegramPollingDisabledCheck`) молча ничего не делал до перезапуска
        // приложения. Теперь ошибка логируется и long-poll продолжается.
        try {
            client.deleteWebhook()
        } catch (e: Exception) {
            consumerLog.warn("[{}] TelegramUpdatesConsumer: deleteWebhook не удался, продолжаем long-poll", Instant.now(), e)
        }
        try {
            while (isWork) {
                try {
                    val offset = KaraokeProperties.getLong("telegramUpdatesOffset")
                    val response = client.getUpdates(offset = offset, timeoutSec = 25)
                    if (!response.ok) {
                        consumerLog.warn(
                            "[{}] TelegramUpdatesConsumer: getUpdates вернул ok=false ({})",
                            Instant.now(),
                            response.description,
                        )
                        Thread.sleep(5_000)
                        continue
                    }
                    response.result.forEach { update -> processUpdate(update.updateId, update.channelPost) }
                } catch (e: Exception) {
                    consumerLog.warn("[{}] TelegramUpdatesConsumer: ошибка long-poll", Instant.now(), e)
                    Thread.sleep(5_000)
                }
            }
            consumerLog.info("[{}] TelegramUpdatesConsumer: остановлен", Instant.now())
        } finally {
            // BR-158: флаг не остаётся поднятым после смерти потока — иначе функция
            // «отключалась молча» до перезапуска приложения.
            isWork = false
            started.set(false)
        }
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
        val songId = Song.parseTelegramPostSongId(text)
        val songVersion = Song.parseTelegramPostSongVersion(text)

        if (songId == null || songVersion == null) {
            notifyManualAttention(
                "Не удалось распознать вышедший пост Telegram (message_id=${post.messageId}) - проставьте ссылку в песне вручную.",
            )
            return
        }

        val song = Song.loadFromDbById(id = songId, database = WORKING_DATABASE, storageService = KSS_APP, storageApiClient = SAC_APP)
        if (song == null) {
            notifyManualAttention(
                "Вышедший пост Telegram (message_id=${post.messageId}) ссылается на песню id=$songId, которая не найдена в LOCAL БД.",
            )
            return
        }

        val currentId = song.idTelegramFor(songVersion)
        if (currentId.isNotBlank() && currentId != "-") {
            // Уже проставлено (повторный апдейт при рестарте long-poll, либо уже вписано вручную) -
            // идемпотентно ничего не делаем, чтобы не затирать реальный id.
            return
        }

        song.fields[song.telegramIdSettingField(songVersion)] = post.messageId.toString()
        // specs/357-folder-import-overwrite (FR-140): TelegramUpdatesConsumer — обработка апдейтов от Telegram между load и save.
        song.saveToDbLocked()
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
