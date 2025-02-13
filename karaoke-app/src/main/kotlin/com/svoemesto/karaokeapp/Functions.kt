package com.svoemesto.karaokeapp//import model.Lyric
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.mlt.getMlt
import com.svoemesto.karaokeapp.mlt.mko.*
import com.svoemesto.karaokeapp.model.*
import java.io.File
import java.lang.Integer.min
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
//import java.nio.file.Path
import kotlin.io.path.Path

fun getVoices(settings: Settings, songVersion: SongVersion) : List<SettingVoice> {

    val listOfVoices: MutableList<SettingVoice> = mutableListOf()
    val countNotEmptyVoices = settings.sourceMarkersList.filter { it.isNotEmpty() }.size

    if (countNotEmptyVoices == 0) return listOfVoices
    val startSilentOffsetMs = settings.getStartSilentOffsetMs()
    for (voiceIndex in 0 until settings.countVoices) {

        val voice = SettingVoice(rootId = settings.id)
        voice.voiceId = voiceIndex
        voice.rootSongLengthMs = settings.songLengthMs
        voice.rootStartSilentOffsetMs = settings.getStartSilentOffsetMs()

        val voiceMarkers = settings.sourceMarkersList[voiceIndex]
        var groupId = 0
        var endTimeMs = 0L
        val lines: MutableList<SettingVoiceLine> = mutableListOf()
        var tmpLines: MutableList<SettingVoiceLine> = mutableListOf()
        var tmpElements: MutableList<SettingVoiceLineElement> = mutableListOf()
        var tmpTextSyllables: MutableList<SettingVoiceLineElementSyllable> = mutableListOf()
        var tmpChordSyllables: MutableList<SettingVoiceLineElementSyllable> = mutableListOf()
        var tmpNoteSyllables: MutableList<SettingVoiceLineElementSyllable> = mutableListOf()
        var prevTextSyllable: SettingVoiceLineElementSyllable? = null

        var lastTextLineWasComment = false

        voiceMarkers.forEachIndexed { markerIndex, sourceMarker ->

            // Время слога - время маркера + стартовый оффсет
            val timeMs = (sourceMarker.time * 1000).toLong() + startSilentOffsetMs
            var currentText = ""

            when (sourceMarker.markertype) {
                "setting" -> {
                    val values = sourceMarker.label.split("|")

                    if (values.size == 1) {
                        if (values[0] == "END") {
                            endTimeMs = timeMs
                        }
                    } else if (values.size == 2) {
                        val (labelType, labelValue) = values
                        when (labelType) {
                            "GROUP" -> {
                                if (groupId != labelValue.toInt()) {
                                    groupId = labelValue.toInt()
                                    if (tmpLines.isNotEmpty() && !lastTextLineWasComment) {
                                        tmpLines.add(SettingVoiceLine.newLine(settings.id,timeMs,groupId))
                                    }
                                }
                            }
                            "COMMENT" -> {
                                // Создаём элемент "комментарий" и добавляем его в список временных элементов
                                val element = SettingVoiceLineElement(
                                    rootId = settings.id,
                                    type = SettingVoiceLineElementTypes.COMMENT
                                )
                                element.groupId = groupId
                                element.addSyllable(
                                    SettingVoiceLineElementSyllable(
                                        rootId = settings.id,
                                        text = labelValue,
                                        syllableStartMs = timeMs,
                                        syllableEndMs = timeMs,
                                        previous = null
                                    )
                                )

                                val lineToAdd = SettingVoiceLine(
                                    rootId = settings.id,
                                    lineStartMs = timeMs,
                                    lineEndMs = timeMs
                                )
                                lineToAdd.addElement(element)
                                tmpLines.add(lineToAdd)

                                lastTextLineWasComment = true

                            }
                            else -> {}
                        }
                    }
                    currentText = ""
                }
                "syllables" -> {
                    if (sourceMarker.chord.isNotEmpty() && songVersion == SongVersion.CHORDS) {
                        var txt = sourceMarker.label.replace("_", " ")
                        if (txt.haveVowel()) {
                            val firstVowelIndex = txt.getFirstVowelIndex()
                            txt = txt.substring(0 until  firstVowelIndex)
                        } else {
                            txt = ""
                        }
                        val prevSyllableToChord = SettingVoiceLineElementSyllable(
                            rootId = settings.id,
                            text = currentText + txt,
                            syllableStartMs = timeMs,
                            syllableEndMs = timeMs,
                            previous = null
                        )
                        val chordSyllable = SettingVoiceLineElementSyllable(
                            rootId = settings.id,
                            text = sourceMarker.chord,
                            syllableStartMs = timeMs,
                            syllableEndMs = timeMs,
                            previous = prevSyllableToChord
                        )
                        tmpChordSyllables.add(chordSyllable)
                    }
                    if (sourceMarker.label.isNotEmpty()) {
                        var txt = sourceMarker.label.replace("_", " ")
                        if (tmpTextSyllables.isEmpty()){
                            txt = txt.uppercaseFirstLetter()
                            prevTextSyllable = null
                        }
                        val textSyllable = SettingVoiceLineElementSyllable(
                            rootId = settings.id,
                            text = txt,
                            syllableStartMs = timeMs,
                            syllableEndMs = timeMs,
                            previous = prevTextSyllable
                        )
                        tmpTextSyllables.add(textSyllable)
                        prevTextSyllable = textSyllable
                        currentText += txt
                    }

                    if (sourceMarker.note.isNotEmpty() && songVersion == SongVersion.CHORDS) {
                        val noteSyllable = SettingVoiceLineElementSyllable(
                            rootId = settings.id,
                            text = sourceMarker.note,
                            syllableStartMs = timeMs,
                            syllableEndMs = timeMs,
                            previous = null
                        )
                        tmpNoteSyllables.add(noteSyllable)
                    }
                    lastTextLineWasComment = false
                }
                "endofsyllable" -> {

                    var txt = "⸳"

                    if (tmpTextSyllables.isEmpty()){
                        txt = txt.uppercaseFirstLetter()
                        prevTextSyllable = null
                    } else {
                        prevTextSyllable = tmpTextSyllables.last()
                        prevTextSyllable?.let {it.syllableEndMs = timeMs}
                    }

                    val textSyllable = SettingVoiceLineElementSyllable(
                        rootId = settings.id,
                        text = txt,
                        syllableStartMs = timeMs,
                        syllableEndMs = timeMs,
                        previous = prevTextSyllable
                    )
                    tmpTextSyllables.add(textSyllable)
                    prevTextSyllable = textSyllable
                    currentText += txt
                }
                "endofline" -> {

                    prevTextSyllable = null
                    currentText = ""

                    if (tmpTextSyllables.isNotEmpty()) {

                        tmpTextSyllables.last().syllableEndMs = timeMs
                        for (indexSyllable in 0 until tmpTextSyllables.size-1) {
                            val currSyllable = tmpTextSyllables[indexSyllable]
                            val nextSyllable = tmpTextSyllables[indexSyllable+1]
                            if (currSyllable.syllableStartMs == currSyllable.syllableEndMs) {
                                currSyllable.syllableEndMs = nextSyllable.syllableStartMs
                            }
                        }

                        val element = SettingVoiceLineElement(
                            rootId = settings.id,
                            type = SettingVoiceLineElementTypes.TEXT
                        )
                        element.groupId = groupId
                        element.addSyllables(tmpTextSyllables)
                        tmpElements.add(element)
                        tmpTextSyllables = mutableListOf()
                    }

                    if (tmpChordSyllables.isNotEmpty()) {
                        val chordElement = SettingVoiceLineElement(
                            rootId = settings.id,
                            type = SettingVoiceLineElementTypes.ACCORD
                        )
                        chordElement.groupId = groupId
                        chordElement.addSyllables(tmpChordSyllables)
                        tmpElements.add(chordElement)
                        tmpChordSyllables = mutableListOf()
                    }

                    if (tmpNoteSyllables.isNotEmpty()) {
                        val noteElement = SettingVoiceLineElement(
                            rootId = settings.id,
                            type = SettingVoiceLineElementTypes.NOTE
                        )
                        noteElement.groupId = groupId
                        noteElement.addSyllables(tmpNoteSyllables)
                        tmpElements.add(noteElement)
                        tmpNoteSyllables = mutableListOf()
                    }

                    if (tmpElements.isNotEmpty()) {
                        val lineToAdd = SettingVoiceLine(
                            rootId = settings.id,
                            lineStartMs = tmpElements.minOf { element ->  element.getSyllables().minOf { it.syllableStartMs } },
                            lineEndMs = tmpElements.minOf { element ->  element.getSyllables().maxOf { it.syllableEndMs } }
                        )
                        lineToAdd.addElements(tmpElements)
                        tmpLines.add(lineToAdd)
                        tmpElements = mutableListOf()
                    } else {
                        if (tmpLines.isNotEmpty()) {
                            tmpLines.add(SettingVoiceLine.emptyLine(settings.id,timeMs,groupId))
                            tmpLines.add(SettingVoiceLine.newLine(settings.id,timeMs,groupId))
                        }

                    }

                }
                "newline" -> {
                    currentText = ""
                    if (tmpLines.isNotEmpty()) {
                        tmpLines.add(SettingVoiceLine.newLine(settings.id,timeMs,groupId))
                    }
                }
                else -> {} // "unmute", "chord", "beat", "note"
            }
        }

        for (lineIndex in 0 until tmpLines.size - 1) {
            val currLine = tmpLines[lineIndex]
            val nextLine = tmpLines[lineIndex + 1]
            val diff = nextLine.lineStartMs - currLine.lineEndMs
            if (diff < 1000) {
                currLine.lineEndMs = Math.max(currLine.lineStartMs, nextLine.lineStartMs - 1000)
            }
        }

        // На данный момент у нас заполнен список tmpLines
        // Надо найти время самой долгой строки и навставлять пустых строк если надо
        if (tmpLines.isNotEmpty()) {

            // Время самой "длинной" строки
            val maxDuration = tmpLines.maxOf { it.lineDurationMs() }

            // Кол-во пустых строк с начала = начало старта первой строки / время длинной строки
            val countStartEmptyLines = (tmpLines.first().lineStartMs / maxDuration)

            // В любом случае добавляем пустую строку в самое начало
            lines.add(SettingVoiceLine.emptyLine(settings.id,0,groupId))

            // Если кол-во пустых строк которые нужно вставить в начало > 0
            if (countStartEmptyLines > 0) {

                // Длительность одной "пустой" строки (а точнее расстояние между их "стартами")
                // равна начало старта первой строки / кол-во пустых строк
                val silentDuration = tmpLines.first().lineStartMs / (countStartEmptyLines + 1)
                for (emptyLineIndex in 0 until countStartEmptyLines) {
                    val timeMs = (emptyLineIndex + 1) * silentDuration
                    lines.add(SettingVoiceLine.emptyLine(settings.id,timeMs,groupId))
                }
            }

            // Проходимся по временным линиям - от первой до последней
            // (используем while а не for т.к. в процессе возможно необходимо будет менять индекс)
            var lineIndex = 0
            while (lineIndex < tmpLines.size) {

                // Получаем текущую линию
                val currLine = tmpLines[lineIndex]
                // Добавляем текущую линию к финальному списку если она не пустая
                if (currLine.haveTextElementOrComment(songVersion)) {
                    lines.add(currLine)

                    for (nextLineIndex in lineIndex + 1 until tmpLines.size) {

                        // Получаем следующую НЕ ПУСТУЮ ЛИНИЮ
                        val nextLine = tmpLines[nextLineIndex]
                        if (nextLine.haveTextElementOrComment(songVersion)) {
                            // Находим кол-во уже имеющихся пустых линий между текущей или следующей
                            val countEmptyLinesBetweenCurrentAndNext = nextLineIndex - lineIndex - 1

                            // Начало "пустого" интервала - с конца текущей линии
                            val startTimeMs = currLine.lineEndMs
                            // Конец "пустого" интервала - с начала следующей линии
                            val endTimeMs = nextLine.lineStartMs
                            // Количество пустых линий, которые должны быть в этом интервале
                            val countNeededEmptyLines = (endTimeMs - startTimeMs) / maxDuration

                            // Вычислем реальное кол-во пустых строк, которые должны быть в этом интервале
                            val countEmptyLinesToAdd =
                                Integer.max(countEmptyLinesBetweenCurrentAndNext, countNeededEmptyLines.toInt())

                            // Если такие строки должны быть - добавляем их
                            if (countEmptyLinesToAdd > 0) {
                                val durationEmptyLine = (endTimeMs - startTimeMs) / (countEmptyLinesToAdd + 1)
                                for (emptyLineIndex in 0 until countEmptyLinesToAdd) {
                                    val timeMs = startTimeMs + (emptyLineIndex + 1) * durationEmptyLine
                                    lines.add(SettingVoiceLine.emptyLine(settings.id, timeMs, groupId))
                                }

                                // Устанавливаем указатель цикла на линию перед следующей, чтобы на следующей итерации цикла
                                // Встать на следующую линию и её добавить как положено
                                lineIndex = nextLineIndex - 1
                            }
                            break
                        }

                    }

                }
                lineIndex++
            }
            // Добавили все линии - надо добавить пустые линии в конце

            val lastTextLine = lines.last()
            val startTime = lastTextLine.lineEndMs
            val endTime = settings.songLengthMs + startSilentOffsetMs

            if (endTime > startTime) {


                // Кол-во пустых строк с начала = начало старта первой строки / время длинной строки
                val countEndEmptyLines = (endTime - startTime) / maxDuration

                // Если кол-во пустых строк которые нужно вставить в начало > 0
                if (countEndEmptyLines > 0) {

                    // Длительность одной "пустой" строки (а точнее расстояние между их "стартами")
                    // равна начало старта первой строки / кол-во пустых строк
                    val silentDuration =  (endTime - startTime) / (countEndEmptyLines + 1)
                    for (emptyLineIndex in 0 until countEndEmptyLines) {
                        val timeMs = startTime + (emptyLineIndex + 1) * silentDuration
                        lines.add(SettingVoiceLine.emptyLine(settings.id,timeMs,groupId))
                    }
                }

                // В любом случае добавляем пустую строку в самый конец
                lines.add(SettingVoiceLine.emptyLine(settings.id,endTime,groupId))
            }

            /*
            Теперь нужно нормализовать промежутки пустых линий и комментариев
            Ищем линии (пустые и комментарии) между двумя текстовыми линиями (или от начала, или до конца)
            Делаем так, чтобы временные интервалы между пустыми линиями и комментариями были одинаковыми
             */
            lineIndex = 0
            while (lineIndex < lines.size - 1) {

                val currLine = lines[lineIndex]

                // Если текущая линия - первая или текстовая
                if (lineIndex == 0 || currLine.haveTextElement(songVersion)) {
                    var nextIndexLine = lineIndex + 1
                    while (nextIndexLine < lines.size) {
                        val nextLine = lines[nextIndexLine]
                        if (nextIndexLine == lines.size -1 || nextLine.haveTextElement(songVersion)) {
                            break
                        }
                        nextIndexLine++
                    }
                    // Если между currLine и nextLine есть еще линии - их и надо нормализовать
                    if (nextIndexLine - lineIndex > 1) {
                        val startTime = currLine.lineEndMs
                        val endTime = lines[nextIndexLine].lineStartMs
                        val durationNormal = (endTime - startTime) / (nextIndexLine - lineIndex)
                        for (i in lineIndex + 1 until nextIndexLine) {
                            val line = lines[i]
                            val time = currLine.lineEndMs + durationNormal * (i - lineIndex)
                            line.lineStartMs = time
                            line.lineEndMs = time
                        }
                    }
                }
                lineIndex++
            }

        }

        tmpLines = mutableListOf()

        voice.addLines(songVersion, lines)

//        voice.fillParents()

        listOfVoices.add(voice)

    }
    // Заполняем longerElementPreviousVoice для войсов (если войсов больше 1)
    if (listOfVoices.size > 1) {
        for (indexVoice in 0 until listOfVoices.size - 1) {
            val currVoice = listOfVoices[indexVoice]
            val nextVoice = listOfVoices[indexVoice + 1]
            val currCrossingLines = currVoice.textLines(songVersion).filter { line -> nextVoice.textLines(songVersion).any { nextLine -> nextLine.isCrossing(line) } }
            // Если есть пересечения со следующим войсом - находим самый длинный элемент с пересечением
            // Если пересечений нет - формируем новый элемент равный примерно половине ширины самого длинного
            val longerCrossingElement = if (currCrossingLines.isNotEmpty()) {
                currCrossingLines.maxBy { it.w(songVersion) }.textElement(songVersion)!!
            } else {
                val longerCurrElement = currVoice.textLines(songVersion).maxBy { it.w(songVersion) }.textElement(songVersion)!!
                val halfSyllables: MutableList<SettingVoiceLineElementSyllable> = mutableListOf()
                for (indexSyllable in 0 .. longerCurrElement.getSyllables().size / 2) {
                    halfSyllables.add(longerCurrElement.getSyllables()[indexSyllable])
                }
                val halfElement = SettingVoiceLineElement(
                    rootId = settings.id,
                    type = longerCurrElement.type
                )
                halfElement.groupId = longerCurrElement.groupId
                halfElement.fontSize = longerCurrElement.fontSize
                halfElement.addSyllables(halfSyllables)
                halfElement
            }
            nextVoice.longerElementPreviousVoice = longerCrossingElement
        }
    }

    if (listOfVoices.isNotEmpty()) {
        val fontSize = getFontSize(songVersion, listOfVoices)
        listOfVoices.forEach { voice ->
            voice.getLines().forEach { line ->
                line.getElements(songVersion).forEach { element ->
                    element.fontSize = fontSize
                }
            }
            voice._countLineTracks = null
        }
    }

    listOfVoices.forEach { it.actuateChilds(songVersion) }

    return listOfVoices
}
fun createKaraoke(settings: Settings, songVersion: SongVersion) {

//    val voices = getVoices(settings, songVersion)
    val mltProp = settings.getMltProp(songVersion)

    val permissions = PosixFilePermissions.fromString("rwxr-x---")

    val templateProject = "<?xml version='1.0' encoding='utf-8'?>\n${getMlt(mltProp)}"
    val fileProject = File(settings.getOutputFilename(SongOutputFile.PROJECT, songVersion))
    val fileMlt = File(settings.getOutputFilename(SongOutputFile.MLT, songVersion))
    Files.createDirectories(Path(fileProject.parent))
    fileProject.writeText(templateProject)
    fileMlt.writeText(templateProject)
    val fileRun = File(settings.getOutputFilename(SongOutputFile.RUN, songVersion))
    fileRun.writeText("echo \"${settings.getOutputFilename(SongOutputFile.MLT, songVersion)}\"\n" +
            "melt -progress \"${settings.getOutputFilename(SongOutputFile.MLT, songVersion)}\"\n")
    Files.setPosixFilePermissions(fileRun.toPath(), permissions)

//    val fileDescription = File(settings.getOutputFilename(SongOutputFile.DESCRIPTION, songVersion))
//    Files.createDirectories(Path(fileDescription.parent))
//    fileDescription.writeText(song.getDescription())

    createBoostyTeaserPicture(settings)
    createBoostyFilesPicture(settings)
//    createVKPicture(settings)
//    createVKLinkPicture(settings)
    createVKLinkPictureWeb(settings)

    createSongTextFile(settings, songVersion)
    createSongDescriptionFile(settings, songVersion)
    createSongPicture(settings, songVersion)

//    val filePictureChords = File(settings.getOutputFilename(SongOutputFile.PICTURECHORDS, songVersion))
//    Files.createDirectories(Path(filePictureChords.parent))
//    createSongChordsPicture(settings, settings.getOutputFilename(SongOutputFile.PICTURECHORDS, songVersion), songVersion, mltProp.getXmlData(listOf(ProducerType.SONGTEXT, 0, "IgnoreCapo")))


}


fun createKaraoke(song: Song) {

    createKaraoke(song.settings, song.songVersion)

    val songVersion = song.songVersion


    if (songVersion == SongVersion.CHORDS && !song.hasChords) return
    println("Создаём ${songVersion.name}: ${song.settings.author} / ${song.settings.songName}")


    val mltProp = MltProp()

    mltProp.setSongVersion(songVersion)

    mltProp.setSettings(song.settings)
    song.settings.voicesForMlt = getVoices(song.settings, songVersion) // song.settings.voices()

    val startSilentOffsetMs = song.settings.getStartSilentOffsetMs()

    mltProp.setStartSilentOffsetMs(startSilentOffsetMs)

    mltProp.setFrameWidthPx(Karaoke.frameWidthPx)
    mltProp.setFrameHeightPx(Karaoke.frameHeightPx)
    mltProp.getFrameHeightPx()




    val countAudioTracks = songVersion.producers.count { it.isAudio && it != ProducerType.MAINBIN }
    val countAllTracks = countAudioTracks + 10
    mltProp.setCountAudioTracks(countAudioTracks)
    mltProp.setCountAllTracks(countAllTracks)

    val countVoices = song.settings.voicesForMlt.size
    mltProp.setCountVoices(countVoices)

    val songLengthMs = song.settings.songLengthMs + startSilentOffsetMs
    val audioLengthMs = song.settings.songLengthMs
    val songLengthFr = convertMillisecondsToFrames(songLengthMs)
    val audioLengthFr = convertMillisecondsToFrames(audioLengthMs)
    mltProp.setSongLengthMs(songLengthMs)
    mltProp.setSongLengthFr(songLengthFr)
    mltProp.setAudioLengthFr(audioLengthFr)

    val timelineLengthMs = songLengthMs + 600_000
    val timelineLengthFr = convertMillisecondsToFrames(timelineLengthMs)
    val timelineStartTimecode = convertMillisecondsToTimecode(0L)
    val timelineEndTimecode = convertMillisecondsToTimecode(timelineLengthMs)
    mltProp.setTimelineLengthMs(timelineLengthMs)
    mltProp.setTimelineLengthFr(timelineLengthFr)
    mltProp.setTimelineStartTimecode(timelineStartTimecode)
    mltProp.setTimelineEndTimecode(timelineEndTimecode)

    val splashLengthMs = Karaoke.timeSplashScreenLengthMs
    val splashLengthFr = convertMillisecondsToFrames(splashLengthMs)
    mltProp.setSplashLengthMs(splashLengthMs)
    mltProp.setSplashLengthFr(splashLengthFr)

    val boostyLengthMs = Karaoke.timeBoostyLengthMs
    val boostyLengthFr = convertMillisecondsToFrames(boostyLengthMs)
    mltProp.setBoostyLengthMs(boostyLengthMs)
    mltProp.setBoostyLengthFr(boostyLengthFr)

    val totalLengthMs = songLengthMs + splashLengthMs + boostyLengthMs
    val totalLengthFr = convertMillisecondsToFrames(totalLengthMs)
    mltProp.setTotalLengthMs(totalLengthMs)
    mltProp.setTotalLengthFr(totalLengthFr)

    val fadeMs = 1000L
    mltProp.setFadeLengthMs(fadeMs)

    val songStartTimecode = convertMillisecondsToTimecode(0L)
    val songEndTimecode = convertMillisecondsToTimecode(songLengthMs)
    val audioEndTimecode = convertMillisecondsToTimecode(songLengthMs - startSilentOffsetMs)
    val songFadeInTimecode = convertMillisecondsToTimecode(fadeMs)
    val songFadeOutTimecode = convertMillisecondsToTimecode(songLengthMs-fadeMs)
    mltProp.setSongStartTimecode(songStartTimecode)
    mltProp.setSongEndTimecode(songEndTimecode)
    mltProp.setAudioEndTimecode(audioEndTimecode)
    mltProp.setSongFadeInTimecode(songFadeInTimecode)
    mltProp.setSongFadeOutTimecode(songFadeOutTimecode)

    val totalStartTimecode = convertMillisecondsToTimecode(0L)
    val totalEndTimecode = convertMillisecondsToTimecode(totalLengthMs)
    val totalFadeInTimecode = convertMillisecondsToTimecode(fadeMs)
    val totalFadeOutTimecode = convertMillisecondsToTimecode(totalLengthMs-fadeMs)
    mltProp.setTotalStartTimecode(totalStartTimecode)
    mltProp.setTotalEndTimecode(totalEndTimecode)
    mltProp.setTotalFadeInTimecode(totalFadeInTimecode)
    mltProp.setTotalFadeOutTimecode(totalFadeOutTimecode)



    val boostyBlankTimecode = convertMillisecondsToTimecode(splashLengthMs)
    val boostyEndTimecode = convertMillisecondsToTimecode(splashLengthMs + boostyLengthMs)
    val voiceBlankTimecode = convertMillisecondsToTimecode(splashLengthMs + boostyLengthMs + startSilentOffsetMs)
    val boostyFadeInTimecode = convertMillisecondsToTimecode(splashLengthMs+fadeMs)
    val boostyFadeOutTimecode = convertMillisecondsToTimecode(splashLengthMs + boostyLengthMs-fadeMs)
    mltProp.setBoostyStartTimecode(boostyBlankTimecode)
    mltProp.setBoostyEndTimecode(boostyEndTimecode)
    mltProp.setBoostyFadeInTimecode(boostyFadeInTimecode)
    mltProp.setBoostyFadeOutTimecode(boostyFadeOutTimecode)
    mltProp.setBoostyBlankTimecode(boostyBlankTimecode)
    mltProp.setVoiceBlankTimecode(voiceBlankTimecode)

    val splashFadeInTimecode = songFadeInTimecode
    val splashFadeOutTimecode = convertMillisecondsToTimecode(splashLengthMs-fadeMs)
    mltProp.setSplashStartTimecode(songStartTimecode)
    mltProp.setSplashEndTimecode(boostyBlankTimecode)
    mltProp.setSplashFadeInTimecode(splashFadeInTimecode)
    mltProp.setSplashFadeOutTimecode(splashFadeOutTimecode)



    val voiceStartTimecode = voiceBlankTimecode
    val voiceEndTimecode = totalEndTimecode
    val voiceFadeInTimecode = convertMillisecondsToTimecode(splashLengthMs + boostyLengthMs + fadeMs)
    val voiceFadeOutTimecode = totalFadeOutTimecode
    mltProp.setVoiceStartTimecode(voiceStartTimecode)
    mltProp.setVoiceEndTimecode(voiceEndTimecode)
    mltProp.setVoiceFadeInTimecode(voiceFadeInTimecode)
    mltProp.setVoiceFadeOutTimecode(voiceFadeOutTimecode)




    val offsetInAudioMs = splashLengthMs + boostyLengthMs + startSilentOffsetMs
    val offsetInVideoMs = splashLengthMs + boostyLengthMs
    val offsetInAudioTimecode = convertMillisecondsToTimecode(offsetInAudioMs)
    val offsetInVideoTimecode = convertMillisecondsToTimecode(offsetInVideoMs)





    val headerAuthor = song.settings.author
    val headerTone = song.settings.key
    val headerBpm = song.settings.bpm
    val headerAlbum = song.settings.album
    val headerYear = song.settings.year
    val headerTrack = song.settings.track
    val headerSongName = song.settings.songName

    val progressSymbolHalfWidth = 0 //(getTextWidthHeightPx(Karaoke.progressSymbol, Karaoke.progressFont.font).first/2).toLong()
    val fontNameSizePt = Integer.min(getFontSizeBySymbolWidth(1100.0 / song.settings.songName.length), 80)
    val yOffset = 0 //-5
    val propAudioVolumeOnValue = song.propAudioVolumeOn
    val propAudioVolumeOffValue = song.propAudioVolumeOff
    val propAudioVolumeCustomValue = song.propAudioVolumeCustom

    val propGuidesValue = mutableListOf<String>()
    val propGuides = propGuidesValue.joinToString(",")





    mltProp.setSongCapo(song.capo)
    mltProp.setSongChordDescription(song.settings.getChordDescription(SongVersion.CHORDS))
    mltProp.setSongName(song.settings.songName.replace("&", "&amp;"))

    mltProp.setLineSpacing(LINE_SPACING)
    mltProp.setShadow(SHADOW)
    mltProp.setTypeWriter(TYPEWRITER)
    mltProp.setAlignment(ALIGNMENT)

    when(song.songVersion) {
        SongVersion.LYRICS -> {
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIOVOCAL)
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIOMUSIC)
            mltProp.setVolume(propAudioVolumeOnValue, ProducerType.AUDIOSONG)
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIOBASS)
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIODRUMS)
        }
        SongVersion.KARAOKE -> {
            mltProp.setVolume(propAudioVolumeCustomValue, ProducerType.AUDIOVOCAL)
            mltProp.setVolume(propAudioVolumeOnValue, ProducerType.AUDIOMUSIC)
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIOSONG)
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIOBASS)
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIODRUMS)
        }
        SongVersion.CHORDS -> {
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIOVOCAL)
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIOMUSIC)
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIOSONG)
            mltProp.setVolume(propAudioVolumeOnValue, ProducerType.AUDIOBASS)
            mltProp.setVolume(propAudioVolumeOnValue, ProducerType.AUDIODRUMS)
        }
    }

    mltProp.setFileName(song.settings.getOutputFilename(SongOutputFile.RUNALL, songVersion).replace("&", "&amp;"), SongOutputFile.RUNALL)
    mltProp.setFileName(song.settings.getOutputFilename(SongOutputFile.RUN, songVersion).replace("&", "&amp;"), SongOutputFile.RUN)
    mltProp.setFileName(song.settings.getOutputFilename(SongOutputFile.MLT, songVersion).replace("&", "&amp;"), SongOutputFile.MLT)
    mltProp.setFileName(song.settings.getOutputFilename(SongOutputFile.PROJECT, songVersion).replace("&", "&amp;"), SongOutputFile.PROJECT)
    mltProp.setFileName(song.settings.getOutputFilename(SongOutputFile.VIDEO, songVersion).replace("&", "&amp;"), SongOutputFile.VIDEO)
    mltProp.setFileName(song.settings.getOutputFilename(SongOutputFile.PICTURE, songVersion).replace("&", "&amp;"), SongOutputFile.PICTURE)
    mltProp.setFileName(song.settings.getOutputFilename(SongOutputFile.PICTURECHORDS, songVersion).replace("&", "&amp;"), SongOutputFile.PICTURECHORDS)
    mltProp.setFileName(song.settings.getOutputFilename(SongOutputFile.SUBTITLE, songVersion).replace("&", "&amp;"), SongOutputFile.SUBTITLE)
    mltProp.setFileName(song.settings.getOutputFilename(SongOutputFile.DESCRIPTION, songVersion).replace("&", "&amp;"), SongOutputFile.DESCRIPTION)
    mltProp.setFileName(song.settings.getOutputFilename(SongOutputFile.TEXT, songVersion).replace("&", "&amp;"), SongOutputFile.TEXT)


    mltProp.setTone(headerTone, ProducerType.HEADER)
    mltProp.setBpm(headerBpm, ProducerType.HEADER)
    mltProp.setYear(headerYear, ProducerType.HEADER)
    mltProp.setTrack(headerTrack, ProducerType.HEADER)
    mltProp.setAuthor(headerAuthor.replace("&", "&amp;amp;"), ProducerType.HEADER)
    mltProp.setAlbum(headerAlbum.replace("&", "&amp;amp;"), ProducerType.HEADER)
    mltProp.setSongName(headerSongName.replace("&", "&amp;amp;"), ProducerType.HEADER)
    mltProp.setPath(song.settings.pathToFileLogoAuthor.replace("&", "&amp;amp;"), "LogoAuthor")
    mltProp.setPath(song.settings.pathToFileLogoAlbum.replace("&", "&amp;amp;"), "LogoAlbum")
    mltProp.setBase64(song.settings.pathToFileLogoAuthor.base64ifFileExists(), "LogoAuthor")
    mltProp.setBase64(song.settings.pathToFileLogoAlbum.base64ifFileExists(), "LogoAlbum")
    mltProp.setPath("/home/nsa/Documents/Караоке/SPLASH.png", ProducerType.BOOSTY)
    mltProp.setBase64("/home/nsa/Documents/Караоке/SPLASH.png".base64ifFileExists(), ProducerType.BOOSTY)

    mltProp.setRootFolder(song.settings.rootFolder.replace("&", "&amp;"), "Song")
    mltProp.setStartTimecode(songStartTimecode, "Song")
    mltProp.setEndTimecode(songEndTimecode, "Song")
    mltProp.setEndTimecode(convertMillisecondsToTimecode(Karaoke.timeSplashScreenLengthMs), ProducerType.SPLASHSTART)
    mltProp.setEndTimecode(convertMillisecondsToTimecode(Karaoke.timeSplashScreenLengthMs + Karaoke.timeBoostyLengthMs), ProducerType.BOOSTY)
    mltProp.setFadeInTimecode(songFadeInTimecode, "Song")
    mltProp.setFadeOutTimecode(songFadeOutTimecode, "Song")
    convertMillisecondsToTimecode(Karaoke.timeSplashScreenLengthMs - 1000).replace(",", ".")
    mltProp.setFadeOutTimecode(convertMillisecondsToTimecode(Karaoke.timeSplashScreenLengthMs - 1000).replace(",", "."), ProducerType.SPLASHSTART)
    convertMillisecondsToTimecode(Karaoke.timeSplashScreenLengthMs + 1000).replace(",", ".")
    mltProp.setFadeInTimecode(convertMillisecondsToTimecode(Karaoke.timeSplashScreenLengthMs + 1000).replace(",", "."), ProducerType.BOOSTY)
    convertMillisecondsToTimecode(Karaoke.timeSplashScreenLengthMs + Karaoke.timeBoostyLengthMs - 1000).replace(",", ".")
    mltProp.setFadeOutTimecode(convertMillisecondsToTimecode(Karaoke.timeSplashScreenLengthMs + Karaoke.timeBoostyLengthMs - 1000).replace(",", "."), ProducerType.BOOSTY)
    mltProp.setLengthMs(songLengthMs, "Song")
    mltProp.setLengthFr(songLengthFr, "Song")
    mltProp.setLengthMs(totalLengthMs, "Total")
    mltProp.setLengthFr(convertMillisecondsToFrames(totalLengthMs, Karaoke.frameFps), "Total")
    mltProp.setLengthFr(convertMillisecondsToFrames(Karaoke.timeSplashScreenLengthMs, Karaoke.frameFps), ProducerType.SPLASHSTART)
    mltProp.setLengthFr(convertMillisecondsToFrames(Karaoke.timeBoostyLengthMs, Karaoke.frameFps), ProducerType.BOOSTY)
    mltProp.setEndTimecode(convertMillisecondsToTimecode(totalLengthMs), "Total")
    mltProp.setGuidesProperty("[${propGuides}]")
    mltProp.setInOffsetAudio(offsetInAudioTimecode)
    mltProp.setInOffsetVideo(offsetInVideoTimecode)

    // Цикл по голосам для предварительной инициализации параметров
    for (voiceId in 0 until countVoices) {
        ProducerType.values().forEach { type ->
            if (type.ids.isEmpty()) {
                mltProp.setId(type.ordinal*100 + voiceId*10, listOf(type, voiceId))
                val key = listOf(type, voiceId)
                if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
            } else {
                for (childId in 0 until type.ids.size) {
                    mltProp.setId(type.ordinal*100 + voiceId*10 + childId*1, listOf(type, voiceId, childId))
                    val key = listOf(type, voiceId, childId)
                    if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
                }
            }

        }

        mltProp.setPath(song.settings.audioSongFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIOSONG, voiceId))
        mltProp.setPath(song.settings.audioMusicFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIOMUSIC, voiceId))
        mltProp.setPath(song.settings.audioVocalFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIOVOCAL, voiceId))
        mltProp.setPath(song.settings.audioBassFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIOBASS, voiceId))
        mltProp.setPath(song.settings.audioDrumsFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIODRUMS, voiceId))

        mltProp.setEnabled(Karaoke.createAudioVocal, listOf(ProducerType.AUDIOVOCAL, voiceId))
        mltProp.setEnabled(Karaoke.createAudioMusic, listOf(ProducerType.AUDIOMUSIC, voiceId))
        mltProp.setEnabled(Karaoke.createAudioSong, listOf(ProducerType.AUDIOSONG, voiceId))
        mltProp.setEnabled(Karaoke.createAudioBass, listOf(ProducerType.AUDIOBASS, voiceId))
        mltProp.setEnabled(Karaoke.createAudioDrums, listOf(ProducerType.AUDIODRUMS, voiceId))
        mltProp.setEnabled(Karaoke.createBackground, listOf(ProducerType.BACKGROUND, voiceId))
        mltProp.setEnabled(Karaoke.createHorizon, listOf(ProducerType.HORIZON, voiceId))
        mltProp.setEnabled(Karaoke.createProgress, listOf(ProducerType.PROGRESS, voiceId))
        mltProp.setEnabled(Karaoke.createFillsSongtext, listOf(ProducerType.FILLCOLORSONGTEXT, voiceId))
        mltProp.setEnabled(Karaoke.createSongtext, listOf(ProducerType.SONGTEXT, voiceId))
        mltProp.setEnabled(Karaoke.createFader, listOf(ProducerType.FADERTEXT, voiceId))
        mltProp.setEnabled(Karaoke.createFader, listOf(ProducerType.FADERCHORDS, voiceId))
        mltProp.setEnabled(Karaoke.createHeader, listOf(ProducerType.HEADER, voiceId))
        mltProp.setEnabled(Karaoke.createCounters, listOf(ProducerType.COUNTER, voiceId))
        mltProp.setEnabled(Karaoke.createWatermark, listOf(ProducerType.WATERMARK, voiceId))

    }

    mltProp.setPath(song.settings.audioSongFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIOSONG))
    mltProp.setPath(song.settings.audioMusicFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIOMUSIC))
    mltProp.setPath(song.settings.audioVocalFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIOVOCAL))
    mltProp.setPath(song.settings.audioBassFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIOBASS))
    mltProp.setPath(song.settings.audioDrumsFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIODRUMS))

    mltProp.setEnabled(Karaoke.createAudioVocal, listOf(ProducerType.AUDIOVOCAL))
    mltProp.setEnabled(Karaoke.createAudioMusic, listOf(ProducerType.AUDIOMUSIC))
    mltProp.setEnabled(Karaoke.createAudioSong, listOf(ProducerType.AUDIOSONG))
    mltProp.setEnabled(Karaoke.createAudioBass, listOf(ProducerType.AUDIOBASS))
    mltProp.setEnabled(Karaoke.createAudioDrums, listOf(ProducerType.AUDIODRUMS))
    mltProp.setEnabled(Karaoke.createBackground, listOf(ProducerType.BACKGROUND))
    mltProp.setEnabled(Karaoke.createHorizon, listOf(ProducerType.HORIZON))
    mltProp.setEnabled(Karaoke.createProgress, listOf(ProducerType.PROGRESS))
    mltProp.setEnabled(Karaoke.createFillsSongtext, listOf(ProducerType.FILLCOLORSONGTEXT))
    mltProp.setEnabled(Karaoke.createSongtext, listOf(ProducerType.SONGTEXT))
    mltProp.setEnabled(Karaoke.createFader, listOf(ProducerType.FADERTEXT))
    mltProp.setEnabled(Karaoke.createFader, listOf(ProducerType.FADERCHORDS))
    mltProp.setEnabled(Karaoke.createHeader, listOf(ProducerType.HEADER))
    mltProp.setEnabled(Karaoke.createCounters, listOf(ProducerType.COUNTER))
    mltProp.setEnabled(Karaoke.createWatermark, listOf(ProducerType.WATERMARK))

    val maxTextWidthPx =
        Karaoke.frameWidthPx.toDouble() - Karaoke.songtextStartPositionXpx * 2      // maxTextWidth - максимальная ширина текста = ширина экрана минус 2 отступа

    // Ширина в пикселах суммарной самой длинной строки
    var maxTextWidthPxByFontSize =
        (Integer.max(song.voices.sumOf { it.maxWidthLinePx }.toInt(), song.voices.sumOf { it.maxWidthSingleLinePx }.toInt()) + Karaoke.songtextStartPositionXpx * (countVoices - 1)).toLong()

    // Максимальный размер шрифта берем из дефолтного значения
    var fontSongtextSizePt = Karaoke.voices[0].groups[0].mltText.font.size
    val step = if (maxTextWidthPxByFontSize > maxTextWidthPx) -1 else 1
    while (true) {
        if ((maxTextWidthPxByFontSize > maxTextWidthPx && step < 0) || (maxTextWidthPxByFontSize < maxTextWidthPx && step > 0)) {
            fontSongtextSizePt += step
            val a1 = (song.voices.sumOf {
                getTextWidthHeightPx(
                    it.maxWidthLineText,
                    Karaoke.voices[0].groups[0].mltText.font.name,
                    Karaoke.voices[0].groups[0].mltText.font.style,
                    fontSongtextSizePt
                ).first
            } + Karaoke.songtextStartPositionXpx * (countVoices).toLong()).toInt()
            val a2 = (song.voices.sumOf {
                getTextWidthHeightPx(
                    it.maxWidthSingleLineText,
                    Karaoke.voices[0].groups[0].mltText.font.name,
                    Karaoke.voices[0].groups[0].mltText.font.style,
                    fontSongtextSizePt
                ).first
            } + Karaoke.songtextStartPositionXpx * (countVoices - 1).toLong()).toInt()

            maxTextWidthPxByFontSize = Integer.max(a1, a2).toLong()
        } else {
            break
        }
    }

    val fontChordsSizePt = (fontSongtextSizePt * Karaoke.chordsHeightCoefficient).toInt()

    val mltTextSongtext = Karaoke.voices[0].groups[0].mltText.copy(
        "0",
        fontSongtextSizePt
    ) //Font(Karaoke.voices[0].groups[0].songtextTextMltText.font.name, Karaoke.voices[0].groups[0].songtextTextMltText.font.style, fontSongtextSizePt)
    val mltTextChords = Karaoke.chordsFont.copy(
        "0",
        fontChordsSizePt
    ) //Font(Karaoke.chordsFont.font.name, Karaoke.chordsFont.font.style, fontChordsSizePt)

    val symbolSongtextHeightPx = mltTextSongtext.h()
    val symbolSongtextWidthPx = mltTextSongtext.w()

    val quarterNoteLengthMs =
        if (song.settings.bpm == 0L) {
            (60000.0 / 120.0).toLong()
        } else {
            (60000.0 / song.settings.bpm).toLong()
        }
    // Находим длительность звучания 1/4 ноты в миллисекундах
    println("quarterNoteLengthMs = $quarterNoteLengthMs")
    val halfNoteLengthMs = quarterNoteLengthMs * 2

    val heightScrollerPx = 100L
    mltProp.setHeightScrollerPx(heightScrollerPx)

    val heightPxPerMsCoeff = (heightScrollerPx / song.voices[0].lines.filter { it.type == SongVoiceLineType.TEXT }.first().mltText.h().toDouble()) / 2

    val widthPxPerMsCoeff = song.voices.flatMap { voice ->
        voice.lines.filter{it.type == SongVoiceLineType.TEXT}.flatMap { line ->
            line.subtitles.map { subtitle ->
                subtitle.mltText
                subtitle.wPx / subtitle.durationMs.toDouble()
            }
        }
    }.max() * heightPxPerMsCoeff

    mltProp.setWidthPxPerMsCoeff(widthPxPerMsCoeff)
    mltProp.setHeightPxPerMsCoeff(heightPxPerMsCoeff)

    var currentVoiceOffset = 0

    val timeToScrollScreenMs = (Karaoke.frameWidthPx / widthPxPerMsCoeff).toLong()
    mltProp.setTimeToScrollScreenMs(timeToScrollScreenMs)

    // Цикл по голосам
    for (voiceId in 0 until countVoices) {

        val songVoice = song.voices[voiceId]

        val scrollLines = songVoice.lines.filter { it.type == SongVoiceLineType.TEXT }
        mltProp.setScrollLines(scrollLines, voiceId)

        val scrollTracks: MutableList<MutableList<Pair<SongVoiceLine, Int>>> = mutableListOf()

        scrollLines.forEachIndexed { indexLine, scrollLine ->
            val scrollLineStartMs = scrollLine.subtitles.first().startMs - timeToScrollScreenMs + startSilentOffsetMs
            val scrollLineEndMs = scrollLine.subtitles.last().startMs + scrollLine.subtitles.last().durationMs + timeToScrollScreenMs + startSilentOffsetMs
            val scrollLineDurationMs = scrollLineEndMs - scrollLineStartMs

            mltProp.setScrollLineStartMs(scrollLineStartMs, listOf(voiceId, indexLine))
            mltProp.setScrollLineEndMs(scrollLineEndMs, listOf(voiceId, indexLine))
            mltProp.setScrollLineDurationMs(scrollLineDurationMs, listOf(voiceId, indexLine))

            var scrollLineWasAddedToTrack = false
            scrollTracks.forEachIndexed { indexTrack, scrollTrack ->
                if (!scrollLineWasAddedToTrack) {
                    val lastLineInTrack = scrollTrack.last().first
                    val lastLineEndMs = lastLineInTrack.subtitles.last().startMs + lastLineInTrack.subtitles.last().durationMs + timeToScrollScreenMs + startSilentOffsetMs
                    if (lastLineEndMs < scrollLineStartMs) {
                        scrollTrack.add(Pair(scrollLine, indexLine))
                        mltProp.setScrollLineTrackId(indexTrack, listOf(voiceId, indexLine))
                        scrollLineWasAddedToTrack = true
                        return@forEachIndexed
                    }
                }
            }
            if (!scrollLineWasAddedToTrack) {
                scrollTracks.add(mutableListOf(Pair(scrollLine, indexLine)))
                mltProp.setScrollLineTrackId(scrollTracks.size-1, listOf(voiceId, indexLine))
            }



        }

        scrollTracks.forEachIndexed { indexTrack, scrollTrack ->
            mltProp.setScrollTrack(scrollTrack, listOf(voiceId, indexTrack))
        }

        mltProp.setCountChilds(scrollTracks.size, listOf(ProducerType.SCROLLERS, voiceId))
        mltProp.setCountChilds(scrollTracks.size, listOf(ProducerType.SCROLLERTRACK, voiceId))
        mltProp.setCountChilds(scrollLines.size, listOf(ProducerType.SCROLLER, voiceId))



        if (voiceId > 0) {
            currentVoiceOffset += (getTextWidthHeightPx(
                song.voices[voiceId - 1].maxWidthLineText,
                Karaoke.voices[0].groups[0].mltText.font.name,
                Karaoke.voices[0].groups[0].mltText.font.style,
                fontSongtextSizePt
            ).first + Karaoke.songtextStartPositionXpx).toInt()
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
        val propRectFaderChordsLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propRectBackChordsLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propProgressLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propHeaderLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propFlashLineValue = mutableListOf<String>() // Список свойств трансформации текста


        val propRectSongtextValueLineOddEven = listOf<MutableList<String>>(
            mutableListOf(),
            mutableListOf()
        )

        val propRectChordsValueLineOddEven = listOf<MutableList<String>>(
            mutableListOf(),
            mutableListOf()
        )

        val voiceSetting = Karaoke.voices[min(voiceId, Karaoke.voices.size - 1)]
        voiceSetting.replaceFontSize(fontSongtextSizePt)

        var currentGroup = 0 // Получаем номер текущей группы
        var groupSetting = voiceSetting.groups[min(
            currentGroup,
            voiceSetting.groups.size - 1
        )] // Получаем настройки для текущей группы
        // В строках есть и текстовые, и аккордовые, и комментарии

        // Пройдем по текстовым строкам и вставим пустые строки, если надо
        var currentPositionEnd = 0L // Устанавливаем текущую позицию конца в ноль
        var voiceLines = mutableListOf<SongVoiceLine>()
        val emptyLines = mutableListOf<SongVoiceLine>()
        var endTimeHidingHeaderMs: Long? = null
        songVoice.lines.filter { it.type == SongVoiceLineType.TEXT || it.type == SongVoiceLineType.EMPTY }
            .forEachIndexed { indexVoiceLineText, voiceLineText ->
                val silentDuration = convertTimecodeToMilliseconds(voiceLineText.start) - currentPositionEnd
                var linesToInsert: Long = silentDuration / songVoice.maxDurationMs
                if (indexVoiceLineText == 0 && linesToInsert == 0L) linesToInsert =
                    1L // Если строка первая - то перед ней обязательно вставляем хотя бы одну пусту строку
                if (linesToInsert > 0) {
                    val silentLineDuration: Long = silentDuration / linesToInsert
                    for (i in 1..linesToInsert) {
                        val startDuration =
                            convertMillisecondsToTimecode(currentPositionEnd) //  + silentLineDuration / 2
                        val endDuration = convertMillisecondsToTimecode(currentPositionEnd + silentLineDuration + startSilentOffsetMs)
                        val emptyVoiceLine = SongVoiceLine(
                            type = SongVoiceLineType.EMPTY,
                            subtitles = mutableListOf(),
                            symbols = mutableListOf(),
                            text = "",
                            group = voiceLineText.group,
                            start = startDuration,
                            end = endDuration,
                            startTp = null,
                            endTp = null,
                            isNeedCounter = false,
                            isFadeLine = false,
                            isMaxLine = false,
                            durationMs = silentLineDuration,
                            mltText = voiceLineText.mltText.copy("0")
                        )
                        emptyLines.add(emptyVoiceLine)
                        currentPositionEnd =
                            currentPositionEnd + silentLineDuration //convertTimecodeToMilliseconds(endDuration)
                    }
                }
                currentPositionEnd = convertTimecodeToMilliseconds(voiceLineText.end) + startSilentOffsetMs
                if (voiceLineText.type != SongVoiceLineType.EMPTY) endTimeHidingHeaderMs = currentPositionEnd
            }
        if (convertTimecodeToMilliseconds(song.endTimecode) - endTimeHidingHeaderMs!! < 10000) endTimeHidingHeaderMs =
            convertTimecodeToMilliseconds(song.endTimecode) - 10000

        // В списке emptyLines сформирован список пустых линий, которые надо добавить к общему списку линий
        // Добавим в список voiceLines все линии из songVoice.lines (текстовые, комментные и если надо - аккордные)
        // и все пустые линии из emptyLines. Отсортируем по времени начала
        if (song.songVersion == SongVersion.CHORDS) {
            voiceLines.addAll(songVoice.lines)
        } else {
            voiceLines.addAll(songVoice.lines.filter { it.type != SongVoiceLineType.CHORDS })
        }
        voiceLines.addAll(emptyLines)
        voiceLines = voiceLines.sortedBy { convertTimecodeToMilliseconds(it.start) }.toMutableList()

        // Теперь для каждой строки нужно прописать шрифт
        // Для каждого титра строки - шрифт и текст

        voiceLines.forEach { voiceLine ->

            // Смещаем время начало и конца линии на startSilentOffsetMs
            voiceLine.start = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(voiceLine.start) + startSilentOffsetMs)
            voiceLine.end = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(voiceLine.end) + startSilentOffsetMs)
            voiceLine.symbols.forEach { voiceLineSymbol ->
                if (voiceLineSymbol.start != "") voiceLineSymbol.start = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(voiceLineSymbol.start) + startSilentOffsetMs)
            }
            voiceLine.subtitles.forEach { subtitle ->
                if (subtitle.startTimecode != "") subtitle.startTimecode = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(subtitle.startTimecode) + startSilentOffsetMs)
                if (subtitle.endTimecode != "") subtitle.endTimecode = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(subtitle.endTimecode) + startSilentOffsetMs)
            }

            val fontSizeCoeff = when (voiceLine.type) {
                SongVoiceLineType.COMMENTS -> Karaoke.shortLineFontScaleCoeff
                SongVoiceLineType.CHORDS -> Karaoke.chordsHeightCoefficient
                else -> 1.0
            }
            currentGroup = voiceLine.group // Получаем номер текущей группы
            groupSetting = voiceSetting.groups[min(
                currentGroup,
                voiceSetting.groups.size - 1
            )] // Получаем настройки для текущей группы
            var currentGroupText = ""

            // Устанавливаем шрифты в зависимости от типа линии
            when (voiceLine.type) {
                SongVoiceLineType.CHORDS -> {
                    voiceLine.mltText =
                        Karaoke.chordsFont.copy(currentGroupText, (fontSongtextSizePt * fontSizeCoeff).toInt())
                    voiceLine.subtitles.forEach { subtitle ->
                        subtitle.mltText = voiceLine.mltText.copy(subtitle.mltText.text)
                        subtitle.mltTextBefore =
                            subtitle.mltTextBefore.copy(subtitle.mltTextBefore.text, fontSongtextSizePt)
                    }
                    voiceLine.symbols.forEach { symbol ->
                        symbol.mltText = voiceLine.mltText.copy(symbol.mltText.text)
                        symbol.mltTextBefore = symbol.mltTextBefore.copy(symbol.mltTextBefore.text, fontSongtextSizePt)
                    }
                }

                else -> {
                    voiceLine.mltText =
                        groupSetting.mltText.copy(currentGroupText, (fontSongtextSizePt * fontSizeCoeff).toInt())
                    voiceLine.subtitles.forEach { subtitle ->
                        subtitle.mltText = groupSetting.mltText.copy(
                            subtitle.mltText.text,
                            (fontSongtextSizePt * fontSizeCoeff).toInt()
                        )
                        subtitle.mltTextBefore = groupSetting.mltText.copy(
                            subtitle.mltTextBefore.text,
                            (fontSongtextSizePt * fontSizeCoeff).toInt()
                        )
                    }
                    voiceLine.symbols.forEach { symbol ->
                        symbol.mltText =
                            groupSetting.mltText.copy(symbol.mltText.text, (fontSongtextSizePt * fontSizeCoeff).toInt())
                        symbol.mltTextBefore = groupSetting.mltText.copy(
                            symbol.mltTextBefore.text,
                            (fontSongtextSizePt * fontSizeCoeff).toInt()
                        )
                    }
                }
            }

            // Если линия - текстовая - проходим по субтитрам и формирум лайнсимволы имея в виду биты если надо
            if (voiceLine.type == SongVoiceLineType.TEXT) {
                var textBefore = ""
                for (subtitle in voiceLine.subtitles) {
                    currentGroupText += subtitle.mltText.text
                }
                if (currentGroupText != "") {
                    voiceLine.symbols.add(
                        SongVoiceLineSymbol(
                            start = "",
                            mltText = voiceLine.mltText.copy(currentGroupText)
                        )
                    )
                }
            }

        }

        // Отметим те линии, для которых нужны счётчики
        var prevLineInEmptyOrComment = true
        voiceLines.forEach { voiceLine ->
            if (voiceLine.type !in listOf(SongVoiceLineType.EMPTY)) {
                voiceLine.isNeedCounter = prevLineInEmptyOrComment
            }
            prevLineInEmptyOrComment = (voiceLine.type in listOf(SongVoiceLineType.EMPTY))
        }

        // Пройдёмся по линиям и пропишем координаты Y для строк
        var currLinePositionY = 0
        voiceLines.forEach { voiceLine ->
            voiceLine.yPx = currLinePositionY
            currLinePositionY += voiceLine.hPx
        }
        val workAreaSongtextHeightPx = currLinePositionY

        val horizonPositionPx =
            ((Karaoke.frameHeightPx / 2 + symbolSongtextHeightPx.toLong() / 2) - Karaoke.horizonOffsetPx).toInt()    // horizonPosition - позиция горизонта = половина экрана + половина высоты символа - оффсет

        // Пройдёмся по линиям и пропишем TransformProperty
        voiceLines.filter { it.type in listOf(SongVoiceLineType.TEXT, SongVoiceLineType.EMPTY) }
            .forEachIndexed { indexVoiceLine, voiceLine ->

                val startTp = TransformProperty(
                    time = voiceLine.start,
                    x = currentVoiceOffset,
                    y = horizonPositionPx - voiceLine.yPx - voiceLine.hPx,
                    w = Karaoke.frameWidthPx,
                    h = workAreaSongtextHeightPx,
                    opacity = if (indexVoiceLine in 1 until voiceLines.filter {
                            it.type in listOf(
                                SongVoiceLineType.TEXT,
                                SongVoiceLineType.EMPTY
                            )
                        }.size - 1) 1.0 else 0.0
                )

                var time = voiceLine.end
                // Если текущий элемент не последний - надо проверить начало следующего элемента
                if (indexVoiceLine != voiceLines.filter {
                        it.type in listOf(
                            SongVoiceLineType.TEXT,
                            SongVoiceLineType.EMPTY
                        )
                    }.size - 1) {
                    val nextVoiceLine = voiceLines.filter {
                        it.type in listOf(
                            SongVoiceLineType.TEXT,
                            SongVoiceLineType.EMPTY
                        )
                    }[indexVoiceLine + 1] // Находим следующую строку
                    val diffInMills = getDiffInMilliseconds(
                        nextVoiceLine.start,
                        voiceLine.end
                    ) // Находим разницу во времени между текущей строкой и следующей
                    if (diffInMills < Karaoke.transferMinimumMsBetweenLinesToScroll) {                            // Если эта разница меньше 200 мс
                        voiceLine.end =
                            nextVoiceLine.start             // Сдвигаем конец текущей линии и конец последнего титра в ней до начала следующей
                        if (voiceLine.subtitles.isNotEmpty()) {
                            voiceLine.subtitles.last().endTimecode = voiceLine.end
                            time =
                                voiceLine.subtitles.last().startTimecode       // сдвигаем время classes.TransformProperty к началу последнего титра текущей строки
                        }
                    }
                }

                val endTp = TransformProperty(
                    time = time,
                    x = currentVoiceOffset,
                    y = horizonPositionPx - voiceLine.yPx - voiceLine.hPx,
                    w = Karaoke.frameWidthPx,
                    h = workAreaSongtextHeightPx,
                    opacity = if (indexVoiceLine in 1 until voiceLines.filter {
                            it.type in listOf(
                                SongVoiceLineType.TEXT,
                                SongVoiceLineType.EMPTY
                            )
                        }.size - 1) 1.0 else 0.0
                )

                voiceLine.startTp = startTp
                voiceLine.endTp = endTp

            }

        songVoice.lines = voiceLines



        propProgressLineValue.add("00:00:00.000=-${progressSymbolHalfWidth} $yOffset ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
        propProgressLineValue.add("${song.endTimecode}=${Karaoke.frameWidthPx - progressSymbolHalfWidth} $yOffset ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
        propFlashLineValue.add("00:00:00.000=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")

        mltProp.setVoiceSetting(voiceSetting, voiceId)
        mltProp.setOffset(currentVoiceOffset, voiceId)
        mltProp.setWorkAreaHeightPx(workAreaSongtextHeightPx.toLong(), listOf(ProducerType.SONGTEXT,voiceId))
        mltProp.setVoicelines(voiceLines, listOf(ProducerType.SONGTEXT,voiceId))
        mltProp.setSymbolHeightPx(symbolSongtextHeightPx, ProducerType.SONGTEXT)
        mltProp.setPositionYPx(horizonPositionPx.toLong(), ProducerType.HORIZON)
        mltProp.setPositionYPx((horizonPositionPx - symbolSongtextHeightPx).toLong(), ProducerType.COUNTER)
        mltProp.setPositionXPx(
            currentVoiceOffset + Karaoke.songtextStartPositionXpx - Karaoke.songtextStartOffsetXpx - symbolSongtextWidthPx,
            listOf(ProducerType.COUNTER, voiceId))
        mltProp.setFontSizePt(fontNameSizePt, listOf(ProducerType.HEADER, "SongName"))

        mltProp.setFontSizePt(fontSongtextSizePt, ProducerType.SONGTEXT)
        mltProp.setFontSizePt(fontChordsSizePt, ProducerType.FADERCHORDS)

        // Настало время прописать classes.TransformProperty для заливок

        val opacityFillValue = voiceSetting.fill.evenOpacity
        voiceLines.filter { it.type == SongVoiceLineType.TEXT }.forEachIndexed { indexLine, voiceLineText ->
            propFlashLineValue.add("${convertFramesToTimecode(convertTimecodeToFrames(voiceLineText.startTp!!.time) - 2)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
            propFlashLineValue.add("${voiceLineText.startTp?.time}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
            propFlashLineValue.add("${convertMillisecondsToTimecode(convertTimecodeToMilliseconds(voiceLineText.startTp!!.time) + 1000)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")

            propRectSongtextValueLineOddEven[indexLine % 2].add(voiceLineText.getFillTps(songVoice, horizonPositionPx, opacityFillValue))
        }

        // Настало время заняться счётчиками вступления.

        var startTimeFirstCounterMs: Long? = null
        voiceLines.filter { it.isNeedCounter }
            .forEach { lyric -> // Проходимся по всем строкам, для которых нужен счётчик
                if (startTimeFirstCounterMs == null) startTimeFirstCounterMs =
                    convertTimecodeToMilliseconds(lyric.start) - halfNoteLengthMs * 4
                for (counterNumber in 0..4) {
                    val startTimeMs = convertTimecodeToMilliseconds(lyric.start) - halfNoteLengthMs * counterNumber
                    val initTimeMs = convertFramesToMilliseconds(convertMillisecondsToFrames(startTimeMs) - 2)
                    val endTimeMs =
                        convertFramesToMilliseconds(convertMillisecondsToFrames(startTimeMs + halfNoteLengthMs) - 2)
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


        val chords = songVoice.lines.filter { it.type == SongVoiceLineType.CHORDS }
            .flatMap { it.symbols }.toList()
        val countFingerboards = (chords.size / Karaoke.maxCountChordsInFingerboard) + 1
        val propRectFingerboardLineValue: MutableMap<Int, MutableList<String>> = mutableMapOf()
        val propRectFingerboardValues: MutableMap<Int, String> = mutableMapOf()
        val chordW = (Karaoke.frameHeightPx / 4)
        val fingerboardW: MutableMap<Int, Int> = mutableMapOf()
        val fingerboardH = chordW

        mltProp.setFingerboardH(fingerboardH, voiceId)
        mltProp.setChordW(chordW, voiceId)
        mltProp.setChordH(chordW, voiceId)
        mltProp.setCountFingerboards(0, voiceId)

        if (song.songVersion == SongVersion.CHORDS && voiceId == 0) {

            propRectFaderChordsLineValue.add("${songStartTimecode}=0 -${fingerboardH + 50} ${Karaoke.frameWidthPx} ${fingerboardH + 50} 1.0")
            propRectFaderChordsLineValue.add("${songFadeInTimecode}=0 0 ${Karaoke.frameWidthPx} ${fingerboardH + 50} 1.0")

            propRectBackChordsLineValue.add("${songStartTimecode}=0 -${fingerboardH} ${Karaoke.frameWidthPx} ${fingerboardH} 1.0")
            propRectBackChordsLineValue.add("${songFadeInTimecode}=0 0 ${Karaoke.frameWidthPx} ${fingerboardH} 1.0")

            val chordXoffsetPx = Karaoke.frameWidthPx / 2 - chordW / 2 + chordW
            val chordsInFingerboards: MutableMap<Int, MutableList<SongVoiceLineSymbol>> = mutableMapOf()

            for (i in 0 until countFingerboards) {
                fingerboardW[i] = if (chords.size >= (i + 1) * Karaoke.maxCountChordsInFingerboard) {
                    Karaoke.maxCountChordsInFingerboard * chordW
                } else {
                    (chords.size % Karaoke.maxCountChordsInFingerboard) * chordW
                }
                chordsInFingerboards[i] = mutableListOf()
                propRectFingerboardLineValue[i] = mutableListOf()
                propRectFingerboardLineValue[i]?.add("${songStartTimecode}=${chordXoffsetPx} -${fingerboardH + 50} ${fingerboardW[i]} ${fingerboardH + 50} 1.0")
                propRectFingerboardLineValue[i]?.add("${songFadeInTimecode}=${chordXoffsetPx} 0 ${fingerboardW[i]} ${fingerboardH + 50} 1.0")
            }

            var prevChordX = 0
            var prevChordTimeCode = songFadeInTimecode
            var currChordX = 0

            var endMoveTimecode = ""
            var prevFingerboardIndex = 0
            chords.forEachIndexed { indexChords, chord ->
                val currFingerboardIndex = indexChords / Karaoke.maxCountChordsInFingerboard
                chordsInFingerboards[currFingerboardIndex]?.add(chord)
                val chordTimecode = chord.start
                val diffChordsMs =
                    convertTimecodeToMilliseconds(chordTimecode) - convertTimecodeToMilliseconds(prevChordTimeCode)
                val movingMs = if (diffChordsMs > 500) 500 else (diffChordsMs / 2).toInt()
                val startMoveTimecode =
                    convertMillisecondsToTimecode(convertTimecodeToMilliseconds(chordTimecode) - movingMs)
                endMoveTimecode = chordTimecode + startSilentOffsetMs
                currChordX = prevChordX - chordW
                for (i in 0 until countFingerboards) {
                    propRectFingerboardLineValue[i]?.add("${startMoveTimecode}=${prevChordX + chordXoffsetPx + (i * Karaoke.maxCountChordsInFingerboard * chordW)} 0 ${fingerboardW[i]} ${fingerboardH + 50} 1.0")
                    propRectFingerboardLineValue[i]?.add("${endMoveTimecode}=${currChordX + chordXoffsetPx + (i * Karaoke.maxCountChordsInFingerboard * chordW)} 0 ${fingerboardW[i]} ${fingerboardH + 50} 1.0")
                }
                prevChordX = currChordX
            }

            if (convertTimecodeToMilliseconds(endMoveTimecode) > endTimeHidingHeaderMs!!) {
                endTimeHidingHeaderMs = convertTimecodeToMilliseconds(endMoveTimecode)
//                endMoveTimecode = convertMillisecondsToTimecode(endTimeHidingHeaderMs!!)
            }

            for (i in 0 until countFingerboards) {
                propRectFingerboardLineValue[i]?.add("${endMoveTimecode}=${currChordX + chordXoffsetPx + (i * Karaoke.maxCountChordsInFingerboard * chordW)} 0 ${fingerboardW[i]} ${fingerboardH + 50} 1.0")
                propRectFingerboardLineValue[i]?.add("${convertMillisecondsToTimecode(endTimeHidingHeaderMs!! + halfNoteLengthMs * 4)}=${currChordX + chordXoffsetPx + (i * Karaoke.maxCountChordsInFingerboard * chordW)} -${fingerboardH + 50} ${fingerboardW[i]} ${fingerboardH + 50} 1.0")
            }

            for (i in 0 until countFingerboards) {
                propRectFingerboardValues[i] = propRectFingerboardLineValue[i]?.joinToString(";") ?: ""
                chordsInFingerboards[i]?.let { mltProp.setChords(it, listOf(voiceId, i)) }
                fingerboardW[i]?.let { mltProp.setFingerboardW(it, listOf(voiceId, i)) }
            }

            mltProp.setCountFingerboards(countFingerboards, voiceId)

            propRectFaderChordsLineValue.add("${endMoveTimecode}=0 0 ${Karaoke.frameWidthPx} ${fingerboardH + 50} 1.0")
            propRectFaderChordsLineValue.add("${convertMillisecondsToTimecode(endTimeHidingHeaderMs!! + halfNoteLengthMs * 4)}=0 -${fingerboardH + 50} ${Karaoke.frameWidthPx} ${fingerboardH + 50} 1.0")

            propRectBackChordsLineValue.add("${endMoveTimecode}=0 0 ${Karaoke.frameWidthPx} ${fingerboardH} 1.0")
            propRectBackChordsLineValue.add("${convertMillisecondsToTimecode(endTimeHidingHeaderMs!! + halfNoteLengthMs * 4)}=0 -${fingerboardH} ${Karaoke.frameWidthPx} ${fingerboardH} 1.0")


        }



        if (song.songVersion != SongVersion.CHORDS) {
            propHeaderLineValue.add("${convertMillisecondsToTimecode(startTimeFirstCounterMs!!)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
        }
        propHeaderLineValue.add("${convertMillisecondsToTimecode(startTimeFirstCounterMs!! + halfNoteLengthMs * 4)}=0 -492 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
        propHeaderLineValue.add("${convertMillisecondsToTimecode(endTimeHidingHeaderMs!!)}=0 -492 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
        propHeaderLineValue.add("${convertMillisecondsToTimecode(endTimeHidingHeaderMs!! + halfNoteLengthMs * 4)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")

        val propRectSongtextValue = propRectSongtextLineValue.joinToString(";")
        val propRectFaderChordsValue = propRectFaderChordsLineValue.joinToString(";")
        val propRectBackChordsValue = propRectBackChordsLineValue.joinToString(";")
        val propProgressValue = propProgressLineValue.joinToString(";")
        val propHeaderValue = propHeaderLineValue.joinToString(";")
        val propFlashValue = propFlashLineValue.joinToString(";")

        val propSongtextFillOddValue = propRectSongtextValueLineOddEven[0].joinToString(";")
        val propSongtextFillEvenValue = propRectSongtextValueLineOddEven[1].joinToString(";")
        val propFillCounter0Value = counters[0].joinToString(";")
        val propFillCounter1Value = counters[1].joinToString(";")
        val propFillCounter2Value = counters[2].joinToString(";")
        val propFillCounter3Value = counters[3].joinToString(";")
        val propFillCounter4Value = counters[4].joinToString(";")





        mltProp.setIgnoreCapo(false)
        val templateSongText = MkoSongText(mltProp, ProducerType.SONGTEXT, voiceId, 0).template()
        mltProp.setIgnoreCapo(true)
        val templateSongTextIgnoreCapo = MkoSongText(mltProp, ProducerType.SONGTEXT, voiceId, 0).template()

//        val templateSongTextLine = MkoSongTextLine(mltProp, ProducerType.SONGTEXTLINE, voiceId, 0).template()
        val templateHorizon = MkoHorizon(mltProp, ProducerType.HORIZON, voiceId, 0).template()
        val templateFlash = MkoFlash(mltProp, ProducerType.FLASH, voiceId, 0).template()
        val templateProgress = MkoProgress(mltProp, ProducerType.PROGRESS, voiceId, 0).template()
        val templateWatermark = MkoWatermark(mltProp, ProducerType.WATERMARK, voiceId, 0).template()
        val templateFaderText = MkoFaderText(mltProp, ProducerType.FADERTEXT, voiceId, 0).template()
        val templateFaderChords = MkoFaderChords(mltProp, ProducerType.FADERCHORDS, voiceId, 0).template()
        val templateBackChords = MkoBackChords(mltProp, ProducerType.BACKCHORDS, voiceId, 0).template()
        val templateHeader = MkoHeader(mltProp, ProducerType.HEADER, voiceId, 0).template()
        val templateSplashstart = MkoSplashStart(mltProp, ProducerType.SPLASHSTART, voiceId, 0).template()
        val templateBoosty = MkoBoosty(mltProp, ProducerType.BOOSTY, voiceId, 0).template()
        val templateCounter0 = MkoCounter(mltProp, ProducerType.COUNTER, voiceId, 0).template()
        val templateCounter1 = MkoCounter(mltProp, ProducerType.COUNTER, voiceId, 1).template()
        val templateCounter2 = MkoCounter(mltProp, ProducerType.COUNTER, voiceId, 2).template()
        val templateCounter3 = MkoCounter(mltProp, ProducerType.COUNTER, voiceId, 3).template()
        val templateCounter4 = MkoCounter(mltProp, ProducerType.COUNTER, voiceId, 4).template()

        val templateFingerboards: MutableMap<Int, MltNode> = mutableMapOf()
        if (song.songVersion == SongVersion.CHORDS) {
            for (i in 0 until countFingerboards) {
                templateFingerboards[i] = MkoFingerboard(mltProp, ProducerType.FINGERBOARD, voiceId, i).template()
            }
        }

        scrollLines.forEachIndexed { indexLine, scrollLine ->
            val templateScroller = MkoScroller(mltProp, ProducerType.SCROLLER, voiceId, indexLine).template()
            mltProp.setXmlData(templateScroller, listOf(ProducerType.SCROLLER, voiceId, indexLine))
        }

//        val templateFingerboard = getTemplateFingerboard(param)



        mltProp.setWorkAreaHeightPx(workAreaSongtextHeightPx.toLong(), listOf(ProducerType.SONGTEXT, voiceId))
        mltProp.setXmlData(templateSongText, listOf(ProducerType.SONGTEXT, voiceId))
        mltProp.setXmlData(templateSongTextIgnoreCapo, listOf(ProducerType.SONGTEXT, voiceId, "IgnoreCapo"))
        mltProp.setRect(propRectSongtextValue, listOf(ProducerType.SONGTEXT, voiceId))

//        mltProp.setXmlData(templateSongTextLine, listOf(ProducerType.SONGTEXTLINE, voiceId))
        mltProp.setXmlData(templateHorizon, listOf(ProducerType.HORIZON, voiceId))
        mltProp.setXmlData(templateFlash, listOf(ProducerType.FLASH, voiceId))
        mltProp.setRect(propFlashValue, listOf(ProducerType.FLASH, voiceId))
        mltProp.setRect(propFlashValue, listOf(ProducerType.FLASH))

        mltProp.setXmlData(templateWatermark, listOf(ProducerType.WATERMARK, voiceId))
        mltProp.setXmlData(templateFaderText, listOf(ProducerType.FADERTEXT, voiceId))

        mltProp.setXmlData(templateFaderChords, listOf(ProducerType.FADERCHORDS, voiceId))
        mltProp.setRect(propRectFaderChordsValue, listOf(ProducerType.FADERCHORDS, voiceId))
        mltProp.setRect(propRectFaderChordsValue, listOf(ProducerType.FADERCHORDS))

        mltProp.setXmlData(templateBackChords, listOf(ProducerType.BACKCHORDS, voiceId))
        mltProp.setRect(propRectBackChordsValue, listOf(ProducerType.BACKCHORDS, voiceId))
        mltProp.setRect(propRectBackChordsValue, listOf(ProducerType.BACKCHORDS))

        for (i in 0 until countFingerboards) {
            templateFingerboards[i]?.let { mltProp.setXmlData(it, listOf(ProducerType.FINGERBOARD, voiceId, i)) }
            propRectFingerboardValues[i]?.let { mltProp.setRect(it, listOf(ProducerType.FINGERBOARD, voiceId, i)) }
        }

        mltProp.setXmlData(templateProgress, listOf(ProducerType.PROGRESS, voiceId))
        mltProp.setRect(propProgressValue, listOf(ProducerType.PROGRESS, voiceId))
        mltProp.setRect(propProgressValue, listOf(ProducerType.PROGRESS))

        mltProp.setRect(propSongtextFillEvenValue, listOf(ProducerType.FILLCOLORSONGTEXT, voiceId, 0))
        mltProp.setRect(propSongtextFillOddValue, listOf(ProducerType.FILLCOLORSONGTEXT, voiceId, 1))

        mltProp.setXmlData(templateHeader, listOf(ProducerType.HEADER, voiceId))
        mltProp.setRect(propHeaderValue, listOf(ProducerType.HEADER, voiceId))
        mltProp.setRect(propHeaderValue, listOf(ProducerType.HEADER))

        mltProp.setXmlData(templateSplashstart, listOf(ProducerType.SPLASHSTART, voiceId))
        mltProp.setXmlData(templateBoosty, listOf(ProducerType.BOOSTY, voiceId))
        mltProp.setPath(getRandomFile(Karaoke.backgroundFolderPath, ".png"), listOf(ProducerType.BACKGROUND, voiceId))
        mltProp.setPath(getRandomFile(Karaoke.backgroundFolderPath, ".png"), listOf(ProducerType.BACKGROUND))


        mltProp.setXmlData(templateCounter0, listOf(ProducerType.COUNTER, voiceId, 0))
        mltProp.setRect(propFillCounter0Value, listOf(ProducerType.COUNTER, voiceId, 0))
        mltProp.setXmlData(templateCounter1, listOf(ProducerType.COUNTER, voiceId, 1))
        mltProp.setRect(propFillCounter1Value, listOf(ProducerType.COUNTER, voiceId, 1))
        mltProp.setXmlData(templateCounter2, listOf(ProducerType.COUNTER, voiceId, 2))
        mltProp.setRect(propFillCounter2Value, listOf(ProducerType.COUNTER, voiceId, 2))
        mltProp.setXmlData(templateCounter3, listOf(ProducerType.COUNTER, voiceId, 3))
        mltProp.setRect(propFillCounter3Value, listOf(ProducerType.COUNTER, voiceId, 3))
        mltProp.setXmlData(templateCounter4, listOf(ProducerType.COUNTER, voiceId, 4))
        mltProp.setRect(propFillCounter4Value, listOf(ProducerType.COUNTER, voiceId, 4))



    }

    val permissions = PosixFilePermissions.fromString("rwxr-x---")

    val templateProject = "<?xml version='1.0' encoding='utf-8'?>\n${getMlt(mltProp)}"
    val fileProject = File(song.settings.getOutputFilename(SongOutputFile.PROJECT, song.songVersion))
    val fileMlt = File(song.settings.getOutputFilename(SongOutputFile.MLT, song.songVersion))
    Files.createDirectories(Path(fileProject.parent))
    fileProject.writeText(templateProject)
    fileMlt.writeText(templateProject)
    val fileRun = File(song.settings.getOutputFilename(SongOutputFile.RUN, song.songVersion))
    fileRun.writeText("echo \"${song.settings.getOutputFilename(SongOutputFile.MLT, song.songVersion)}\"\n" +
            "melt -progress \"${song.settings.getOutputFilename(SongOutputFile.MLT, song.songVersion).toString()}\"\n")
    Files.setPosixFilePermissions(fileRun.toPath(), permissions)

    val fileDescription = File(song.settings.getOutputFilename(SongOutputFile.DESCRIPTION, song.songVersion))
    Files.createDirectories(Path(fileDescription.parent))
    fileDescription.writeText(song.settings.getDescription(songVersion))

    createBoostyTeaserPicture(song.settings)
    createBoostyFilesPicture(song.settings)
//    createVKPicture(song.settings)
//    createVKLinkPicture(song.settings)
    createVKLinkPictureWeb(song.settings)

    createSongTextFile(song.settings, song.songVersion)
    createSongDescriptionFile(song.settings, song.songVersion)

    createSongPicture(song.settings, song.songVersion)

    val filePictureChords = File(song.settings.getOutputFilename(SongOutputFile.PICTURECHORDS, song.songVersion))
    Files.createDirectories(Path(filePictureChords.parent))
    createSongChordsPicture(song.settings, song.settings.getOutputFilename(SongOutputFile.PICTURECHORDS, song.songVersion), song.songVersion, mltProp.getXmlData(listOf(ProducerType.SONGTEXT, 0, "IgnoreCapo")))

}
