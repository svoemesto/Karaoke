// file: src/main/kotlin/com/svoemesto/karaokeapp/config/WellKnownSecurityConfig.kt

package com.svoemesto.karaokeapp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.AntPathRequestMatcher

@Configuration
class WellKnownSecurityConfig {

    @Bean
    @Order(-1) // Наивысший приоритет
    fun wellKnownSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        println("Creating Well-Known Security Filter Chain Bean") // <-- Добавьте это
        http
            .securityMatcher(AntPathRequestMatcher("/.well-known/**"))
            .authorizeHttpRequests { requests ->
                requests.anyRequest().permitAll() // Разрешить всем
            }
            .sessionManagement { session -> session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS) }
            .csrf { csrf -> csrf.disable() }

        return http.build()
    }
}