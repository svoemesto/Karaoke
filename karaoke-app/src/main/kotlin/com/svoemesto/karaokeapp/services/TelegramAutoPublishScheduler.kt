package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.Song
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.sql.SQLException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Плановый тик автопубликации демо-версий песен в Telegram (Фаза 2,
 * specs/113-telegram-demo-publish, FR-001).
 *
 * Вызывается с фиксированной задержкой (~60 секунд — короче, чем скользящее окно
 * `telegramAutoPublishWindowMinutes` по умолчанию 59 минут (временно для отладки),
 * чтобы гарантированно поймать любую песню, чья date/time попала в окно). На каждый тик:
 *
 *  1. Если `telegramAutoPublishEnabled=false` — no-op (FR-013).
 *  2. Cheap SELECT кандидатов из `tbl_songs` (только id + publish_date/publish_time,
 *     без текстов/маркеров/base64) с фильтром `publish_date IS NOT NULL AND
 *     publish_time IS NOT NULL AND id_telegram_demo = ''` (FR-008: не трогаем уже
 *     опубликованные).
 *  3. Для каждой строки парсит dateTimePublish и фильтрует по окну
 *     `[now - window, now]` (Q1 clarify: «5-10 минут»). Прошедшие даты — skip
 *     («опоздавшая», FR-001 уточнение).
 *  4. Загружает полный `Song` через `Song.loadFromDbById` (только для прошедших фильтр)
 *     и вызывает [TelegramAutoPublishService.publishToTelegram] (allowPastDate=false).
 *
 * Дополнительно — фаза «resume RENDERING»: песни, у которых state=rendering (из
 * player_readiness_flags), проверяются на завершение их `RENDER_MP4_DEMO` задачи
 * (статус DONE/ERROR) и вызывают [TelegramAutoPublishService.onRenderCompleted] для
 * продолжения публикации. Это替代ляет прямой callback из KaraokeProcessWorker
 * (worker не знает про TelegramAutoPublishService) и делает scheduler единственной
 * точкой orchestration.
 *
 * Паттерн — по образцу `SongReleaseAnnouncementScheduler` (константный `fixedDelay`,
 * window читается из KaraokeProperties): `fixedDelayString` с SpEL `${...}` здесь не
 * работает, т.к. KaraokeProperties хранит значения в собственном base64-файле, а не
 * в Spring `application.properties`. Ошибки логируются и не прерывают следующий тик
 * (тот же подход, что `SongReleaseAnnouncementScheduler.checkOnAir`).
 *
 * @see docs/features/telegram-auto-publish.md
 */
@Component
class TelegramAutoPublishScheduler {
    // Периодичность тика: 60 секунд. Короче window (59 мин по умолчанию, временно для отладки), чтобы любая песня,
    // чья date/time попала в окно, гарантированно поймана. fixedDelay (не fixedRate) —
    // гарантия, что долгий тик (несколько sendVideo с retry) не наезжает на следующий.
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    fun tick() {
        val tickStartMs = System.currentTimeMillis()
        if (!KaraokeProperties.getBoolean("telegramAutoPublishEnabled")) {
            return
        }

        val channelId = KaraokeProperties.getString("telegramAutoPublishChannelId")
        if (channelId.isBlank()) {
            // FR-013: без channelId публикация невозможна — логируем и пропускаем тик.
            return
        }

        try {
            val beforeResume = System.currentTimeMillis()
            resumeRenderingSongs()
            val beforePublish = System.currentTimeMillis()
            publishScheduledSongs()
        } catch (e: Exception) {
            e.printStackTrace()
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
            if (song.idTelegramDemo.isNotEmpty()) continue
            if (song.telegramAutoPublishState != TelegramAutoPublishState.RENDERING.code) continue

            val renderProcess = findRenderDemoProcess(songId)
            if (renderProcess == null) continue // ещё в очереди/работает
            val isDone = renderProcess.status == "DONE"
            val isError = renderProcess.status == "ERROR"
            if (!isDone && !isError) continue

            val error = if (isError) "RENDER_MP4_DEMO failed (status=ERROR)" else null
            TelegramAutoPublishService.onRenderCompleted(songId, success = isDone, error = error)
        }
    }

    // Фаза 2 тика: песни с заполненными date/time и пустым idTelegramDemo → publishToTelegram.
    private fun publishScheduledSongs() {
        val windowMinutes = KaraokeProperties.getLong("telegramAutoPublishWindowMinutes").let { if (it <= 0) 59L else it }
        val candidates = loadWindowCandidateIds(windowMinutes)
        for (songId in candidates) {
            val song =
                Song.loadFromDbById(
                    id = songId,
                    database = WORKING_DATABASE,
                    storageService = KSS_APP,
                    storageApiClient = SAC_APP,
                )
            if (song == null) {
                continue
            }
            // allowPastDate=true: окно (windowMinutes) — единственный контроль допустимого
            // опоздания для auto-публикации. Без этого бот пропустит любую песню, чьё
            // publishTime прошло более нескольких секунд назад (по строгому Q1 clarify
            // "опоздавшие").
            val result = TelegramAutoPublishService.publishToTelegram(song, allowPastDate = true)
        }
    }

    // Cheap SELECT id песен в состоянии RENDERING (из player_readiness_flags JSON) с пустым
    // id_telegram_demo. Без loadListFromDb (без тяжёлых полей).
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
                        WHERE (id_telegram_demo IS NULL OR id_telegram_demo = '')
                          AND player_readiness_flags LIKE '%telegramAutoPublishState%'
                        """.trimIndent(),
                    )
                rs.use {
                    while (rs.next()) {
                        val flags = rs.getString("player_readiness_flags") ?: ""
                        if (flags.contains("\"telegramAutoPublishState\":\"rendering\"")) {
                            result.add(rs.getLong("id"))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
        }
        return result
    }

    // Cheap SELECT id + publish_date/publish_time (без текстов/маркеров) с фильтром по
    // заполненности date/time и пустому id_telegram_demo. Окно [now - window, now]
    // применяется на стороне Kotlin (Postgres to_timestamp не нужен — парсим в Kotlin,
    // тот же формат "dd.MM.yy HH:mm", что Song.dateTimePublish).
    private fun loadWindowCandidateIds(windowMinutes: Long): List<Long> {
        val connection = WORKING_DATABASE.getConnection() ?: return emptyList()
        val result = mutableListOf<Long>()
        val now = nowMoscow()
        val windowStart = Date(now.time - windowMinutes * 60_000L)
        try {
            connection.createStatement().use { st ->
                val rs =
                    st.executeQuery(
                        """
                        SELECT id, publish_date, publish_time
                        FROM tbl_songs
                        WHERE publish_date IS NOT NULL AND publish_date != ''
                          AND publish_time IS NOT NULL AND publish_time != ''
                          AND (id_telegram_demo IS NULL OR id_telegram_demo = '')
                        """.trimIndent(),
                    )
                rs.use {
                    while (rs.next()) {
                        val date = rs.getString("publish_date") ?: ""
                        val time = rs.getString("publish_time") ?: ""
                        val publishAt = parseDateTimePublish(date, time) ?: continue
                        // Q1 clarify: окно (now - window, now] — только что наступившие.
                        // Прошедшие за пределами окна — skip («опоздавшая»).
                        if (publishAt > windowStart && publishAt <= now) {
                            result.add(rs.getLong("id"))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
        }
        return result
    }

    // Находит терминальную render-задачу RENDER_MP4_DEMO для песни (если она есть и не в работе).
    // Возвращает пару (status, id) или null, если задача ещё WORKING/WAITING или не найдена.
    private fun findRenderDemoProcess(songId: Long): RenderProcessInfo? {
        val connection = WORKING_DATABASE.getConnection() ?: return null
        try {
            connection.createStatement().use { st ->
                // 2026-08-02 fix: process_status, не status (старое имя, несовместимо с текущей схемой).
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

    private fun parseDateTimePublish(
        date: String,
        time: String,
    ): Date? =
        if (date.isBlank() || time.isBlank()) {
            null
        } else {
            try {
                // Явная таймзона MSK: в контейнере karaoke-app JVM-локаль = UTC (TZ env не
                // применяется к SimpleDateFormat), а publish_date/publish_time в БД
                // интерпретируются как московское время. Без явной таймзоны парсинг
                // даёт UTC-время, и сравнение с nowMoscow() (MSK) даёт ошибку в 3 часа
                // (диагностировано 02.08.2026 — песня 14:00 не публиковалась).
                SimpleDateFormat("dd.MM.yy HH:mm")
                    .apply {
                        timeZone = TimeZone.getTimeZone("Europe/Moscow")
                    }.parse("$date $time")
            } catch (_: Exception) {
                null
            }
        }

    private fun nowMoscow(): Date = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).time

    private data class RenderProcessInfo(
        val status: String,
        val id: Long,
    )
}
