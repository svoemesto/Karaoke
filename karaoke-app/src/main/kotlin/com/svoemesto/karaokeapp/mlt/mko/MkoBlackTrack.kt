package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.hexRGB
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.xmldata
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.*

data class MkoBlackTrack(val mltProp: MltProp,
                         val voiceId: Int = 0) : MltKaraokeObject {
    val type: ProducerType = ProducerType.BLACKTRACK
    val mltGenerator = MltGenerator(mltProp, type)
    override fun producer(): MltNode = mltGenerator
        .producer(
            id = MltGenerator.nameBlackTrack(type, voiceId),
            props = MltNodeBuilder()
                .propertyName("length", mltProp.getLengthFr("Song"))
                .propertyName("eof", "pause")
                .propertyName("resource", 0)
                .propertyName("aspect_ratio", 1)
                .propertyName("mlt_service", "color")
                .propertyName("kdenlive:duration", mltProp.getEndTimecode("Song"))
                .propertyName("mlt_image_format", "rgba")
                .propertyName("kdenlive:playlistid", "black_track")
                .propertyName("set.test_audio", 0)
                .build()
        )


}
