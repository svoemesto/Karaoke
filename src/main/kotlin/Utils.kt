import com.google.gson.GsonBuilder
import mlt.MltObject
import mlt.MltObjectAlignmentX
import mlt.MltObjectAlignmentY
import mlt.MltObjectType
import mlt.MltText
import model.Fingerboard
import model.Marker
import model.MltNode
import model.MusicChord
import model.MusicNote
import model.Song
import model.SongVersion
import model.Subtitle
import java.awt.AlphaComposite
import java.awt.BasicStroke
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
import kotlin.streams.toList


fun main() {

//    createSettingsFilesForAll("/home/nsa/Documents/Караоке/Пикник")
//    createSettingsFilesForAll("/home/nsa/Documents/Караоке/Павел Кашин")
//    createSettingsFilesForAll("/home/nsa/Documents/Караоке/Ария")
//    createSettingsFilesForAll("/home/nsa/Documents/Караоке/Агата Кристи")
//    createSettingsFilesForAll("/home/nsa/Documents/Караоке/Король и Шут","m4a")
//    createSettingsFilesForAll("/home/nsa/Documents/Караоке/Високосный год","wav")

//    val musicChord = MusicChord.X7
//    val musicNote = MusicNote.C
//    GuitarString.values().forEach { gs ->
//        println(gs.getPrintedString(musicChord.getNotes(musicNote).map { it.first }))
//    }
//    for (i in 0 .. 12) {
//        val result = musicChord.getFingerboard(musicNote,i)
//
//        if (result.isNotEmpty()) {
//            println(result.joinToString("\n"))
//            println()
//        }
//    }

//    convertMarkersToSubtitles("/home/nsa/Documents/Караоке/Ундервуд/2020 - Человек-Лук/2 сцена Жить в эпоху перемен.kdenlive")
//    convertMarkersToSubtitles("/home/nsa/Documents/Караоке/Ундервуд/2020 - Человек-Лук/3 сцена Встреча на рынке.kdenlive")
//    convertMarkersToSubtitles("/home/nsa/Documents/Караоке/Ундервуд/2020 - Человек-Лук/4 сцена Мы справимся, мессир Конфликт.kdenlive")
//    convertMarkersToSubtitles("/home/nsa/Documents/Караоке/Ундервуд/2020 - Человек-Лук/5 сцена За прогресс.kdenlive")
//    convertMarkersToSubtitles("/home/nsa/Documents/Караоке/Ундервуд/2020 - Человек-Лук/6 сцена Welcome welcome welcome Танец Винегрет.kdenlive")
//    convertMarkersToSubtitles("/home/nsa/Documents/Караоке/Ундервуд/2020 - Человек-Лук/7 сцена Трансформация.kdenlive")
//    convertMarkersToSubtitles("/home/nsa/Documents/Караоке/Ундервуд/2020 - Человек-Лук/8 сцена Первый подвиг.kdenlive")
//    convertMarkersToSubtitles("/home/nsa/Documents/Караоке/Ундервуд/2020 - Человек-Лук/9 сцена Явление антагониста.kdenlive")
//    convertMarkersToSubtitles("/home/nsa/Documents/Караоке/Ундервуд/2020 - Человек-Лук/10 сцена Ботанический сад.kdenlive")
//    convertMarkersToSubtitles("/home/nsa/Documents/Караоке/Ундервуд/2020 - Человек-Лук/11 сцена Казино.kdenlive")

}

fun createSettingsFilesForAll(startFolder: String, extention: String = "flac") {
    val patternFileName = "(\\d{4}).*\\s\\((\\d{2})\\)\\s\\[(.*)\\]\\s-\\s(.*)\\.(.*)"
    val regexpFileName = Regex(patternFileName)
    val patternFolderName = "(\\d{4}).*\\s-\\s(.*)"
    val regexpFolderName = Regex(patternFolderName)

    val fileDemucs2tracks = File("$startFolder/demusc2track.run")
    val fileDemucs4tracks = File("$startFolder/demusc4track.run")
    val fileDemucs5tracks = File("$startFolder/demusc5track.run")
    val fileMainPairs = File("$startFolder/mainPairs.txt")

    var textFileDemucs2tracks = ""
    var textFileDemucs4tracks = ""
    var textFileDemucs5tracks = ""
    var textFileMainPairs = ""

    val listFiles = getListFiles(startFolder,extention)
    println("Всего файлов = ${listFiles.size}")
    listFiles.map{File(it)}.forEach { file ->
        val fileName = file.name

        val fileAbsolutePath = file.absolutePath
        val fileFolder = file.parent
        val folderName = file.parentFile.name
        val fileNamesMatchResult = regexpFileName.findAll(fileName).toList().firstOrNull()
        val folderNamesMatchResult = regexpFolderName.findAll(folderName).toList().firstOrNull()

        val songYear = fileNamesMatchResult?.let {fileNamesMatchResult.groups[1]?.value}
        val songTrack = fileNamesMatchResult?.let {fileNamesMatchResult.groups[2]?.value}
        val songAuthor = fileNamesMatchResult?.let {fileNamesMatchResult.groups[3]?.value}
        var songName = fileNamesMatchResult?.let {fileNamesMatchResult.groups[4]?.value}
        val songFormat = extention //fileNamesMatchResult?.let {fileNamesMatchResult.groups[5]?.value}
        val songAlbum = folderNamesMatchResult?.let {folderNamesMatchResult.groups[2]?.value}
        val settingFileName = fileAbsolutePath.substring(0,fileAbsolutePath.length-songFormat!!.length-1)+".settings"
        val textFileName = fileAbsolutePath.substring(0,fileAbsolutePath.length-songFormat!!.length-1)+".txt"
        val kdenliveFileName = fileAbsolutePath.substring(0,fileAbsolutePath.length-songFormat!!.length-1)+".kdenlive"
        val fileNameWOExt = fileName.substring(0, fileName.length-songFormat!!.length-1)

        if (songName != null) {
            songName = songName.uppercaseFirstLetter()
        }

        println("Year = $songYear")
        println("Track = $songTrack")
        println("Author = $songAuthor")
        println("Name = $songName")
        println("Format = $songFormat")
        println("Album = $songAlbum")
        println("settingFileName = $settingFileName")
        println()

        val pathToResultedModel = "$fileFolder/$DEMUCS_MODEL_NAME"
        val separatedStem = "vocals"
        val oldNoStemName = "$pathToResultedModel/${fileNameWOExt}-no_$separatedStem.wav"
        val newNoStemName = "$pathToResultedModel/${fileNameWOExt}-accompaniment.wav"

        val textDemucs2track = "python3 -m demucs -n $DEMUCS_MODEL_NAME -d cuda --filename \"{track}-{stem}.{ext}\" --two-stems=$separatedStem -o \"$fileFolder\" \"$fileAbsolutePath\"\n" +
                "mv \"$oldNoStemName\" \"$newNoStemName\"" + "\n"
        val textDemucs4track = "python3 -m demucs -n $DEMUCS_MODEL_NAME -d cuda --filename \"{track}-{stem}.{ext}\" -o \"$fileFolder\" \"$fileAbsolutePath\"\n"

        textFileDemucs2tracks += textDemucs2track
        textFileDemucs4tracks += textDemucs4track

        textFileDemucs5tracks += textDemucs2track
        textFileDemucs5tracks += textDemucs4track

        textFileMainPairs += "//        Pair(\"$fileFolder\",\"$fileNameWOExt\"),\n"

        val settingFile = File(settingFileName)
        if (!settingFile.exists()) {

            val text =
                "NAME=$songName"+"\n"+
                        "AUTHOR=$songAuthor" + "\n" +
                        "ALBUM=$songAlbum" + "\n" +
                        "YEAR=$songYear" + "\n" +
                        "FORMAT=$songFormat" + "\n" +
                        "TRACK=$songTrack" + "\n" +
                        "KEY=" + "\n" +
                        "BPM=" + "\n"
            settingFile.writeText(text)
        }

        val textFile = File(textFileName)
        if (!textFile.exists()) {
            val text = "\n"
            textFile.writeText(text)
        }


        val kdenliveTemplate = "<?xml version='1.0' encoding='utf-8'?>\n" +
                "<mlt LC_NUMERIC=\"C\" producer=\"main_bin\" version=\"7.10.0\" root=\"${fileFolder}\">\n" +
                " <profile frame_rate_num=\"60\" sample_aspect_num=\"1\" display_aspect_den=\"9\" colorspace=\"709\" progressive=\"1\" description=\"HD 1080p 60 fps\" display_aspect_num=\"16\" frame_rate_den=\"1\" width=\"1920\" height=\"1080\" sample_aspect_den=\"1\"/>\n" +
                " <producer id=\"producer0\" in=\"00:00:00.000\">\n" +
                "  <property name=\"eof\">pause</property>\n" +
                "  <property name=\"resource\">${DEMUCS_MODEL_NAME}/${fileNameWOExt}-vocals.wav</property>\n" +
                "  <property name=\"seekable\">1</property>\n" +
                "  <property name=\"audio_index\">0</property>\n" +
                "  <property name=\"video_index\">-1</property>\n" +
                "  <property name=\"mute_on_pause\">1</property>\n" +
                "  <property name=\"mlt_service\">avformat</property>\n" +
                "  <property name=\"kdenlive:clipname\">VOICE</property>\n" +
                "  <property name=\"kdenlive:clip_type\">1</property>\n" +
                "  <property name=\"kdenlive:folderid\">-1</property>\n" +
                "  <property name=\"kdenlive:id\">3</property>\n" +
                " </producer>\n" +
                " <producer id=\"producer1\" in=\"00:00:00.000\">\n" +
                "  <property name=\"eof\">pause</property>\n" +
                "  <property name=\"resource\">${DEMUCS_MODEL_NAME}/${fileNameWOExt}-accompaniment.wav</property>\n" +
                "  <property name=\"seekable\">1</property>\n" +
                "  <property name=\"audio_index\">0</property>\n" +
                "  <property name=\"video_index\">-1</property>\n" +
                "  <property name=\"mute_on_pause\">1</property>\n" +
                "  <property name=\"mlt_service\">avformat</property>\n" +
                "  <property name=\"kdenlive:clipname\">MUSIC</property>\n" +
                "  <property name=\"kdenlive:clip_type\">1</property>\n" +
                "  <property name=\"kdenlive:folderid\">-1</property>\n" +
                "  <property name=\"kdenlive:id\">2</property>\n" +
                " </producer>\n" +
                " <playlist id=\"main_bin\">\n" +
                "  <property name=\"kdenlive:docproperties.activeTrack\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.audioChannels\">2</property>\n" +
                "  <property name=\"kdenlive:docproperties.audioTarget\">1</property>\n" +
                "  <property name=\"kdenlive:docproperties.compositing\">1</property>\n" +
                "  <property name=\"kdenlive:docproperties.disablepreview\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.documentid\">1671265183813</property>\n" +
                "  <property name=\"kdenlive:docproperties.enableTimelineZone\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.enableexternalproxy\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.enableproxy\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.externalproxyparams\">./;GL;.LRV;./;GX;.MP4;./;GP;.LRV;./;GP;.MP4</property>\n" +
                "  <property name=\"kdenlive:docproperties.generateimageproxy\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.generateproxy\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.groups\">[\n" +
                "]\n" +
                "</property>\n" +
                "  <property name=\"kdenlive:docproperties.kdenliveversion\">22.08.3</property>\n" +
                "  <property name=\"kdenlive:docproperties.position\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.previewextension\"/>\n" +
                "  <property name=\"kdenlive:docproperties.previewparameters\"/>\n" +
                "  <property name=\"kdenlive:docproperties.profile\">atsc_1080p_60</property>\n" +
                "  <property name=\"kdenlive:docproperties.proxyextension\"/>\n" +
                "  <property name=\"kdenlive:docproperties.proxyimageminsize\">2000</property>\n" +
                "  <property name=\"kdenlive:docproperties.proxyimagesize\">800</property>\n" +
                "  <property name=\"kdenlive:docproperties.proxyminsize\">1000</property>\n" +
                "  <property name=\"kdenlive:docproperties.proxyparams\"/>\n" +
                "  <property name=\"kdenlive:docproperties.proxyresize\">640</property>\n" +
                "  <property name=\"kdenlive:docproperties.scrollPos\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.seekOffset\">30000</property>\n" +
                "  <property name=\"kdenlive:docproperties.version\">1.04</property>\n" +
                "  <property name=\"kdenlive:docproperties.verticalzoom\">1</property>\n" +
                "  <property name=\"kdenlive:docproperties.videoTarget\">-1</property>\n" +
                "  <property name=\"kdenlive:docproperties.zonein\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.zoneout\">75</property>\n" +
                "  <property name=\"kdenlive:docproperties.zoom\">8</property>\n" +
                "  <property name=\"kdenlive:expandedFolders\"/>\n" +
                "  <property name=\"kdenlive:documentnotes\"/>\n" +
                "  <property name=\"xml_retain\">1</property>\n" +
                "  <entry producer=\"producer0\" in=\"00:00:00.000\"/>\n" +
                "  <entry producer=\"producer1\" in=\"00:00:00.000\"/>\n" +
                " </playlist>\n" +
                " <producer id=\"black_track\" in=\"00:00:00.000\" out=\"00:10:59.333\">\n" +
                "  <property name=\"eof\">continue</property>\n" +
                "  <property name=\"resource\">black</property>\n" +
                "  <property name=\"aspect_ratio\">1</property>\n" +
                "  <property name=\"mlt_service\">color</property>\n" +
                "  <property name=\"mlt_image_format\">rgba</property>\n" +
                "  <property name=\"set.test_audio\">0</property>\n" +
                " </producer>\n" +
                " <playlist id=\"playlist0\">\n" +
                "  <property name=\"kdenlive:audio_track\">1</property>\n" +
                " </playlist>\n" +
                " <playlist id=\"playlist1\">\n" +
                "  <property name=\"kdenlive:audio_track\">1</property>\n" +
                " </playlist>\n" +
                " <tractor id=\"tractor0\" in=\"00:00:00.000\">\n" +
                "  <property name=\"kdenlive:audio_track\">1</property>\n" +
                "  <property name=\"kdenlive:trackheight\">69</property>\n" +
                "  <property name=\"kdenlive:timeline_active\">1</property>\n" +
                "  <property name=\"kdenlive:collapsed\">28</property>\n" +
                "  <property name=\"kdenlive:thumbs_format\"/>\n" +
                "  <property name=\"kdenlive:audio_rec\"/>\n" +
                "  <track hide=\"both\" producer=\"playlist0\"/>\n" +
                "  <track hide=\"both\" producer=\"playlist1\"/>\n" +
                "  <filter id=\"filter0\">\n" +
                "   <property name=\"window\">75</property>\n" +
                "   <property name=\"max_gain\">20dB</property>\n" +
                "   <property name=\"mlt_service\">volume</property>\n" +
                "   <property name=\"internal_added\">237</property>\n" +
                "   <property name=\"disable\">1</property>\n" +
                "  </filter>\n" +
                "  <filter id=\"filter1\">\n" +
                "   <property name=\"channel\">-1</property>\n" +
                "   <property name=\"mlt_service\">panner</property>\n" +
                "   <property name=\"internal_added\">237</property>\n" +
                "   <property name=\"start\">0.5</property>\n" +
                "   <property name=\"disable\">1</property>\n" +
                "  </filter>\n" +
                "  <filter id=\"filter2\">\n" +
                "   <property name=\"iec_scale\">0</property>\n" +
                "   <property name=\"mlt_service\">audiolevel</property>\n" +
                "   <property name=\"dbpeak\">1</property>\n" +
                "   <property name=\"disable\">1</property>\n" +
                "  </filter>\n" +
                " </tractor>\n" +
                " <playlist id=\"playlist2\">\n" +
                "  <property name=\"kdenlive:audio_track\">1</property>\n" +
                " </playlist>\n" +
                " <playlist id=\"playlist3\">\n" +
                "  <property name=\"kdenlive:audio_track\">1</property>\n" +
                " </playlist>\n" +
                " <tractor id=\"tractor1\" in=\"00:00:00.000\">\n" +
                "  <property name=\"kdenlive:audio_track\">1</property>\n" +
                "  <property name=\"kdenlive:trackheight\">246</property>\n" +
                "  <property name=\"kdenlive:timeline_active\">1</property>\n" +
                "  <property name=\"kdenlive:collapsed\">0</property>\n" +
                "  <property name=\"kdenlive:thumbs_format\"/>\n" +
                "  <property name=\"kdenlive:audio_rec\"/>\n" +
                "  <track hide=\"video\" producer=\"playlist2\"/>\n" +
                "  <track hide=\"video\" producer=\"playlist3\"/>\n" +
                "  <filter id=\"filter3\">\n" +
                "   <property name=\"window\">75</property>\n" +
                "   <property name=\"max_gain\">20dB</property>\n" +
                "   <property name=\"mlt_service\">volume</property>\n" +
                "   <property name=\"internal_added\">237</property>\n" +
                "   <property name=\"disable\">1</property>\n" +
                "  </filter>\n" +
                "  <filter id=\"filter4\">\n" +
                "   <property name=\"channel\">-1</property>\n" +
                "   <property name=\"mlt_service\">panner</property>\n" +
                "   <property name=\"internal_added\">237</property>\n" +
                "   <property name=\"start\">0.5</property>\n" +
                "   <property name=\"disable\">1</property>\n" +
                "  </filter>\n" +
                "  <filter id=\"filter5\">\n" +
                "   <property name=\"iec_scale\">0</property>\n" +
                "   <property name=\"mlt_service\">audiolevel</property>\n" +
                "   <property name=\"dbpeak\">1</property>\n" +
                "   <property name=\"disable\">1</property>\n" +
                "  </filter>\n" +
                " </tractor>\n" +
                " <tractor id=\"tractor2\" in=\"00:00:00.000\">\n" +
                "  <track producer=\"black_track\"/>\n" +
                "  <track producer=\"tractor0\"/>\n" +
                "  <track producer=\"tractor1\"/>\n" +
                "  <transition id=\"transition0\">\n" +
                "   <property name=\"a_track\">0</property>\n" +
                "   <property name=\"b_track\">1</property>\n" +
                "   <property name=\"mlt_service\">mix</property>\n" +
                "   <property name=\"kdenlive_id\">mix</property>\n" +
                "   <property name=\"internal_added\">237</property>\n" +
                "   <property name=\"always_active\">1</property>\n" +
                "   <property name=\"accepts_blanks\">1</property>\n" +
                "   <property name=\"sum\">1</property>\n" +
                "  </transition>\n" +
                "  <transition id=\"transition1\">\n" +
                "   <property name=\"a_track\">0</property>\n" +
                "   <property name=\"b_track\">2</property>\n" +
                "   <property name=\"mlt_service\">mix</property>\n" +
                "   <property name=\"kdenlive_id\">mix</property>\n" +
                "   <property name=\"internal_added\">237</property>\n" +
                "   <property name=\"always_active\">1</property>\n" +
                "   <property name=\"accepts_blanks\">1</property>\n" +
                "   <property name=\"sum\">1</property>\n" +
                "  </transition>\n" +
                "  <filter id=\"filter6\">\n" +
                "   <property name=\"window\">75</property>\n" +
                "   <property name=\"max_gain\">20dB</property>\n" +
                "   <property name=\"mlt_service\">volume</property>\n" +
                "   <property name=\"internal_added\">237</property>\n" +
                "   <property name=\"disable\">1</property>\n" +
                "  </filter>\n" +
                "  <filter id=\"filter7\">\n" +
                "   <property name=\"channel\">-1</property>\n" +
                "   <property name=\"mlt_service\">panner</property>\n" +
                "   <property name=\"internal_added\">237</property>\n" +
                "   <property name=\"start\">0.5</property>\n" +
                "   <property name=\"disable\">1</property>\n" +
                "  </filter>\n" +
                " </tractor>\n" +
                "</mlt>\n"

        val kdenliveFile = File(kdenliveFileName)
        if (!kdenliveFile.exists()) {
            kdenliveFile.writeText(kdenliveTemplate)
        }

    }

    if (!fileDemucs2tracks.exists()) fileDemucs2tracks.writeText(textFileDemucs2tracks)
    if (!fileDemucs4tracks.exists()) fileDemucs4tracks.writeText(textFileDemucs4tracks)
    if (!fileDemucs5tracks.exists()) fileDemucs5tracks.writeText(textFileDemucs5tracks)
    if (!fileMainPairs.exists()) fileMainPairs.writeText(textFileMainPairs)

}

fun getNewTone(tone: String, capo: Int): String {
    val noteAndTone = tone.split(" ")
    val nameChord = noteAndTone[0]
    val (chord, note) = MusicChord.getChordNote(nameChord)
    var newIndexNote = MusicNote.values().indexOf(note!!) - capo
    if (newIndexNote < 0) newIndexNote = MusicNote.values().size + newIndexNote
    val newNote = MusicNote.values()[newIndexNote]
    return "${newNote.names.first()} ${noteAndTone[1]}"
}
fun generateChordLayout(chordName: String, capo: Int): List<MltObject> {
    val chordNameAndFret = chordName.split("|")
    val nameChord = chordNameAndFret[0]
    val fretChord = if (chordNameAndFret.size > 1) chordNameAndFret[1].toInt() else 0
    val (chord, note) = MusicChord.getChordNote(nameChord)
    return if (chord!=null && note != null) generateChordLayout(chord, note, fretChord, capo) else emptyList()
}
fun generateChordLayout(chord: MusicChord, startRootNote: MusicNote, startInitFret: Int, capo: Int): List<MltObject> {

    var newIndexNote = MusicNote.values().indexOf(startRootNote) - capo
    if (newIndexNote < 0) newIndexNote = MusicNote.values().size + newIndexNote
    val note = MusicNote.values()[newIndexNote]
    var fret = startInitFret - capo
    if (fret < 0) fret = 0

    var fingerboards: List<Fingerboard> = chord.getFingerboard(note, if (fret == 0) note.defaultRootFret else fret, capo)

    var nextFret = fret
    while (fingerboards.isEmpty()) {
        nextFret += 1
        fingerboards = chord.getFingerboard(note, if (nextFret == 0) note.defaultRootFret else nextFret)
    }

    val initFret = fingerboards[0].rootFret
    val result:MutableList<MltObject> = mutableListOf()
    var chordLayoutW = (Karaoke.frameHeightPx / 4).toInt()
    var chordLayoutH = chordLayoutW

    val chordName = "${note.names.first()}${chord.names.first()}"
    val chordNameMltText = Karaoke.chordLayoutChordNameMltText.copy(chordName)
//    chordNameMltText.text = chordName

    val fretW = (chordLayoutW / 6.0).toInt()
    var fretNumberTextH = 0
    val mltShapeFingerCircleDiameter = fretW/2
    val fretRectangleMltShape = Karaoke.chordLayoutFretsRectangleMltShape.copy()

    // Бэкграунд
    result.add(MltObject(
        layoutW = chordLayoutW,
        layoutH = chordLayoutH,
        _shape = Karaoke.chordLayoutBackgroundRectangleMltShape,
        alignmentX = MltObjectAlignmentX.LEFT,
        alignmentY = MltObjectAlignmentY.TOP,
        _x = 0,
        _y = 0,
        _w = chordLayoutW,
        _h = chordLayoutH
    ))

    // Название аккорда
    val mltTextChordName = MltObject(
        layoutW = chordLayoutW,
        layoutH = chordLayoutH,
        _shape = chordNameMltText,
        alignmentX = MltObjectAlignmentX.CENTER,
        alignmentY = MltObjectAlignmentY.TOP,
        _x = chordLayoutW/2,
        _y = 0,
        _h = (chordLayoutH * 0.2).toInt()
    )
    result.add(mltTextChordName)

    // Номера ладов
    val firstFret = if (initFret == 0) 1 else initFret
    for (fret in firstFret+capo..(firstFret+capo+3)) {
        val fretNumberMltText = Karaoke.chordLayoutFretsNumbersMltText.copy(fret.toString())
//        fretNumberMltText.text = fret.toString()

        val mltTextFretNumber = MltObject(
            layoutW = chordLayoutW,
            layoutH = chordLayoutH,
            _shape = fretNumberMltText,
            alignmentX = MltObjectAlignmentX.CENTER,
            alignmentY = MltObjectAlignmentY.TOP,
            _x = fretW * (fret - firstFret + 1 - capo) + fretW/2,
            _y = mltTextChordName.h,
            _h = (chordLayoutH * 0.1).toInt()
        )
        fretNumberTextH = mltTextFretNumber.h
        result.add(mltTextFretNumber)
    }

    val mltShapeFretRectangleH = (chordLayoutH - (mltTextChordName.h + 2*fretNumberTextH)) / 5

    // Прямоугольники ладов

    for (string in 0..4) {
        // Порожек или каподастр
        if (initFret == 0) {
            val nutRectangleMltShape = if (capo == 0) Karaoke.chordLayoutNutsRectangleMltShape.copy() else Karaoke.chordLayoutСapoRectangleMltShape.copy()
            val mltShapeNutRectangle = MltObject(
                layoutW = chordLayoutW,
                layoutH = chordLayoutH,
                _shape = nutRectangleMltShape,
                alignmentX = MltObjectAlignmentX.RIGHT,
                alignmentY = MltObjectAlignmentY.TOP,
                _x = fretW,
                _y = mltTextChordName.h + fretNumberTextH + mltShapeFretRectangleH*(string) + mltShapeFingerCircleDiameter/2,
                _w = fretW/5,
                _h = mltShapeFretRectangleH
            )
            result.add(mltShapeNutRectangle)
        }
        for (fret in 1..4) {
            val mltShapeFretRectangle = MltObject(
                layoutW = chordLayoutW,
                layoutH = chordLayoutH,
                _shape = fretRectangleMltShape,
                alignmentX = MltObjectAlignmentX.CENTER,
                alignmentY = MltObjectAlignmentY.TOP,
                _x = fretW * fret + fretW/2,
                _y = mltTextChordName.h + fretNumberTextH + mltShapeFretRectangleH*(string) + mltShapeFingerCircleDiameter/2,
                _w = fretW,
                _h = mltShapeFretRectangleH
            )
            result.add(mltShapeFretRectangle)
        }
    }

    // Распальцовка
    fingerboards.forEach { fingerboard ->

        // Приглушение струны
        if (fingerboard.muted) {
            val mutedRectangleMltShape = Karaoke.chordLayoutMutedRectangleMltShape.copy()
            val mltShapeMutedRectangle = MltObject(
                layoutW = chordLayoutW,
                layoutH = chordLayoutH,
                _shape = mutedRectangleMltShape,
                alignmentX = MltObjectAlignmentX.LEFT,
                alignmentY = MltObjectAlignmentY.TOP,
                _x = fretW,
                _y = mltTextChordName.h + fretNumberTextH + mltShapeFretRectangleH*(fingerboard.guitarString.number-1) + mltShapeFingerCircleDiameter/2 - fretRectangleMltShape.shapeOutline/2,
                _w = fretW*4,
                _h = fretRectangleMltShape.shapeOutline
            )
            result.add(mltShapeMutedRectangle)
        }

        if (!((initFret == 0 && fingerboard.fret == 0) || fingerboard.muted)) {
            val fingerCircleMltShape = Karaoke.chordLayoutFingerCircleMltShape.copy()
            val mltShapeFingerCircle = MltObject(
                layoutW = chordLayoutW,
                layoutH = chordLayoutH,
                _shape = fingerCircleMltShape,
                alignmentX = MltObjectAlignmentX.LEFT,
                alignmentY = MltObjectAlignmentY.TOP,
                _x = fretW * (fingerboard.fret - initFret + (if (initFret != 0) 1 else 0)) + fretW/2 - (mltShapeFingerCircleDiameter)/2,
                _y = mltTextChordName.h + fretNumberTextH + mltShapeFretRectangleH*(fingerboard.guitarString.number-1) + mltShapeFingerCircleDiameter/2 - mltShapeFingerCircleDiameter/2,
                _w = mltShapeFingerCircleDiameter,
                _h = mltShapeFingerCircleDiameter
            )
            result.add(mltShapeFingerCircle)
        }


    }

    // Барре (если первый лад не нулевой)
    if (initFret != 0) {
        val fingerCircleMltShape = Karaoke.chordLayoutFingerCircleMltShape.copy()
        fingerCircleMltShape.type = MltObjectType.ROUNDEDRECTANGLE
        val mltShapeFingerCircle = MltObject(
            layoutW = chordLayoutW,
            layoutH = chordLayoutH,
            _shape = fingerCircleMltShape,
            alignmentX = MltObjectAlignmentX.LEFT,
            alignmentY = MltObjectAlignmentY.TOP,
            _x = fretW + fretW/2 - (mltShapeFingerCircleDiameter)/2,
            _y = mltTextChordName.h + fretNumberTextH + mltShapeFingerCircleDiameter/2 - mltShapeFingerCircleDiameter/2,
            _w = mltShapeFingerCircleDiameter,
            _h = mltShapeFretRectangleH*5 +  mltShapeFingerCircleDiameter
        )
        result.add(mltShapeFingerCircle)
    }

    return result
}

fun getChordLayoutPicture(mltObjects:List<MltObject>): BufferedImage {

    val imageType = BufferedImage.TYPE_INT_ARGB

    if (mltObjects.isEmpty()) {
        val resultImage = BufferedImage((Karaoke.frameHeightPx/4).toInt(), (Karaoke.frameHeightPx/4).toInt(), imageType)
        val graphics2D = resultImage.graphics as Graphics2D
        val opaque = 1f
        val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)
        graphics2D.composite = alphaChannel
        graphics2D.color = Color.BLACK
        graphics2D.fillRect(0,0,(Karaoke.frameHeightPx/4).toInt(), (Karaoke.frameHeightPx/4).toInt())
        graphics2D.dispose()
        return resultImage
    }
    val resultImage = BufferedImage(mltObjects[0].layoutW, mltObjects[0].layoutH, imageType)
    val graphics2D = resultImage.graphics as Graphics2D


    mltObjects.forEach { obj ->

        when (obj.shape.type) {
            MltObjectType.TEXT -> {
                val opaque = obj.shape.shapeColor.alpha / 255f
                val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)
                graphics2D.composite = alphaChannel
                graphics2D.color = obj.shape.shapeColor
                graphics2D.stroke = BasicStroke(obj.shape.shapeOutline.toFloat())

                val textToOverlay = (obj.shape as MltText).text
                graphics2D.font = (obj.shape as MltText).font
                graphics2D.drawString(textToOverlay, obj.x, obj.y + obj.h - obj.h/4)

            }
            else -> {

                var opaque = obj.shape.shapeColor.alpha / 255f
                var alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)
                graphics2D.composite = alphaChannel
                graphics2D.color = obj.shape.shapeColor

                when (obj.shape.type) {
                    MltObjectType.RECTANGLE -> graphics2D.fillRect(obj.x,obj.y,obj.w, obj.h)
                    MltObjectType.CIRCLE -> graphics2D.fillOval(obj.x,obj.y,obj.w, obj.h)
                    MltObjectType.ROUNDEDRECTANGLE -> graphics2D.fillRoundRect(obj.x,obj.y,obj.w, obj.h, Integer.min(obj.w, obj.h), Integer.min(obj.w, obj.h))
                    else -> {}
                }

                opaque = obj.shape.shapeOutlineColor.alpha / 255f
                alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)
                graphics2D.composite = alphaChannel
                graphics2D.stroke = BasicStroke(obj.shape.shapeOutline.toFloat())

                graphics2D.color = obj.shape.shapeOutlineColor
                when (obj.shape.type) {
                    MltObjectType.RECTANGLE -> graphics2D.drawRect(obj.x,obj.y,obj.w, obj.h)
                    MltObjectType.CIRCLE -> graphics2D.drawOval(obj.x,obj.y,obj.w, obj.h)
                    MltObjectType.ROUNDEDRECTANGLE -> graphics2D.drawRoundRect(obj.x,obj.y,obj.w, obj.h, Integer.min(obj.w, obj.h), Integer.min(obj.w, obj.h))
                    else -> {}
                }

            }
        }
    }
    graphics2D.dispose()
    return resultImage
}

fun getFontSizeByHeight(heightPx: Int, font: Font): Int {
    var fontSize = 1
    while (getTextWidthHeightPx("0", Font(font.fontName, font.style, fontSize)).second < heightPx) {
        fontSize += 1
    }
    return fontSize-1
}

fun getFileNameByMasks(pathToFolder: String, startWith: String, suffixes: List<String>,extension: String): String {

    val files = Files.walk(Path(pathToFolder))
        .filter(Files::isRegularFile)
        .map { it.toString() }
        .filter{ it.endsWith(extension) && it.startsWith("${pathToFolder}/$startWith")}
        .map { Path(it).toFile().name }
        .toList()
    suffixes.forEach { suffix ->
        val filename = files.firstOrNull{it.startsWith("${startWith}${suffix}")}
        if (filename != null) return filename
    }
    return ""

}

fun getSongChordsPicture(song: Song, mltNode: MltNode): BufferedImage {

    val songTextSymbolsGroup = mltNode.body as MutableList<MltNode>
    val startViewport = songTextSymbolsGroup.first { it.name == "startviewport" }
    val startViewportFields = startViewport.fields["rect"]!!.split(",")
    val frameW = startViewportFields[2].toInt()
    val frameH = startViewportFields[3].toInt()
    // Находим количество страниц, на которые надо разделить текст, чтобы ширина была больше высоты
    var countPages = 0
    do {
        countPages++
    } while ((frameW.toDouble() * countPages) / (frameH.toDouble() / countPages) < 1.2 )

    // Находим минимальную высоту страницы
    var minPageH = frameH / (countPages)

    val opaque: Float = 1f
    val colorBack = Color(255,255,255,255)
    val colorText = Color(0,0,0,255)
    val colorChord = Color(255,0,0,255)
    var fontText = Font("Montserrat SemiBold", 0, 10)
    val imageType = BufferedImage.TYPE_INT_ARGB
    val resultImage = BufferedImage(frameW, frameH, imageType)
    val graphics2D = resultImage.graphics as Graphics2D
    val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)

    graphics2D.composite = alphaChannel
    graphics2D.background = colorBack
    graphics2D.color = colorBack
    graphics2D.fillRect(0,0,frameW, frameH)
    graphics2D.color = colorBack
    graphics2D.font = fontText

    var currPage = 1

    data class PicturePage(
        val number: Int,
        var h: Int,
        var lines: MutableList<MltNode>
    )
    val pages: MutableList<PicturePage> = mutableListOf()
    var currentH = 0
    var listLines: MutableList<MltNode> = mutableListOf()
    songTextSymbolsGroup.filter {it.name == "item"} .forEach { songTextSymbols ->
        val nodePosition = (songTextSymbols.body as MutableList<*>)[0] as MltNode
        val nodeText = (songTextSymbols.body as MutableList<*>)[1] as MltNode
        val x = nodePosition.fields["x"]!!.toInt()
        val y = nodePosition.fields["y"]!!.toInt()
        val textToOverlay = nodeText.body as String
        val isChord = (nodeText.fields["font"]!!.split(" ")[0] == Karaoke.chordsFont.font.fontName.split(" ")[0])
        val fontTextTmp = Font(nodeText.fields["font"], 0, nodeText.fields["font-pixel-size"]!!.toInt())
        graphics2D.font = fontTextTmp
        val fontMetrics = graphics2D.fontMetrics
        val rectH = fontMetrics.getStringBounds(textToOverlay, graphics2D).height.toInt()
        currentH = y + rectH
        val totalH = minPageH + pages.sumOf { it.h } // Полная высота - минимальная высота плюс высоты уже найденных страниц
        // Если текущая высота больше полной высоты и строчка не аккорд - переход на следующую страницу
        if (currentH > totalH && isChord) {
            val picturePage = PicturePage(number = currPage, h = currentH - pages.sumOf { it.h } - rectH, lines = listLines)
            pages.add(picturePage)
            currPage++
            listLines = mutableListOf()
            listLines.add(songTextSymbols)
//            println("${picturePage.number} - ${picturePage.h} - ${picturePage.lines.last().body}")
        } else {
            listLines.add(songTextSymbols)
        }
    }
    val picturePage = PicturePage(number = currPage, h = currentH - pages.sumOf { it.h }, lines = listLines)
    pages.add(picturePage)

    var totalH = 0
    val bis: MutableList<BufferedImage> = mutableListOf()
    pages.forEach { picturePage ->

        val resultImagePage = BufferedImage(frameW, picturePage.h+20, imageType)
        val graphics2Dpage = resultImagePage.graphics as Graphics2D
        val alphaChannelPage = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)

        graphics2Dpage.composite = alphaChannelPage
        graphics2Dpage.background = colorBack
        graphics2Dpage.color = colorBack
        graphics2Dpage.fillRect(0,0,frameW, picturePage.h+20)
        graphics2Dpage.color = colorBack
        graphics2Dpage.font = fontText

        picturePage.lines.forEach { songTextSymbols ->
            val nodePosition = (songTextSymbols.body as MutableList<*>)[0] as MltNode
            val nodeText = (songTextSymbols.body as MutableList<*>)[1] as MltNode
            val x = nodePosition.fields["x"]!!.toInt()
            val y = nodePosition.fields["y"]!!.toInt() - totalH
            val textToOverlay = (nodeText.body as String).replace("&amp;amp;", "&")
            val isChord = (nodeText.fields["font"]!!.split(" ")[0] == Karaoke.chordsFont.font.fontName.split(" ")[0])
            graphics2Dpage.color = if (isChord) colorChord else colorText
            fontText = Font(nodeText.fields["font"], 0, (nodeText.fields["font-pixel-size"]!!.toInt() / if (isChord) Karaoke.chordsHeightCoefficient else 1.0).toInt())
            graphics2Dpage.font = fontText
            val fontMetrics = graphics2Dpage.fontMetrics
            val rectH = fontMetrics.getStringBounds(textToOverlay, graphics2Dpage).height.toInt()
            val newY = y + rectH
            graphics2Dpage.drawString(textToOverlay, x, newY)
        }
        graphics2Dpage.dispose()
        bis.add(resultImagePage)
        totalH += picturePage.h
    }

    val fullW = frameW * pages.size
    var fullH = (210 * fullW / 297.0).toInt()

    val resultImages = BufferedImage(fullW, pages.maxOf { it.h } + 20, imageType)
    val graphics2Dresult = resultImages.graphics as Graphics2D
    graphics2Dresult.composite = alphaChannel
    graphics2Dresult.background = colorBack
    graphics2Dresult.color = colorBack
    graphics2Dresult.fillRect(0,0,fullW, pages.maxOf { it.h } + 20)
    bis.forEachIndexed{ index, bi ->
        graphics2Dresult.drawImage(bi, frameW * index, 0, null)
    }
    graphics2Dresult.dispose()

    val nameW = fullW
    val nameH = Integer.max(fullH - (pages.maxOf { it.h } + 20),200)

    val resultImageName = BufferedImage(nameW, nameH, imageType)
    val graphics2name = resultImageName.graphics as Graphics2D
    graphics2name.composite = alphaChannel
    graphics2name.background = colorBack
    graphics2name.color = colorBack
    graphics2name.fillRect(0,0,nameW, nameH)
    graphics2name.color = colorText
    val textToOverlay = "${song.settings.author} - ${song.settings.year} - «${song.settings.songName}» (${song.settings.key}, ${song.settings.bpm} bpm)"
    var rectW = 0
    var rectH = 0
    fontText = Font("Montserrat SemiBold", 0, 10)
    do {
        fontText = Font(fontText.name, fontText.style, fontText.size+1)
        graphics2name.font = fontText
        val fontMetrics = graphics2name.fontMetrics
        val rect = fontMetrics.getStringBounds(textToOverlay, graphics2name)
        rectW = rect.width.toInt()
        rectH = rect.height.toInt()
    } while (!(rectH > (nameH * 0.7) || rectW > (nameW * 0.7)))

    var centerX = (nameW - rectW) / 2
    var centerY = (nameH - rectH) / 2 + rectH
    graphics2name.drawString(textToOverlay, centerX, centerY)
    graphics2name.dispose()
    fullH = resultImageName.height + resultImages.height
    val resultImagesAndName = BufferedImage(fullW, fullH, imageType)
    val graphics2DresultAndName = resultImagesAndName.graphics as Graphics2D
    graphics2DresultAndName.composite = alphaChannel
    graphics2DresultAndName.background = colorBack
    graphics2DresultAndName.color = colorBack
    graphics2DresultAndName.fillRect(0,0,fullW, fullH)
    graphics2DresultAndName.drawImage(resultImageName, 0, 0, null)
    graphics2DresultAndName.drawImage(resultImages, 0, nameH, null)

    graphics2DresultAndName.dispose()

    return resultImagesAndName
}

fun createSongChordsPicture(song: Song, fileName: String, songVersion: SongVersion, isBluetoothDelay: Boolean, mltNode: MltNode) {
    if (songVersion == SongVersion.CHORDS && isBluetoothDelay == false) {
        val resultImage = getSongChordsPicture(song, mltNode)
        val file = File(fileName)
        ImageIO.write(resultImage, "png", file)
    }
}
fun createSongPicture(song: Song, fileName: String, songVersion: SongVersion, isBluetoothDelay: Boolean) {
    val caption = songVersion.text
    val comment: String = "${songVersion.textForDescription}${if (isBluetoothDelay) " с задержкой видео на ${Karaoke.timeOffsetBluetoothSpeakerMs}ms" else ""}"
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
    } while (!(rectH > 430 || rectW > (frameW * 0.95)))

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

    graphics2D.drawImage(biLogoAlbum, 260, 50, null)
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

//    graphics2D.drawImage(biLogoAlbum, 50, 50, null)
//    graphics2D.drawImage(biLogoAuthor, 710, 50, null)

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

            val startTimecode = convertFramesToTimecode(currMarker.pos, 60.0)
            val endTimecode = convertFramesToTimecode(nextMarker.pos, 60.0)

            val subtitle = Subtitle(
                startTimecode = startTimecode,
                endTimecode = endTimecode,
                mltText = Karaoke.voices[0].groups[0].mltText.copy(subText),
                isLineStart = isLineStart,
                isLineEnd = isLineEnd
            )
            subtitles.add(subtitle)
        }

        var textSubtitleFile = ""
        for (index in 0 until subtitles.size) {
            val subtitle = subtitles[index]
            textSubtitleFile += "${index+1}\n${subtitle.startTimecode} --> ${subtitle.endTimecode}\n${subtitle.mltText.text}\n\n"
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
    return Files.walk(Path(pathToFolder)).filter(Files::isRegularFile).map { it.toString() }.filter{ it.endsWith(extention) && it.startsWith("${pathToFolder}/$startWith")}.toList().sorted()
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

class Ribbon(private val input: String) {
    private var position = -1
    private val length: Int
    private var flag = -1
    private var startSyllableIndex = 0
    private var endSyllableIndex = 0

    init {
        length = input.length
    }

    fun setEndSyllableIndex() {
        endSyllableIndex = position
    }

    fun extractSyllable(): String {
        val result = input.substring(startSyllableIndex, endSyllableIndex + 1)
        startSyllableIndex = endSyllableIndex + 1
        flag = position
        endSyllableIndex = 0
        return result
    }

    fun readCurrentPosition(): Char {
        check(!(position < 0 || position > length - 1))
        return input[position]
    }

    fun setFlag() {
        flag = position
    }

    fun rewindToFlag() {
        if (flag >= 0) {
            position = flag
        }
    }

    fun moveHeadForward(): Boolean {
        return if (position + 1 < length) {
            position++
            true
        } else {
            false
        }
    }
}

class MainRibbon {
    val vowels = "аеёиоуыюяэАЕЁИОУЫЮЯЭeuioayYEUIOAїіє"
    val nonPairConsonant = "лйрнмЛЙРНМ.,:-"
    fun syllables(input: String?): List<String> {
        val result: MutableList<String> = ArrayList()
        val ribbon = Ribbon(input!!)
        while (ribbon.moveHeadForward()) {
            ribbon.setFlag()
            if (checkVowel(ribbon.readCurrentPosition())) {
                if (ribbon.moveHeadForward() && ribbon.moveHeadForward()) {
                    if (checkVowel(ribbon.readCurrentPosition())) {
                        ribbon.rewindToFlag()
                        ribbon.setEndSyllableIndex()
                        result.add(ribbon.extractSyllable())
                        continue
                    }
                }
                ribbon.rewindToFlag()
                if (ribbon.moveHeadForward() && checkSpecialConsonant(ribbon.readCurrentPosition())) {
                    ribbon.setEndSyllableIndex()
                    result.add(ribbon.extractSyllable())
                    continue
                }
                ribbon.rewindToFlag()
                if (hasMoreVowels(ribbon)) {
                    ribbon.rewindToFlag()
                    ribbon.setEndSyllableIndex()
                    result.add(ribbon.extractSyllable())
                    continue
                } else {
                    while (ribbon.moveHeadForward());
                    ribbon.setEndSyllableIndex()
                    result.add(ribbon.extractSyllable())
                }
            }
        }
        return result
    }

    fun checkVowel(ch: Char): Boolean {
        return vowels.contains(ch.toString())
    }

    fun hasMoreVowels(ribbon: Ribbon): Boolean {
        while (ribbon.moveHeadForward()) {
            if (checkVowel(ribbon.readCurrentPosition())) {
                return true
            }
        }
        return false
    }

    fun checkSpecialConsonant(ch: Char): Boolean {
        return nonPairConsonant.contains(ch.toString())
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val mainRibbon = MainRibbon()
            println(mainRibbon.syllables("Он"))
        }
    }
}