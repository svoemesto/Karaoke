package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.model.News
import com.svoemesto.karaokeapp.model.NewsDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// «Новости» — публичная сторона (доступно и анонимам, НЕ под /api/public/account/**, поэтому
// SiteAuthInterceptor тут не применяется, см. PublicChatController для сравнения с защищённым
// разделом). Хранилище — tbl_news, синхронизируется на PROD с LOCAL штатным движком (SyncTarget
// "news"). ВАЖНО: используем com.svoemesto.karaokeweb.WORKING_DATABASE (свой Connection, env-флаг
// WEB_WORK_ON_SERVER) — НЕ com.svoemesto.karaokeapp.WORKING_DATABASE (тот резолвится в LOCAL по
// флагам karaoke-app, которые на проде не выставлены; см. инвариант "karaoke-web Settings trap"
// в DEVELOPMENT.md — здесь модель News не тянет ничего из ConstantsKt, поэтому безопасна для karaoke-web).

/**
 * Контроллер (HTTP/WebSocket endpoints) для public news .
 *
 * @see AGENTS.md
 */
@RestController
@RequestMapping("/api/public/news")
class PublicNewsController {
    // Только опубликованные (publish_at уже наступил), свежие сверху — публичная лента /news.
    @GetMapping("")
    fun list(): List<NewsDto> = News.loadPublished(WORKING_DATABASE)

    // Лёгкий запрос для бейджа/тоста — только новости с id больше уже увиденной пользователем
    // (last_seen хранится в localStorage браузера, см. NewsBell.vue — работает и для анонимов).
    @GetMapping("/since")
    fun since(
        @RequestParam(defaultValue = "0") id: Long,
    ): Map<String, Any> {
        val items = News.loadPublishedSince(WORKING_DATABASE, id)
        return mapOf("count" to items.size, "items" to items)
    }
}
