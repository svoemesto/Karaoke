//import model.Lyric
import mlt.getMlt
import model.SongVoiceLine
import model.ProducerType
import model.Settings
import model.Song
import model.SongOutputFile
import model.SongVersion
import model.SongVoiceLineSymbol
import model.TransformProperty
import java.awt.Font
import java.io.File
import java.lang.Integer.min
import java.nio.file.Files
import java.nio.file.Path

fun createKaraokeAll(pathToSettingsFile: String) {
    val settings = Settings(pathToSettingsFile)
    createKaraoke(Song(settings, SongVersion.LYRICS), false)
    createKaraoke(Song(settings, SongVersion.KARAOKE), false)
    createKaraoke(Song(settings, SongVersion.CHORDS), false)
    createKaraoke(Song(settings, SongVersion.LYRICS), true)
    createKaraoke(Song(settings, SongVersion.KARAOKE), true)
    createKaraoke(Song(settings, SongVersion.CHORDS), true)
}
fun createKaraoke(song: Song, isBluetoothDelay: Boolean) {

    println("Создаём ${song.songVersion.name}${if (isBluetoothDelay) " for Bluetooth" else ""}: ${song.settings.author} / ${song.settings.songName}")
//    println("Аккорды: ${song.accords}")

    val param = mutableMapOf<String, Any?>()

    param["SONG_VERSION"] = song.songVersion
    param["SONG_NAME"] = song.settings.songName
    param["ID_BLUETOOTH_DELAY"] = isBluetoothDelay

    param["COUNT_VOICES"] = song.voices.size
    param["LINE_SPACING"] = LINE_SPACING
    param["SHADOW"] = SHADOW
    param["TYPEWRITER"] = TYPEWRITER
    param["ALIGNMENT"] = ALIGNMENT

    val maxTextWidthPx = Karaoke.frameWidthPx.toDouble() - Karaoke.songtextStartPositionXpx * 2      // maxTextWidth - максимальная ширина текста = ширина экрана минус 2 отступа

    // Ширина в пикселах суммарной самой длинной строки
    var maxTextWidthPxByFontSize = song.voices.sumOf { it.maxWidthLinePx } + Karaoke.songtextStartPositionXpx*(song.voices.size-1)

    // Максимальный размер шрифта берем из дефолтного значения
    var fontSongtextSizePt = Karaoke.voices[0].groups[0].songtextTextMltText.font.size
    val step = if (maxTextWidthPxByFontSize > maxTextWidthPx) -1 else 1
    while (true) {
        if ((maxTextWidthPxByFontSize > maxTextWidthPx && step < 0) || (maxTextWidthPxByFontSize < maxTextWidthPx && step > 0)) {
            fontSongtextSizePt += step
            maxTextWidthPxByFontSize = (song.voices.sumOf{getTextWidthHeightPx(it.maxWidthLineText, Karaoke.voices[0].groups[0].songtextTextMltText.font.name, Karaoke.voices[0].groups[0].songtextTextMltText.font.style, fontSongtextSizePt).first} + Karaoke.songtextStartPositionXpx*(song.voices.size-1).toLong()).toLong()
        } else {
            break
        }
    }

    val fontChordsSizePt = (fontSongtextSizePt * Karaoke.chordsHeightCoefficient).toInt()

    val fontSongtext = Font(Karaoke.voices[0].groups[0].songtextTextMltText.font.name, Karaoke.voices[0].groups[0].songtextTextMltText.font.style, fontSongtextSizePt)
    val fontChords = Font(Karaoke.chordsFont.font.name, Karaoke.chordsFont.font.style, fontChordsSizePt)

    val symbolSongtextHeightPx = getTextWidthHeightPx("0", fontSongtext).second
    val symbolSongtextWidthPx = getTextWidthHeightPx("0", fontSongtext).first

    val symbolChordsHeightPx = getTextWidthHeightPx("0", fontChords).second
    val symbolChordsWidthPx = getTextWidthHeightPx("0", fontChords).first

    val quarterNoteLengthMs = if (song.settings.ms == 0L) (60000.0 / song.settings.bpm).toLong() else song.settings.ms // Находим длительность звучания 1/4 ноты в миллисекундах
    val halfNoteLengthMs = quarterNoteLengthMs * 2

    var currentVoiceOffset = 0L

    for (voiceId in 0 until song.voices.size) {

        val songVoice = song.voices[voiceId]

        if (voiceId > 0) {
            currentVoiceOffset += (getTextWidthHeightPx(song.voices[voiceId-1].maxWidthLineText, Karaoke.voices[0].groups[0].songtextTextMltText.font.name, Karaoke.voices[0].groups[0].songtextTextMltText.font.style, fontSongtextSizePt).first + Karaoke.songtextStartPositionXpx).toLong()
        }

        val counters = listOf<MutableList<String>>(
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        counters[0].add("00:00:00.000=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
        counters[1].add("00:00:00.000=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
        counters[2].add("00:00:00.000=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
        counters[3].add("00:00:00.000=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
        counters[4].add("00:00:00.000=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")

        val beats = listOf<MutableList<String>>(
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val propRectSongtextLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propRectChordsLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propRectFaderChordsLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propRectFingerboardLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propProgressLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propHeaderLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propFlashLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propGuidesValue = mutableListOf<String>()
        val guidesTypes = listOf<Long>(8, 7, 4, 3)

        val propRectSongtextValueLineOddEven = listOf<MutableList<String>>(
            mutableListOf(),
            mutableListOf()
        )

        val propRectChordsValueLineOddEven = listOf<MutableList<String>>(
            mutableListOf(),
            mutableListOf()
        )

        val voiceSetting = Karaoke.voices[min(voiceId, Karaoke.voices.size-1)]
        voiceSetting.replaceFontSize(fontSongtextSizePt)

        var currentGroup = 0 // Получаем номер текущей группы
        var groupSetting = voiceSetting.groups[min(currentGroup, voiceSetting.groups.size-1)] // Получаем настройки для текущей группы
        songVoice.lines.forEach { voiceLine ->
            val fontText = Font(groupSetting.songtextTextMltText.font.name, groupSetting.songtextTextMltText.font.style, fontSongtextSizePt)
            val fontBeat = Font(groupSetting.songtextBeatMltText.font.name, groupSetting.songtextBeatMltText.font.style, fontSongtextSizePt)
            voiceLine.symbols.forEach {lineSymbol ->
                lineSymbol.font = if (lineSymbol.isBeat) fontBeat else fontText
            }
            val lineText = voiceLine.subtitles.map { it.text }.toList().joinToString("")
            currentGroup = voiceLine.subtitles.firstOrNull()?.group ?: 0 // Получаем номер текущей группы
            groupSetting = voiceSetting.groups[min(currentGroup, voiceSetting.groups.size-1)] // Получаем настройки для текущей группы
            var currentGroupText = ""

            voiceLine.fontText = fontText
            voiceLine.fontBeat = fontBeat

            for (subtitle in voiceLine.subtitles) {
                currentGroup = subtitle.group // Получаем номер текущей группы
                groupSetting = voiceSetting.groups[min(currentGroup, voiceSetting.groups.size-1)] // Получаем настройки для текущей группы
                val fontTextForSubtitle = Font(groupSetting.songtextTextMltText.font.name, groupSetting.songtextTextMltText.font.style, fontSongtextSizePt)
                val fontBeatForSubtitle = Font(groupSetting.songtextBeatMltText.font.name, groupSetting.songtextBeatMltText.font.style, fontSongtextSizePt)
                if (Karaoke.showMainBeatVowels && subtitle.isBeat) {
                    var textBeforeVowel = ""
                    var textVowel = ""
                    var textAfterVowel = ""
                    var isFoundVowel = false
                    for (i in subtitle.text.indices) {
                        val symbol = subtitle.text[i]
                        if (symbol in LETTERS_VOWEL) {
                            isFoundVowel = true
                            if (i>0) textBeforeVowel = subtitle.text.substring(0 until i)
                            textVowel = symbol.toString()
                            if (i<subtitle.text.length-1) textAfterVowel = subtitle.text.substring(i+1)
                            break
                        }
                    }
                    if (!isFoundVowel) {
                        currentGroupText += subtitle.text
                    } else {
                        currentGroupText += textBeforeVowel
                        if (currentGroupText != "") {
                            voiceLine.symbols.add(
                                SongVoiceLineSymbol(
                                    text = currentGroupText,
                                    font = fontTextForSubtitle,
                                    group = currentGroup,
                                    isBeat = false
                                )
                            )
                            currentGroupText = ""
                        }

                        currentGroupText += textVowel
                        if (currentGroupText != "") {
                            voiceLine.symbols.add(
                                SongVoiceLineSymbol(
                                    text = currentGroupText,
                                    font = fontBeatForSubtitle,
                                    group = currentGroup,
                                    isBeat = true
                                )
                            )
                            currentGroupText = ""
                        }
                        currentGroupText += textAfterVowel
                    }
                } else {
                    currentGroupText += subtitle.text
                }

            }

            if (currentGroupText != "") {
                voiceLine.symbols.add(
                    SongVoiceLineSymbol(
                        text = currentGroupText,
                        font = fontText,
                        group = currentGroup,
                        isBeat = false
                    )
                )
            }
        }

        var currentPositionEnd = 0L // Устанавливаем текущую позицию конца в ноль
        var endTimeHidingHeaderMs: Long? = null
        val voiceLinesSongtext = mutableListOf<SongVoiceLine>()
        val voiceLinesSongchords = mutableListOf<SongVoiceLine>()
        val voiceLinesChords = mutableListOf<SongVoiceLine>()

        songVoice.lines.forEach { voiceLine ->
            val silentDuration = convertTimecodeToMilliseconds(voiceLine.start) - currentPositionEnd
            var linesToInsert: Long = silentDuration / songVoice.maxDurationMs
            if (voiceLine == songVoice.lines.first() && linesToInsert == 0L) linesToInsert = 1L
            if (linesToInsert > 0) {
                val silentLineDuration: Long = silentDuration / linesToInsert
                for (i in 1..linesToInsert) {
                    val startDuration = convertMillisecondsToTimecode(currentPositionEnd) //  + silentLineDuration / 2
                    val endDuration = convertMillisecondsToTimecode(currentPositionEnd + silentLineDuration)
                    val emptyVoiceLine = SongVoiceLine(
                        subtitles = mutableListOf(),
                        symbols = mutableListOf(),
                        text = "",
                        start = startDuration,
                        end = endDuration,
                        startTp = null,
                        endTp = null,
                        isEmptyLine = true,
                        isNeedCounter = false,
                        isFadeLine = false,
                        isMaxLine = false,
                        durationMs = silentLineDuration,
                        fontText = voiceLine.fontText,
                        fontBeat = voiceLine.fontBeat
                    )

                    voiceLinesSongtext.add(emptyVoiceLine)
                    voiceLinesSongchords.add(emptyVoiceLine.copy())
                    voiceLinesChords.add(emptyVoiceLine.copy())

                    currentPositionEnd = currentPositionEnd + silentLineDuration //convertTimecodeToMilliseconds(endDuration)
                }
            }
            voiceLine.isNeedCounter = ((linesToInsert > 0 && !voiceLine.isEmptyLine) || voiceLine == songVoice.lines.first())
            voiceLinesSongtext.add(voiceLine)
            voiceLinesSongchords.add(voiceLine.copy())

            // ***************************************************************************************************************
            // Тут надо сформировать правильную строчку для voiceLinesChords
            // Находим аккорды, которые по времени попадают в текущую строку
            val chords = song.chords.filter { convertTimecodeToMilliseconds(it.timecode) in convertTimecodeToMilliseconds(voiceLine.start) until convertTimecodeToMilliseconds(voiceLine.end) }
            // Проходимся по всем аккордам, попавшим в текущую строку
            val symbolsChords = mutableListOf<SongVoiceLineSymbol>()
            chords.forEach { chord ->
                // Находим субтитр текущей строки, на который попадает аккорд
                val subtitle = voiceLine.subtitles.firstOrNull {convertTimecodeToMilliseconds(chord.timecode) in convertTimecodeToMilliseconds(it.startTimecode) until convertTimecodeToMilliseconds(it.endTimecode)}
                // Если такой субтитр найден
                if (subtitle != null) {
                    // Находим позицию первой гласной в тексте субтитра
//                    val firstVowelIndex = max(subtitle.text.getFirstVowelIndex(), 0)
                    val firstVowelIndex = 0
                    // Получаем текст строки до этого символа
                    var textBefore = ""
                    for (sub in voiceLine.subtitles) {
                        if (sub == subtitle) {
                            textBefore += subtitle.text.substring(0,firstVowelIndex)
                            break
                        } else {
                            textBefore += sub.text
                        }
                    }
                    symbolsChords.add(SongVoiceLineSymbol(text = chord.text, textBeforeChord = textBefore, font = fontChords))
                }
            }
            val voiceLineChords = voiceLine.copy()
            voiceLineChords.symbols = symbolsChords
            voiceLinesChords.add(voiceLineChords)

            // Формируем SongVoiceLine для аккордов текущей строки.
            // ***************************************************************************************************************

            currentPositionEnd = convertTimecodeToMilliseconds(voiceLine.end)
            if (!voiceLine.isEmptyLine) endTimeHidingHeaderMs = currentPositionEnd
        }

        if (convertTimecodeToMilliseconds(song.endTimecode) - endTimeHidingHeaderMs!! < 10000) endTimeHidingHeaderMs = convertTimecodeToMilliseconds(song.endTimecode) - 10000

        val workAreaSongtextHeightPx = symbolSongtextHeightPx * voiceLinesSongtext.size // Высота рабочей области
        val workAreaChordsHeightPx = (symbolSongtextHeightPx + symbolChordsHeightPx) * voiceLinesSongchords.size // Высота рабочей области
        val horizonPositionPx = (Karaoke.frameHeightPx / 2 + symbolSongtextHeightPx.toLong() / 2) - Karaoke.horizonOffsetPx    // horizonPosition - позиция горизонта = половина экрана + половина высоты символа - оффсет
        val songLengthMs = convertTimecodeToMilliseconds(song.endTimecode)
        val progressSymbolHalfWidth =  0 //(getTextWidthHeightPx(Karaoke.progressSymbol, Karaoke.progressFont.font).first/2).toLong()
        val kdeHeaderAuthor = song.settings.author
        val kdeHeaderTone = song.settings.key
        val kdeHeaderBpm = song.settings.bpm
        val kdeHeaderAlbum = song.settings.album
        val kdeHeaderYear = song.settings.year
        val kdeHeaderTrack = song.settings.track
        val kdeHeaderSongName = song.settings.songName
        val fontNameSizePt = Integer.min(getFontSizeBySymbolWidth(1100.0 / song.settings.songName.length), 80)
        val yOffset = 0 //-5

        propProgressLineValue.add("00:00:00.000=-${progressSymbolHalfWidth} $yOffset ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
        propProgressLineValue.add("${song.endTimecode}=${Karaoke.frameWidthPx - progressSymbolHalfWidth} $yOffset ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
        propFlashLineValue.add("00:00:00.000=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")

        val chordW = (Karaoke.frameHeightPx/4).toInt()
        val fingerboardW = (Karaoke.frameWidthPx - chordW + (song.chords.size + 1) * chordW).toInt()
        val fingerboardH = chordW

        param["VOICE${voiceId}_FINGERBOARD_W"] = fingerboardW
        param["VOICE${voiceId}_FINGERBOARD_H"] = fingerboardH
        param["VOICE${voiceId}_CHORD_W"] = chordW
        param["VOICE${voiceId}_CHORD_H"] = chordW
        param["VOICE${voiceId}_CHORDS"] = song.chords

        param["VOICE${voiceId}_SETTING"] = voiceSetting
        param["VOICE${voiceId}_OFFSET"] = currentVoiceOffset
        param["VOICE${voiceId}_WORK_AREA_SONGTEXT_HEIGHT_PX"] = workAreaSongtextHeightPx.toLong()
        param["VOICE${voiceId}_WORK_AREA_CHORDS_HEIGHT_PX"] = workAreaChordsHeightPx.toLong()
        param["VOICE${voiceId}_VOICELINES_SONGTEXT"] = voiceLinesSongtext
        param["VOICE${voiceId}_VOICELINES_SONGCHORDS"] = voiceLinesSongchords
        param["VOICE${voiceId}_VOICELINES_CHORDS"] = voiceLinesChords

        param["SYMBOL_SONGTEXT_HEIGHT_PX"] = symbolSongtextHeightPx
        param["SYMBOL_CHORDS_HEIGHT_PX"] = symbolChordsHeightPx
        param["HORIZON_POSITION_PX"] = horizonPositionPx
        param["COUNTER_POSITION_Y_PX"] = (horizonPositionPx - symbolSongtextHeightPx).toLong()
        param["VOICE${voiceId}_COUNTER_POSITION_X_PX"] = currentVoiceOffset + Karaoke.songtextStartPositionXpx - Karaoke.songtextStartOffsetXpx - symbolSongtextWidthPx
        param["SONG_LENGTH_MS"] = songLengthMs
        param["FONT_SONGTEXT_SIZE_PT"] = fontSongtextSizePt
        param["FONT_CHORDS_SIZE_PT"] = fontChordsSizePt

        param["HEADER_AUTHOR"] = kdeHeaderAuthor
        param["HEADER_TONE"] = kdeHeaderTone
        param["HEADER_BPM"] = kdeHeaderBpm
        param["HEADER_ALBUM"] = kdeHeaderAlbum
        param["HEADER_YEAR"] = kdeHeaderYear
        param["HEADER_TRACK"] = kdeHeaderTrack
        param["HEADER_SONG_NAME"] = kdeHeaderSongName
        param["HEADER_SONG_NAME_FONT_SIZE"] = fontNameSizePt
        param["LOGOAUTHOR_PATH"] = "${song.settings.rootFolder}/LogoAuthor.png"
        param["LOGOALBUM_PATH"] = "${song.settings.rootFolder}/LogoAlbum.png"

        voiceLinesSongtext.forEachIndexed { indexLine, voiceLineSongtext ->

            val voiceLineSongchords = voiceLinesSongchords[indexLine]

            val startTpSongtext = TransformProperty(
                time = voiceLineSongtext.start,
                x = currentVoiceOffset,
                y = horizonPositionPx - ((indexLine + 1) * symbolSongtextHeightPx).toLong(),
                w = Karaoke.frameWidthPx,
                h = workAreaSongtextHeightPx.toLong(),
                opacity = if (indexLine in 1 until voiceLinesSongtext.size-1) 1.0 else 0.0
            )

            val startTpChords = TransformProperty(
                time = voiceLineSongchords.start,
                x = currentVoiceOffset,
                y = horizonPositionPx - ((indexLine + 1) * (symbolSongtextHeightPx + symbolChordsHeightPx)).toLong(),
                w = Karaoke.frameWidthPx,
                h = workAreaChordsHeightPx.toLong(),
                opacity = if (indexLine in 1 until voiceLinesSongchords.size-1) 1.0 else 0.0
            )

            var time = voiceLineSongtext.end
            // Если текущий элемент не последний - надо проверить начало следующего элемента
            if (indexLine != voiceLinesSongtext.size - 1) {
                val nextVoiceLineSongtext = voiceLinesSongtext[indexLine + 1] // Находим следующую строку
                val nextVoiceLineChords = voiceLinesSongchords[indexLine + 1] // Находим следующую строку
                val diffInMills = getDiffInMilliseconds(nextVoiceLineSongtext.start, voiceLineSongtext.end) // Находим разницу во времени между текущей строкой и следующей
                if (diffInMills < Karaoke.transferMinimumMsBetweenLinesToScroll) {                            // Если эта разница меньше 200 мс
                    voiceLineSongtext.end = nextVoiceLineSongtext.start             // Сдвигаем конец текущей линии и конец последнего титра в ней до начала следующей
                    voiceLineSongchords.end = nextVoiceLineChords.start             // Сдвигаем конец текущей линии и конец последнего титра в ней до начала следующей
                    if (voiceLineSongtext.subtitles.isNotEmpty()) {
                        voiceLineSongtext.subtitles.last().endTimecode = voiceLineSongtext.end
                        voiceLineSongchords.subtitles.last().endTimecode = voiceLineSongchords.end
                        time = voiceLineSongtext.subtitles.last().startTimecode       // сдвигаем время classes.TransformProperty к началу последнего титра текущей строки
                    }
                }
            }

            val endTpSongtext = TransformProperty(
                time = time,
                x = currentVoiceOffset,
                y = horizonPositionPx - ((indexLine + 1) * symbolSongtextHeightPx).toLong(),
                w = Karaoke.frameWidthPx,
                h = workAreaSongtextHeightPx.toLong(),
                opacity = if (indexLine in 1 until voiceLinesSongtext.size-1) 1.0 else 0.0
            )
            val endTpChords = TransformProperty(
                time = time,
                x = currentVoiceOffset,
                y = horizonPositionPx - ((indexLine + 1) * (symbolSongtextHeightPx + symbolChordsHeightPx)).toLong(),
                w = Karaoke.frameWidthPx,
                h = workAreaChordsHeightPx.toLong(),
                opacity = if (indexLine in 1 until voiceLinesSongchords.size-1) 1.0 else 0.0
            )

            voiceLineSongtext.startTp = startTpSongtext
            voiceLineSongtext.endTp = endTpSongtext

            voiceLineSongchords.startTp = startTpChords
            voiceLineSongchords.endTp = endTpChords

            val ddd = ""
        }


        // Настало время прописать classes.TransformProperty для заливок



        voiceLinesSongtext.forEachIndexed { indexLine, voiceLineSongtext ->

            val voiceLineSongchords = voiceLinesSongchords[indexLine]

            val nextVoiceLineSongtext = if (indexLine < voiceLinesSongtext.size - 1) voiceLinesSongtext[indexLine + 1] else null //Следующая строка
            val nextVoiceLineChords = if (indexLine < voiceLinesSongchords.size - 1) voiceLinesSongchords[indexLine + 1] else null //Следующая строка
            propRectSongtextLineValue.add("${voiceLineSongtext.startTp?.time}=${voiceLineSongtext.startTp?.x} ${voiceLineSongtext.startTp?.y} ${voiceLineSongtext.startTp?.w} ${voiceLineSongtext.startTp?.h} ${voiceLineSongtext.startTp?.opacity}")
            propRectChordsLineValue.add("${voiceLineSongchords.startTp?.time}=${voiceLineSongchords.startTp?.x} ${voiceLineSongchords.startTp?.y} ${voiceLineSongchords.startTp?.w} ${voiceLineSongchords.startTp?.h} ${voiceLineSongchords.startTp?.opacity}")

            if (!voiceLineSongtext.isEmptyLine) { // Если текущая строка пустая - ничего больше не делаем. Переход между строками будет плавны
                propRectSongtextLineValue.add("${voiceLineSongtext.endTp?.time}=${voiceLineSongtext.endTp?.x} ${voiceLineSongtext.endTp?.y} ${voiceLineSongtext.endTp?.w} ${voiceLineSongtext.endTp?.h} ${voiceLineSongtext.endTp?.opacity}")
                propRectChordsLineValue.add("${voiceLineSongchords.endTp?.time}=${voiceLineSongchords.endTp?.x} ${voiceLineSongchords.endTp?.y} ${voiceLineSongchords.endTp?.w} ${voiceLineSongchords.endTp?.h} ${voiceLineSongchords.endTp?.opacity}")

                propFlashLineValue.add("${convertFramesToTimecode(convertTimecodeToFrames(voiceLineSongtext.startTp!!.time)-2)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
                propFlashLineValue.add("${voiceLineSongtext.startTp?.time}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
                propFlashLineValue.add("${convertMillisecondsToTimecode(convertTimecodeToMilliseconds(voiceLineSongtext.startTp!!.time)+1000)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")

                val currSubtitle = voiceLineSongtext.subtitles[0] // Получаем первый титр текущей строки (он точно есть, т.к. строка не пустая)
//                val startTime = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(currSubtitle.startTimecode) - (1000 / Karaoke.frameFps + 1).toLong()) // Время начала анимации = времени начала этого титра минус оффсет
                val startTime = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(currSubtitle.startTimecode) - Karaoke.timeOffsetStartFillingLineMs) // Время начала анимации = времени начала этого титра минус оффсет
                val x = Karaoke.songtextStartPositionXpx - Karaoke.songtextStartOffsetXpx + currentVoiceOffset
                val partOfSymbolSongtextHeightPx = symbolSongtextHeightPx / 7
                val songtextH = symbolSongtextHeightPx - 2*partOfSymbolSongtextHeightPx // Высота = высоте символа
                val chordsH = songtextH // symbolSongtextHeightPx + symbolChordsHeightPx // Высота = высоте символа
                var songtextY: Double = horizonPositionPx - symbolSongtextHeightPx + partOfSymbolSongtextHeightPx // Координата y = позиция горизонта - высота символа
                var chordsY: Double = songtextY // horizonPositionPx - (symbolSongtextHeightPx + symbolChordsHeightPx) // Координата y = позиция горизонта - высота символа

                var songtextYdelta = currSubtitle.longSubtitleInt * partOfSymbolSongtextHeightPx
                var songtextHdelta = currSubtitle.longSubtitleInt * (2*partOfSymbolSongtextHeightPx)
                val propRectSongtextValueFade = "$startTime=$x ${songtextY.toLong()} 1 ${songtextH.toLong()} 0.0" // Свойство трансформации заливки с полной прозрачностью
                val propRectChordsValueFade = "$startTime=$x ${chordsY.toLong()} 1 ${chordsH.toLong()} 0.0" // Свойство трансформации заливки с полной прозрачностью
                propRectSongtextValueLineOddEven[indexLine % 2].add(propRectSongtextValueFade)
                propRectChordsValueLineOddEven[indexLine % 2].add(propRectChordsValueFade)

                val startW = Karaoke.songtextStartOffsetXpx //.toDouble() // Смещаем стартовую позицию w на величину TITLE_OFFSET_START_X_PX
                val timeFirstIn = currSubtitle.startTimecode // Время - начало текущего титра
                val wFirstIn = startW + voiceLineSongtext.getSubtitleXpx(currSubtitle).toLong()
                val propRectSongtextValueFirstIn = "$timeFirstIn=$x ${songtextY.toLong()} $wFirstIn ${songtextH.toLong()} ${if (indexLine % 2 == 0) Karaoke.voices[voiceId].fill.oddOpacity else Karaoke.voices[voiceId].fill.evenOpacity}" // Начало анимации титра - в начальной позиции титра с непрозрачностью 60%
                val propRectChordsValueFirstIn = "$timeFirstIn=$x ${chordsY.toLong()} $wFirstIn ${chordsH.toLong()} ${if (indexLine % 2 == 0) Karaoke.voices[voiceId].fill.oddOpacity else Karaoke.voices[voiceId].fill.evenOpacity}" // Начало анимации титра - в начальной позиции титра с непрозрачностью 60%
                propRectSongtextValueLineOddEven[indexLine % 2].add(propRectSongtextValueFirstIn)
                propRectChordsValueLineOddEven[indexLine % 2].add(propRectChordsValueFirstIn)

                for (indexSub in 0..(voiceLineSongtext.subtitles.size) - 2) { // Проходимся по титрам текущей линии от первого до предпоследнего

                    val nextSubSongtext = voiceLineSongtext.subtitles[indexSub + 1] // Следующий титр

                    songtextYdelta = (voiceLineSongtext.subtitles[indexSub].longSubtitleInt * partOfSymbolSongtextHeightPx)
                    songtextHdelta = (voiceLineSongtext.subtitles[indexSub].longSubtitleInt * (2*partOfSymbolSongtextHeightPx))

                    var timeOut = convertFramesToTimecode(convertTimecodeToFrames(nextSubSongtext.startTimecode)-2) // Время - начало следующего титра
                    var wOut = startW + voiceLineSongtext.getSubtitleXpx(nextSubSongtext).toLong()
                    var propRectSongtextValueOut = "$timeOut=$x ${(songtextY-songtextYdelta).toLong()} $wOut ${(songtextH+songtextHdelta).toLong()} ${if (indexLine % 2 == 0) Karaoke.voices[voiceId].fill.oddOpacity else Karaoke.voices[voiceId].fill.evenOpacity}" // Конец анимации титра - в конечной позиции титра с непрозрачностью 60%
                    var propRectChordsValueOut = "$timeOut=$x ${(chordsY-songtextYdelta).toLong()} $wOut ${(chordsH+songtextHdelta).toLong()} ${if (indexLine % 2 == 0) Karaoke.voices[voiceId].fill.oddOpacity else Karaoke.voices[voiceId].fill.evenOpacity}" // Конец анимации титра - в конечной позиции титра с непрозрачностью 60%
                    propRectSongtextValueLineOddEven[indexLine % 2].add(propRectSongtextValueOut)
                    propRectChordsValueLineOddEven[indexLine % 2].add(propRectChordsValueOut)

                    timeOut = nextSubSongtext.startTimecode // Время - начало следующего титра
                    wOut = startW + voiceLineSongtext.getSubtitleXpx(nextSubSongtext).toLong()
                    propRectSongtextValueOut = "$timeOut=$x ${songtextY.toLong()} $wOut ${songtextH.toLong()} ${if (indexLine % 2 == 0) Karaoke.voices[voiceId].fill.oddOpacity else Karaoke.voices[voiceId].fill.evenOpacity}" // Конец анимации титра - в конечной позиции титра с непрозрачностью 60%
                    propRectChordsValueOut = "$timeOut=$x ${chordsY.toLong()} $wOut ${chordsH.toLong()} ${if (indexLine % 2 == 0) Karaoke.voices[voiceId].fill.oddOpacity else Karaoke.voices[voiceId].fill.evenOpacity}" // Конец анимации титра - в конечной позиции титра с непрозрачностью 60%
                    propRectSongtextValueLineOddEven[indexLine % 2].add(propRectSongtextValueOut)
                    propRectChordsValueLineOddEven[indexLine % 2].add(propRectChordsValueOut)

                }

                // На этом этапе мы закрасили все титры линии, кроме последнего

                val currSub = voiceLineSongtext.subtitles[(voiceLineSongtext.subtitles.size) - 1]   // Текущий титр - последний титр текущей строки
                val nextSub = if (nextVoiceLineSongtext?.subtitles != null && nextVoiceLineSongtext.subtitles.isNotEmpty()) nextVoiceLineSongtext.subtitles[0] else null // Следующий титр - первый титр следующей строки (может не быть)
                val diffInMills = if (nextVoiceLineSongtext != null) getDiffInMilliseconds(nextVoiceLineSongtext.start, voiceLineSongtext.end) else Karaoke.transferMinimumMsBetweenLinesToScroll

                if (diffInMills < Karaoke.transferMinimumMsBetweenLinesToScroll) {
                    songtextY -= symbolSongtextHeightPx
                    chordsY -= (symbolSongtextHeightPx + symbolChordsHeightPx)
                }

                songtextYdelta = (currSub.longSubtitleInt * partOfSymbolSongtextHeightPx)
                songtextHdelta = (currSub.longSubtitleInt * (2*partOfSymbolSongtextHeightPx))

                var timeOut = currSub.endTimecode // Время - конец текущего титра
                var wOut = startW + voiceLineSongtext.widthLinePx + Karaoke.songtextStartOffsetXpx
                var propRectSongtextValueOut = "$timeOut=$x ${(songtextY-songtextYdelta).toLong()} $wOut ${(songtextH+songtextHdelta).toLong()} ${if (indexLine % 2 == 0) Karaoke.voices[voiceId].fill.oddOpacity else Karaoke.voices[voiceId].fill.evenOpacity}"
                var propRectChordsValueOut = "$timeOut=$x ${(chordsY-songtextYdelta).toLong()} $wOut ${(chordsH+songtextHdelta).toLong()} ${if (indexLine % 2 == 0) Karaoke.voices[voiceId].fill.oddOpacity else Karaoke.voices[voiceId].fill.evenOpacity}"
                propRectSongtextValueLineOddEven[indexLine % 2].add(propRectSongtextValueOut)
                propRectChordsValueLineOddEven[indexLine % 2].add(propRectChordsValueOut)

                if (diffInMills > Karaoke.transferMinimumMsBetweenLinesToScroll) {
                    songtextY -= symbolSongtextHeightPx
                    chordsY -= (symbolSongtextHeightPx + symbolChordsHeightPx)
                }

//                if (nextSub == null) {
//                    songtextY -= symbolSongtextHeightPx
//                    chordsY -= (symbolSongtextHeightPx + symbolChordsHeightPx)
//                }

                var timeFadeOut = if (diffInMills < Karaoke.transferMinimumMsBetweenLinesToScroll) {
                    if (nextSub != null) {
                        nextSub.endTimecode
                    } else {
                        if (nextVoiceLineSongtext != null) {
                            if (nextVoiceLineSongtext.isEmptyLine) {
                                songtextY -= symbolSongtextHeightPx
                                chordsY -= (symbolSongtextHeightPx + symbolChordsHeightPx)
                            }
                            convertMillisecondsToTimecode(convertTimecodeToMilliseconds(nextVoiceLineSongtext.end) - Karaoke.timeOffsetStartFillingLineMs)
                        } else {
                            convertMillisecondsToTimecode(convertTimecodeToMilliseconds(currSub.endTimecode) + Karaoke.transferMinimumMsBetweenLinesToScroll)
                        }
                    }
//                    nextSub?.endTimecode  ?: nextVoiceLineSongtext?.start ?: convertMillisecondsToTimecode(convertTimecodeToMilliseconds(currSub.endTimecode) + Karaoke.transferMinimumMsBetweenLinesToScroll)
                } else {
                    if (nextSub != null) {
                        nextSub.startTimecode
                    } else {
                        if (nextVoiceLineSongtext != null) {
                            if (nextVoiceLineSongtext.isEmptyLine) {
                                songtextY -= symbolSongtextHeightPx
                                chordsY -= (symbolSongtextHeightPx + symbolChordsHeightPx)
                            }
                            convertMillisecondsToTimecode(convertTimecodeToMilliseconds(nextVoiceLineSongtext.start) - Karaoke.timeOffsetStartFillingLineMs)
                        } else {
                            convertMillisecondsToTimecode(convertTimecodeToMilliseconds(currSub.endTimecode) + Karaoke.transferMinimumMsBetweenLinesToScroll)
                        }
                    }
//                    nextSub?.startTimecode  ?: nextVoiceLineSongtext?.start ?: convertMillisecondsToTimecode(convertTimecodeToMilliseconds(currSub.endTimecode) + Karaoke.transferMinimumMsBetweenLinesToScroll)
                }
                timeFadeOut = convertFramesToTimecode(convertTimecodeToFrames(timeFadeOut) - if(nextSub == null) 2 else 0)
                val propRectSongtextValueFadeOut = "$timeFadeOut=$x ${(songtextY-songtextYdelta).toLong()} $wOut ${(songtextH+songtextHdelta).toLong()} 0.0"
                val propRectChordsValueFadeOut = "$timeFadeOut=$x ${(chordsY-songtextYdelta).toLong()} $wOut ${(chordsH+songtextHdelta).toLong()} 0.0"
                propRectSongtextValueLineOddEven[indexLine % 2].add(propRectSongtextValueFadeOut)
                propRectChordsValueLineOddEven[indexLine % 2].add(propRectChordsValueFadeOut)
            }
        }

        // Настало время заняться счётчиками вступления.

        var startTimeFirstCounterMs: Long? = null
        voiceLinesSongtext.filter { it.isNeedCounter }
            .forEach { lyric -> // Проходимся по всем строкам, для которых нужен счётчик
                if (startTimeFirstCounterMs == null) startTimeFirstCounterMs = convertTimecodeToMilliseconds(lyric.start) - halfNoteLengthMs * 4
                for (counterNumber in 0..4) {
                    val startTimeMs = convertTimecodeToMilliseconds(lyric.start) - halfNoteLengthMs * counterNumber
                    val initTimeMs = convertFramesToMilliseconds(convertMillisecondsToFrames(startTimeMs) - 2)
                    val endTimeMs = convertFramesToMilliseconds(convertMillisecondsToFrames(startTimeMs + halfNoteLengthMs) - 2)
                    if (startTimeMs > 0) {
                        counters[counterNumber].add("${convertMillisecondsToTimecode(initTimeMs)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
                        counters[counterNumber].add("${convertMillisecondsToTimecode(startTimeMs)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
                        counters[counterNumber].add("${convertMillisecondsToTimecode(endTimeMs)}=0 ${-symbolSongtextHeightPx} ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")

                        propFlashLineValue.add("${convertMillisecondsToTimecode(initTimeMs)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
                        propFlashLineValue.add("${convertMillisecondsToTimecode(startTimeMs)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
                        propFlashLineValue.add("${convertMillisecondsToTimecode(endTimeMs)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")

                    }
                }
            }
        if (startTimeFirstCounterMs == null || startTimeFirstCounterMs!! < 0L) startTimeFirstCounterMs = 3000

        val kdeIn = "00:00:00.000"
        val kdeFadeIn = "00:00:01.000"
        val kdeOut = song.endTimecode.replace(",", ".")
        val kdeFadeOut = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(song.endTimecode) - 1000).replace(",", ".")

        propRectFaderChordsLineValue.add("${convertMillisecondsToTimecode(startTimeFirstCounterMs!!)}=0 -${fingerboardH} ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
        propRectFaderChordsLineValue.add("${convertMillisecondsToTimecode(startTimeFirstCounterMs!!+halfNoteLengthMs*4)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")

        propRectFingerboardLineValue.add("${convertMillisecondsToTimecode(startTimeFirstCounterMs!!)}=0 -${fingerboardH} ${fingerboardW} ${fingerboardH} 1.0")
        propRectFingerboardLineValue.add("${convertMillisecondsToTimecode(startTimeFirstCounterMs!!+halfNoteLengthMs*4)}=0 0 ${fingerboardW} ${fingerboardH} 1.0")
        var prevChordX = 0
        var prevChordTimeCode = kdeFadeIn
        var currChordX = 0
        song.chords.forEachIndexed { indexChords, chord ->
            val chordTimecode = chord.timecode
            val diffChordsMs = convertTimecodeToMilliseconds(chordTimecode) - convertTimecodeToMilliseconds(prevChordTimeCode)
            val movingMs = if (diffChordsMs > 1000) 1000 else (diffChordsMs/2).toInt()
            val startMoveTimecode = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(chordTimecode) - movingMs)
            val endMoveTimecode = chordTimecode
            currChordX = prevChordX - chordW
            propRectFingerboardLineValue.add("${startMoveTimecode}=${prevChordX} 0 ${fingerboardW} ${fingerboardH} 1.0")
            propRectFingerboardLineValue.add("${endMoveTimecode}=${currChordX} 0 ${fingerboardW} ${fingerboardH} 1.0")
            prevChordX = currChordX
        }
        propRectFingerboardLineValue.add("${convertMillisecondsToTimecode(endTimeHidingHeaderMs!!)}=${currChordX} 0 ${fingerboardW} ${fingerboardH} 1.0")
        propRectFingerboardLineValue.add("${convertMillisecondsToTimecode(endTimeHidingHeaderMs!!+halfNoteLengthMs*4)}=${currChordX} -${fingerboardH} ${fingerboardW} ${fingerboardH} 0.0")

        propRectFaderChordsLineValue.add("${convertMillisecondsToTimecode(endTimeHidingHeaderMs!!)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
        propRectFaderChordsLineValue.add("${convertMillisecondsToTimecode(endTimeHidingHeaderMs!!+halfNoteLengthMs*4)}=0 -${fingerboardH} ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")

//        // Такты
//        var delayMs = convertTimecodeToMilliseconds(song.beatTimecode) // + TIME_OFFSET_MS
//
//        // Такт в мс
//        val beatMs = if (song.settings.ms == 0L) (60000.0 / song.settings.bpm).toLong() else song.settings.ms// song.delay
//        val different = ((delayMs / (beatMs * 4)) - 1) * (beatMs * 4)
//        delayMs -= different
//
//        var currentPositionStartMs = 0L
//        var beatCounter = 1L
//        propGuidesValue.add("""{"comment": "Offset", "pos": ${convertMillisecondsToFrames(Karaoke.timeOffsetComputerMs.absoluteValue)}, "type": 0}""")
//        while ((delayMs + currentPositionStartMs + beatMs) < convertTimecodeToMilliseconds(song.endTimecode)) {
//
//            val tick = (beatCounter - 1) % 4
//            currentPositionStartMs = (delayMs + beatMs * (beatCounter - 1))// + TIME_OFFSET_MS
//            val currentPositionStartFrame = convertMillisecondsToFrames(currentPositionStartMs)
//
//            val currentPositionStartMs2fb = convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionStartMs) - 2, Karaoke.frameFps)
//            val currentPositionStartMs1fb = convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionStartMs) - 1, Karaoke.frameFps)
//            val currentPositionEndMs = convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionStartMs + (beatMs * (4 - tick))) - 3, Karaoke.frameFps)
//            val currentPositionEndMs1fa = convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionEndMs) + 1, Karaoke.frameFps)
//            val currentPositionEndMs2fa = convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionEndMs) + 2, Karaoke.frameFps)
//
//            val point0 = "${convertMillisecondsToTimecode(currentPositionStartMs2fb)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0"
//            val point1 = "${convertMillisecondsToTimecode(currentPositionStartMs1fb)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0"
//            val point2 = "${convertMillisecondsToTimecode(currentPositionStartMs)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0"
//            val point3 = "${convertMillisecondsToTimecode(currentPositionEndMs)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0"
//            val point4 = "${convertMillisecondsToTimecode(currentPositionEndMs1fa)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0"
//            val point5 = "${convertMillisecondsToTimecode(currentPositionEndMs2fa)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0"
//
//            beats[tick.toInt()].add(point0)
//            beats[tick.toInt()].add(point1)
//            beats[tick.toInt()].add(point2)
//            beats[tick.toInt()].add(point3)
//            beats[tick.toInt()].add(point4)
//            beats[tick.toInt()].add(point5)
//
//            if (tick == 0L) {
//                propGuidesValue.add("""{"comment": "|", "pos": $currentPositionStartFrame, "type": ${guidesTypes[tick.toInt()]}}""")
//            }
//
//            beatCounter += 1
//        }

        propHeaderLineValue.add("${convertMillisecondsToTimecode(startTimeFirstCounterMs!!)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
        propHeaderLineValue.add("${convertMillisecondsToTimecode(startTimeFirstCounterMs!!+halfNoteLengthMs*4)}=0 -492 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
        propHeaderLineValue.add("${convertMillisecondsToTimecode(endTimeHidingHeaderMs!!)}=0 -492 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
        propHeaderLineValue.add("${convertMillisecondsToTimecode(endTimeHidingHeaderMs!!+halfNoteLengthMs*4)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")

        val propRectSongtextValue = propRectSongtextLineValue.joinToString(";")
        val propRectChordsValue = propRectChordsLineValue.joinToString(";")
        val propRectFingerboardValue = propRectFingerboardLineValue.joinToString(";")
        val propRectFaderChordsValue = propRectFaderChordsLineValue.joinToString(";")
        val propProgressValue = propProgressLineValue.joinToString(";")
        val propHeaderValue = propHeaderLineValue.joinToString(";")
        val propFlashValue = propFlashLineValue.joinToString(";")
        val propSongtextFillOddValue = propRectSongtextValueLineOddEven[0].joinToString(";")
        val propChordsFillOddValue = propRectChordsValueLineOddEven[0].joinToString(";")
        val propSongtextFillEvenValue = propRectSongtextValueLineOddEven[1].joinToString(";")
        val propChordsFillEvenValue = propRectChordsValueLineOddEven[1].joinToString(";")
        val propFillCounter0Value = counters[0].joinToString(";")
        val propFillCounter1Value = counters[1].joinToString(";")
        val propFillCounter2Value = counters[2].joinToString(";")
        val propFillCounter3Value = counters[3].joinToString(";")
        val propFillCounter4Value = counters[4].joinToString(";")
        val propBeat1Value = beats[0].joinToString(";")
        val propBeat2Value = beats[1].joinToString(";")
        val propBeat3Value = beats[2].joinToString(";")
        val propBeat4Value = beats[3].joinToString(";")
        val propGuides = propGuidesValue.joinToString(",")


        val kdeInOffsetAudio = convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs - if(isBluetoothDelay) Karaoke.timeOffsetBluetoothSpeakerMs else 0) // convertMillisecondsToTimecode(convertTimecodeToMilliseconds(kdeIn) + max(Karaoke.timeOffsetStartFillingLineMs.toInt(),0).absoluteValue)
        val kdeInOffsetVideo = convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs) // convertMillisecondsToTimecode(convertTimecodeToMilliseconds(kdeIn) + max(-Karaoke.timeOffsetStartFillingLineMs.toInt(),0).absoluteValue)
        val kdeLengthMs = convertTimecodeToMilliseconds(song.endTimecode)
        val kdeLengthFrames = convertTimecodeToFrames(song.endTimecode, Karaoke.frameFps)

        param["SONG_ROOT_FOLDER"] = song.settings.rootFolder
        param["SONG_START_TIMECODE"] = kdeIn
        param["SONG_END_TIMECODE"] = kdeOut
        param["SPLASHSTART_END_TIMECODE"] = convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs)
        param["SONG_FADEIN_TIMECODE"] = kdeFadeIn
        param["SONG_FADEOUT_TIMECODE"] = kdeFadeOut
        param["SPLASHSTART_FADEOUT_TIMECODE"] = convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs - 1000).replace(",", ".")
        param["SONG_LENGTH_MS"] = kdeLengthMs
        param["SONG_LENGTH_FR"] = kdeLengthFrames
        param["GUIDES_PROPERTY"] = "[${propGuides}]"
        param["IN_OFFSET_AUDIO"] = kdeInOffsetAudio
        param["IN_OFFSET_VIDEO"] = kdeInOffsetVideo


        val templateSongText = getTemplateSongText(param,voiceId)
        val templateChordsText = getTemplateChords(param,voiceId)
        val templateHorizon = getTemplateHorizon(param)
        val templateFlash = getTemplateFlash(param)
        val templateProgress = getTemplateProgress(param)
        val templateWatermark = getTemplateWatermark(param)
        val templateMicrophone = getTemplateMicrophone(param)
        val templateFaderText = getTemplateFaderText(param)
        val templateFaderChords = getTemplateFaderChords(param)
        val templateFingerboard = getTemplateFingerboard(param)
        val templateHeader = getTemplateHeader(param)
        val templateSplashstart = getTemplateSplashstart(param)
        val templateCounter0 = getTemplateCounter(param,0, voiceId)
        val templateCounter1 = getTemplateCounter(param,1, voiceId)
        val templateCounter2 = getTemplateCounter(param,2, voiceId)
        val templateCounter3 = getTemplateCounter(param,3, voiceId)
        val templateCounter4 = getTemplateCounter(param,4, voiceId)
        val templateBeat1 = getTemplateBeat1(param)
        val templateBeat2 = getTemplateBeat2(param)
        val templateBeat3 = getTemplateBeat3(param)
        val templateBeat4 = getTemplateBeat4(param)

        param["${ProducerType.AUDIOSONG.text.uppercase()}${voiceId}_ID"] = idProducerAudioSong
        param["${ProducerType.AUDIOSONG.text.uppercase()}${voiceId}_PATH"] = song.settings.audioSongFileName

        param["${ProducerType.AUDIOMUSIC.text.uppercase()}${voiceId}_ID"] = idProducerAudioMusic
        param["${ProducerType.AUDIOMUSIC.text.uppercase()}${voiceId}_PATH"] = song.settings.audioMusicFileName

        param["${ProducerType.AUDIOVOCAL.text.uppercase()}${voiceId}_ID"] = idProducerAudioVocal
        param["${ProducerType.AUDIOVOCAL.text.uppercase()}${voiceId}_PATH"] = song.settings.audioVocalFileName
        param["HIDE_TRACTOR_${ProducerType.AUDIOVOCAL.text.uppercase()}${voiceId}"] = "both"

        param["${ProducerType.AUDIOBASS.text.uppercase()}${voiceId}_ID"] = idProducerAudioBass
        param["${ProducerType.AUDIOBASS.text.uppercase()}${voiceId}_PATH"] = song.settings.audioBassFileName

        param["${ProducerType.AUDIODRUMS.text.uppercase()}${voiceId}_ID"] = idProducerAudioDrums
        param["${ProducerType.AUDIODRUMS.text.uppercase()}${voiceId}_PATH"] = song.settings.audioDrumsFileName

        param["${ProducerType.SONGTEXT.text.uppercase()}${voiceId}_ID"] = idProducerSongText
        param["${ProducerType.SONGTEXT.text.uppercase()}${voiceId}_WORK_AREA_SONGTEXT_HEIGHT_PX"] = workAreaSongtextHeightPx.toLong()
        param["${ProducerType.SONGTEXT.text.uppercase()}${voiceId}_XML_DATA"] = templateSongText
        param["${ProducerType.SONGTEXT.text.uppercase()}${voiceId}_PROPERTY_RECT"] = propRectSongtextValue
        param["HIDE_TRACTOR_${ProducerType.SONGTEXT.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.CHORDS.text.uppercase()}${voiceId}_ID"] = idProducerChordsText
        param["${ProducerType.CHORDS.text.uppercase()}${voiceId}_WORK_AREA_CHORDS_HEIGHT_PX"] = workAreaChordsHeightPx.toLong()
        param["${ProducerType.CHORDS.text.uppercase()}${voiceId}_XML_DATA"] = templateChordsText
        param["${ProducerType.CHORDS.text.uppercase()}${voiceId}_PROPERTY_RECT"] = propRectChordsValue
        param["HIDE_TRACTOR_${ProducerType.CHORDS.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.HORIZON.text.uppercase()}${voiceId}_ID"] = idProducerHorizon
        param["${ProducerType.HORIZON.text.uppercase()}${voiceId}_XML_DATA"] = templateHorizon
        param["HIDE_TRACTOR_${ProducerType.HORIZON.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.FLASH.text.uppercase()}${voiceId}_ID"] = idProducerFlash
        param["${ProducerType.FLASH.text.uppercase()}${voiceId}_XML_DATA"] = templateFlash
        param["${ProducerType.FLASH.text.uppercase()}${voiceId}_PROPERTY_RECT"] = propFlashValue
        param["HIDE_TRACTOR_${ProducerType.FLASH.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.WATERMARK.text.uppercase()}${voiceId}_ID"] = idProducerWatermark
        param["${ProducerType.WATERMARK.text.uppercase()}${voiceId}_XML_DATA"] = templateWatermark
        param["HIDE_TRACTOR_${ProducerType.WATERMARK.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.FADERTEXT.text.uppercase()}${voiceId}_ID"] = idProducerFaderText
        param["${ProducerType.FADERTEXT.text.uppercase()}${voiceId}_XML_DATA"] = templateFaderText
        param["HIDE_TRACTOR_${ProducerType.FADERTEXT.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.FADERCHORDS.text.uppercase()}${voiceId}_ID"] = idProducerFaderChords
        param["${ProducerType.FADERCHORDS.text.uppercase()}${voiceId}_XML_DATA"] = templateFaderChords
        param["${ProducerType.FADERCHORDS.text.uppercase()}${voiceId}_PROPERTY_RECT"] = propRectFaderChordsValue
        param["HIDE_TRACTOR_${ProducerType.FADERCHORDS.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.FINGERBOARD.text.uppercase()}${voiceId}_ID"] = idProducerFingerboard
        param["${ProducerType.FINGERBOARD.text.uppercase()}${voiceId}_XML_DATA"] = templateFingerboard
        param["${ProducerType.FINGERBOARD.text.uppercase()}${voiceId}_PROPERTY_RECT"] = propRectFingerboardValue
        param["HIDE_TRACTOR_${ProducerType.FINGERBOARD.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.PROGRESS.text.uppercase()}${voiceId}_ID"] = idProducerProgress
        param["${ProducerType.PROGRESS.text.uppercase()}${voiceId}_XML_DATA"] = templateProgress
        param["${ProducerType.PROGRESS.text.uppercase()}${voiceId}_PROPERTY_RECT"] = propProgressValue
        param["HIDE_TRACTOR_${ProducerType.PROGRESS.text.uppercase()}${voiceId}"] = "audio"


        param["${ProducerType.FILLCOLORSONGTEXT.text.uppercase()}${voiceId}_EVEN_ID"] = idProducerFillColorSongtextEven
        param["${ProducerType.FILLCOLORSONGTEXT.text.uppercase()}${voiceId}_EVEN_PROPERTY_RECT"] = propSongtextFillEvenValue

        param["${ProducerType.FILLCOLORSONGTEXT.text.uppercase()}${voiceId}_ODD_ID"] = idProducerFillColorSongtextOdd
        param["${ProducerType.FILLCOLORSONGTEXT.text.uppercase()}${voiceId}_ODD_PROPERTY_RECT"] = propSongtextFillOddValue

        param["${ProducerType.FILLCOLORCHORDS.text.uppercase()}${voiceId}_EVEN_ID"] = idProducerFillColorChordsEven
        param["${ProducerType.FILLCOLORCHORDS.text.uppercase()}${voiceId}_EVEN_PROPERTY_RECT"] =  propChordsFillEvenValue

        param["${ProducerType.FILLCOLORCHORDS.text.uppercase()}${voiceId}_ODD_ID"] = idProducerFillColorChordsOdd
        param["${ProducerType.FILLCOLORCHORDS.text.uppercase()}${voiceId}_ODD_PROPERTY_RECT"] =  propChordsFillOddValue


        param["${ProducerType.HEADER.text.uppercase()}${voiceId}_ID"] = idProducerHeader
        param["${ProducerType.HEADER.text.uppercase()}${voiceId}_XML_DATA"] = templateHeader
        param["${ProducerType.HEADER.text.uppercase()}${voiceId}_PROPERTY_RECT"] = propHeaderValue
        param["HIDE_TRACTOR_${ProducerType.HEADER.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.SPLASHSTART.text.uppercase()}${voiceId}_ID"] = idProducerSplashstart
        param["${ProducerType.SPLASHSTART.text.uppercase()}${voiceId}_XML_DATA"] = templateSplashstart
        param["HIDE_TRACTOR_${ProducerType.SPLASHSTART.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.MICROPHONE.text.uppercase()}${voiceId}_ID"] = idProducerMicrophone
        param["${ProducerType.MICROPHONE.text.uppercase()}${voiceId}_XML_DATA"] = templateMicrophone

        param["${ProducerType.BACKGROUND.text.uppercase()}${voiceId}_ID"] = idProducerBackground
        param["${ProducerType.BACKGROUND.text.uppercase()}${voiceId}_PATH"] = getRandomFile(Karaoke.backgroundFolderPath, ".png")
        param["HIDE_TRACTOR_${ProducerType.BACKGROUND.text.uppercase()}${voiceId}"] = "audio"


        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}0_ID"] = idProducerCounter0
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}0_XML_DATA"] = templateCounter0
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}0_PROPERTY_RECT"] = propFillCounter0Value
        param["HIDE_TRACTOR_${ProducerType.COUNTER.text.uppercase()}${voiceId}0"] = "audio"

        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}1_ID"] = idProducerCounter1
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}1_XML_DATA"] = templateCounter1
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}1_PROPERTY_RECT"] = propFillCounter1Value
        param["HIDE_TRACTOR_${ProducerType.COUNTER.text.uppercase()}${voiceId}1"] = "audio"

        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}2_ID"] = idProducerCounter2
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}2_XML_DATA"] = templateCounter2
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}2_PROPERTY_RECT"] = propFillCounter2Value
        param["HIDE_TRACTOR_${ProducerType.COUNTER.text.uppercase()}${voiceId}2"] = "audio"

        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}3_ID"] = idProducerCounter3
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}3_XML_DATA"] = templateCounter3
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}3_PROPERTY_RECT"] = propFillCounter3Value
        param["HIDE_TRACTOR_${ProducerType.COUNTER.text.uppercase()}${voiceId}3"] = "audio"

        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}4_ID"] = idProducerCounter4
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}4_XML_DATA"] = templateCounter4
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}4_PROPERTY_RECT"] = propFillCounter4Value
        param["HIDE_TRACTOR_${ProducerType.COUNTER.text.uppercase()}${voiceId}4"] = "audio"


        param["${ProducerType.BEAT.text.uppercase()}${voiceId}1_ID"] = idProducerBeat1
        param["${ProducerType.BEAT.text.uppercase()}${voiceId}1_XML_DATA"] = templateBeat1
        param["${ProducerType.BEAT.text.uppercase()}${voiceId}1_PROPERTY_RECT"] = propBeat1Value
        param["HIDE_TRACTOR_${ProducerType.BEAT.text.uppercase()}${voiceId}1"] = "audio"

        param["${ProducerType.BEAT.text.uppercase()}${voiceId}2_ID"] = idProducerBeat2
        param["${ProducerType.BEAT.text.uppercase()}${voiceId}2_XML_DATA"] = templateBeat2
        param["${ProducerType.BEAT.text.uppercase()}${voiceId}2_PROPERTY_RECT"] = propBeat2Value
        param["HIDE_TRACTOR_${ProducerType.BEAT.text.uppercase()}${voiceId}2"] = "audio"

        param["${ProducerType.BEAT.text.uppercase()}${voiceId}3_ID"] = idProducerBeat3
        param["${ProducerType.BEAT.text.uppercase()}${voiceId}3_XML_DATA"] = templateBeat3
        param["${ProducerType.BEAT.text.uppercase()}${voiceId}3_PROPERTY_RECT"] = propBeat3Value
        param["HIDE_TRACTOR_${ProducerType.BEAT.text.uppercase()}${voiceId}3"] = "audio"

        param["${ProducerType.BEAT.text.uppercase()}${voiceId}4_ID"] = idProducerBeat4
        param["${ProducerType.BEAT.text.uppercase()}${voiceId}4_XML_DATA"] = templateBeat4
        param["${ProducerType.BEAT.text.uppercase()}${voiceId}4_PROPERTY_RECT"] = propBeat4Value
        param["HIDE_TRACTOR_${ProducerType.BEAT.text.uppercase()}${voiceId}4"] = "audio"

        param["${ProducerType.AUDIOVOCAL.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createAudioVocal
        param["${ProducerType.AUDIOMUSIC.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createAudioMusic
        param["${ProducerType.AUDIOSONG.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createAudioSong
        param["${ProducerType.AUDIOBASS.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createAudioBass
        param["${ProducerType.AUDIODRUMS.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createAudioDrums
        param["${ProducerType.BACKGROUND.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createBackground
        param["${ProducerType.MICROPHONE.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createMicrophone
        param["${ProducerType.HORIZON.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createHorizon
        param["${ProducerType.PROGRESS.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createProgress
        param["${ProducerType.FILLCOLORSONGTEXT.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createFillsSongtext
        param["${ProducerType.FILLCOLORCHORDS.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createFillsChords
        param["${ProducerType.SONGTEXT.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createSongtext
        param["${ProducerType.CHORDS.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createChords
        param["${ProducerType.FADERTEXT.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createFader
        param["${ProducerType.FADERCHORDS.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createFader
        param["${ProducerType.HEADER.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createHeader
        param["${ProducerType.BEAT.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createBeats
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createCounters
        param["${ProducerType.WATERMARK.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createWatermark

    }

    param["SONG_PROJECT_FILENAME"] = song.getOutputFilename(SongOutputFile.PROJECT, isBluetoothDelay)
    param["SONG_VIDEO_FILENAME"] = song.getOutputFilename(SongOutputFile.VIDEO, isBluetoothDelay)
    param["SONG_PICTURE_FILENAME"] = song.getOutputFilename(SongOutputFile.PICTURE, isBluetoothDelay)
    param["SONG_SUBTITLE_FILENAME"] = song.getOutputFilename(SongOutputFile.SUBTITLE, isBluetoothDelay)
    param["SONG_DESCRIPTION_FILENAME"] = song.getOutputFilename(SongOutputFile.DESCRIPTION, isBluetoothDelay)

    val templateProject = "<?xml version='1.0' encoding='utf-8'?>\n${getMlt(param)}"
    val fileProject = File(param["SONG_PROJECT_FILENAME"].toString())
    Files.createDirectories(Path.of(fileProject.parent))
    fileProject.writeText(templateProject)

//    val fileSub = File(param["SONG_SUBTITLE_FILENAME"].toString())
//    Files.createDirectories(Path.of(fileSub.parent))
//    fileSub.writeText(song.voices[0].srtFileBody)

    val fileDescription = File(param["SONG_DESCRIPTION_FILENAME"].toString())
    Files.createDirectories(Path.of(fileDescription.parent))
    fileDescription.writeText(song.getDescription(isBluetoothDelay))

    val filePictures = File(param["SONG_DESCRIPTION_FILENAME"].toString())
    Files.createDirectories(Path.of(filePictures.parent))
    createSongPicture(song, param["SONG_PICTURE_FILENAME"].toString(),song.songVersion, isBluetoothDelay)

}
