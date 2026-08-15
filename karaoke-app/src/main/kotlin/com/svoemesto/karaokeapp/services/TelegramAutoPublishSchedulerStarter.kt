package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Автозапуск планового бота автопубликации при старте приложения (Фаза 2,
 * specs/113-telegram-demo-publish).
 *
 * По образцу [TelegramUpdatesConsumerStarter] (Фаза 1): логирует старт scheduler'а,
 * если `telegramAutoPublishEnabled=true`. Сам [TelegramAutoPublishScheduler] —
 * Spring `@Component` с `@Scheduled` и стартует автоматически при `@EnableScheduling`
 * (есть в `KaraokeAppApplication`); этот starter не управляет lifecycle scheduler'а,
 * а лишь фиксирует в логе, что бот активирован (удобно для отладки «почему не
 * публикует» — видно, что флаг включён при старте).
 *
 * Endpoint `POST /api/song/publishToTelegramNow` (кнопка «Опубликовать сейчас»)
 * работает независимо от `telegramAutoPublishEnabled` — он всегда доступен.
 *
 * @see archive/docs/features/telegram-auto-publish.md
 */
@Component
class TelegramAutoPublishSchedulerStarter {
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (KaraokeProperties.getBoolean("telegramAutoPublishEnabled")) {
            println("TelegramAutoPublishScheduler: старт (telegramAutoPublishEnabled=true) at ${java.time.Instant.now()}")
        }
        // Диагностика: проверим, что @Scheduled-бин вообще зарегистрирован в Spring-контексте.
        val ctx =
            org.springframework.web.context.ContextLoader
                .getCurrentWebApplicationContext()
        if (ctx != null) {
            val beans = ctx.getBeansOfType(TelegramAutoPublishScheduler::class.java)
            println("TelegramAutoPublishSchedulerStarter: beans of type TelegramAutoPublishScheduler in context = ${beans.size} (expected=1)")
        } else {
            println("TelegramAutoPublishSchedulerStarter: WARN — no WebApplicationContext available")
        }
    }
}
