package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.xmldata

data class MkoBoosty(
    val mltProp: MltProp) : MltKaraokeObject {
    val type: ProducerType = ProducerType.BOOSTY
    val voiceId: Int = 0
    val mltGenerator = MltGenerator(mltProp, type)

    override fun producer(): MltNode = mltGenerator
        .producer(
            timecodeIn =  mltProp.getEndTimecode(ProducerType.SPLASHSTART),
            timecodeOut = mltProp.getEndTimecode("Song"),
            props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                .propertyName("length", Karaoke.timeBoostyStartMs.toString())
                .propertyName("kdenlive:duration", mltProp.getEndTimecode(ProducerType.BOOSTY))
                .propertyName("xmldata", mltProp.getXmlData(listOf(type, voiceId)).toString().xmldata())
                .propertyName("meta.media.width", Karaoke.frameWidthPx)
                .propertyName("meta.media.height", Karaoke.frameHeightPx)
                .build()
        )

    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
            val body = it as MutableList<MltNode>
            body.addAll(MltNodeBuilder().blank(mltProp.getEndTimecode(ProducerType.SPLASHSTART)).build())
            body.add(
                mltGenerator.entry(
                    timecodeIn = mltProp.getEndTimecode(ProducerType.SPLASHSTART),
                    timecodeOut = mltProp.getEndTimecode(ProducerType.BOOSTY),
                    nodes = MltNodeBuilder()
                        .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
                        .filterQtblend(mltGenerator.nameFilterQtblend, "${mltProp.getEndTimecode(ProducerType.SPLASHSTART)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.000000;${mltProp.getFadeInTimecode(ProducerType.BOOSTY)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.000000;${mltProp.getFadeOutTimecode(ProducerType.BOOSTY)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.000000;${mltProp.getEndTimecode(ProducerType.BOOSTY)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.000000")
                        .build()
                )
            )
        }
        return result
    }

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator
        .tractor(
            timecodeIn = mltProp.getEndTimecode(ProducerType.SPLASHSTART),
            timecodeOut = mltProp.getEndTimecode(ProducerType.BOOSTY)
        )

    override fun template(): MltNode = MltNode(
        name = "kdenlivetitle",
        fields = PropertiesMltNodeBuilder()
            .duration("0")
            .LC_NUMERIC("C")
            .width("${Karaoke.frameWidthPx}")
            .height("${Karaoke.frameHeightPx}")
            .`out`("0")
            .build(),
        body = MltNodeBuilder()
            .item(
                fields = PropertiesMltNodeBuilder()
                    .type("QGraphicsPixmapItem")
                    .`z-index`("6")
                    .build(),
                body = MltNodeBuilder()
                    .position(
                        fields = PropertiesMltNodeBuilder()
                            .x("0")
                            .y("0")
                            .build(),
                        body = MltNodeBuilder()
                            .transform(body = "1,0,0,0,1,0,0,0,1")
                            .build()
                    )
                    .content(
                        fields = PropertiesMltNodeBuilder()
                            .base64(mltProp.getBase64(ProducerType.BOOSTY))
                            .build()
                    )
                    .build()
            )
            .build()
    )

}
