package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProcess
import com.svoemesto.karaokeapp.KaraokeProcessTypes
import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.News
import com.svoemesto.karaokeapp.model.PublicationType
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SongField
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import java.io.File
import java.sql.SQLException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/**
 * Выполняет полный цикл автопубликации песни в группу ВКонтакте (specs/121-vk-news-auto-publish).
 *
 * Два типа публикации (FR-027): [PublicationType.AIR] (авто, по `tbl_news`) и
 * [PublicationType.PREMIUM] (ручной, кнопка в карточке песни). Идемпотентность — общая
 * по [Song.idVk] (один пост на песню, независимо от типа, FR-008). После успеха бот
 * записывает id поста в `Song.idVk` через [Song.saveToDb] (с диффом, recordhash-триггером,
 * SSE — Constitution Principle II/III).
 *
 * Поток: проверка `idVk` (FR-008) → проверка готовности `isContentReady` (FR-022) →
 * проверка демо-MP4 (FR-020, рендер при необходимости) → формирование текста по шаблону
 * (FR-023, через [VkTemplateService]) → `video.save` + `wall.post` (FR-019, через
 * [VkApiClient]) → запись `idVk` (FR-004). При сбое — 3 ретрая с backoff в [VkApiClient].
 *
 * @see docs/features/vk-news-auto-publish.md
 */
object VkAutoPublishService {
    private val client = VkApiClient()
    private val warmupClient = VkPreviewWarmupClient()
    private val songLocks = ConcurrentHashMap<Long, Any>()
    private const val PREWARM_FAILURE_PREFIX = "preview prewarm failed:"

    private fun lockFor(songId: Long): Any = songLocks.computeIfAbsent(songId) { Any() }

    /**
     * Выполняет полный цикл публикации [song] в группу ВК типа [type] (FR-001, FR-008,
     * FR-022, FR-020, FR-019, FR-004, FR-026).
     *
     * @param song Песня для публикации.
     * @param type Тип публикации (AIR — авто, PREMIUM — ручной/при становлении доступной). Дефолт AIR (FR-027).
     * @param persistPostId `true` (по умолчанию) — после успешной wall.post записать id поста в
     *   `Song.idVk` через штатный saveToDb (FR-004). `false` — только отправить, **не сохранять**
     *   post_id (используется для PREMIUM-публикации при становлении песни доступной premium-подписчикам,
     *   чтобы этот же слот `idVk` мог заполнить будущая AIR-публикация при выходе песни в эфир).
     * @return [VkAutoPublishResult] с финальным состоянием.
     */
    fun publishToVk(
        song: Song,
        type: PublicationType = PublicationType.AIR,
        persistPostId: Boolean = true,
    ): VkAutoPublishResult {
        // FR-008: идемпотентность — уже опубликовано, ничего не делаем.
        // Для PREMIUM: если idVk заполнен — это значит, что air-публикация уже прошла;
        // повторный premium в этом случае бесполезен (премиум-период заведомо позади), skip.
        if (song.idVk.isNotEmpty()) {
            return VkAutoPublishResult(
                state = VkAutoPublishState.PUBLISHED,
                postId = song.idVk,
            )
        }

        // Для PREMIUM: если уже есть newsPremiumVkSent=true — повторно не публикуем
        // (страховка от дублей между тиками scheduler'a и ручными вызовами).
        if (type == PublicationType.PREMIUM && song.newsPremiumVkSent) {
            return VkAutoPublishResult(
                state = VkAutoPublishState.PUBLISHED,
                postId = "",
            )
        }

        // FR-022: песня должна быть готова (контент), иначе публикация бессмысленна.
        if (!song.isContentReady) {
            return VkAutoPublishResult(
                state = VkAutoPublishState.SCHEDULED,
                error = "not content-ready (idStatus=${song.idStatus})",
            )
        }

        // Рендерим шаблон, чтобы узнать — нужно ли прикреплять демо-MP4 (маркер {demoVideo}).
        val news = newsFor(song, type)
        val rendered = VkTemplateService.renderWithFlags(VkTemplateService.templateFor(type), song, news)
        if (!rendered.includeDemoVideo) {
            // Шаблон без {demoVideo} — публикуем только текст, без рендера/прикрепления видео.
            return publishTextOnly(song, rendered.message, type, persistPostId)
        }

        // FR-020: есть ли готовый демо-MP4 нужного размера?
        val demoFile = File(song.pathToFileRenderMp4ForVersion(com.svoemesto.karaokeapp.services.RenderVersion.DEMO))
        val maxFileSizeBytes =
            KaraokeProperties.getLong("vkAutoPublishMaxVideoSizeMb").let { if (it <= 0) 50L else it } * 1024 * 1024

        if (!demoFile.exists() || demoFile.length() > maxFileSizeBytes) {
            return startRenderAndReturn(song, type, persistPostId)
        }

        return publishFile(song, demoFile, rendered.message, type, persistPostId)
    }

    /**
     * Callback завершения рендера `RENDER_MP4_DEMO` (FR-020 сц. 2/3). Вызывается
     * scheduler'ом при обнаружении DONE/ERROR задачи.
     *
     * @param persistPostId прокидывается из [publishToVk] — false для PREMIUM-вызовов.
     */
    fun onRenderCompleted(
        songId: Long,
        type: PublicationType,
        persistPostId: Boolean = true,
        success: Boolean,
        error: String?,
    ): VkAutoPublishResult? {
        val song =
            Song.loadFromDbById(
                id = songId,
                database = WORKING_DATABASE,
                storageService = KSS_APP,
                storageApiClient = SAC_APP,
            ) ?: return null

        // specs/122 fix: динамически переопределяем effective-параметры если рендер был для PREMIUM.
        // VkAutoPublishScheduler.resumeRenderingSongs вызывает onRenderCompleted со ЗНАЧЕНИЯМИ
        // ПО УМОЛЧАНИЮ (хотя у VK он передаёт явно type=AIR), но если в момент завершения флаг
        // newsPremiumPublishPending=true — этот рендер для PREMIUM, и нужно сменить шаблон +
        // persist=false, иначе слот idVk займётся преждевременно.
        val effectiveType =
            if (song.newsPremiumPublishPending) PublicationType.PREMIUM else type
        val effectivePersistPostId =
            if (song.newsPremiumPublishPending) false else persistPostId

        // Идемпотентность: если за время рендера песню уже опубликовали (вручную) — ничего не делаем.
        if (song.idVk.isNotEmpty()) {
            return VkAutoPublishResult(
                state = VkAutoPublishState.PUBLISHED,
                postId = song.idVk,
            )
        }

        if (effectiveType == PublicationType.PREMIUM && song.newsPremiumVkSent) {
            return VkAutoPublishResult(
                state = VkAutoPublishState.PUBLISHED,
                postId = "",
            )
        }

        if (!success) {
            writeFailure(song, "render failed: ${error ?: "unknown render error"}")
            return VkAutoPublishResult(
                state = VkAutoPublishState.SEND_FAILED,
                error = "render failed: ${error ?: "unknown render error"}",
            )
        }

        val demoFile = File(song.pathToFileRenderMp4ForVersion(com.svoemesto.karaokeapp.services.RenderVersion.DEMO))
        val maxFileSizeBytes =
            KaraokeProperties.getLong("vkAutoPublishMaxVideoSizeMb").let { if (it <= 0) 50L else it } * 1024 * 1024

        if (!demoFile.exists()) {
            writeFailure(song, "render completed but demo file not found: ${demoFile.absolutePath}")
            return VkAutoPublishResult(
                state = VkAutoPublishState.SEND_FAILED,
                error = "demo file not found after render",
            )
        }
        if (demoFile.length() > maxFileSizeBytes) {
            writeFailure(song, "demo file ${demoFile.length()} bytes still exceeds limit $maxFileSizeBytes bytes after re-render")
            return VkAutoPublishResult(
                state = VkAutoPublishState.SEND_FAILED,
                error = "file still exceeds limit after re-render",
            )
        }
        // Рендерим шаблон для текста поста (видео уже готово — включаем по маркеру {demoVideo}).
        // Используем effectiveType / effectivePersistPostId (specs/122 fix) — для premium-рендеров
        // берётся PREMIUM-шаблон и persist=false.
        val news = newsFor(song, effectiveType)
        val rendered = VkTemplateService.renderWithFlags(VkTemplateService.templateFor(effectiveType), song, news)
        return publishFile(song, demoFile, rendered.message, effectiveType, effectivePersistPostId)
    }

    /** Ставит задачу `RENDER_MP4_DEMO` в очередь (FR-020 сц. 2/3) и возвращает RENDERING. */
    private fun startRenderAndReturn(
        song: Song,
        type: PublicationType,
        persistPostId: Boolean = true,
    ): VkAutoPublishResult {
        val processId =
            KaraokeProcess.createProcess(
                song = song,
                action = KaraokeProcessTypes.RENDER_MP4_DEMO,
                doWait = false,
                prior = 1,
                threadId = KaraokeProcess.THREAD_LANE_HEAVY_RENDER,
                context =
                    mapOf(
                        "width" to 1280,
                        "height" to 720,
                        "fps" to 30,
                        "version" to com.svoemesto.karaokeapp.services.RenderVersion.DEMO.name,
                        "vkType" to type.code,
                    ),
            )
        if (processId <= 0) {
            writeFailure(song, "could not enqueue RENDER_MP4_DEMO (processId=$processId)")
            return VkAutoPublishResult(
                state = VkAutoPublishState.SEND_FAILED,
                error = "could not enqueue render task",
            )
        }
        song.vkAutoPublishState = VkAutoPublishState.RENDERING.code
        song.vkAutoPublishLastError = ""
        song.saveToDb()
        return VkAutoPublishResult(state = VkAutoPublishState.RENDERING)
    }

    /** Выполняет `video.save` + `wall.post` (FR-019) с демо-MP4 и записывает результат (FR-004) или ошибку. */
    private fun publishFile(
        song: Song,
        demoFile: File,
        message: String,
        type: PublicationType,
        persistPostId: Boolean = true,
    ): VkAutoPublishResult {
        val groupId = KaraokeProperties.getString("vkGroupId")
        if (groupId.isBlank()) {
            writeFailure(song, "vkGroupId is empty — cannot publish")
            return VkAutoPublishResult(
                state = VkAutoPublishState.SEND_FAILED,
                error = "vkGroupId is empty",
            )
        }

        val lock = lockFor(song.id)
        return synchronized(lock) {
            // Идемпотентность под локом: если параллельный вызов уже опубликовал — выходим.
            // song здесь может быть stale (загружен до того, как параллельный onRenderCompleted
            // успел сделать wall.post), поэтому перечитываем idVk из БД под локом.
            val persistedIdVk = Song.loadFromDbById(id = song.id, database = WORKING_DATABASE, storageService = KSS_APP, storageApiClient = SAC_APP)?.idVk ?: ""
            if (persistedIdVk.isNotEmpty()) {
                song.fields[SongField.ID_VK] = persistedIdVk
                return@synchronized VkAutoPublishResult(
                    state = VkAutoPublishState.PUBLISHED,
                    postId = persistedIdVk,
                )
            }
            if (type == PublicationType.PREMIUM && song.newsPremiumVkSent) {
                return@synchronized VkAutoPublishResult(
                    state = VkAutoPublishState.PUBLISHED,
                    postId = "",
                )
            }

            // specs/130-vk-preview-generation: прогреваем PNG до публикации, чтобы VK-бот
            // получил готовый файл без задержки первой генерации. Ошибка прогрева блокирует
            // wall.post и записывается через SEND_FAILED с префиксом.
            val warmup = warmupClient.warmup(song.id)
            if (warmup.status == VkPreviewWarmupStatus.FAILED) {
                val reason = "$PREWARM_FAILURE_PREFIX ${warmup.error ?: "unknown"} (http=${warmup.httpStatus}, attempts=${warmup.attempts})"
                writeFailure(song, reason)
                return@synchronized VkAutoPublishResult(
                    state = VkAutoPublishState.SEND_FAILED,
                    error = reason,
                )
            }

            // PUBLISHING — фиксируем начало отправки (для UI «Публикуется»).
            song.vkAutoPublishState = VkAutoPublishState.PUBLISHING.code
            song.vkAutoPublishLastAttemptAt = nowIso8601()
            song.vkAutoPublishLastError = ""
            song.saveToDb()

            val result = client.sendPostWithVideo(groupId, message, demoFile, song.id)

            if (result.state == VkAutoPublishState.PUBLISHED && result.postId != null) {
                if (persistPostId) {
                    // FR-004 (AIR): запись id поста в Song.idVk через штатный saveToDb → SSE + sync.
                    song.fields[SongField.ID_VK] = result.postId
                } else {
                    // PREMIUM: не сохраняем post_id — этот слот заполнится будущей AIR-публикацией.
                    song.newsPremiumVkSent = true
                }
                song.vkAutoPublishState = VkAutoPublishState.PUBLISHED.code
                song.vkAutoPublishLastError = ""
                song.saveToDb()
                return@synchronized result
            }

            writeFailure(song, result.error ?: "sendPostWithVideo failed (no error description)")
            result
        }
    }

    /** Публикует только текст (без видео) — для шаблонов без маркера `{demoVideo}`. */
    private fun publishTextOnly(
        song: Song,
        message: String,
        type: PublicationType,
        persistPostId: Boolean = true,
    ): VkAutoPublishResult {
        val groupId = KaraokeProperties.getString("vkGroupId")
        if (groupId.isBlank()) {
            writeFailure(song, "vkGroupId is empty — cannot publish")
            return VkAutoPublishResult(
                state = VkAutoPublishState.SEND_FAILED,
                error = "vkGroupId is empty",
            )
        }

        val lock = lockFor(song.id)
        return synchronized(lock) {
            // Идемпотентность под локом: см. publishFile.
            val persistedIdVk = Song.loadFromDbById(id = song.id, database = WORKING_DATABASE, storageService = KSS_APP, storageApiClient = SAC_APP)?.idVk ?: ""
            if (persistedIdVk.isNotEmpty()) {
                song.fields[SongField.ID_VK] = persistedIdVk
                return@synchronized VkAutoPublishResult(
                    state = VkAutoPublishState.PUBLISHED,
                    postId = persistedIdVk,
                )
            }
            if (type == PublicationType.PREMIUM && song.newsPremiumVkSent) {
                return@synchronized VkAutoPublishResult(
                    state = VkAutoPublishState.PUBLISHED,
                    postId = "",
                )
            }

            // specs/130-vk-preview-generation: прогреваем PNG до публикации.
            val warmup = warmupClient.warmup(song.id)
            if (warmup.status == VkPreviewWarmupStatus.FAILED) {
                val reason = "$PREWARM_FAILURE_PREFIX ${warmup.error ?: "unknown"} (http=${warmup.httpStatus}, attempts=${warmup.attempts})"
                writeFailure(song, reason)
                return@synchronized VkAutoPublishResult(
                    state = VkAutoPublishState.SEND_FAILED,
                    error = reason,
                )
            }

            song.vkAutoPublishState = VkAutoPublishState.PUBLISHING.code
            song.vkAutoPublishLastAttemptAt = nowIso8601()
            song.vkAutoPublishLastError = ""
            song.saveToDb()

            val result = client.wallPost(groupId, message, attachments = null)

            if (result.state == VkAutoPublishState.PUBLISHED && result.postId != null) {
                if (persistPostId) {
                    song.fields[SongField.ID_VK] = result.postId
                } else {
                    song.newsPremiumVkSent = true
                }
                song.vkAutoPublishState = VkAutoPublishState.PUBLISHED.code
                song.vkAutoPublishLastError = ""
                song.saveToDb()
                return@synchronized result
            }

            writeFailure(song, result.error ?: "wallPost (text-only) failed")
            result
        }
    }

    /** Для AIR — ищет связанную опубликованную новость `air`; для PREMIUM — null (FR-026). */
    private fun newsFor(
        song: Song,
        type: PublicationType,
    ): News? {
        if (type != PublicationType.AIR) return null
        return findAirNewsForSong(song.id)
    }

    /** Ищет опубликованную новость `air` для песни по song_id или по link=/song?id=<id>. */
    private fun findAirNewsForSong(songId: Long): News? {
        val connection = WORKING_DATABASE.getConnection() ?: return null
        try {
            val linkPattern = "%/song?id=$songId%"
            connection
                .prepareStatement(
                    """
                    SELECT id FROM ${News.TABLE_NAME}
                    WHERE category = 'air'
                      AND publish_at IS NOT NULL AND publish_at <= now()
                      AND (song_id = ? OR link LIKE ?)
                    ORDER BY id DESC LIMIT 1
                    """.trimIndent(),
                ).use { ps ->
                    ps.setLong(1, songId)
                    ps.setString(2, linkPattern)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            val newsId = rs.getLong("id")
                            return News.getById(newsId, WORKING_DATABASE)
                        }
                    }
                }
        } catch (e: SQLException) {
        }
        return null
    }

    /** Записывает состояние SEND_FAILED + lastError + lastAttemptAt через штатный saveToDb. */
    private fun writeFailure(
        song: Song,
        error: String,
    ) {
        song.vkAutoPublishState = VkAutoPublishState.SEND_FAILED.code
        song.vkAutoPublishLastAttemptAt = nowIso8601()
        song.vkAutoPublishLastError = error
        song.saveToDb()
    }

    private fun nowMoscow(): java.util.Date = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).time

    private fun nowIso8601(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").apply { timeZone = TimeZone.getTimeZone("Europe/Moscow") }.format(nowMoscow())
}
