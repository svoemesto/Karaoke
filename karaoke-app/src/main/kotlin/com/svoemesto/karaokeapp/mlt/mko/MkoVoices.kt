package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.xmldata
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.*

data class MkoVoices(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type)

    override fun producerBlackTrack(): MltNode = mltGenerator
        .producer(
            timecodeIn = mltProp.getTimelineStartTimecode(),
            timecodeOut = mltProp.getTimelineEndTimecode(),
            id = MltGenerator.nameProducerBlackTrack(type, voiceId),
            props = MltNodeBuilder()
                .propertyName("length", 2147483647)
//                .propertyName("length", mltProp.getTimelineLengthFr())
                .propertyName("eof", "pause")
                .propertyName("resource", 0)
                .propertyName("aspect_ratio", 1)
                .propertyName("mlt_service", "color")
                .propertyName("kdenlive:duration", mltProp.getTotalEndTimecode())
                .propertyName("mlt_image_format", "rgba")
                .propertyName("kdenlive:playlistid", "black_track")
                .propertyName("set.test_audio", 0)
                .build()
        )

    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
            val body = it as MutableList<MltNode>
            body.addAll(MltNodeBuilder().blank(mltProp.getInOffsetVideo()).build())
            body.add(
                mltGenerator.entry(
                    id = "{${mltProp.getUUID(listOf(type, voiceId))}}",
                    nodes = MltNodeBuilder()
                        .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
                        .build()
                )
            )
        }
        return result
    }

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()

    override fun tractorSequence(): MltNode = mltGenerator
        .tractor(
            id = "{${mltProp.getUUID(listOf(type, voiceId))}}",
            timecodeIn = mltProp.getSongStartTimecode(),
            timecodeOut = mltProp.getSongEndTimecode(),
            body = MltNodeBuilder()
                .propertyName("kdenlive:sequenceproperties.hasAudio", 0)
                .propertyName("kdenlive:sequenceproperties.hasVideo", 1)
                .propertyName("kdenlive:clip_type", 2)
                .propertyName("kdenlive:duration", mltProp.getSongEndTimecode())
                .propertyName("kdenlive:clipname", mltGenerator.name)
                .propertyName("kdenlive:description")
                .propertyName("kdenlive:uuid", "{${mltProp.getUUID(listOf(type, voiceId))}}")
                .propertyName("kdenlive:producer_type", 17)
                .propertyName("kdenlive:folderid", -1)
                .propertyName("kdenlive:id", mltGenerator.id)
                .propertyName("kdenlive:sequenceproperties.activeTrack", 0)
                .propertyName("kdenlive:sequenceproperties.documentuuid", "{${mltProp.getUUID(listOf(ProducerType.MAINBIN, voiceId))}}")
                .propertyName("kdenlive:sequenceproperties.tracks", mltProp.getCountVoices())
                .propertyName("kdenlive:sequenceproperties.tracksCount", mltProp.getCountVoices())
                .propertyName("kdenlive:sequenceproperties.verticalzoom", 1)
                .propertyName("kdenlive:sequenceproperties.zonein", 0)
                .propertyName("kdenlive:sequenceproperties.zoneout", 75)
                .propertyName("kdenlive:sequenceproperties.zoom", 8)
                .propertyName("kdenlive:sequenceproperties.groups", "[]")
                .propertyName("kdenlive:sequenceproperties.guides", "[]")
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameProducerBlackTrack(ProducerType.VOICES, voiceId))))
                .nodes(
                    (0 until mltProp.getCountVoices()).map { MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.VOICE, it))) }
                )
                .transitionsAndFilters(mltGenerator.name, 0, mltProp.getCountVoices())
                .build()
        )

}
