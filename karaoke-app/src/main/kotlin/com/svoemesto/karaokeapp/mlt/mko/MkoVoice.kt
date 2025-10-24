package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType

data class MkoVoice(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type, voiceId)

    private var mkoVoiceUUID = mltProp.getUUID(listOf(type, voiceId))
    private var mainBinUUID = mltProp.getUUID(listOf(ProducerType.MAINBIN))
    private var folderIdVoice = mltProp.getId(listOf(ProducerType.VOICE, voiceId))
    private val songStartTimecode = mltProp.getSongStartTimecode()
    private val songEndTimecode = mltProp.getSongEndTimecode()
    private val totalEndTimecode = mltProp.getTotalEndTimecode()
    private val timelineStartTimecode = mltProp.getTimelineStartTimecode()
    private val timelineEndTimecode = mltProp.getTimelineEndTimecode()

    override fun producerBlackTrack(): MltNode = mltGenerator
        .producer(
            timecodeIn = timelineStartTimecode,
            timecodeOut = timelineEndTimecode,
            id = MltGenerator.nameProducerBlackTrack(type, voiceId),
            props = MltNodeBuilder()
                .propertyName("length", 2147483647)
                .propertyName("eof", "pause")
                .propertyName("resource", 0)
                .propertyName("aspect_ratio", 1)
                .propertyName("mlt_service", "color")
                .propertyName("kdenlive:duration", totalEndTimecode)
                .propertyName("mlt_image_format", "rgba")
                .propertyName("kdenlive:playlistid", "black_track")
                .propertyName("set.test_audio", 0)
                .build()
        )

    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
            @Suppress("UNCHECKED_CAST")
            val body = it as MutableList<MltNode>

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

    override fun mainFilePlaylistTransformProperties(): String = ""

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator
        .tractor(
            timecodeIn = songStartTimecode,
            timecodeOut = songEndTimecode
        )
    override fun tractorSequence(): MltNode {

        val tracks = listOf(
            MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.LINES, voiceId))),
            MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.COUNTERS, voiceId))),
            MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.CHORDSBOARD, voiceId))),
        )

        return mltGenerator
            .tractor(
                id = "{${mkoVoiceUUID}}",
                body = MltNodeBuilder()
                    .propertyName("kdenlive:sequenceproperties.hasAudio", 0)
                    .propertyName("kdenlive:sequenceproperties.hasVideo", 1)
                    .propertyName("kdenlive:clip_type", 2)
                    .propertyName("kdenlive:duration", songEndTimecode)
                    .propertyName("kdenlive:clipname", mltGenerator.name)
                    .propertyName("kdenlive:description")
                    .propertyName("kdenlive:uuid", "{${mkoVoiceUUID}}")
                    .propertyName("kdenlive:producer_type", 17)
                    .propertyName("kdenlive:folderid", folderIdVoice)
                    .propertyName("kdenlive:id", mltGenerator.id)
                    .propertyName("kdenlive:sequenceproperties.activeTrack", 0)
                    .propertyName(
                        "kdenlive:sequenceproperties.documentuuid",
                        "{${mainBinUUID}}"
                    )
                    .propertyName("kdenlive:sequenceproperties.tracks", tracks.size)
                    .propertyName("kdenlive:sequenceproperties.tracksCount", tracks.size)
                    .propertyName("kdenlive:sequenceproperties.verticalzoom", 1)
                    .propertyName("kdenlive:sequenceproperties.zonein", 0)
                    .propertyName("kdenlive:sequenceproperties.zoneout", 75)
                    .propertyName("kdenlive:sequenceproperties.zoom", 8)
                    .propertyName("kdenlive:sequenceproperties.groups", "[]")
                    .propertyName("kdenlive:sequenceproperties.guides", "[]")
                    .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameProducerBlackTrack(ProducerType.VOICE, voiceId))))
                    .nodes(tracks)
                    .transitionsAndFilters(mltGenerator.name, 0, tracks.size)
                    .build()
            )
    }

}
