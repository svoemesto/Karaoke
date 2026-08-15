package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.SNS
import com.svoemesto.karaokeapp.services.StorageApiClient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.Serializable
import java.nio.file.Files
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
 * @see archive/archive/docs/features/async-process-queue.md
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
            updateStatusProcessSong(database = database, storageService = storageService, storageApiClient = storageApiClient)
            if (command != "tail" || args[0][0] !in KaraokeProcessWorker.argsIgnoredToLog) {
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
                            song.saveToDb()
                        }
                    }

                    KaraokeProcessTypes.MELT_KARAOKE.name -> {
                        if (song.statusProcessKaraoke != status) {
                            song.statusProcessKaraoke = status
                            song.saveToDb()
                        }
                    }

                    KaraokeProcessTypes.MELT_CHORDS.name -> {
                        if (song.statusProcessChords != status) {
                            song.statusProcessChords = status
                            song.saveToDb()
                        }
                    }

                    KaraokeProcessTypes.MELT_TABS.name -> {
                        if (song.statusProcessMelody != status) {
                            song.statusProcessMelody = status
                            song.saveToDb()
                        }
                    }

                    KaraokeProcessTypes.RENDER_MP4_DEMO.name -> {
                        if (song.statusProcessDemo != status) {
                            song.statusProcessDemo = status
                            song.saveToDb()
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    companion object {
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
         * @see archive/archive/docs/features/async-process-queue.md
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
            val sql = "select count(*) as cnt from tbl_processes where process_status = 'WAITING' and process_command <> 'tail'"
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

        fun deleteDone(database: KaraokeConnection) {
            println("[${Timestamp.from(Instant.now())}] KaraokeProcess: Удаляем DONE (если есть)")
            try {
                val connection = database.getConnection()
                if (connection == null) {
                    println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                    return
                }
                val sql = "DELETE FROM tbl_processes WHERE process_status = ?"
                val ps = connection.prepareStatement(sql)
                ps.setString(1, "DONE")
                ps.executeUpdate()
                ps.close()
            } catch (e: Exception) {
                println(e.message)
            }
        }

        fun setWorkingToWaiting(database: KaraokeConnection) {
            println("[${Timestamp.from(Instant.now())}] KaraokeProcess: Сбрасываем WORKING (если есть) в WAITING...")

            try {
                val connection = database.getConnection()
                if (connection == null) {
                    println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                    return
                }
                val sql =
                    "UPDATE tbl_processes SET " +
                        "process_status = ? " +
                        "WHERE process_status = ?"
                val ps = connection.prepareStatement(sql)
                ps.setString(1, "WAITING")
                ps.setString(2, "WORKING")
                ps.executeUpdate()
                ps.close()
            } catch (e: Exception) {
                println(e.message)
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
         * @see archive/archive/docs/features/async-process-queue.md
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
                    "thread_id " +
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
                    "${process.threadId} " +
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
         * @see archive/archive/docs/features/async-process-queue.md
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

            val sql =
                """
                SELECT *
                FROM (
                    SELECT *,
                           ROW_NUMBER() OVER (
                               PARTITION BY thread_id
                               ORDER BY process_priority, process_order, id
                           ) AS rn
                    FROM tbl_processes
                    WHERE process_status = 'WAITING'
                ) ranked
                WHERE rn = 1;
                """.trimIndent()

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
            var rs: ResultSet? = null
            var sql: String
            val where: MutableList<String> = mutableListOf()

            try {
                statement = connection.createStatement()
                sql = "SELECT tbl_processes.*" +
                    " FROM tbl_processes"
                if (args.containsKey("id")) where += "id=${args["id"]}"
                if (args.containsKey("ids")) where += "tbl_processes.id in (${args["ids"]})"
                if (args.containsKey("process_name")) where += "process_name LIKE '%${args["process_name"]}%'"
                if (args.containsKey("process_status")) where += "process_status = '${args["process_status"]}'"
                if (args.containsKey("process_order")) where += "process_order=${args["process_order"]}"
                if (args.containsKey("process_priority")) where += "process_priority=${args["process_priority"]}"
                if (args.containsKey("process_command")) where += "process_command LIKE '%${args["process_command"]}%'"
                if (args.containsKey("process_args")) where += "process_args LIKE '%${args["process_args"]}%'"
                if (args.containsKey("process_description")) where += "process_description LIKE '%${args["process_description"]}%'"
                if (args.containsKey("song_id")) where += "song_id=${args["song_id"]}"
                if (args.containsKey("process_type")) where += "process_type = '${args["process_type"]}'"
                if (args.containsKey("process_prioritet")) where += "process_prioritet = '${args["process_prioritet"]}'"
                if (args.containsKey("filter_notail")) where += "process_command <> 'tail'"
                if (args.containsKey("thread_id")) where += "thread_id=${args["thread_id"]}"

                if (where.isNotEmpty()) sql += " WHERE ${where.joinToString(" AND ")}"

//                println(sql)

                rs = statement.executeQuery(sql)
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
                    result.add(process)
                }
                result.sort()

                if (args.containsKey("filter_limit")) {
                    val limit = args["filter_limit"]?.toInt() ?: return result
                    val resultLimit: MutableList<KaraokeProcess> = mutableListOf()
                    for (i in 0 until limit) {
                        resultLimit.add(result[i])
                    }
                    return resultLimit
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

        /**
         * Есть ли у песни [songId] незавершённое задание в очереди (`WAITING` или `WORKING`).
         * Используется для блокировки переименования файла песни в SongEdit, пока над ней идёт
         * фоновая обработка (specs/124-filename-sanitization-rename, FR-013) — иначе
         * каскадное переименование могло бы переименовать файл, который в этот момент читает/пишет
         * уже запущенный процесс (например, Demucs).
         *
         * @see archive/archive/docs/features/async-process-queue.md
         */
        fun hasActiveProcess(
            songId: Long,
            database: KaraokeConnection,
        ): Boolean =
            loadList(args = mapOf("song_id" to songId.toString()), database = database)
                .any { it.status == KaraokeProcessStatuses.WAITING.toString() || it.status == KaraokeProcessStatuses.WORKING.toString() }

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

            var wasWorking = false
            existedProcesses.forEach { existedProcess ->
                if (existedProcess.status != KaraokeProcessStatuses.WORKING.name) {
                    delete(existedProcess.id, song.database)
                } else {
                    wasWorking = true
                }
            }
            if (wasWorking) return 0

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

            val separatedProcesses = separate(karaokeProcess)

            return createDbInstance(separatedProcesses)[0]?.id ?: 0
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
                val childProcess = KaraokeProcess(parentProcess.database)
                childProcess.name = parentProcess.name
                childProcess.status = parentProcess.status
                childProcess.order = parentProcess.order
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
                result.add(childProcess)
            }

            return result
        }
    }
}
