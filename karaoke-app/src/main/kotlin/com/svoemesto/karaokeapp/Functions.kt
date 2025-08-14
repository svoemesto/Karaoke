package com.svoemesto.karaokeapp//import model.Lyric
import com.svoemesto.karaokeapp.mlt.getMlt
import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.services.SNS
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
//import java.nio.file.Path
import kotlin.io.path.Path

fun getVoices(settings: Settings, songVersion: SongVersion) : List<SettingVoice> {

    val listOfVoices: MutableList<SettingVoice> = mutableListOf()
    val countNotEmptyVoices = settings.sourceMarkersList.filter { it.isNotEmpty() }.size

    if (countNotEmptyVoices == 0) return listOfVoices
    val startSilentOffsetMs = settings.getStartSilentOffsetMs()
    for (voiceIndex in 0 until countNotEmptyVoices) {

        val voice = SettingVoice(rootId = settings.id)
        voice.voiceId = voiceIndex
        voice.rootSongLengthMs = settings.songLengthMs
        voice.rootStartSilentOffsetMs = settings.getStartSilentOffsetMs()

        val voiceMarkers = settings.sourceMarkersList[voiceIndex].filter { it.markertype in songVersion.markertypes.map { it.value } }
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
                Markertype.SETTING.value -> {
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
                                        note = "",
                                        chord = "",
                                        stringlad = "",
                                        locklad = "",
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
                Markertype.SYLLABLES.value, Markertype.NOTE.value, Markertype.CHORD.value -> {

                    val textSyllable = if (sourceMarker.label.isNotEmpty()) {
                        var txt = sourceMarker.label.replace("_", " ")
                        if (sourceMarker.markertype == Markertype.NOTE.value) {
                            txt = " . "
                        }
                        if (sourceMarker.markertype == Markertype.CHORD.value) {
                            if (sourceMarker.label.isBlank() || sourceMarker.label == sourceMarker.chord) {
                                txt = "♪  "
                            } else {
                                txt = sourceMarker.label
                            }

                        }
                        if (tmpTextSyllables.isEmpty()){
                            txt = txt.uppercaseFirstLetter()
                            prevTextSyllable = null
                        }
                        currentText += txt
                        SettingVoiceLineElementSyllable(
                            rootId = settings.id,
                            text = txt,
                            note = sourceMarker.note,
                            chord = sourceMarker.chord,
                            stringlad = sourceMarker.stringlad,
                            locklad = sourceMarker.locklad,
                            syllableStartMs = timeMs,
                            syllableEndMs = timeMs,
                            previous = prevTextSyllable
                        )
                    } else null

                    if (textSyllable != null) {
                        tmpTextSyllables.add(textSyllable)
                        prevTextSyllable = textSyllable
                    }

                    lastTextLineWasComment = false
                }
                Markertype.ENDOFSYLLABLES.value, Markertype.ENDOF_NOTE.value, Markertype.ENDOF_CHORD.value -> {

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
                        note = sourceMarker.note,
                        chord = sourceMarker.chord,
                        stringlad = sourceMarker.stringlad,
                        locklad = sourceMarker.locklad,
                        syllableStartMs = timeMs,
                        syllableEndMs = timeMs,
                        previous = prevTextSyllable
                    )
                    tmpTextSyllables.add(textSyllable)
                    prevTextSyllable = textSyllable
                    currentText += txt
                }
                Markertype.ENDOFLINE.value, Markertype.EOL_NOTE.value, Markertype.EOL_CHORD.value -> {

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
                Markertype.NEWLINE.value, Markertype.NEWLINE_NOTE.value, Markertype.NEWLINE_CHORD.value -> {
                    currentText = ""
                    if (tmpLines.isNotEmpty()) {
                        tmpLines.add(SettingVoiceLine.newLine(settings.id,timeMs,groupId))
                    }
                }
                else -> {} // "unmute", "beat", и т.п.
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

    if (songVersion in listOf(SongVersion.CHORDS, SongVersion.CHORDSVK) && (!File(settings.drumsNameFlac).exists() || !File(settings.bassNameFlac).exists())) {
        val args = settings.argsDemucs5()
        args.forEach { arg -> runCommand(arg) }
    }

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
    createSponsrTeaserPicture(settings)
//    createVKPicture(settings)
//    createVKLinkPicture(settings)
    createVKLinkPictureWeb(settings)

    createSongTextFile(settings, songVersion)
    createSongDescriptionFile(settings, songVersion)
    createSongPicture(settings, songVersion)

//    val filePictureChords = File(settings.getOutputFilename(SongOutputFile.PICTURECHORDS, songVersion))
//    Files.createDirectories(Path(filePictureChords.parent))
//    createSongChordsPicture(settings, settings.getOutputFilename(SongOutputFile.PICTURECHORDS, songVersion), songVersion, mltProp.getXmlData(listOf(ProducerType.SONGTEXT, 0, "IgnoreCapo")))

    SNS.send(SseNotification.message(Message(
        type = "info",
        head = "createKaraoke",
        body = "createKaraoke версии «${songVersion.name}» для песни «${settings.fileName}» прошло успешно."
    )))

}