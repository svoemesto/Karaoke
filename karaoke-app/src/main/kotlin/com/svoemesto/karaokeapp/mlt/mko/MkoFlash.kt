package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.model.TransformProperty

data class MkoFlash(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type)

    private val songVersion = mltProp.getSongVersion()
    private val frameWidthPx = mltProp.getFrameWidthPx()
    private val frameHeightPx = mltProp.getFrameHeightPx()
    private val songLengthFr = mltProp.getSongLengthFr()
    private val songStartTimecode = mltProp.getSongStartTimecode()
    private val songEndTimecode = mltProp.getSongEndTimecode()
    private val songFadeInTimecode = mltProp.getSongFadeInTimecode()
    private val songFadeOutTimecode = mltProp.getSongFadeOutTimecode()
    private val horizonPositionYPx = mltProp.getPositionYPx(ProducerType.HORIZON)
    private val fontSize = mltProp.getFontSize()
    private val symbolHeightPx = mltProp.getSymbolHeightPx()
    private val mkoFlashProducerRect = mltProp.getRect(listOf(type))
    private val inOffsetVideo = mltProp.getInOffsetVideo()

    override fun producer(): MltNode = mltGenerator
        .producer(
            props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                .propertyName("length", songLengthFr)
                .propertyName("kdenlive:duration", songEndTimecode)
                .propertyName("xmldata", template().toString().xmldata())
                .propertyName("meta.media.width", frameWidthPx)
                .propertyName("meta.media.height", frameHeightPx)
                .filterQtblend(mltGenerator.nameFilterQtblend, mkoFlashProducerRect)
                .build()
        )

    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
            val body = it as MutableList<MltNode>
            body.addAll(MltNodeBuilder().blank(inOffsetVideo).build())
            body.add(
                mltGenerator.entry(
                    nodes = MltNodeBuilder()
                        .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
                        .filterQtblend(mltGenerator.nameFilterQtblend, mainFilePlaylistTransformProperties())
                        .build()
                )
            )
        }
        return result
    }

    override fun mainFilePlaylistTransformProperties(): String {
        val tpStart = TransformProperty(time = songStartTimecode, x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 0.0)
        val tpFadeIn = TransformProperty(time = songFadeInTimecode, x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 1.0)
        val tpFadeOut = TransformProperty(
            time = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(songFadeOutTimecode) - 1000),
            x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 1.0
        )
        val tpEnd = TransformProperty(
            time = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(songEndTimecode) - 1000),
            x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 0.0
        )
        val resultListTp = listOf(tpStart, tpFadeIn, tpFadeOut, tpEnd)
        return resultListTp.joinToString(";")
    }

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()

    override fun template(): MltNode {
        val templateFlashGroup = mutableListOf<MltNode>()

        val haveNotes = songVersion.producers.contains(ProducerType.MELODYNOTE)
        val haveChords = songVersion.producers.contains(ProducerType.CHORDS)
        val deltaY = if (haveNotes) {
            val sylFontSize = fontSize
            val melodyNoteFontSize = (sylFontSize * Karaoke.melodyNoteHeightCoefficient).toInt()
            val melodyNoteMltTextHeight = Karaoke.melodyNoteFont.copy("C", melodyNoteFontSize).h()
            val noteHeight = (melodyNoteMltTextHeight * Karaoke.melodyNoteHeightOffsetCoefficient).toInt()

            val tabsHeightCoefficient = Karaoke.melodyTabsHeightCoefficient
            val tabsFontSize = (sylFontSize * tabsHeightCoefficient).toInt()
            val tabsFont = Karaoke.melodyTabsFont
            val tabsMltTextHeight = tabsFont.copy("C", tabsFontSize).h()
            val tabsHeightOffsetCoefficient = Karaoke.melodyTabsHeightOffsetCoefficient
            val heightBetweenTabsLines = (tabsMltTextHeight * tabsHeightOffsetCoefficient).toInt()

            val tabsHeight = tabsMltTextHeight + 5 * heightBetweenTabsLines
            (noteHeight + tabsHeight) / 2
        } else if (haveChords) {
            val sylFontSize = fontSize
            val chordsFontSize = (sylFontSize * Karaoke.chordsHeightCoefficient).toInt()
            val chordsMltTextHeight = Karaoke.chordsFont.copy("C", chordsFontSize).h()
            val chordHeight = (chordsMltTextHeight * Karaoke.chordsHeightOffsetCoefficient).toInt()
            chordHeight / 2
        } else {
            0
        }

        val templateFlash = MltNode(
            type = ProducerType.HORIZON,
            name = "kdenlivetitle",
            fields = mutableMapOf(
                Pair("duration","0"),
                Pair("LC_NUMERIC","C"),
                Pair("width","$frameWidthPx"),
                Pair("height","$frameHeightPx"),
                Pair("out","0"),
            ),
            body = mutableListOf(
                MltNode(
                    name = "item",
                    fields = mutableMapOf(
                        Pair("type","QGraphicsRectItem"),
                        Pair("z-index","0"),
                    ),
                    body = mutableListOf(
                        MltNode(
                            name = "position",
                            fields = mutableMapOf(
                                Pair("x","0"),
                                Pair("y","${horizonPositionYPx + deltaY}")
                            ),
                            body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(Pair("zoom","100")), body = "1,0,0,0,1,0,0,0,1"))
                        ),
                        MltNode(
                            name = "content",
                            fields = mutableMapOf(
                                Pair("brushcolor", Karaoke.flashColor.mlt()),
                                Pair("pencolor", "0,0,0,255"),
                                Pair("penwidth","0"),
                                Pair("rect","0,0,${frameWidthPx},3")
                            )
                        )
                    )
                ),

                MltNode(
                    name = "item",
                    fields = mutableMapOf(
                        Pair("type","QGraphicsRectItem"),
                        Pair("z-index","0"),
                    ),
                    body = mutableListOf(
                        MltNode(
                            name = "position",
                            fields = mutableMapOf(
                                Pair("x","0"),
                                Pair("y","${horizonPositionYPx - symbolHeightPx + Karaoke.horizonOffsetPx - deltaY + 3}")
                            ),
                            body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(Pair("zoom","100")), body = "1,0,0,0,1,0,0,0,1"))
                        ),
                        MltNode(
                            name = "content",
                            fields = mutableMapOf(
                                Pair("brushcolor", Karaoke.flashColor.mlt()),
                                Pair("pencolor", "0,0,0,255"),
                                Pair("penwidth","0"),
                                Pair("rect","0,0,${frameWidthPx},3")
                            )
                        )
                    )
                ),

                templateFlashGroup,
                MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${frameWidthPx},${frameHeightPx}"))),
                MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${frameWidthPx},${frameHeightPx}"))),
                MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
            )
        )

        return templateFlash
    }
}

