package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.News
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.server.ResponseStatusException
import java.sql.SQLException
import java.sql.Timestamp

// Админка «Новостей» (tbl_news) — готовятся на LOCAL из webvue3, уходят на прод штатной
// синхронизацией (SyncTarget "news", см. sync/SyncTarget.kt), как Словари (DictionariesController).
// Параметр target оставлен свободным (как в ChatController) на случай локальной отладки с
// target=remote, дефолт — LOCAL.

/**
 * Контроллер (HTTP/WebSocket endpoints) для news .
 *
 * @see AGENTS.md
 */
@Controller
@RequestMapping("/api/news")
class NewsController {
    /**
     * BR-119 (аудит #336, пакет `PKG-S3-23`): верхняя граница размера страницы новостей.
     * Раньше `pageSize` не ограничивался — `pageSize=Int.MAX_VALUE` пытался отдать всю
     * ленту (19k+ строк) одним ответом.
     */
    companion object {
        const val MAX_PAGE_SIZE = 200
    }

    /**
     * BR-119 (аудит #336, пакет `PKG-S3-23`): SQL-ошибки админского списка новостей
     * пишутся в SLF4J (не в stdout через `println`).
     */
    private val log = LoggerFactory.getLogger("infra.admin.news")

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

    // Постранично (specs/090-news-pagination) — при 19000+ строках в tbl_news (см.
    // specs/089-auto-news-song-release) полная выгрузка одним ответом делает список непригодным.
    @PostMapping("/list")
    @ResponseBody
    fun list(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "50") pageSize: Int,
    ): Map<String, Any> {
        // BR-119 (аудит #336, пакет `PKG-S3-23`): page/pageSize clamp'ятся.
        // Раньше page=-3 давал offset=-150, Postgres отвергал LIMIT/OFFSET, SQLException
        // глотался в News.loadAll — и клиент получал 200 с пустым списком при непустом total.
        // pageSize сверху тоже ограничен: pageSize=Int.MAX_VALUE отдавал всю ленту одним ответом.
        val safePage = page.coerceAtLeast(0)
        val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val offset = safePage * safePageSize
        return try {
            withDb(target) { db ->
                mapOf(
                    "news" to News.loadAll(db, limit = safePageSize, offset = offset).map { it.toDTO() },
                    "total" to News.countAll(db),
                )
            }
        } catch (e: SQLException) {
            // BR-119: сбой БД — 503, а не пустой «успешный» список.
            log.error("news.list: SQL-ошибка (sqlState={}, page={}, pageSize={})", e.sqlState, safePage, safePageSize, e)
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "news.list.unavailable: не удалось получить список новостей",
            )
        }
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

    // Старый backfill «в эфире» (specs/089-auto-news-song-release) удалён вместе с
    // tbl_song_news_announced (specs/101-song-news-flag) — механизм «в эфире» больше не нуждается в
    // backfill'е (узкое скользящее окно проверки само не захватывает старые события). Backfill для
    // нового флага «доступна» — см. ApiController.doBackfillNewsAvailable
    // (POST /utils/backfillnewsavailable), тяжёлая операция выполняется в фоновом потоке с
    // SSE-тостом, не синхронно внутри HTTP-запроса.
}
