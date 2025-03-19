package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.*

data class MkoHorizon(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {
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
    private val symbolHeightPx = mltProp.getSymbolHeightPx()
    private val fontSize = mltProp.getFontSize()
    private val inOffsetVideo = mltProp.getInOffsetVideo()
    private val songLengthMs = mltProp.getLengthMs("Song") + mltProp.getSettings()!!.getStartSilentOffsetMs()
    private val textLines = mltProp.getSettings()!!.voicesForMlt[0].textLines(songVersion)


    override fun producer(): MltNode = mltGenerator
        .producer(
            props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                .propertyName("length", songLengthFr)
                .propertyName("kdenlive:duration", songEndTimecode)
                .propertyName("xmldata", template().toString().xmldata())
                .propertyName("meta.media.width", frameWidthPx)
                .propertyName("meta.media.height", frameHeightPx)
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
        val templateHorizonGroup = mutableListOf<MltNode>()

        val voiceLines = mltProp.getVoicelines(listOf(ProducerType.SONGTEXT,0))

        val haveNotes = songVersion.producers.contains(ProducerType.MELODYNOTE)
        val deltaY = if (haveNotes) {
            val sylFontSize = fontSize
            val melodyNoteFontSize = (sylFontSize * Karaoke.melodyNoteHeightCoefficient).toInt()
            val melodyNoteMltTextHeight = Karaoke.melodyNoteFont.copy("C", melodyNoteFontSize).h()
            (melodyNoteMltTextHeight * Karaoke.melodyNoteHeightOffsetCoefficient).toInt() / 2
        } else {
            0
        }

        if (Karaoke.paintHorizon) {
            textLines.forEach { textLine ->

                val lineX = ((textLine.lineStartMs.toDouble() / (songLengthMs)) * (frameWidthPx.toLong())).toLong()
                val lineW = ((textLine.lineEndMs.toDouble() / (songLengthMs)) * (frameWidthPx.toLong())).toLong() - lineX

                templateHorizonGroup.add(
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
                                    Pair("y","${horizonPositionYPx + deltaY}"),
                                ),
                                body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(Pair("zoom","100")), body = "1,0,0,0,1,0,0,0,1"))
                            ),
                            MltNode(
                                name = "content",
                                fields = mutableMapOf(
                                    Pair("brushcolor", Karaoke.horizonColors[0].mlt()),
                                    Pair("pencolor","0,0,0,255"),
                                    Pair("penwidth","0"),
                                    Pair("rect","$lineX,0,$lineW,3")
                                )
                            )
                        )
                    )
                )

            }

        }



        val templateHorizon = MltNode(
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
                                Pair("brushcolor", Karaoke.horizonColor.mlt()),
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
                                Pair("brushcolor", Karaoke.horizonColor.mlt()),
                                Pair("pencolor", "0,0,0,255"),
                                Pair("penwidth","0"),
                                Pair("rect","0,0,${frameWidthPx},3")
                            )
                        )
                    )
                ),

                templateHorizonGroup,
                MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${frameWidthPx},${frameHeightPx}"))),
                MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${frameWidthPx},${frameHeightPx}"))),
                MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
            )
        )

        return templateHorizon
    }

}
