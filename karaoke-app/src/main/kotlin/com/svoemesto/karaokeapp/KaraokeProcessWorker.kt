package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.Settings
import org.springframework.stereotype.Component
import java.io.BufferedReader
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
            val args = karaokeProcess.args[0]
            val processBuilder = ProcessBuilder(args)
            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()

            try {


                val inputStream = process.inputStream

                val reader = BufferedReader(InputStreamReader(inputStream))
                var line: String? = reader.readLine()
                while (line != null) {

                    val matchResult = regex.find(line)
                    if (matchResult != null) {
                        val currentFrame = matchResult.groupValues[1]
                        val percentage = matchResult.groupValues[2]
    //                    println("Current Frame: $currentFrame, percentage: $percentage")
                        this.percentage = percentage
                    }
                    line = reader.readLine()
                }

                karaokeProcess.status = KaraokeProcessStatuses.DONE.name
                karaokeProcess.end = Timestamp.from(Instant.now())
                karaokeProcess.priority = 999
                karaokeProcess.save()

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

        private fun getKaraokeProcessToStart(): KaraokeProcess? {
            return KaraokeProcess.getProcessToStart()
        }

        private fun doStart() {
            val timeout = 1000L
            isWork = true
            stopAfterThreadIsDone = false
            while (isWork) {
                Thread.sleep(timeout)
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