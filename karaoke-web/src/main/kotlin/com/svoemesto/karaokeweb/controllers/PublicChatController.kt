package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.model.SiteChatMessage
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.config.SiteAuthInterceptor
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

// «Чат с автором проекта» — сторона пользователя (karaoke-public). Весь класс проходит через
// SiteAuthInterceptor (WebMvcConfig — addPathPatterns("/api/public/account/**")), поэтому "siteUser"
// в request всегда установлен и не забанен к моменту вызова любого метода здесь. Хранилище —
// tbl_site_chat_messages целиком на PROD-БД. ВАЖНО: используем com.svoemesto.karaokeweb.WORKING_DATABASE
// (свой Connection, env-флаг WEB_WORK_ON_SERVER) — НЕ com.svoemesto.karaokeapp.WORKING_DATABASE (тот
// всегда резолвится в LOCAL по флагам karaoke-app, которые на проде не выставлены). storageService/
// storageApiClient — конструкторные Spring-бины (как в PublicPlaylistController), НЕ глобалы
// KSS_APP/SAC_APP из karaoke-app.services: karaoke-web не сканирует com.svoemesto.karaokeapp.services
// (нет @ComponentScan туда, см. KaraokeWebService.kt) — эти lateinit var никогда бы не были
// инициализированы и упали бы UninitializedPropertyAccessException на первом же вызове.

/**
 * Контроллер (HTTP/WebSocket endpoints) для public chat .
 *
 * @see AGENTS.md
 */
@RestController
@RequestMapping("/api/public/account/chat")
class PublicChatController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
) {
    private fun currentUser(request: HttpServletRequest): SiteUser = request.getAttribute(SiteAuthInterceptor.SITE_USER_ATTR) as SiteUser

    private fun premiumRequired(): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "premium_required"))

    // Чат целиком — премиум-функция: и чтение истории, и отправка доступны только активному премиуму
    // (истёкший премиум теряет доступ к разделу, не только к отправке). Заодно отмечает сообщения ОТ
    // автора прочитанными — только если запрос реально прошёл гейт. Пагинация курсором по id (тред
    // append-only, id стабилен между запросами) — beforeId подгружает историю вверх, afterId — новые
    // сообщения при поллинге; без курсоров — последние DEFAULT_PAGE_SIZE (первое открытие чата).
    @GetMapping("/messages")
    fun messages(
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) beforeId: Long?,
        @RequestParam(required = false) afterId: Long?,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val user = currentUser(request)
        if (!user.isEffectivePremium) return premiumRequired()
        val list =
            SiteChatMessage
                .loadPageByUser(
                    siteUserId = user.id,
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    limit = limit ?: SiteChatMessage.DEFAULT_PAGE_SIZE,
                    beforeId = beforeId,
                    afterId = afterId,
                ).map { it.toDTO() }
        SiteChatMessage.markThreadReadByUser(user.id, WORKING_DATABASE)
        return ResponseEntity.ok(
            mapOf(
                "messages" to list,
                "total" to SiteChatMessage.countByUser(user.id, WORKING_DATABASE),
            ),
        )
    }

    // Отправка нового сообщения — только активный премиум.
    @PostMapping("/send")
    fun send(
        @RequestParam body: String,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val user = currentUser(request)
        if (!user.isEffectivePremium) return premiumRequired()
        if (body.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "empty_body"))
        }
        val created =
            SiteChatMessage.createNew(
                siteUserId = user.id,
                isFromAuthor = false,
                body = body,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        return ResponseEntity.ok(created?.toDTO())
    }

    // Непрочитанные ответы автора текущему пользователю — бейдж на публичной стороне. Тоже за
    // премиум-гейтом: не-премиум пользователь не имеет доступа к чату вообще, значит и не должен
    // получать сигнал о «новых сообщениях» в нём (для него всегда 0, без похода в БД).
    @GetMapping("/unreadcount")
    fun unreadCount(request: HttpServletRequest): Map<String, Int> {
        val user = currentUser(request)
        if (!user.isEffectivePremium) return mapOf("count" to 0)
        val count = SiteChatMessage.countUnreadForUser(user.id, WORKING_DATABASE)
        return mapOf("count" to count)
    }
}
