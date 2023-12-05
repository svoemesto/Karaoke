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

data class MkoScrollerTrack(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0): MltKaraokeObject {

    val mltGenerator = MltGenerator(mltProp, type, voiceId, childId)

//    override fun producer(): MltNode = mltGenerator
//        .producer(
//            props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
//                .propertyName("kdenlive:folderid", mltProp.getId(listOf(ProducerType.COUNTERS, voiceId)))
//                .propertyName("length", mltProp.getSongLengthFr())
//                .propertyName("kdenlive:duration", mltProp.getSongEndTimecode())
//                .propertyName("xmldata", mltProp.getXmlData(listOf(type, voiceId, childId)).toString().xmldata())
//                .propertyName("meta.media.width", Karaoke.frameWidthPx)
//                .propertyName("meta.media.height", Karaoke.frameHeightPx)
//                .build()
//        )

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

            val nodes = MltNodeBuilder()
                .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
                .build()

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

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()

//    override fun template(): MltNode {
//        val voiceSetting = mltProp.getVoiceSetting(voiceId)
//        val mltText: MltText = voiceSetting!!.groups[0].mltText
//        mltText.shapeColor =  Karaoke.countersColors[childId]
//
//        return MltNode(
//            name = "kdenlivetitle",
//            fields = mutableMapOf(
//                Pair("duration","0"),
//                Pair("LC_NUMERIC","C"),
//                Pair("width","${Karaoke.frameWidthPx}"),
//                Pair("height","${Karaoke.frameHeightPx}"),
//                Pair("out","0"),
//            ), body = mutableListOf(
//                MltNode(
//                    name = "item",
//                    fields = mutableMapOf(
//                        Pair("type","QGraphicsTextItem"),
//                        Pair("z-index","0"),
//                    ), body = mutableListOf(
//                        MltNode(
//                            name = "position",
//                            fields = mutableMapOf(Pair("x","${mltProp.getPositionXPx(listOf(ProducerType.COUNTER, voiceId))}"),Pair("y","${mltProp.getPositionYPx(ProducerType.COUNTER)}")),
//                            body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
//                        mltText.mltNode(childId.toString())
//                    )
//                ),
//                MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},${Karaoke.frameHeightPx}"))),
//                MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},${Karaoke.frameHeightPx}"))),
//                MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
//            )
//        )
//    }
}
