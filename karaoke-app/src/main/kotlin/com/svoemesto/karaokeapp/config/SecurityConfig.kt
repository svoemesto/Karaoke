// file: src/main/kotlin/com/svoemesto/karaokeapp/config/SecurityConfig.kt

package com.svoemesto.karaokeapp.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
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
    private val logger = LoggerFactory.getLogger(SecurityConfig::class.java)

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        logger.info(">>> Creating PasswordEncoder...")
        val encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
        logger.info("<<< PasswordEncoder created.")
        return encoder
    }

    @Bean
    @Order(1)
    fun webSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        logger.info(">>> Creating WebSecurityFilterChain (Order 1)...")
        // BR-114 (аудит #336, пакет PKG-S3-22): состояние периметра зафиксировано явно.
        //
        // Сейчас `.anyRequest().permitAll()` — это НЕ «так и надо», а следствие того, что
        // механизма аутентификации в приложении нет: нет ни formLogin/httpBasic, ни
        // UserDetailsService, ни фильтра, выпускающего аутентифицированного субъекта.
        // Правило `authenticated()` для `/api/private/**` защищает пустое множество —
        // контроллеров с таким путём в репозитории нет.
        //
        // Включить `authenticated()` «просто так» нельзя: админка (единственный UI)
        // получила бы 401 на каждый запрос без возможности войти. Выбор механизма
        // (form-login / basic-auth на nginx / mTLS / SSO) — решение владельца.
        //
        // Безопасный шов, который уже работает: `AdminApiTokenFilter` — при заданном
        // `karaoke.security.admin-token` (env `KARAOKE_ADMIN_TOKEN`) admin-эндпоинты
        // требуют заголовок `X-Admin-Token`, иначе 401. Без токена фильтр пропускает
        // запросы, но пишет WARN об открытом admin-API. Подробности — в KDoc фильтра
        // и в specs/394-pkg-s3-22/report.md (§ BR-114).
        logger.warn(
            "Периметр admin-API: authorization по-прежнему permitAll (BR-114). " +
                "Защита включается настройкой karaoke.security.admin-token (AdminApiTokenFilter); " +
                "механизм аутентификации — за владельцем.",
        )
        try {
            http
                .authorizeHttpRequests { requests ->
                    requests
                        .requestMatchers("/api/private/**")
                        .authenticated()
                        .anyRequest()
                        .permitAll()
                }.sessionManagement { session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                }.csrf { csrfConfigurer ->
                    csrfConfigurer.disable()
                }
            logger.info("<<< WebSecurityFilterChain (Order 1) created successfully.")
            return http.build()
        } catch (e: Exception) {
            logger.error("!!! Error creating WebSecurityFilterChain (Order 1)", e)
            throw e
        }
    }
}
