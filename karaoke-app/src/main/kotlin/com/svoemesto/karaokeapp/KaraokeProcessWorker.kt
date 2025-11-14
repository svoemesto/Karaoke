package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SNS
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.sql.Timestamp
import java.time.Instant


class KaraokeProcessThread(val karaokeProcess: KaraokeProcess? = null, var percentage: String? = null): Thread() {

    override fun run() {
        super.run()
        if (karaokeProcess != null) {

            karaokeProcess.status = KaraokeProcessStatuses.WORKING.name
            karaokeProcess.start = Timestamp.from(Instant.now())
            karaokeProcess.save()

            if (karaokeProcess.args[0][0] == "runFunctionWithArgs") {

                runFunctionWithArgs(karaokeProcess.args[0])
                karaokeProcess.status = KaraokeProcessStatuses.DONE.name
                karaokeProcess.end = Timestamp.from(Instant.now())
                karaokeProcess.priority = 999
                karaokeProcess.save()

            } else {

                val regex = Regex("Current Frame:\\s+(\\d+), percentage:\\s+(\\d+)")
                val regexDuration = Regex("Duration:\\s+(\\d\\d:\\d\\d:\\d\\d\\.\\d\\d),")
                val regexCurrent = Regex("time=(\\d\\d:\\d\\d:\\d\\d\\.\\d\\d)")
                val regexPercentageSheetsage = Regex("^\\s{0,2}(\\d{1,3})%\\|")
                val args = karaokeProcess.args[0]
                val processBuilder = ProcessBuilder(args)
                processBuilder.redirectErrorStream(true)

                val process = processBuilder.start()
                if (process.isAlive) {
                    if (karaokeProcess.command != "tail" || karaokeProcess.args[0][0] !in KaraokeProcessWorker.argsIgnoredToLog) {
                        println("[${Timestamp.from(Instant.now())}] KaraokeProcessThread[${karaokeProcess.threadId}]: Установка приоритета задания: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}")
                    }
                    setProcessPriority(process.pid(), karaokeProcess.prioritet)
                }

                try {
                    if (karaokeProcess.command != "tail" || karaokeProcess.args[0][0] !in KaraokeProcessWorker.argsIgnoredToLog) {
                        println("[${Timestamp.from(Instant.now())}] KaraokeProcessThread[${karaokeProcess.threadId}]: Начинаем работу с заданием: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}")
                        KaraokeProcessWorker.sendCountWaitingMessage(KaraokeProcess.getCountWaiting(database = karaokeProcess.database))
                    }
                    val inputStream = process.inputStream
                    var duration: String? = null
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    var line: String? = reader.readLine()
                    var log = ""
                    var lastLine = ""
                    while (line != null) {
                        lastLine = line
                        log += "[${Timestamp.from(Instant.now())}] $line\n"
                        val matchResult = regex.find(line)
                        if (matchResult != null) {
//                        val currentFrame = matchResult.groupValues[1]
                            val percentage = matchResult.groupValues[2]
                            this.percentage = percentage
                        } else {
                            if (duration != null) {
                                val matchResultCurrent = regexCurrent.find(line)
                                if (matchResultCurrent != null) {
                                    val current = matchResultCurrent.groupValues[1]
                                    this.percentage = (((convertTimecodeToMilliseconds(current).toDouble() / convertTimecodeToMilliseconds(duration).toDouble()) * 10000).toInt().toDouble() / 100).toString()
                                }
                            } else {
                                val matchResultDuration = regexDuration.find(line)
                                if (matchResultDuration != null) {
                                    duration = matchResultDuration.groupValues[1]
                                } else {
                                    val matchResultPercentageSheetsage = regexPercentageSheetsage.find(line)
                                    if (matchResultPercentageSheetsage != null) {
                                        val percentage = matchResultPercentageSheetsage.groupValues[1]
                                        this.percentage = percentage
                                    }
                                }
                            }
                        }
                        line = reader.readLine()
                    }
                    if (karaokeProcess.command != "tail" || karaokeProcess.args[0][0] !in KaraokeProcessWorker.argsIgnoredToLog) {
                        println("[${Timestamp.from(Instant.now())}] KaraokeProcessThread[${karaokeProcess.threadId}]: Завершаем работу с заданием: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}")
                    }
                    if (log != "") {
                        if (karaokeProcess.command != "tail" || karaokeProcess.args[0][0] !in KaraokeProcessWorker.argsIgnoredToLog) {
                            println("[${Timestamp.from(Instant.now())}] KaraokeProcessThread[${karaokeProcess.threadId}]: Выводим лог задания: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}")
                        }
                        log = args.joinToString(" ") + "\n\n" + log
                        val logFileName = "$PATH_TO_LOGS/[${Timestamp.from(Instant.now())}] ${karaokeProcess.name} - ${karaokeProcess.description}.log".rightFileName()
                        try {
                            File(logFileName).writeText(log, Charsets.UTF_8)
                            runCommand(listOf("chmod", "666", logFileName))
                        } catch (e: Exception) {
                            println(e.message)
                        }
                    }

                    if (karaokeProcess.type == "SHEETSAGE" &&  lastLine == "NotImplementedError: Dynamic chunking not implemented. Try halving measures_per_chunk.") {
                        // Если процесс SHEETSAGE завершился ошибкой - создаём для этой же песни процесс SHEETSAGE2 с таким же приоритетом
                        val settings = Settings.loadFromDbById(id = karaokeProcess.settingsId.toLong(), database = WORKING_DATABASE, storageService = KSS_APP)
                        settings?.let {
                            KaraokeProcess.createProcess(
                                settings = settings,
                                action = KaraokeProcessTypes.SHEETSAGE2,
                                doWait = true,
                                prior = karaokeProcess.priority,
                                threadId = 0
                            )
                        }
                    }

                    if (karaokeProcess.command != "tail" || karaokeProcess.args[0][0] !in KaraokeProcessWorker.argsIgnoredToLog) {
                        println("[${Timestamp.from(Instant.now())}] KaraokeProcessThread[${karaokeProcess.threadId}]: DONE успешно завершенное задание: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}")
                    }
                    karaokeProcess.status = KaraokeProcessStatuses.DONE.name
                    karaokeProcess.end = Timestamp.from(Instant.now())
                    karaokeProcess.priority = 999
                    karaokeProcess.save()

//                if (karaokeProcess.type == KaraokeProcessTypes.DEMUCS2.name) {
//                    KaraokeProcess.delete(karaokeProcess.id, karaokeProcess.database)
//                }

                } catch (_: Exception) {
                    process.destroy()
                    println("[${Timestamp.from(Instant.now())}] KaraokeProcessThread[${karaokeProcess.threadId}]: ERROR задание: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}")
                    karaokeProcess.status = KaraokeProcessStatuses.ERROR.name
                    karaokeProcess.end = Timestamp.from(Instant.now())
                    karaokeProcess.priority = -1
                    karaokeProcess.save()
                }
            }

        }

    }
}

@Component
class KaraokeProcessWorker {


    companion object {
        val argsIgnoredToLog = listOf("ln", "rm", "chmod", "mkdir", "cp", "mv")
        var isWork: Boolean = false
        var stopAfterThreadIsDone: Boolean = false
        var withoutControl = false

        val threadsMap: MutableMap<Int, KaraokeProcessThread?> = mutableMapOf()

//        var workThread: KaraokeProcessThread? = null

        fun start(database: KaraokeConnection) {
            if (!isWork) {
                KaraokeProcess.deleteDone(database)
                KaraokeProcess.setWorkingToWaiting(database)
                sendCountWaitingMessage(KaraokeProcess.getCountWaiting(database))
                doStart(database)
            } else {
                stopAfterThreadIsDone = false
                sendStateMessage()
            }
        }

        fun stop() {
            if (isWork) {
                doStop()
                sendStateMessage()
            }
        }

        fun sendStateMessage() {
            val messageProcessWorkerState = SseNotification.processWorkerState(
                ProcessWorkerStateMessage(
                    isWork = isWork,
                    stopAfterThreadIsDone = stopAfterThreadIsDone
                )
            )
            try {
                SNS.send(messageProcessWorkerState)
            } catch (e: Exception) {
                println(e.message)
            }
        }

        fun sendCountWaitingMessage(countWaiting: Long) {
            val messageProcessCountWaiting = SseNotification.processCountWaiting(
                    ProcessCountWaitingMessage(
                            countWaiting = countWaiting
                    )
            )
            try {
                SNS.send(messageProcessCountWaiting)
            } catch (e: Exception) {
                println(e.message)
            }
        }

        private fun getKaraokeProcessesToStart(database: KaraokeConnection): Map<Int, KaraokeProcess> {
            return KaraokeProcess.getProcessesToStart(database)
        }

        private fun doStart(database: KaraokeConnection) {
            val timeout = 10L
            var counter = 0L
            var id = 0L
//            var settingsId = 0L
//            var processType = ""
//            var percentage = 0.0

            val intervalCheckDummy = 6_000
            val intervalCheckFiles = 24_000
            var requestNewSongTimeoutMs = Karaoke.requestNewSongTimeoutMs
            var requestNewSongLastTimeMs = Karaoke.requestNewSongLastTimeMs

            isWork = true
            stopAfterThreadIsDone = false
            sendStateMessage()
            println("[${Timestamp.from(Instant.now())}] ProcessWorker: Стартуем")

            while (isWork) {

                val currentTimeMs = System.currentTimeMillis()

                if (Karaoke.checkLastAlbum) {
                    if (requestNewSongLastTimeMs + requestNewSongTimeoutMs < currentTimeMs) {
                        requestNewSongLastTimeMs = currentTimeMs
                        println("[${Timestamp.from(Instant.now())}] ProcessWorker: Проверка нового альбома...")
                        val (authorForRequest, album, reason) = checkLastAlbumYm()
                        if (reason >= 0) {
                            // Удачный запрос (может быть найден новый альбом)
                            Karaoke.requestNewSongLastSuccessAuthor = authorForRequest
                            Karaoke.requestNewSongLastSuccessTimeMs = currentTimeMs
                            Karaoke.requestNewSongLastSuccessTimeCode = millisecondsToTimeFormatted(currentTimeMs)
                            if (reason == 1) {
                                // Найден новый альбом - сообщим об этом в сообщении
                                SNS.send(SseNotification.message(Message(
                                    type = "info",
                                    head = "Новый альбом",
                                    body = "У автора «$authorForRequest» найден новый альбом «$album»"
                                )))
                            }
                        } else if (reason == -1) {
                            // Неудачный запрос, увеличиваем время таймаута
                            if (requestNewSongTimeoutMs < 3_600_000) {
                                requestNewSongTimeoutMs += Karaoke.requestNewSongTimeoutIncreaseMs
                                Karaoke.requestNewSongTimeoutMs = requestNewSongTimeoutMs
                                Karaoke.requestNewSongTimeoutMin = requestNewSongTimeoutMs / 60_000L
                            }
                        } else {
                            // Не удалось найти автора! - считаем что запрос был удачный, не нужно увеличивать таймаут
                            Karaoke.requestNewSongLastSuccessTimeMs = currentTimeMs
                            Karaoke.requestNewSongLastSuccessTimeCode = millisecondsToTimeFormatted(currentTimeMs)
                        }
                        Karaoke.requestNewSongLastTimeMs = requestNewSongLastTimeMs
                        Karaoke.requestNewSongLastTimeCode = millisecondsToTimeFormatted(requestNewSongLastTimeMs)
                        Karaoke.requestNewSongLastAuthor = authorForRequest

                        requestNewSongTimeoutMs = Karaoke.requestNewSongTimeoutMs
                        requestNewSongLastTimeMs = Karaoke.requestNewSongLastTimeMs

                    }
                }


                if (counter % (intervalCheckDummy / timeout) == 0L) {
                    SNS.send(SseNotification.dummy())
                }

                counter++
                if (!withoutControl) {
                    Thread.sleep(timeout)

                    if (counter % (intervalCheckFiles / timeout) == 0L) {

                        if (Karaoke.monitoringRemoteSettingsSync) {
//                            println("ProcessWorker: Проверка sync-записей по таймеру...")
                            // Получаем список sync-записей из REMOTE DATABASE
                            val listSettingsSync = Settings.loadListFromDb(database = Connection.remote(), sync = true, storageService = KSS_APP)
                            listSettingsSync.forEach { settingsSync ->
                                val settingsLocal = Settings.loadFromDbById(id = settingsSync.id, database = Connection.local(), storageService = KSS_APP)
                                if (settingsLocal != null) {
                                    // Запись в локальной БД есть, надо обновить
                                    val diff = Settings.getDiff(settingsSync, settingsLocal)
                                    val setStr = diff.filter { it.recordDiffRealField }
                                        .joinToString(", ") { "${it.recordDiffName} = ?" }
                                    if (setStr != "") {
                                        val sql = "UPDATE tbl_settings SET $setStr WHERE id = ?"

                                        val connection = Connection.local().getConnection()
                                        if (connection == null) {
                                            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных LOCAL")
                                        } else {
                                            val ps = connection.prepareStatement(sql)

                                            var index = 1
                                            diff.filter{ it.recordDiffRealField }.forEach {
                                                when (it.recordDiffValueNew) {
                                                    is Long -> ps.setLong(index, it.recordDiffValueNew)
                                                    is Int -> ps.setInt(index, it.recordDiffValueNew)
                                                    else -> ps.setString(index, it.recordDiffValueNew.toString())
                                                }
                                                index++
                                            }
                                            ps.setLong(index, settingsLocal.id)
                                            ps.executeUpdate()
                                            ps.close()
                                            if (Karaoke.autoUpdateRemoteSettings && Karaoke.allowUpdateRemote) {
                                                val (listCreate, listUpdate, listDelete) = updateRemoteSettingFromLocalDatabase(settingsLocal.id)
                                                if (listCreate.size + listUpdate.size + listDelete.size != 0) {
                                                    SNS.send(SseNotification.crud(listOf(listCreate, listUpdate, listDelete)))
                                                }

                                            }
                                        }
                                    }
                                } else {
                                    // Записи в локальной БД нет, надо создать
                                    val sqlToInsert = settingsSync.getSqlToInsert()
                                    val connection = Connection.local().getConnection()
                                    if (connection == null) {
                                        println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных LOCAL")
                                    } else {
                                        val ps = connection.prepareStatement(sqlToInsert)
                                        ps.executeUpdate()
                                        ps.close()
                                    }
                                }

                                if (settingsSync.tags == "RENDER") {
                                    val settingsLocal = Settings.loadFromDbById(id = settingsSync.id, database = Connection.local(), storageService = KSS_APP)
                                    if (settingsLocal != null) {
                                        settingsLocal.sourceMarkersList.forEachIndexed { voice, _ ->
                                            val strText = settingsLocal.convertMarkersToSrt(voice)
                                            val fileName = "${settingsLocal.rootFolder}/${settingsLocal.rightSettingFileName}.voice${voice+1}.srt"
                                            File(fileName).writeText(strText)
                                            runCommand(listOf("chmod", "666", fileName))
                                        }

                                        settingsLocal.createKaraoke(createLyrics = true, createKaraoke = true)

                                        KaraokeProcess.createProcess(
                                            settings = settingsLocal,
                                            action = KaraokeProcessTypes.MELT_LYRICS,
                                            doWait = true,
                                            prior = 0,
                                            threadId = 0
                                        )
                                        KaraokeProcess.createProcess(
                                            settings = settingsLocal,
                                            action = KaraokeProcessTypes.MELT_KARAOKE,
                                            doWait = true,
                                            prior = 1,
                                            threadId = 0
                                        )
                                    }
                                }
                            }
                            // Удаляем записи из sync-таблицы
                            listSettingsSync.map{ it.id }.forEach { idToDel ->
                                Settings.deleteFromDb(id = idToDel, database = Connection.remote(), sync = true)
                            }
                        }

                    }

                }

                val karaokeProcessesToStart = getKaraokeProcessesToStart(database)
                val karaokeProcessesToStartIds = karaokeProcessesToStart.keys.toList()
                val threadsIds = threadsMap.filter { it.value != null }.keys.toList()

                /*
                Для каждого id из karaokeProcessesToStartIds проверяем, есть ли такой же id в threadsIds
                Если такого нет или такой есть и он null или !isAlive - тогда надо запустить новый процесс с таким же id
                Иначе обновляем персентаж
                 */

                karaokeProcessesToStartIds.forEach { threadId ->
                    if (!threadsIds.contains(threadId) || (threadsIds.contains(threadId) && (threadsMap[threadId] == null || !threadsMap[threadId]!!.isAlive))) {
                        val karaokeProcess = karaokeProcessesToStart[threadId]
                        val countWaiting = KaraokeProcess.getCountWaiting(database)
                        sendCountWaitingMessage(countWaiting)
                        if (karaokeProcess != null && (!stopAfterThreadIsDone || karaokeProcess.command == "tail")) {
                            val args = karaokeProcess.args[0]
                            if (args.isNotEmpty()) {
                                if (id > 0) {

                                    val kp = KaraokeProcess.load(id, database)
                                    val diffs = KaraokeProcess.getDiff(kp)
                                    if (diffs.isNotEmpty()) {
                                        karaokeProcess.save()
                                    }

                                }
//                                workThread = KaraokeProcessThread(karaokeProcess)
                                threadsMap[threadId] = KaraokeProcessThread(karaokeProcess)

                                id = karaokeProcess.id
//                            settingsId = karaokeProcess.settingsId.toLong()
//                            processType = karaokeProcess.type
//                                percentage = 0.0
                                withoutControl = karaokeProcess.withoutControl
                                if (karaokeProcess.command != "tail" || karaokeProcess.args[0][0] !in argsIgnoredToLog) {
                                    println("[${Timestamp.from(Instant.now())}] ProcessWorker: Стартуем новое задание: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}")
                                }
//                                workThread!!.start()
                                threadsMap[threadId]!!.start()
                            }
                        } else {
                            if (id != 0L) {
                                val kp = KaraokeProcess.load(id, database)
                                val diffs = KaraokeProcess.getDiff(kp)
                                if (diffs.isNotEmpty()) {
//                                    workThread?.karaokeProcess?.save()
                                    threadsMap[threadId]?.karaokeProcess?.save()
                                }
                            }
                            if (stopAfterThreadIsDone) {
                                stopAfterThreadIsDone = false
                                isWork = false
                                withoutControl = false
                                sendStateMessage()
                                println("[${Timestamp.from(Instant.now())}] ProcessWorker: Останавливаемся")
                            }

                        }
                    } else {

                        if (!withoutControl) {

//                            val kp = workThread?.karaokeProcess
                            val kp = threadsMap[threadId]?.karaokeProcess
                            val diffs = KaraokeProcess.getDiff(kp)
                            if (diffs.isNotEmpty()) {
//                                if (percentage != (workThread?.karaokeProcess?.percentage ?: 0.0)) {
//                                    percentage = workThread?.karaokeProcess?.percentage ?: 0.0
//                                }
//                                workThread?.karaokeProcess?.save()

//                                if (percentage != (threadsMap[threadId]?.karaokeProcess?.percentage ?: 0.0)) {
//                                    percentage = threadsMap[threadId]?.karaokeProcess?.percentage ?: 0.0
//                                }
                                threadsMap[threadId]?.karaokeProcess?.save()
                            }
                        }

                    }
                }

            }
        }

        private fun doStop() {
            stopAfterThreadIsDone = true
        }

        fun getPercentage(karaokeProcess: KaraokeProcess): String {
            val workThread = threadsMap.filter { it.key == karaokeProcess.threadId && it.value?.karaokeProcess?.id == karaokeProcess.id }.values.toList().firstOrNull()
            return workThread?.percentage ?: "---"
        }

    }
}