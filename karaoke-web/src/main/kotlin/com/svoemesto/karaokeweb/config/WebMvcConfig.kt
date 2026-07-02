package com.svoemesto.karaokeweb.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val siteAuthInterceptor: SiteAuthInterceptor,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(siteAuthInterceptor)
            .addPathPatterns("/api/public/account/**", "/api/public/auth/me", "/api/public/auth/logout")
    }
}
