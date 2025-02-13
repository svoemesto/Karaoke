package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.KaraokeVoice
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.xmldata
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.model.TransformProperty

data class MkoCounter(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {

    val mltGenerator = MltGenerator(mltProp, type, voiceId, childId)

    private val frameWidthPx = mltProp.getFrameWidthPx()
    private val frameHeightPx = mltProp.getFrameHeightPx()
    private val songLengthFr = mltProp.getSongLengthFr()
    private val songEndTimecode = mltProp.getSongEndTimecode()
    private val mkoCounterId = mltProp.getId(listOf(ProducerType.COUNTERS, voiceId))
    private val mkoCounterFilePlaylistTransformProperties = mltProp.getRect(listOf(type, voiceId, childId))
    private val mkoCounterPositionXPx = mltProp.getPositionXPx(listOf(ProducerType.COUNTER, voiceId))
    private val mkoCounterPositionYPx = mltProp.getPositionYPx(listOf(ProducerType.COUNTER))
    private val fontSize = mltProp.getFontSize()

    override fun producer(): MltNode = mltGenerator
        .producer(
            props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                .propertyName("kdenlive:folderid", mkoCounterId)
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
        return mkoCounterFilePlaylistTransformProperties
    }
    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()

    override fun template(): MltNode {

        val mltText: MltText = Karaoke.voices[0].groups[0].mltText.copy(childId.toString(), fontSize)
        mltText.shapeColor =  Karaoke.countersColors[childId]

        return MltNode(
            name = "kdenlivetitle",
            fields = mutableMapOf(
                Pair("duration","0"),
                Pair("LC_NUMERIC","C"),
                Pair("width","$frameWidthPx"),
                Pair("height","$frameHeightPx"),
                Pair("out","0"),
            ), body = mutableListOf(
                MltNode(
                    name = "item",
                    fields = mutableMapOf(
                        Pair("type","QGraphicsTextItem"),
                        Pair("z-index","0"),
                    ), body = mutableListOf(
                        MltNode(
                            name = "position",
                            fields = mutableMapOf(Pair("x","$mkoCounterPositionXPx"),Pair("y","$mkoCounterPositionYPx")),
                            body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                        mltText.mltNode(childId.toString())
                    )
                ),
                MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${frameWidthPx},${frameHeightPx}"))),
                MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${frameWidthPx},${frameHeightPx}"))),
                MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
            )
        )
    }
}
