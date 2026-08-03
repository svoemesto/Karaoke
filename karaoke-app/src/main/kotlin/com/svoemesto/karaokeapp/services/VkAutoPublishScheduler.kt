package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.News
import com.svoemesto.karaokeapp.model.PublicationType
import com.svoemesto.karaokeapp.model.Song
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap

/**
 * Плановый бот автопубликации новостей категории `air` в группу ВКонтакте
 * (specs/121-vk-news-auto-publish, FR-002a).
 *
 * Триггер — отдельный `@Scheduled`-тик каждые 60 секунд (по образцу
 * [TelegramAutoPublishScheduler], не встраивается в sync `tbl_news` — см. FR-002a).
 * Тик сканирует `tbl_news` на `air`-новости с `publish_at <= now()`, для каждой
 * определяет связанную песню (`News.song_id` или разбор `News.link` `/song?id=<id>`),
 * и если у песни `Song.idVk` пуст — публикует через [VkAutoPublishService].
 *
 * Также обрабатывает песни в состоянии `RENDERING` (FR-020 сц. 2/3): при завершении
 * задачи `RENDER_MP4_DEMO` вызывает [VkAutoPublishService.onRenderCompleted].
 *
 * Редкий случай (FR-004a, FR-021) — ручная `air`-новость без `song_id`: публикуется
 * без видео, только текст; идемпотентность через in-memory Set (News не имеет
 * JSON-блоба для хранения состояния).
 *
 * FR-006 rate limit: бот считает свои посты за последний час и переносит остаток
 * на следующий тик (`vkAutoPublishRateLimitPerHour`).
 *
 * @see docs/features/vk-news-auto-publish.md
 */
@Component
class VkAutoPublishScheduler {
    // In-memory идемпотентность для редкого случая (FR-004a): news.id уже опубликованных
    // без-song_id новостей. Не переживает рестарт — но дубли после рестарта маловероятны
    // (publish_at <= now() остается, но Set пуст → попытка повторной публикации; для
    // без-song_id случая это приемлемый компромисс без миграции News).
    private val publishedNewsIdsWithoutSong = ConcurrentHashMap.newKeySet<Long>()

    // In-memory rate-limit счётчик: timestamp'ы успешных постов за последний час (FR-006).
    private val postTimestamps = mutableListOf<Long>()

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    fun tick() {
        if (!KaraokeProperties.getBoolean("vkAutoPublishEnabled")) return

        val groupId = KaraokeProperties.getString("vkGroupId")
        if (groupId.isBlank()) {
            return
        }

        try {
            resumeRenderingSongs()
            publishScheduledNews()
        } catch (e: Exception) {
        }
    }

    // Фаза 1 тика: песни в состоянии RENDERING, чья render-задача завершена → продолжить.
    private fun resumeRenderingSongs() {
        val renderingIds = loadRenderingCandidateIds()
        for (songId in renderingIds) {
            val song =
                Song.loadFromDbById(
                    id = songId,
                    database = WORKING_DATABASE,
                    storageService = KSS_APP,
                    storageApiClient = SAC_APP,
                ) ?: continue
            if (song.idVk.isNotEmpty()) continue
            if (song.vkAutoPublishState != VkAutoPublishState.RENDERING.code) continue

            val renderProcess = findRenderDemoProcess(songId)
            if (renderProcess == null) continue
            val isDone = renderProcess.status == "DONE"
            val isError = renderProcess.status == "ERROR"
            if (!isDone && !isError) continue

            val error = if (isError) "RENDER_MP4_DEMO failed (status=ERROR)" else null
            VkAutoPublishService.onRenderCompleted(songId, PublicationType.AIR, success = isDone, error = error)
        }
    }

    // Фаза 2 тика: опубликованные air-новости с пустым idVk у связанной песни → publishToVk.
    private fun publishScheduledNews() {
        val candidates = loadNewsCandidates()
        for (news in candidates) {
            val songId = resolveSongId(news)
            if (songId != null) {
                val song =
                    Song.loadFromDbById(
                        id = songId,
                        database = WORKING_DATABASE,
                        storageService = KSS_APP,
                        storageApiClient = SAC_APP,
                    ) ?: continue
                if (song.idVk.isNotEmpty()) continue // FR-008
                if (!acquireRateLimitSlot()) continue // FR-006
                VkAutoPublishService.publishToVk(song, PublicationType.AIR)
            } else {
                // FR-021 редкий: ручная air-новость без song_id → пост без видео.
                if (publishedNewsIdsWithoutSong.contains(news.id)) continue // FR-004a
                if (!acquireRateLimitSlot()) continue
                publishNewsWithoutVideo(news)
            }
        }
    }

    /** FR-021 редкий: публикация ручной air-новости без song_id (только текст). */
    private fun publishNewsWithoutVideo(news: News) {
        val groupId = KaraokeProperties.getString("vkGroupId")
        val message =
            buildString {
                append(news.title)
                if (news.body.isNotBlank()) {
                    append("\n\n")
                    append(news.body)
                }
                if (!news.link.isNullOrBlank()) {
                    append("\n")
                    append(news.link)
                }
            }
        val client = VkApiClient()
        val result = client.wallPost(groupId, message, attachments = null)
        if (result.state == VkAutoPublishState.PUBLISHED) {
            publishedNewsIdsWithoutSong.add(news.id)
        } else {
        }
    }

    /** FR-006: проверяет, не превышен ли лимит постов в час. Если нет — занимает слот. */
    private fun acquireRateLimitSlot(): Boolean {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - 3_600_000L
        synchronized(postTimestamps) {
            postTimestamps.removeAll { it < oneHourAgo }
            val limit = KaraokeProperties.getLong("vkAutoPublishRateLimitPerHour").let { if (it <= 0) 3L else it }
            if (postTimestamps.size >= limit) return false
            postTimestamps.add(now)
        }
        return true
    }

    /** Cheap SELECT id песен в состоянии RENDERING (из player_readiness_flags JSON) с пустым id_vk. */
    private fun loadRenderingCandidateIds(): List<Long> {
        val connection = WORKING_DATABASE.getConnection() ?: return emptyList()
        val result = mutableListOf<Long>()
        try {
            connection.createStatement().use { st ->
                val rs =
                    st.executeQuery(
                        """
                        SELECT id, player_readiness_flags
                        FROM tbl_songs
                        WHERE (id_vk IS NULL OR id_vk = '')
                          AND player_readiness_flags LIKE '%vkAutoPublishState%'
                        """.trimIndent(),
                    )
                rs.use {
                    while (rs.next()) {
                        val flags = rs.getString("player_readiness_flags") ?: ""
                        if (flags.contains("\"vkAutoPublishState\":\"rendering\"")) {
                            result.add(rs.getLong("id"))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
        }
        return result
    }

    /** SQL-кандидаты: опубликованные air-новости (с song_id или с link=/song?id=...). */
    private fun loadNewsCandidates(): List<News> {
        val connection = WORKING_DATABASE.getConnection() ?: return emptyList()
        val result = mutableListOf<News>()
        try {
            connection.createStatement().use { st ->
                val rs =
                    st.executeQuery(
                        """
                        SELECT id, title, body, link, song_id, category, publish_at
                        FROM tbl_news
                        WHERE category = 'air'
                          AND publish_at IS NOT NULL AND publish_at <= now()
                        ORDER BY publish_at ASC, id ASC
                        """.trimIndent(),
                    )
                rs.use {
                    while (rs.next()) {
                        val news = News(database = WORKING_DATABASE)
                        news.id = rs.getLong("id")
                        news.title = rs.getString("title") ?: ""
                        news.body = rs.getString("body") ?: ""
                        news.link = rs.getString("link")
                        news.songId = rs.getLong("song_id").takeIf { !rs.wasNull() }
                        news.category = rs.getString("category") ?: "air"
                        news.publishAt = rs.getTimestamp("publish_at")
                        result.add(news)
                    }
                }
            }
        } catch (e: SQLException) {
        }
        return result
    }

    /** Определяет songId из news.song_id или парсингом news.link (/song?id=<id>). */
    private fun resolveSongId(news: News): Long? {
        news.songId?.let { return it }
        val link = news.link ?: return null
        val regex = Regex("""song\?id=(\d+)""")
        return regex
            .find(link)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
    }

    /** Находит терминальную render-задачу RENDER_MP4_DEMO для песни (если она есть и не в работе). */
    private fun findRenderDemoProcess(songId: Long): RenderProcessInfo? {
        val connection = WORKING_DATABASE.getConnection() ?: return null
        try {
            connection.createStatement().use { st ->
                // 2026-08-02 fix: process_status, не status (старое имя колонки, несовместимо с текущей схемой).
                val rs =
                    st.executeQuery(
                        """
                        SELECT process_status, id
                        FROM tbl_processes
                        WHERE song_id = $songId AND process_type = 'RENDER_MP4_DEMO'
                        ORDER BY id DESC LIMIT 1
                        """.trimIndent(),
                    )
                rs.use {
                    if (rs.next()) {
                        return RenderProcessInfo(rs.getString("process_status"), rs.getLong("id"))
                    }
                }
            }
        } catch (e: SQLException) {
        }
        return null
    }

    private data class RenderProcessInfo(
        val status: String,
        val id: Long,
    )
}
