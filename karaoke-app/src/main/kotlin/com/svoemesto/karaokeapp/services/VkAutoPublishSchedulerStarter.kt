package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Автозапуск планового бота автопубликации в ВКонтакте при старте приложения
 * (specs/121-vk-news-auto-publish).
 *
 * По образцу [TelegramAutoPublishSchedulerStarter] (Фаза 2): логирует старт
 * scheduler'а, если `vkAutoPublishEnabled=true`. Сам [VkAutoPublishScheduler] —
 * Spring `@Component` с `@Scheduled` и стартует автоматически при
 * `@EnableScheduling` (есть в `KaraokeAppApplication`); этот starter не управляет
 * lifecycle scheduler'а, а лишь фиксирует в логе, что бот активирован (удобно для
 * отладки «почему не публикует» — видно, что флаг включён при старте).
 *
 * Endpoint `POST /api/song/publishToVkNow` (кнопки «Опубликовать во ВК (air)» /
 * «Опубликовать во ВК (premium)») работает независимо от `vkAutoPublishEnabled` —
 * он всегда доступен.
 *
 * @see docs/features/vk-news-auto-publish.md
 */
@Component
class VkAutoPublishSchedulerStarter {
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (KaraokeProperties.getBoolean("vkAutoPublishEnabled")) {
            println("VkAutoPublishScheduler: старт (vkAutoPublishEnabled=true)")
        }
    }
}
