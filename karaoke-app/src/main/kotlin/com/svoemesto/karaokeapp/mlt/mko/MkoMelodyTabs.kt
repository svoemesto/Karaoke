package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.*

data class MkoMelodyTabs(
    val mltProp: MltProp,
    val type: ProducerType = ProducerType.MELODYTABS,
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

        val sylFontSize = element.fontSize
        val tabsHeightCoefficient = Karaoke.melodyTabsHeightCoefficient
        val tabsFontSize = (sylFontSize * tabsHeightCoefficient).toInt()
        val tabsFont = Karaoke.melodyTabsFont
        val tabsMltTextHeight = tabsFont.copy("C", tabsFontSize).h()
        val tabsHeightOffsetCoefficient = Karaoke.melodyTabsHeightOffsetCoefficient
        val heightBetweenTabsLines = (tabsMltTextHeight * tabsHeightOffsetCoefficient).toInt()
        val offsetYTabsLines = tabsMltTextHeight / 2

        val tabsLineColor = Karaoke.tabsLineColor.mlt()

        val openStringHeightCoefficient = Karaoke.melodyOpenStringHeightCoefficient
        val openStringFontSize = (sylFontSize * openStringHeightCoefficient).toInt()
        val openStringFont = Karaoke.melodyOpenStringFont
        val openStringMltTextHeight = openStringFont.copy("C", openStringFontSize).h()


        var widthAreaPx= element.w()
        var heightAreaPx= tabsMltTextHeight + 5 * heightBetweenTabsLines

        val x = 0
        val y = 0
        val body: MutableList<MltNode> = mutableListOf()

        // Рисуем линии табулатуры
        (0 until 6).forEach { indexLine ->
            val lineX = 0
            val lineY = offsetYTabsLines + heightBetweenTabsLines * indexLine - 1
            val lineW = widthAreaPx
            val lineH = 3

            body.add(
                MltNode(
                    name = "item",
                    fields = mutableMapOf(
                        Pair("type","QGraphicsRectItem"),
                        Pair("z-index","0"),
                    ),
                    body = mutableListOf(
                        // Начальная позиция прямоугольника, которая будет считаться для него началом координат
                        MltNode(name = "position", fields = mutableMapOf(Pair("x","0"), Pair("y","0")), body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(Pair("zoom","100")), body = "1,0,0,0,1,0,0,0,1"))),
                        MltNode(
                            name = "content",
                            fields = mutableMapOf(
                                Pair("brushcolor", tabsLineColor),
                                Pair("pencolor", "0,0,0,255"),
                                Pair("penwidth","0"),
                                Pair("rect","${lineX},${lineY},${lineW},${lineH}")
                            )
                        )
                    )
                )
            )
        }
        // Рисуем вертикальные линии
        val verticalLinesProps = listOf(
            "0,${offsetYTabsLines},3,${heightAreaPx - tabsMltTextHeight}",
            "7,${offsetYTabsLines},3,${heightAreaPx - tabsMltTextHeight}",
            "${widthAreaPx - 3},${offsetYTabsLines},3,${heightAreaPx - tabsMltTextHeight}",
            "${widthAreaPx - 8},${offsetYTabsLines},3,${heightAreaPx - tabsMltTextHeight}"
        )
        verticalLinesProps.forEach { verticalLinesProp ->
            body.add(
                MltNode(name = "item", fields = mutableMapOf(Pair("type","QGraphicsRectItem"), Pair("z-index","0"),),
                    body = mutableListOf(
                        // Начальная позиция прямоугольника, которая будет считаться для него началом координат
                        MltNode(name = "position", fields = mutableMapOf(Pair("x","0"), Pair("y","0")), body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(Pair("zoom","100")), body = "1,0,0,0,1,0,0,0,1"))),
                        MltNode(
                            name = "content",
                            fields = mutableMapOf(
                                Pair("brushcolor", tabsLineColor),
                                Pair("pencolor", "0,0,0,255"),
                                Pair("penwidth","0"),
                                Pair("rect", verticalLinesProp)
                            )
                        )
                    )
                )
            )
        }

        // Рисуем названия нот открытых струн
        val openStringNotes = listOf("E", "B", "G", "D", "A", "e")
        openStringNotes.forEachIndexed { indexString, openStringNote ->
            val openStringMltText = openStringFont.copy(openStringNote, openStringFontSize)
            val lineY = offsetYTabsLines + heightBetweenTabsLines * indexString
            val nodeY = lineY - openStringMltTextHeight / 2
            body.add(
                MltNode(name = "item", fields = mutableMapOf(Pair("type","QGraphicsTextItem"), Pair("z-index","0"),),
                    body = mutableListOf(
                        // Начальная позиция прямоугольника, которая будет считаться для него началом координат
                        MltNode(
                            name = "position",
                            fields = mutableMapOf(
                                Pair("x","12"),
                                Pair("y","${nodeY}")
                            ),
                            body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(Pair("zoom","100")), body = "1,0,0,0,1,0,0,0,1"))
                        ),
                        openStringMltText.mltNode(openStringMltText.text)
                    )
                )
            )
        }

        element.getSyllables().forEach { noteElement ->
            val stringLadText = noteElement.stringlad
            if (stringLadText.contains("|")) {
                val (stringText, ladText) = stringLadText.split("|")
                val stringIndex = stringText.toInt()
                val ladIndex = ladText.toInt()


                val initialXposition = noteElement.x()
                val areaW = noteElement.w()

                val tabsMltText = tabsFont.copy(ladText, tabsFontSize)

                val tabsMltTextWidth = tabsMltText.w()
                val tabsMltTextHeight = tabsMltText.h()

                val deltaX = (areaW - tabsMltTextWidth) / 2

                val tabsX = initialXposition + deltaX
                val lineY = offsetYTabsLines + heightBetweenTabsLines * stringIndex
                val tabsY = lineY - tabsMltTextHeight / 2

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
                                    Pair("x","${tabsX}"),
                                    Pair("y","${tabsY}")
                                ),
                                body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(Pair("zoom","100")), body = "1,0,0,0,1,0,0,0,1"))
                            ),
                            tabsMltText.mltNode(tabsMltText.text)
                        )
                    )
                )

            }

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
