package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.SiteChatMessage
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

// «Чат с автором проекта» — сторона автора (webvue3). Таблица tbl_site_chat_messages живёт целиком
// на PROD-БД (пользователи пишут через karaoke-web на проде) — фронт всегда шлёт target=remote, но
// параметр оставлен свободным (как в TariffsController/SiteUsersController) на случай локальной
// отладки с target=local.

/**
 * Контроллер (HTTP/WebSocket endpoints) для chat .
 *
 * @see AGENTS.md
 */
@Controller
@RequestMapping("/api/chat")
class ChatController {
    private fun resolveDb(target: String?): KaraokeConnection = if (target == "remote") Connection.remote() else Connection.local()

    // resolveDb() открывает новое физическое соединение на каждый вызов — без явного close() пул
    // Postgres постепенно исчерпывается (см. комментарий к withDb в SiteUsersController/TariffsController).
    private fun <T> withDb(
        target: String?,
        block: (KaraokeConnection) -> T,
    ): T {
        val db = resolveDb(target)
        return try {
            block(db)
        } finally {
            try {
                db.getConnection()?.close()
            } catch (_: Exception) {
            }
        }
    }

    @PostMapping("/threads")
    @ResponseBody
    fun threads(
        @RequestParam(required = false) target: String?,
    ): Map<String, Any> =
        withDb(target) { db ->
            mapOf("threads" to SiteChatMessage.loadThreads(db))
        }

    // Открытие треда автором — заодно отмечает сообщения ОТ пользователя прочитанными. Пагинация
    // курсором по id (не offset — тред append-only, id стабилен даже если между запросами пришли
    // новые сообщения): beforeId — подгрузка истории вверх, afterId — поллинг новых сообщений.
    // Без limit/курсоров — последние DEFAULT_PAGE_SIZE сообщений (первое открытие треда).
    @PostMapping("/messages")
    @ResponseBody
    fun messages(
        @RequestParam siteUserId: Long,
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) beforeId: Long?,
        @RequestParam(required = false) afterId: Long?,
    ): Map<String, Any> =
        withDb(target) { db ->
            val list =
                SiteChatMessage
                    .loadPageByUser(
                        siteUserId = siteUserId,
                        database = db,
                        storageService = KSS_APP,
                        storageApiClient = SAC_APP,
                        limit = limit ?: SiteChatMessage.DEFAULT_PAGE_SIZE,
                        beforeId = beforeId,
                        afterId = afterId,
                    ).map { it.toDTO() }
            SiteChatMessage.markThreadReadByAuthor(siteUserId, db)
            mapOf(
                "messages" to list,
                "total" to SiteChatMessage.countByUser(siteUserId, db),
            )
        }

    @PostMapping("/reply")
    @ResponseBody
    fun reply(
        @RequestParam siteUserId: Long,
        @RequestParam body: String,
        @RequestParam(required = false) target: String?,
    ): Long =
        withDb(target) { db ->
            if (body.isBlank()) return@withDb 0L
            SiteChatMessage
                .createNew(
                    siteUserId,
                    isFromAuthor = true,
                    body = body,
                    database = db,
                    storageService = KSS_APP,
                    storageApiClient = SAC_APP,
                )?.id
                ?: 0L
        }

    // Поиск пользователя сайта по email/имени — «Начать чат» в webvue3 (можно открыть диалог с
    // пользователем, который ещё не писал сам, а не только отвечать на уже начатые треды).
    @PostMapping("/searchusers")
    @ResponseBody
    fun searchUsers(
        @RequestParam term: String,
        @RequestParam(required = false) target: String?,
    ): Map<String, Any> =
        withDb(target) { db ->
            mapOf(
                "users" to
                    SiteUser
                        .searchByTerm(
                            term,
                            limit = 20,
                            database = db,
                            storageService = KSS_APP,
                            storageApiClient = SAC_APP,
                        ).map { it.toDTO() },
            )
        }

    // Непрочитанные сообщения ОТ пользователей — бейдж пункта меню «Чат» в webvue3.
    @PostMapping("/unreadcount")
    @ResponseBody
    fun unreadCount(
        @RequestParam(required = false) target: String?,
    ): Int =
        withDb(target) { db ->
            SiteChatMessage.countUnreadFromUsers(db)
        }
}
