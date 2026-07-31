package com.svoemesto.karaokeapp.services

/**
 * Результат одного цикла автопубликации демо-версии песни в Telegram (Фаза 2,
 * specs/113-telegram-demo-publish).
 *
 * Возвращается [TelegramAutoPublishService.publishToTelegram] (и
 * [TelegramAutoPublishService.onRenderCompleted]) — содержит финальное
 * состояние песни после попытки публикации и (опционально) `message_id` от
 * Telegram или текст ошибки. Сервис сам записывает эти же значения в
 * `player_readiness_flags` песни через [com.svoemesto.karaokeapp.model.Song.saveToDb];
 * этот класс — лишь синхронный ответ вызывающему коду (scheduler'у или endpoint'у
 * «Опубликовать сейчас»).
 *
 * @see docs/features/telegram-auto-publish.md
 */
data class TelegramAutoPublishResult(
    val state: TelegramAutoPublishState,
    val messageId: String? = null,
    val error: String? = null,
)
