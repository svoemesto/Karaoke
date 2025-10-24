package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.convertMillisecondsToTimecode
import com.svoemesto.karaokeapp.convertTimecodeToMilliseconds
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.*

data class MkoChordBoard(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type, voiceId)

    private val songVersion = mltProp.getSongVersion()
    private val frameWidthPx = mltProp.getFrameWidthPx()
    private val frameHeightPx = mltProp.getFrameHeightPx()
    private val chordWidthPx = frameHeightPx / 4
    private val chordHeightPx = chordWidthPx
    private val chords = mltProp.getChords()
    private val timelineStartTimecode = mltProp.getTimelineStartTimecode()
    private val timelineEndTimecode = mltProp.getTimelineEndTimecode()
    private val totalEndTimecode = mltProp.getTotalEndTimecode()
    private val inOffsetVideo = mltProp.getInOffsetVideo()
    private val mkoChordBoardUUID = mltProp.getUUID(listOf(type, voiceId))

    private val startSilentOffsetMs = mltProp.getStartSilentOffsetMs()
    private val songStartTimecode = mltProp.getSongStartTimecode()
    private val songEndTimecode = mltProp.getSongEndTimecode()
//    private val songFadeInTimecode = mltProp.getSongFadeInTimecode()
//    private val songFadeOutTimecode = mltProp.getSongFadeOutTimecode()
    private var mainBinUUID = mltProp.getUUID(listOf(ProducerType.MAINBIN))

    private val firstChordStartMs = chords.first().syllableStartMs + startSilentOffsetMs
    private val lastChordEndMs = chords.last().syllableEndMs + startSilentOffsetMs
    private var folderIdVoice = mltProp.getId(listOf(ProducerType.VOICE, voiceId))

    private val firstChordStartTimecode = convertMillisecondsToTimecode(firstChordStartMs)
    private val lastChordEndTimecode = convertMillisecondsToTimecode(lastChordEndMs)


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
            @Suppress("UNCHECKED_CAST")
            val body = it as MutableList<MltNode>
            body.addAll(MltNodeBuilder().blank(inOffsetVideo).build())
            body.add(
                mltGenerator.entry(
                    id = "{${mkoChordBoardUUID}}",
                    nodes = MltNodeBuilder()
                        .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
//                        .filterQtblend("filter_qtblend_${listOf(ProducerType.CHORDSBOARD.name, voiceId).hashCode()}", mainFilePlaylistTransformProperties())
                        .build()
                )
            )
        }
        return result
    }

    override fun mainFilePlaylistTransformProperties(): String {
        println("chords.first() = ${chords.first()}")
        println("chords.last() = ${chords.last()}")
        println("firstChordStartMs = $firstChordStartMs")
        println("lastChordEndMs = $lastChordEndMs")
        println("firstChordStartTimecode = $firstChordStartTimecode")
        println("lastChordEndTimecode = $lastChordEndTimecode")
        val tpZero = TransformProperty(
            time = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(songStartTimecode)),
            x = 0, y = -chordHeightPx, w = frameWidthPx, h = frameHeightPx, opacity = 1.0
        )
        val tpStart = TransformProperty(
            time = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(firstChordStartTimecode) - 1000),
            x = 0, y = -chordHeightPx, w = frameWidthPx, h = frameHeightPx, opacity = 1.0
        )
        val tpFadeIn = TransformProperty(
            time = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(firstChordStartTimecode)),
            x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 1.0
        )
        val tpFadeOut = TransformProperty(
            time = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(lastChordEndTimecode)),
            x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 1.0
        )
        val tpEnd = TransformProperty(
            time = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(lastChordEndTimecode) + 1000),
            x = 0, y = -chordHeightPx, w = frameWidthPx, h = frameHeightPx, opacity = 1.0
        )
        val resultListTp = listOf(tpZero, tpStart, tpFadeIn, tpFadeOut, tpEnd)

        println("mainFilePlaylistTransformProperties = ${resultListTp.joinToString(";")}")

        return resultListTp.joinToString(";")
    }

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()

    override fun tractorSequence(): MltNode {

        val subTrackNodes = type.childs().asReversed().filter {it in songVersion.producers}. map {
            MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(it, voiceId)))
        }
//        val subTrackNodes = listOf(
//            MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.CHORDPICTURELINES, voiceId))),
//            MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.CHORDPICTUREFADER, voiceId)))
//        )

        return mltGenerator.tractor(
                id = "{${mkoChordBoardUUID}}",
                timecodeIn = songStartTimecode,
                timecodeOut = songEndTimecode,
                body = MltNodeBuilder()
                    .propertyName("kdenlive:sequenceproperties.hasAudio", 0)
                    .propertyName("kdenlive:sequenceproperties.hasVideo", 1)
                    .propertyName("kdenlive:clip_type", 2)
                    .propertyName("kdenlive:duration", songEndTimecode)
                    .propertyName("kdenlive:clipname", mltGenerator.name)
                    .propertyName("kdenlive:description")
                    .propertyName("kdenlive:uuid", "{${mkoChordBoardUUID}}")
                    .propertyName("kdenlive:producer_type", 17)
                    .propertyName("kdenlive:folderid", folderIdVoice)
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
                    .node(
                        MltNode(
                            name = "track",
                            fields = mutableMapOf("producer" to MltGenerator.nameProducerBlackTrack(ProducerType.CHORDSBOARD, voiceId))
                        )
                    )
                    .nodes(subTrackNodes)
                    .transitionsAndFilters(mltGenerator.name, 0, subTrackNodes.size)
                    .build()
            )
    }
}
