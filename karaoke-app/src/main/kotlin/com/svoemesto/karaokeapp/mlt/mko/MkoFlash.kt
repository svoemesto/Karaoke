package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.mlt
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.xmldata
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType

data class MkoFlash(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type)


    override fun producer(): MltNode = mltGenerator
        .producer(
            props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                .propertyName("length", mltProp.getLengthFr("Song"))
                .propertyName("kdenlive:duration", mltProp.getEndTimecode("Song"))
                .propertyName("xmldata", mltProp.getXmlData(listOf(type, voiceId)).toString().xmldata())
                .propertyName("meta.media.width", Karaoke.frameWidthPx)
                .propertyName("meta.media.height", Karaoke.frameHeightPx)
                .filterQtblend(mltGenerator.nameFilterQtblend, mltProp.getRect(listOf(type, voiceId)))
                .build()
        )

    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
            val body = it as MutableList<MltNode>
            body.addAll(MltNodeBuilder().blank(mltProp.getInOffsetVideo()).build())
            body.add(
                mltGenerator.entry(
                    nodes = MltNodeBuilder()
                        .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
                        .filterQtblend(mltGenerator.nameFilterQtblend, "${mltProp.getStartTimecode("Song")}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.000000;${mltProp.getFadeInTimecode("Song")}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.000000;${mltProp.getFadeOutTimecode("Song")}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.000000;${mltProp.getEndTimecode("Song")}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.000000")
                        .build()
                )
            )
        }
        return result
    }


    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()

    override fun template(): MltNode {
        val templateFlashGroup = mutableListOf<MltNode>()

        val voiceLines = mltProp.getVoicelines(listOf(ProducerType.SONGTEXT, 0))

        val templateFlash = MltNode(
            type = ProducerType.HORIZON,
            name = "kdenlivetitle",
            fields = mutableMapOf(
                Pair("duration","0"),
                Pair("LC_NUMERIC","C"),
                Pair("width","${Karaoke.frameWidthPx}"),
                Pair("height","${Karaoke.frameHeightPx}"),
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
                                Pair("y","${mltProp.getPositionYPx(ProducerType.HORIZON)}")
                            ),
                            body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(Pair("zoom","100")), body = "1,0,0,0,1,0,0,0,1"))
                        ),
                        MltNode(
                            name = "content",
                            fields = mutableMapOf(
                                Pair("brushcolor", Karaoke.flashColor.mlt()),
                                Pair("pencolor", "0,0,0,255"),
                                Pair("penwidth","0"),
                                Pair("rect","0,0,${Karaoke.frameWidthPx},3")
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
                                Pair("y","${mltProp.getPositionYPx(ProducerType.HORIZON) - mltProp.getSymbolHeightPx(ProducerType.SONGTEXT) - 6}")
                            ),
                            body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(Pair("zoom","100")), body = "1,0,0,0,1,0,0,0,1"))
                        ),
                        MltNode(
                            name = "content",
                            fields = mutableMapOf(
                                Pair("brushcolor", Karaoke.flashColor.mlt()),
                                Pair("pencolor", "0,0,0,255"),
                                Pair("penwidth","0"),
                                Pair("rect","0,0,${Karaoke.frameWidthPx},3")
                            )
                        )
                    )
                ),

                templateFlashGroup,
                MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},${Karaoke.frameHeightPx}"))),
                MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},${Karaoke.frameHeightPx}"))),
                MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
            )
        )

        return templateFlash
    }
}

