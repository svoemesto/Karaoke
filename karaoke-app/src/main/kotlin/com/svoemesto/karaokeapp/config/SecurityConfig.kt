// file: src/main/kotlin/com/svoemesto/karaokeapp/config/SecurityConfig.kt

package com.svoemesto.karaokeapp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.util.*

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val customUserDetailsService: UserDetailsService
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder()
    }

    @Bean
    @Order(1) // Более низкий приоритет для общей безопасности
    fun webSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        // Эта цепочка будет обрабатывать все остальные запросы
        http
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers("/api/private/**").authenticated()
                    .anyRequest().permitAll()
            }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .cors { cors ->
                cors.configurationSource(corsConfigurationSource())
            }
            .csrf { csrfConfigurer ->
                csrfConfigurer.disable()
            }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        val origins = System.getenv("CORS_ALLOWED_ORIGINS")?.split(",")?.map { it.trim() }?.toTypedArray()
            ?: arrayOf("http://localhost:3000", "http://127.0.0.1:3000") // Обновите, если ваш фронтенд на другом порту
        configuration.allowedOrigins = origins.asList()
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}