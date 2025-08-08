package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.*
import java.awt.Font

data class MkoChords(
    val mltProp: MltProp,
    val type: ProducerType = ProducerType.CHORDS,
    val voiceId: Int = 0,
    val lineId: Int = 0,
    val elementId: Int = 0
): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type, voiceId, lineId, elementId)

    private val songVersion = mltProp.getSongVersion()
    private val frameWidthPx = mltProp.getFrameWidthPx()
    private val frameHeightPx = mltProp.getFrameHeightPx()
    private val settings = mltProp.getSettings()
    private val songEndTimecode  = mltProp.getSongEndTimecode()
    private var lineDurationOnScreen = mltProp.getDurationOnScreen(listOf(ProducerType.LINE, voiceId, lineId))
    private var folderIdLines = mltProp.getId(listOf(ProducerType.CHORDS, voiceId))
    private val lineEndTimecode = if (lineDurationOnScreen > 0) {
        convertMillisecondsToTimecode(lineDurationOnScreen)
    } else {
        lineDurationOnScreen = convertTimecodeToMilliseconds(songEndTimecode)
        songEndTimecode
    }
    private val capo = mltProp.getSongCapo()

    override fun producer(): MltNode {
        var widthAreaPx= frameWidthPx
        var heightAreaPx= frameHeightPx

        val sett = settings
        if (sett != null) {
            try {
                val element = sett.voicesForMlt[voiceId].getLines()[lineId].getElements(songVersion)[elementId]
                widthAreaPx = element.w()
                heightAreaPx = element.h()
            } catch (_: Exception) {
            }
        }

        return mltGenerator
            .producer(
                timecodeOut = lineEndTimecode,
                props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                    .propertyName("kdenlive:folderid", folderIdLines)
                    .propertyName("length", convertMillisecondsToFrames(lineDurationOnScreen))
                    .propertyName("kdenlive:duration", lineEndTimecode)
                    .propertyName("xmldata", template().toString().xmldata())
                    .propertyName("meta.media.width", widthAreaPx)
                    .propertyName("meta.media.height", heightAreaPx)
                    .build()
            )
    }

    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
            val body = it as MutableList<MltNode>
            body.add(
                mltGenerator.entry(
                    timecodeOut = lineEndTimecode,
                    nodes = MltNodeBuilder()
                        .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
                        .build()
                )
            )
        }
        return result
    }

    override fun mainFilePlaylistTransformProperties(): String = ""

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor(timecodeOut = lineEndTimecode)

    override fun template(): MltNode {

        val sett = settings ?: return MltNode()
        val element = try {
            sett.voicesForMlt[voiceId].getLines()[lineId].getElements(songVersion).filter { it.type == SettingVoiceLineElementTypes.TEXT }.first()
        } catch (e: Exception) {
            return MltNode()
        }

        var widthAreaPx= element.w()
        var heightAreaPx= element.h()

        val haveChords = songVersion.producers.contains(ProducerType.CHORDS) && element.getSyllables().any { it.note != "" }

        val deltaY = if (haveChords) {
//            val sylFontSize = element.fontSize
//            val chordsFontSize = (sylFontSize * Karaoke.chordsHeightCoefficient).toInt()
//            val chordsMltTextHeight = Karaoke.chordsFont.copy("C", chordsFontSize).h()
//            val chordHeight = (chordsMltTextHeight * Karaoke.chordsHeightOffsetCoefficient).toInt()
            0
        } else {
            0
        }

        val x = 0
        val y = deltaY
        val body: MutableList<MltNode> = mutableListOf()

        // Формируем текст аккорда в зависимости он наличия каподастра и располагаем его над гласной слога

        element.getSyllables().filter {it.chord.isNotBlank()}.forEach { chordElement ->

            // Находим позицию гласной буквы в слоге (0 - если гласная первая или если её нет)
            fun String.firstVowelIndex(): Int {
                val vovels = "♪ёуеыаоэяиюeuioaїієѣ" + "ёуеыаоэяиюeuioaїієѣ".uppercase()
                for (i in this.indices) {
                    if (this[i] in vovels) {
                        return i
                    }
                }
                return 0
            }

            val textCurrentSyllables = chordElement.text
            val textSyllablesWithPrevious = chordElement.textSyllablesWithPrevious()
            val textPreviousSyllables = textSyllablesWithPrevious.substring(0, textSyllablesWithPrevious.length - textCurrentSyllables.length)
            val textSyllablesBeforeChord = textPreviousSyllables + textCurrentSyllables.substring(0, textCurrentSyllables.firstVowelIndex())

            val chordElementText = getTransposingChord(chordElement.chord, capo)

            val chordX = Karaoke.voices[0].groups[chordElement.groupId].mltText.copy(textSyllablesBeforeChord, chordElement.fontSize).w()

            val sylFontSize = chordElement.fontSize
            val chordsFontSize = (sylFontSize * Karaoke.chordsHeightCoefficient).toInt()
            val chordMltText = Karaoke.chordsFont.copy(chordElementText, chordsFontSize)

            body.add(
                MltNode(
                    name = "item",
                    fields = mutableMapOf(
                        Pair("type","QGraphicsTextItem"),
                        Pair("z-index","0"),
                    ),
                    body = mutableListOf(
                        // Начальная позиция прямоугольника, которая будет считаться для него началом координат
                        MltNode(
                            name = "position",
                            fields = mutableMapOf(
                                Pair("x","${chordX}"),
                                Pair("y","${y}")
                            ),
                            body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(Pair("zoom","100")), body = "1,0,0,0,1,0,0,0,1"))
                        ),
                        chordMltText.mltNode(chordMltText.text)
                    )
                )
            )

        }

        // Добавляем конечные узлы - вьюпорт, бэкграунд и т.п.
        body.addAll(
            MltNodeBuilder()
                .startviewport("0,0,${frameWidthPx},${frameHeightPx}")
                .endviewport("0,0,${frameWidthPx},${frameHeightPx}")
                .background("0,0,0,0")
                .build()
        )

        return MltNode(
            name = "kdenlivetitle",
            fields = PropertiesMltNodeBuilder()
                .duration(convertMillisecondsToFrames(lineDurationOnScreen).toString())
                .LC_NUMERIC("C")
                .width("$frameWidthPx")
                .height("$frameHeightPx")
                .`out`((convertMillisecondsToFrames(lineDurationOnScreen)-1).toString())
                .build(),
            body = body
        )
    }
}
