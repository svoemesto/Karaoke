// file: src/main/kotlin/com/svoemesto/karaokeapp/config/SecurityConfig.kt

package com.svoemesto.karaokeapp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
// УБРАНО: import org.springframework.security.oauth2.jwt.JwtDecoder // Удалено: не нужно для открытого /api/**
// УБРАНО: import org.springframework.security.web.util.matcher.AntPathRequestMatcher // Удалено: не нужно

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder()
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers("/api/**").permitAll() // Разрешить все запросы к /api/**
                    // УБРАНО: .requestMatchers("/.well-known/**").permitAll()
                    // УБРАНО: .requestMatchers("/oauth2/**").permitAll()
                    .anyRequest().authenticated() // Все остальные запросы требуют аутентификации
            }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .csrf { csrf -> csrf.disable() }
        // УБРАНО: oauth2ResourceServer, так как /api/** открыт
        // .oauth2ResourceServer { oauth2 -> ... }
        // УБРАНО: userDetailsService, так как аутентификация не требуется для /api/**
        // .userDetailsService(...) // Убираем

        return http.build()
    }

    // УБРАНО: jwtDecoder метод
    // УБРАНО: customUserDetailsService метод
}