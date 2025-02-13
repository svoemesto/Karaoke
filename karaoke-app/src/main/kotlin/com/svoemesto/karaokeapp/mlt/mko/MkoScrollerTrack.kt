package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.KaraokeVoice
import com.svoemesto.karaokeapp.convertMillisecondsToTimecode
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.xmldata
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType

data class MkoScrollerTrack(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {

    val mltGenerator = MltGenerator(mltProp, type, voiceId, childId)


    // тут продюссеры скролов
    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
            val body = it as MutableList<MltNode>

            val timeToScrollScreenMs = mltProp.getTimeToScrollScreenMs()

            val scrollTrack = mltProp.getScrollTrack(listOf(voiceId, childId))

            var scrollLineStartMsPrev = 0L
            var scrollLineEndMsPrev = 0L
            var scrollLineDurationMsPrev = 0L

            scrollTrack.forEachIndexed { indexLine, sl ->

                val (scrollLine, indexLine) = sl

                val scrollLineStartMs = scrollLine.subtitles.first().startMs - timeToScrollScreenMs
                val scrollLineEndMs = scrollLine.subtitles.last().startMs + scrollLine.subtitles.last().durationMs + timeToScrollScreenMs
                val scrollLineDurationMs = scrollLineEndMs - scrollLineStartMs

                val blankDurationMs = scrollLineStartMs - scrollLineEndMsPrev
                body.addAll(
                    MltNodeBuilder()
                        .blank(convertMillisecondsToTimecode(blankDurationMs))
                        .build()
                )

                val nodes = MltNodeBuilder()
                    .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
                    .filterQtblend(mltGenerator.nameFilterQtblend, mltProp.getRect(listOf(ProducerType.SCROLLER, voiceId, indexLine)))
                    .build()

                body.add(
                    mltGenerator.entry(
                        id = MltGenerator.nameProducer(ProducerType.SCROLLER, voiceId, indexLine),
                        timecodeIn = mltProp.getSongStartTimecode(),
                        timecodeOut = convertMillisecondsToTimecode(scrollLineDurationMs),
                        nodes = nodes
                    )
                )

                scrollLineStartMsPrev = scrollLineStartMs
                scrollLineEndMsPrev = scrollLineEndMs
                scrollLineDurationMsPrev = scrollLineDurationMs
            }

        }
        return result
    }

    override fun mainFilePlaylistTransformProperties(): String = ""

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()

}
