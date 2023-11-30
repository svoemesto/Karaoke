package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.hexRGB
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.xmldata

data class MkoFillcolorSongtext(
    val mltProp: MltProp,
                                val isEven: Boolean,
                                val voiceId: Int = 0) : MltKaraokeObject {
    val type: ProducerType = ProducerType.FILLCOLORSONGTEXT
    val childId: Int = if (isEven) 0 else 1
    val mltGenerator = MltGenerator(mltProp, type, voiceId, childId)

    override fun producer(): MltNode = mltGenerator
        .producer(
            props = MltNodeBuilder()
                .propertyName("length", mltProp.getLengthFr("Song"))
                .propertyName("eof", "pause")
                .propertyName("resource", Karaoke.voices[0].fill.evenColor.hexRGB())
                .propertyName("aspect_ratio", 1)
                .propertyName("mlt_service", "color")
                .propertyName("kdenlive:duration", mltProp.getEndTimecode("Song"))
                .propertyName("mlt_image_format", "rgb")
                .propertyName("kdenlive:clipname", mltGenerator.name)
                .propertyName("kdenlive:folderid", -1)
                .propertyName("kdenlive:clip_type", if (type.isAudio) 1 else 2)
                .propertyName("kdenlive:id", mltGenerator.id)
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
                        .filterQtblend(mltGenerator.nameFilterQtblend, mltProp.getRect(listOf(type, voiceId, childId)), distort = 1)
                        .build()
                )
            )
        }
        return result
    }


    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()


    override fun tractor(): MltNode = mltGenerator.tractor()

}
