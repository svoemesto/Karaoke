import com.google.gson.GsonBuilder
import model.Marker
import model.Settings
import model.Song
import model.SongVersion
import model.Subtitle
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.*
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.math.roundToInt
import kotlin.random.Random



fun main() {



}

fun createSongPicture(song: Song, fileName: String, songVersion: SongVersion, isBluetoothDelay: Boolean) {
    val caption = songVersion.text
    val comment: String = if (isBluetoothDelay) "для Bluetooth-колонок, задержка видео ${Karaoke.timeOffsetBluetoothSpeakerMs} ms" else ""
    val pathToLogoAlbum = "${song.settings.rootFolder}/LogoAlbum.png"
    val pathToLogoAuthor = "${song.settings.rootFolder}/LogoAuthor.png"

    val frameW = 1920
    val frameH = 1080
    val opaque: Float = 1f
    var fontSongname = Font("Montserrat SemiBold", 0, 10)
    var fontCaption = Font("Montserrat SemiBold", 0, 200)
    var fontComment = Font("Montserrat SemiBold", 0, 60)
    val colorSongname = Color(255,255,127,255)
    val colorCaption = Color(85,255,255,255)
    val colorComment = Color(85,255,255,255)
    var textToOverlay = song.settings.songName
    val imageType = BufferedImage.TYPE_INT_ARGB
    var resultImage = BufferedImage(frameW, frameH, imageType)
    val graphics2D = resultImage.graphics as Graphics2D
    val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)

    val biLogoAlbum = ImageIO.read(File(pathToLogoAlbum))
    val biLogoAuthor = ImageIO.read(File(pathToLogoAuthor))

    graphics2D.composite = alphaChannel
    graphics2D.background = Color.BLACK
    graphics2D.color = Color.BLACK
    graphics2D.fillRect(0,0,frameW, frameH)
    graphics2D.color = colorSongname
    graphics2D.font = fontSongname

    var rectW = 0
    var rectH = 0
    do {
        fontSongname = Font(fontSongname.name, fontSongname.style, fontSongname.size+1)
        graphics2D.font = fontSongname
        val fontMetrics = graphics2D.fontMetrics
        val rect = fontMetrics.getStringBounds(textToOverlay, graphics2D)
        rectW = rect.width.toInt()
        rectH = rect.height.toInt()
    } while (rectW < frameW * 0.95)

    var centerX = (frameW - rectW) / 2
    var centerY = (frameH - rectH) / 2 + rectH
    graphics2D.drawString(textToOverlay, centerX, centerY)

    textToOverlay = caption.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    graphics2D.color = colorCaption
    graphics2D.font = fontCaption
    var fontMetrics = graphics2D.fontMetrics
    var rect = fontMetrics.getStringBounds(textToOverlay, graphics2D)
    rectW = rect.width.toInt()
    rectH = rect.height.toInt()

    centerX = (frameW - rectW) / 2
    centerY = frameH - 100
    graphics2D.drawString(textToOverlay, centerX, centerY)

    graphics2D.drawImage(biLogoAlbum, 50, 50, null)
    graphics2D.drawImage(biLogoAuthor, 710, 50, null)

    if (comment != "") {
        textToOverlay = comment
        graphics2D.color = colorComment
        graphics2D.font = fontComment
        var fontMetrics = graphics2D.fontMetrics
        var rect = fontMetrics.getStringBounds(textToOverlay, graphics2D)
        rectW = rect.width.toInt()
        rectH = rect.height.toInt()

        centerX = (frameW - rectW) / 2
        centerY = frameH - 20
        graphics2D.drawString(textToOverlay, centerX, centerY)


    }

    graphics2D.drawImage(biLogoAlbum, 50, 50, null)
    graphics2D.drawImage(biLogoAuthor, 710, 50, null)

    graphics2D.dispose()

    val file = File(fileName)

    ImageIO.write(resultImage, "png", file)

}
fun test() {


    val fileNameXml = "src/main/resources/settings.xml"
    val props = Properties()
//    val frameW = Integer.valueOf(props.getProperty("FRAME_WIDTH_PX", "1"));
    var kdeBackgroundFolderPath = props.getProperty("kdeBackgroundFolderPath", "&&&")

    props.setProperty("FRAME_FPS", Karaoke.frameFps.toString())
    props.setProperty("VOICES_SETTINGS", """
        voice=0;group=0;fontNameText=Tahoma;colorText=255,255,255,255;fontNameBeat=Tahoma;colorBeat=155,255,255,255
        voice=0;group=1;fontNameText=Lobster;colorBeat=105,255,105,255;fontNameBeat=Lobster;colorText=255,255,155,255
        """
        .trimIndent())
    props.storeToXML(File(fileNameXml).outputStream(), "Какой-то комментарий")

    props.loadFromXML(File(fileNameXml).inputStream())

    val videoSettings = props.getProperty("VOICES_SETTINGS").split("\n")

    videoSettings.forEach { vs ->
        if (vs.isNotEmpty()) {
            val vars = vs.split(";")
            vars.forEach { variable ->
                val nameAndValue = variable.split("=")
                when(nameAndValue[0]) {
                    "voice" -> println("${nameAndValue[0]} = ${(nameAndValue[1].toLong())}")
                    "group" -> println("${nameAndValue[0]} = ${(nameAndValue[1].toLong())}")
                    "fontNameText" -> println("${nameAndValue[0]} = ${(nameAndValue[1] as String)}")
                    "fontNameBeat" -> println("${nameAndValue[0]} = ${(nameAndValue[1] as String)}")
                    "colorText" -> {
                        val rgba = nameAndValue[1].split(",")
                        println("colorText r = ${(rgba[0].toLong())}")
                        println("colorText g = ${(rgba[1].toLong())}")
                        println("colorText b = ${(rgba[2].toLong())}")
                        println("colorText a = ${(rgba[3].toLong())}")
                    }
                    "colorBeat" -> {
                        val rgba = nameAndValue[1].split(",")
                        println("colorBeat r = ${(rgba[0].toLong())}")
                        println("colorBeat g = ${(rgba[1].toLong())}")
                        println("colorBeat b = ${(rgba[2].toLong())}")
                        println("colorBeat a = ${(rgba[3].toLong())}")
                    }
                }
            }
        }
    }



}

fun getTextWidthHeightPx(text: String, fontName: String, fontStyle: Int, fontSize: Int): Pair<Double, Double> {
    return getTextWidthHeightPx(text, Font(fontName, fontStyle, fontSize))
}

fun getTextWidthHeightPx(text: String, font: Font): Pair<Double, Double> {
    val graphics2D = BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB).graphics as Graphics2D
    graphics2D.font = font
    val rect = graphics2D.fontMetrics.getStringBounds(text, graphics2D)
    return Pair(rect.width, rect.height)
}

fun convertMarkersToSubtitles(pathToSourceFile: String, pathToResultFile: String = "") {

    val gson = GsonBuilder()
        .setLenient()
        .create()

    val sourceFileBody = File(pathToSourceFile).readText(Charsets.UTF_8)
    val regexpLines = Regex("""<property name=\"kdenlive:markers\"[^<]([\s\S]+?)</property>""")
    val linesMatchResults = regexpLines.findAll(sourceFileBody)
    var countSubsFile = 0L
    val subsFiles: MutableList<MutableList<Marker>> = emptyList<MutableList<Marker>>().toMutableList()
    linesMatchResults.forEach { lineMatchResult ->
        val textToAnalize = lineMatchResult.groups.get(1)?.value?.replace("\n", "")?.replace("[", "")?.replace("]", "")
        val regexpMarkers = Regex("""\{[^\}]([\s\S]+?)\}""")
        val markersMatchResults = regexpMarkers.findAll(textToAnalize!!)
        if (markersMatchResults.iterator().hasNext()) {
            countSubsFile++
            val markers = mutableListOf<Marker>()
            markersMatchResults.forEach { markerMatchResult ->
                val marker = gson.fromJson(markerMatchResult.value, Marker::class.java)
                markers.add(marker)
            }
            subsFiles.add(markers)
        }
    }


    var countCreatedFiles = 0L
    for (indexSubFiles in 0 until subsFiles.size) {
        val subFile = subsFiles[indexSubFiles]
        var prevMarkerIsEndLine = true
        val subtitles = mutableListOf<Subtitle>()
        for (indexMarker in 0 until subFile.size) {

            val currMarker = subFile[indexMarker]

            if (currMarker.comment in ".\\/*" || indexMarker == subFile.size-1) {
                prevMarkerIsEndLine = true
                continue
            }

            val nextMarker = subFile[indexMarker+1]
            val isLineStart = prevMarkerIsEndLine
            val isLineEnd = (nextMarker.comment in ".\\/*" || indexMarker == subFile.size-1)
            prevMarkerIsEndLine = isLineEnd

            var subText = currMarker.comment.replace(" ", "_").replace("-", "")
            if (isLineStart) subText = subText[0].uppercase()+subText.subSequence(1,subText.length)
            if (isLineStart) subText = "//${subText}"
            if (isLineEnd) subText = "${subText}\\\\"

            val startTimecode = convertFramesToTimecode(currMarker.pos, 25.0)
            val endTimecode = convertFramesToTimecode(nextMarker.pos, 25.0)

            val subtitle = Subtitle(
                startTimecode = startTimecode,
                endTimecode = endTimecode,
                text = subText,
                isLineStart = isLineStart,
                isLineEnd = isLineEnd
            )
            subtitles.add(subtitle)
        }

        var textSubtitleFile = ""
        for (index in 0 until subtitles.size) {
            val subtitle = subtitles[index]
            textSubtitleFile += "${index+1}\n${subtitle.startTimecode} --> ${subtitle.endTimecode}\n${subtitle.text}\n\n"
        }

        if (textSubtitleFile != "") {
            countCreatedFiles++
            val fileNameNewSubs = "${pathToSourceFile}${if (countCreatedFiles == 1L) "" else "_${countCreatedFiles-1}"}.srt"
            File(fileNameNewSubs).writeText(textSubtitleFile)
        }

    }

}

fun getRandomFile(pathToFolder: String, extention: String = ""): String {
    val listFiles = getListFiles(pathToFolder, extention)
    return if (listFiles.isEmpty()) "" else listFiles[Random.nextInt(listFiles.size)]
}

fun getListFiles(pathToFolder: String, extention: String = "", startWith: String = ""): List<String> {
    return Files.walk(Path(pathToFolder)).filter(Files::isRegularFile).map { it.toString() }.filter{ it.endsWith(extention) && it.startsWith("${pathToFolder}/$startWith")}.toList()
}

fun extractSubtitlesFromAutorecognizedFile(pathToFileFrom: String, pathToFileTo: String): String {
    val text = File(pathToFileFrom).readText(Charsets.UTF_8)
    val regexpLines = Regex("""href=\"\d+?#[^\/a](.+?)\/a""")
    val linesMatchResults = regexpLines.findAll(text)
    var counter = 0L
    var subs = ""
    linesMatchResults.forEach { lineMatchResult->
        val line = lineMatchResult.value
        val startEnd = Regex("""href=\"\d+?[^\"&gt](.+?)\"&gt""").find(line)?.groups?.get(1)?.value?.split(":")
        val start = convertMillisecondsToTimecode(((startEnd?.get(0)?:"0").toDouble()*1000).toLong())
        val end = convertMillisecondsToTimecode(((startEnd?.get(1)?:"0").toDouble()*1000).toLong())
        val word = Regex("""&gt[^&lt](.+?)&lt""").find(line)?.groups?.get(1)?.value
        if (word != "Речь отсутствует") {
            counter++
            subs += "${counter}\n${start} --> ${end}\n${word}\n\n"
        }
    }
    File(pathToFileTo).writeText(subs)
    return subs
}

fun convertMillisecondsToFrames(milliseconds: Long, fps:Double = Karaoke.frameFps): Long {
    val frameLength = 1000.0 / fps
    return Math.round(milliseconds / frameLength)
}

fun convertMillisecondsToFramesDouble(milliseconds: Long, fps:Double = Karaoke.frameFps): Double {
    val frameLength = 1000.0 / fps
    return milliseconds / frameLength
}

fun convertFramesToMilliseconds(frames: Long, fps:Double = Karaoke.frameFps): Long {
    val frameLength = 1000.0 / fps
    return (frames * frameLength).roundToInt().toLong()
}

fun convertMillisecondsToTimecode(milliseconds: Long): String {
    val hours = milliseconds / (1000*60*60)
    val minutes = (milliseconds - hours*1000*60*60) / (1000*60)
    val seconds = (milliseconds - hours*1000*60*60 - minutes*1000*60) / 1000
    val ms = milliseconds - hours*1000*60*60 - minutes*1000*60 - seconds*1000
    return "%02d:%02d:%02d.%03d".format(hours,minutes,seconds,ms)
}

fun convertFramesToTimecode(frames: Long, fps:Double = Karaoke.frameFps): String {
    return convertMillisecondsToTimecode(milliseconds = convertFramesToMilliseconds(frames,fps))
}

fun convertTimecodeToMilliseconds(timecode: String): Long {
    val hhmmssmm = timecode.split(":")
    val hours = hhmmssmm[0].toLong()
    val minutes = hhmmssmm[1].toLong()
    val ssmm = hhmmssmm[2].replace(",", ".").split(".")
    val seconds = ssmm[0].toLong()
    val milliseconds = ssmm[1].toLong()
    return milliseconds + seconds * 1000 + minutes * 1000 * 60 + hours * 1000 * 60 * 60
}

fun convertTimecodeToFrames(timecode: String, fps:Double = Karaoke.frameFps): Long {
    return convertMillisecondsToFrames(convertTimecodeToMilliseconds(timecode = timecode), fps)
}

fun getBeatNumberByMilliseconds(timeInMilliseconds: Long, beatMs: Long, firstBeatTimecode: String): Long {

    var delayMs = convertTimecodeToMilliseconds(firstBeatTimecode)
    val diff = ((delayMs / (beatMs * 4))-1) * (beatMs * 4)
    delayMs -= diff

    val firstBeatMs = delayMs
    // println("Время звучания 1 бита = $beatMs ms")
//    val firstBeatMs = convertTimecodeToMilliseconds(firstBeatTimecode)
    // println("Первый отмеченый бит находится от начала в $firstBeatMs ms")
    // println("Время = $timeInMilliseconds ms")
    var timeInMillsCorrected = timeInMilliseconds - firstBeatMs
    // println("Время после сдвигания = $timeInMillsCorrected ms")
    val count4beatsBefore = (timeInMillsCorrected / (beatMs * 4))
    // println("Перед первым временем находится как минимум $count4beatsBeafore тактов по 4 бита")
    val different = count4beatsBefore * (beatMs * 4)
    // println("Надо сдвинуть время на $different ms")
    timeInMillsCorrected -= different
    // println("После сдвига время находится от начала в $timeInMillsCorrected ms и это должно быть меньше, чем ${(beatMs * 4).toLong()} ms")
    // println("Результат = $result")
    return ((timeInMillsCorrected / (beatMs)) % 4) + 1
}

fun getBeatNumberByTimecode(timeInTimecode: String, beatMs: Long, firstBeatTimecode: String): Long {
    return getBeatNumberByMilliseconds(convertTimecodeToMilliseconds(timeInTimecode), beatMs, firstBeatTimecode)
}
fun getDurationInMilliseconds(start: String, end: String): Long {
    return convertTimecodeToMilliseconds(end) - convertTimecodeToMilliseconds(start)
}

fun getDiffInMilliseconds(firstTimecode: String, secondTimecode: String): Long {
    return convertTimecodeToMilliseconds(firstTimecode) - convertTimecodeToMilliseconds(secondTimecode)
}

fun getSymbolWidth(fontSizePt: Int): Double {
    // Получение ширины символа (в пикселях) для размера шрифта (в пунктах)
    return fontSizePt*0.6
}

fun getFontSizeBySymbolWidth(symbolWidthPx: Double): Int {
    // Получение размера шрифта (в пунктах) для ширины символа (в пикселах)
    return (symbolWidthPx/0.6).toInt()
}

fun replaceVowelOrConsonantLetters(str: String, isVowel: Boolean = true, replSymbol: String = " "): String {
    var result = ""
    str.forEach { symbol ->
        if ((symbol in LETTERS_VOWEL) == isVowel) result += replSymbol else result += symbol
    }
    return result
}
