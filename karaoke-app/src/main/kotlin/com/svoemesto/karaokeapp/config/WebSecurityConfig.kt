// file: src/main/kotlin/com/svoemesto/karaokeapp/config/WebSecurityConfig.kt

package com.svoemesto.karaokeapp.config

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.UUID

// Оставляем @EnableWebSecurity только здесь
@Configuration
@EnableWebSecurity
class WebSecurityConfig {

    // --- Цепочка фильтров для Authorization Server (очень высокий приоритет) ---
    @Bean
    @Order(-2) // Высокий приоритет
    fun authorizationServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        // Применяем стандартные настройки AS
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http)

        // Настройка исключений для AS (например, для /oauth2/token), если нужно
        http
            .exceptionHandling { exceptions ->
                // exceptions.defaultAuthenticationEntryPointFor(...)
            }

        return http.build()
    }

    // --- Цепочка фильтров для Resource Server (API) ---
    @Bean
    @Order(0) // Приоритет ниже, чем у AS
    fun resourceServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers("/api/**").permitAll() // Разрешить все запросы к /api/**
                    .anyRequest().authenticated() // Все остальные запросы требуют аутентификации
            }
            .sessionManagement { session ->
                session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)
            }
            .csrf { csrf -> csrf.disable() }
        // .oauth2ResourceServer { oauth2 -> oauth2.jwt { jwt -> jwt.decoder(jwtDecoder()) } } // Включить при необходимости

        return http.build()
    }

    // --- Бины для AS ---
    @Bean
    fun registeredClientRepository(): RegisteredClientRepository {
        val registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("vue-client")
            .clientSecret("{noop}secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) // SPA
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("http://localhost:7906/callback")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope(OidcScopes.EMAIL)
            .scope("api:read")
            .scope("api:write")
            .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
            .build()

        return InMemoryRegisteredClientRepository(registeredClient)
    }

    @Bean
    fun authorizationServerSettings(): AuthorizationServerSettings {
        return AuthorizationServerSettings.builder().build()
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource())
    }

    @Bean
    fun jwkSource(): JWKSource<SecurityContext> {
        val keyPair = generateRsaKey()
        val publicKey = keyPair.public as RSAPublicKey
        val privateKey = keyPair.private as RSAPrivateKey
        val rsaKey = RSAKey.Builder(publicKey)
            .keyID(UUID.randomUUID().toString())
            .privateKey(privateKey)
            .build()
        val jwkSet = JWKSet(rsaKey)
        return ImmutableJWKSet(jwkSet)
    }

    private fun generateRsaKey(): KeyPair {
        return try {
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(2048)
            keyPairGenerator.generateKeyPair()
        } catch (ex: Exception) {
            throw IllegalStateException(ex)
        }
    }

    // --- Бины для RS (если потребуется) ---
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder()
    }

    // Убедитесь, что CustomUserDetailsService аннотирован @Service
    // @Bean
    // fun customUserDetailsService(): CustomUserDetailsService { ... } // Не нужно определять здесь
}