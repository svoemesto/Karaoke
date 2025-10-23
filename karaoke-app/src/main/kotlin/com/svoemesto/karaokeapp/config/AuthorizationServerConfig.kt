//// file: src/main/kotlin/com/svoemesto/karaokeapp/config/AuthorizationServerConfig.kt
//
//package com.svoemesto.karaokeapp.config
//
//import com.nimbusds.jose.jwk.JWKSet
//import com.nimbusds.jose.jwk.RSAKey
//import com.nimbusds.jose.jwk.source.ImmutableJWKSet
//import com.nimbusds.jose.jwk.source.JWKSource
//import com.nimbusds.jose.proc.SecurityContext
//import org.springframework.context.annotation.Bean
//import org.springframework.context.annotation.Configuration
//import org.springframework.core.annotation.Order
//import org.springframework.security.config.annotation.web.builders.HttpSecurity
//import org.springframework.security.oauth2.core.AuthorizationGrantType
//import org.springframework.security.oauth2.core.ClientAuthenticationMethod
//import org.springframework.security.oauth2.core.oidc.OidcScopes
//import org.springframework.security.oauth2.jwt.JwtDecoder
//import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
//import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
//import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
//// --- ПРАВИЛЬНЫЕ ИМПОРТЫ ДЛЯ applyDefaultSecurity ---
//import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
//// ---
//import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
//import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
//import org.springframework.security.web.SecurityFilterChain
//import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
//import org.springframework.security.web.util.matcher.AntPathRequestMatcher // Явный импорт AntPathRequestMatcher
//import java.security.KeyPair
//import java.security.KeyPairGenerator
//import java.security.interfaces.RSAPrivateKey
//import java.security.interfaces.RSAPublicKey
//import java.util.UUID
//
//@Configuration
//class AuthorizationServerConfig {
//
//    // --- УСТАНАВЛИВАЕМ ВЫСОКИЙ ПРИОРИТЕТ ---
////    @Bean
////    @Order(-1) // Наивысший приоритет для цепочки Authorization Server
////    fun authorizationServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
////        // --- ПРИМЕНЯЕМ СТАНДАРТНЫЕ НАСТРОЙКИ AS ---
////        // Это должно настроить /.well-known/openid-configuration как публичный
////        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http)
////
////        // --- НЕ ВЫЗЫВАЕМ authorizeHttpRequests ПОСЛЕ applyDefaultSecurity ---
////        // http.authorizeHttpRequests { ... } // <-- НЕ ДЕЛАЕМ ЭТО
////
////        // Обработка исключений (опционально, но часто используется)
////        http
////            .exceptionHandling { exceptions ->
////                // Для эндпоинтов, требующих аутентификации клиента (например, /oauth2/token)
////                // Можно настроить специфичный EntryPoint, но стандартный часто подходит
////                // exceptions.defaultAuthenticationEntryPointFor(...)
////            }
////        // УБРАНО: .oauth2ResourceServer { ... } - Не нужно в цепочке AS
////
////        return http.build()
////    }
//
////    @Bean
////    fun registeredClientRepository(): RegisteredClientRepository {
////        val registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
////            .clientId("vue-client")
////            .clientSecret("{noop}secret")
////            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) // SPA
////            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
////            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
////            .redirectUri("http://localhost:7906/callback")
////            .scope(OidcScopes.OPENID)
////            .scope(OidcScopes.PROFILE)
////            .scope(OidcScopes.EMAIL)
////            .scope("api:read")
////            .scope("api:write")
////            .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
////            .build()
////
////        return InMemoryRegisteredClientRepository(registeredClient)
////    }
//
////    @Bean
////    fun authorizationServerSettings(): AuthorizationServerSettings {
////        return AuthorizationServerSettings.builder().build()
////    }
//
////    @Bean
////    fun jwtDecoder(): JwtDecoder {
////        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource())
////    }
//
////    @Bean
////    fun jwkSource(): JWKSource<SecurityContext> {
////        val keyPair = generateRsaKey()
////        val publicKey = keyPair.public as RSAPublicKey
////        val privateKey = keyPair.private as RSAPrivateKey
////        val rsaKey = RSAKey.Builder(publicKey)
////            .keyID(UUID.randomUUID().toString())
////            .privateKey(privateKey)
////            .build()
////        val jwkSet = JWKSet(rsaKey)
////        return ImmutableJWKSet(jwkSet)
////    }
//
//    private fun generateRsaKey(): KeyPair {
//        return try {
//            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
//            keyPairGenerator.initialize(2048)
//            keyPairGenerator.generateKeyPair()
//        } catch (ex: Exception) {
//            throw IllegalStateException(ex)
//        }
//    }
//}