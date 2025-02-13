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

data class MkoVoices(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type)

    private val frameWidthPx = mltProp.getFrameWidthPx()
    private val frameHeightPx = mltProp.getFrameHeightPx()
    private val timelineStartTimecode = mltProp.getTimelineStartTimecode()
    private val timelineEndTimecode = mltProp.getTimelineEndTimecode()
    private val totalEndTimecode = mltProp.getTotalEndTimecode()
    private val inOffsetVideo = mltProp.getInOffsetVideo()
    private val mkoVoicesUUID = mltProp.getUUID(listOf(type))

    private val songStartTimecode = mltProp.getSongStartTimecode()
    private val songEndTimecode = mltProp.getSongEndTimecode()
    private val songFadeInTimecode = mltProp.getSongFadeInTimecode()
    private val songFadeOutTimecode = mltProp.getSongFadeOutTimecode()
    private val countVoices = mltProp.getCountVoices()
    private var mainBinUUID = mltProp.getUUID(listOf(ProducerType.MAINBIN))

    override fun producerBlackTrack(): MltNode = mltGenerator
        .producer(
            timecodeIn = timelineStartTimecode,
            timecodeOut = timelineEndTimecode,
            id = MltGenerator.nameProducerBlackTrack(type),
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
            body.addAll(MltNodeBuilder().blank(inOffsetVideo).build())
            body.add(
                mltGenerator.entry(
                    id = "{${mkoVoicesUUID}}",
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
        val tpStart = TransformProperty(
            time = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(songStartTimecode) + 1000),
            x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 0.0
        )
        val tpFadeIn = TransformProperty(
            time = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(songFadeInTimecode) + 1000),
            x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 1.0
        )
        val tpFadeOut = TransformProperty(
            time = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(songFadeOutTimecode) - 1000),
            x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 1.0
        )
        val tpEnd = TransformProperty(
            time = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(songEndTimecode) - 1000),
            x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 0.0
        )
        val resultListTp = listOf(tpStart, tpFadeIn, tpFadeOut, tpEnd)
        return resultListTp.joinToString(";")
    }

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()

    override fun tractorSequence(): MltNode = mltGenerator
        .tractor(
            id = "{${mkoVoicesUUID}}",
            timecodeIn = songStartTimecode,
            timecodeOut = songEndTimecode,
            body = MltNodeBuilder()
                .propertyName("kdenlive:sequenceproperties.hasAudio", 0)
                .propertyName("kdenlive:sequenceproperties.hasVideo", 1)
                .propertyName("kdenlive:clip_type", 2)
                .propertyName("kdenlive:duration", songEndTimecode)
                .propertyName("kdenlive:clipname", mltGenerator.name)
                .propertyName("kdenlive:description")
                .propertyName("kdenlive:uuid", "{${mkoVoicesUUID}}")
                .propertyName("kdenlive:producer_type", 17)
                .propertyName("kdenlive:folderid", -1)
                .propertyName("kdenlive:id", mltGenerator.id)
                .propertyName("kdenlive:sequenceproperties.activeTrack", 0)
                .propertyName("kdenlive:sequenceproperties.documentuuid", "{${mainBinUUID}}")
                .propertyName("kdenlive:sequenceproperties.tracks", countVoices)
                .propertyName("kdenlive:sequenceproperties.tracksCount", countVoices)
                .propertyName("kdenlive:sequenceproperties.verticalzoom", 1)
                .propertyName("kdenlive:sequenceproperties.zonein", 0)
                .propertyName("kdenlive:sequenceproperties.zoneout", 75)
                .propertyName("kdenlive:sequenceproperties.zoom", 8)
                .propertyName("kdenlive:sequenceproperties.groups", "[]")
                .propertyName("kdenlive:sequenceproperties.guides", "[]")
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameProducerBlackTrack(ProducerType.VOICES))))
                .nodes(
                    (0 until countVoices).map { MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.VOICE, it))) }
                )
                .transitionsAndFilters(mltGenerator.name, 0, countVoices)
                .build()
        )

}
