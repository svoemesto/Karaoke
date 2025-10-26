// file: src/main/kotlin/com/svoemesto/karaokeapp/config/AuthorizationServerConfig.kt

package com.svoemesto.karaokeapp.config

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
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
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*

@Configuration
class AuthorizationServerConfig {

    private val logger = LoggerFactory.getLogger(AuthorizationServerConfig::class.java)

    @Bean
    @Order(0) // Высокая важность (первая цепочка)
    fun authorizationServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/oauth2/**", "/.well-known/**", "/login", "/logout")
            .authorizeHttpRequests { authz ->
                authz.anyRequest().authenticated()
            }
            .formLogin { formLogin ->
                formLogin.loginPage("/login").permitAll()
            }
//            .logout { logout ->
//                logout.logoutSuccessHandler(CustomLogoutSuccessHandler()) // Если нужно
//            }
            .csrf { csrfConfigurer ->
                csrfConfigurer.disable()
            }
        return http.build()
    }

    @Bean
    @Order(1) // Низкий приоритет (для всех прочих запросов)
    fun webSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { authz ->
                authz
                    .requestMatchers("/api/public/**").permitAll()
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
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
    fun registeredClientRepository(): RegisteredClientRepository {
        logger.info(">>> Creating RegisteredClientRepository...")
        val vueClient = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("vue-client")
            .clientSecret("{noop}vue_secret_123")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("http://localhost:7906/callback")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope(OidcScopes.EMAIL)
            .scope("api:read")
            .scope("api:write")
            .clientSettings(
                ClientSettings.builder()
                    .requireAuthorizationConsent(true)
                    .requireProofKey(true)
                    .build()
            )
            .build()

        logger.info("<<< RegisteredClientRepository created with vue-client.")
        return InMemoryRegisteredClientRepository(vueClient)
    }

    @Bean
    fun authorizationServerSettings(): AuthorizationServerSettings {
        logger.info(">>> Creating AuthorizationServerSettings...")
        val settings = AuthorizationServerSettings.builder().build()
        logger.info("<<< AuthorizationServerSettings created.")
        return settings
    }

    @Bean
    fun jwtDecoder(jwkSource: JWKSource<SecurityContext>): JwtDecoder {
        logger.info(">>> Creating JwtDecoder...")
        val decoder = OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource)
        logger.info("<<< JwtDecoder created.")
        return decoder
    }

    @Bean
    fun jwkSource(): JWKSource<SecurityContext> {
        logger.info(">>> Creating JWKSource...")
        val keyPair = generateRsaKey()
        val publicKey = keyPair.public as RSAPublicKey
        val privateKey = keyPair.private as RSAPrivateKey
        val rsaKey = RSAKey.Builder(publicKey)
            .privateKey(privateKey)
            .keyID(UUID.randomUUID().toString())
            .build()
        val jwkSet = JWKSet(rsaKey)
        val source: JWKSource<SecurityContext> = ImmutableJWKSet(jwkSet) // Явное указание типа
        logger.info("<<< JWKSource created.")
        return source
    }

    private fun generateRsaKey(): KeyPair {
        logger.debug(">>> Generating RSA Key Pair...")
        return try {
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(2048)
            val keyPair = keyPairGenerator.generateKeyPair()
            logger.debug("<<< RSA Key Pair generated.")
            keyPair
        } catch (ex: Exception) {
            logger.error("!!! Error generating RSA Key Pair", ex)
            throw IllegalStateException(ex)
        }
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