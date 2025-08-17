package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.*
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

            val regex = Regex("Current Frame:\\s+(\\d+), percentage:\\s+(\\d+)")
            val regexDuration = Regex("Duration:\\s+(\\d\\d:\\d\\d:\\d\\d\\.\\d\\d),")
            val regexCurrent = Regex("time=(\\d\\d:\\d\\d:\\d\\d\\.\\d\\d)")
            val regexPercentageSheetsage = Regex("^\\s{0,2}(\\d{1,3})%\\|")
            val args = karaokeProcess.args[0]
            val processBuilder = ProcessBuilder(args)
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()
            if (process.isAlive) {
                println("[${Timestamp.from(Instant.now())}] KaraokeProcessThread: Установка приоритета задания: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}")
                setProcessPriority(process.pid(), karaokeProcess.prioritet)
            }

            try {

                println("[${Timestamp.from(Instant.now())}] KaraokeProcessThread: Начинаем работу с заданием: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}")
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
                        val currentFrame = matchResult.groupValues[1]
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
                println("[${Timestamp.from(Instant.now())}] KaraokeProcessThread: Завершаем работу с заданием: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}")
                if (log != "") {
                    println("[${Timestamp.from(Instant.now())}] KaraokeProcessThread: Выводим лог задания: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}")
                    log = args.joinToString(" ") + "\n\n" + log
                    val logFileName = "$PATH_TO_LOGS/[${Timestamp.from(Instant.now())}] ${karaokeProcess.name} - ${karaokeProcess.description}.log".rightFileName()
                    try {
                        File(logFileName).writeText(log, Charsets.UTF_8)
                    } catch (e: Exception) {
                        println(e.message)
                    }
                }

                if (karaokeProcess.type == "SHEETSAGE" &&  lastLine == "NotImplementedError: Dynamic chunking not implemented. Try halving measures_per_chunk.") {
                    // Если процесс SHEETSAGE завершился ошибкой - создаём для этой же песни процесс SHEETSAGE2 с таким же приоритетом
                    val settings = Settings.loadFromDbById(karaokeProcess.settingsId.toLong(), WORKING_DATABASE)
                    settings?.let {
                        KaraokeProcess.createProcess(settings, KaraokeProcessTypes.SHEETSAGE2, true, karaokeProcess.priority)
                    }
                }

                println("[${Timestamp.from(Instant.now())}] KaraokeProcessThread: DONE успешно завершенное задание: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}")
                karaokeProcess.status = KaraokeProcessStatuses.DONE.name
                karaokeProcess.end = Timestamp.from(Instant.now())
                karaokeProcess.priority = 999
                karaokeProcess.save()

//                if (karaokeProcess.type == KaraokeProcessTypes.DEMUCS2.name) {
//                    KaraokeProcess.delete(karaokeProcess.id, karaokeProcess.database)
//                }

            } catch (e: Exception) {
                process.destroy()
                println("[${Timestamp.from(Instant.now())}] KaraokeProcessThread: ERROR задание: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}")
                karaokeProcess.status = KaraokeProcessStatuses.ERROR.name
                karaokeProcess.end = Timestamp.from(Instant.now())
                karaokeProcess.priority = -1
                karaokeProcess.save()
            }
        }

    }
}

@Component
class KaraokeProcessWorker {


    companion object {
        var isWork: Boolean = false
        var stopAfterThreadIsDone: Boolean = false
        var withoutControl = false

        var workThread: KaraokeProcessThread? = null

        fun start(database: KaraokeConnection) {
            if (!isWork) {
                KaraokeProcess.setWorkingToWaiting(database)
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

        fun deleteDone(database: KaraokeConnection) {
            KaraokeProcess.loadList(mapOf(Pair("process_status","DONE")), database = database).forEach { KaraokeProcess.delete(it.id, database) }
        }

        private fun getKaraokeProcessToStart(database: KaraokeConnection): KaraokeProcess? {
            return KaraokeProcess.getProcessToStart(database)
        }

        private fun doStart(database: KaraokeConnection) {
            val timeout = 10L
            var counter = 0L
            var id = 0L
            var settingsId = 0L
            var processType = ""
            var percentage = 0.0

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
                            val listSettingsSync = Settings.loadListFromDb(database = Connection.remote(), sync = true)
                            listSettingsSync.forEach { settingsSync ->
                                val settingsLocal = Settings.loadFromDbById(id = settingsSync.id, database = Connection.local())
                                if (settingsLocal != null) {
                                    // Запись в локальной БД есть, надо обновить
                                    val diff = Settings.getDiff(settingsSync, settingsLocal)
                                    val setStr = diff.filter{ it.recordDiffRealField }.map { "${it.recordDiffName} = ?" }.joinToString(", ")
                                    if (setStr != "") {
                                        val sql = "UPDATE tbl_settings SET $setStr WHERE id = ?"

                                        val connection = Connection.local().getConnection()
                                        if (connection == null) {
                                            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                                        } else {
                                            val ps = connection.prepareStatement(sql)

                                            var index = 1
                                            diff.filter{ it.recordDiffRealField }.forEach {
                                                if (it.recordDiffValueNew is Long) {
                                                    ps.setLong(index, it.recordDiffValueNew.toLong())
                                                } else if (it.recordDiffValueNew is Int) {
                                                    ps.setInt(index, it.recordDiffValueNew.toInt())
                                                } else {
                                                    ps.setString(index, it.recordDiffValueNew.toString())
                                                }
                                                index++
                                            }
                                            ps.setLong(index, settingsLocal.id)
                                            ps.executeUpdate()
                                            ps.close()
                                        }
                                    }
                                } else {
                                    // Записи в локальной БД нет, надо создать
                                    val sqlToInsert = settingsSync.getSqlToInsert()
                                    val connection = Connection.local().getConnection()
                                    if (connection == null) {
                                        println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                                    } else {
                                        val ps = connection.prepareStatement(sqlToInsert)
                                        ps.executeUpdate()
                                        ps.close()
                                    }
                                }

                                if (settingsSync.tags == "RENDER") {
                                    val settingsLocal = Settings.loadFromDbById(id = settingsSync.id, database = Connection.local())
                                    if (settingsLocal != null) {
                                        settingsLocal.sourceMarkersList.forEachIndexed { voice, _ ->
                                            val strText = settingsLocal.convertMarkersToSrt(voice)
                                            File("${settingsLocal.rootFolder}/${settingsLocal.rightSettingFileName}.voice${voice+1}.srt").writeText(strText)
                                        }

                                        settingsLocal.createKaraoke(createLyrics = true, createKaraoke = true)

                                        KaraokeProcess.createProcess(settingsLocal, KaraokeProcessTypes.MELT_LYRICS, true, 0)
                                        KaraokeProcess.createProcess(settingsLocal, KaraokeProcessTypes.MELT_KARAOKE, true, 1)
                                    }
                                }
                            }
                            // Удаляем записи из sync-таблицы
                            listSettingsSync.map{ it.id }.forEach { idToDel ->
                                Settings.deleteFromDb(id = idToDel, database = Connection.remote(), sync = true)
                            }
                        }

                        // Каждые 120 секунд проверяем наличие файлов на обновление
//                        val listFiles = getListFiles("/clouds/Yandex.Disk/Karaoke/_TMP","settings")
//                        listFiles.forEach {fileName ->
//                            val tmpSettings = Settings.loadFromFile(fileName, readonly = true, database)
//                            File(fileName).delete()
//                            val settings = Settings.loadFromDbById(tmpSettings.id, database)
//                            if (settings != null) {
//
//                                val needCreateKaraoke = (settings.idStatus < tmpSettings.idStatus && tmpSettings.idStatus == 3L && settings.sourceMarkers != tmpSettings.sourceMarkers)
//
//                                settings.fields[SettingField.ID_STATUS] = tmpSettings.fields[SettingField.ID_STATUS] ?: ""
//                                settings.fields[SettingField.NAME] = tmpSettings.fields[SettingField.NAME] ?: ""
//                                settings.fields[SettingField.AUTHOR] = tmpSettings.fields[SettingField.AUTHOR] ?: ""
//                                settings.fields[SettingField.ALBUM] = tmpSettings.fields[SettingField.ALBUM] ?: ""
//                                settings.fields[SettingField.DATE] = tmpSettings.fields[SettingField.DATE] ?: ""
//                                settings.fields[SettingField.TIME] = tmpSettings.fields[SettingField.TIME] ?: ""
//                                settings.fields[SettingField.YEAR] = tmpSettings.fields[SettingField.YEAR] ?: ""
//                                settings.fields[SettingField.TRACK] = tmpSettings.fields[SettingField.TRACK] ?: ""
//                                settings.fields[SettingField.KEY] = tmpSettings.fields[SettingField.KEY] ?: ""
//                                settings.fields[SettingField.BPM] = tmpSettings.fields[SettingField.BPM] ?: ""
//                                settings.fields[SettingField.ID_BOOSTY] = tmpSettings.fields[SettingField.ID_BOOSTY] ?: ""
//                                settings.fields[SettingField.ID_BOOSTY_FILES] = tmpSettings.fields[SettingField.ID_BOOSTY_FILES] ?: ""
//                                settings.fields[SettingField.ID_SPONSR] = tmpSettings.fields[SettingField.ID_SPONSR] ?: ""
//                                settings.fields[SettingField.VERSION_BOOSTY] = tmpSettings.fields[SettingField.VERSION_BOOSTY] ?: ""
//                                settings.fields[SettingField.VERSION_BOOSTY_FILES] = tmpSettings.fields[SettingField.VERSION_BOOSTY_FILES] ?: ""
//                                settings.fields[SettingField.VERSION_SPONSR] = tmpSettings.fields[SettingField.VERSION_SPONSR] ?: ""
//                                settings.fields[SettingField.ID_VK] = tmpSettings.fields[SettingField.ID_VK] ?: ""
//                                settings.fields[SettingField.ID_DZEN_LYRICS] = tmpSettings.fields[SettingField.ID_DZEN_LYRICS] ?: ""
//                                settings.fields[SettingField.ID_DZEN_KARAOKE] = tmpSettings.fields[SettingField.ID_DZEN_KARAOKE] ?: ""
//                                settings.fields[SettingField.ID_DZEN_MELODY] = tmpSettings.fields[SettingField.ID_DZEN_MELODY] ?: ""
//                                settings.fields[SettingField.ID_DZEN_CHORDS] = tmpSettings.fields[SettingField.ID_DZEN_CHORDS] ?: ""
//                                settings.fields[SettingField.ID_VK_LYRICS] = tmpSettings.fields[SettingField.ID_VK_LYRICS] ?: ""
//                                settings.fields[SettingField.ID_VK_KARAOKE] = tmpSettings.fields[SettingField.ID_VK_KARAOKE] ?: ""
//                                settings.fields[SettingField.ID_VK_MELODY] = tmpSettings.fields[SettingField.ID_VK_MELODY] ?: ""
//                                settings.fields[SettingField.ID_VK_CHORDS] = tmpSettings.fields[SettingField.ID_VK_CHORDS] ?: ""
//                                settings.fields[SettingField.ID_PL_LYRICS] = tmpSettings.fields[SettingField.ID_PL_LYRICS] ?: ""
//                                settings.fields[SettingField.ID_PL_KARAOKE] = tmpSettings.fields[SettingField.ID_PL_KARAOKE] ?: ""
//                                settings.fields[SettingField.ID_PL_MELODY] = tmpSettings.fields[SettingField.ID_PL_MELODY] ?: ""
//                                settings.fields[SettingField.ID_PL_CHORDS] = tmpSettings.fields[SettingField.ID_PL_CHORDS] ?: ""
//                                settings.fields[SettingField.ID_TELEGRAM_LYRICS] = tmpSettings.fields[SettingField.ID_TELEGRAM_LYRICS] ?: ""
//                                settings.fields[SettingField.ID_TELEGRAM_KARAOKE] = tmpSettings.fields[SettingField.ID_TELEGRAM_KARAOKE] ?: ""
//                                settings.fields[SettingField.ID_TELEGRAM_MELODY] = tmpSettings.fields[SettingField.ID_TELEGRAM_MELODY] ?: ""
//                                settings.fields[SettingField.VERSION_DZEN_LYRICS] = tmpSettings.fields[SettingField.VERSION_DZEN_LYRICS] ?: ""
//                                settings.fields[SettingField.VERSION_DZEN_KARAOKE] = tmpSettings.fields[SettingField.VERSION_DZEN_KARAOKE] ?: ""
//                                settings.fields[SettingField.VERSION_DZEN_MELODY] = tmpSettings.fields[SettingField.VERSION_DZEN_MELODY] ?: ""
//                                settings.fields[SettingField.VERSION_DZEN_CHORDS] = tmpSettings.fields[SettingField.VERSION_DZEN_CHORDS] ?: ""
//                                settings.fields[SettingField.VERSION_VK_LYRICS] = tmpSettings.fields[SettingField.VERSION_VK_LYRICS] ?: ""
//                                settings.fields[SettingField.VERSION_VK_KARAOKE] = tmpSettings.fields[SettingField.VERSION_VK_KARAOKE] ?: ""
//                                settings.fields[SettingField.VERSION_VK_MELODY] = tmpSettings.fields[SettingField.VERSION_VK_MELODY] ?: ""
//                                settings.fields[SettingField.VERSION_VK_CHORDS] = tmpSettings.fields[SettingField.VERSION_VK_CHORDS] ?: ""
//                                settings.fields[SettingField.VERSION_PL_LYRICS] = tmpSettings.fields[SettingField.VERSION_PL_LYRICS] ?: ""
//                                settings.fields[SettingField.VERSION_PL_KARAOKE] = tmpSettings.fields[SettingField.VERSION_PL_KARAOKE] ?: ""
//                                settings.fields[SettingField.VERSION_PL_MELODY] = tmpSettings.fields[SettingField.VERSION_PL_MELODY] ?: ""
//                                settings.fields[SettingField.VERSION_PL_CHORDS] = tmpSettings.fields[SettingField.VERSION_PL_CHORDS] ?: ""
//                                settings.fields[SettingField.VERSION_TELEGRAM_LYRICS] = tmpSettings.fields[SettingField.VERSION_TELEGRAM_LYRICS] ?: ""
//                                settings.fields[SettingField.VERSION_TELEGRAM_KARAOKE] = tmpSettings.fields[SettingField.VERSION_TELEGRAM_KARAOKE] ?: ""
//                                settings.fields[SettingField.VERSION_TELEGRAM_MELODY] = tmpSettings.fields[SettingField.VERSION_TELEGRAM_MELODY] ?: ""
//                                settings.fields[SettingField.ID_TELEGRAM_CHORDS] = tmpSettings.fields[SettingField.ID_TELEGRAM_CHORDS] ?: ""
//                                settings.fields[SettingField.RESULT_VERSION] = tmpSettings.fields[SettingField.RESULT_VERSION] ?: ""
//                                settings.fields[SettingField.DIFFBEATS] = tmpSettings.fields[SettingField.DIFFBEATS] ?: ""
//                                settings.fields[SettingField.COLOR] = tmpSettings.fields[SettingField.COLOR] ?: ""
//                                settings.fields[SettingField.RATE] = tmpSettings.fields[SettingField.RATE] ?: ""
//                                settings.sourceText = tmpSettings.sourceText
//                                settings.resultText = tmpSettings.resultText
//                                settings.sourceMarkers = tmpSettings.sourceMarkers
//                                settings.saveToDb()
//
//                                if (needCreateKaraoke) {
//                                    settings.sourceMarkersList.forEachIndexed { voice, _ ->
//                                        val strText = settings.convertMarkersToSrt(voice)
//                                        File("${settings.rootFolder}/${settings.rightSettingFileName}.voice${voice+1}.srt").writeText(strText)
//                                    }
//
//                                    settings.createKaraoke(createLyrics = true, createKaraoke = true)
//
//                                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS, true, 0)
//                                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, 1)
//                                }
//
//                            }
//                        }
                    }

                }


                // Проверяем, выполняется ли в данный момент какое-то задание
                // Если да - ждём, если нет запускаем новое задание (если оно есть в очереди)
                if (workThread == null || !workThread!!.isAlive) {
                    println("[${Timestamp.from(Instant.now())}] ProcessWorker: Получаем новое задание...")
                    val karaokeProcess = getKaraokeProcessToStart(database)
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
                            workThread = KaraokeProcessThread(karaokeProcess)

                            id = karaokeProcess.id.toLong()
                            settingsId = karaokeProcess.settingsId.toLong()
                            processType = karaokeProcess.type
                            percentage = 0.0
                            withoutControl = karaokeProcess.withoutControl
                            println("[${Timestamp.from(Instant.now())}] ProcessWorker: Стартуем новое задание: ${karaokeProcess.name} - [${karaokeProcess.type}] - ${karaokeProcess.description}")
                            workThread!!.start()
                        }
                    } else {
                        if (id == 0L) println("[${Timestamp.from(Instant.now())}] ProcessWorker: id процесса = 0, что-то не то.")
                        val kp = KaraokeProcess.load(id, database)
                        val diffs = KaraokeProcess.getDiff(kp)
                        if (diffs.isNotEmpty()) {
                            workThread?.karaokeProcess?.save()
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

                        val kp = workThread?.karaokeProcess
                        val diffs = KaraokeProcess.getDiff(kp)
                        if (diffs.isNotEmpty()) {
                            if (percentage != (workThread?.karaokeProcess?.percentage ?: 0.0)) {
                                percentage = workThread?.karaokeProcess?.percentage ?: 0.0
                            }
                            workThread?.karaokeProcess?.save()

                        }
                    }

                }
            }
        }

        private fun doStop() {
            stopAfterThreadIsDone = true
        }

        fun getPercentage(karaokeProcess: KaraokeProcess): String {
            return if (isWork && workThread != null && workThread!!.isAlive && workThread!!.karaokeProcess!!.id == karaokeProcess.id) {
                workThread!!.percentage ?: "---"
            } else {
                "---"
            }
        }

    }
}