package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.SNS
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeapp.tuple.TupleSchedulerSql
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.Serializable
import java.nio.file.Files
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

/**
 * Задание в async-очереди (см. `async-process-queue.md`).
 *
 * Жизненный цикл:
 * 1. `WAITING` — создано в БД (`tbl_processes`).
 * 2. `WORKING` — `KaraokeProcessWorker` подхватил, запустил subprocess.
 * 3. `DONE` / `ERROR` / `WAITING` (если force-stop) — терминальное.
 *
 * Содержит:
 * - `threadId`, `name`, `type`, `description` — UI-метаданные.
 * - `args` — параметры для subprocess или Kotlin-функции
 *   (для `runFunctionWithArgs` — `[(funcName, [args])]`).
 * - `percentage` — прогресс (парсится из stdout).
 * - `start`, `finish` — таймстампы.
 * - `errorMessage` — если `ERROR`.
 * - `processChainId` — id родительского процесса (для цепочек).
 * - `isKillPreviousChainTasksOnStart` — отменить предыдущие в цепочке.
 *
 * Все статусы — в `KaraokeProcessStatuses` enum.
 *
 * @see archive/docs/features/async-process-queue.md
 */
class KaraokeProcess(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable,
    Comparable<KaraokeProcess>,
    KaraokeDbTable {
    override fun getTableName() = "tbl_processes"

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "process_name")
    var name: String = "Process name"

    @KaraokeDbTableField(name = "process_status")
    var status: String = KaraokeProcessStatuses.CREATING.name

    @KaraokeDbTableField(name = "process_order")
    var order: Int = -1

    @KaraokeDbTableField(name = "process_priority")
    var priority: Int = 1

    @KaraokeDbTableField(name = "process_command")
    var command: String = ""

    @KaraokeDbTableField(name = "process_args")
    var args: List<List<String>> = mutableListOf(mutableListOf())

    @KaraokeDbTableField(name = "process_envs")
    var envs: Map<String, String> = mutableMapOf()

    var argsDescription: List<String> = mutableListOf()

    @KaraokeDbTableField(name = "process_description")
    var description: String = "description"

    @KaraokeDbTableField(name = "song_id")
    var songId: Int = 0

    @KaraokeDbTableField(name = "process_type")
    var type: String = KaraokeProcessTypes.NONE.name

    @KaraokeDbTableField(name = "process_start")
    var start: Timestamp? = null

    @KaraokeDbTableField(name = "process_end")
    var end: Timestamp? = null

    @KaraokeDbTableField(name = "process_prioritet")
    var prioritet: Int = 0

    @KaraokeDbTableField(name = "without_control")
    var withoutControl: Boolean = false

    @KaraokeDbTableField(name = "thread_id")
    var threadId: Int = 0

    // specs/315-admin-ui-karaoke-process-v5 (T005): поля для parent-child визуализации и
    // soft-delete. Декларации допустимы (для load/DTO), но save() их НЕ пишет (FR-019 race
    // protection, Lesson #5 iter #1) — см. save() ниже.
    @KaraokeDbTableField(name = "process_chain_id")
    var processChainId: Long? = null

    @KaraokeDbTableField(name = "process_deleted_at")
    var processDeletedAt: Timestamp? = null

    /** Владелец временных ресурсов кортежа: `process_chain_id ?: id` (FR-004). */
    fun scopeId(): Long = processChainId ?: id

    /** argv шага с развёрнутым `{scope}` (FR-004). */
    fun expandedArgs(): List<String> =
        args.firstOrNull()?.let { com.svoemesto.karaokeapp.tuple.TupleScope.expand(it, scopeId()) } ?: emptyList()

    /** Помечает одиночную строку головой кортежа (`chain_id = id`, `order = 0`, FR-006). */
    fun markAsHead(): Int {
        val connection = database.getConnection() ?: return 0
        return try {
            connection
                .prepareStatement("UPDATE tbl_processes SET process_chain_id = id, process_order = 0 WHERE id = ?")
                .use { ps ->
                    ps.setLong(1, id)
                    ps.executeUpdate()
                }
        } catch (e: Exception) {
            println("[${Timestamp.from(Instant.now())}] WARN process.head_mark_failed: processId=$id: ${e.message}")
            0
        }
    }

    override fun toDTO(): KaraokeProcessDTO =
        KaraokeProcessDTO(
            id = id,
            name = name,
            status = status,
            order = order,
            priority = priority,
            command = command,
            args = args,
            envs = envs,
            argsDescription = argsDescription,
            description = description,
            songId = songId,
            type = type,
            start = start,
            end = end,
            prioritet = prioritet,
            startStr = startStr,
            endStr = endStr,
            percentage = percentage,
            percentageStr = percentageStr,
            timePassedMs = timePassedMs,
            timePassedStr = timePassedStr,
            timeLeftMs = timeLeftMs,
            timeLeftStr = timeLeftStr,
            withoutControl = withoutControl,
            threadId = threadId,
            processDeletedAt = processDeletedAt,
        )

    fun copy(): KaraokeProcess {
        val result = KaraokeProcess(database)
        result.id = id
        result.name = name
        result.status = status
        result.order = order
        result.priority = priority
        result.command = command
        result.args = args
        result.envs = envs
        result.argsDescription = argsDescription
        result.description = description
        result.songId = songId
        result.type = type
        result.start = start
        result.end = end
        result.prioritet = prioritet
        result.withoutControl = withoutControl
        result.threadId = threadId
        return result
    }

    val argsJson: String get() {
        return Json.encodeToString(args)
    }

    val envsJson: String get() {
        return Json.encodeToString(envs)
    }

    val startStr: String get() {
        return if (start != null) {
            val dateFormat = SimpleDateFormat("yy-MM-dd HH:mm:ss")
            val formattedDate = dateFormat.format(Date(start!!.time))
            formattedDate
        } else {
            ""
        }
    }

    val endStr: String get() {
        return if (end != null) {
            val dateFormat = SimpleDateFormat("yy-MM-dd HH:mm:ss")
            val formattedDate = dateFormat.format(Date(end!!.time))
            formattedDate
        } else {
            ""
        }
    }

    val percentage: Double get() {
        return when (status) {
            KaraokeProcessStatuses.DONE.name -> {
                100.0
            }
            KaraokeProcessStatuses.WORKING.name -> {
                val percentString = KaraokeProcessWorker.getPercentage(this)
                if (percentString == "---") {
                    0.0
                } else {
                    percentString.toDouble()
                }
            }
            else -> {
                0.0
            }
        }
    }

    val percentageStr: String get() {
        val perc = percentage
        return if (perc == 0.0) {
            ""
        } else {
            "$perc %"
        }
    }

    val timePassedMs: Long get() {
        if (start == null) return -1L
        return when (status) {
            KaraokeProcessStatuses.DONE.name -> {
                if (end == null) return -1L
                val diffMs = end!!.time - start!!.time
                diffMs
            }
            KaraokeProcessStatuses.WORKING.name -> {
                val currTime = Timestamp.from(Instant.now())
                val diffMs = currTime.time - start!!.time
                diffMs
            }
            else -> {
                -1L
            }
        }
    }

    val timePassedStr: String get() {
        val t = timePassedMs
        return if (t == -1L) {
            ""
        } else {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(t)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(t) - TimeUnit.MINUTES.toSeconds(minutes)
            String.format("%d:%02d", minutes, seconds)
        }
    }

    val timeLeftMs: Long get() {
        val perc = percentage
        if (start == null || perc == 0.0) return -1L
        return when (status) {
            KaraokeProcessStatuses.WORKING.name -> {
                val passed = timePassedMs
                val fullMs = (passed * 100.0 / perc).toLong()
                val tail = fullMs - passed
                tail
            }
            else -> {
                -1L
            }
        }
    }

    val timeLeftStr: String get() {
        val t = timeLeftMs
        return if (t == -1L) {
            ""
        } else {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(t)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(t) - TimeUnit.MINUTES.toSeconds(minutes)
            String.format("%d:%02d", minutes, seconds)
        }
    }

    override fun compareTo(other: KaraokeProcess): Int {
        var result = priority.compareTo(other.priority)
        if (result != 0) return result
        result = order.compareTo(other.order)
        if (result != 0) return result
        return id.compareTo(other.id)
    }

    override fun save() {
        val connection = database.getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
            return
        }
        val sql =
            "UPDATE tbl_processes SET " +
                "process_name = ?, " +
                "process_status = ?, " +
                "process_order = ?, " +
                "process_priority = ?, " +
                "process_command = ?, " +
                "process_args = ?, " +
                "process_envs = ?, " +
                "process_description = ?, " +
                "song_id = ?, " +
                "process_type = ?, " +
                "process_start = ?, " +
                "process_end = ?, " +
                "process_prioritet = ?, " +
                "process_start_str = ?, " +
                "process_end_str = ?, " +
                "process_percentage = ?, " +
                "process_percentage_str = ?, " +
                "process_time_passed_ms = ?, " +
                "process_time_passed_str = ?, " +
                "process_time_left_ms = ?, " +
                "process_time_left_str = ?, " +
                "without_control = ?, " +
                "thread_id = ? " +
                "WHERE id = ?"
        val ps = connection.prepareStatement(sql)
        var index = 1
        ps.setString(index, name)
        index++
        ps.setString(index, status)
        index++
        ps.setInt(index, order)
        index++
        ps.setInt(index, priority)
        index++
        ps.setString(index, command)
        index++
        ps.setString(index, argsJson)
        index++
        ps.setString(index, envsJson)
        index++
        ps.setString(index, description)
        index++
        ps.setInt(index, songId)
        index++
        ps.setString(index, type)
        index++
        if (start != null) ps.setTimestamp(index, start!!) else ps.setNull(index, 0)
        index++
        if (end != null) ps.setTimestamp(index, end!!) else ps.setNull(index, 0)
        index++
        ps.setInt(index, prioritet)
        index++
        ps.setString(index, startStr)
        index++
        ps.setString(index, endStr)
        index++
        ps.setDouble(index, percentage)
        index++
        ps.setString(index, percentageStr)
        index++
        ps.setLong(index, timePassedMs)
        index++
        ps.setString(index, timePassedStr)
        index++
        ps.setLong(index, timeLeftMs)
        index++
        ps.setString(index, timeLeftStr)
        index++
        ps.setBoolean(index, withoutControl)
        index++
        ps.setInt(index, threadId)
        index++
        ps.setLong(index, id)
        ps.executeUpdate()
        ps.close()

        if (!withoutControl) {
            // BR-032: сбой шага останавливает цепочку — оставшиеся WAITING/CREATING-шаги
            // помечаются ERROR, иначе воркер возьмёт шаг N+1 после ERROR шага N.
            if (status == KaraokeProcessStatuses.ERROR.name && processChainId != null) {
                stopChainSiblingsOnError()
            }
            // FR-001/FR-016 (674): приоритет — свойство кортежа. Терминальное значение ставится
            // всему кортежу только когда он завершён, а не на каждом промежуточном шаге.
            val terminalNow =
                status == KaraokeProcessStatuses.DONE.name || status == KaraokeProcessStatuses.ERROR.name
            if (terminalNow && processChainId != null && !hasPendingChainSiblings()) {
                setChainPriority(
                    chainId = processChainId!!,
                    priority = if (status == KaraokeProcessStatuses.ERROR.name) -1 else 999,
                    database = database,
                )
            }
            // BR-030/BR-032 (аудит #336, пакет PKG-S2-10): терминальный статус шага цепочки
            // больше не агрегируется в песню, пока цепочка не завершена. Раньше DONE первого
            // шага сразу выставлял песне DONE, хотя 720p/симлинки ещё не сделаны.
            val isTerminal = status == KaraokeProcessStatuses.DONE.name || status == KaraokeProcessStatuses.ERROR.name
            val chainComplete = processChainId == null || !hasPendingChainSiblings()
            if (!isTerminal || chainComplete) {
                updateStatusProcessSong(database = database, storageService = storageService, storageApiClient = storageApiClient)
            }
            val firstArg = args.firstOrNull()?.firstOrNull()
            if (command != "tail" || firstArg !in KaraokeProcessWorker.argsIgnoredToLog) {
                val messageRecordChange =
                    SseNotification.recordChange(
                        RecordChangeMessage(
                            tableName = "tbl_processes",
                            recordId = id,
                            diffs = emptyList(),
                            databaseName = database.name,
                            record = this.toDTO(),
                        ),
                    )
                SNS.send(messageRecordChange)
            }
        }

//        if (status == KaraokeProcessStatuses.DONE.name) {
//            println("[${Timestamp.from(Instant.now())}] KaraokeProcess: Удаляем успешно завершенное задание: $name - [$type] - $description")
//            delete(id, database)
//        }

//        val controller = ApplicationContextProvider.getCurrentApplicationContext().getBean(MainController::class.java)
//        controller.processesUpdate(id.toLong())
    }

    /**
     * BR-030 (аудит #336, пакет PKG-S2-10): узкая запись прогресса.
     *
     * Пишет ТОЛЬКО percentage-колонки и только пока строка в `WORKING`. Терминальные поля
     * (`process_status`, `process_end`, `process_priority`) принадлежат потоку задания;
     * раньше цикл `doStart` каждые ~10 мс писал их полным `save()` и возвращал строку в
     * `WORKING` после `DONE`.
     *
     * @return число обновлённых строк (0 = строка уже не WORKING — писать нечего).
     */
    fun saveProgress(): Int {
        val connection =
            database.getConnection() ?: run {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                return 0
            }
        val sql =
            "UPDATE tbl_processes SET " +
                "process_percentage = ?, " +
                "process_percentage_str = ?, " +
                "process_time_passed_ms = ?, " +
                "process_time_passed_str = ?, " +
                "process_time_left_ms = ?, " +
                "process_time_left_str = ? " +
                "WHERE id = ? AND process_status = ?"
        return try {
            val updated =
                connection.prepareStatement(sql).use { ps ->
                    ps.setDouble(1, percentage)
                    ps.setString(2, percentageStr)
                    ps.setLong(3, timePassedMs)
                    ps.setString(4, timePassedStr)
                    ps.setLong(5, timeLeftMs)
                    ps.setString(6, timeLeftStr)
                    ps.setLong(7, id)
                    ps.setString(8, KaraokeProcessStatuses.WORKING.name)
                    ps.executeUpdate()
                }
            if (updated > 0) {
                // Прогресс должен доходить до UI так же, как раньше через save().
                try {
                    SNS.send(
                        SseNotification.recordChange(
                            RecordChangeMessage(
                                tableName = "tbl_processes",
                                recordId = id,
                                diffs = emptyList(),
                                databaseName = database.name,
                                record = this.toDTO(),
                            ),
                        ),
                    )
                } catch (e: Exception) {
                    println("[${Timestamp.from(Instant.now())}] WARN process.progress_sse_failed: processId=$id: ${e.message}")
                }
            }
            updated
        } catch (e: Exception) {
            println("[${Timestamp.from(Instant.now())}] WARN process.progress_save_failed: processId=$id: ${e.message}")
            0
        }
    }

    /**
     * BR-031 (аудит #336, пакет PKG-S2-10): атомарный claim задания воркером.
     *
     * `UPDATE ... SET process_status='WORKING' WHERE id=? AND process_status='WAITING'`
     * с проверкой числа строк. Два воркера/повторный запуск не могут взять одну строку;
     * если строка уже не WAITING (удалена, взята другим, отменена) — `false`, поток не стартует.
     *
     * @return true, если строка перешла в WORKING именно этим вызовом.
     */
    fun claimForWorking(): Boolean {
        val connection = database.getConnection() ?: return false
        val sql =
            "UPDATE tbl_processes SET process_status = ?, process_start = ? " +
                "WHERE id = ? AND process_status = ?"
        return try {
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, KaraokeProcessStatuses.WORKING.name)
                ps.setTimestamp(2, Timestamp.from(Instant.now()))
                ps.setLong(3, id)
                ps.setString(4, KaraokeProcessStatuses.WAITING.name)
                val updated = ps.executeUpdate()
                if (updated == 1) {
                    status = KaraokeProcessStatuses.WORKING.name
                    start = Timestamp.from(Instant.now())
                }
                updated == 1
            }
        } catch (e: Exception) {
            println("[${Timestamp.from(Instant.now())}] WARN process.claim_failed: processId=$id: ${e.message}")
            false
        }
    }

    /**
     * BR-032: есть ли в цепочке незавершённые шаги (кроме текущего).
     */
    fun hasPendingChainSiblings(): Boolean {
        val chainId = processChainId ?: return false
        val connection = database.getConnection() ?: return false
        val sql =
            "SELECT 1 FROM tbl_processes WHERE process_chain_id = ? AND id <> ? " +
                "AND process_status IN (?, ?, ?) AND process_deleted_at IS NULL LIMIT 1"
        return try {
            connection.prepareStatement(sql).use { ps ->
                ps.setLong(1, chainId)
                ps.setLong(2, id)
                ps.setString(3, KaraokeProcessStatuses.WAITING.name)
                ps.setString(4, KaraokeProcessStatuses.WORKING.name)
                ps.setString(5, KaraokeProcessStatuses.CREATING.name)
                ps.executeQuery().use { rs -> rs.next() }
            }
        } catch (e: Exception) {
            // Fail-closed: не смогли проверить — считаем, что цепочка не завершена,
            // и НЕ выставляем песне терминальный статус.
            println("[${Timestamp.from(Instant.now())}] WARN process.chain_check_failed: processId=$id: ${e.message}")
            true
        }
    }

    /**
     * BR-032: при ERROR шага перевести оставшиеся шаги цепочки в ERROR, чтобы воркер
     * не запускал шаг N+1. Терминальные поля работающих шагов не трогаем.
     *
     * @return число остановленных шагов.
     */
    fun stopChainSiblingsOnError(): Int {
        val chainId = processChainId ?: return 0
        val connection = database.getConnection() ?: return 0
        val sql =
            "UPDATE tbl_processes SET process_status = ?, process_end = ?, " +
                "process_description = COALESCE(process_description, '') || ? " +
                "WHERE process_chain_id = ? AND id <> ? AND process_status IN (?, ?) " +
                "AND process_deleted_at IS NULL"
        return try {
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, KaraokeProcessStatuses.ERROR.name)
                ps.setTimestamp(2, Timestamp.from(Instant.now()))
                ps.setString(3, " [цепочка остановлена: ошибка шага $id]")
                ps.setLong(4, chainId)
                ps.setLong(5, id)
                ps.setString(6, KaraokeProcessStatuses.WAITING.name)
                ps.setString(7, KaraokeProcessStatuses.CREATING.name)
                ps.executeUpdate()
            }
        } catch (e: Exception) {
            println("[${Timestamp.from(Instant.now())}] WARN process.chain_stop_failed: processId=$id: ${e.message}")
            0
        }
    }

    /**
     * BR-032: сохранить `process_chain_id` узким UPDATE (колонка не входит в `save()`,
     * чтобы не перезаписывать её из устаревшего объекта — FR-019).
     */
    fun saveChainId(): Int {
        val chainId = processChainId ?: return 0
        val connection = database.getConnection() ?: return 0
        val sql = "UPDATE tbl_processes SET process_chain_id = ? WHERE id = ?"
        return try {
            connection.prepareStatement(sql).use { ps ->
                ps.setLong(1, chainId)
                ps.setLong(2, id)
                ps.executeUpdate()
            }
        } catch (e: Exception) {
            println("[${Timestamp.from(Instant.now())}] WARN process.chain_id_save_failed: processId=$id: ${e.message}")
            0
        }
    }

    fun updateStatusProcessSong(
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
    ) {
        if (songId != 0) {
            val song =
                Song.loadFromDbById(
                    id = songId.toLong(),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                )
            song?.let {
                when (type) {
                    KaraokeProcessTypes.MELT_LYRICS.name -> {
                        if (song.statusProcessLyrics != status) {
                            song.statusProcessLyrics = status
                            // specs/299: race с SongEdit / KEY_BPM_FROM_FILE — saveToDbLocked обеспечивает атомарность.
                            song.saveToDbLocked()
                        }
                    }

                    KaraokeProcessTypes.MELT_KARAOKE.name -> {
                        if (song.statusProcessKaraoke != status) {
                            song.statusProcessKaraoke = status
                            // specs/299: см. комментарий выше.
                            song.saveToDbLocked()
                        }
                    }

                    KaraokeProcessTypes.MELT_CHORDS.name -> {
                        if (song.statusProcessChords != status) {
                            song.statusProcessChords = status
                            // specs/299: см. комментарий выше.
                            song.saveToDbLocked()
                        }
                    }

                    KaraokeProcessTypes.MELT_TABS.name -> {
                        if (song.statusProcessMelody != status) {
                            song.statusProcessMelody = status
                            // specs/299: см. комментарий выше.
                            song.saveToDbLocked()
                        }
                    }

                    KaraokeProcessTypes.RENDER_MP4_DEMO.name -> {
                        if (song.statusProcessDemo != status) {
                            song.statusProcessDemo = status
                            // specs/299: см. комментарий выше.
                            song.saveToDbLocked()
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(KaraokeProcess::class.java)

        /**
         * Маркер хвоста «причина ошибки» в `process_description` (BR-036, пакет PKG-S3-22).
         * Отдельной колонки под причину в схеме нет; описание видит оператор в админке,
         * поэтому причина дописывается туда (идемпотентно — см. [appendErrorReason]).
         */
        internal const val ERROR_REASON_MARKER = " | ERROR: "

        /** Ограничение длины описания с причиной, символов. */
        internal const val ERROR_REASON_MAX_LENGTH = 500

        // Именованные "лейны" threadId для задач, не привязанных к конкретной песне/сессии редактирования
        const val THREAD_LANE_HEAVY_RENDER = 0 // MELT_*, DEMUCS*, SHEETSAGE и т.п. — тяжёлые задачи, нельзя гнать параллельно
        const val THREAD_LANE_LIGHT_BACKGROUND = -1 // SmartCopy, uploadToLocalStore — быстрые фоновые задачи
        const val THREAD_LANE_REMOTE_STORE_UPLOAD = -2 // uploadToRemoteStore — сетевая загрузка может быть медленной, свой отдельный лейн
        const val THREAD_LANE_HEALTH_REPORT = 1 // задачи автоисправления HealthReport (кроме MELT_* — те на THREAD_LANE_HEAVY_RENDER)
        const val THREAD_LANE_STEM_JOBS = 2 // премиум-фича «Создать минусовку» (StemJob) — свой лейн, чтобы не блокировать и не блокироваться обычным пайплайном выпуска песен

        fun getDiff(procA: KaraokeProcess?): List<RecordDiff> {
            val result: MutableList<RecordDiff> = mutableListOf()
            if (procA != null) {
                result.add(RecordDiff("process_name", procA.name, ""))
                result.add(RecordDiff("process_type", procA.type, ""))
                result.add(RecordDiff("process_status", procA.status, ""))
                result.add(RecordDiff("process_order", procA.order, ""))
                result.add(RecordDiff("process_priority", procA.priority, ""))
                result.add(RecordDiff("process_start", procA.start, ""))
                result.add(RecordDiff("process_end", procA.end, ""))

                result.add(RecordDiff("process_start_str", procA.startStr, ""))
                result.add(RecordDiff("process_end_str", procA.endStr, ""))
                result.add(RecordDiff("process_percentage_str", procA.percentageStr, ""))
                result.add(RecordDiff("process_percentage", procA.percentage, ""))
                result.add(RecordDiff("process_time_passed_str", procA.timePassedStr, ""))
                result.add(RecordDiff("process_time_left_str", procA.timeLeftStr, ""))
                result.add(RecordDiff("process_time_passed_ms", procA.timePassedMs, ""))
                result.add(RecordDiff("process_time_left_ms", procA.timeLeftMs, ""))
                result.add(RecordDiff("without_control", procA.withoutControl, ""))
                result.add(RecordDiff("thread_id", procA.threadId, ""))
            }
            return result
        }

        /**
         * Считает число `WAITING`-заданий (кроме `tail`-продолжений shell-пайплайна).
         *
         * @param throwOnError specs/088-fix-queue-swallowed-errors: по умолчанию `false` —
         *   сбой БД тихо логируется, возвращается `0` (сегодняшнее поведение для всех
         *   вызывающих мест, кроме главного цикла очереди). При `true` (используется только
         *   изнутри `KaraokeProcessWorker.doStart()`) сбой пробрасывается как [SQLException] —
         *   тот же тип, что и `.save()`, чтобы retry-механизм `KaraokeProcessWorker.start()`
         *   (specs/087-fix-shared-db-connection) одинаково реагировал на любой сбой БД внутри
         *   главного цикла, а не только на сбой сохранения прогресса.
         * @see archive/docs/features/async-process-queue.md
         */
        fun getCountWaiting(
            database: KaraokeConnection,
            throwOnError: Boolean = false,
        ): Long {
            val connection = database.getConnection()
            if (connection == null) {
                val message = "Невозможно установить соединение с базой данных ${database.name}"
                println("[${Timestamp.from(Instant.now())}] $message")
                if (throwOnError) throw SQLException(message)
                return 0L
            }
            var statement: Statement? = null
            var rs: ResultSet? = null
            // 674 (FR-015): «в очереди» — число живых кортежей, а не строк-шагов.
            val sql =
                "select count(distinct coalesce(process_chain_id, id)) as cnt from tbl_processes " +
                    "where process_status = 'WAITING' and process_deleted_at IS NULL"
            var result = 0L

            try {
                statement = connection.createStatement()
                rs = statement.executeQuery(sql)
                rs.next()
                result = rs.getLong("cnt")
            } catch (e: SQLException) {
                if (throwOnError) throw e
                e.printStackTrace()
            } finally {
                try {
                    rs?.close() // close result set
                    statement?.close() // close statement
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return result
        }

        fun getLastUpdated(
            lastTime: Long? = null,
            database: KaraokeConnection,
        ): List<Int> {
            if (lastTime == null) return emptyList()

            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                return emptyList()
            }
            var statement: Statement? = null
            var rs: ResultSet? = null
            val sql: String

            val result: MutableList<Int> = mutableListOf()

            try {
                statement = connection.createStatement()
                sql = "SELECT id FROM tbl_processes WHERE last_update > '${Timestamp(lastTime)}'::timestamp"
                rs = statement.executeQuery(sql)
                while (rs.next()) {
                    result.add(rs.getInt("id"))
                }
                return result
            } catch (e: SQLException) {
                e.printStackTrace()
            } finally {
                try {
                    rs?.close() // close result set
                    statement?.close() // close statement
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return emptyList()
        }

        fun convertJsonToArgs(json: String): List<List<String>> =
            try {
                Json.decodeFromString(ListSerializer(ListSerializer(String.serializer())), json)
            } catch (_: Exception) {
                emptyList()
            }

        fun convertJsonToEnvs(json: String): Map<String, String> =
            try {
                Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), json)
            } catch (_: Exception) {
                emptyMap()
            }

        fun createDbInstance(processes: List<KaraokeProcess>): List<KaraokeProcess?> {
            val result: MutableList<KaraokeProcess?> = mutableListOf()
            processes.forEach { process ->
                result.add(createDbInstance(process))
            }
            return result
        }

        /**
         * Удаляет терминальные (`DONE`) задания при старте воркера.
         *
         * BR-036 (аудит #336, пакет PKG-S3-22): раньше исключение глушилось
         * (`println(e.message)` — без стека и без признака «не удалось»), и восстановление
         * продолжалось как ни в чём не бывало. Теперь причина уходит в лог со стеком, а
         * вызывающий (`KaraokeProcessWorker.start`) видит результат и сигналит в SSE.
         *
         * @return `true` — очистка выполнена (или чистить было нечего); `false` — сбой БД.
         */
        fun deleteDone(database: KaraokeConnection): Boolean {
            println("[${Timestamp.from(Instant.now())}] KaraokeProcess: Удаляем DONE (если есть)")
            try {
                val connection = database.getConnection()
                if (connection == null) {
                    println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                    return false
                }
                return purgeFinishedTuples(database)
            } catch (e: Exception) {
                logger.error("KaraokeProcess: не удалось удалить DONE-задания при старте воркера", e)
                return false
            }
        }

        /**
         * Возвращает осиротевшие `WORKING`-задания в `WAITING` при старте воркера.
         *
         * BR-036 (аудит #336, пакет PKG-S3-22): сбой этого шага оставлял строки в `WORKING` —
         * воркер их не выбирает (`getProcessesToStart` берёт только `WAITING`), лейн стоит,
         * а очередь при этом выглядит работающей. Теперь исключение логируется со стеком,
         * а вызывающий получает `false` и сигналит в SSE/мониторинг.
         *
         * @return `true` — сброс выполнен; `false` — сбой БД, `WORKING`-строки могли остаться.
         */
        /**
         * 674 (FR-005/FR-010): удалить только **полностью завершённые** кортежи (все строки `DONE`).
         *
         * Публичное имя вызывающего (`deleteDone`) сохранено ради существующих call-sites; семантика
         * отличается от старой (раньше удалялись все `DONE`-строки, включая головы живых кортежей).
         * `ERROR`-кортежи сохраняются для admin-retry.
         */
        fun purgeFinishedTuples(database: KaraokeConnection): Boolean {
            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                return false
            }
            return try {
                connection
                    .prepareStatement(
                        "DELETE FROM tbl_processes p " +
                            "WHERE p.process_chain_id IS NOT NULL " +
                            "AND NOT EXISTS (SELECT 1 FROM tbl_processes s " +
                            "WHERE s.process_chain_id = p.process_chain_id AND s.process_status <> 'DONE')",
                    ).use { ps -> ps.executeUpdate() }
                true
            } catch (e: Exception) {
                logger.error("KaraokeProcess: не удалось удалить завершённые кортежи", e)
                false
            }
        }

        /** 674 (FR-011): идемпотентная нормализация строк очереди к кортежной модели. */
        fun normalizeTuples(database: KaraokeConnection): Int {
            val connection = database.getConnection() ?: return 0
            var changed = 0
            return try {
                changed +=
                    connection
                        .prepareStatement(
                            "UPDATE tbl_processes SET process_chain_id = id, process_order = 0 " +
                                "WHERE process_chain_id IS NULL",
                        ).use { it.executeUpdate() }
                changed +=
                    connection
                        .prepareStatement(
                            "UPDATE tbl_processes SET process_status = 'WAITING', process_end = NULL, " +
                                "process_percentage = 0, process_percentage_str = '' " +
                                "WHERE process_status IN ('WORKING', 'CREATING')",
                        ).use { it.executeUpdate() }
                // Безголовые цепочки (голова удалена старым deleteDone): голова = шаг с минимальным order.
                val headless = mutableListOf<Long>()
                connection
                    .prepareStatement(
                        "SELECT DISTINCT p.process_chain_id FROM tbl_processes p " +
                            "WHERE p.process_chain_id IS NOT NULL " +
                            "AND NOT EXISTS (SELECT 1 FROM tbl_processes h WHERE h.id = p.process_chain_id)",
                    ).use { ps ->
                        ps.executeQuery().use { rs ->
                            while (rs.next()) headless += rs.getLong(1)
                        }
                    }
                headless.forEach { chainId ->
                    val headRow =
                        connection
                            .prepareStatement(
                                "SELECT id FROM tbl_processes WHERE process_chain_id = ? " +
                                    "ORDER BY process_order, id LIMIT 1",
                            ).use { ps ->
                                ps.setLong(1, chainId)
                                ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
                            } ?: return@forEach
                    connection
                        .prepareStatement("UPDATE tbl_processes SET process_chain_id = id WHERE id = ?")
                        .use { ps ->
                            ps.setLong(1, headRow)
                            ps.executeUpdate()
                        }
                    changed++
                }
                println(
                    "[${Timestamp.from(Instant.now())}] KaraokeProcess: нормализовано строк кортежей: $changed " +
                        "(chainless/working/creating/headless)",
                )
                changed
            } catch (e: Exception) {
                logger.error("KaraokeProcess: не удалось нормализовать кортежи", e)
                changed
            }
        }

        /** 674 (FR-005): есть ли у scope живые строки (для janitor — не удалять чужое/живое). */
        fun isScopeLive(
            scopeId: Long,
            database: KaraokeConnection,
        ): Boolean {
            val connection = database.getConnection() ?: return true
            return try {
                connection
                    .prepareStatement(
                        "SELECT 1 FROM tbl_processes WHERE (id = ? OR process_chain_id = ?) " +
                            "AND process_status IN ('WAITING', 'WORKING', 'CREATING') " +
                            "AND process_deleted_at IS NULL LIMIT 1",
                    ).use { ps ->
                        ps.setLong(1, scopeId)
                        ps.setLong(2, scopeId)
                        ps.executeQuery().use { rs -> rs.next() }
                    }
            } catch (e: Exception) {
                true
            }
        }

        /** 674 (FR-016): приоритет — свойство кортежа. */
        fun setChainPriority(
            chainId: Long,
            priority: Int,
            database: KaraokeConnection,
        ): Int {
            val connection = database.getConnection() ?: return 0
            return try {
                connection
                    .prepareStatement("UPDATE tbl_processes SET process_priority = ? WHERE process_chain_id = ?")
                    .use { ps ->
                        ps.setInt(1, priority)
                        ps.setLong(2, chainId)
                        ps.executeUpdate()
                    }
            } catch (e: Exception) {
                println("[${Timestamp.from(Instant.now())}] WARN process.chain_priority_failed: chain=$chainId: ${e.message}")
                0
            }
        }

        /** Первый исполняемый (`WAITING`) шаг кортежа — resume с первого не-`DONE`. */
        fun firstRunnableStep(
            chainId: Long,
            database: KaraokeConnection,
        ): KaraokeProcess? {
            val connection = database.getConnection() ?: return null
            return try {
                connection.prepareStatement(TupleSchedulerSql.FIRST_RUNNABLE_STEP).use { ps ->
                    ps.setLong(1, chainId)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) return null
                        KaraokeProcess(database).also { process ->
                            process.id = rs.getLong("id")
                            process.name = rs.getString("process_name")
                            process.status = rs.getString("process_status")
                            process.order = rs.getInt("process_order")
                            process.priority = rs.getInt("process_priority")
                            process.command = rs.getString("process_command")
                            process.args = convertJsonToArgs(rs.getString("process_args"))
                            process.envs = convertJsonToEnvs(rs.getString("process_envs"))
                            process.description = rs.getString("process_description")
                            process.songId = rs.getInt("song_id")
                            process.type = rs.getString("process_type")
                            process.start = rs.getTimestamp("process_start")
                            process.end = rs.getTimestamp("process_end")
                            process.prioritet = rs.getInt("process_prioritet")
                            process.withoutControl = rs.getBoolean("without_control")
                            process.threadId = rs.getInt("thread_id")
                            process.processChainId = rs.getLong("process_chain_id").takeIf { !rs.wasNull() }
                            process.processDeletedAt = rs.getTimestamp("process_deleted_at")
                        }
                    }
                }
            } catch (e: Exception) {
                println("[${Timestamp.from(Instant.now())}] WARN process.first_runnable_failed: chain=$chainId: ${e.message}")
                null
            }
        }

        /**
         * 674: единое создание цепочки/кортежа. Голова вставляется первой, её id — `process_chain_id`;
         * одиночная строка помечается головой (`chain_id = id`, `order = 0`).
         */
        fun createChain(processes: List<KaraokeProcess>): Long? {
            if (processes.isEmpty()) return null
            if (processes.size == 1) {
                val head = createDbInstance(processes[0]) ?: return null
                head.markAsHead()
                return head.id
            }
            val head = createDbInstance(processes.first()) ?: return null
            head.processChainId = head.id
            head.order = 0
            head.saveChainId()
            processes.drop(1).forEachIndexed { index, child ->
                child.processChainId = head.id
                child.order = index + 1
            }
            createDbInstance(processes.drop(1))
            return head.id
        }

        fun setWorkingToWaiting(database: KaraokeConnection): Boolean {
            println("[${Timestamp.from(Instant.now())}] KaraokeProcess: Сбрасываем WORKING (если есть) в WAITING...")

            try {
                val connection = database.getConnection()
                if (connection == null) {
                    println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                    return false
                }
                val sql =
                    "UPDATE tbl_processes SET " +
                        "process_status = 'WAITING', process_end = NULL, " +
                        "process_percentage = 0, process_percentage_str = '' " +
                        "WHERE process_status IN ('WORKING', 'CREATING')"
                connection.prepareStatement(sql).use { ps -> ps.executeUpdate() }
                return true
            } catch (e: Exception) {
                logger.error("KaraokeProcess: не удалось сбросить WORKING → WAITING при старте воркера", e)
                return false
            }
        }

        /**
         * Дописывает причину сбоя к описанию задания (`process_description`) при `ERROR`.
         *
         * BR-036 (аудит #336, пакет PKG-S3-22): раньше при `ERROR` в БД попадал только статус,
         * а причина уходила в stdout контейнера — в админке задание выглядело «упало, почему —
         * неизвестно». Отдельной колонки под причину нет, поэтому по совету карточки пишем в
         * `process_description` (она же видна оператору), сохраняя исходное описание шага.
         *
         * Идемпотентно: старый хвост ` | ERROR: …` отбрасывается, текст ограничен
         * [ERROR_REASON_MAX_LENGTH].
         */
        internal fun appendErrorReason(
            description: String,
            cause: Throwable?,
        ): String {
            if (cause == null) return description
            val base = description.substringBefore(ERROR_REASON_MARKER)
            val reason =
                buildString {
                    append(cause::class.simpleName ?: cause.javaClass.simpleName)
                    cause.message?.let { append(": ").append(it) }
                }.replace('\n', ' ').trim()
            val tail = "$ERROR_REASON_MARKER$reason"
            val head = base.trimEnd()
            val room = ERROR_REASON_MAX_LENGTH - tail.length
            return if (head.length <= room) {
                head + tail
            } else {
                head.take(room.coerceAtLeast(0)) + tail
            }
        }

        /**
         * Точечный аналог [setWorkingToWaiting] для одного thread-лейна: возвращает в `WAITING`
         * только записи конкретного `threadId`, которые числятся `WORKING` (осиротевшие - без живого
         * обработчика в `KaraokeProcessWorker.threadsMap`), не трогая другие лейны.
         *
         * Используется `resolveAction` проверки мониторинга `LaneStalledCheck` для восстановления
         * зависшего лейна без перезапуска всего воркера.
         *
         * @param database подключение к БД (local/remote/virtual)
         * @param threadId thread-лейн, который нужно восстановить
         * @return число восстановленных записей (0, если восстанавливать было нечего)
         * @see archive/docs/features/async-process-queue.md
         * @see archive/docs/features/monitoring.md
         */
        fun setWorkingToWaitingForThread(
            database: KaraokeConnection,
            threadId: Int,
        ): Int {
            println(
                "[${Timestamp.from(
                    Instant.now(),
                )}] KaraokeProcess: Сбрасываем WORKING в WAITING точечно для лейна threadId=$threadId...",
            )
            try {
                val connection = database.getConnection()
                if (connection == null) {
                    println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                    return 0
                }
                val sql =
                    "UPDATE tbl_processes SET " +
                        "process_status = ? " +
                        "WHERE process_status = ? AND thread_id = ?"
                val ps = connection.prepareStatement(sql)
                ps.setString(1, "WAITING")
                ps.setString(2, "WORKING")
                ps.setInt(3, threadId)
                val updated = ps.executeUpdate()
                ps.close()
                return updated
            } catch (e: Exception) {
                println(e.message)
                return 0
            }
        }

        fun createDbInstance(process: KaraokeProcess): KaraokeProcess? {
            val sql =
                "INSERT INTO tbl_processes (" +
                    "process_name, " +
                    "process_status, " +
                    "process_order, " +
                    "process_priority, " +
                    "process_command, " +
                    "process_args, " +
                    "process_envs, " +
                    "process_description, " +
                    "song_id, " +
                    "process_type, " +
                    "process_start, " +
                    "process_end, " +
                    "process_prioritet, " +
                    "process_start_str, " +
                    "process_end_str, " +
                    "process_percentage, " +
                    "process_percentage_str, " +
                    "process_time_passed_ms, " +
                    "process_time_passed_str, " +
                    "process_time_left_ms, " +
                    "process_time_left_str, " +
                    "without_control, " +
                    "thread_id, " +
                    // BR-032 (аудит #336, пакет PKG-S2-10): без этой колонки шаги цепочки
                    // жили как независимые WAITING-строки — админский retry/фильтр по
                    // process_chain_id их не видел.
                    "process_chain_id " +
                    ") VALUES(" +
                    "'${process.name.replace("'","''")}', " +
                    "'${process.status}', " +
                    "${process.order}, " +
                    "${process.priority}, " +
                    "'${process.command.replace("'","''")}', " +
                    "'${process.argsJson.replace("'","''")}', " +
                    "'${process.envsJson.replace("'","''")}', " +
                    "'${process.description.replace("'","''")}', " +
                    "${process.songId}, " +
                    "'${process.type}', " +
                    "${process.start}, " +
                    "${process.end}, " +
                    "${process.prioritet}, " +
                    "'${process.startStr}', " +
                    "'${process.endStr}', " +
                    "${process.percentage}, " +
                    "'${process.percentageStr}', " +
                    "${process.timePassedMs}, " +
                    "'${process.timePassedStr}', " +
                    "${process.timeLeftMs}, " +
                    "'${process.timeLeftStr}', " +
                    "${process.withoutControl}, " +
                    "${process.threadId}, " +
                    "${process.processChainId ?: "NULL"} " +
                    ")"

            val connection = process.database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${process.database.name}")
                return null
            }
            val ps = connection.prepareStatement(sql)
            ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS)
            val rs = ps.generatedKeys

            val result =
                if (rs.next()) {
                    process.id = rs.getLong(1)
                    process
                } else {
                    null
                }

            ps.close()

            result?.let {
                if (!it.withoutControl) {
                    val messageRecordAdd =
                        SseNotification.recordAdd(
                            RecordAddMessage(
                                tableName = "tbl_processes",
                                recordId = result.id,
                                databaseName = process.database.name,
                                record = result.toDTO(),
                            ),
                        )
                    SNS.send(messageRecordAdd)
                }
            }

            if (process.status == "WAITING" &&
                process.command != "tail"
            ) {
                KaraokeProcessWorker.sendCountWaitingMessage(getCountWaiting(database = process.database))
            }

            return result
        }

        /**
         * Выбирает по одному ближайшему к запуску `WAITING`-заданию на каждый thread-лейн.
         *
         * @param throwOnError specs/088-fix-queue-swallowed-errors: по умолчанию `false` —
         *   сбой БД тихо логируется, возвращается пустая карта (сегодняшнее поведение для
         *   всех вызывающих мест, кроме главного цикла очереди). При `true` (используется
         *   только изнутри `KaraokeProcessWorker.doStart()`) сбой пробрасывается как
         *   [SQLException] — см. [getCountWaiting].
         * @see archive/docs/features/async-process-queue.md
         */
        fun getProcessesToStart(
            database: KaraokeConnection,
            throwOnError: Boolean = false,
        ): Map<Int, KaraokeProcess> {
            val result: MutableMap<Int, KaraokeProcess> = mutableMapOf()

            val connection = database.getConnection()
            if (connection == null) {
                val message = "Невозможно установить соединение с базой данных ${database.name}"
                println("[${Timestamp.from(Instant.now())}] $message")
                if (throwOnError) throw SQLException(message)
                return emptyMap()
            }

            var statement: Statement? = null
            var rs: ResultSet? = null
//            val sql = "SELECT * FROM tbl_processes WHERE process_status = 'WAITING' ORDER BY process_priority, process_order, id LIMIT 1;"

            val sql = TupleSchedulerSql.SELECT_TUPLE_HEADS

            try {
                statement = connection.createStatement()
                rs = statement.executeQuery(sql)
                while (rs.next()) {
                    val process = KaraokeProcess(database)

                    process.id = rs.getLong("id")
                    process.name = rs.getString("process_name")
                    process.status = rs.getString("process_status")
                    process.order = rs.getInt("process_order")
                    process.priority = rs.getInt("process_priority")
                    process.command = rs.getString("process_command")
                    process.args = convertJsonToArgs(rs.getString("process_args"))
                    process.envs = convertJsonToEnvs(rs.getString("process_envs"))
                    process.description = rs.getString("process_description")
                    process.songId = rs.getInt("song_id")
                    process.type = rs.getString("process_type")
                    process.start = rs.getTimestamp("process_start")
                    process.end = rs.getTimestamp("process_end")
                    process.prioritet = rs.getInt("process_prioritet")
                    process.withoutControl = rs.getBoolean("without_control")
                    process.threadId = rs.getInt("thread_id")
                    process.processChainId = rs.getLong("process_chain_id").takeIf { !rs.wasNull() }
                    process.processDeletedAt = rs.getTimestamp("process_deleted_at")

                    result.put(process.threadId, process)
                }
            } catch (e: SQLException) {
                if (throwOnError) throw e
                e.printStackTrace()
            } finally {
                try {
                    rs?.close() // close result set
                    statement?.close() // close statement
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return result
        }

        /**
         * Загрузить список процессов по фильтрам.
         *
         * Фильтры параметризованы (`PreparedStatement` + `?`, BR-028): значения
         * из HTTP-параметров биндятся, а не склеиваются в SQL. Числовые поля
         * (`id`, `ids`, `process_order`, `thread_id`, …) приводятся к `Long`;
         * невалидное значение даёт `1=0` (пустая выборка), а не расширение фильтра.
         */
        fun loadList(
            args: Map<String, String> = emptyMap(),
            database: KaraokeConnection,
        ): List<KaraokeProcess> {
            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                return emptyList()
            }
            var statement: Statement? = null
            var preparedStatement: PreparedStatement? = null
            var rs: ResultSet? = null
            var sql: String
            val where = SqlWhereBuilder()

            fun addNumeric(
                column: String,
                value: String?,
            ) {
                val number = value?.trim()?.toLongOrNull()
                if (number == null) where.none() else where.add("$column = ?", number)
            }

            try {
                sql = "SELECT tbl_processes.*" +
                    " FROM tbl_processes"
                if (args.containsKey("id")) addNumeric("id", args["id"])
                if (args.containsKey("ids")) {
                    val ids = args["ids"]!!.split(",").map { it.trim().toLongOrNull() }
                    if (ids.isEmpty() || ids.any { it == null }) {
                        where.none()
                    } else {
                        where.add("tbl_processes.id IN (${ids.joinToString(", ") { "?" }})", *ids.toTypedArray())
                    }
                }
                if (args.containsKey("process_name")) where.add("process_name LIKE ?", "%${args["process_name"]}%")
                if (args.containsKey("process_status")) where.add("process_status = ?", args["process_status"])
                if (args.containsKey("process_order")) addNumeric("process_order", args["process_order"])
                if (args.containsKey("process_priority")) addNumeric("process_priority", args["process_priority"])
                if (args.containsKey("process_command")) where.add("process_command LIKE ?", "%${args["process_command"]}%")
                if (args.containsKey("process_args")) where.add("process_args LIKE ?", "%${args["process_args"]}%")
                if (args.containsKey("process_description")) {
                    where.add("process_description LIKE ?", "%${args["process_description"]}%")
                }
                if (args.containsKey("song_id")) addNumeric("song_id", args["song_id"])
                if (args.containsKey("process_type")) where.add("process_type = ?", args["process_type"])
                if (args.containsKey("process_prioritet")) addNumeric("process_prioritet", args["process_prioritet"])
                if (args.containsKey("filter_notail")) where.raw("process_command <> 'tail'")
                if (args.containsKey("thread_id")) addNumeric("thread_id", args["thread_id"])
                // BR-037 (аудит #336, пакет PKG-S3-22): soft-deleted задания
                // (`process_deleted_at <> NULL`) больше НЕ возвращаются по умолчанию.
                // Раньше `loadList` их отдавал: в админке строка оставалась видимой, а
                // `hasActiveProcess` навсегда видел `WAITING` — переименование файла песни
                // блокировалось «выполняется фоновая обработка», sync-audio пропускал
                // ребёнка. `getProcessesToStart` фильтровал их и раньше (эталон).
                // Явный `include_deleted` — для инструментов разбора/восстановления.
                if (!args.containsKey("include_deleted")) where.raw("process_deleted_at IS NULL")

                if (!where.isEmpty()) sql += " WHERE ${where.build().joinToString(" AND ")}"

                rs =
                    if (where.isEmpty()) {
                        statement = connection.createStatement()
                        statement.executeQuery(sql)
                    } else {
                        preparedStatement = connection.prepareStatement(sql)
                        where.build().bind(preparedStatement)
                        preparedStatement.executeQuery()
                    }
                val result: MutableList<KaraokeProcess> = mutableListOf()
                while (rs.next()) {
                    val process = KaraokeProcess(database)

                    process.id = rs.getLong("id")
                    process.name = rs.getString("process_name")
                    process.status = rs.getString("process_status")
                    process.order = rs.getInt("process_order")
                    process.priority = rs.getInt("process_priority")
                    process.command = rs.getString("process_command")
                    process.args = convertJsonToArgs(rs.getString("process_args"))
                    process.envs = convertJsonToEnvs(rs.getString("process_envs"))
                    process.description = rs.getString("process_description")
                    process.songId = rs.getInt("song_id")
                    process.type = rs.getString("process_type")
                    process.start = rs.getTimestamp("process_start")
                    process.end = rs.getTimestamp("process_end")
                    process.prioritet = rs.getInt("process_prioritet")
                    process.withoutControl = rs.getBoolean("without_control")
                    process.threadId = rs.getInt("thread_id")
                    process.processChainId = rs.getLong("process_chain_id").takeIf { !rs.wasNull() }
                    process.processDeletedAt = rs.getTimestamp("process_deleted_at")
                    result.add(process)
                }
                result.sort()

                if (args.containsKey("filter_limit")) {
                    // BR-034 (аудит #336, пакет PKG-S3-22): `toInt()` бросал NumberFormatException
                    // на нечисле (`?filterLimit=abc`), а `result[i]` — IndexOutOfBounds, когда
                    // лимит больше числа строк (`?filterLimit=1000` при 50 заданиях). Оба
                    // исключения не ловились (catch — только SQLException) → HTTP 500.
                    // Теперь срез — через `take()`, а невалидное значение — явная ошибка
                    // контракта (контроллеры отдают 400, см. `applyFilterLimit`).
                    return applyFilterLimit(result, args["filter_limit"])
                }

                return result
            } catch (e: SQLException) {
                e.printStackTrace()
            } finally {
                try {
                    rs?.close() // close result set
                    statement?.close() // close statement
                    preparedStatement?.close() // close prepared statement
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return emptyList()
        }

        /**
         * Есть ли у песни [songId] незавершённое задание в очереди (`WAITING` или `WORKING`).
         * Используется для блокировки переименования файла песни в SongEdit, пока над ней идёт
         * фоновая обработка (specs/124-filename-sanitization-rename, FR-013) — иначе
         * каскадное переименование могло бы переименовать файл, который в этот момент читает/пишет
         * уже запущенный процесс (например, Demucs).
         *
         * @see archive/docs/features/async-process-queue.md
         */
        fun hasActiveProcess(
            songId: Long,
            database: KaraokeConnection,
        ): Boolean =
            loadList(args = mapOf("song_id" to songId.toString()), database = database)
                .any { it.status == KaraokeProcessStatuses.WAITING.toString() || it.status == KaraokeProcessStatuses.WORKING.toString() }

        /**
         * Срез результата [loadList] по `filter_limit` — без индексации и без падения.
         *
         * BR-034 (аудит #336, пакет PKG-S3-22). Правила:
         * - нечисловое значение, отрицательное число или `null` — `IllegalArgumentException`
         *   (ошибка контракта: на HTTP-границе контроллер отвечает 400, см.
         *   `ApiController.apisProcessesDigests`/`apisProcessesPages`);
         * - лимит больше числа строк — возвращается весь список (раньше был `IndexOutOfBounds`);
         * - `0` — пустой список (осмысленный «ничего не отдавать»).
         *
         * @throws IllegalArgumentException если значение не является неотрицательным целым
         */
        internal fun applyFilterLimit(
            result: List<KaraokeProcess>,
            limitValue: String?,
        ): List<KaraokeProcess> {
            val raw = limitValue?.trim()
            val limit =
                raw?.toIntOrNull()
                    ?: throw IllegalArgumentException(
                        "filter_limit должен быть целым числом, получено: '${limitValue ?: "null"}'",
                    )
            require(limit >= 0) { "filter_limit не может быть отрицательным: $limit" }
            return result.take(limit)
        }

        /** 674: удалить все строки кортежа (терминальные, не живые — вызывается после single-flight). */
        fun deleteChain(
            chainId: Long,
            database: KaraokeConnection,
        ): Int {
            val connection = database.getConnection() ?: return 0
            return try {
                connection.prepareStatement("DELETE FROM tbl_processes WHERE process_chain_id = ?").use { ps ->
                    ps.setLong(1, chainId)
                    ps.executeUpdate()
                }
            } catch (e: Exception) {
                println("[${Timestamp.from(Instant.now())}] WARN process.chain_delete_failed: chain=$chainId: ${e.message}")
                0
            }
        }

        fun delete(
            id: Long,
            database: KaraokeConnection,
        ) {
            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                return
            }
            val sql = "DELETE FROM tbl_processes WHERE id = ?"
            val ps = connection.prepareStatement(sql)
            ps.setLong(1, id)
            ps.executeUpdate()
            ps.close()

            val messageRecordDelete =
                SseNotification.recordDelete(
                    RecordDeleteMessage(
                        recordId = id,
                        tableName = "tbl_processes",
                        databaseName = database.name,
                    ),
                )
            SNS.send(messageRecordDelete)

//            KaraokeProcessWorker.sendCountWaitingMessage(getCountWaiting(database = database))
        }

        fun load(
            id: Long,
            database: KaraokeConnection,
        ): KaraokeProcess? = loadList(mapOf(Pair("id", id.toString())), database).firstOrNull()

        @Suppress("UNCHECKED_CAST")
        fun createProcess(
            song: Song,
            action: KaraokeProcessTypes,
            doWait: Boolean = false,
            prior: Int = 1,
            threadId: Int,
            context: Map<String, Any> = emptyMap(),
        ): Long {
            // Находим есть ли уже такой процесс. Если нет - создаём. Если есть и не в статусе "в работе" - пересоздаём

            val existedProcessesLookupArgs =
                mutableMapOf(
                    "song_id" to song.id.toString(),
                    "process_type" to action.name,
                    "thread_id" to threadId.toString(),
                )
            if (action == KaraokeProcessTypes.UPLOAD_TO_LOCAL_STORE || action == KaraokeProcessTypes.UPLOAD_TO_REMOTE_STORE) {
                // Один и тот же process_type/thread_id используется для загрузки РАЗНЫХ файлов одной песни -
                // без уточнения по karaokeFileType задачи для разных файлов затирали бы друг друга
                (context["karaokeFileType"] as? String)?.let {
                    existedProcessesLookupArgs["process_args"] = "karaokeFileType=$it"
                }
            }
            val existedProcesses = loadList(existedProcessesLookupArgs, song.database)

            // BR-031 (аудит #336, пакет PKG-S2-10): не удаляем WAITING/WORKING/CREATING.
            // Раньше удалялось всё, кроме WORKING: (1) пока только что выбранный воркером
            // шаг ещё не записал WORKING, второй вызов удалял его строку — поток работал
            // «в никуда»; (2) у выполняющейся цепочки удалялись WAITING-шаги 2..N.
            // Активное задание = single-flight (return 0), пересоздаём только терминальные.
            // 674 (FR-010): single-flight по кортежу. Живой кортеж блокирует постановку целиком,
            // его терминальные строки (включая DONE-голову) не удаляются; удаляются только
            // полностью завершённые кортежи.
            val hasActive =
                existedProcesses.any {
                    it.processDeletedAt == null &&
                        it.status in
                        setOf(
                            KaraokeProcessStatuses.WORKING.name,
                            KaraokeProcessStatuses.WAITING.name,
                            KaraokeProcessStatuses.CREATING.name,
                        )
                }
            if (hasActive) return 0
            existedProcesses
                .map { it.processChainId ?: it.id }
                .distinct()
                .forEach { chainId ->
                    deleteChain(chainId, song.database)
                }

            // specs/126-key-from-file (#126, OpenProject): если для KEY_BPM_FROM_FILE файл
            // `<songFile> [key].json` уже есть, НЕ создаём процесс — применяем key/bpm
            // напрямую (см. Song.applyKeyBpmFromFileIfExists). Это защита от всех call-sites
            // (HealthReport, ручной /api/process, миграции), когда файл уже валиден.
            // Race-safety обеспечена saveToDbLocked() внутри helper + single-flight через
            // existedProcesses выше.
            if (action == KaraokeProcessTypes.KEY_BPM_FROM_FILE && song.applyKeyBpmFromFileIfExists()) {
                return 0
            }

            val karaokeProcess = KaraokeProcess(song.database)
            with(karaokeProcess) {
                this.name = "[${song.author}] - [${song.album}] - «${song.songName}»"
                this.status = if (doWait) KaraokeProcessStatuses.WAITING.name else KaraokeProcessStatuses.CREATING.name
                this.order = -1
                this.priority = prior
                this.command = ""
                this.type = action.name
                this.songId = song.id.toInt()
                this.threadId = threadId

                when (action) {
                    KaraokeProcessTypes.MELT_LYRICS -> {
                        val songKaraokeMp4Absolute = song.getOutputFilename(SongOutputFile.VIDEO, SongVersion.KARAOKE).rightFileName()
                        val songKaraokePngAbsolute = song.getOutputFilename(SongOutputFile.PICTURE, SongVersion.KARAOKE).rightFileName()
                        val songLyricsMp4Absolute = song.getOutputFilename(SongOutputFile.VIDEO, SongVersion.LYRICS).rightFileName()
                        val songLyricsPngAbsolute = song.getOutputFilename(SongOutputFile.PICTURE, SongVersion.LYRICS).rightFileName()
                        val songKaraokeMp4Relative =
                            song
                                .getOutputFilename(
                                    SongOutputFile.VIDEO,
                                    SongVersion.KARAOKE,
                                    relative = true,
                                ).rightFileName()
                        val songKaraokePngRelative =
                            song
                                .getOutputFilename(
                                    SongOutputFile.PICTURE,
                                    SongVersion.KARAOKE,
                                    relative = true,
                                ).rightFileName()
                        val songLyricsMp4Relative =
                            song
                                .getOutputFilename(
                                    SongOutputFile.VIDEO,
                                    SongVersion.LYRICS,
                                    relative = true,
                                ).rightFileName()
                        val songLyricsPngRelative =
                            song
                                .getOutputFilename(
                                    SongOutputFile.PICTURE,
                                    SongVersion.LYRICS,
                                    relative = true,
                                ).rightFileName()
                        if (!File(song.pathToSymlinkFolderMP4).exists()) {
                            Files.createDirectories(Path(song.pathToSymlinkFolderMP4))
                            runCommand(listOf("chmod", "777", song.pathToSymlinkFolderMP4))
                        }
                        if (!File(song.pathToSymlinkFolderPNG).exists()) {
                            Files.createDirectories(Path(song.pathToSymlinkFolderPNG))
                            runCommand(listOf("chmod", "777", song.pathToSymlinkFolderPNG))
                        }
                        if (!File(song.pathToSymlinkFolderSponsr).exists()) {
                            Files.createDirectories(Path(song.pathToSymlinkFolderSponsr))
                            runCommand(listOf("chmod", "777", song.pathToSymlinkFolderSponsr))
                        }
                        val newNoStemNameFlacSymlinkQ = song.accompanimentNameFlacSymlink.rightFileName().wrapInQuotes()
                        val songKaraokeMp4Symlink =
                            "${song.pathToSymlinkFolderMP4}/${File(
                                songKaraokeMp4Absolute,
                            ).name}".rightFileName().wrapInQuotes()
                        val songKaraokePngSymlink =
                            "${song.pathToSymlinkFolderPNG}/${File(
                                songKaraokePngAbsolute,
                            ).name}".rightFileName().wrapInQuotes()
                        val songLyricsMp4Symlink =
                            "${song.pathToSymlinkFolderMP4}/${File(
                                songLyricsMp4Absolute,
                            ).name}".rightFileName().wrapInQuotes()
                        val songLyricsPngSymlink =
                            "${song.pathToSymlinkFolderPNG}/${File(
                                songLyricsPngAbsolute,
                            ).name}".rightFileName().wrapInQuotes()

                        val songSponsrPngAbsolute = song.getOutputFilename(SongOutputFile.PICTURESPONSRTEASER)
                        val songSponsrPngRelative = song.getOutputFilename(SongOutputFile.PICTURESPONSRTEASER, relative = true)
                        val songSponsrPngSymlink =
                            "${song.pathToSymlinkFolderSponsr}/${File(
                                songSponsrPngAbsolute,
                            ).name}".rightFileName().wrapInQuotes()

                        description = "Кодирование LYRICS"
                        prioritet = 19
                        val cpuPercentMeltLyrics = cpuLimitPercentForType(KaraokeProcessTypes.MELT_LYRICS)
                        envs = mapOf("MLT_CPU_LIMIT" to dockerCpusEnvValue(cpuPercentMeltLyrics))
                        args =
                            listOf(
                                listOf(
                                    "docker",
                                    "compose",
                                    "-f",
                                    "/sm-karaoke/system/mlt-docker/docker-compose.yaml",
                                    "run",
                                    "--rm",
                                    "mlt",
                                    "-progress",
                                    "${song.rootFolder}/done_projects/${song.fileName} [lyrics].mlt".rightFileName(),
                                ),
                                listOf("chmod", "666", song.pathToFileLyrics),
                                listOf("mkdir", "-p", song.pathToStoreFolderLyrics),
                                listOf("chmod", "777", song.pathToStoreFolderLyrics),
                                listOf("cp", song.pathToFileLyrics, song.pathToStoreFolderLyrics),
                                listOf("chmod", "666", song.pathToStoreFileLyrics),
                                listOf("mkdir", "-p", song.pathToFolder720Lyrics),
                                listOf("chmod", "777", song.pathToFolder720Lyrics),
                                listOf("rm", song.pathToFile720Lyrics),
                                cpulimitPrefix(cpuPercentMeltLyrics) +
                                    listOf(
                                        "ffmpeg",
                                        "-i",
                                        song.pathToFileLyrics,
                                        "-c:v",
                                        "hevc_nvenc",
                                        "-preset",
                                        "fast",
                                        "-b:v",
                                        "1000k",
                                        "-vf",
                                        "scale=1280:720,fps=30",
                                        "-c:a",
                                        "aac",
                                        song.pathToFile720Lyrics,
                                        "-y",
                                    ),
                                listOf("chmod", "666", song.pathToFile720Lyrics),
                                listOf(
                                    "rm",
                                    "-f",
                                    newNoStemNameFlacSymlinkQ,
                                    songKaraokeMp4Symlink,
                                    songLyricsMp4Symlink,
                                    songKaraokePngSymlink,
                                    songLyricsPngSymlink,
                                    songSponsrPngSymlink,
                                ),
                                // Ссылка на flack accompaniment в папку pathToSymlinkFolderBoostyFiles
                                listOf(
                                    "ln",
                                    "-s",
                                    song.relativePathToNoStemNameFlac.rightFileName().wrapInQuotes(),
                                    newNoStemNameFlacSymlinkQ,
                                ),
                                listOf("chmod", "666", newNoStemNameFlacSymlinkQ),
                                // Ссылка на mp4 karaoke в папку pathToSymlinkFolderMP4
                                listOf("ln", "-s", songKaraokeMp4Relative.wrapInQuotes(), songKaraokeMp4Symlink),
                                listOf("chmod", "666", songKaraokeMp4Symlink),
                                // Ссылка на mp4 lyrics в папку pathToSymlinkFolderMP4
                                listOf("ln", "-s", songLyricsMp4Relative.wrapInQuotes(), songLyricsMp4Symlink),
                                listOf("chmod", "666", songLyricsMp4Symlink),
                                // Ссылка на png karaoke в папку pathToSymlinkFolderPNG
                                listOf("ln", "-s", songKaraokePngRelative.wrapInQuotes(), songKaraokePngSymlink),
                                listOf("chmod", "666", songKaraokePngSymlink),
                                // Ссылка на png lyrics в папку pathToSymlinkFolderPNG
                                listOf("ln", "-s", songLyricsPngRelative.wrapInQuotes(), songLyricsPngSymlink),
                                listOf("chmod", "666", songLyricsPngSymlink),
                                // Ссылка на png sponsr в папку pathToSymlinkFolderBoostyPNG
                                listOf("ln", "-s", songSponsrPngRelative.wrapInQuotes(), songSponsrPngSymlink),
                                listOf("chmod", "666", songSponsrPngSymlink),
                            )
                    }
                    KaraokeProcessTypes.MELT_KARAOKE -> {
                        val songKaraokeMp4Absolute = song.getOutputFilename(SongOutputFile.VIDEO, SongVersion.KARAOKE).rightFileName()
                        val songKaraokePngAbsolute = song.getOutputFilename(SongOutputFile.PICTURE, SongVersion.KARAOKE).rightFileName()
                        val songLyricsMp4Absolute = song.getOutputFilename(SongOutputFile.VIDEO, SongVersion.LYRICS).rightFileName()
                        val songLyricsPngAbsolute = song.getOutputFilename(SongOutputFile.PICTURE, SongVersion.LYRICS).rightFileName()
                        val songKaraokeMp4Relative =
                            song
                                .getOutputFilename(
                                    SongOutputFile.VIDEO,
                                    SongVersion.KARAOKE,
                                    relative = true,
                                ).rightFileName()
                        val songKaraokePngRelative =
                            song
                                .getOutputFilename(
                                    SongOutputFile.PICTURE,
                                    SongVersion.KARAOKE,
                                    relative = true,
                                ).rightFileName()
                        val songLyricsMp4Relative =
                            song
                                .getOutputFilename(
                                    SongOutputFile.VIDEO,
                                    SongVersion.LYRICS,
                                    relative = true,
                                ).rightFileName()
                        val songLyricsPngRelative =
                            song
                                .getOutputFilename(
                                    SongOutputFile.PICTURE,
                                    SongVersion.LYRICS,
                                    relative = true,
                                ).rightFileName()
                        if (!File(song.pathToSymlinkFolderMP4).exists()) {
                            Files.createDirectories(Path(song.pathToSymlinkFolderMP4))
                            runCommand(listOf("chmod", "777", song.pathToSymlinkFolderMP4))
                        }
                        if (!File(song.pathToSymlinkFolderPNG).exists()) {
                            Files.createDirectories(Path(song.pathToSymlinkFolderPNG))
                            runCommand(listOf("chmod", "777", song.pathToSymlinkFolderPNG))
                        }
                        if (!File(song.pathToSymlinkFolderSponsr).exists()) {
                            Files.createDirectories(Path(song.pathToSymlinkFolderSponsr))
                            runCommand(listOf("chmod", "777", song.pathToSymlinkFolderSponsr))
                        }
                        val newNoStemNameFlacSymlinkQ = song.accompanimentNameFlacSymlink.rightFileName().wrapInQuotes()
                        val songKaraokeMp4Symlink =
                            "${song.pathToSymlinkFolderMP4}/${File(
                                songKaraokeMp4Absolute,
                            ).name}".rightFileName().wrapInQuotes()
                        val songKaraokePngSymlink =
                            "${song.pathToSymlinkFolderPNG}/${File(
                                songKaraokePngAbsolute,
                            ).name}".rightFileName().wrapInQuotes()
                        val songLyricsMp4Symlink =
                            "${song.pathToSymlinkFolderMP4}/${File(
                                songLyricsMp4Absolute,
                            ).name}".rightFileName().wrapInQuotes()
                        val songLyricsPngSymlink =
                            "${song.pathToSymlinkFolderPNG}/${File(
                                songLyricsPngAbsolute,
                            ).name}".rightFileName().wrapInQuotes()

                        val songSponsrPngAbsolute = song.getOutputFilename(SongOutputFile.PICTURESPONSRTEASER)
                        val songSponsrPngRelative = song.getOutputFilename(SongOutputFile.PICTURESPONSRTEASER, relative = true)
                        val songSponsrPngSymlink =
                            "${song.pathToSymlinkFolderSponsr}/${File(
                                songSponsrPngAbsolute,
                            ).name}".rightFileName().wrapInQuotes()

                        description = "Кодирование KARAOKE"
                        prioritet = 19
                        val cpuPercentMeltKaraoke = cpuLimitPercentForType(KaraokeProcessTypes.MELT_KARAOKE)
                        envs = mapOf("MLT_CPU_LIMIT" to dockerCpusEnvValue(cpuPercentMeltKaraoke))
                        args =
                            listOf(
                                listOf(
                                    "docker",
                                    "compose",
                                    "-f",
                                    "/sm-karaoke/system/mlt-docker/docker-compose.yaml",
                                    "run",
                                    "--rm",
                                    "mlt",
                                    "-progress",
                                    "${song.rootFolder}/done_projects/${song.fileName} [karaoke].mlt".rightFileName(),
                                ),
                                listOf("chmod", "666", song.pathToFileKaraoke),
                                listOf("mkdir", "-p", song.pathToStoreFolderKaraoke),
                                listOf("chmod", "777", song.pathToStoreFolderKaraoke),
                                listOf("cp", song.pathToFileKaraoke, song.pathToStoreFileKaraoke),
                                listOf("chmod", "666", song.pathToStoreFileKaraoke),
                                listOf("mkdir", "-p", song.pathToFolder720Karaoke),
                                listOf("chmod", "777", song.pathToFolder720Karaoke),
                                listOf("rm", song.pathToFile720Karaoke),
                                cpulimitPrefix(cpuPercentMeltKaraoke) +
                                    listOf(
                                        "ffmpeg",
                                        "-i",
                                        song.pathToFileKaraoke,
                                        "-c:v",
                                        "hevc_nvenc",
                                        "-preset",
                                        "fast",
                                        "-b:v",
                                        "1000k",
                                        "-vf",
                                        "scale=1280:720,fps=30",
                                        "-c:a",
                                        "aac",
                                        song.pathToFile720Karaoke,
                                        "-y",
                                    ),
                                listOf("chmod", "666", song.pathToFile720Karaoke),
                                listOf(
                                    "rm",
                                    "-f",
                                    newNoStemNameFlacSymlinkQ,
                                    songKaraokeMp4Symlink,
                                    songLyricsMp4Symlink,
                                    songKaraokePngSymlink,
                                    songLyricsPngSymlink,
                                    songSponsrPngSymlink,
                                ),
                                // Ссылка на flack accompaniment в папку pathToSymlinkFolderBoostyFiles
                                listOf(
                                    "ln",
                                    "-s",
                                    song.relativePathToNoStemNameFlac.rightFileName().wrapInQuotes(),
                                    newNoStemNameFlacSymlinkQ,
                                ),
                                listOf("chmod", "666", newNoStemNameFlacSymlinkQ),
                                // Ссылка на mp4 karaoke в папку pathToSymlinkFolderMP4
                                listOf("ln", "-s", songKaraokeMp4Relative.wrapInQuotes(), songKaraokeMp4Symlink),
                                listOf("chmod", "666", songKaraokeMp4Symlink),
                                // Ссылка на mp4 lyrics в папку pathToSymlinkFolderMP4
                                listOf("ln", "-s", songLyricsMp4Relative.wrapInQuotes(), songLyricsMp4Symlink),
                                listOf("chmod", "666", songLyricsMp4Symlink),
                                // Ссылка на png karaoke в папку pathToSymlinkFolderPNG
                                listOf("ln", "-s", songKaraokePngRelative.wrapInQuotes(), songKaraokePngSymlink),
                                listOf("chmod", "666", songKaraokePngSymlink),
                                // Ссылка на png lyrics в папку pathToSymlinkFolderPNG
                                listOf("ln", "-s", songLyricsPngRelative.wrapInQuotes(), songLyricsPngSymlink),
                                listOf("chmod", "666", songLyricsPngSymlink),
                                // Ссылка на png sponsr в папку pathToSymlinkFolderBoostyPNG
                                listOf("ln", "-s", songSponsrPngRelative.wrapInQuotes(), songSponsrPngSymlink),
                                listOf("chmod", "666", songSponsrPngSymlink),
                            )
                    }
                    KaraokeProcessTypes.MELT_CHORDS -> {
                        description = "Кодирование CHORDS"
                        prioritet = 19
                        envs = mapOf("MLT_CPU_LIMIT" to dockerCpusEnvValue(cpuLimitPercentForType(KaraokeProcessTypes.MELT_CHORDS)))
                        args =
                            listOf(
                                listOf(
                                    "docker",
                                    "compose",
                                    "-f",
                                    "/sm-karaoke/system/mlt-docker/docker-compose.yaml",
                                    "run",
                                    "--rm",
                                    "mlt",
                                    "-progress",
                                    "${song.rootFolder}/done_projects/${song.fileName} [chords].mlt".rightFileName(),
                                ),
                                listOf("chmod", "666", song.pathToFileChords),
                            )
                    }
                    KaraokeProcessTypes.MELT_TABS -> {
                        description = "Кодирование TABS"
                        prioritet = 19
                        envs = mapOf("MLT_CPU_LIMIT" to dockerCpusEnvValue(cpuLimitPercentForType(KaraokeProcessTypes.MELT_TABS)))
                        args =
                            listOf(
                                listOf(
                                    "docker",
                                    "compose",
                                    "-f",
                                    "/sm-karaoke/system/mlt-docker/docker-compose.yaml",
                                    "run",
                                    "--rm",
                                    "mlt",
                                    "-progress",
                                    "${song.rootFolder}/done_projects/${song.fileName} [tabs].mlt".rightFileName(),
                                ),
                                listOf("chmod", "666", song.pathToFileMelody),
                            )
                    }

                    KaraokeProcessTypes.DEMUCS2 -> {
                        description = "Демукс 2"
                        val (demuxArgs, demuxEnvs) = song.argsDemucs2(threadId = this.threadId)
                        args = demuxArgs
                        envs = demuxEnvs
                    }
                    KaraokeProcessTypes.DEMUCS5 -> {
                        description = "Демукс 5"
                        val (demuxArgs, demuxEnvs) = song.argsDemucs5(threadId = this.threadId)
                        args = demuxArgs
                        envs = demuxEnvs
                    }
                    KaraokeProcessTypes.SHEETSAGE -> {
                        val srcWav = "/sm-karaoke/system/sheetsage/source.wav"
                        val resultFolder = "~/Karaoke/output/output"
                        val resultPdf = "$resultFolder/output.pdf"
                        val resultMidi = "$resultFolder/output.midi"
                        val resultLy = "$resultFolder/output.ly"
                        val resultBeattimes = "$resultFolder/beattimes"
                        description = "SHEETSAGE"
                        val cpuPercentSheetsage = cpuLimitPercentForType(KaraokeProcessTypes.SHEETSAGE)
                        args =
                            listOf(
                                listOf("mkdir", "-p", resultFolder),
                                listOf("rm", "-f", srcWav, resultPdf, resultMidi, resultLy, resultBeattimes),
                                cpulimitPrefix(cpuPercentSheetsage) +
                                    listOf(
                                        "ffmpeg",
                                        "-i",
                                        song.fileAbsolutePath.rightFileName(),
                                        "-compression_level",
                                        "8",
                                        srcWav,
                                        "-y",
                                    ),
                                cpulimitPrefix(cpuPercentSheetsage) +
                                    listOf("~/sheetsage/sheetsage.sh", "-j", "-o", "output/output", srcWav),
                                listOf("mkdir", "-p", song.pathToFolderSheetsage),
                                listOf("mv", resultPdf.rightFileName(), song.pathToFileSheetsagePDF.rightFileName()),
                                listOf("mv", resultMidi.rightFileName(), song.pathToFileSheetsageMIDI.rightFileName()),
                                listOf("mv", resultLy.rightFileName(), song.pathToFileSheetsageLY.rightFileName()),
                                listOf("mv", resultBeattimes.rightFileName(), song.pathToFileSheetsageBeattimes.rightFileName()),
                            )
                        argsDescription =
                            listOf(
                                "SHEETSAGE - Создание папки output",
                                "SHEETSAGE - Удаление старых файлов",
                                "SHEETSAGE - Декодирование FLAC в WAV",
                                "SHEETSAGE - Распознавание аккордов",
                                "SHEETSAGE - Создание папки sheetsage",
                                "SHEETSAGE - Копирование файла PDF",
                                "SHEETSAGE - Копирование файла MIDI",
                                "SHEETSAGE - Копирование файла LY",
                                "SHEETSAGE - Копирование файла Beattimes",
                            )
                    }
                    KaraokeProcessTypes.SHEETSAGE2 -> {
                        val srcWav = "/sm-karaoke/system/sheetsage/source.wav"
                        val resultFolder = "~/Karaoke/output/output"
                        val resultPdf = "$resultFolder/output.pdf"
                        val resultMidi = "$resultFolder/output.midi"
                        val resultLy = "$resultFolder/output.ly"
                        val resultBeattimes = "$resultFolder/beattimes"
                        description = "SHEETSAGE2"
                        val cpuPercentSheetsage2 = cpuLimitPercentForType(KaraokeProcessTypes.SHEETSAGE2)
                        args =
                            listOf(
                                listOf("mkdir", "-p", resultFolder),
                                listOf("rm", "-f", srcWav, resultPdf, resultMidi, resultLy, resultBeattimes),
                                cpulimitPrefix(cpuPercentSheetsage2) +
                                    listOf(
                                        "ffmpeg",
                                        "-i",
                                        song.fileAbsolutePath.rightFileName(),
                                        "-compression_level",
                                        "8",
                                        srcWav,
                                        "-y",
                                    ),
                                cpulimitPrefix(cpuPercentSheetsage2) +
                                    listOf("~/sheetsage/sheetsage.sh", "-j", "-o", "output/output", "--measures_per_chunk", "4", srcWav),
                                listOf("mkdir", "-p", song.pathToFolderSheetsage),
                                listOf("mv", resultPdf.rightFileName(), song.pathToFileSheetsagePDF.rightFileName()),
                                listOf("mv", resultMidi.rightFileName(), song.pathToFileSheetsageMIDI.rightFileName()),
                                listOf("mv", resultLy.rightFileName(), song.pathToFileSheetsageLY.rightFileName()),
                                listOf("mv", resultBeattimes.rightFileName(), song.pathToFileSheetsageBeattimes.rightFileName()),
                            )
                        argsDescription =
                            listOf(
                                "SHEETSAGE2 - Создание папки output",
                                "SHEETSAGE2 - Удаление старых файлов",
                                "SHEETSAGE2 - Декодирование FLAC в WAV",
                                "SHEETSAGE2 - Распознавание аккордов",
                                "SHEETSAGE2 - Создание папки sheetsage",
                                "SHEETSAGE2 - Копирование файла PDF",
                                "SHEETSAGE2 - Копирование файла MIDI",
                                "SHEETSAGE2 - Копирование файла LY",
                                "SHEETSAGE2 - Копирование файла Beattimes",
                            )
                    }
                    KaraokeProcessTypes.FF_720_KAR -> {
                        val destinationFolder = song.pathToFolder720Karaoke
                        val sourceFile = song.pathToFileKaraoke
                        val destinationFile = song.pathToFile720Karaoke
                        if (File(destinationFile).exists()) return -1
                        if (!File(destinationFolder).exists()) {
                            Files.createDirectories(Path(destinationFolder))
                            runCommand(listOf("chmod", "777", destinationFolder))
                        }
                        description = "720P KARAOKE"
                        args =
                            listOf(
                                cpulimitPrefix(cpuLimitPercentForType(KaraokeProcessTypes.FF_720_KAR)) +
                                    listOf(
                                        "ffmpeg",
                                        "-i",
                                        sourceFile,
                                        "-c:v",
                                        "hevc_nvenc",
                                        "-preset",
                                        "fast",
                                        "-b:v",
                                        "1000k",
                                        "-vf",
                                        "scale=1280:720,fps=30",
                                        "-c:a",
                                        "aac",
                                        destinationFile,
                                        "-y",
                                    ),
                                listOf("chmod", "666", destinationFile),
                            )
                    }
                    KaraokeProcessTypes.FF_720_LYR -> {
                        val destinationFolder = song.pathToFolder720Lyrics
                        val sourceFile = song.pathToFileLyrics
                        val destinationFile = song.pathToFile720Lyrics
                        if (File(destinationFile).exists()) return -1
                        if (!File(destinationFolder).exists()) {
                            Files.createDirectories(Path(destinationFolder))
                            runCommand(listOf("chmod", "777", destinationFolder))
                        }

                        description = "720P LYRICS"
                        args =
                            listOf(
                                cpulimitPrefix(cpuLimitPercentForType(KaraokeProcessTypes.FF_720_LYR)) +
                                    listOf(
                                        "ffmpeg",
                                        "-i",
                                        sourceFile,
                                        "-c:v",
                                        "hevc_nvenc",
                                        "-preset",
                                        "fast",
                                        "-b:v",
                                        "1000k",
                                        "-vf",
                                        "scale=1280:720,fps=30",
                                        "-c:a",
                                        "aac",
                                        destinationFile,
                                        "-y",
                                    ),
                                listOf("chmod", "666", destinationFile),
                            )
                    }
                    KaraokeProcessTypes.SYMLINK -> {
                        withoutControl = true
                        val songKaraokeMp4Absolute = song.getOutputFilename(SongOutputFile.VIDEO, SongVersion.KARAOKE).rightFileName()
                        val songKaraokePngAbsolute = song.getOutputFilename(SongOutputFile.PICTURE, SongVersion.KARAOKE).rightFileName()
                        val songLyricsMp4Absolute = song.getOutputFilename(SongOutputFile.VIDEO, SongVersion.LYRICS).rightFileName()
                        val songLyricsPngAbsolute = song.getOutputFilename(SongOutputFile.PICTURE, SongVersion.LYRICS).rightFileName()
                        val songKaraokeMp4Relative =
                            song
                                .getOutputFilename(
                                    SongOutputFile.VIDEO,
                                    SongVersion.KARAOKE,
                                    relative = true,
                                ).rightFileName()
                        val songKaraokePngRelative =
                            song
                                .getOutputFilename(
                                    SongOutputFile.PICTURE,
                                    SongVersion.KARAOKE,
                                    relative = true,
                                ).rightFileName()
                        val songLyricsMp4Relative =
                            song
                                .getOutputFilename(
                                    SongOutputFile.VIDEO,
                                    SongVersion.LYRICS,
                                    relative = true,
                                ).rightFileName()
                        val songLyricsPngRelative =
                            song
                                .getOutputFilename(
                                    SongOutputFile.PICTURE,
                                    SongVersion.LYRICS,
                                    relative = true,
                                ).rightFileName()
                        if (!File(song.pathToSymlinkFolderMP4).exists()) {
                            Files.createDirectories(Path(song.pathToSymlinkFolderMP4))
                            runCommand(listOf("chmod", "777", song.pathToSymlinkFolderMP4))
                        }
                        if (!File(song.pathToSymlinkFolderPNG).exists()) {
                            Files.createDirectories(Path(song.pathToSymlinkFolderPNG))
                            runCommand(listOf("chmod", "777", song.pathToSymlinkFolderPNG))
                        }
                        if (!File(song.pathToSymlinkFolderSponsr).exists()) {
                            Files.createDirectories(Path(song.pathToSymlinkFolderSponsr))
                            runCommand(listOf("chmod", "777", song.pathToSymlinkFolderPNG))
                        }

                        val newNoStemNameFlacSymlinkQ = song.accompanimentNameFlacSymlink.rightFileName().wrapInQuotes()
                        val songKaraokeMp4Symlink =
                            "${song.pathToSymlinkFolderMP4}/${File(
                                songKaraokeMp4Absolute,
                            ).name}".rightFileName().wrapInQuotes()
                        val songKaraokePngSymlink =
                            "${song.pathToSymlinkFolderPNG}/${File(
                                songKaraokePngAbsolute,
                            ).name}".rightFileName().wrapInQuotes()
                        val songLyricsMp4Symlink =
                            "${song.pathToSymlinkFolderMP4}/${File(
                                songLyricsMp4Absolute,
                            ).name}".rightFileName().wrapInQuotes()
                        val songLyricsPngSymlink =
                            "${song.pathToSymlinkFolderPNG}/${File(
                                songLyricsPngAbsolute,
                            ).name}".rightFileName().wrapInQuotes()

                        val songSponsrPngAbsolute = song.getOutputFilename(SongOutputFile.PICTURESPONSRTEASER)
                        val songSponsrPngRelative = song.getOutputFilename(SongOutputFile.PICTURESPONSRTEASER, relative = true)
                        val songSponsrPngSymlink =
                            "${song.pathToSymlinkFolderSponsr}/${File(
                                songSponsrPngAbsolute,
                            ).name}".rightFileName().wrapInQuotes()

                        description = "SYMLINK"
                        args =
                            listOf(
                                listOf(
                                    "rm",
                                    "-f",
                                    newNoStemNameFlacSymlinkQ,
                                    songKaraokeMp4Symlink,
                                    songLyricsMp4Symlink,
                                    songKaraokePngSymlink,
                                    songLyricsPngSymlink,
                                    songSponsrPngSymlink,
                                ),
                                // Ссылка на flack accompaniment в папку pathToSymlinkFolderBoostyFiles
                                listOf(
                                    "ln",
                                    "-s",
                                    song.relativePathToNoStemNameFlac.rightFileName().wrapInQuotes(),
                                    newNoStemNameFlacSymlinkQ,
                                ),
                                listOf("chmod", "666", newNoStemNameFlacSymlinkQ),
                                // Ссылка на mp4 karaoke в папку pathToSymlinkFolderMP4
                                listOf("ln", "-s", songKaraokeMp4Relative.wrapInQuotes(), songKaraokeMp4Symlink),
                                listOf("chmod", "666", songKaraokeMp4Symlink),
                                // Ссылка на mp4 lyrics в папку pathToSymlinkFolderMP4
                                listOf("ln", "-s", songLyricsMp4Relative.wrapInQuotes(), songLyricsMp4Symlink),
                                listOf("chmod", "666", songLyricsMp4Symlink),
                                // Ссылка на png karaoke в папку pathToSymlinkFolderPNG
                                listOf("ln", "-s", songKaraokePngRelative.wrapInQuotes(), songKaraokePngSymlink),
                                listOf("chmod", "666", songKaraokePngSymlink),
                                // Ссылка на png lyrics в папку pathToSymlinkFolderPNG
                                listOf("ln", "-s", songLyricsPngRelative.wrapInQuotes(), songLyricsPngSymlink),
                                listOf("chmod", "666", songLyricsPngSymlink),
                                // Ссылка на png sponsr в папку pathToSymlinkFolderBoostyPNG
                                listOf("ln", "-s", songSponsrPngRelative.wrapInQuotes(), songSponsrPngSymlink),
                                listOf("chmod", "666", songSponsrPngSymlink),
                            )
                    }
//                    KaraokeProcessTypes.FF_MP3_KAR -> {
//                        description = "MP3 KARAOKE"
//                        args = listOf(
//                            listOf("mkdir", "-p", song.pathToFolderMP3Karaoke),
//                            listOf("chmod", "777", song.pathToFolderMP3Karaoke),
//                            listOf("rm", song.pathToFileMP3Karaoke),
//                            listOf(
//                                "ffmpeg",
//                                "-i",
//                                song.accompanimentNameFlac.rightFileName(),
//                                "-ab",
//                                "320k",
//                                "-map_metadata",
//                                "0",
//                                "-id3v2_version",
//                                "3",
//                                song.pathToFileMP3Karaoke,
//                                "-y"
//                            ),
//                            listOf("chmod", "666", song.pathToFileMP3Karaoke),
//                        )
//                    }
//                    KaraokeProcessTypes.FF_MP3_LYR -> {
//                        description = "MP3 LYRICS"
//                        args = listOf(
//                            listOf("mkdir", "-p", song.pathToFolderMP3Lyrics),
//                            listOf("chmod", "777", song.pathToFolderMP3Lyrics),
//                            listOf("rm", song.pathToFileMP3Lyrics),
//                            listOf(
//                                "ffmpeg",
//                                "-i",
//                                song.fileAbsolutePath.rightFileName(),
//                                "-ab",
//                                "320k",
//                                "-map_metadata",
//                                "0",
//                                "-id3v2_version",
//                                "3",
//                                song.pathToFileMP3Lyrics,
//                                "-y"
//                            ),
//                            listOf("chmod", "666", song.pathToFileMP3Lyrics),
//                        )
//                    }
                    KaraokeProcessTypes.FF_MP3_ACCOMPANIMENT -> {
                        description = "MP3 ACCOMPANIMENT"
                        args =
                            listOf(
                                listOf("rm", song.accompanimentNameMp3),
                                listOf(
                                    "ffmpeg",
                                    "-i",
                                    song.accompanimentNameFlac.rightFileName(),
                                    "-ab",
                                    "320k",
                                    "-map_metadata",
                                    "0",
                                    "-id3v2_version",
                                    "3",
                                    song.accompanimentNameMp3,
                                    "-y",
                                ),
                                listOf("chmod", "666", song.accompanimentNameMp3),
                            )
                    }
                    KaraokeProcessTypes.FF_MP3_VOCAL -> {
                        description = "MP3 VOCAL"
                        args =
                            listOf(
                                listOf("rm", song.vocalsNameMp3),
                                listOf(
                                    "ffmpeg",
                                    "-i",
                                    song.vocalsNameFlac.rightFileName(),
                                    "-ab",
                                    "320k",
                                    "-map_metadata",
                                    "0",
                                    "-id3v2_version",
                                    "3",
                                    song.vocalsNameMp3,
                                    "-y",
                                ),
                                listOf("chmod", "666", song.vocalsNameMp3),
                            )
                    }
                    KaraokeProcessTypes.FF_MP3_DRUMS -> {
                        description = "MP3 DRUMS"
                        args =
                            listOf(
                                listOf("rm", song.drumsNameMp3),
                                listOf(
                                    "ffmpeg",
                                    "-i",
                                    song.drumsNameFlac.rightFileName(),
                                    "-ab",
                                    "320k",
                                    "-map_metadata",
                                    "0",
                                    "-id3v2_version",
                                    "3",
                                    song.drumsNameMp3,
                                    "-y",
                                ),
                                listOf("chmod", "666", song.drumsNameMp3),
                            )
                    }
                    KaraokeProcessTypes.FF_MP3_BASS -> {
                        description = "MP3 BASS"
                        args =
                            listOf(
                                listOf("rm", song.bassNameMp3),
                                listOf(
                                    "ffmpeg",
                                    "-i",
                                    song.bassNameFlac.rightFileName(),
                                    "-ab",
                                    "320k",
                                    "-map_metadata",
                                    "0",
                                    "-id3v2_version",
                                    "3",
                                    song.bassNameMp3,
                                    "-y",
                                ),
                                listOf("chmod", "666", song.bassNameMp3),
                            )
                    }
                    KaraokeProcessTypes.FF_MP3_OTHER -> {
                        description = "MP3 OTHER"
                        args =
                            listOf(
                                listOf("rm", song.otherNameMp3),
                                listOf(
                                    "ffmpeg",
                                    "-i",
                                    song.otherNameFlac.rightFileName(),
                                    "-ab",
                                    "320k",
                                    "-map_metadata",
                                    "0",
                                    "-id3v2_version",
                                    "3",
                                    song.otherNameMp3,
                                    "-y",
                                ),
                                listOf("chmod", "666", song.otherNameMp3),
                            )
                    }
                    KaraokeProcessTypes.UPLOAD_TO_LOCAL_STORE -> {
                        description = "UPLOAD TO LOCAL STORE"
                        val pathToFile = context["pathToFile"] as String
                        val karaokeFileType = context["karaokeFileType"] as String
                        val deleteAfterUpload = context["deleteAfterUpload"] as? String
                        args =
                            listOf(
                                listOfNotNull(
                                    "runFunctionWithArgs",
                                    "uploadToLocalStore",
                                    "songId=${song.id}",
                                    "pathToFile=$pathToFile",
                                    "karaokeFileType=$karaokeFileType",
                                    (context["storageFileName"] as? String)?.let { "storageFileName=$it" },
                                    (context["bucketName"] as? String)?.let { "bucketName=$it" },
                                    deleteAfterUpload?.let { "deleteAfterUpload=$it" },
                                ),
                            )
                    }
                    KaraokeProcessTypes.UPLOAD_TO_REMOTE_STORE -> {
                        description = "UPLOAD TO REMOTE STORE"
                        val pathToFile = context["pathToFile"] as String
                        val karaokeFileType = context["karaokeFileType"] as String
                        val deleteAfterUpload = context["deleteAfterUpload"] as? String
                        args =
                            listOf(
                                listOfNotNull(
                                    "runFunctionWithArgs",
                                    "uploadToRemoteStore",
                                    "songId=${song.id}",
                                    "pathToFile=$pathToFile",
                                    "karaokeFileType=$karaokeFileType",
                                    (context["storageFileName"] as? String)?.let { "storageFileName=$it" },
                                    (context["bucketName"] as? String)?.let { "bucketName=$it" },
                                    deleteAfterUpload?.let { "deleteAfterUpload=$it" },
                                ),
                            )
                    }
                    KaraokeProcessTypes.SMARTCOPY -> {
                        description = "Smart Copy"
                        args = context["args"] as List<List<String>>
                        argsDescription = context["argsDescription"] as List<String>
                        type = context["typesText"] as String
                    }

                    KaraokeProcessTypes.KEY_BPM_FROM_FILE -> {
                        description = "Key Bpm from file"
                        val (actionArgs, actionEnvs) = song.argsKeyBpmFinder()
                        args = actionArgs
                        envs = actionEnvs
                    }

                    KaraokeProcessTypes.RENDER_MP4_LYRICS,
                    KaraokeProcessTypes.RENDER_MP4_KARAOKE,
                    KaraokeProcessTypes.RENDER_MP4_CHORDS,
                    KaraokeProcessTypes.RENDER_MP4_TABS,
                    KaraokeProcessTypes.RENDER_MP4_DEMO,
                    -> {
                        val version = context["version"] as? String ?: "KARAOKE"
                        description = "RENDER MP4 ($version)"
                        val songId = song.id
                        val isDemo = version == "DEMO"
                        val width = context["width"] as? Int ?: if (isDemo) 1280 else 1920
                        val height = context["height"] as? Int ?: if (isDemo) 720 else 1080
                        val fps = context["fps"] as? Int ?: if (isDemo) 30 else 60
                        // Спека 144: маркер "кем инициирован рендер" — persisted в args (переживает
                        // WAITING->WORKING->DONE), читается в пост-хуке публикации Telegram
                        // (KaraokeProcessWorker.run) без отдельной колонки/миграции. Только approve-flow
                        // (SongEditorController.triggerRenderMp4DemoIfNeeded) передаёт "trigger" -> "approve";
                        // ручной рендер из UI (ApiController) context не содержит, токен не добавляется.
                        val trigger = context["trigger"] as? String
                        args =
                            listOf(
                                listOfNotNull(
                                    "runFunctionWithArgs",
                                    "renderMp4",
                                    "songId=${song.id}",
                                    "width=$width",
                                    "height=$height",
                                    "fps=$fps",
                                    "version=$version",
                                    trigger?.let { "trigger=$it" },
                                ),
                            )
                    }

                    KaraokeProcessTypes.FORCED_ALIGN_MARKERS -> {
                        description = "Точные маркеры (forced-alignment)"
                        val useFinetunedModel = context["useFinetunedModel"] as? String ?: "false"
                        args =
                            listOf(
                                listOf(
                                    "runFunctionWithArgs",
                                    "executeForcedAlignMarkers",
                                    "songId=${song.id}",
                                    "useFinetunedModel=$useFinetunedModel",
                                ),
                            )
                    }

                    else -> {}
                }
            }

            karaokeProcess.updateStatusProcessSong(
                database = song.database,
                storageService = song.storageService,
                storageApiClient = song.storageApiClient,
            )

            // BR-033 (аудит #336, пакет PKG-S2-10): валидируем args ДО вставки. Пустой
            // внешний список ронял главный цикл воркера (args[0]), пустой шаг навсегда
            // блокировал лейн. Невалидное задание не создаём вовсе.
            ProcessArgsPolicy.validationError(karaokeProcess.args)?.let { reason ->
                println("[${Timestamp.from(Instant.now())}] WARN process.create_rejected: action=${action.name} songId=${song.id}: $reason")
                return 0
            }

            // 674 (FR-001/FR-006): кортеж — единица исполнения; одиночное задание — кортеж из
            // одного шага (голова с chain_id = id, order = 0).
            return createChain(separate(karaokeProcess)) ?: 0
        }

        fun separate(parentProcess: KaraokeProcess): List<KaraokeProcess> {
            if (parentProcess.args.size == 1) return listOf(parentProcess)
            val result: MutableList<KaraokeProcess> = mutableListOf()

            parentProcess.args.forEachIndexed { index, childArgs ->
                val desc =
                    if (parentProcess.args.size == parentProcess.argsDescription.size) {
                        parentProcess.argsDescription[index]
                    } else {
                        parentProcess.description
                    }
                val command = if (index == 0) "" else "tail"
                val childProcess = KaraokeProcess(parentProcess.database, parentProcess.storageService, parentProcess.storageApiClient)
                childProcess.name = parentProcess.name
                childProcess.status = parentProcess.status
                // BR-032: номер шага — в process_order (0..N-1), чтобы воркер шёл по порядку.
                childProcess.order = index
                childProcess.priority = parentProcess.priority
                childProcess.command = command
                childProcess.type = parentProcess.type
                childProcess.songId = parentProcess.songId
                childProcess.description = desc
                childProcess.prioritet = parentProcess.prioritet
                childProcess.withoutControl = parentProcess.withoutControl
                childProcess.threadId = parentProcess.threadId
                childProcess.envs = parentProcess.envs
                childProcess.args = listOf(childArgs)
                childProcess.processChainId = parentProcess.processChainId
                result.add(childProcess)
            }

            return result
        }
    }
}
