package com.svoemesto.karaokeapp.services

/**
 * Результат одного цикла автопубликации в группу ВКонтакте (specs/121-vk-news-auto-publish).
 *
 * Возвращается [VkAutoPublishService.publishToVk] (и
 * [VkAutoPublishService.onRenderCompleted]) — содержит финальное состояние
 * после попытки публикации и (опционально) id поста ВК (`-<groupId>_<postId>`,
 * записывается в `Song.idVk`) или текст ошибки. Сервис сам записывает эти же
 * значения в `player_readiness_flags` песни через
 * [com.svoemesto.karaokeapp.model.Song.saveToDb]; этот класс — лишь
 * синхронный ответ вызывающему коду (scheduler'у или endpoint'у
 * «Опубликовать во ВК (air/premium)»).
 *
 * @see docs/features/vk-news-auto-publish.md
 */
data class VkAutoPublishResult(
    val state: VkAutoPublishState,
    val postId: String? = null,
    val error: String? = null,
)
