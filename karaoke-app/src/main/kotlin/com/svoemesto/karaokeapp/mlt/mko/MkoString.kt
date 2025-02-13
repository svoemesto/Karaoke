package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.model.PropertiesMltNodeBuilder
import java.awt.Font

data class MkoString(
    val mltProp: MltProp,
    val type: ProducerType = ProducerType.STRING,
    val voiceId: Int = 0,
    val lineId: Int = 0,
    val elementId: Int = 0
): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type, voiceId, lineId, elementId)

    private val songVersion = mltProp.getSongVersion()
    private val frameWidthPx = mltProp.getFrameWidthPx()
    private val frameHeightPx = mltProp.getFrameHeightPx()
    private val settings = mltProp.getSettings()
    private val songEndTimecode  = mltProp.getSongEndTimecode()
    private var lineDurationOnScreen = mltProp.getDurationOnScreen(listOf(ProducerType.LINE, voiceId, lineId))
    private var folderIdLines = mltProp.getId(listOf(ProducerType.LINES, voiceId))
    private val lineEndTimecode = if (lineDurationOnScreen > 0) {
        convertMillisecondsToTimecode(lineDurationOnScreen)
    } else {
        lineDurationOnScreen = convertTimecodeToMilliseconds(songEndTimecode)
        songEndTimecode
    }

    override fun producer(): MltNode {
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
                timecodeOut = lineEndTimecode,
                props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                    .propertyName("kdenlive:folderid", folderIdLines)
                    .propertyName("length", convertMillisecondsToFrames(lineDurationOnScreen))
                    .propertyName("kdenlive:duration", lineEndTimecode)
                    .propertyName("xmldata", template().toString().xmldata())
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

    override fun template(): MltNode {

        val sett = settings ?: return MltNode()
        val element = try {
            sett.voicesForMlt[voiceId].getLines()[lineId].getElements(songVersion)[elementId]
        } catch (e: Exception) {
            return MltNode()
        }

        var widthAreaPx= frameWidthPx
        var heightAreaPx= frameHeightPx

        val x = 0
        val y = 0
        val body: MutableList<MltNode> = mutableListOf()

        val textItem = MltNodeBuilder()
            .item(
                fields = PropertiesMltNodeBuilder()
                    .type("QGraphicsTextItem")
                    .`z-index`("2")
                    .build(),
                body = MltNodeBuilder()
                    .position(
                        fields = PropertiesMltNodeBuilder()
                            .x(x.toString())
                            .y(y.toString())
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
                        element.mltText().mltNode(element.mltText().text)
                    )
                    .build()
            )

            .build()

        body.addAll(textItem)
        body.addAll(
            MltNodeBuilder()
                .startviewport("0,0,${widthAreaPx},${heightAreaPx}")
                .endviewport("0,0,${widthAreaPx},${heightAreaPx}")
                .background("0,0,0,0")
                .build()
        )


        return MltNode(
            name = "kdenlivetitle",
            fields = PropertiesMltNodeBuilder()
                .duration(convertMillisecondsToFrames(lineDurationOnScreen).toString())
                .LC_NUMERIC("C")
                .width("$widthAreaPx")
                .height("$heightAreaPx")
                .`out`((convertMillisecondsToFrames(lineDurationOnScreen)-1).toString())
                .build(),
            body = body
        )
    }
}
