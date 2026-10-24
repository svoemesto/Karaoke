package com.svoemesto.karaokeweb.config

import com.svoemesto.karaokeweb.services.KaraokeProperties
import com.svoemesto.karaokeweb.services.RateLimitInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Конфигурация для web mvc .
 *
 * Регистрирует:
 *  - SiteAuthInterceptor для эндпоинтов под /api/public/account/ и /api/siteusers/.
 *  - Два НЕЗАвисимых экземпляра RateLimitInterceptor для эндпоинтов /api/public/song-picture/ и
 *    /api/public/song-vk-image/ (FR-010, SC-008). Лимиты берутся из KaraokeProperties.
 *
 * @see archive/docs/features/site-traffic-resilience.md (FR-010)
 */
@Configuration
class WebMvcConfig(
    private val siteAuthInterceptor: SiteAuthInterceptor,
    private val properties: KaraokeProperties,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry
            .addInterceptor(siteAuthInterceptor)
            .addPathPatterns(
                "/api/public/account/**",
                "/api/public/auth/me",
                "/api/public/auth/logout",
                // Админские endpoint'ы для управления share-ссылками (webvue3 admin):
                // требуется залогиненный site-user. Проверка isEditor — внутри контроллера.
                "/api/siteusers/**",
            )
            // Список тарифов — витринные данные (цена/срок, без персональных данных), должен быть
            // виден анонимам на /premium и странице песни ДО регистрации — иначе цена не мотивирует
            // зарегистрироваться. Остальные подписочные эндпоинты (price/create/list/cancel) остаются
            // за авторизацией — там персональные скидки/статус заказа.
            .excludePathPatterns("/api/public/account/subscription/tariffs")

        // FR-010: rate-limit для song-picture и song-vk-image (защита от bot-storm).
        // Аудит BR-004 (OpenProject #378): раньше здесь мутировался ОДИН И ТОТ ЖЕ singleton-бин
        // RateLimitInterceptor — второй `apply` перетирал `endpointName`/`limitPerMinute` первого,
        // поэтому оба URL-паттерна работали с лимитом vk-image и делили общий bucket. Теперь
        // создаются ДВА независимых экземпляра: имя endpoint'а и лимит — конструкторные
        // (immutable), состояние (bucket'ы) у каждого своё.
        registry
            .addInterceptor(RateLimitInterceptor("song-picture", properties.rateLimitSongPicturePerMinute))
            .addPathPatterns("/api/public/song-picture/**")

        registry
            .addInterceptor(RateLimitInterceptor("song-vk-image", properties.rateLimitSongVkImagePerMinute))
            .addPathPatterns("/api/public/song-vk-image/**")
    }
}
