// file: src/main/kotlin/com/svoemesto/karaokeapp/config/CorsConfig.kt

package com.svoemesto.karaokeapp.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**") // Применить ко всем путям
            .allowedOriginPatterns("http://localhost:7906") // Разрешить только с этого origin
            // .allowedOriginPatterns("http://localhost:3000") // Для dev server Vue
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD") // Укажите нужные методы
            .allowedHeaders("*") // Разрешить все заголовки
            .allowCredentials(true) // Разрешить credentials
    }
}