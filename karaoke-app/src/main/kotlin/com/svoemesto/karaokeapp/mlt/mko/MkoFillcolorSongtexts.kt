package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.convertMillisecondsToTimecode
import com.svoemesto.karaokeapp.convertTimecodeToMilliseconds
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.xmldata
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.*

data class MkoFillcolorSongtexts(val mltProp: MltProp,
                                 val voiceId: Int = 0) : MltKaraokeObject {
    val type: ProducerType = ProducerType.FILLCOLORSONGTEXTS
    val mltGenerator = MltGenerator(mltProp, type)

    override fun tractor(): MltNode = mltGenerator.tractor()

    override fun tractorSequence(): MltNode = mltGenerator
        .tractor(
            id = "{${mltProp.getUUID(listOf(type, voiceId))}}",
            body = MltNodeBuilder()
                .propertyName("kdenlive:sequenceproperties.hasAudio", 0)
                .propertyName("kdenlive:sequenceproperties.hasVideo", 1)
                .propertyName("kdenlive:clip_type", 2)
                .propertyName("kdenlive:duration", mltProp.getEndTimecode("Song"))
                .propertyName("kdenlive:clipname", mltGenerator.name)
                .propertyName("kdenlive:description")
                .propertyName("kdenlive:uuid", "{${mltProp.getUUID(listOf(type, voiceId))}}")
                .propertyName("kdenlive:producer_type", 17)
                .propertyName("kdenlive:folderid", -1)
                .propertyName("kdenlive:id", mltGenerator.id)
                .propertyName("kdenlive:sequenceproperties.activeTrack", 0)
                .propertyName("kdenlive:sequenceproperties.documentuuid", "{${mltProp.getUUID(listOf(ProducerType.MAINBIN, voiceId))}}")
                .propertyName("kdenlive:sequenceproperties.tracks", ProducerType.FILLCOLORSONGTEXT.ids.size)
                .propertyName("kdenlive:sequenceproperties.tracksCount", ProducerType.FILLCOLORSONGTEXT.ids.size)
                .propertyName("kdenlive:sequenceproperties.verticalzoom", 1)
                .propertyName("kdenlive:sequenceproperties.zonein", 0)
                .propertyName("kdenlive:sequenceproperties.zoneout", 75)
                .propertyName("kdenlive:sequenceproperties.zoom", 8)
                .propertyName("kdenlive:sequenceproperties.groups", "[]")
                .propertyName("kdenlive:sequenceproperties.guides", "[]")
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameBlackTrack(ProducerType.FILLCOLORSONGTEXT, voiceId))))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.name(ProducerType.FILLCOLORSONGTEXT, voiceId, 0))))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.name(ProducerType.FILLCOLORSONGTEXT, voiceId, 1))))
                .transitionsAndFilters(mltGenerator.name, ProducerType.FILLCOLORSONGTEXT.ids.size)
                .build()
        )


}
