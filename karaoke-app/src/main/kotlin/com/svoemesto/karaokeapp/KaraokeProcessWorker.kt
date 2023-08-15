package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.Settings
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
                                this.percentage = ((convertTimecodeToMilliseconds(current).toDouble() / convertTimecodeToMilliseconds(duration).toDouble()) * 100).toInt().toString()
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

                if (karaokeProcess.type == KaraokeProcessTypes.DEMUCS2.name) {
                    KaraokeProcess.delete(karaokeProcess.id)
                }


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

        fun start() {
            if (!isWork) {
                doStart()
            } else {
                stopAfterThreadIsDone = false
            }
        }

        fun stop() {
            if (isWork) {
                doStop()
            }
        }

        fun deleteDone() {
            KaraokeProcess.loadList(mapOf(Pair("process_status","DONE"))).forEach { KaraokeProcess.delete(it.id) }
        }

        private fun getKaraokeProcessToStart(): KaraokeProcess? {
            return KaraokeProcess.getProcessToStart()
        }

        private fun doStart() {
            val timeout = 1000L
            var counter = 0
            isWork = true
            stopAfterThreadIsDone = false
            while (isWork) {
                counter++
                Thread.sleep(timeout)

                if (counter % 120 == 0) {
                    // Каждые 120 секунд проверяем наличие файлов на обновление
                    val listFiles = getListFiles("/clouds/Yandex.Disk/Karaoke/_TMP","settings")
                    listFiles.forEach {fileName ->
                        val tmpSettings = Settings.loadFromFile(fileName, readonly = true)
                        File(fileName).delete()
                        val settings = Settings.loadFromDbById(tmpSettings.id)
                        if (settings != null) {

                            if (settings.sourceText != tmpSettings.sourceText || settings.sourceMarkers != tmpSettings.sourceMarkers) {

                                settings.sourceText = tmpSettings.sourceText
                                settings.sourceMarkers = tmpSettings.sourceMarkers
                                settings.saveToDb()

                                settings.sourceMarkersList.forEachIndexed { voice, _ ->
                                    val strText = settings.convertMarkersToSrt(voice)
                                    File("${settings.rootFolder}/${settings.fileName}.voice${voice+1}.srt").writeText(strText)
                                }

                                settings.createKaraoke()

                                KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS, true, 0)
                                KaraokeProcess.createProcess(settings, KaraokeProcessTypes.FF_720_LYR, true, 0)
                                KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, 1)
                                KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS_BT, true, 3)
                                KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE_BT, true, 3)

                            }

                        }
                    }
                }

                // Проверяем, выполняется ли в данный момент какое-то задание
                // Если да - ждём, если нет запускаем новое задание (если оно есть в очереди)
                if (workThread == null || !workThread!!.isAlive) {
                    if (!stopAfterThreadIsDone) {
                        val karaokeProcess = getKaraokeProcessToStart()
                        if (karaokeProcess != null) {
                            val args = karaokeProcess.args[0]
                            if (args.isNotEmpty()) {
                                workThread = KaraokeProcessThread(karaokeProcess)
                                workThread!!.start()
                            }
                        }
                    } else {
                        stopAfterThreadIsDone = false
                        isWork = false
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