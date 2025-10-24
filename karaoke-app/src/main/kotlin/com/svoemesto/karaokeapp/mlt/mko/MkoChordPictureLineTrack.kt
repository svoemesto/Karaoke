package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType

data class MkoChordPictureLineTrack(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val trackId: Int = 0, val elementId: Int = 0): MltKaraokeObject {

    val mltGenerator = MltGenerator(mltProp, type, voiceId, trackId)

    private val songStartTimecode = mltProp.getSongStartTimecode()
    private val songEndTimecode = mltProp.getSongEndTimecode()
    private val chords = mltProp.getChords()
    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
            @Suppress("UNCHECKED_CAST")
            val body = it as MutableList<MltNode>

            val chordsInTrack = chords.filter { chord -> chord.chordPictureTrackId == trackId }
//            var chordPictureStartMsPrev = 0L
            var chordPictureEndMsPrev = 0L
//            var chordPictureDurationMsPrev = 0L

            chordsInTrack.forEach { chord ->

                val scrollLineStartMs = chord.startChordVisibleTime
                val scrollLineEndMs = chord.endChordVisibleTime

                val scrollChordLineDurationMs = mltProp.getDurationOnScreen(listOf(ProducerType.CHORDPICTURELINE, voiceId, chord.chordId))
                val chordEndTimecode = if (scrollChordLineDurationMs > 0) {
                    convertMillisecondsToTimecode(scrollChordLineDurationMs)
                } else {
                    songEndTimecode
                }

                val blankDurationMs = scrollLineStartMs - chordPictureEndMsPrev

                if (blankDurationMs > 0) {
                    body.addAll(
                        MltNodeBuilder()
                            .blank(convertMillisecondsToTimecode(blankDurationMs))
                            .build()
                    )
                }

                val nodes = if (chord.transformChordProperties.isNotEmpty()) {
                    MltNodeBuilder()
                        .propertyName("kdenlive:id", "filePlaylist${listOf(ProducerType.CHORDPICTURELINE.name, voiceId, chord.chordId).hashCode()}")
                        .filterQtblend("filter_qtblend_${listOf(ProducerType.CHORDPICTURELINE.name, voiceId, chord.chordId).hashCode()}", chord.transformChordProperties.joinToString(";"))
                        .build()
                } else {
                    MltNodeBuilder()
                        .propertyName("kdenlive:id", "filePlaylist${listOf(ProducerType.CHORDPICTURELINE.name, voiceId, chord.chordId).hashCode()}")
                        .build()
                }

                body.add(
                    mltGenerator.entry(
                        id = "{${mltProp.getUUID(listOf(ProducerType.CHORDPICTURELINE, voiceId, chord.chordId))}}",
                        timecodeIn = songStartTimecode,
                        timecodeOut = chordEndTimecode,
                        nodes = nodes
                    )
                )

//                chordPictureStartMsPrev = scrollLineStartMs
                chordPictureEndMsPrev = scrollLineEndMs
//                chordPictureDurationMsPrev = scrollChordLineDurationMs

            }



        }
        return result
    }

    override fun mainFilePlaylistTransformProperties(): String = ""

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()

}
