package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeweb.WORKING_DATABASE
import com.svoemesto.karaokeweb.config.SiteAuthInterceptor
import com.svoemesto.karaokeweb.services.SongShareLinkService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Admin-endpoint'ы для управления share-ссылками site-user'ов (используется из webvue3 → modal
 * `UserShareLinksModal`). Все три пути требуют залогиненного site-user'а с
 * `tbl_site_users.is_editor = true` (см. [WebMvcConfig] для path-pattern).
 *
 * Поддерживает `target=local|remote`:
 *   - `local` → [WORKING_DATABASE] (локальная БД разработчика)
 *   - `remote` → [Connection.remote] (прод-БД через siteusers-роуты)
 *
 * @see archive/docs/features/guest-share-link.md
 */
@RestController
@RequestMapping("/api/siteusers/share")
class SiteShareLinksController(
    private val shareService: SongShareLinkService,
) {
    /**
     * Список share-ссылок пользователя (активные и завершённые).
     * Body: { siteUserId, activeOnly?, limit?, target? }
     */
    @PostMapping("/links")
    fun listLinks(
        @RequestBody body: Map<String, Any?>,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val caller = resolveEditorOrThrow(request)
        val siteUserId =
            (body["siteUserId"] as? Number)?.toLong()
                ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("errorCode" to "share.tokenMissing"))
        val activeOnly = (body["activeOnly"] as? Boolean) ?: false
        val limit = (body["limit"] as? Number)?.toInt() ?: 50
        val database =
            resolveDatabase(body["target"] as? String)
                ?: return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf("errorCode" to "site.remote_unavailable"))

        val links = shareService.listLinksForUser(siteUserId, activeOnly, limit, database)
        return ResponseEntity.ok(mapOf("links" to links, "callerId" to caller.id))
    }

    /**
     * Admin-отзыв конкретной share-ссылки по её id.
     * Body: { shareLinkId, reason?, target? }
     */
    @PostMapping("/links/revoke")
    fun revokeLink(
        @RequestBody body: Map<String, Any?>,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val caller = resolveEditorOrThrow(request)
        val shareLinkId =
            (body["shareLinkId"] as? Number)?.toLong()
                ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("errorCode" to "share.tokenMissing"))
        val reason = (body["reason"] as? String) ?: "admin"
        val database =
            resolveDatabase(body["target"] as? String)
                ?: return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf("errorCode" to "site.remote_unavailable"))

        shareService.revokeLinkById(shareLinkId, reason, database)
        return ResponseEntity.ok(mapOf("revoked" to true, "callerId" to caller.id))
    }

    /**
     * Список playback-сессий по конкретной ссылке (для аудита/расследований).
     * Body: { shareLinkId, target? }
     */
    @PostMapping("/sessions")
    fun listSessions(
        @RequestBody body: Map<String, Any?>,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> {
        resolveEditorOrThrow(request)
        val shareLinkId =
            (body["shareLinkId"] as? Number)?.toLong()
                ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("errorCode" to "share.tokenMissing"))
        val database =
            resolveDatabase(body["target"] as? String)
                ?: return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf("errorCode" to "site.remote_unavailable"))

        val sessions = shareService.listSessionsForLink(shareLinkId, database)
        return ResponseEntity.ok(mapOf("sessions" to sessions))
    }

    /**
     * Достаёт site-user из атрибутов request (выставленных [SiteAuthInterceptor]) и проверяет
     * `isEditor == true`. Если нет — 403 share.notEditor.
     */
    private fun resolveEditorOrThrow(request: HttpServletRequest): SiteUser {
        val user =
            request.getAttribute(SiteAuthInterceptor.SITE_USER_ATTR) as? SiteUser
                ?: return throw IllegalStateException(
                    "SiteAuthInterceptor не выставил SITE_USER_ATTR — должен быть в path-patterns",
                )
        if (!user.isEditor) {
            throw ShareAccessDeniedException("Caller ${user.id} is not editor")
        }
        return user
    }

    /**
     * Резолвит KaraokeConnection по target. `local`/`null` → [WORKING_DATABASE], `remote` →
     * [Connection.remote]. Возвращает null, если remote-БД недоступна.
     */
    private fun resolveDatabase(target: String?): KaraokeConnection? =
        when (target) {
            null, "local" -> WORKING_DATABASE
            "remote" -> {
                try {
                    Connection.remote()
                } catch (e: Exception) {
                    null
                }
            }
            else -> WORKING_DATABASE
        }
}

/**
 * BR-007 (аудит #336, OpenProject #401): отказ «вы не редактор» должен быть **403**,
 * а не 500.
 *
 * Раньше исключение было `private class` ВНУТРИ контроллера и НЕ имело
 * `@ResponseStatus`: Spring не находил ни обработчика, ни аннотации → RuntimeException
 * уезжал в стандартный 500. При этом KDoc самого класса обещал «403 share.notEditor
 * (Spring-уровневый маппинг в JSON)» — маппинга не было нигде.
 *
 * Теперь:
 *  - `@ResponseStatus(FORBIDDEN)` — корректный статус даже без advice;
 *  - класс верхнего уровня (виден обработчику исключений);
 *  - [SiteShareLinksExceptionHandler] отдаёт JSON `{"errorCode":"share.notEditor"}`,
 *    как остальные endpoint'ы этого контроллера.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
class ShareAccessDeniedException(
    message: String,
) : RuntimeException(message)

/**
 * BR-007: маппинг исключений [SiteShareLinksController] в JSON с `errorCode`.
 *
 * Advice ОГРАНИЧЕН этим контроллером (`assignableTypes`), чтобы не перехватывать
 * исключения других endpoint'ов.
 *
 *  - [ShareAccessDeniedException] → 403 `share.notEditor` (не редактор);
 *  - `IllegalStateException` → 500 `share.internal` (например, `SiteAuthInterceptor`
 *    не выставил атрибут — это наша ошибка конфигурации, а не «пользователь виноват»).
 */
@RestControllerAdvice(assignableTypes = [SiteShareLinksController::class])
class SiteShareLinksExceptionHandler {
    @ExceptionHandler(ShareAccessDeniedException::class)
    fun onAccessDenied(e: ShareAccessDeniedException): ResponseEntity<Map<String, Any?>> =
        ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(mapOf("errorCode" to "share.notEditor", "message" to (e.message ?: "not editor")))

    @ExceptionHandler(IllegalStateException::class)
    fun onIllegalState(e: IllegalStateException): ResponseEntity<Map<String, Any?>> {
        LoggerFactory
            .getLogger(SiteShareLinksExceptionHandler::class.java)
            .warn("share-links:internal error", e)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("errorCode" to "share.internal"))
    }
}
