package com.svoemesto.karaokeweb.config

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeweb.services.SiteUserTokenService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Класс Site Auth Interceptor.
 *
 * @see AGENTS.md
 */
@Component
class SiteAuthInterceptor(
    private val siteUserTokenService: SiteUserTokenService,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val token = request.getHeader("Authorization")?.removePrefix("Bearer ")?.trim()
        val user = token?.takeIf { it.isNotBlank() }?.let { siteUserTokenService.resolveToken(it, WORKING_DATABASE) }
        if (user == null) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json;charset=UTF-8"
            response.writer.write("""{"error":"unauthorized"}""")
            return false
        }
        request.setAttribute(SITE_USER_ATTR, user)
        return true
    }

    companion object {
        const val SITE_USER_ATTR = "siteUser"
    }
}
