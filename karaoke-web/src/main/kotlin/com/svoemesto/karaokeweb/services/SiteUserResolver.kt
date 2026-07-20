package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeweb.WORKING_DATABASE
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

// Опциональный (не кидающий 401) резолв SiteUser по Authorization-заголовку — общий для всех
// мест, где нужно "если пользователь залогинен, вот кто это" без блокировки анонимных запросов
// (в отличие от SiteAuthInterceptor, который требует токен и покрывает только
// /api/public/account/**, /api/public/auth/**). Не кэшируется — бан/снятие премиума должны
// действовать немедленно, а не по TTL какого-то локального кэша.
@Component
class SiteUserResolver(
    private val siteUserTokenService: SiteUserTokenService,
) {
    fun resolve(request: HttpServletRequest): SiteUser? {
        val header = request.getHeader("Authorization") ?: return null
        val token = header.removePrefix("Bearer ").trim().takeIf { it.isNotBlank() } ?: return null
        return siteUserTokenService.resolveToken(token, WORKING_DATABASE)
    }
}
