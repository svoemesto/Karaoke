package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.News
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.sql.Timestamp

// Админка «Новостей» (tbl_news) — готовятся на LOCAL из webvue3, уходят на прод штатной
// синхронизацией (SyncTarget "news", см. sync/SyncTarget.kt), как Словари (DictionariesController).
// Параметр target оставлен свободным (как в ChatController) на случай локальной отладки с
// target=remote, дефолт — LOCAL.
@Controller
@RequestMapping("/api/news")
class NewsController {
    private fun resolveDb(target: String?): KaraokeConnection = if (target == "remote") Connection.remote() else Connection.local()

    // resolveDb() открывает новое физическое соединение на каждый вызов — без явного close() пул
    // Postgres постепенно исчерпывается (см. ChatController/DictionariesController).
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

    // "2026-07-09T15:00" (datetime-local) -> Timestamp; пусто/некорректно -> null (черновик без даты).
    private fun parsePublishAt(publishAt: String?): Timestamp? {
        if (publishAt.isNullOrBlank()) return null
        return try {
            Timestamp.valueOf(publishAt.replace("T", " ").let { if (it.length == 16) "$it:00" else it })
        } catch (_: Exception) {
            null
        }
    }

    @PostMapping("/list")
    @ResponseBody
    fun list(
        @RequestParam(required = false) target: String?,
    ): Map<String, Any> =
        withDb(target) { db ->
            mapOf("news" to News.loadAll(db).map { it.toDTO() })
        }

    @PostMapping("/create")
    @ResponseBody
    fun create(
        @RequestParam title: String,
        @RequestParam body: String,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) link: String?,
        @RequestParam(required = false) publishAt: String?,
        @RequestParam(required = false) target: String?,
    ): Long =
        withDb(target) { db ->
            if (title.isBlank()) return@withDb 0L
            News
                .createNew(
                    title = title,
                    body = body,
                    category = category?.takeIf { it.isNotBlank() } ?: "general",
                    link = link?.takeIf { it.isNotBlank() },
                    publishAt = parsePublishAt(publishAt),
                    database = db,
                )?.id ?: 0L
        }

    @PostMapping("/update")
    @ResponseBody
    fun update(
        @RequestParam id: Long,
        @RequestParam(required = false) title: String?,
        @RequestParam(required = false) body: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) link: String?,
        @RequestParam(required = false) publishAt: String?,
        @RequestParam(required = false) clearPublishAt: Boolean?,
        @RequestParam(required = false) target: String?,
    ): Long =
        withDb(target) { db ->
            val item = News.getById(id, db) ?: return@withDb 0L
            title?.let { item.title = it }
            body?.let { item.body = it }
            category?.let { item.category = it }
            link?.let { item.link = it.takeIf { v -> v.isNotBlank() } }
            if (clearPublishAt == true) {
                item.publishAt = null
            } else if (publishAt != null) {
                item.publishAt = parsePublishAt(publishAt)
            }
            item.save()
            item.id
        }

    @PostMapping("/delete")
    @ResponseBody
    fun delete(
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
    ): Boolean = withDb(target) { db -> News.delete(id, db) }
}
