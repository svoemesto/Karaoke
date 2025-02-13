package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.convertMillisecondsToFrames
import com.svoemesto.karaokeapp.convertMillisecondsToTimecode
import com.svoemesto.karaokeapp.convertTimecodeToMilliseconds
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType

data class MkoElement(
    val mltProp: MltProp,
    val type: ProducerType = ProducerType.ELEMENT,
    val voiceId: Int = 0,
    val lineId: Int = 0,
    val elementId: Int = 0
): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type, voiceId, lineId, elementId)

    private val songVersion = mltProp.getSongVersion()
    private val frameWidthPx = mltProp.getFrameWidthPx()
    private val frameHeightPx = mltProp.getFrameHeightPx()
    private val settings = mltProp.getSettings()
    private val songStartTimecode  = mltProp.getSongStartTimecode()
    private val songEndTimecode  = mltProp.getSongEndTimecode()
    private var lineDurationOnScreen = mltProp.getDurationOnScreen(listOf(ProducerType.LINE, voiceId, lineId))
    private var mkoElementUUID = mltProp.getUUID(listOf(type, voiceId, lineId, elementId))
    private var mainBinUUID = mltProp.getUUID(listOf(ProducerType.MAINBIN))
    private var folderIdLines = mltProp.getId(listOf(ProducerType.LINES, voiceId))
    private val lineEndTimecode = if (lineDurationOnScreen > 0) {
        convertMillisecondsToTimecode(lineDurationOnScreen)
    } else {
        lineDurationOnScreen = convertTimecodeToMilliseconds(songEndTimecode)
        songEndTimecode
    }
    override fun producerBlackTrack(): MltNode {

        var widthAreaPx= frameWidthPx
        var heightAreaPx= frameHeightPx

        val sett = settings
        if (sett != null) {
            try {
                val element = sett.voicesForMlt[voiceId].getLines()[lineId].getElements(songVersion)[elementId]
                widthAreaPx = element.w()
                heightAreaPx = element.h()
            } catch (_: Exception) {
            }
        }

        return mltGenerator
            .producer(
//                timecodeIn = mltProp.getTimelineStartTimecode(),
                timecodeOut = lineEndTimecode,
                id = MltGenerator.nameProducerBlackTrack(type, voiceId, lineId, elementId),
                props = MltNodeBuilder()
                    .propertyName("length", convertMillisecondsToFrames(lineDurationOnScreen))
                    .propertyName("eof", "pause")
                    .propertyName("resource", 0)
                    .propertyName("aspect_ratio", 1)
                    .propertyName("mlt_service", "color")
                    .propertyName("kdenlive:duration", lineEndTimecode)
                    .propertyName("mlt_image_format", "rgba")
                    .propertyName("kdenlive:playlistid", "black_track")
                    .propertyName("set.test_audio", 0)

                    .propertyName("meta.media.width", widthAreaPx)
                    .propertyName("meta.media.height", heightAreaPx)

                    .build()
            )
    }

    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
            val body = it as MutableList<MltNode>
            body.add(
                mltGenerator.entry(
                    id = "{${mkoElementUUID}}",
                    timecodeOut = lineEndTimecode,
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
    override fun tractor(): MltNode = mltGenerator.tractor(timecodeOut = lineEndTimecode)

    override fun tractorSequence(): MltNode {

        var widthAreaPx= frameWidthPx
        var heightAreaPx= frameHeightPx

        val sett = settings
        if (sett != null) {
            try {
                val element = sett.voicesForMlt[voiceId].getLines()[lineId].getElements(songVersion)[elementId]
                widthAreaPx = element.w()
                heightAreaPx = element.h()
            } catch (_: Exception) {
            }
        }

        return mltGenerator.tractor(
            id = "{${mkoElementUUID}}",
            timecodeIn = songStartTimecode,
            timecodeOut = lineEndTimecode,
            body = MltNodeBuilder()
                .propertyName("kdenlive:sequenceproperties.hasAudio", 0)
                .propertyName("kdenlive:sequenceproperties.hasVideo", 1)
                .propertyName("kdenlive:clip_type", 2)
                .propertyName("kdenlive:duration", lineEndTimecode)
                .propertyName("kdenlive:clipname", mltGenerator.name)
                .propertyName("kdenlive:folderid", folderIdLines)
                .propertyName("kdenlive:description")
                .propertyName("kdenlive:uuid", "{${mkoElementUUID}}")
                .propertyName("kdenlive:producer_type", 17)
                .propertyName("kdenlive:id", mltGenerator.id)
                .propertyName("kdenlive:sequenceproperties.activeTrack", 0)
                .propertyName("kdenlive:sequenceproperties.documentuuid", "{${mainBinUUID}}")
                .propertyName("kdenlive:sequenceproperties.tracks", 2)
                .propertyName("kdenlive:sequenceproperties.tracksCount", 2)
                .propertyName("kdenlive:sequenceproperties.verticalzoom", 1)
                .propertyName("kdenlive:sequenceproperties.zonein", 0)
                .propertyName("kdenlive:sequenceproperties.zoneout", 75)
                .propertyName("kdenlive:sequenceproperties.zoom", 8)
                .propertyName("kdenlive:sequenceproperties.groups", "[]")
                .propertyName("kdenlive:sequenceproperties.guides", "[]")

                .propertyName("meta.media.width", widthAreaPx)
                .propertyName("meta.media.height", heightAreaPx)

                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameProducerBlackTrack(ProducerType.ELEMENT, voiceId, lineId, elementId))))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.FILL, voiceId, lineId, elementId))))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.STRING, voiceId, lineId, elementId))))
                .transitionsAndFilters(mltGenerator.name, 0, 2)
                .build()
        )
    }
}
