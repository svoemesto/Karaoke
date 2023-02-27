import java.io.IOException
import java.io.InputStreamReader

val TEST_AUDIO_FILE_PATH = "/home/nsa/Documents/Караоке/Агата Кристи/1988 - Второй фронт/(01) [Агата Кристи] Инспектор ПО.flac"
fun main() {
//    println(MediaInfo.executeMediaInfo(TEST_AUDIO_FILE_PATH, "--Output=JSON"))
    println(MediaInfo.getInfoBySectionAndParameter(TEST_AUDIO_FILE_PATH, "Audio","Channels"))
}
class MediaInfo {

    companion object {

        fun getInfo(media: String): String {
            return executeMediaInfo(media)
        }

        fun getInfoByParameter(media: String, parameter: String): String {
            return executeMediaInfo(media, parameter)
        }

        fun getInfoBySectionAndParameter(media: String, section: String, parameter: String): String {
            val param = "--Inform='$section;%$parameter%'"
            return executeMediaInfo(media, param)
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
            val exePath: String = PATH_TO_MEDIAINFO
            val param = mutableListOf<String>()
            param.add(exePath)
            if (parameters.isNotEmpty()) {
                for (i in parameters.indices) {
                    param.add(parameters[i])
                }
            }
            val builder = ProcessBuilder(param)
            builder.redirectErrorStream(true)
            val process = builder.start()
            val buffer = StringBuilder()
            InputStreamReader(process.inputStream).use { reader ->
                var i: Int
                while (reader.read().also { i = it } != -1) {
                    buffer.append(i.toChar())
                }
            }
            val status = process.waitFor()
            val out = buffer.toString()
            return out.substring(0, out.length - 2)
        }
    }
}
