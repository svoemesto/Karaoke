package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.model.TransformProperty
import com.svoemesto.karaokeapp.xmldata

data class MkoFaderText(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type)

    private val frameWidthPx = mltProp.getFrameWidthPx()
    private val frameHeightPx = mltProp.getFrameHeightPx()
    private val songLengthFr = mltProp.getSongLengthFr()
    private val songStartTimecode = mltProp.getSongStartTimecode()
    private val songEndTimecode = mltProp.getSongEndTimecode()
    private val songFadeInTimecode = mltProp.getSongFadeInTimecode()
    private val songFadeOutTimecode = mltProp.getSongFadeOutTimecode()
    private val fontSize = mltProp.getFontSize()
    private val inOffsetVideo = mltProp.getInOffsetVideo()

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
            @Suppress("UNCHECKED_CAST")
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
        val tpFadeOut = TransformProperty(time = songFadeOutTimecode, x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 1.0)
        val tpEnd = TransformProperty(time = songEndTimecode, x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 0.0)
        val resultListTp = listOf(tpStart, tpFadeIn, tpFadeOut, tpEnd)
        return resultListTp.joinToString(";")
    }
    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()

    override fun template(): MltNode {
        val w = frameWidthPx
        val h = Karaoke.voices[0].groups[0].mltText.copy("W", fontSize).h().toLong() * 2
        val x = 0
        val yTop = 0
        val yBottom = frameHeightPx - h

        return MltNode(
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
                                Pair("x","$x"),
                                Pair("y","$yTop")
                            ),
                            body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(Pair("zoom","100")), body = "1,0,0,0,1,0,0,0,1"))
                        ),
                        MltNode(
                            name = "content",
                            fields = mutableMapOf(
                                Pair("brushcolor","0,0,0,255"),
                                Pair("pencolor","0,0,0,255"),
                                Pair("penwidth","0"),
                                Pair("penwidth","0"),
                                Pair("rect","0,0,$w,$h"),
                                Pair("gradient","#ff000000;#00bf4040;0;100;90")
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
                                Pair("x","$x"),
                                Pair("y","$yBottom")
                            ),
                            body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(Pair("zoom","100")), body = "1,0,0,0,1,0,0,0,1"))
                        ),
                        MltNode(
                            name = "content",
                            fields = mutableMapOf(
                                Pair("brushcolor","0,0,0,255"),
                                Pair("pencolor","0,0,0,255"),
                                Pair("penwidth","0"),
                                Pair("penwidth","0"),
                                Pair("rect","0,0,$w,$h"),
                                Pair("gradient","#ff000000;#00bf4040;100;0;90")
                            )
                        )
                    )
                ),
                MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${frameWidthPx},${frameHeightPx}"))),
                MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${frameWidthPx},${frameHeightPx}"))),
                MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
            )
        )

    }
}
