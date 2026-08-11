package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.services.SongShareLinkService
import com.svoemesto.karaokeweb.services.SiteUserResolver
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Контроллер временного полного доступа к песне (add-song-share-link).
 *
 * Эндпоинты для владельца (требуется авторизация SiteUser):
 *  - POST `/api/public/share/{songId}/create` — создать/перевыпустить ссылку.
 *  - GET  `/api/public/share/mine/{songId}` — текущая активная ссылка пользователя.
 *  - POST `/api/public/share/mine/{songId}/revoke` — отозвать.
 *
 * Эндпоинты для гостя (анонимные):
 *  - POST `/api/public/share/claim` — обмен секрета на sessionTokenHash.
 *  - POST `/api/public/share/heartbeat` — продление lease.
 *  - POST `/api/public/share/release` — завершение сессии.
 *  - GET  `/share/{id}/{secret}` — публичная HTML-страница (SPA рендерит ShareView.vue).
 *
 * Ошибки share-флоу приходят с JSON-ключом `errorCode` (см. ShareErrorCode), для остальных
 * контроллеров используется `error`. Фронт ShareView различает их по `errorCode`.
 *
 * @see docs/features/guest-share-link.md
 */
@RestController
@RequestMapping("/api/public/share")
class PublicShareController(
    private val shareService: SongShareLinkService,
    private val siteUserResolver: SiteUserResolver,
    @Value("\${app.public-site-url}") private val publicSiteUrl: String,
) {
    @PostMapping("/{songId}/create")
    fun create(
        @PathVariable songId: Long,
        @RequestParam(required = false, defaultValue = "3600") ttlSeconds: Long,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val user = siteUserResolver.resolve(request) ?: return unauthorized()
        if (!user.isEffectivePremium) {
            return ResponseEntity
                .status(403)
                .body(mapOf("errorCode" to "share.notOwner"))
        }
        if (ttlSeconds != 3600L && ttlSeconds != 86_400L && ttlSeconds != 604_800L) {
            return ResponseEntity.status(400).body(mapOf("errorCode" to "share.tokenMissing"))
        }
        return try {
            val result =
                shareService.createLink(
                    siteUserId = user.id,
                    songId = songId,
                    ttlSeconds = ttlSeconds,
                    baseUrl = publicSiteUrl,
                )
            ResponseEntity.ok(
                mapOf(
                    "linkId" to result.linkId,
                    "secret" to result.secret,
                    "url" to result.url,
                    "expiresAt" to result.expiresAt,
                    "expiresAtMs" to result.expiresAtMs,
                    "expiresAtLabel" to result.expiresAtLabel,
                    "ttlSeconds" to ttlSeconds,
                ),
            )
        } catch (e: SongShareLinkService.LinkAlreadyActive) {
            ResponseEntity
                .status(429)
                .body(
                    mapOf(
                        "errorCode" to e.code.dbValue,
                        "reason" to e.reason,
                        "limit" to e.limit,
                        "actual" to e.actual,
                    ),
                )
        } catch (e: SongShareLinkService.SongUnavailable) {
            ResponseEntity.status(409).body(mapOf("errorCode" to e.code.dbValue))
        } catch (_: Exception) {
            ResponseEntity.status(500).body(mapOf("errorCode" to "share.notFound"))
        }
    }

    @GetMapping("/mine/{songId}")
    fun getMine(
        @PathVariable songId: Long,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val user = siteUserResolver.resolve(request) ?: return unauthorized()
        val link =
            shareService.getCurrentForOwner(user.id, songId)
                ?: return ResponseEntity.ok(mapOf("link" to null))
        return ResponseEntity.ok(
            mapOf(
                "link" to
                    mapOf(
                        "linkId" to link.linkId,
                        "songId" to link.songId,
                        "active" to link.active,
                        "expiresAt" to link.expiresAt,
                        "expiresAtMs" to link.expiresAtMs,
                        "expiresAtLabel" to link.expiresAtLabel,
                        "createdAt" to link.createdAt,
                        "createdAtMs" to link.createdAtMs,
                        "createdAtLabel" to link.createdAtLabel,
                        "revokedAt" to link.revokedAt,
                        "revokedAtMs" to link.revokedAtMs,
                        "revokedAtLabel" to link.revokedAtLabel,
                        "revokeReason" to link.revokeReason,
                        "firstUsedAt" to link.firstUsedAt,
                        "firstUsedAtMs" to link.firstUsedAtMs,
                        "firstUsedAtLabel" to link.firstUsedAtLabel,
                        "lastUsedAt" to link.lastUsedAt,
                        "lastUsedAtMs" to link.lastUsedAtMs,
                        "lastUsedAtLabel" to link.lastUsedAtLabel,
                        "sessionsTotal" to link.sessionsTotal,
                        "rejectedConcurrent" to link.rejectedConcurrent,
                    ),
            ),
        )
    }

    @PostMapping("/mine/{songId}/revoke")
    fun revoke(
        @PathVariable songId: Long,
        @RequestParam(required = false, defaultValue = "manual") reason: String,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val user = siteUserResolver.resolve(request) ?: return unauthorized()
        shareService.revokeLink(user.id, songId, reason)
        return ResponseEntity.ok(mapOf("revoked" to true))
    }

    @PostMapping("/claim")
    fun claim(
        @RequestBody body: Map<String, Any?>,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val secret =
            (body["secret"] as? String)?.takeIf { it.isNotBlank() }
                ?: return ResponseEntity.status(400).body(mapOf("errorCode" to "share.tokenMissing"))
        val browserHash =
            (body["browserHash"] as? String)?.takeIf { it.isNotBlank() }
                ?: return ResponseEntity.status(400).body(mapOf("errorCode" to "share.tokenMissing"))
        return try {
            val result = shareService.tryClaim(secret, browserHash, request)
            ResponseEntity.ok(
                mapOf(
                    "linkId" to result.linkId,
                    "songId" to result.songId,
                    "sessionTokenHash" to result.sessionTokenHash,
                    "redirectTo" to "/player/${result.songId}?share=1&session=${result.sessionTokenHash}",
                    // Карточка песни для ShareView лендинга — фронт рисует превью с
                    // картинками альбома/автора и подписью «Название — Автор (Альбом, Год)»
                    // ещё до открытия плеера. Не критично для безопасности: превью картинки
                    // и так публично отдаются через /api/public/picture, а текстовые поля
                    // видны всем без авторизации.
                    "songName" to result.songName,
                    "author" to result.author,
                    "album" to result.album,
                    "year" to result.year,
                    "albumImageUrl" to result.albumImageUrl,
                    "artistImageUrl" to result.artistImageUrl,
                ),
            )
        } catch (e: SongShareLinkService.ConcurrentLimit) {
            ResponseEntity.status(409).body(mapOf("errorCode" to e.code.dbValue))
        } catch (e: SongShareLinkService.RateLimited) {
            ResponseEntity.status(429).body(mapOf("errorCode" to e.code.dbValue))
        } catch (_: SongShareLinkService.NotFound) {
            ResponseEntity.status(404).body(mapOf("errorCode" to "share.notFound"))
        } catch (_: Exception) {
            ResponseEntity.status(500).body(mapOf("errorCode" to "share.notFound"))
        }
    }

    @PostMapping("/heartbeat")
    fun heartbeat(@RequestBody body: Map<String, Any?>): ResponseEntity<Map<String, Any?>> {
        val sessionTokenHash =
            (body["sessionTokenHash"] as? String)?.takeIf { it.isNotBlank() }
                ?: return ResponseEntity.status(400).body(mapOf("errorCode" to "share.tokenMissing"))
        return try {
            shareService.heartbeat(sessionTokenHash)
            ResponseEntity.ok(mapOf("ok" to true))
        } catch (_: SongShareLinkService.LeaseExpired) {
            ResponseEntity.status(410).body(mapOf("errorCode" to "share.leaseExpired"))
        } catch (_: Exception) {
            ResponseEntity.status(410).body(mapOf("errorCode" to "share.leaseExpired"))
        }
    }

    @PostMapping("/release")
    // Поддерживает и JSON (@RequestBody), и form-urlencoded (@RequestParam) — последнее нужно для
    // navigator.sendBeacon при уходе со страницы: sendBeacon не отправляет application/json, только
    // text/plain или form-data. См. spec.md FR-012 + KaraokePlayer._sendShareRelease.
    fun release(
        @RequestParam(required = false) sessionTokenHash: String?,
        @RequestParam(required = false) result: String?,
        @RequestBody(required = false) body: Map<String, Any?>?,
    ): ResponseEntity<Map<String, Any?>> {
        val finalHash =
            sessionTokenHash?.takeIf { it.isNotBlank() }
                ?: (body?.get("sessionTokenHash") as? String)?.takeIf { it.isNotBlank() }
                ?: return ResponseEntity.status(400).body(mapOf("errorCode" to "share.tokenMissing"))
        val finalResult = result ?: (body?.get("result") as? String) ?: "closed"
        shareService.release(finalHash, finalResult)
        return ResponseEntity.ok(mapOf("ok" to true))
    }

    /**
     * Диагностический эндпоинт: НЕ вызывает tryClaim, чтобы не создавать сессию, но
     * пошагово показывает, на каком этапе провалится реальный claim. Полезно, когда
     * claim возвращает 500/404, но в логи ничего не попадает (log.warn буферизуется
     * или фильтруется) — здесь всё видно в JSON-ответе.
     */
    @PostMapping("/debug")
    fun debug(@RequestBody body: Map<String, Any?>): ResponseEntity<Map<String, Any?>> {
        val secret =
            (body["secret"] as? String)?.takeIf { it.isNotBlank() }
                ?: return ResponseEntity.status(400).body(mapOf("errorCode" to "share.tokenMissing"))
        val diagnostics = shareService.debugTryClaim(secret)
        return ResponseEntity.ok(diagnostics)
    }

    private fun unauthorized(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.status(401).body(mapOf("errorCode" to "share.tokenMissing"))
}
