package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.convertMillisecondsToFrames
import com.svoemesto.karaokeapp.convertMillisecondsToTimecode
import com.svoemesto.karaokeapp.convertTimecodeToMilliseconds
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.*

data class MkoChordPictureElement(
    val mltProp: MltProp,
    val type: ProducerType = ProducerType.CHORDPICTUREELEMENT,
    val voiceId: Int = 0,
    val lineId: Int = 0,
    val elementId: Int = 0
): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type, voiceId, lineId)

    private val frameWidthPx = mltProp.getFrameWidthPx()
    private val frameHeightPx = mltProp.getFrameHeightPx()
    private val chordWidthPx = frameHeightPx / 4
    private val chordHeightPx = chordWidthPx

    private val songVersion = mltProp.getSongVersion()
//    private val settings = mltProp.getSettings()
    private val songStartTimecode  = mltProp.getSongStartTimecode()
    private val songEndTimecode  = mltProp.getSongEndTimecode()
    private var chordDurationOnScreen = mltProp.getDurationOnScreen(listOf(ProducerType.CHORDPICTURELINE, voiceId, lineId))
    private var mkoElementUUID = mltProp.getUUID(listOf(type, voiceId, lineId))
    private var mainBinUUID = mltProp.getUUID(listOf(ProducerType.MAINBIN))
    private var folderIdChordPictures = mltProp.getId(listOf(ProducerType.CHORDPICTUREELEMENT, voiceId))
    private val chordEndTimecode = if (chordDurationOnScreen > 0) {
        convertMillisecondsToTimecode(chordDurationOnScreen)
    } else {
        chordDurationOnScreen = convertTimecodeToMilliseconds(songEndTimecode)
        songEndTimecode
    }
    override fun producerBlackTrack(): MltNode {

        val widthAreaPx= chordWidthPx
        val heightAreaPx= chordHeightPx

        return mltGenerator
            .producer(
                timecodeOut = chordEndTimecode,
                id = MltGenerator.nameProducerBlackTrack(type, voiceId, lineId),
                props = MltNodeBuilder()
                    .propertyName("length", convertMillisecondsToFrames(chordDurationOnScreen))
                    .propertyName("eof", "pause")
                    .propertyName("resource", 0)
                    .propertyName("aspect_ratio", 1)
                    .propertyName("mlt_service", "color")
                    .propertyName("kdenlive:duration", chordEndTimecode)
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
            @Suppress("UNCHECKED_CAST")
            val body = it as MutableList<MltNode>
            body.add(
                mltGenerator.entry(
                    id = "{${mkoElementUUID}}",
                    timecodeOut = chordEndTimecode,
                    nodes = MltNodeBuilder()
                        .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
                        .filterQtblend("filter_qtblend_${listOf(ProducerType.CHORDPICTUREELEMENT.name, voiceId, lineId).hashCode()}", mainFilePlaylistTransformProperties())
                        .build()
                )
            )
        }
        return result
    }
    override fun mainFilePlaylistTransformProperties(): String {
        val tpStart = TransformProperty(
            time = songStartTimecode,
            x = 0,
            y = -(frameHeightPx / 4 + frameHeightPx / 8) + 75,
            w = frameWidthPx,
            h = frameHeightPx,
            opacity = 1.0)
        val resultListTp = listOf(tpStart)
        return resultListTp.joinToString(";")
    }
    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()
    override fun tractor(): MltNode = mltGenerator.tractor(timecodeOut = chordEndTimecode)

    override fun tractorSequence(): MltNode {

        val widthAreaPx= chordWidthPx
        val heightAreaPx= chordHeightPx

        val subTrackNodes = ProducerType.CHORDPICTUREELEMENT.childs().asReversed().filter {it in songVersion.producers}. map {
            MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(it, voiceId, lineId)))
        }

        return mltGenerator.tractor(
            id = "{${mkoElementUUID}}",
            timecodeIn = songStartTimecode,
            timecodeOut = chordEndTimecode,
            body = MltNodeBuilder()
                .propertyName("kdenlive:sequenceproperties.hasAudio", 0)
                .propertyName("kdenlive:sequenceproperties.hasVideo", 1)
                .propertyName("kdenlive:clip_type", 2)
                .propertyName("kdenlive:duration", chordEndTimecode)
                .propertyName("kdenlive:clipname", mltGenerator.name)
                .propertyName("kdenlive:folderid", folderIdChordPictures)
                .propertyName("kdenlive:description")
                .propertyName("kdenlive:uuid", "{${mkoElementUUID}}")
                .propertyName("kdenlive:producer_type", 17)
                .propertyName("kdenlive:id", mltGenerator.id)
                .propertyName("kdenlive:sequenceproperties.activeTrack", 0)
                .propertyName("kdenlive:sequenceproperties.documentuuid", "{${mainBinUUID}}")
                .propertyName("kdenlive:sequenceproperties.tracks", subTrackNodes.size)
                .propertyName("kdenlive:sequenceproperties.tracksCount", subTrackNodes.size)
                .propertyName("kdenlive:sequenceproperties.verticalzoom", 1)
                .propertyName("kdenlive:sequenceproperties.zonein", 0)
                .propertyName("kdenlive:sequenceproperties.zoneout", 75)
                .propertyName("kdenlive:sequenceproperties.zoom", 8)
                .propertyName("kdenlive:sequenceproperties.groups", "[]")
                .propertyName("kdenlive:sequenceproperties.guides", "[]")

                .propertyName("meta.media.width", widthAreaPx)
                .propertyName("meta.media.height", heightAreaPx)

                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameProducerBlackTrack(ProducerType.CHORDPICTUREELEMENT, voiceId, lineId))))
                .nodes(subTrackNodes)
                .transitionsAndFilters(mltGenerator.name, 0, subTrackNodes.size)
                .build()
        )
    }
}
