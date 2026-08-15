package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.Message
import com.svoemesto.karaokeapp.model.News
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SseNotification
import java.sql.SQLException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Автоматическое создание двух независимых видов новостей о песне (specs/101-song-news-flag,
 * заменяет прежний единый механизм specs/089-auto-news-song-release/specs/092-fix-auto-news-triggers):
 *
 * - **«Доступна»** (`category="premium"`) — [detectAndAnnounceAvailability], вызывается ТОЛЬКО из
 *   `karaoke-web` `MainController.doChangeRecords` в момент применения синхронизации, когда
 *   обнаруживается переход флага [Song.newsAvailableAnnounced] false→true.
 * - **«В эфире»** (`category="air"`) — [checkOnAirWindow], вызывается ТОЛЬКО из `karaoke-web`
 *   `SongReleaseAnnouncementScheduler` (плановая проверка на проде, ~раз в 5 минут) или создаётся
 *   вручную администратором. Синхронизация и апрув задания редактора больше НЕ создают эту новость
 *   напрямую (FR-006/FR-007 spec.md).
 *
 * Ни один из двух механизмов не использует отдельную вспомогательную таблицу учёта
 * (`tbl_song_news_announced` удалена) — идемпотентность обоих видов строится на существовании
 * новости в `tbl_news` ([News.existsAnnouncement], `category="premium"`/`category="air"`
 * соответственно); «в эфире» дополнительно ограничена узким скользящим окном проверки
 * (specs/152-fix-false-collection-news — до этой фичи «доступна» полагалась только на флаг
 * [Song.newsAvailableAnnounced] целевой БД, что допускало ложные срабатывания при отложенном
 * первом схождении флага между LOCAL и SERVER, см. research.md).
 *
 * @see archive/archive/docs/features/dual-db-sync.md
 * @see archive/docs/features/approve-pipeline.md
 */
object SongReleaseAnnouncementService {
    // Тот же порядок величины, что SongSyncTarget.rowChunkSize (25) — тяжёлые текстовые поля/маркеры
    // одной песни делают полную загрузку тысяч строк разом OOM-опасной (см.
    // specs/082-fix-import-folder-oom, тот же класс бага).
    private const val CHUNK_SIZE = 25

    // Запас в 2× периодичность плановой проверки (~5 минут) — компенсирует дрейф между тиками
    // @Scheduled(fixedDelay=...), который не гарантирует точного попадания в границы 5-минутных
    // интервалов (research.md п.4). Дубли внутри окна отсекаются существование-проверкой в
    // checkOnAirWindow, а не сужением окна.
    private const val ON_AIR_WINDOW_LOOKBACK_MINUTES = 10L

    /** Альбом и год песни в виде суффикса `" (альбом «X», Y)"` для заголовка новости — пустая строка, если ни альбом, ни год не заполнены. */
    private fun albumYearSuffix(song: Song): String {
        val parts = mutableListOf<String>()
        if (song.album.isNotBlank()) parts.add("альбом «${song.album}»")
        if (song.year > 0) parts.add(song.year.toString())
        return if (parts.isEmpty()) "" else " (" + parts.joinToString(", ") + ")"
    }

    /** Автор + альбом/год одной строкой для тела новости. */
    private fun bodyDetails(song: Song): String {
        val parts = mutableListOf(song.author)
        if (song.album.isNotBlank()) parts.add("альбом «${song.album}»")
        if (song.year > 0) parts.add(song.year.toString())
        return parts.joinToString(", ")
    }

    /**
     * Детекция перехода флага [Song.newsAvailableAnnounced] false→true при применении синхронизации
     * на сервере (FR-004 spec.md) — вызывается ПОСЛЕ того, как соответствующий `UPDATE`/`INSERT` в
     * `tbl_songs` уже применён. [wasAvailableBefore] — значение флага ДО применения этого конкретного
     * изменения на ЭТОЙ базе (читает вызывающий код заранее, см. `MainController.doChangeRecords`) —
     * используется только как дешёвый fast-path (пропустить загрузку полной песни, если локально
     * нечего проверять), а не как единственный источник истины: копия БД может впервые узнавать уже
     * давно истинное значение флага (например, после точечного backfill), что не является реальным
     * новым событием (specs/152-fix-false-collection-news, root cause в research.md). Поэтому
     * дополнительно проверяются два содержательных гейта против текущего состояния `tbl_news`:
     * - уже существует новость `category="premium"` по этой песне — не дублировать (идемпотентность
     *   не зависит от того, сколько раз/с какой стороны обнаруживается транзиция);
     * - уже существует новость `category="air"` («в эфире») по этой песне — «в эфире» логически
     *   подразумевает, что песня уже давно доступна, значит появление «в коллекции» после этого
     *   момента заведомо не про новое событие.
     * Ошибки логируются и не пробрасываются — сбой детекции не должен ронять уже применённую
     * синхронизацию.
     *
     * @see archive/docs/features/approve-pipeline.md
     * @return true, если новость была создана в этом вызове.
     */
    fun detectAndAnnounceAvailability(
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
        songId: Long,
        wasAvailableBefore: Boolean,
    ): Boolean {
        if (wasAvailableBefore) return false
        return try {
            val song =
                Song.loadFromDbById(songId, database = database, storageService = storageService, storageApiClient = storageApiClient)
                    ?: return false
            if (!song.newsAvailableAnnounced) return false
            val link = "/song?id=${song.id}"
            if (News.existsAnnouncement(songId = song.id, link = link, category = "premium", database = database)) return false
            if (News.existsAnnouncement(songId = song.id, link = link, category = "air", database = database)) return false
            News.createAutoAnnouncement(
                songId = song.id,
                title =
                    NewsTemplateService.render(
                        NewsTemplateService.template("newsTemplatePremiumTitle", database),
                        song,
                        news = null,
                        truncate = true,
                        database = database,
                    ),
                body =
                    NewsTemplateService.render(
                        NewsTemplateService.template("newsTemplatePremiumBody", database),
                        song,
                        news = null,
                        truncate = false,
                        database = database,
                    ),
                link = link,
                category = "premium",
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
            true
        } catch (e: Exception) {
            println("SongReleaseAnnouncementService.detectAndAnnounceAvailability error (songId=$songId): ${e.message}")
            false
        }
    }

    /**
     * Момент "сейчас" в московской таймзоне — та же точка отсчёта, что использует [Song.onAir], чтобы
     * окно проверки было согласовано с тем, что в итоге проверяет `isPubliclyWatchable` на загруженных
     * полных объектах.
     */
    private fun nowMoscow(): Date = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).time

    /** Тот же парсинг, что использует [Song.dateTimePublish] — `null`, если поля пустые/некорректные. */
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
                // даёт UTC-время, и сравнение с nowMoscow() (MSK) даёт ошибку в 3 часа.
                SimpleDateFormat("dd.MM.yy HH:mm")
                    .apply {
                        timeZone = TimeZone.getTimeZone("Europe/Moscow")
                    }.parse("$date $time")
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Дешёвый первый проход для механизма «в эфире» (research.md п.4): только `id` + текстовые
     * `publish_date`/`publish_time` для песен со статусом >= 6 (без текста/маркеров/base64) —
     * оставляет только те id, чей вычисленный момент эфира попал в скользящее окно
     * `(now - lookbackMinutes, now]`. Старые (давно вышедшие в эфир) песни физически никогда не
     * попадают в это окно — поэтому очистка `tbl_news` не создаёт по ним новостей задним числом.
     */
    private fun windowCandidateIds(
        database: KaraokeConnection,
        lookbackMinutes: Long,
    ): List<Long> {
        val connection = database.getConnection() ?: return emptyList()
        val result = mutableListOf<Long>()
        val now = nowMoscow()
        val windowStart = Date(now.time - lookbackMinutes * 60_000L)
        try {
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT id, publish_date, publish_time FROM tbl_songs WHERE id_status >= 6").use { rs ->
                    while (rs.next()) {
                        val date = rs.getString("publish_date") ?: ""
                        val time = rs.getString("publish_time") ?: ""
                        val publishAt = parseDateTimePublish(date, time) ?: continue
                        if (publishAt > windowStart && publishAt <= now) {
                            result.add(rs.getLong("id"))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            println("SongReleaseAnnouncementService.windowCandidateIds SQLException: ${e.message}")
        }
        return result
    }

    /**
     * Плановая проверка «песня вышла в эфир» (FR-006 spec.md) — вызывается ТОЛЬКО из
     * `SongReleaseAnnouncementScheduler` (~раз в 5 минут, PROD). Рассматривает только кандидатов из
     * [windowCandidateIds] (не весь каталог), загружает их полными объектами чанками, фильтрует по
     * [Song.isPubliclyWatchable], и для каждого — создаёт ровно одну новость категории `"air"`, если
     * такой (автоматической или созданной вручную администратором) для этой песни ещё нет
     * ([News.existsAutoAnnouncement]). Ошибки логируются и не пробрасываются.
     *
     * @return id песен, для которых в этом вызове была создана новость.
     */
    fun checkOnAirWindow(
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
        lookbackMinutes: Long = ON_AIR_WINDOW_LOOKBACK_MINUTES,
    ): List<Long> {
        val created = mutableListOf<Long>()
        try {
            val candidateIds = windowCandidateIds(database, lookbackMinutes)
            candidateIds.chunked(CHUNK_SIZE).forEach { chunk ->
                val loaded =
                    Song.loadListFromDb(
                        args = mapOf("ids" to chunk.joinToString(",")),
                        database = database,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                loaded.filter { it.isPubliclyWatchable }.forEach { song ->
                    val link = "/song?id=${song.id}"
                    if (!News.existsAnnouncement(songId = song.id, link = link, category = "air", database = database)) {
                        News.createAutoAnnouncement(
                            songId = song.id,
                            title =
                                NewsTemplateService.render(
                                    NewsTemplateService.template("newsTemplateAirTitle", database),
                                    song,
                                    news = null,
                                    truncate = true,
                                    database = database,
                                ),
                            body =
                                NewsTemplateService.render(
                                    NewsTemplateService.template("newsTemplateAirBody", database),
                                    song,
                                    news = null,
                                    truncate = false,
                                    database = database,
                                ),
                            link = link,
                            category = "air",
                            database = database,
                            storageService = storageService,
                            storageApiClient = storageApiClient,
                        )
                        created.add(song.id)
                    }
                }
            }
        } catch (e: Exception) {
            println("SongReleaseAnnouncementService.checkOnAirWindow error: ${e.message}")
        }
        return created
    }

    /**
     * Одноразовый backfill флага «доступна для новости» (FR-012 spec.md) — выставляет
     * [Song.newsAvailableAnnounced] в `true` через обычный `Song.saveToDb()` (НЕ raw SQL, см.
     * research.md п.3 — гарантирует консистентный `recordhash` без риска разойтись между LOCAL/PROD)
     * для песен, уже удовлетворяющих [Song.isContentReady], но у которых флаг ещё `false`. Не создаёт
     * никакой новости — единственная цель backfill'а именно в этом, иначе первая же обычная
     * синхронизация уже готовой песни создала бы новость «доступна» из ничего. Вызывается
     * администратором вручную, отдельно на `Connection.local()` и на `Connection.remote()` — до
     * очистки `tbl_news`/удаления `tbl_song_news_announced`.
     *
     * @return число песен, у которых флаг был выставлен в этом вызове.
     */
    fun backfillNewsAvailableFlag(
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
    ): Int {
        var count = 0
        try {
            val hashes = Song.listHashes(database = database, whereText = "WHERE id_status = 6") ?: emptyList()
            hashes.map { it.id }.chunked(CHUNK_SIZE).forEach { chunk ->
                val loaded =
                    Song.loadListFromDb(
                        args = mapOf("ids" to chunk.joinToString(",")),
                        database = database,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                loaded.filter { it.isContentReady && !it.newsAvailableAnnounced }.forEach { song ->
                    song.newsAvailableAnnounced = true
                    song.saveToDb()
                    count++
                }
            }
        } catch (e: Exception) {
            println("SongReleaseAnnouncementService.backfillNewsAvailableFlag error: ${e.message}")
        }
        return count
    }

    /**
     * Отчёт о backfill флагов публикации (specs/124-news-flags-backfill, FR-013 spec.md) — содержит
     * разбивку по категориям для анализа и итоговый тост SSE. [skippedNoMarkers] сейчас всегда 0,
     * потому что фильтр кандидатов идёт через `Song.isContentReady` (см.
     * [Song.isContentReady]/sourceMarkersList.isNotEmpty()) — оставлено на случай будущего
     * ослабления критерия ready.
     */
    data class BackfillReport(
        val totalCandidates: Int,
        val fixedNewsAvailableAnnounced: Int,
        val fixedPremiumComplete: Int,
        val alreadyOk: Int,
        val skippedActivePublishing: Int,
        val skippedNoMarkers: Int,
        val durationMs: Long,
        val dryRun: Boolean,
    ) {
        /**
         * Многострочное представление для `Message.body` финального SSE-тоста. Формат не строго JSON,
         * чтобы администратор мог прочитать результат глазами без клика на детали (UI-шаблон — по
         * образцу других backfill-тостов в `HomeView.vue`).
         */
        fun toBody(): String =
            listOf(
                "Всего кандидатов: $totalCandidates",
                "Выставлен newsAvailableAnnounced: $fixedNewsAvailableAnnounced",
                "Переведён в premiumAutoPublishState=COMPLETE: $fixedPremiumComplete",
                "Уже были в полном complete-состоянии: $alreadyOk",
                "Пропущено (активная публикация в TG/VK): $skippedActivePublishing",
                "Пропущено (нет маркеров): $skippedNoMarkers",
                "Длительность: $durationMs мс",
                "Режим dry-run: ${if (dryRun) "да (без записи)" else "нет (записано)"}",
            ).joinToString("\n")
    }

    /**
     * Одноразовый backfill ПОЛНОГО complete-набора флагов публикации (specs/124-news-flags-backfill,
     * FR-001…FR-005 spec.md) для уже готовых песен на LOCAL. В отличие от более узкого
     * [backfillNewsAvailableFlag], здесь кроме `newsAvailableAnnounced=true` явно выставляется:
     * `newsPremiumPublishPending=false`, `newsPremiumTelegramSent=true`, `newsPremiumVkSent=true`,
     * `premiumAutoPublishState="COMPLETE"`, `premiumAutoPublishLastError=""`, `premiumAttemptCount=0`.
     *
     * Зачем: до feature 122 некоторые готовые песни были опубликованы без флагов `premiumAutoPublish*`;
     * при развёртывании feature 122 каждый обычный `Song.saveToDb()` (например, при любом правке в
     * админке) триггерил бы в `markNewsAvailableIfReady` Block 2 переход `newsPremiumPublishPending
     * false→true` + state=RUNNING — а за ним `PremiumAutoPublishScheduler` запускал бы автопубликацию
     * в TG+VK для 15000 песен разом (лавина). Backfill заполняет флаги «уже завершёнными» явно, без
     * прохождения через state RUNNING. После backfill следующая sync LOCAL→PROD распространяет флаги
     * обычным recordhash-механизмом; на PROD-окно включается kill-switch (`newsAutoPublishKillSwitch`)
     * чтобы уже-применённые флаги не сработали в обратку как «новая песня появилась в коллекции» и
     * не создали лавину auto-новостей.
     *
     * Кандидаты фильтруются по `Song.isContentReady` (status ≥6 + стемы + картинки + маркеры).
     * Дополнительно пропускаются песни с активной автопубликацией в TG/VK (`telegramAutoPublishState`
     * или `vkAutoPublishState` в `[rendering, publishing]`) — для них менять флаги премиум-каналов
     * было бы гонкой с уже идущим рендером. Запись идёт через штатный `Song.saveToDb()` (НЕ raw SQL,
     * см. research.md п.3 — recordhash гарантированно консистентен с sync-движком; raw SQL бы
     * разошёлся по формуле хэша при неосторожной правке флагов).
     *
     * @return отчёт с разбивкой по категориям (всегда, в т.ч. при partial failure — см. catch).
     */
    fun backfillPublishFlags(
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
        dryRun: Boolean = false,
    ): BackfillReport {
        val startMs = System.currentTimeMillis()
        // Счётчики объявлены ДО try — чтобы catch мог собрать честный частичный отчёт, если
        // упали в середине (а не возвращал все нули).
        var totalCandidates = 0
        var fixedNewsAvailableAnnounced = 0
        var fixedPremiumComplete = 0
        var alreadyOk = 0
        var skippedActivePublishing = 0
        var skippedNoMarkers = 0
        var processedForProgress = 0
        return try {
            val hashes = Song.listHashes(database = database, whereText = "WHERE id_status = 6") ?: emptyList()
            totalCandidates = hashes.size
            hashes.map { it.id }.chunked(CHUNK_SIZE).forEach { chunk ->
                val loaded =
                    Song.loadListFromDb(
                        args = mapOf("ids" to chunk.joinToString(",")),
                        database = database,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                loaded.forEach { song ->
                    // Все фильтры — после загрузки, чтобы SQL-кэш отработал на одном дешёвом
                    // запросе (WHERE id_status=6); тяжёлые поля (стемы/картинки/маркеры) грузим
                    // один раз и фильтруем in-memory чанк-локально.
                    if (!song.isContentReady) {
                        // sourceMarkersList.isNotEmpty() внутри isContentReady; прочие редкие случаи
                        // (стемы не готовы, картинки не готовы) считаем «нет маркеров» — для отчёта
                        // это самая частая причина isContentReady=false на старых песнях.
                        skippedNoMarkers++
                        return@forEach
                    }
                    val tgBusy = song.telegramAutoPublishState in setOf("rendering", "publishing")
                    val vkBusy = song.vkAutoPublishState in setOf("rendering", "publishing")
                    if (tgBusy || vkBusy) {
                        skippedActivePublishing++
                        return@forEach
                    }
                    // Уже в полном complete-состоянии? Проверяем «идемпотентный» набор (полный
                    // complete). Если всё так — пропускаем, чтобы diff был действительно пустым при
                    // повторном запуске и не плодить RSS-шум; saveToDb с теми же значениями дал бы
                    // пустой diff, но мы экономим round-trip к БД.
                    val alreadyComplete =
                        song.premiumAutoPublishState == "COMPLETE" &&
                            song.newsAvailableAnnounced &&
                            song.newsPremiumTelegramSent &&
                            song.newsPremiumVkSent &&
                            !song.newsPremiumPublishPending &&
                            song.premiumAutoPublishLastError.isEmpty() &&
                            song.premiumAttemptCount == 0
                    if (alreadyComplete) {
                        alreadyOk++
                        processedForProgress++
                        reportProgressIf(processedForProgress, totalCandidates, dryRun)
                        return@forEach
                    }
                    // Фиксируем ДО записи — нужно для отчёта «сколько переходов было».
                    val willFlipNewsAvailable = !song.newsAvailableAnnounced
                    val willFixPremiumComplete = song.premiumAutoPublishState != "COMPLETE"
                    // Установка флагов через setter'ы (см. Song.newsAvailableAnnounced и др.) — они
                    // идемпотентно модифицируют JSON-блоб playerReadiness_flags. Никаких изменений
                    // других полей/файлов saveToDb не делает, если других различий в diff нет.
                    song.newsAvailableAnnounced = true
                    song.newsPremiumPublishPending = false
                    song.newsPremiumTelegramSent = true
                    song.newsPremiumVkSent = true
                    song.premiumAutoPublishState = "COMPLETE"
                    song.premiumAutoPublishLastError = ""
                    song.premiumAttemptCount = 0
                    if (!dryRun) {
                        song.saveToDb()
                    }
                    if (willFlipNewsAvailable) fixedNewsAvailableAnnounced++
                    if (willFixPremiumComplete) fixedPremiumComplete++
                    processedForProgress++
                    reportProgressIf(processedForProgress, totalCandidates, dryRun)
                }
            }
            BackfillReport(
                totalCandidates = totalCandidates,
                fixedNewsAvailableAnnounced = fixedNewsAvailableAnnounced,
                fixedPremiumComplete = fixedPremiumComplete,
                alreadyOk = alreadyOk,
                skippedActivePublishing = skippedActivePublishing,
                skippedNoMarkers = skippedNoMarkers,
                durationMs = System.currentTimeMillis() - startMs,
                dryRun = dryRun,
            ).also {
                println(
                    "SongReleaseAnnouncementService.backfillPublishFlags (${database.name}, " +
                        "dryRun=$dryRun): ${it.toBody().replace("\n", " | ")}",
                )
            }
        } catch (e: Exception) {
            println("SongReleaseAnnouncementService.backfillPublishFlags error: ${e.message}")
            BackfillReport(
                totalCandidates = totalCandidates,
                fixedNewsAvailableAnnounced = fixedNewsAvailableAnnounced,
                fixedPremiumComplete = fixedPremiumComplete,
                alreadyOk = alreadyOk,
                skippedActivePublishing = skippedActivePublishing,
                skippedNoMarkers = skippedNoMarkers,
                durationMs = System.currentTimeMillis() - startMs,
                dryRun = dryRun,
            )
        }
    }

    /**
     * Прогресс-тост для backfill — слать примерно раз в 500 обработанных песен (не в каждом
     * чанке), иначе поток тостов забивает очередь SSE-уведомлений администратора. Для dry-run
     * прогресс НЕ слать (операция мгновенная, итог всё равно придёт финальным тостом). Точка
     * 100% пропускается — финальный «завершено»-тост с отчётом придёт отдельным уведомлением от
     * endpoint'а в [ApiController].
     */
    private fun reportProgressIf(
        processed: Int,
        total: Int,
        dryRun: Boolean,
    ) {
        if (dryRun) return
        if (processed % 500 != 0 || processed == total) return
        val head = "Backfill флагов публикации"
        val body = "Обработано $processed / $total"
        try {
            com.svoemesto.karaokeapp.services.SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = head,
                        body = body,
                    ),
                ),
            )
        } catch (_: Exception) {
            // SSE — best-effort, не валим backfill из-за падающего уведомления
        }
    }
}
