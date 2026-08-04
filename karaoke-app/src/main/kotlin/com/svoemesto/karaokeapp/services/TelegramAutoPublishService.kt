package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProcess
import com.svoemesto.karaokeapp.KaraokeProcessTypes
import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SongField
import com.svoemesto.karaokeapp.model.PublicationType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Бизнес-логика автопубликации демо-версии песни в Telegram-канал (Фаза 2,
 * specs/113-telegram-demo-publish).
 *
 * Центральный сервис, вызываемый из двух мест:
 *  - [TelegramAutoPublishScheduler.tick] — плановый тик по наступлению date/time песни;
 *  - `POST /api/song/publishToTelegramNow` (кнопка «Опубликовать сейчас» в webvue3).
 *
 * Реализует полный цикл FR-003 (render-or-use-existing) → FR-010 (sendVideo с retry)
 * → FR-006 (запись message_id в `Song.idTelegramDemo` через штатный
 * [Song.saveToDb]). Идемпотентен (FR-007/FR-008): если `idTelegramDemo` уже заполнен —
 * сразу возвращает `PUBLISHED` без действий. Прошедшая date/time — «опоздавшая»
 * (Q1 clarify): не публикуется, возвращает `SCHEDULED`.
 *
 * Состояние публикации хранится в JSON-блобе [Song.playerReadinessFlags] (ключи
 * `telegramAutoPublishState` / `telegramAutoPublishLastAttemptAt` /
 * `telegramAutoPublishLastError`), не отдельной колонкой — паттерн
 * specs/101-song-news-flag.
 *
 * @see docs/features/telegram-auto-publish.md
 */
object TelegramAutoPublishService {
    private val client = TelegramApiClient()

    /**
     * Выполняет полный цикл автопубликации одной песни (FR-001, FR-003, FR-006, FR-008,
     * FR-010, FR-011). Вызывается scheduler'ом (плановый тик) или endpoint'ом «Опубликовать
     * сейчас» (ручной триггер). Все шаги, кроме рендера (который ставит асинхронную задачу
     * `RENDER_MP4_DEMO`), выполняются синхронно в вызывающем потоке.
     *
     * @param song загруженная песня (полный объект через `Song.loadFromDbById`)
     * @param allowPastDate `true` для ручного триггера (кнопка «Опубликовать сейчас» —
     *   публикует даже с прошедшей date/time); `false` для scheduler'а (Q1 clarify —
     *   прошедшие даты пропускаются как «опоздавшая»). Для премиум-публикации (при
     *   `publicationType=PREMIUM`) всегда интерпретируется как `true` — у неё нет своего
     *   расписания date/time, она триггерится событием newsAvailableAnnounced false→true.
     * @param publicationType Тип публикации: `AIR` (по расписанию) или `PREMIUM` (при становлении
     *   песни доступной для premium-подписчиков — specs/122-premium-auto-publish). От типа зависит
     *   только выбор шаблона подписи (AIR-шаблон при AIR, PREMIUM-шаблон при PREMIUM).
     * @param persistMessageId `true` (по умолчанию) — после успешной sendVideo записать message_id
     *   в `Song.idTelegramDemo` через штатный saveToDb (FR-006). `false` — только отправить,
     *   **не сохранять** message_id (используется для PREMIUM-публикации, чтобы этот же слот
     *   `idTelegramDemo` мог заполнить будущая AIR-публикация при выходе песни в эфир).
     * @return результат: `PUBLISHED` (успех, `messageId` заполнен), `RENDERING` (поставлен
     *   рендер, публикация продолжится через [onRenderCompleted]), `SEND_FAILED` (ошибка
     *   send или рендера), `SCHEDULED` (песня не готова или дата в прошлом при
     *   `allowPastDate=false`)
     *
     * @see docs/features/telegram-auto-publish.md
     */
    fun publishToTelegram(
        song: Song,
        allowPastDate: Boolean = false,
        publicationType: com.svoemesto.karaokeapp.model.PublicationType = com.svoemesto.karaokeapp.model.PublicationType.AIR,
        persistMessageId: Boolean = true,
    ): TelegramAutoPublishResult {
        // FR-008: идемпотентность — уже опубликовано, ничего не делаем.
        // Для PREMIUM: если idTelegramDemo заполнен — это значит, что air-публикация уже прошла;
        // повторный premium в этом случае бесполезен (премиум-период заведомо позади), skip.
        if (song.idTelegramDemo.isNotEmpty()) {
            return TelegramAutoPublishResult(
                state = TelegramAutoPublishState.PUBLISHED,
                messageId = song.idTelegramDemo,
            )
        }

        // Для PREMIUM: если уже есть newsPremiumTelegramSent=true — повторно не публикуем
        // (страховка от дублей между тиками scheduler'a и ручными вызовами).
        if (publicationType == com.svoemesto.karaokeapp.model.PublicationType.PREMIUM &&
            song.newsPremiumTelegramSent
        ) {
            return TelegramAutoPublishResult(
                state = TelegramAutoPublishState.PUBLISHED,
                messageId = "",
            )
        }

        // FR-001 / Q1 clarify: прошедшая date/time — «опоздавшая», бот не публикует
        // (кроме ручного триггера allowPastDate=true). Для PREMIUM — всегда allowPastDate=true
        // (у премиум-публикации нет своего расписания).
        val effectiveAllowPastDate = allowPastDate || publicationType == com.svoemesto.karaokeapp.model.PublicationType.PREMIUM
        val dt = song.dateTimePublish
        if (!effectiveAllowPastDate && dt != null && dt.before(nowMoscow())) {
            return TelegramAutoPublishResult(
                state = TelegramAutoPublishState.SCHEDULED,
                error = "dateTimePublish < now() — опоздавшая публикация",
            )
        }

        // FR-011: песня должна быть публично готова (контент), иначе публикация бессмысленна.
        if (!song.isContentReady) {
            return TelegramAutoPublishResult(
                state = TelegramAutoPublishState.SCHEDULED,
                error = "not content-ready (idStatus=${song.idStatus})",
            )
        }

        // FR-003: есть ли готовый демо-MP4 нужного размера?
        val demoFile = File(song.pathToFileRenderMp4ForVersion(com.svoemesto.karaokeapp.services.RenderVersion.DEMO))
        val maxFileSizeBytes =
            KaraokeProperties.getLong("telegramAutoPublishMaxFileSizeMb").let { if (it <= 0) 50L else it } * 1024 * 1024

        if (!demoFile.exists() || demoFile.length() > maxFileSizeBytes) {
            // FR-003 сц. 2/3: файла нет или превышает лимит → ставим рендер, продолжим через
            // onRenderCompleted (вызывается scheduler'ом при обнаружении DONE/ERROR задачи).
            return startRenderAndReturn(song, publicationType)
        }

        // Файл готов и в лимите → переходим к sendVideo (FR-005, FR-010).
        return publishFile(song, demoFile, publicationType, persistMessageId)
    }

    /**
     * Callback завершения рендера `RENDER_MP4_DEMO` (FR-003 сц. 2/3). Вызывается
     * [TelegramAutoPublishScheduler] для песен в состоянии `RENDERING`, у которых
     * соответствующая `KaraokeProcess`-задача перешла в терминальный статус (DONE/ERROR).
     *
     * При `success=true` — загружает песню, проверяет готовый файл и размер, продолжает
     * публикацию через [publishFile] (sendVideo + retry). При `success=false` — пишет
     * `state=SEND_FAILED`, `lastError="render failed: <error>"`.
     *
     * @param songId id песни
     * @param publicationType тип публикации (AIR/PREMIUM) — прокидывается в publishFile для выбора шаблона.
     * @param persistMessageId true для AIR (записать idTelegramDemo после sendVideo), false для PREMIUM.
     * @param successtrue если render-задача завершилась DONE
     * @param error текст ошибки рендера (null/пусто при success=true)
     * @return результат публикации (PUBLISHED/SEND_FAILED) или null, если песня не найдена /
     *   уже опубликована / не в состоянии RENDERING
     *
     * @see docs/features/telegram-auto-publish.md
     */
    fun onRenderCompleted(
        songId: Long,
        publicationType: com.svoemesto.karaokeapp.model.PublicationType = com.svoemesto.karaokeapp.model.PublicationType.AIR,
        persistMessageId: Boolean = true,
        success: Boolean,
        error: String?,
    ): TelegramAutoPublishResult? {
        val song =
            Song.loadFromDbById(
                id = songId,
                database = WORKING_DATABASE,
                storageService = KSS_APP,
                storageApiClient = SAC_APP,
            ) ?: return null

        // specs/122 fix: динамически переопределяем effective-параметры если в момент завершения
        // рендера выяснилось, что это PREMIUM-публикация. Без этого TelegramAutoPublishScheduler.
        // resumeRenderingSongs вызывает onRenderCompleted со ЗНАЧЕНИЯМИ ПО УМОЛЧАНИЮ
        // (publicationType=AIR, persistMessageId=true), даже если рендер был запущен из
        // PremiumAutoPublishScheduler для PREMIUM-публикации. Это было критическим багом
        // 02.08.2026 — публикация пошла бы с AIR-шаблоном и записала id в idTelegramDemo,
        // не оставив слот для будущей AIR-публикации.
        val effectivePublicationType =
            if (song.newsPremiumPublishPending) com.svoemesto.karaokeapp.model.PublicationType.PREMIUM else publicationType
        val effectivePersistMessageId =
            if (song.newsPremiumPublishPending) false else persistMessageId

        // Идемпотентность: если за время рендера песню уже опубликовали (вручную) — ничего не делаем.
        if (song.idTelegramDemo.isNotEmpty()) {
            return TelegramAutoPublishResult(
                state = TelegramAutoPublishState.PUBLISHED,
                messageId = song.idTelegramDemo,
            )
        }

        // Для PREMIUM: повторный skip если уже отправлено.
        if (effectivePublicationType == com.svoemesto.karaokeapp.model.PublicationType.PREMIUM &&
            song.newsPremiumTelegramSent
        ) {
            return TelegramAutoPublishResult(
                state = TelegramAutoPublishState.PUBLISHED,
                messageId = "",
            )
        }

        if (!success) {
            writeFailure(song, "render failed: ${error ?: "unknown render error"}")
            return TelegramAutoPublishResult(
                state = TelegramAutoPublishState.SEND_FAILED,
                error = "render failed: ${error ?: "unknown render error"}",
            )
        }

        // Рендер успешен — проверяем файл и продолжаем публикацию.
        val demoFile = File(song.pathToFileRenderMp4ForVersion(com.svoemesto.karaokeapp.services.RenderVersion.DEMO))
        val maxFileSizeBytes =
            KaraokeProperties.getLong("telegramAutoPublishMaxFileSizeMb").let { if (it <= 0) 50L else it } * 1024 * 1024

        if (!demoFile.exists()) {
            writeFailure(song, "render completed but demo file not found: ${demoFile.absolutePath}")
            return TelegramAutoPublishResult(
                state = TelegramAutoPublishState.SEND_FAILED,
                error = "demo file not found after render",
            )
        }
        if (demoFile.length() > maxFileSizeBytes) {
            // Файл всё ещё превышает лимит даже после перерендера — отказ (админ должен
            // уменьшить demoFragmentBounds или увеличить telegramAutoPublishMaxFileSizeMb).
            writeFailure(song, "demo file ${demoFile.length()} bytes still exceeds limit $maxFileSizeBytes bytes after re-render")
            return TelegramAutoPublishResult(
                state = TelegramAutoPublishState.SEND_FAILED,
                error = "file still exceeds limit after re-render",
            )
        }
        return publishFile(song, demoFile, effectivePublicationType, effectivePersistMessageId)
    }

    // Ставит задачу RENDER_MP4_DEMO в очередь (FR-003 сц. 2/3) и возвращает RENDERING.
    // Дефолты DEMO (1280/720/30) — те же, что в ApiController.kt и KaraokeProcess.kt.
    // publicationType пробрасывается в context KaraokeProcess (для логирования/идентификации задачи)
    // — сам рендер DEMO один и тот же независимо от типа публикации.
    private fun startRenderAndReturn(
        song: Song,
        publicationType: com.svoemesto.karaokeapp.model.PublicationType,
    ): TelegramAutoPublishResult {
        val processId =
            KaraokeProcess.createProcess(
                song = song,
                action = KaraokeProcessTypes.RENDER_MP4_DEMO,
                doWait = true,
                prior = 1,
                threadId = KaraokeProcess.THREAD_LANE_HEAVY_RENDER,
                context =
                    mapOf(
                        "width" to 1280,
                        "height" to 720,
                        "fps" to 30,
                        "version" to com.svoemesto.karaokeapp.services.RenderVersion.DEMO.name,
                        "telegramType" to publicationType.code,
                    ),
            )
        if (processId <= 0) {
            writeFailure(song, "could not enqueue RENDER_MP4_DEMO (processId=$processId, possibly already running)")
            return TelegramAutoPublishResult(
                state = TelegramAutoPublishState.SEND_FAILED,
                error = "could not enqueue render task",
            )
        }
        song.telegramAutoPublishState = TelegramAutoPublishState.RENDERING.code
        song.telegramAutoPublishLastError = ""
        song.saveToDb()
        return TelegramAutoPublishResult(state = TelegramAutoPublishState.RENDERING)
    }

    // Выполняет sendVideo (FR-005, FR-010) и записывает результат (FR-006) или ошибку.
    // publicationType — выбор шаблона (AIR-шаблон или PREMIUM-шаблон).
    // persistMessageId — false для PREMIUM-публикации (не записывать idTelegramDemo, чтобы этот же
    // слот заполнила будущая AIR-публикация).
    private fun publishFile(
        song: Song,
        demoFile: File,
        publicationType: com.svoemesto.karaokeapp.model.PublicationType,
        persistMessageId: Boolean,
    ): TelegramAutoPublishResult {
        val channelId = KaraokeProperties.getString("telegramAutoPublishChannelId")
        if (channelId.isBlank()) {
            writeFailure(song, "telegramAutoPublishChannelId is empty — cannot publish")
            return TelegramAutoPublishResult(
                state = TelegramAutoPublishState.SEND_FAILED,
                error = "telegramAutoPublishChannelId is empty",
            )
        }

        // FR-005: подпись к видео (≤1024 символа). Шаблон выбирается по publicationType:
        // AIR — telegramTemplateAir (по расписанию), PREMIUM — telegramTemplatePremium
        // (при становлении песни доступной premium-подписчикам).
        val caption =
            TelegramTemplateService.render(
                TelegramTemplateService.templateFor(publicationType),
                song,
            )

        // PUBLISHING — фиксируем начало sendVideo (для UI «Публикуется»).
        song.telegramAutoPublishState = TelegramAutoPublishState.PUBLISHING.code
        song.telegramAutoPublishLastAttemptAt = nowIso8601()
        song.telegramAutoPublishLastError = ""
        song.saveToDb()

        val maxFileSizeBytes =
            KaraokeProperties.getLong("telegramAutoPublishMaxFileSizeMb").let { if (it <= 0) 50L else it } * 1024 * 1024
        val result = client.sendVideo(channelId, demoFile, caption, maxFileSizeBytes)

        if (result.state == TelegramAutoPublishState.PUBLISHED && result.messageId != null) {
            if (persistMessageId) {
                // FR-006 (AIR): запись message_id в idTelegramDemo через штатный saveToDb → SSE + sync.
                song.fields[SongField.ID_TELEGRAM_DEMO] = result.messageId
            } else {
                // PREMIUM: не сохраняем message_id — этот слот заполнится будущей AIR-публикацией.
                // Но отмечаем факт успешной премиум-отправки для идемпотентности и UI.
                song.newsPremiumTelegramSent = true
            }
            song.telegramAutoPublishState = TelegramAutoPublishState.PUBLISHED.code
            song.telegramAutoPublishLastError = ""
            song.saveToDb()
            return result
        }

        // SEND_FAILED — все ретраи FR-010 исчерпаны.
        writeFailure(song, result.error ?: "sendVideo failed (no error description)")
        return result
    }

    // Записывает состояние SEND_FAILED + lastError + lastAttemptAt через штатный saveToDb.
    private fun writeFailure(
        song: Song,
        error: String,
    ) {
        song.telegramAutoPublishState = TelegramAutoPublishState.SEND_FAILED.code
        song.telegramAutoPublishLastAttemptAt = nowIso8601()
        song.telegramAutoPublishLastError = error
        song.saveToDb()
    }

    private fun nowMoscow(): Date = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).time

    private fun nowIso8601(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").apply { timeZone = TimeZone.getTimeZone("Europe/Moscow") }.format(nowMoscow())
}
