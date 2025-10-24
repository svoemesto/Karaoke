package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType

data class MkoLines(
    val mltProp: MltProp,
    val type: ProducerType = ProducerType.LINES,
    val voiceId: Int = 0,
    val lineId: Int = 0,
    val elementId: Int = 0
): MltKaraokeObject {

    val mltGenerator = MltGenerator(mltProp, type, voiceId)

    private val timelineStartTimecode = mltProp.getTimelineStartTimecode()
    private val timelineEndTimecode = mltProp.getTimelineEndTimecode()
    private val totalEndTimecode = mltProp.getTotalEndTimecode()
    private val settings = mltProp.getSettings()
    private val songStartTimecode  = mltProp.getSongStartTimecode()
    private val songEndTimecode  = mltProp.getSongEndTimecode()
    private var mkoLinesUUID = mltProp.getUUID(listOf(type, voiceId))
    private var folderIdVoice = mltProp.getId(listOf(ProducerType.VOICE, voiceId))
    private var mainBinUUID = mltProp.getUUID(listOf(ProducerType.MAINBIN))

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
                    id = "{${mkoLinesUUID}}",
                    nodes = MltNodeBuilder()
                        .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
                        .build()
                )
            )
        }
        return result
    }
    override fun mainFilePlaylistTransformProperties(): String {
        val voice = settings?.voicesForMlt?.get(voiceId)
        return voice?.linesTransformProperties()?.joinToString(";")?:""
    }
    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()

    override fun tractorSequence(): MltNode {
        val sett = settings ?: return MltNode()
        val voice = sett.voicesForMlt[voiceId]
        val countLineTracks = voice.countLineTracks

        val subTrackNodes = (0 until countLineTracks).map { trackI ->
            MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.LINETRACK, voiceId, trackI)))
        }

        return mltGenerator
            .tractor(
                id = "{${mkoLinesUUID}}",
                timecodeIn = songStartTimecode,
                timecodeOut = songEndTimecode,
                body = MltNodeBuilder()
                    .propertyName("kdenlive:sequenceproperties.hasAudio", 0)
                    .propertyName("kdenlive:sequenceproperties.hasVideo", 1)
                    .propertyName("kdenlive:clip_type", 2)
                    .propertyName("kdenlive:duration", songEndTimecode)
                    .propertyName("kdenlive:clipname", mltGenerator.name)
                    .propertyName("kdenlive:description")
                    .propertyName("kdenlive:uuid", "{${mkoLinesUUID}}")
                    .propertyName("kdenlive:producer_type", 17)
                    .propertyName("kdenlive:folderid", folderIdVoice)
                    .propertyName("kdenlive:id", mltGenerator.id)
                    .propertyName("kdenlive:sequenceproperties.activeTrack", 0)
                    .propertyName(
                        "kdenlive:sequenceproperties.documentuuid",
                        "{${mainBinUUID}}"
                    )
                    .propertyName(
                        "kdenlive:sequenceproperties.tracks",
                        subTrackNodes.size
                    )
                    .propertyName(
                        "kdenlive:sequenceproperties.tracksCount",
                        subTrackNodes.size
                    )
                    .propertyName("kdenlive:sequenceproperties.verticalzoom", 1)
                    .propertyName("kdenlive:sequenceproperties.zonein", 0)
                    .propertyName("kdenlive:sequenceproperties.zoneout", 75)
                    .propertyName("kdenlive:sequenceproperties.zoom", 8)
                    .propertyName("kdenlive:sequenceproperties.groups", "[]")
                    .propertyName("kdenlive:sequenceproperties.guides", "[]")
                    .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameProducerBlackTrack(ProducerType.LINES, voiceId))))
                    .nodes(subTrackNodes)
                    .transitionsAndFilters(mltGenerator.name, 0, subTrackNodes.size)
                    .build()
            )
    }
    override fun template(): MltNode = MltNode()

}
