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
 *  - Два экземпляра RateLimitInterceptor для эндпоинтов /api/public/song-picture/ и
 *    /api/public/song-vk-image/ (FR-010, SC-008). Лимиты берутся из KaraokeProperties.
 *
 * @see archive/docs/features/site-traffic-resilience.md (FR-010)
 */
@Configuration
class WebMvcConfig(
    private val siteAuthInterceptor: SiteAuthInterceptor,
    private val rateLimitInterceptor: RateLimitInterceptor,
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
        // Используем один @Component RateLimitInterceptor, но конфигурируем endpointName/limitPerMinute
        // перед каждым addInterceptor. Так как interceptor один — он shared между обоими URL pattern'ами,
        // ключ buckets строится с endpointName, поэтому bucket'ы НЕ пересекаются.
        val songPictureInterceptor =
            rateLimitInterceptor.apply {
                endpointName = "song-picture"
                limitPerMinute = properties.rateLimitSongPicturePerMinute
            }
        registry
            .addInterceptor(songPictureInterceptor)
            .addPathPatterns("/api/public/song-picture/**")

        val songVkImageInterceptor =
            rateLimitInterceptor.apply {
                endpointName = "song-vk-image"
                limitPerMinute = properties.rateLimitSongVkImagePerMinute
            }
        registry
            .addInterceptor(songVkImageInterceptor)
            .addPathPatterns("/api/public/song-vk-image/**")
    }
}
