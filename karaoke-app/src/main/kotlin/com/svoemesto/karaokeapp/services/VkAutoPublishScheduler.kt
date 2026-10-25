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
 * @see archive/docs/features/vk-news-auto-publish.md
 */
@Component
class VkAutoPublishScheduler {
    // BR-151/BR-152 (аудит #336, пакет `PKG-S3-23`): категория логов авто-публикации ВК.
    private val vkLog = org.slf4j.LoggerFactory.getLogger("infra.vk.publish")

    // In-memory идемпотентность для редкого случая (FR-004a): news.id уже опубликованных
    // без-song_id новостей. Не переживает рестарт — но дубли после рестарта маловероятны
    // (publish_at <= now() остается, но Set пуст → попытка повторной публикации; для
    // без-song_id случая это приемлемый компромисс без миграции News).
    private val publishedNewsIdsWithoutSong = ConcurrentHashMap.newKeySet<Long>()

    /**
     * BR-156: верхняя граница числа кандидатов-новостей за один тик.
     *
     * Раньше выборка была без LIMIT и читала всю историю air-новостей; теперь за тик
     * обрабатывается не больше этого числа кандидатов (остальные — на следующих тиках).
     */
    private val newsCandidateLimit = 200

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
            // BR-152: сбой БД в загрузчике кандидатов прерывает тик (false), а не
            // выглядит как «нечего публиковать».
            if (!resumeRenderingSongs()) return
            if (!publishScheduledNews()) return
        } catch (e: Exception) {
            // BR-151 (аудит #336, пакет `PKG-S3-23`): тик больше не «падает молча» —
            // исключение логируется через SLF4J (категория + Throwable).
            vkLog.error("VkAutoPublishScheduler.tick failed", e)
        }
    }

    // Фаза 1 тика: песни в состоянии RENDERING, чья render-задача завершена → продолжить.
    //
    // BR-152: сбой загрузчика кандидатов возвращает null — тик прерывается, вместо того
    // чтобы тихо «не публиковать ничего».
    private fun resumeRenderingSongs(): Boolean {
        val renderingIds = loadRenderingCandidateIds() ?: return false
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
        return true
    }

    // Фаза 2 тика: опубликованные air-новости с пустым idVk у связанной песни → publishToVk.
    //
    // BR-152: сбой загрузчика кандидатов возвращает null — тик прерывается.
    private fun publishScheduledNews(): Boolean {
        val candidates = loadNewsCandidates() ?: return false
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
                // BR-157 (аудит #336, пакет `PKG-S3-23`): слот rate-limit РЕЗЕРВИРУЕТСЯ,
                // но расходуется только при фактическом посте. Раньше слот занимался ДО
                // попытки, и результат `SCHEDULED` (контент не готов) / `RENDERING`
                // (только поставили рендер) израсходовал лимит без единого поста: после
                // трёх таких тиков бот замолкал до конца часа.
                val reservedAt = reserveRateLimitSlot() ?: continue // FR-006
                // BR-151: сбой обработки ОДНОЙ новости не должен обрывать цикл —
                // остальные новости тика публикуются как обычно.
                val publishResult =
                    runCatching {
                        VkAutoPublishService.publishToVk(song, PublicationType.AIR)
                    }.onFailure { e ->
                        vkLog.warn("VkAutoPublishScheduler: не удалось опубликовать песню {} (songId={})", song.songName, song.id, e)
                    }.getOrNull()
                if (publishResult?.state != VkAutoPublishState.PUBLISHED) {
                    releaseRateLimitSlot(reservedAt)
                }
            } else {
                // FR-021 редкий: ручная air-новость без song_id → пост без видео.
                if (publishedNewsIdsWithoutSong.contains(news.id)) continue // FR-004a
                // BR-157: слот возвращается, если поста фактически не было.
                val reservedAt = reserveRateLimitSlot() ?: continue
                // BR-151: изоляция сбоя одной новости.
                val published =
                    runCatching {
                        publishNewsWithoutVideo(news)
                    }.onFailure { e ->
                        vkLog.warn("VkAutoPublishScheduler: не удалось опубликовать новость без видео (newsId={})", news.id, e)
                    }.getOrDefault(false)
                if (!published) {
                    releaseRateLimitSlot(reservedAt)
                }
            }
        }
        return true
    }

    /** FR-021 редкий: публикация ручной air-новости без song_id (только текст). */
    private fun publishNewsWithoutVideo(news: News): Boolean {
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
        // specs/437 (#161): сбой VK API (сеть / нет прокси) не должен валить @Scheduled-тик.
        // Пост НЕ помечаем опубликованным — попробуем снова на следующем тике.
        val result =
            try {
                client.wallPost(groupId, message, attachments = null)
            } catch (e: Exception) {
                vkLog.warn(
                    "VkAutoPublishScheduler.publishNewsWithoutVideo: wallPost exception for news {} ({})",
                    news.id,
                    e.message ?: e.javaClass.simpleName,
                    e,
                )
                return false
            }
        if (result.state == VkAutoPublishState.PUBLISHED) {
            publishedNewsIdsWithoutSong.add(news.id)
            return true
        }
        vkLog.warn(
            "VkAutoPublishScheduler.publishNewsWithoutVideo: news {} not published: {}",
            news.id,
            result.error,
        )
        return false
    }

    /**
     * FR-006: резервирует слот rate-limit (постов в час).
     *
     * BR-157 (аудит #336, пакет `PKG-S3-23`): резервирует, а не «тратит». Если поста
     * фактически не вышло (`SCHEDULED`/`RENDERING`/`SEND_FAILED`), слот возвращается
     * через [releaseRateLimitSlot] — иначе лимит расходовался бы на публикации, которых
     * не было, и бот замолкал до конца часа.
     *
     * @return timestamp резервирования или `null`, если лимит исчерпан.
     */
    private fun reserveRateLimitSlot(): Long? {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - 3_600_000L
        synchronized(postTimestamps) {
            postTimestamps.removeAll { it < oneHourAgo }
            val limit = KaraokeProperties.getLong("vkAutoPublishRateLimitPerHour").let { if (it <= 0) 3L else it }
            if (postTimestamps.size >= limit) return null
            postTimestamps.add(now)
            return now
        }
    }

    /** BR-157: возвращает слот rate-limit, если пост фактически не состоялся. */
    private fun releaseRateLimitSlot(reservedAt: Long) {
        synchronized(postTimestamps) {
            postTimestamps.remove(reservedAt)
        }
    }

    /** Cheap SELECT id песен в состоянии RENDERING (из player_readiness_flags JSON) с пустым id_vk. */
    private fun loadRenderingCandidateIds(): List<Long>? {
        val connection =
            WORKING_DATABASE.getConnection()
                ?: run {
                    vkLog.warn("VkAutoPublishScheduler: нет соединения с БД — кандидаты RENDERING не прочитаны")
                    return null
                }
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
            // BR-152: сбой SELECT раньше был неотличим от «кандидатов нет».
            vkLog.warn(
                "VkAutoPublishScheduler: не удалось прочитать кандидатов RENDERING (sqlState={})",
                e.sqlState,
                e,
            )
            return null
        }
        return result
    }

    /** SQL-кандидаты: опубликованные air-новости (с song_id или с link=/song?id=...). */
    private fun loadNewsCandidates(): List<News>? {
        val connection =
            WORKING_DATABASE.getConnection()
                ?: run {
                    vkLog.warn("VkAutoPublishScheduler: нет соединения с БД — кандидаты air-новостей не прочитаны")
                    return null
                }
        val result = mutableListOf<News>()
        try {
            // BR-156 (аудит #336, пакет `PKG-S3-23`): кандидаты отбираются ОДНИМ SQL
            // с JOIN на tbl_songs и LIMIT.
            //
            // Раньше SELECT читал ВСЕ air-новости за всю историю (без LIMIT), и для
            // каждой в цикле грузилась полная песня (`Song.loadFromDbById` — тяжёлый
            // `SELECT *`), а уже потом проверялось `song.idVk.isNotEmpty()`. Стоимость
            // тика росла линейно с историей новостей и повторялась каждые 60 с.
            //
            // Теперь: LEFT JOIN подтягивает `id_vk` связанной песни (в т.ч. по ссылке
            // вида `/song?id=<id>`), уже опубликованные песни отсекаются ещё в SQL, а
            // выборка ограничена `LIMIT` — за тик грузится не больше `newsCandidateLimit`
            // песен вместо всей истории.
            val sql =
                """
                SELECT n.id, n.title, n.body, n.link, n.song_id, n.category, n.publish_at, s.id_vk AS song_id_vk
                FROM tbl_news n
                LEFT JOIN tbl_songs s
                       ON s.id = COALESCE(
                              n.song_id,
                              NULLIF(substring(n.link FROM 'song.id=([0-9]+)'), '')::bigint,
                          )
                WHERE n.category = 'air'
                  AND n.publish_at IS NOT NULL AND n.publish_at <= now()
                ORDER BY n.publish_at ASC, n.id ASC
                LIMIT ?
                """.trimIndent()
            val ps = connection.prepareStatement(sql)
            ps.use {
                it.setInt(1, newsCandidateLimit)
                it.executeQuery().use { rs ->
                    while (rs.next()) {
                        val songIdVk = rs.getString("song_id_vk")
                        // BR-156: песня уже опубликована — кандидат не нужен, полную песню
                        // не грузим.
                        if (!songIdVk.isNullOrEmpty()) continue
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
            // BR-152: сбой SELECT раньше был неотличим от «новостей нет».
            vkLog.warn(
                "VkAutoPublishScheduler: не удалось прочитать кандидатов air-новостей (sqlState={})",
                e.sqlState,
                e,
            )
            return null
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
        val connection =
            WORKING_DATABASE.getConnection()
                ?: run {
                    vkLog.warn("VkAutoPublishScheduler: нет соединения с БД — render-задача не найдена (songId={})", songId)
                    return null
                }
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
            // BR-152: сбой SELECT раньше был неотличим от «задачи нет».
            vkLog.warn(
                "VkAutoPublishScheduler: не удалось найти render-задачу (songId={}, sqlState={})",
                songId,
                e.sqlState,
                e,
            )
            return null
        }
        return null
    }

    private data class RenderProcessInfo(
        val status: String,
        val id: Long,
    )
}
