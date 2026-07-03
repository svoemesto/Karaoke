package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.SiteUserDto
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.WORKING_DATABASE
import com.svoemesto.karaokeweb.config.SiteAuthInterceptor
import com.svoemesto.karaokeweb.services.CaptchaConfigService
import com.svoemesto.karaokeweb.services.SiteUserTokenService
import com.svoemesto.karaokeweb.services.YandexCaptchaValidationService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.sql.Timestamp

@RestController
@RequestMapping("/api/public/auth")
class PublicAuthController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
    private val passwordEncoder: PasswordEncoder,
    private val siteUserTokenService: SiteUserTokenService,
    private val captchaConfigService: CaptchaConfigService,
    private val yandexCaptchaValidationService: YandexCaptchaValidationService,
) {

    @GetMapping("/config")
    fun config(): Map<String, String> = mapOf("captchaClientKey" to captchaConfigService.getClientKey())

    @PostMapping("/register")
    fun register(
        @RequestParam email: String,
        @RequestParam password: String,
        @RequestParam passwordConfirm: String,
        @RequestParam(required = false) displayName: String?,
        @RequestParam(required = false) captchaToken: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> {
        if (!yandexCaptchaValidationService.validate(captchaToken ?: "", request.remoteAddr)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "captcha"))
        }
        if (!email.contains("@") || email.length < 5) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "invalid_email"))
        }
        if (displayName.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "display_name_required"))
        }
        if (password.length < 6) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "weak_password"))
        }
        if (password != passwordConfirm) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "password_mismatch"))
        }

        val user = SiteUser.createNewSiteUser(
            email = email,
            rawPassword = password,
            displayName = displayName,
            database = WORKING_DATABASE,
            passwordEncoder = passwordEncoder,
            storageService = storageService,
            storageApiClient = storageApiClient,
        ) ?: return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "email_taken"))

        val token = siteUserTokenService.issueToken(user.id, WORKING_DATABASE)
            ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to "token"))

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(mapOf("token" to token, "user" to user.toDTO()))
    }

    @PostMapping("/login")
    fun login(@RequestParam email: String, @RequestParam password: String): ResponseEntity<Map<String, Any>> {
        val user = SiteUser.getSiteUserByEmail(email, WORKING_DATABASE, storageService, storageApiClient)
        if (user == null || !user.checkPassword(password, passwordEncoder)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "invalid_credentials"))
        }
        if (user.isBanned) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "banned", "reason" to user.banReason))
        }

        user.lastLoginAt = Timestamp(System.currentTimeMillis())
        user.save()

        val token = siteUserTokenService.issueToken(user.id, WORKING_DATABASE)
            ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to "token"))

        return ResponseEntity.ok(mapOf("token" to token, "user" to user.toDTO()))
    }

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest): Map<String, Boolean> {
        val token = request.getHeader("Authorization")?.removePrefix("Bearer ")?.trim()
        token?.takeIf { it.isNotBlank() }?.let { siteUserTokenService.revokeToken(it, WORKING_DATABASE) }
        return mapOf("ok" to true)
    }

    @GetMapping("/me")
    fun me(request: HttpServletRequest): SiteUserDto {
        val user = request.getAttribute(SiteAuthInterceptor.SITE_USER_ATTR) as SiteUser
        return user.toDTO()
    }
}
