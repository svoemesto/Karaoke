package com.svoemesto.karaokeweb.config // Замените на актуальный пакет вашего приложения

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

/**
 * Конфигурация для security .
 *
 * @see AGENTS.md
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {
    // karaoke-web не сканирует пакет com.svoemesto.karaokeapp.config (нет @ComponentScan туда), поэтому
    // одноимённый бин из karaoke-app (SecurityConfig.kt) сюда не долетает — нужен собственный.
    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers("/api/**")
                    .permitAll() // Разрешить доступ к /api/**
                    // Добавьте другие публичные пути, если нужно, например:
                    // .requestMatchers("/", "/index.html", "/static/**", "/webjars/**").permitAll()
                    .anyRequest()
                    .permitAll() // Позволяет всем остальным запросам быть доступными без аутентификации
                // Если вы хотите, чтобы остальные пути (не /api/**) всё же требовали логина,
                // замените .anyRequest().permitAll() на:
                // .anyRequest().authenticated()
            }
            // Отключаем CSRF, если ваше API не использует сессии и CSRF-токены (обычно так для REST API)
            .csrf { csrf -> csrf.disable() }

        return http.build()
    }
}
