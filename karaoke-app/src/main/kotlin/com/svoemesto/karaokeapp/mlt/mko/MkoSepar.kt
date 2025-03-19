package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.*
import java.awt.Font

data class MkoSepar(
    val mltProp: MltProp,
    val type: ProducerType = ProducerType.SEPAR,
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
    private var folderIdLines = mltProp.getId(listOf(ProducerType.LINES, voiceId))
    private val lineEndTimecode = if (lineDurationOnScreen > 0) {
        convertMillisecondsToTimecode(lineDurationOnScreen)
    } else {
        lineDurationOnScreen = convertTimecodeToMilliseconds(songEndTimecode)
        songEndTimecode
    }

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

        val haveNotes = songVersion.producers.contains(ProducerType.MELODYNOTE) && element.getSyllables().any { it.note != "" }

        val deltaY = if (haveNotes) {
            val sylFontSize = element.fontSize
            val melodyNoteFontSize = (sylFontSize * Karaoke.melodyNoteHeightCoefficient).toInt()
            val melodyNoteMltTextHeight = Karaoke.melodyNoteFont.copy("C", melodyNoteFontSize).h()
            (melodyNoteMltTextHeight * Karaoke.melodyNoteHeightOffsetCoefficient).toInt()
        } else {
            0
        }

        var widthAreaPx= element.w()
        var heightAreaPx = element.h() + deltaY

        val x = 0
        val y = 0
        val body: MutableList<MltNode> = mutableListOf()

        // Формируем прямоугольные области для каждого слога и добавляем их в переменную body

        val rects = element.transformProperties().asRects()
        rects.forEachIndexed { index, rect ->
            val color = Karaoke.separLineColor.mlt()

            body.add(
                MltNode(
                    name = "item",
                    fields = mutableMapOf(
                        Pair("type","QGraphicsRectItem"),
                        Pair("z-index","0"),
                    ),
                    body = mutableListOf(
                        // Начальная позиция прямоугольника, которая будет считаться для него началом координат
                        MltNode(
                            name = "position",
                            fields = mutableMapOf(
                                Pair("x","${rect.x}"),
                                Pair("y","0")
                            ),
                            body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(Pair("zoom","100")), body = "1,0,0,0,1,0,0,0,1"))
                        ),
                        MltNode(
                            name = "content",
                            fields = mutableMapOf(
                                Pair("brushcolor", color),
                                Pair("pencolor", "0,0,0,255"),
                                Pair("penwidth","0"),
                                Pair("rect","0,0,2,${heightAreaPx}")
                            )
                        )
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
