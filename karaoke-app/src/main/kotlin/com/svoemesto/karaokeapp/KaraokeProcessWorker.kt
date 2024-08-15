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
            val args = karaokeProcess.args[0]
            val processBuilder = ProcessBuilder(args)
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()
            if (process.isAlive) {
                val niceCommand = "nice -n ${karaokeProcess.prioritet} ${process.pid()}"
                Runtime.getRuntime().exec(niceCommand)
            }
            try {

                val inputStream = process.inputStream
                var duration: String? = null
                val reader = BufferedReader(InputStreamReader(inputStream))
                var line: String? = reader.readLine()
                while (line != null) {
                    val matchResult = regex.find(line)
                    if (matchResult != null) {
                        val currentFrame = matchResult.groupValues[1]
                        val percentage = matchResult.groupValues[2]
    //                    println("Current Frame: $currentFrame, percentage: $percentage")
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
                            }
                        }
                    }
                    line = reader.readLine()
                }

                karaokeProcess.status = KaraokeProcessStatuses.DONE.name
                karaokeProcess.end = Timestamp.from(Instant.now())
                karaokeProcess.priority = 999
                karaokeProcess.save()

//                if (karaokeProcess.type == KaraokeProcessTypes.DEMUCS2.name) {
//                    KaraokeProcess.delete(karaokeProcess.id, karaokeProcess.database)
//                }


            } catch (e: Exception) {
                process.destroy()
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


        var workThread: KaraokeProcessThread? = null

        fun start(database: KaraokeConnection) {
            if (!isWork) {
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
            val timeout = 100L
            var counter = 0L
            var id = 0L
            var settingsId = 0L
            var processType = ""
            var percentage = 0.0

            val intervalCheckDummy = 60_000
            val intervalCheckFiles = 240_000

            isWork = true
            stopAfterThreadIsDone = false
            sendStateMessage()
            while (isWork) {
                counter++
                Thread.sleep(timeout)

                if (counter % (intervalCheckDummy / timeout) == 0L) {
                    SNS.send(SseNotification.dummy())
                }

                if (counter % (intervalCheckFiles / timeout) == 0L) {
                    // Каждые 120 секунд проверяем наличие файлов на обновление
                    val listFiles = getListFiles("/clouds/Yandex.Disk/Karaoke/_TMP","settings")
                    listFiles.forEach {fileName ->
                        val tmpSettings = Settings.loadFromFile(fileName, readonly = true, database)
                        File(fileName).delete()
                        val settings = Settings.loadFromDbById(tmpSettings.id, database)
                        if (settings != null) {

                            val needCreateKaraoke = (settings.idStatus < tmpSettings.idStatus && tmpSettings.idStatus == 3L && settings.sourceMarkers != tmpSettings.sourceMarkers)

                            settings.fields[SettingField.ID_STATUS] = tmpSettings.fields[SettingField.ID_STATUS] ?: ""
                            settings.fields[SettingField.NAME] = tmpSettings.fields[SettingField.NAME] ?: ""
                            settings.fields[SettingField.AUTHOR] = tmpSettings.fields[SettingField.AUTHOR] ?: ""
                            settings.fields[SettingField.ALBUM] = tmpSettings.fields[SettingField.ALBUM] ?: ""
                            settings.fields[SettingField.DATE] = tmpSettings.fields[SettingField.DATE] ?: ""
                            settings.fields[SettingField.TIME] = tmpSettings.fields[SettingField.TIME] ?: ""
                            settings.fields[SettingField.YEAR] = tmpSettings.fields[SettingField.YEAR] ?: ""
                            settings.fields[SettingField.TRACK] = tmpSettings.fields[SettingField.TRACK] ?: ""
                            settings.fields[SettingField.KEY] = tmpSettings.fields[SettingField.KEY] ?: ""
                            settings.fields[SettingField.BPM] = tmpSettings.fields[SettingField.BPM] ?: ""
                            settings.fields[SettingField.ID_BOOSTY] = tmpSettings.fields[SettingField.ID_BOOSTY] ?: ""
                            settings.fields[SettingField.ID_BOOSTY_FILES] = tmpSettings.fields[SettingField.ID_BOOSTY_FILES] ?: ""
                            settings.fields[SettingField.ID_VK] = tmpSettings.fields[SettingField.ID_VK] ?: ""
                            settings.fields[SettingField.ID_YOUTUBE_LYRICS] = tmpSettings.fields[SettingField.ID_YOUTUBE_LYRICS] ?: ""
                            settings.fields[SettingField.ID_YOUTUBE_KARAOKE] = tmpSettings.fields[SettingField.ID_YOUTUBE_KARAOKE] ?: ""
                            settings.fields[SettingField.ID_YOUTUBE_CHORDS] = tmpSettings.fields[SettingField.ID_YOUTUBE_CHORDS] ?: ""
                            settings.fields[SettingField.ID_VK_LYRICS] = tmpSettings.fields[SettingField.ID_VK_LYRICS] ?: ""
                            settings.fields[SettingField.ID_VK_KARAOKE] = tmpSettings.fields[SettingField.ID_VK_KARAOKE] ?: ""
                            settings.fields[SettingField.ID_VK_CHORDS] = tmpSettings.fields[SettingField.ID_VK_CHORDS] ?: ""
                            settings.fields[SettingField.ID_TELEGRAM_LYRICS] = tmpSettings.fields[SettingField.ID_TELEGRAM_LYRICS] ?: ""
                            settings.fields[SettingField.ID_TELEGRAM_KARAOKE] = tmpSettings.fields[SettingField.ID_TELEGRAM_KARAOKE] ?: ""
                            settings.fields[SettingField.ID_TELEGRAM_CHORDS] = tmpSettings.fields[SettingField.ID_TELEGRAM_CHORDS] ?: ""
                            settings.fields[SettingField.RESULT_VERSION] = tmpSettings.fields[SettingField.RESULT_VERSION] ?: ""
                            settings.fields[SettingField.COLOR] = tmpSettings.fields[SettingField.COLOR] ?: ""
                            settings.sourceText = tmpSettings.sourceText
                            settings.resultText = tmpSettings.resultText
                            settings.sourceMarkers = tmpSettings.sourceMarkers
                            settings.saveToDb()

                            if (needCreateKaraoke) {
                                settings.sourceMarkersList.forEachIndexed { voice, _ ->
                                    val strText = settings.convertMarkersToSrt(voice)
                                    File("${settings.rootFolder}/${settings.fileName}.voice${voice+1}.srt").writeText(strText)
                                }

                                settings.createKaraoke()

                                KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS, true, 0)
                                KaraokeProcess.createProcess(settings, KaraokeProcessTypes.FF_720_LYR, true, 0)
                                KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, 1)
                            }

                        }
                    }
                }

                // Проверяем, выполняется ли в данный момент какое-то задание
                // Если да - ждём, если нет запускаем новое задание (если оно есть в очереди)
                if (workThread == null || !workThread!!.isAlive) {
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
                            workThread!!.start()
                        }
                    } else {
                        val kp = KaraokeProcess.load(id, database)
                        val diffs = KaraokeProcess.getDiff(kp)
                        if (diffs.isNotEmpty()) {
                            workThread?.karaokeProcess?.save()
                        }

                        stopAfterThreadIsDone = false
                        isWork = false
                        sendStateMessage()
                    }
                } else {

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