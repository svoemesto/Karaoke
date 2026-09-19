package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.AudioCompareHistoryEntry
import com.svoemesto.karaokeapp.model.Message
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SongField
import com.svoemesto.karaokeapp.model.SongType
import com.svoemesto.karaokeapp.model.SseNotification
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.SNS
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.sql.Timestamp
import java.time.Instant
import kotlin.concurrent.thread

/**
 * Механизм «Синхронизация аудио-потомков» (OpenProject #141, specs/413-sync-audio-descendants).
 *
 * Песня-потомок (`audio_parent_id` указывает на родителя) — по сути копия родителя по
 * звучанию. Правки контента родителя (текст/маркеры/форматирование) должны отражаться в
 * потомках с учётом аудио-сдвига. Этот объект — точка входа: хук из [Song.saveToDb]
 * ставит родителя в очередь, фоновый воркер последовательно синхронизирует его потомков.
 *
 * **Гейт запуска** ([shouldEnqueue]): родитель в статусе >= 5; изменены content-поля
 * ИЛИ статус перешёл из `<5` в `>=5`; для типа «Песня» маркеры непусты; запись не
 * порождена самой синхронизацией ([inSync]).
 *
 * **Очередь**: дедупликация по id ([pendingParents] — LinkedHashSet), один воркер
 * (single-flight, [workerRunning]) — авто-синхронизация и массовая админ-функция
 * используют одну очередь и не могут идти одновременно.
 *
 * **Перенос** ([syncAudioDescendantFromParent]): набор как у [applyAudioParentMarkers]
 * (текст/маркеры со сдвигом/форматирование) + свежие аудио-метрики и history + статус 5
 * + перегенерация `.srt`; одним [Song.saveToDbLocked] (specs/299).
 *
 * @see specs/413-sync-audio-descendants/spec.md
 * @see specs/413-sync-audio-descendants/research.md
 * @see docs/features/sync-audio-descendants.md
 */
object SyncAudioDescendants {
    /** Content-поля `Song.getDiff`, изменение которых запускает синхронизацию. */
    val CONTENT_FIELDS: Set<String> =
        setOf(
            "source_text",
            "result_text",
            "source_markers",
            "formatted_text_song",
            "formatted_text_tabs",
            "formatted_text_chords",
        )

    /** Статус родителя, с которого разрешена синхронизация потомков. */
    const val PARENT_READY_STATUS = 5

    // --- Флаги/очередь -------------------------------------------------------------------

    /**
     * Подавляет хук сохранения в потоке, который сам пишет потомка (защита от каскада/циклов).
     * Ставится вокруг [syncAudioDescendantFromParent], снимается в `finally`.
     */
    private val inSync = ThreadLocal.withInitial { false }

    /**
     * Выполняет [block], подавляя триггеры синхронизации в текущем потоке. Нужно для
     * `saveToDbLocked()`: внутренний `saveToDb()` идёт до `commit()`, поэтому его хук
     * должен быть подавлен, а явный вызов [onSongSaved] — после фиксации транзакции.
     * Сохраняет/восстанавливает предыдущее значение (защита от вложенных вызовов).
     */
    fun <T> suppressTriggers(block: () -> T): T {
        val previous = inSync.get()
        inSync.set(true)
        try {
            return block()
        } finally {
            inSync.set(previous)
        }
    }

    /** Очередь родителей к синхронизации; дедупликация по id + порядок постановки. */
    private val pendingParents = LinkedHashSet<Long>()

    @Volatile
    private var workerRunning = false

    /** true, если текущий проход запущен админ-функцией и требует SSE-сводки. */
    @Volatile
    private var adminPending = false

    private val lock = Any()

    // --- Результат -----------------------------------------------------------------------

    /** Итог обработки одного родителя (для лога/admin-сводки). */
    data class SyncAudioResult(
        val parentId: Long,
        val processed: Int,
        val synced: Int,
        val skipped: Int,
        val reason: String,
    )

    // --- Гейт ----------------------------------------------------------------------------

    /**
     * Чистая функция гейта: нужно ли запускать синхронизацию для сохранённой песни.
     *
     * @param newStatus итоговый `idStatus` сохраняемой песни
     * @param oldStatus `idStatus` из БД до сохранения (0 для INSERT)
     * @param changedContentFields имена изменённых content-полей (подмножество [CONTENT_FIELDS])
     * @param songType тип песни
     * @param markersEmpty пусты ли маркеры песни
     * @param suppressTriggeredBySync true, если запись порождена самой синхронизацией
     * @return true, если родителя нужно поставить в очередь
     */
    fun shouldEnqueue(
        newStatus: Long,
        oldStatus: Long,
        changedContentFields: Set<String>,
        songType: SongType,
        markersEmpty: Boolean,
        suppressTriggeredBySync: Boolean,
    ): Boolean {
        if (suppressTriggeredBySync) return false
        if (newStatus < PARENT_READY_STATUS) return false
        val contentChanged = changedContentFields.any { it in CONTENT_FIELDS }
        val transitionIntoReady = oldStatus < PARENT_READY_STATUS && newStatus >= PARENT_READY_STATUS
        if (!contentChanged && !transitionIntoReady) return false
        if (songType == SongType.SONG && markersEmpty) return false
        return true
    }

    /**
     * Хук из [Song.saveToDb]: гейт + постановка родителя в очередь. Не блокирует сохранение.
     *
     * @param song сохраняемая песня (родитель)
     * @param previous состояние песни до сохранения (null для INSERT)
     * @param changedContentFields имена изменённых content-полей из diff
     */
    fun onSongSaved(
        song: Song,
        previous: Song?,
        changedContentFields: Set<String>,
    ) {
        val should =
            shouldEnqueue(
                newStatus = song.idStatus,
                oldStatus = previous?.idStatus ?: 0L,
                changedContentFields = changedContentFields,
                songType = song.songType,
                markersEmpty = song.sourceMarkersList.all { it.isEmpty() },
                suppressTriggeredBySync = inSync.get(),
            )
        if (!should) return
        enqueue(listOf(song.id))
    }

    // --- Очередь/воркер ------------------------------------------------------------------

    private fun enqueue(parentIds: Collection<Long>) {
        synchronized(lock) {
            pendingParents.addAll(parentIds)
        }
        startWorkerIfNeeded()
    }

    private fun startWorkerIfNeeded() {
        synchronized(lock) {
            if (workerRunning) return
            workerRunning = true
        }
        thread(name = "sync-audio-descendants") {
            try {
                drainQueue()
            } catch (e: Exception) {
                println("[sync-audio] worker error: ${e.message}")
            } finally {
                synchronized(lock) {
                    workerRunning = false
                    // Между проверкой в finally и снятием флага могла поступить новая задача
                    // (enqueue увидел workerRunning=true и не запустил воркер) — перезапускаем.
                    if (pendingParents.isNotEmpty()) {
                        startWorkerIfNeeded()
                    } else if (adminPending) {
                        notifyAdminSummary()
                        adminPending = false
                    }
                }
            }
        }
    }

    private fun drainQueue() {
        var processedParents = 0
        var processedChildren = 0
        var synced = 0
        var skipped = 0
        while (true) {
            val parentId =
                synchronized(lock) {
                    val it = pendingParents.iterator()
                    if (!it.hasNext()) return@synchronized null
                    val id = it.next()
                    it.remove()
                    id
                } ?: break
            val result =
                try {
                    syncOneParent(parentId)
                } catch (e: Exception) {
                    println("[sync-audio] parent=$parentId — error: ${e.message}")
                    SyncAudioResult(parentId, 0, 0, 1, "error:${e.message}")
                }
            processedParents++
            processedChildren += result.processed
            synced += result.synced
            skipped += result.skipped
        }
        adminProcessedParents = processedParents
        adminProcessedChildren = processedChildren
        adminSynced = synced
        adminSkipped = skipped
    }

    @Volatile private var adminProcessedParents = 0

    @Volatile private var adminProcessedChildren = 0

    @Volatile private var adminSynced = 0

    @Volatile private var adminSkipped = 0

    private fun notifyAdminSummary() {
        val summary =
            "Обработано родителей $adminProcessedParents, потомков $adminProcessedChildren: " +
                "синхронизировано $adminSynced, пропущено $adminSkipped"
        println("[sync-audio] admin — processed_parents=$adminProcessedParents processed_children=$adminProcessedChildren synced=$adminSynced skipped=$adminSkipped")
        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "Синхронизация аудио-потомков",
                    body = summary,
                ),
            ),
        )
    }

    // --- Синхронизация одного родителя ---------------------------------------------------

    /**
     * Синхронизирует всех прямых аудио-потомков (`audio_parent_id = parentId`, `id_status < 6`)
     * одного родителя. Последовательный, ошибки на песне не прерывают проход.
     */
    fun syncOneParent(parentId: Long): SyncAudioResult {
        val database = WORKING_DATABASE
        val parent =
            Song.loadFromDbById(
                id = parentId,
                database = database,
                storageService = KSS_APP,
                storageApiClient = SAC_APP,
            )
        if (parent == null) {
            println("[sync-audio] parent=$parentId — skipped: not_found")
            return SyncAudioResult(parentId, 0, 0, 1, "not_found")
        }
        if (parent.idStatus < PARENT_READY_STATUS) {
            println("[sync-audio] parent=$parentId — skipped: not_ready (id_status=${parent.idStatus})")
            return SyncAudioResult(parentId, 0, 0, 1, "not_ready")
        }
        val childIds = loadChildIds(parentId, database)
        var processed = 0
        var synced = 0
        var skipped = 0
        childIds.forEach { childId ->
            try {
                val child =
                    Song.loadFromDbById(
                        id = childId,
                        database = database,
                        storageService = KSS_APP,
                        storageApiClient = SAC_APP,
                    )
                if (child == null) {
                    skipped++
                    println("[sync-audio] parent=$parentId child=$childId — skipped: not_found")
                    return@forEach
                }
                processed++
                if (child.idStatus >= 6) {
                    skipped++
                    println("[sync-audio] parent=$parentId child=$childId — skipped: already_ready")
                    return@forEach
                }
                if (KaraokeProcess.hasActiveProcess(songId = child.id, database = database)) {
                    skipped++
                    println("[sync-audio] parent=$parentId child=$childId — skipped: active_process")
                    return@forEach
                }
                val cmp = WaveformCompare.compareWaveforms(child, parent)
                if (!cmp.ok) {
                    skipped++
                    println("[sync-audio] parent=$parentId child=$childId — skipped: no_audio")
                    return@forEach
                }
                if (cmp.similarityPercent < AUDIO_PARENT_THRESHOLD) {
                    skipped++
                    println(
                        "[sync-audio] parent=$parentId child=$childId — skipped: below_threshold:${cmp.similarityPercent}%",
                    )
                    return@forEach
                }
                syncAudioDescendantFromParent(child, parent, cmp.similarityPercent, cmp.deltaMs)
                synced++
                println(
                    "[sync-audio] parent=$parentId child=$childId — synced (${cmp.similarityPercent}%, delta=${cmp.deltaMs})",
                )
            } catch (e: Exception) {
                skipped++
                println("[sync-audio] parent=$parentId child=$childId — error: ${e.message}")
            }
        }
        return SyncAudioResult(parentId, processed, synced, skipped, "ok")
    }

    private fun loadChildIds(
        parentId: Long,
        database: KaraokeConnection,
    ): List<Long> {
        val result = mutableListOf<Long>()
        val connection = database.getConnection() ?: return result
        try {
            connection
                .prepareStatement(
                    "SELECT id FROM tbl_songs WHERE audio_parent_id = ? AND id_status < 6 ORDER BY id",
                ).use { ps ->
                    ps.setLong(1, parentId)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) result.add(rs.getLong("id"))
                    }
                }
        } catch (e: Exception) {
            println("[sync-audio] parent=$parentId — ошибка выборки потомков: ${e.message}")
        }
        return result
    }

    /**
     * Переносит контент родителя в потомка (со сдвигом маркеров), обновляет аудио-метрики
     * и history, выставляет статус 5, сохраняет одним `saveToDbLocked()` и перегенерирует `.srt`.
     */
    fun syncAudioDescendantFromParent(
        child: Song,
        parent: Song,
        similarityPercent: Int,
        deltaMs: Long,
    ) {
        inSync.set(true)
        try {
            child.sourceText = parent.sourceText
            child.resultText = parent.resultText
            child.sourceMarkers = shiftMarkersAndFixEnd(parent.sourceMarkers, deltaMs, child.ms)
            child.formattedTextSong = parent.formattedTextSong
            child.formattedTextTabs = parent.formattedTextTabs
            child.formattedTextChords = parent.formattedTextChords
            child.audioSimilarityPercent = similarityPercent
            child.audioDeltaMs = deltaMs
            child.audioCompareHistory = mergeCompareHistory(child, parent.id, similarityPercent, deltaMs)
            child.fields[SongField.ID_STATUS] = PARENT_READY_STATUS.toString()
            child.saveToDbLocked()
            writeSrtFiles(child)
        } finally {
            inSync.remove()
        }
    }

    private fun mergeCompareHistory(
        child: Song,
        parentId: Long,
        similarityPercent: Int,
        deltaMs: Long,
    ): String {
        val history =
            child.audioCompareHistoryList
                .filter { it.id != parentId }
                .toMutableList()
        history.add(
            AudioCompareHistoryEntry(
                id = parentId,
                similarityPercent = similarityPercent,
                deltaMs = deltaMs,
                ok = true,
                comparedAt = Instant.now().toString(),
            ),
        )
        return Json.encodeToString(ListSerializer(AudioCompareHistoryEntry.serializer()), history)
    }

    private fun writeSrtFiles(child: Song) {
        child.sourceMarkersList.indices.forEach { voice ->
            try {
                val srt = child.convertMarkersToSrt(voice)
                val pathToFile = "${child.rootFolder}/${child.fileName}.voice${voice + 1}.srt"
                File(pathToFile).writeText(srt)
                runCommand(listOf("chmod", "666", pathToFile))
            } catch (e: Exception) {
                println("[sync-audio] child=${child.id} — ошибка записи .srt (голос ${voice + 1}): ${e.message}")
            }
        }
    }

    // --- Массовая админ-функция ----------------------------------------------------------

    /**
     * Ставит в очередь всех уникальных родителей, у которых есть потомки со статусом <6.
     * Возвращает `"OK"` если запущено, `"ALREADY_RUNNING"` если проход уже идёт.
     *
     * @see specs/413-sync-audio-descendants/contracts/http-endpoint-syncaudioparents.md
     */
    fun syncAll(): String {
        synchronized(lock) {
            if (workerRunning && adminPending) return "ALREADY_RUNNING"
        }
        val parentIds = loadParentsWithPendingChildren()
        if (parentIds.isEmpty()) {
            println("[sync-audio] admin — нет потомков со статусом <6")
            return "OK"
        }
        synchronized(lock) {
            adminPending = true
            resetAdminCounters()
            pendingParents.addAll(parentIds)
        }
        startWorkerIfNeeded()
        println("[sync-audio] admin — поставлено родителей: ${parentIds.size}")
        return "OK"
    }

    private fun resetAdminCounters() {
        adminProcessedParents = 0
        adminProcessedChildren = 0
        adminSynced = 0
        adminSkipped = 0
    }

    private fun loadParentsWithPendingChildren(): List<Long> {
        val result = mutableListOf<Long>()
        val connection = WORKING_DATABASE.getConnection() ?: return result
        try {
            connection
                .prepareStatement(
                    "SELECT DISTINCT audio_parent_id FROM tbl_songs WHERE audio_parent_id <> 0 AND id_status < 6 ORDER BY audio_parent_id",
                ).use { ps ->
                    ps.executeQuery().use { rs ->
                        while (rs.next()) result.add(rs.getLong("audio_parent_id"))
                    }
                }
        } catch (e: Exception) {
            println("[sync-audio] admin — ошибка выборки родителей: ${e.message}")
        }
        return result
    }

    /** Диагностика: текущий размер очереди (для тестов/отладки). */
    fun pendingCount(): Int = synchronized(lock) { pendingParents.size }

    /**
     * Только добавляет родителей в очередь (без запуска воркера) — тестовый шов для проверки
     * дедупликации [pendingParents]. В продакшене очередь пополняется через [enqueue].
     */
    internal fun enqueueForTest(parentIds: Collection<Long>) {
        synchronized(lock) { pendingParents.addAll(parentIds) }
    }

    /** Только очищает очередь — тестовый шов. */
    internal fun clearForTest() {
        synchronized(lock) { pendingParents.clear() }
    }

    /** @return timestamp для логов (совместимость с остальным кодом). */
    @Suppress("unused")
    private fun now(): Timestamp = Timestamp.from(Instant.now())
}
