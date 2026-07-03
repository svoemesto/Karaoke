package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.SiteUserDto
import com.svoemesto.karaokeweb.config.SiteAuthInterceptor
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*

// Весь класс проходит через SiteAuthInterceptor (см. WebMvcConfig — addPathPatterns("/api/public/account/**")),
// поэтому "siteUser" в request всегда установлен и не забанен к моменту вызова любого метода здесь.
@RestController
@RequestMapping("/api/public/account")
class PublicAccountController(
    private val passwordEncoder: PasswordEncoder,
) {

    private fun currentUser(request: HttpServletRequest): SiteUser =
        request.getAttribute(SiteAuthInterceptor.SITE_USER_ATTR) as SiteUser

    @GetMapping("/profile")
    fun profile(request: HttpServletRequest): SiteUserDto = currentUser(request).toDTO()

    @PostMapping("/profile")
    fun updateProfile(
        @RequestParam(required = false) displayName: String?,
        @RequestParam(required = false) sponsrUid: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        if (displayName.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "display_name_required"))
        }
        val user = currentUser(request)
        user.displayName = displayName
        sponsrUid?.let { user.sponsrUid = it }
        user.save()
        return ResponseEntity.ok(user.toDTO())
    }

    @PostMapping("/change-password")
    fun changePassword(
        @RequestParam oldPassword: String,
        @RequestParam newPassword: String,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> {
        val user = currentUser(request)
        if (!user.checkPassword(oldPassword, passwordEncoder)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "invalid_old_password"))
        }
        if (newPassword.length < 6) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "weak_password"))
        }
        user.setPassword(newPassword, passwordEncoder)
        user.save()
        return ResponseEntity.ok(mapOf("ok" to true))
    }
}
