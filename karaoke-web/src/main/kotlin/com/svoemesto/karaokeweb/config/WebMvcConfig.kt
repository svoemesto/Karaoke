package com.svoemesto.karaokeweb.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Конфигурация для web mvc .
 *
 * @see AGENTS.md
 */
@Configuration
class WebMvcConfig(
    private val siteAuthInterceptor: SiteAuthInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry
            .addInterceptor(siteAuthInterceptor)
            .addPathPatterns("/api/public/account/**", "/api/public/auth/me", "/api/public/auth/logout")
            // Список тарифов — витринные данные (цена/срок, без персональных данных), должен быть
            // виден анонимам на /premium и странице песни ДО регистрации — иначе цена не мотивирует
            // зарегистрироваться. Остальные подписочные эндпоинты (price/create/list/cancel) остаются
            // за авторизацией — там персональные скидки/статус заказа.
            .excludePathPatterns("/api/public/account/subscription/tariffs")
    }
}
