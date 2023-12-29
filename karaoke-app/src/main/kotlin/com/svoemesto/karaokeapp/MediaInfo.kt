package com.svoemesto.karaokeapp

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.InputStreamReader

val TEST_AUDIO_FILE_PATH = "/home/nsa/Documents/Караоке/Агата Кристи/1988 - Второй фронт/(01) [Агата Кристи] Инспектор ПО.flac"
fun mainMediaInfo() {
//    println(MediaInfo.executeMediaInfo(TEST_AUDIO_FILE_PATH, "--Output=JSON"))
//    println(MediaInfo.getInfoBySectionAndParameter(TEST_AUDIO_FILE_PATH, "Audio","Duration"))
    println(MediaInfo.getInfoBySectionAndParameter(TEST_AUDIO_FILE_PATH, "Audio", "Duration"))
}

class MediaInfo {

    companion object {

        fun getInfo(media: String): Map<String, Any> {
            val exePath: String = PATH_TO_MEDIAINFO
            val param = mutableListOf<String>()
            param.add(exePath)
            param.add(media)
            param.add("--Output=JSON")
            val process = ProcessBuilder(param).redirectErrorStream(true).start()
            val buffer = StringBuilder()
            InputStreamReader(process.inputStream).use { reader ->
                var i: Int
                while (reader.read().also { i = it } != -1) {
                    buffer.append(i.toChar())
                }
            }
            val status = process.waitFor()
            val out = buffer.toString()
            val objectMapper = ObjectMapper()
            val result: Map<String, Any> = objectMapper.readValue(out, object : TypeReference<HashMap<String, Any>>() {})
            return result
        }

        fun getInfoByParameter(media: String, parameter: String): String {
            return executeMediaInfo(media, parameter)
        }

        fun getInfoBySectionAndParameter(media: String, section: String, parameter: String): String? {

            try {
                val mediaInfo = getInfo(media)
                val media = mediaInfo["media"] as HashMap<String, Any>
                val tracks = media["track"] as List<HashMap<String, Any>>

                tracks.forEach {
                    val track = it as HashMap<String, String>
                    if (track["@type"] == section) {
                        return track[parameter]
                    }
                }
            } catch (e: Exception) {
                println("${media} exeption: ${e.message}")
            }
            return null
        }

        fun executeMediaInfo(media: String): String {
            val param = mutableListOf<String>()
            param.add(media)
            return executeMediaInfo(param)
        }
        fun executeMediaInfo(media: String, parameter: String): String {
            val param = mutableListOf<String>()
            param.add(media)
            param.add(parameter)
            return executeMediaInfo(param)
        }

        fun executeMediaInfo(parameters: List<String>): String {
            println(parameters)
            val exePath: String = PATH_TO_MEDIAINFO
            val param = mutableListOf<String>()
            param.add(exePath)
            if (parameters.isNotEmpty()) {
                for (i in parameters.indices) {
                    param.add(parameters[i])
                }
            }
            println(param)
//            val builder = ProcessBuilder(param)
            val builder = ProcessBuilder("mediainfo","--Inform=\"%%Duration%%\"", "$TEST_AUDIO_FILE_PATH", "--Output=JSON" )
            builder.redirectErrorStream(true)
//            builder.redirectOutput(ProcessBuilder.Redirect.PIPE)
            val process = builder.start()
            println(process)
            val buffer = StringBuilder()
            InputStreamReader(process.inputStream).use { reader ->
                var i: Int
                while (reader.read().also { i = it } != -1) {
                    buffer.append(i.toChar())
                }
            }
//            val status = process.waitFor()
            val out = buffer.toString()
            println(out)

            val objectMapper = ObjectMapper()

            val result: Map<String, Any> = objectMapper.readValue(out, object : TypeReference<HashMap<String, Any>>() {})

            println(result)

            return out.substring(0, out.length - 2)
        }
    }
}
