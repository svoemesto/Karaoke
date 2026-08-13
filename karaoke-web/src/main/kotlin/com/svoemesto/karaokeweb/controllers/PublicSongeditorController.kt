package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SongAssignmentStatus
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.WORKING_DATABASE
import com.svoemesto.karaokeweb.services.SiteUserResolver
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

private val selfAssignLog = LoggerFactory.getLogger("PublicSongeditorController")

/**
 * Публичная часть (БЕЗ SiteAuthInterceptor): self-assign (взять свободную песню себе).
 *
 * Отделён от `/api/public/account/editor/` (path-pattern) — те публичные маршруты защищены
 * `SiteAuthInterceptor` и предполагают cookie-based авторизацию пользователя, у которого
 * уже есть редакторская роль. Self-assign живёт на публичной странице конкретной песни
 * `/song/{id}`, открытой анонимам — фронт сам решает, показать ли кнопку (только
 * залогиненным редакторам с флагом canSelfAssignTasks=true), а эндпоинт перепроверяет права
 * через `siteUserResolver`.
 *
 * @see docs/features/editor-tasks.md#self-assign
 */
@RestController
@RequestMapping("/api/public/songeditor")
class PublicSongeditorController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
    private val siteUserResolver: SiteUserResolver,
) {
    private val db get() = WORKING_DATABASE

    /**
     * Self-assign (FR-005/FR-006, spec 182): редактор с флагом `canSelfAssignTasks` берёт себе
     * свободную песню. Атомарно через `SELECT FOR UPDATE` в транзакции (race protection для
     * одновременных кликов разных редакторов → один 200, второй 409). Идемпотентно по
     * `(song_id, assignee_id)` (повторный клик того же редактора → 200 + `idempotent:true`,
     * новая строка НЕ создаётся).
     */
    @PostMapping("/assign-self")
    @ResponseBody
    fun assignSelf(
        @RequestParam songId: Long,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> {
        // На странице песни кнопка рисуется только для self-assign-редакторов — фронт уже
        // проверил через /api/public/auth/me. Бэкенд перепроверяет НЕ доверяя фронту.
        val user =
            siteUserResolver.resolve(request)
                ?: return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(mapOf("ok" to false, "error" to "unauthorized"))

        if (!user.isEditor) {
            return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(mapOf("ok" to false, "error" to "forbidden_not_editor"))
        }
        if (!user.canSelfAssignTasks) {
            return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(mapOf("ok" to false, "error" to "forbidden_not_self_assign_editor"))
        }

        if (Song.loadFromDbById(songId, db, storageService = storageService, storageApiClient = storageApiClient) == null) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(mapOf("ok" to false, "error" to "song_not_found"))
        }

        // Атомарная секция (FR-006 / US3). Используем прямое JDBC-соединение для SELECT FOR UPDATE,
        // потому что reflection-loader (SongAssignment / KaraokeDbTable) не имеет публичного способа
        // залочить строку. try-finally восстанавливает autoCommit — иначе thread-local кеш в
        // KaraokeConnection остался бы в транзакции.
        val conn =
            db.getConnection()
                ?: return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(mapOf("ok" to false, "error" to "db_unavailable"))

        val previousAutoCommit = conn.autoCommit
        return try {
            conn.autoCommit = false
            // Лочка + поиск ЛЮБОГО существующего задания на эту песню. У tbl_song_assignments
            // есть UNIQUE(song_id, assignee_id), но физически для одной песни может быть
            // НЕСКОЛЬКО строк (разные assignee после approve/refuse). Нам важен ЛЮБОЙ row: своё —
            // идемпотентно 200, чужое — 409.
            var conflictAssignee: Long? = null
            var ownAssignmentId: Long? = null
            conn
                .prepareStatement(
                    "SELECT id, assignee_id FROM tbl_song_assignments WHERE song_id = ? FOR UPDATE"
                ).use { ps ->
                    ps.setLong(1, songId)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            val a = rs.getLong("assignee_id")
                            if (a == user.id) {
                                ownAssignmentId = rs.getLong("id")
                            } else if (conflictAssignee == null) {
                                conflictAssignee = a
                            }
                        }
                    }
                }

            when {
                ownAssignmentId != null -> {
                    val id = ownAssignmentId!!
                    conn.commit()
                    selfAssignLog.info("[self-assign] user={} song={} result=idempotent id={}", user.id, songId, id)
                    ResponseEntity.ok(mapOf("ok" to true, "id" to id, "idempotent" to true))
                }
                conflictAssignee != null -> {
                    conn.rollback()
                    selfAssignLog.info("[self-assign] user={} song={} result=conflict existingAssignee={}", user.id, songId, conflictAssignee)
                    ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(mapOf("ok" to false, "error" to "song_already_taken"))
                }
                else -> {
                    // UNIQUE(song_id, assignee_id) подстрахует от concurrent INSERT разных редакторов.
                    val newId: Long =
                        conn
                            .prepareStatement(
                                "INSERT INTO tbl_song_assignments " +
                                    "(assignee_id, song_id, voice, admin_status, review_comment, assigned_by, recordhash) " +
                                    "VALUES (?, ?, 0, ?, '', ?, '') RETURNING id"
                            ).use { ps ->
                                ps.setLong(1, user.id)
                                ps.setLong(2, songId)
                                ps.setString(3, SongAssignmentStatus.ADMIN_OPEN)
                                ps.setLong(4, user.id)
                                ps.executeQuery().use { rs ->
                                    rs.next()
                                    rs.getLong("id")
                                }
                            }
                    conn.commit()
                    selfAssignLog.info("[self-assign] user={} song={} result=created id={}", user.id, songId, newId)
                    ResponseEntity.ok(mapOf("ok" to true, "id" to newId, "idempotent" to false))
                }
            }
        } catch (e: Exception) {
            try {
                conn.rollback()
            } catch (_: Exception) {
            }
            selfAssignLog.warn("[self-assign] user={} song={} error={}", user.id, songId, e.message)
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("ok" to false, "error" to "internal", "message" to (e.message ?: "")))
        } finally {
            try {
                conn.autoCommit = previousAutoCommit
            } catch (_: Exception) {
            }
        }
    }
}
