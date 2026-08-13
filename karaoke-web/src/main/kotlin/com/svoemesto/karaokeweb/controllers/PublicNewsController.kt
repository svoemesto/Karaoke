package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.WORKING_DATABASE
import com.svoemesto.karaokeweb.services.SiteUserResolver

import com.svoemesto.karaokeapp.model.News
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// «Новости» — публичная сторона для постраничной ленты /news (доступно и анонимам, НЕ под
// /api/public/account/**, поэтому SiteAuthInterceptor тут не применяется, см. PublicChatController
// для сравнения с защищённым разделом). Эндпоинт /since (бейдж/тост колокольчика) — только для
// залогиненных, см. since() — иначе каждая анонимная вкладка опрашивает `/since?id=0` каждые 45 сек
// (NewsBell.vue) и при ~19k+ строках в tbl_news тянет 3.5+ MB JSON, что в пиках исчерпывает
// HikariCP pool (10 коннектов) и валит сайт на 7-10 мин (Pass 52, 2026-08-13).
// Хранилище — tbl_news, синхронизируется на PROD с LOCAL штатным движком (SyncTarget "news").
// ВАЖНО: используем com.svoemesto.karaokeweb.WORKING_DATABASE (свой Connection, env-флаг
// WEB_WORK_ON_SERVER) — НЕ com.svoemesto.karaokeapp.WORKING_DATABASE (тот резолвится в LOCAL по
// флагам karaoke-app, которые на проде не выставлены; см. инвариант "karaoke-web Song trap"
// в DEVELOPMENT.md — здесь модель News не тянет ничего из ConstantsKt, поэтому безопасна для karaoke-web).

/**
 * Контроллер (HTTP/WebSocket endpoints) для public news .
 *
 * @see AGENTS.md
 */
@RestController
@RequestMapping("/api/public/news")
class PublicNewsController(
    private val siteUserResolver: SiteUserResolver,
) {
    // Только опубликованные (publish_at уже наступил), свежие сверху — публичная лента /news.
    // Постранично (specs/090-news-pagination) — при 19000+ строках в tbl_news (см.
    // specs/089-auto-news-song-release) полная выгрузка одним ответом деградирует ленту.
    @GetMapping("")
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Map<String, Any> {
        val offset = page * size
        val items = News.loadPublished(WORKING_DATABASE, limit = size, offset = offset)
        val total = News.countPublished(WORKING_DATABASE)
        return mapOf(
            "items" to items,
            "total" to total,
            "hasMore" to (offset + items.size < total),
        )
    }

    // Бейдж/тост «новое сообщение» — `lastSeenId` из localStorage анонима, для залогиненного —
    // последний просмотренный id новости (см. NewsBell.vue). Возвращаем ТОЛЬКО для залогиненных:
    // запрос `/since?id=0` = «все опубликованные», 3.5+ MB при ~19k строках в tbl_news; массовый
    // анонимный polling (45 сек × N вкладок × N пользователей) → exhaustion HikariCP pool (10
    // коннектов) → 7-10 мин каскадных зависаний сайта (Pass 52, 2026-08-13). Анонимам значок
    // новостей не нужен — публичная лента /news остаётся доступной через list() выше.
    @GetMapping("/since")
    fun since(
        @RequestParam(defaultValue = "0") id: Long,
        request: HttpServletRequest,
    ): Map<String, Any> {
        if (siteUserResolver.resolve(request) == null) {
            // Аноним — пустой ответ (бейдж не показывается, см. NewsBell.vue: unread=0, visible=false).
            return mapOf("count" to 0, "items" to emptyList<Any>())
        }
        // Залогиненный: ограничиваем верх LIMIT-ом как страховку (если кто-то залогинился
        // впервые за 2 года, не отдадим весь архив разом — UI рассчитан на свежие уведомления,
        // остальное подтянется через /news при необходимости).
        val items = News.loadPublishedSince(WORKING_DATABASE, id, limit = 50)
        return mapOf("count" to items.size, "items" to items)
    }
}
