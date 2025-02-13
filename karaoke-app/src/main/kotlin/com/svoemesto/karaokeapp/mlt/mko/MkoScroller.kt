package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.model.PropertiesMltNodeBuilder
import java.awt.Font

data class MkoScroller(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {

    val mltGenerator = MltGenerator(mltProp, type, voiceId, childId)

    override fun producer(): MltNode = mltGenerator
        .producer(
            props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                .propertyName("kdenlive:folderid", mltProp.getId(listOf(ProducerType.SCROLLERS, voiceId)))
                .propertyName("length", mltProp.getSongLengthFr())
                .propertyName("kdenlive:duration", mltProp.getSongEndTimecode())
//                .propertyName("xmldata", mltProp.getXmlData(listOf(type, voiceId, childId)).toString().xmldata())
//                .propertyName("xmldata", mltProp.getXmlData(listOf(ProducerType.SCROLLER, voiceId, childId)).toString().xmldata())
                .propertyName("xmldata", template().toString().xmldata())
                .propertyName("meta.media.width", mltProp.getFrameWidthPx())
                .propertyName("meta.media.height", mltProp.getFrameHeightPx())
                .build()
        )

    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
            val body = it as MutableList<MltNode>
            body.add(
                mltGenerator.entry(
                    nodes = MltNodeBuilder()
                        .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
//                        .filterQtblend(mltGenerator.nameFilterQtblend, mltProp.getRect(listOf(type, voiceId, childId)))
                        .build()
                )
            )
        }
        return result
    }
    override fun mainFilePlaylistTransformProperties(): String = ""

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()

    override fun template(): MltNode {
        val voiceSetting = mltProp.getVoiceSetting(voiceId)
        val mltText: MltText = voiceSetting!!.groups[0].mltText
        mltText.shapeColor =  Karaoke.countersColors[0]
        val body: MutableList<MltNode> = mutableListOf()

        val offsetX = Karaoke.songtextStartPositionXpx
        val heightScrollerPx = mltProp.getHeightScrollerPx()
        val widthPxPerMsCoeff = mltProp.getWidthPxPerMsCoeff()
        var widthAreaPx = 0L

        val initFont = mltProp.getScrollLines(voiceId)[childId].mltText.font
        var fontSize = 1

        while (getTextWidthHeightPx("0", Font(MAIN_FONT_NAME, initFont.style, fontSize)).second < heightScrollerPx) {
            fontSize++
        }
        fontSize--

        val scrollLine = mltProp.getScrollLines(voiceId)[childId]

        val timeToScrollScreenMs = mltProp.getTimeToScrollScreenMs()
        val scrollLineStartMs = scrollLine.subtitles.first().startMs - timeToScrollScreenMs
        val scrollLineEndMs = scrollLine.subtitles.last().startMs + scrollLine.subtitles.last().durationMs + timeToScrollScreenMs
        val scrollLineDurationMs = scrollLineEndMs - scrollLineStartMs

        val initStartMs = mltProp.getScrollLines(voiceId)[childId].subtitles.first().startMs
        scrollLine.subtitles.forEachIndexed { indexSubtitle, subtitle ->
            val subStartMs = subtitle.startMs - initStartMs
            val subDurationMs = subtitle.durationMs
            val xScrollerPx = (subStartMs * widthPxPerMsCoeff).toLong()
            val yScrollerPx = 0L
            val widthScrollerPx = (subDurationMs * widthPxPerMsCoeff).toLong()

            widthAreaPx += widthScrollerPx

            val subRectItem = MltNodeBuilder()
                .item(
                    fields = PropertiesMltNodeBuilder()
                        .type("QGraphicsRectItem")
                        .`z-index`("1")
                        .build(),
                    body = MltNodeBuilder()
                        .position(
                            fields = PropertiesMltNodeBuilder()
                                .x(xScrollerPx.toString())
                                .y(yScrollerPx.toString())
                                .build(),
                            body = MltNodeBuilder()
                                .transform(
                                    fields = PropertiesMltNodeBuilder()
                                        .zoom("100")
                                        .build(),
                                    body = "1,0,0,0,1,0,0,0,1"
                                )
                                .build()
                        )
                        .content(
                            fields = PropertiesMltNodeBuilder()
                                .brushcolor(if (indexSubtitle % 2 == 0) "8,0,255,255" else "234,255,0,255")
                                .pencolor("0,0,0,255")
                                .penwidth("0")
                                .rect("0,0,${widthScrollerPx},${heightScrollerPx}")
                                .build()
                        )
                        .build()
                )
                .item(
                    fields = PropertiesMltNodeBuilder()
                        .type("QGraphicsTextItem")
                        .`z-index`("2")
                        .build(),
                    body = MltNodeBuilder()
                        .position(
                            fields = PropertiesMltNodeBuilder()
                                .x(xScrollerPx.toString())
                                .y(yScrollerPx.toString())
                                .build(),
                            body = MltNodeBuilder()
                                .transform(
                                    fields = PropertiesMltNodeBuilder()
                                        .zoom("100")
                                        .build(),
                                    body = "1,0,0,0,1,0,0,0,1"
                                )
                                .build()
                        )
                        .node(
                            subtitle.mltText.copy(subtitle.mltText.text.replace("&","&amp;amp;"), Font(MAIN_FONT_NAME, initFont.style, fontSize)).mltNode(subtitle.mltText.text.replace("&","&amp;amp;"))
                        )
                        .build()
                )

                .build()

            body.addAll(subRectItem)

        }

        body.addAll(
            MltNodeBuilder()
                .startviewport("0,0,${widthAreaPx},${heightScrollerPx}")
                .endviewport("0,0,${widthAreaPx},${heightScrollerPx}")
                .background("0,0,0,0")
                .build()
        )

        val rectStartTimecode = mltProp.getSongStartTimecode()
        val rectEndTimecode = convertMillisecondsToTimecode(scrollLineDurationMs)

        val rectStartXpx = mltProp.getFrameWidthPx() + offsetX
        val rectStartYpx = 0
        val rectStartWpx = widthAreaPx
        val rectStartHpx = heightScrollerPx
        val rectStartOpacity = 1.0

        val rectEndXpx = - (mltProp.getFrameWidthPx() + widthAreaPx + offsetX)
        val rectEndYpx = 0
        val rectEndWpx = widthAreaPx
        val rectEndHpx = heightScrollerPx
        val rectEndOpacity = 1.0

        val propRect =
            listOf(
                "$rectStartTimecode=$rectStartXpx $rectStartYpx $rectStartWpx $rectStartHpx $rectStartOpacity",
                "$rectEndTimecode=$rectEndXpx $rectEndYpx $rectEndWpx $rectEndHpx $rectEndOpacity"
            ).joinToString(";")
        mltProp.setRect(propRect, listOf(type, voiceId, childId))

        return MltNode(
            name = "kdenlivetitle",
            fields = PropertiesMltNodeBuilder()
                .duration("0")
                .LC_NUMERIC("C")
                .width("${widthAreaPx}")
                .height("${heightScrollerPx}")
                .`out`("0")
                .build(),
            body = body
        )

    }
}
