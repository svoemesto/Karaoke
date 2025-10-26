// file: src/main/kotlin/com/svoemesto/karaokeapp/config/SecurityConfig.kt

package com.svoemesto.karaokeapp.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val customUserDetailsService: UserDetailsService
) {

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
        try {
            http
                .authorizeHttpRequests { requests ->
                    requests
                        .requestMatchers("/api/private/**").authenticated()
                        .anyRequest().permitAll()
                }
                .sessionManagement { session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                }
                .csrf { csrfConfigurer ->
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