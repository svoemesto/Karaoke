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

data class MkoFillcolorSongtexts(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type, voiceId)

    private val timelineStartTimecode = mltProp.getTimelineStartTimecode()
    private val timelineEndTimecode = mltProp.getTimelineEndTimecode()
    private val totalEndTimecode = mltProp.getTotalEndTimecode()
    private val songStartTimecode = mltProp.getSongStartTimecode()
    private val songEndTimecode = mltProp.getSongEndTimecode()
    private val mkoFillcolorSongtextsUUID = mltProp.getUUID(listOf(type, voiceId))
    private val mkoFillcolorSongtextsFolderId = mltProp.getId(listOf(ProducerType.FILLCOLORSONGTEXTS, voiceId))
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
            val body = it as MutableList<MltNode>
            body.add(
                mltGenerator.entry(
                    id = "{${mkoFillcolorSongtextsUUID}}",
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
    override fun tractor(): MltNode = mltGenerator.tractor()

    override fun tractorSequence(): MltNode = mltGenerator
        .tractor(
            id = "{${mkoFillcolorSongtextsUUID}}",
            timecodeIn = songStartTimecode,
            timecodeOut = songEndTimecode,
            body = MltNodeBuilder()
                .propertyName("kdenlive:sequenceproperties.hasAudio", 0)
                .propertyName("kdenlive:sequenceproperties.hasVideo", 1)
                .propertyName("kdenlive:clip_type", 2)
                .propertyName("kdenlive:duration", songEndTimecode)
                .propertyName("kdenlive:clipname", mltGenerator.name)
                .propertyName("kdenlive:folderid", mkoFillcolorSongtextsFolderId)
                .propertyName("kdenlive:description")
                .propertyName("kdenlive:uuid", "{${mkoFillcolorSongtextsUUID}}")
                .propertyName("kdenlive:producer_type", 17)
                .propertyName("kdenlive:id", mltGenerator.id)
                .propertyName("kdenlive:sequenceproperties.activeTrack", 0)
                .propertyName("kdenlive:sequenceproperties.documentuuid", "{${mainBinUUID}}")
                .propertyName("kdenlive:sequenceproperties.tracks", ProducerType.FILLCOLORSONGTEXT.ids.size)
                .propertyName("kdenlive:sequenceproperties.tracksCount", ProducerType.FILLCOLORSONGTEXT.ids.size)
                .propertyName("kdenlive:sequenceproperties.verticalzoom", 1)
                .propertyName("kdenlive:sequenceproperties.zonein", 0)
                .propertyName("kdenlive:sequenceproperties.zoneout", 75)
                .propertyName("kdenlive:sequenceproperties.zoom", 8)
                .propertyName("kdenlive:sequenceproperties.groups", "[]")
                .propertyName("kdenlive:sequenceproperties.guides", "[]")
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameProducerBlackTrack(ProducerType.FILLCOLORSONGTEXTS, voiceId))))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.FILLCOLORSONGTEXT, voiceId, 0))))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.FILLCOLORSONGTEXT, voiceId, 1))))
                .transitionsAndFilters(mltGenerator.name, 0, ProducerType.FILLCOLORSONGTEXT.ids.size)
                .build()
        )


}
