package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.services.SNS
import kotlinx.serialization.builtins.ListSerializer
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

data class ProcessesDTO(
    val id: Int,
    val name: String,
    val status: String,
    val order: Int,
    val priority: Int,
    val command: String,
    val args: List<List<String>>,
    val argsDescription: List<String>,
    val description: String,
    val settingsId: Int,
    val type: String,
    val start: Timestamp?,
    val end: Timestamp?,
    val prioritet: Int,
    val startStr: String,
    val endStr: String,
    val percentage: Double,
    val percentageStr: String,
    val timePassedMs: Long,
    val timePassedStr: String,
    val timeLeftMs: Long,
    val timeLeftStr: String
) : Serializable, Comparable<ProcessesDTO> {

    override fun compareTo(other: ProcessesDTO): Int {
        var result = priority.compareTo(other.priority)
        if (result != 0) return result
        result = order.compareTo(other.order)
        if (result != 0) return result
        return id.compareTo(other.id)

    }
}

//@Component
class KaraokeProcess(
    val database: KaraokeConnection = WORKING_DATABASE
) : Serializable, Comparable<KaraokeProcess>  {
    var id: Int = 0
    var name: String = "Process name"
    var status: String = KaraokeProcessStatuses.CREATING.name
    var order: Int = -1
    var priority: Int = 1
    var command: String = ""
    var args: List<List<String>> = mutableListOf(mutableListOf())
    var argsDescription: List<String> = mutableListOf()
    var description: String = "description"
    var settingsId: Int = 0
    var type: String = KaraokeProcessTypes.NONE.name
    var start: Timestamp? = null
    var end: Timestamp? = null
    var prioritet: Int = 0

    fun toDTO(): ProcessesDTO {
        return ProcessesDTO(
            id = id,
            name = name,
            status = status,
            order = order,
            priority = priority,
            command = command,
            args = args,
            argsDescription = argsDescription,
            description = description,
            settingsId = settingsId,
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
            timeLeftStr = timeLeftStr
        )
    }
    fun copy(): KaraokeProcess {
        val result = KaraokeProcess(database)
        result.id = id
        result.name = name
        result.status = status
        result.order = order
        result.priority = priority
        result.command = command
        result.args = args
        result.argsDescription = argsDescription
        result.description = description
        result.settingsId = settingsId
        result.type = type
        result.start = start
        result.end = end
        result.prioritet = prioritet
        return result
    }

    val argsJson: String get() {
        return Json.encodeToString(args)
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
            else -> {0.0}
        }
    }

    val percentageStr: String get() {
        val perc = percentage
        return if (perc == 0.0) {
            ""
        } else {
            "${perc} %"
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
            else -> {-1L}
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
            else -> {-1L}
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

    fun save() {

        val connection = database.getConnection()
        val sql = "UPDATE tbl_processes SET " +
                "process_name = ?, " +
                "process_status = ?, " +
                "process_order = ?, " +
                "process_priority = ?, " +
                "process_command = ?, " +
                "process_args = ?, " +
                "process_description = ?, " +
                "settings_id = ?, " +
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
                "process_time_left_str = ? " +
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
        ps.setString(index, description)
        index++
        ps.setInt(index, settingsId)
        index++
        ps.setString(index, type)
        index++
        if (start != null) ps.setTimestamp(index, start!!) else  ps.setNull(index, 0)
        index++
        if (end != null) ps.setTimestamp(index, end!!) else  ps.setNull(index, 0)
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
        ps.setInt(index, id)
        ps.executeUpdate()
        ps.close()

        val messageRecordChange = SseNotification.recordChange(
            RecordChangeMessage(
                tableName = "tbl_processes",
                recordId = id.toLong(),
                diffs = emptyList(),
                databaseName = database.name,
                record = this.toDTO()
            )
        )

        updateStatusProcessSettings(database)
        SNS.send(messageRecordChange)

        if (status == KaraokeProcessStatuses.DONE.name) {
            delete(id, database)
        }

//        val controller = ApplicationContextProvider.getCurrentApplicationContext().getBean(MainController::class.java)
//        controller.processesUpdate(id.toLong())

    }

    fun updateStatusProcessSettings(database: KaraokeConnection) {
        if (settingsId != 0) {
            val settings = Settings.loadFromDbById(settingsId.toLong(), database)
            settings?.let {
                when (type) {
                    KaraokeProcessTypes.MELT_LYRICS.name -> {
                        if (settings.statusProcessLyrics != status) {
                            settings.statusProcessLyrics = status
                            if (settings.statusProcessLyrics == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessKaraoke == KaraokeProcessStatuses.DONE.name &&
                                settings.idStatus == 4L) {
                                settings.fields[SettingField.ID_STATUS] = "6"
                            }
                            settings.saveToDb()
                        } else {}
                    }

                    KaraokeProcessTypes.MELT_KARAOKE.name -> {
                        if (settings.statusProcessKaraoke != status) {
                            settings.statusProcessKaraoke = status
                            if (settings.statusProcessLyrics == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessKaraoke == KaraokeProcessStatuses.DONE.name &&
                                settings.idStatus == 4L) {
                                settings.fields[SettingField.ID_STATUS] = "6"
                            }
                            settings.saveToDb()
                        } else {}
                    }

                    KaraokeProcessTypes.MELT_CHORDS.name -> {
                        if (settings.statusProcessChords != status) {
                            settings.statusProcessChords = status
                            if (settings.statusProcessLyrics == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessKaraoke == KaraokeProcessStatuses.DONE.name &&
                                settings.idStatus == 4L) {
                                settings.fields[SettingField.ID_STATUS] = "6"
                            }
                            settings.saveToDb()
                        } else {}
                    }

                    else -> {}
                }
            }
        }
    }

    companion object {

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
            }
            return result
        }

        fun getLastUpdated(lastTime: Long? = null, database: KaraokeConnection): List<Int> {
            if (lastTime == null) return emptyList()

            val connection = database.getConnection()
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

        fun convertJsonToArgs(json: String): List<List<String>> {
            return Json.decodeFromString(ListSerializer(ListSerializer(String.serializer())), json)
        }

        fun createDbInstance(processes: List<KaraokeProcess>): List<KaraokeProcess?> {
            val result: MutableList<KaraokeProcess?> = mutableListOf()
            processes.forEach { process ->
                result.add(createDbInstance(process))
            }
            return result
        }

        fun createDbInstance(process: KaraokeProcess) : KaraokeProcess? {
            val sql =
                "INSERT INTO tbl_processes (" +
                        "process_name, " +
                        "process_status, " +
                        "process_order, " +
                        "process_priority, " +
                        "process_command, " +
                        "process_args, " +
                        "process_description, " +
                        "settings_id, " +
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
                        "process_time_left_str " +
                        ") VALUES(" +
                        "'${process.name.rightFileName()}', " +
                        "'${process.status}', " +
                        "${process.order}, " +
                        "${process.priority}, " +
                        "'${process.command}', " +
                        "'${process.argsJson}', " +
                        "'${process.description}', " +
                        "${process.settingsId}, " +
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
                        "'${process.timeLeftStr}' " +
                ")"

            val connection = process.database.getConnection()
            val ps = connection.prepareStatement(sql)
            ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS)
            val rs = ps.generatedKeys

            val result = if (rs.next()) {
                process.id = rs.getInt(1)
                process
            } else null

            ps.close()

            result?.let {
                val messageRecordAdd = SseNotification.recordAdd(
                    RecordAddMessage(
                        tableName = "tbl_processes",
                        recordId = result.id.toLong(),
                        databaseName = process.database.name,
                        record = result.toDTO()
                    )
                )
                SNS.send(messageRecordAdd)
            }

            return result

        }

        fun loadList(args: Map<String, String> = emptyMap(), database: KaraokeConnection): List<KaraokeProcess> {

            val connection = database.getConnection()
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
                if (args.containsKey("settings_id")) where += "settings_id=${args["settings_id"]}"
                if (args.containsKey("process_type")) where += "process_type = '${args["process_type"]}'"
                if (args.containsKey("process_prioritet")) where += "process_prioritet = '${args["process_prioritet"]}'"
                if (args.containsKey("filter_notail")) where += "process_command <> 'tail'"


                if (where.size > 0) sql += " WHERE ${where.joinToString(" AND ")}"


//                println(sql)

                rs = statement.executeQuery(sql)
                val result: MutableList<KaraokeProcess> = mutableListOf()
                while (rs.next()) {
                    val process = KaraokeProcess(database)

                    process.id = rs.getInt("id")
                    process.name = rs.getString("process_name")
                    process.status = rs.getString("process_status")
                    process.order = rs.getInt("process_order")
                    process.priority = rs.getInt("process_priority")
                    process.command = rs.getString("process_command")
                    process.args = convertJsonToArgs(rs.getString("process_args"))
                    process.description = rs.getString("process_description")
                    process.settingsId = rs.getInt("settings_id")
                    process.type = rs.getString("process_type")
                    process.start = rs.getTimestamp("process_start")
                    process.end = rs.getTimestamp("process_end")
                    process.prioritet = rs.getInt("process_prioritet")
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

        fun delete(id: Int, database: KaraokeConnection) {

            val connection = database.getConnection()
            val sql = "DELETE FROM tbl_processes WHERE id = ?"
            val ps = connection.prepareStatement(sql)
            var index = 1
            ps.setInt(index, id)
            ps.executeUpdate()
            ps.close()

            val messageRecordDelete = SseNotification.recordDelete(
                RecordDeleteMessage(
                    recordId = id.toLong(),
                    tableName = "tbl_processes",
                    databaseName = database.name
                )
            )
            SNS.send(messageRecordDelete)

        }

        fun load(id: Long, database: KaraokeConnection): KaraokeProcess? {

            return loadList(mapOf(Pair("id", id.toString())), database).firstOrNull()

        }

        fun getProcessToStart(database: KaraokeConnection): KaraokeProcess? {

            return loadList(mapOf(Pair("process_status", KaraokeProcessStatuses.WAITING.name)), database).firstOrNull()

        }

        fun createProcess(settings: Settings, action: KaraokeProcessTypes, doWait: Boolean = false, prior: Int = 1): Int {

            // Находим есть ли уже такой процесс. Если нет - создаём. Если есть и не в статусе "в работе" - пересоздаём

            val existedProcesses = loadList(
                mapOf(
                    Pair("settings_id", settings.id.toString()),
                    Pair("process_type", action.name),
                ), settings.database
            )

            var wasWorking = false
            existedProcesses.forEach { existedProcess ->
                println(existedProcess.id)
                if (existedProcess.status != KaraokeProcessStatuses.WORKING.name) {
                    delete(existedProcess.id, settings.database)
                } else {
                    wasWorking = true
                }
            }
            if (wasWorking) return 0


            val karaokeProcess = KaraokeProcess(settings.database)
            with(karaokeProcess) {
                name = "[${settings.author}] - [${settings.album}] - «${settings.songName}»"
                status = if (doWait) KaraokeProcessStatuses.WAITING.name else KaraokeProcessStatuses.CREATING.name
                order = -1
                priority = prior
                command = ""
                type = action.name
                settingsId = settings.id.toInt()

                when (action) {
                    KaraokeProcessTypes.MELT_LYRICS -> {
                        if (!File(settings.pathToSymlinkFolder).exists()) Files.createDirectories(Path(settings.pathToSymlinkFolder))
                        val songKaraoke = Song(settings, SongVersion.KARAOKE, true)
                        val songLyrics = Song(settings, SongVersion.LYRICS, true)
                        val songKaraokeMp4 = songKaraoke.getOutputFilename(SongOutputFile.VIDEO).rightFileName()
                        val songKaraokeTxt = songKaraoke.getOutputFilename(SongOutputFile.DESCRIPTION).rightFileName()
                        val songLyricsMp4 = songLyrics.getOutputFilename(SongOutputFile.VIDEO).rightFileName()
                        val songKaraokeMp4Symlink = "${settings.pathToSymlinkFolder}/${File(songKaraokeMp4).name}".rightFileName()
                        val songKaraokeTxtSymlink = "${settings.pathToSymlinkFolder}/${File(songKaraokeTxt).name}".rightFileName()
                        val songLyricsMp4Symlink = "${settings.pathToSymlinkFolder}/${File(songLyricsMp4).name}".rightFileName()

                        description = "Кодирование LYRICS"
                        prioritet = 19
                        args = listOf(
                            listOf(
                                "melt",
                                "-progress",
                                "${settings.rootFolder.rightFileName()}/done_projects/${if (!settings.fileName.startsWith (settings.year.toString())) "${settings.year} " else ""}${settings.fileName.rightFileName()} [lyrics].mlt"
                            ),
                            listOf("mkdir", "-p", settings.pathToStoreFolderLyrics),
                            listOf("cp", settings.pathToFileLyrics, settings.pathToStoreFileLyrics),
                            listOf("mkdir", "-p", settings.pathToFolder720Lyrics),
                            listOf("rm", settings.pathToFile720Lyrics),
                            listOf(
                                "ffmpeg",
                                "-i",
                                settings.pathToFileLyrics,
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
                                settings.pathToFile720Lyrics,
                                "-y"
                            ),
                            listOf("rm", settings.fileAbsolutePathSymlink.rightFileName().wrapInQuotes()),
                            listOf("rm", settings.newNoStemNameFlacSymlink.rightFileName().wrapInQuotes()),
                            listOf("rm", settings.vocalsNameFlacSymlink.rightFileName().wrapInQuotes()),
                            listOf("rm", songKaraokeMp4Symlink.rightFileName().wrapInQuotes()),
                            listOf("rm", songKaraokeTxtSymlink.rightFileName().wrapInQuotes()),
                            listOf("rm", songLyricsMp4Symlink.rightFileName().wrapInQuotes()),
                            listOf("ln", "-s", settings.fileAbsolutePath.rightFileName().wrapInQuotes(), settings.fileAbsolutePathSymlink.rightFileName().wrapInQuotes()),
                            listOf("ln", "-s", settings.newNoStemNameFlac.rightFileName().wrapInQuotes(), settings.newNoStemNameFlacSymlink.rightFileName().wrapInQuotes()),
                            listOf("ln", "-s", settings.vocalsNameFlac.rightFileName().wrapInQuotes(), settings.vocalsNameFlacSymlink.rightFileName().wrapInQuotes()),
                            listOf("ln", "-s", songKaraokeMp4.wrapInQuotes(), songKaraokeMp4Symlink.wrapInQuotes()),
                            listOf("ln", "-s", songKaraokeTxt.wrapInQuotes(), songKaraokeTxtSymlink.wrapInQuotes()),
                            listOf("ln", "-s", songLyricsMp4.wrapInQuotes(), songLyricsMp4Symlink.wrapInQuotes())
                        )
                        argsDescription = listOf(
                            "Кодирование LYRICS",
                            "Create folder (if need)",
                            "Copy LYRICS to store",
                            "Create folder (if need)",
                            "Delete 720P LYRICS",
                            "720P LYRICS",
                            "Delete SYMLINK flac song",
                            "Delete SYMLINK flac music",
                            "Delete SYMLINK flac vocal",
                            "Delete SYMLINK KARAOKE MP4",
                            "Delete SYMLINK KARAOKE TXT",
                            "Delete SYMLINK LYRICS MP4",
                            "Create SYMLINK flac song",
                            "Create SYMLINK flac music",
                            "Create SYMLINK flac vocal",
                            "Create SYMLINK KARAOKE MP4",
                            "Create SYMLINK KARAOKE TXT",
                            "Create SYMLINK LYRICS MP4"
                        )
                    }
                    KaraokeProcessTypes.MELT_KARAOKE -> {
                        if (!File(settings.pathToSymlinkFolder).exists()) Files.createDirectories(Path(settings.pathToSymlinkFolder))
                        val songKaraoke = Song(settings, SongVersion.KARAOKE, true)
                        val songLyrics = Song(settings, SongVersion.LYRICS, true)
                        val songKaraokeMp4 = songKaraoke.getOutputFilename(SongOutputFile.VIDEO).rightFileName()
                        val songKaraokeTxt = songKaraoke.getOutputFilename(SongOutputFile.DESCRIPTION).rightFileName()
                        val songLyricsMp4 = songLyrics.getOutputFilename(SongOutputFile.VIDEO).rightFileName()
                        val songKaraokeMp4Symlink = "${settings.pathToSymlinkFolder}/${File(songKaraokeMp4).name}".rightFileName()
                        val songKaraokeTxtSymlink = "${settings.pathToSymlinkFolder}/${File(songKaraokeTxt).name}".rightFileName()
                        val songLyricsMp4Symlink = "${settings.pathToSymlinkFolder}/${File(songLyricsMp4).name}".rightFileName()

                        description = "Кодирование KARAOKE"
                        prioritet = 19
                        args = listOf(
                            listOf(
                                "melt",
                                "-progress",
                                "${settings.rootFolder.rightFileName()}/done_projects/${if (!settings.fileName.startsWith (settings.year.toString())) "${settings.year} " else ""}${settings.fileName.rightFileName()} [karaoke].mlt"
                            ),
                            listOf("mkdir", "-p", settings.pathToStoreFolderKaraoke),
                            listOf("cp", settings.pathToFileKaraoke, settings.pathToStoreFileKaraoke),
                            listOf("mkdir", "-p", settings.pathToFolder720Karaoke),
                            listOf("rm", settings.pathToFile720Karaoke),
                            listOf(
                                "ffmpeg",
                                "-i",
                                settings.pathToFileKaraoke,
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
                                settings.pathToFile720Karaoke,
                                "-y"
                            ),
                            listOf("rm", settings.fileAbsolutePathSymlink.rightFileName().wrapInQuotes()),
                            listOf("rm", settings.newNoStemNameFlacSymlink.rightFileName().wrapInQuotes()),
                            listOf("rm", settings.vocalsNameFlacSymlink.rightFileName().wrapInQuotes()),
                            listOf("rm", songKaraokeMp4Symlink.rightFileName().wrapInQuotes()),
                            listOf("rm", songKaraokeTxtSymlink.rightFileName().wrapInQuotes()),
                            listOf("rm", songLyricsMp4Symlink.rightFileName().wrapInQuotes()),
                            listOf("ln", "-s", settings.fileAbsolutePath.rightFileName().wrapInQuotes(), settings.fileAbsolutePathSymlink.rightFileName().wrapInQuotes()),
                            listOf("ln", "-s", settings.newNoStemNameFlac.rightFileName().wrapInQuotes(), settings.newNoStemNameFlacSymlink.rightFileName().wrapInQuotes()),
                            listOf("ln", "-s", settings.vocalsNameFlac.rightFileName().wrapInQuotes(), settings.vocalsNameFlacSymlink.rightFileName().wrapInQuotes()),
                            listOf("ln", "-s", songKaraokeMp4.wrapInQuotes(), songKaraokeMp4Symlink.wrapInQuotes()),
                            listOf("ln", "-s", songKaraokeTxt.wrapInQuotes(), songKaraokeTxtSymlink.wrapInQuotes()),
                            listOf("ln", "-s", songLyricsMp4.wrapInQuotes(), songLyricsMp4Symlink.wrapInQuotes())
                        )
                        argsDescription = listOf(
                            "Кодирование KARAOKE",
                            "Create folder (if need)",
                            "Copy KARAOKE to store",
                            "Create folder (if need)",
                            "Delete 720P KARAOKE",
                            "720P KARAOKE",
                            "Delete SYMLINK flac song",
                            "Delete SYMLINK flac music",
                            "Delete SYMLINK flac vocal",
                            "Delete SYMLINK KARAOKE MP4",
                            "Delete SYMLINK KARAOKE TXT",
                            "Delete SYMLINK LYRICS MP4",
                            "Create SYMLINK flac song",
                            "Create SYMLINK flac music",
                            "Create SYMLINK flac vocal",
                            "Create SYMLINK KARAOKE MP4",
                            "Create SYMLINK KARAOKE TXT",
                            "Create SYMLINK LYRICS MP4"
                        )
                    }
                    KaraokeProcessTypes.MELT_CHORDS -> {
                        description = "Кодирование CHORDS"
                        prioritet = 19
                        args = listOf(
                            listOf(
                                "melt",
                                "-progress",
                                "${settings.rootFolder.rightFileName()}/done_projects/${if (!settings.fileName.startsWith (settings.year.toString())) "${settings.year} " else ""}${settings.fileName.rightFileName()} [chords].mlt"
                            )
                        )
                    }

                    KaraokeProcessTypes.DEMUCS2 -> {
                        description = "Демукс 2"
                        args = listOf(
                            listOf(
                                "python3",
                                "-m",
                                "demucs",
                                "-n",
                                DEMUCS_MODEL_NAME,
                                "-d",
//                                "cuda",
                                "cpu",
                                "--filename",
                                "{track}-{stem}.{ext}",
                                "--two-stems=${settings.separatedStem}",
                                "-o",
                                settings.rootFolder.rightFileName(),
                                settings.fileAbsolutePath.rightFileName()
                            ),
                            listOf("mv", settings.oldNoStemNameWav.rightFileName(), settings.newNoStemNameWav.rightFileName()),
                            listOf("ffmpeg", "-i", settings.newNoStemNameWav.rightFileName(), "-compression_level", "8", settings.newNoStemNameFlac.rightFileName(), "-y"),
                            listOf("rm", settings.newNoStemNameWav.rightFileName()),
                            listOf("ffmpeg", "-i", settings.vocalsNameWav.rightFileName(), "-compression_level", "8", settings.vocalsNameFlac.rightFileName(), "-y"),
                            listOf("rm", settings.vocalsNameWav.rightFileName())
                        )
                        argsDescription = listOf(
                            "Демукс 2 - demucs",
                            "Демукс 2 - rename music file",
                            "Демукс 2 - music to flac",
                            "Демукс 2 - del music wav",
                            "Демукс 2 - vocal to flac",
                            "Демукс 2 - del vocal wav"
                        )
                    }
                    KaraokeProcessTypes.DEMUCS5 -> {
                        description = "Демукс 5"
                        args = listOf(
                            listOf(
                                "python3",
                                "-m",
                                "demucs",
                                "-n",
                                DEMUCS_MODEL_NAME,
                                "-d",
//                                "cuda",
                                "cpu",
                                "--filename",
                                "{track}-{stem}.{ext}",
                                "--two-stems=${settings.separatedStem}",
                                "-o",
                                settings.rootFolder.rightFileName(),
                                settings.fileAbsolutePath.rightFileName()
                            ),
                            listOf("mv", settings.oldNoStemNameWav.rightFileName(), settings.newNoStemNameWav.rightFileName()),
                            listOf("ffmpeg", "-i", settings.newNoStemNameWav.rightFileName(), "-compression_level", "8", settings.newNoStemNameFlac.rightFileName(), "-y"),
                            listOf("rm", settings.newNoStemNameWav.rightFileName()),
                            listOf("ffmpeg", "-i", settings.vocalsNameWav.rightFileName(), "-compression_level", "8", settings.vocalsNameFlac.rightFileName(), "-y"),
                            listOf("rm", settings.vocalsNameWav.rightFileName()),
                            listOf(
                                "python3",
                                "-m",
                                "demucs",
                                "-n",
                                DEMUCS_MODEL_NAME,
                                "-d",
//                                "cuda",
                                "cpu",
                                "--filename",
                                "{track}-{stem}.{ext}",
                                "-o",
                                settings.rootFolder.rightFileName(),
                                settings.fileAbsolutePath.rightFileName()
                            ),
                            listOf("ffmpeg", "-i", settings.drumsNameWav.rightFileName(), "-compression_level", "8", settings.drumsNameFlac.rightFileName(), "-y"),
                            listOf("rm", settings.drumsNameWav.rightFileName()),
                            listOf("ffmpeg", "-i", settings.bassNameWav.rightFileName(), "-compression_level", "8", settings.bassNameFlac.rightFileName(), "-y"),
                            listOf("rm", settings.bassNameWav.rightFileName()),
                            listOf("ffmpeg", "-i", settings.guitarsNameWav.rightFileName(), "-compression_level", "8", settings.guitarsNameFlac.rightFileName(), "-y"),
                            listOf("rm", settings.guitarsNameWav.rightFileName()),
                            listOf("ffmpeg", "-i", settings.otherNameWav.rightFileName(), "-compression_level", "8", settings.otherNameFlac.rightFileName(), "-y"),
                            listOf("rm", settings.otherNameWav.rightFileName()),
                            listOf("ffmpeg", "-i", settings.vocalsNameWav.rightFileName(), "-compression_level", "8", settings.vocalsNameFlac.rightFileName(), "-y"),
                            listOf("rm", settings.vocalsNameWav.rightFileName())
                        )
                        argsDescription = listOf(
                            "Демукс 5 - demucs 2",
                            "Демукс 5 - rename music file",
                            "Демукс 5 - music to flac",
                            "Демукс 5 - del music wav",
                            "Демукс 5 - vocal to flac",
                            "Демукс 5 - del vocal wav",
                            "Демукс 5 - demucs 5",
                            "Демукс 5 - drums to flac",
                            "Демукс 5 - del drums wav",
                            "Демукс 5 - bass to flac",
                            "Демукс 5 - del bass wav",
                            "Демукс 5 - guitars to flac",
                            "Демукс 5 - del guitars wav",
                            "Демукс 5 - other to flac",
                            "Демукс 5 - del other wav",
                            "Демукс 5 - vocal to flac",
                            "Демукс 5 - del vocal wav"
                        )
                    }
                    KaraokeProcessTypes.SHEETSAGE -> {
                        val srcWav = "/home/nsa/Documents/sheetsage/source.wav"
                        val resultFolder = "/home/nsa/Karaoke/output/output"
                        val resultPdf = "$resultFolder/output.pdf"
                        val resultMidi = "$resultFolder/output.midi"
                        val resultLy = "$resultFolder/output.ly"
                        val resultBeattimes = "$resultFolder/beattimes"
                        description = "SHEETSAGE"
                        args = listOf(
                            listOf("mkdir", "-p", resultFolder),
                            listOf("rm", "-f", srcWav, resultPdf, resultMidi, resultLy, resultBeattimes),
                            listOf("ffmpeg", "-i", settings.fileAbsolutePath.rightFileName(), "-compression_level", "8", srcWav, "-y"),
                            listOf("/home/nsa/sheetsage/sheetsage.sh", "-j", "-o", "output/output", srcWav),
                            listOf("mkdir", "-p", settings.pathToFolderSheetsage),
                            listOf("mv", resultPdf.rightFileName(), settings.pathToFileSheetsagePDF.rightFileName()),
                            listOf("mv", resultMidi.rightFileName(), settings.pathToFileSheetsageMIDI.rightFileName()),
                            listOf("mv", resultLy.rightFileName(), settings.pathToFileSheetsageLY.rightFileName()),
                            listOf("mv", resultBeattimes.rightFileName(), settings.pathToFileSheetsageBeattimes.rightFileName())
                        )
                        argsDescription = listOf(
                            "SHEETSAGE - Создание папки output",
                            "SHEETSAGE - Удаление старых файлов",
                            "SHEETSAGE - Декодирование FLAC в WAV",
                            "SHEETSAGE - Распознавание аккордов",
                            "SHEETSAGE - Создание папки sheetsage",
                            "SHEETSAGE - Копирование файла PDF",
                            "SHEETSAGE - Копирование файла MIDI",
                            "SHEETSAGE - Копирование файла LY",
                            "SHEETSAGE - Копирование файла Beattimes"
                        )
                    }
                    KaraokeProcessTypes.FF_720_KAR -> {
                        val destinationFolder = settings.pathToFolder720Karaoke
                        val sourceFile = settings.pathToFileKaraoke
                        val destinationFile = settings.pathToFile720Karaoke
                        if (File(destinationFile).exists()) return -1
                        if (!File(destinationFolder).exists()) Files.createDirectories(Path(destinationFolder))
                        description = "720P KARAOKE"
                        args = listOf(
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
                                "-y"
                            )
                        )
                    }
                    KaraokeProcessTypes.FF_720_LYR -> {

                        val destinationFolder = settings.pathToFolder720Lyrics
                        val sourceFile = settings.pathToFileLyrics
                        val destinationFile = settings.pathToFile720Lyrics
                        if (File(destinationFile).exists()) return -1
                        if (!File(destinationFolder).exists()) Files.createDirectories(Path(destinationFolder))

                        description = "720P LYRICS"
                        args = listOf(
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
                                "-y"
                            )
                        )
                    }
                    KaraokeProcessTypes.SYMLINK -> {
                        val songKaraoke = Song(settings, SongVersion.KARAOKE, true)
                        val songLyrics = Song(settings, SongVersion.LYRICS, true)
                        val songKaraokeMp4 = songKaraoke.getOutputFilename(SongOutputFile.VIDEO).rightFileName()
                        val songKaraokeTxt = songKaraoke.getOutputFilename(SongOutputFile.DESCRIPTION).rightFileName()
                        val songLyricsMp4 = songLyrics.getOutputFilename(SongOutputFile.VIDEO).rightFileName()
                        if (!File(settings.pathToSymlinkFolder).exists()) Files.createDirectories(Path(settings.pathToSymlinkFolder))
                        val songKaraokeMp4Symlink = "${settings.pathToSymlinkFolder}/${File(songKaraokeMp4).name}".rightFileName()
                        val songKaraokeTxtSymlink = "${settings.pathToSymlinkFolder}/${File(songKaraokeTxt).name}".rightFileName()
                        val songLyricsMp4Symlink = "${settings.pathToSymlinkFolder}/${File(songLyricsMp4).name}".rightFileName()

                        description = "SYMLINK"
                        args = listOf(
                            listOf("rm", "-f",
                                settings.fileAbsolutePathSymlink.rightFileName().wrapInQuotes(),
                                settings.newNoStemNameFlacSymlink.rightFileName().wrapInQuotes(),
                                settings.vocalsNameFlacSymlink.rightFileName().wrapInQuotes(),
                                songKaraokeMp4Symlink.rightFileName().wrapInQuotes(),
                                songKaraokeTxtSymlink.rightFileName().wrapInQuotes(),
                                songLyricsMp4Symlink.rightFileName().wrapInQuotes()
                            ),
                            listOf("ln", "-s", settings.fileAbsolutePath.rightFileName().wrapInQuotes(), settings.fileAbsolutePathSymlink.rightFileName().wrapInQuotes()),
                            listOf("ln", "-s", settings.newNoStemNameFlac.rightFileName().wrapInQuotes(), settings.newNoStemNameFlacSymlink.rightFileName().wrapInQuotes()),
                            listOf("ln", "-s", settings.vocalsNameFlac.rightFileName().wrapInQuotes(), settings.vocalsNameFlacSymlink.rightFileName().wrapInQuotes()),
                            listOf("ln", "-s", songKaraokeMp4.wrapInQuotes(), songKaraokeMp4Symlink.wrapInQuotes()),
                            listOf("ln", "-s", songKaraokeTxt.wrapInQuotes(), songKaraokeTxtSymlink.wrapInQuotes()),
                            listOf("ln", "-s", songLyricsMp4.wrapInQuotes(), songLyricsMp4Symlink.wrapInQuotes())
                        )
                        argsDescription = listOf(
                            "Delete old SYMLINKs",
                            "Create SYMLINK flac song",
                            "Create SYMLINK flac music",
                            "Create SYMLINK flac vocal",
                            "Create SYMLINK KARAOKE MP4",
                            "Create SYMLINK KARAOKE TXT",
                            "Create SYMLINK LYRICS MP4"
                        )
                    }
                    KaraokeProcessTypes.FF_MP3_KAR -> {
                        description = "MP3 KARAOKE"
                        args = listOf(
                            listOf("mkdir", "-p", settings.pathToFolderMP3Karaoke),
                            listOf("rm", settings.pathToFileMP3Karaoke),
                            listOf(
                                "ffmpeg",
                                "-i",
                                settings.newNoStemNameFlac.rightFileName(),
                                "-ab",
                                "320k",
                                "-map_metadata",
                                "0",
                                "-id3v2_version",
                                "3",
                                settings.pathToFileMP3Karaoke,
                                "-y"
                            )
                        )
                        argsDescription = listOf(
                            "Create folder (if need)",
                            "Delete MP3 KARAOKE",
                            "MP3 KARAOKE"
                        )
                    }
                    KaraokeProcessTypes.FF_MP3_LYR -> {
                        description = "MP3 LYRICS"
                        args = listOf(
                            listOf("mkdir", "-p", settings.pathToFolderMP3Lyrics),
                            listOf("rm", settings.pathToFileMP3Lyrics),
                            listOf(
                                "ffmpeg",
                                "-i",
                                settings.fileAbsolutePath.rightFileName(),
                                "-ab",
                                "320k",
                                "-map_metadata",
                                "0",
                                "-id3v2_version",
                                "3",
                                settings.pathToFileMP3Lyrics,
                                "-y"
                            )
                        )
                        argsDescription = listOf(
                            "Create folder (if need)",
                            "Delete MP3 LYRICS",
                            "MP3 LYRICS"
                        )
                    }
                    else -> {}
                }
            }

            karaokeProcess.updateStatusProcessSettings(settings.database)

            val separatedProcesses = separate(karaokeProcess)

            return createDbInstance(separatedProcesses)[0]?.id ?: 0

        }

        fun separate(parentProcess: KaraokeProcess): List<KaraokeProcess> {
            if (parentProcess.args.size == 1) return listOf(parentProcess)
            val result: MutableList<KaraokeProcess> = mutableListOf()

            parentProcess.args.forEachIndexed { index, childArgs ->
                println(childArgs)
                val desc = if (parentProcess.args.size == parentProcess.argsDescription.size) {
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
                childProcess.settingsId = parentProcess.settingsId
                childProcess.description = desc
                childProcess.prioritet = parentProcess.prioritet
                childProcess.args = listOf(childArgs)
                result.add(childProcess)
            }

            return result
        }

    }
}