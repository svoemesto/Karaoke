package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.SettingField
import com.svoemesto.karaokeapp.model.Settings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.Serializable
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.concurrent.TimeUnit

class KaraokeProcess : Serializable, Comparable<KaraokeProcess>  {
    var id: Int = 0
    var name: String = "Process name"
    var status: String = KaraokeProcessStatuses.CREATING.name
    var order: Int = -1
    var priority: Int = 1
    var command: String = ""
    var args: List<List<String>> = mutableListOf(mutableListOf())
    var description: String = "description"
    var settingsId: Int = 0
    var type: String = KaraokeProcessTypes.NONE.name
    var start: Timestamp? = null
    var end: Timestamp? = null

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

    val percentage: Int get() {
        return when (status) {
            KaraokeProcessStatuses.DONE.name -> {
                100
            }
            KaraokeProcessStatuses.WORKING.name -> {
                val percentString = KaraokeProcessWorker.getPercentage(this)
                if (percentString == "---") {
                    0
                } else {
                    percentString.toInt()
                }
            }
            else -> {0}
        }
    }

    val percentageStr: String get() {
        val perc = percentage
        return if (perc == 0) {
            ""
        } else {
            "${perc} %"
        }
    }

    val timePassedMs: Long get() {
        if (start == null) return -1L
        return when (status) {
            KaraokeProcessStatuses.DONE.name -> {
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
        if (start == null || perc == 0) return -1L
        return when (status) {
            KaraokeProcessStatuses.WORKING.name -> {
                val passed = timePassedMs
                val fullMs = passed * 100 / perc
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

        Class.forName("org.postgresql.Driver")
        val connection = DriverManager.getConnection(CONNECTION_URL, CONNECTION_USER, CONNECTION_PASSWORD)
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
                "process_end = ? " +
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
        ps.setInt(index, id)
        ps.executeUpdate()
        ps.close()
        connection.close()

        updateStatusProcessSettings()

    }

    fun updateStatusProcessSettings() {
        if (settingsId != 0) {
            val settings = Settings.loadFromDbById(settingsId.toLong())
            settings?.let {
                when (type) {
                    KaraokeProcessTypes.MELT_LYRICS.name -> {
                        if (settings.statusProcessLyrics != status) {
                            settings.statusProcessLyrics = status
                            if (settings.statusProcessLyrics == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessLyricsBt == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessKaraoke == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessKaraokeBt == KaraokeProcessStatuses.DONE.name &&
                                settings.idStatus < 6L) {
                                settings.fields[SettingField.ID_STATUS] = "6"
                            }
                            settings.saveToDb()
                        } else {}
                    }
                    KaraokeProcessTypes.MELT_LYRICS_BT.name -> {
                        if (settings.statusProcessLyricsBt != status) {
                            settings.statusProcessLyricsBt = status
                            if (settings.statusProcessLyrics == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessLyricsBt == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessKaraoke == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessKaraokeBt == KaraokeProcessStatuses.DONE.name &&
                                settings.idStatus < 6L) {
                                settings.fields[SettingField.ID_STATUS] = "6"
                            }
                            settings.saveToDb()
                        } else {}
                    }
                    KaraokeProcessTypes.MELT_KARAOKE.name -> {
                        if (settings.statusProcessKaraoke != status) {
                            settings.statusProcessKaraoke = status
                            if (settings.statusProcessLyrics == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessLyricsBt == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessKaraoke == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessKaraokeBt == KaraokeProcessStatuses.DONE.name &&
                                settings.idStatus < 6L) {
                                settings.fields[SettingField.ID_STATUS] = "6"
                            }
                            settings.saveToDb()
                        } else {}
                    }
                    KaraokeProcessTypes.MELT_KARAOKE_BT.name -> {
                        if (settings.statusProcessKaraokeBt != status) {
                            settings.statusProcessKaraokeBt = status
                            if (settings.statusProcessLyrics == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessLyricsBt == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessKaraoke == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessKaraokeBt == KaraokeProcessStatuses.DONE.name &&
                                settings.idStatus < 6L) {
                                settings.fields[SettingField.ID_STATUS] = "6"
                            }
                            settings.saveToDb()
                        } else {}
                    }
                    KaraokeProcessTypes.MELT_CHORDS.name -> {
                        if (settings.statusProcessChords != status) {
                            settings.statusProcessChords = status
                            if (settings.statusProcessLyrics == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessLyricsBt == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessKaraoke == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessKaraokeBt == KaraokeProcessStatuses.DONE.name &&
                                settings.idStatus < 6L) {
                                settings.fields[SettingField.ID_STATUS] = "6"
                            }
                            settings.saveToDb()
                        } else {}
                    }
                    KaraokeProcessTypes.MELT_CHORDS_BT.name -> {
                        if (settings.statusProcessChordsBt != status) {
                            settings.statusProcessChordsBt = status
                            if (settings.statusProcessLyrics == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessLyricsBt == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessKaraoke == KaraokeProcessStatuses.DONE.name &&
                                settings.statusProcessKaraokeBt == KaraokeProcessStatuses.DONE.name &&
                                settings.idStatus < 6L) {
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

        fun convertJsonToArgs(json: String): List<List<String>> {
            return Json.decodeFromString(ListSerializer(ListSerializer(String.serializer())), json)
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
                        "process_end" +
                        ") VALUES(" +
                        "'${process.name.replace("'","''")}', " +
                        "'${process.status}', " +
                        "${process.order}, " +
                        "${process.priority}, " +
                        "'${process.command}', " +
                        "'${process.argsJson}', " +
                        "'${process.description}', " +
                        "${process.settingsId}, " +
                        "'${process.type}', " +
                        "${process.start}, " +
                        "${process.end}" +
                ")"

            Class.forName("org.postgresql.Driver")
            val connection = DriverManager.getConnection(CONNECTION_URL, CONNECTION_USER, CONNECTION_PASSWORD)
            val ps = connection.prepareStatement(sql)
            ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS)
            val rs = ps.generatedKeys

            val result = if (rs.next()) {
                process.id = rs.getInt(1)
                process
            } else null

            ps.close()
            connection.close()

            return result

        }

        fun loadList(args: Map<String, String> = emptyMap()): List<KaraokeProcess> {

            Class.forName("org.postgresql.Driver")
            val connection = DriverManager.getConnection(CONNECTION_URL, CONNECTION_USER, CONNECTION_PASSWORD)
            var statement: Statement? = null
            var rs: ResultSet? = null
            var sql: String
            val where: MutableList<String> = mutableListOf()

            try {
                statement = connection.createStatement()
                sql = "SELECT tbl_processes.*" +
                        " FROM tbl_processes"
                if (args.containsKey("id")) where += "id=${args["id"]}"
                if (args.containsKey("process_name")) where += "process_name LIKE '%${args["process_name"]}%'"
                if (args.containsKey("process_status")) where += "process_status LIKE '%${args["process_status"]}%'"
                if (args.containsKey("process_order")) where += "process_order=${args["process_order"]}"
                if (args.containsKey("process_priority")) where += "process_priority=${args["process_priority"]}"
                if (args.containsKey("process_command")) where += "process_command LIKE '%${args["process_command"]}%'"
                if (args.containsKey("process_args")) where += "process_args LIKE '%${args["process_args"]}%'"
                if (args.containsKey("process_description")) where += "process_description LIKE '%${args["process_description"]}%'"
                if (args.containsKey("settings_id")) where += "settings_id=${args["settings_id"]}"
                if (args.containsKey("process_type")) where += "process_type LIKE '%${args["process_type"]}%'"

                if (where.size > 0) sql += " WHERE ${where.joinToString(" AND ")}"

//                println(sql)

                rs = statement.executeQuery(sql)
                val result: MutableList<KaraokeProcess> = mutableListOf()
                while (rs.next()) {
                    val process = KaraokeProcess()

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
                    result.add(process)

                }
                result.sort()
                return result
            } catch (e: SQLException) {
                e.printStackTrace()
            } finally {
                try {
                    rs?.close() // close result set
                    statement?.close() // close statement
                    connection?.close()
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return emptyList()
        }

        fun delete(id: Int) {

            Class.forName("org.postgresql.Driver")
            val connection = DriverManager.getConnection(CONNECTION_URL, CONNECTION_USER, CONNECTION_PASSWORD)
            val sql = "DELETE FROM tbl_processes WHERE id = ?"
            val ps = connection.prepareStatement(sql)
            var index = 1
            ps.setInt(index, id)
            ps.executeUpdate()
            ps.close()
            connection.close()

        }

        fun load(id: Long): KaraokeProcess? {

            return loadList(mapOf(Pair("id", id.toString()))).firstOrNull()

        }

        fun getProcessToStart(): KaraokeProcess? {

            return loadList(mapOf(Pair("process_status", KaraokeProcessStatuses.WAITING.name))).firstOrNull()

        }

        fun createProcess(settings: Settings, action: KaraokeProcessTypes, doWait: Boolean = false, prior: Int = 1): Int {

            // Находим есть ли уже такой процесс. Если нет - создаём. Если есть и не в статусе "в работе" - пересоздаём

            val existedProcess = loadList(
                mapOf(
                    Pair("settings_id", settings.id.toString()),
                    Pair("process_type", action.name),
                )
            ).firstOrNull()

            existedProcess?.let {
                println(existedProcess.id)
                if (existedProcess.status != KaraokeProcessStatuses.WORKING.name) {
                    delete(existedProcess.id)
                } else {
                    return 0
                }
            }

            val karaokeProcess = KaraokeProcess()
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
                        description = "Кодирование LYRICS"
                        args = listOf(
                            listOf(
                                "melt",
                                "-progress",
                                "${settings.rootFolder}/done_projects/${settings.fileName} [lyrics].mlt"
                            )
                        )
                    }
                    KaraokeProcessTypes.MELT_KARAOKE -> {
                        description = "Кодирование KARAOKE"
                        args = listOf(
                            listOf(
                                "melt",
                                "-progress",
                                "${settings.rootFolder}/done_projects/${settings.fileName} [karaoke].mlt"
                            )
                        )
                    }
                    KaraokeProcessTypes.MELT_CHORDS -> {
                        description = "Кодирование CHORDS"
                        args = listOf(
                            listOf(
                                "melt",
                                "-progress",
                                "${settings.rootFolder}/done_projects/${settings.fileName} [chords].mlt"
                            )
                        )
                    }
                    KaraokeProcessTypes.MELT_LYRICS_BT -> {
                        description = "Кодирование LYRICS_BT"
                        args = listOf(
                            listOf(
                                "melt",
                                "-progress",
                                "${settings.rootFolder}/done_projects/${settings.fileName} [lyrics] bluetooth.mlt"
                            )
                        )
                    }
                    KaraokeProcessTypes.MELT_KARAOKE_BT -> {
                        description = "Кодирование KARAOKE_BT"
                        args = listOf(
                            listOf(
                                "melt",
                                "-progress",
                                "${settings.rootFolder}/done_projects/${settings.fileName} [karaoke] bluetooth.mlt"
                            )
                        )
                    }
                    KaraokeProcessTypes.MELT_CHORDS_BT -> {
                        description = "Кодирование CHORDS_BT"
                        args = listOf(
                            listOf(
                                "melt",
                                "-progress",
                                "${settings.rootFolder}/done_projects/${settings.fileName} [chords] bluetooth.mlt"
                            )
                        )
                    }
                    else -> {}
                }
            }

            karaokeProcess.updateStatusProcessSettings()

            return createDbInstance(karaokeProcess)?.id ?: 0

        }


    }
}