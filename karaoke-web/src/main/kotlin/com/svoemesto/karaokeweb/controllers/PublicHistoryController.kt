package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.model.ListeningHistory
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeweb.config.SiteAuthInterceptor
import com.svoemesto.karaokeweb.dto.HistoryEntryDto
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// «История прослушиваний» (QW-13) — отдельный контроллер, не расширение
// PublicPlaylistController.kt (другая предметная область, общий только auth-префикс, см.
// specs/009-listening-history/research.md Decision 3). Тот же SiteAuthInterceptor-гейт, что у
// плейлистов — весь путь /api/public/account/** требует валидный токен.

/**
 * Контроллер (HTTP endpoints) для истории прослушиваний.
 *
 * @see specs/009-listening-history/contracts/history-api.md
 */
@RestController
@RequestMapping("/api/public/account")
class PublicHistoryController {
    private fun currentUser(request: HttpServletRequest): SiteUser = request.getAttribute(SiteAuthInterceptor.SITE_USER_ATTR) as SiteUser

    @GetMapping("/history")
    fun history(request: HttpServletRequest): Map<String, Any> {
        val user = currentUser(request)
        val items = ListeningHistory.getForUser(siteUserId = user.id, database = WORKING_DATABASE).map { HistoryEntryDto.fromEntry(it) }
        return mapOf("items" to items)
    }
}
