package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.convertMillisecondsToTimecode
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType

data class MkoLineTrack(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val trackId: Int = 0, val elementId: Int = 0): MltKaraokeObject {

    val mltGenerator = MltGenerator(mltProp, type, voiceId, trackId)

    private val settings = mltProp.getSettings()
    private val songStartTimecode = mltProp.getSongStartTimecode()
    private val songEndTimecode = mltProp.getSongEndTimecode()
//    mltProp.getDurationOnScreen(listOf(ProducerType.LINE, voiceId, line.lineId))
//    mltProp.getUUID(listOf(ProducerType.LINE, voiceId, line.lineId))
    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
            @Suppress("UNCHECKED_CAST")
            val body = it as MutableList<MltNode>

            settings?.let { settings ->
                val listOfVoices = settings.voicesForMlt
                val voice = listOfVoices[voiceId]

                val linesInTrack = voice.linesForMlt().filter { line -> line.trackId == trackId && !line.isEmptyLine }
//                var lineStartMsPrev = 0L
                var lineEndMsPrev = 0L
//                var lineDurationMsPrev = 0L

                linesInTrack.forEach { line ->

                    val scrollLineStartMs = line.startVisibleTime
                    val scrollLineEndMs = line.endVisibleTime

                    val scrollLineDurationMs = mltProp.getDurationOnScreen(listOf(ProducerType.LINE, voiceId, line.lineId))
                    val lineEndTimecode = if (scrollLineDurationMs > 0) {
                        convertMillisecondsToTimecode(scrollLineDurationMs)
                    } else {
                        songEndTimecode
                    }

                    val blankDurationMs = scrollLineStartMs - lineEndMsPrev

                    if (blankDurationMs > 0) {
                        body.addAll(
                            MltNodeBuilder()
                                .blank(convertMillisecondsToTimecode(blankDurationMs))
                                .build()
                        )
                    }

                    val nodes = if (line.transformProperties.isNotEmpty()) {
                        MltNodeBuilder()
                            .propertyName("kdenlive:id", "filePlaylist${listOf(ProducerType.LINE.name, voiceId, line.lineId, 0).hashCode()}")
                            .filterQtblend("filter_qtblend_${listOf(ProducerType.LINE.name, voiceId, line.lineId, 0).hashCode()}", line.transformProperties.joinToString(";"))
                            .build()
                    } else {
                        MltNodeBuilder()
                            .propertyName("kdenlive:id", "filePlaylist${listOf(ProducerType.LINE.name, voiceId, line.lineId, 0).hashCode()}")
                            .build()
                    }

                    body.add(
                        mltGenerator.entry(
                            id = "{${mltProp.getUUID(listOf(ProducerType.LINE, voiceId, line.lineId))}}",
                            timecodeIn = songStartTimecode,
                            timecodeOut = lineEndTimecode,
                            nodes = nodes
                        )
                    )

//                    lineStartMsPrev = scrollLineStartMs
                    lineEndMsPrev = scrollLineEndMs
//                    lineDurationMsPrev = scrollLineDurationMs

                }

            }

        }
        return result
    }

    override fun mainFilePlaylistTransformProperties(): String = ""

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()

}
