package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.*
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
            sett.voicesForMlt[voiceId].getLines()[lineId].getElements(songVersion).filter { it.type in listOf(SettingVoiceLineElementTypes.TEXT, SettingVoiceLineElementTypes.COMMENT) }.first()
        } catch (e: Exception) {
            return MltNode()
        }

        val haveNotes = songVersion.producers.contains(ProducerType.MELODYNOTE) && element.getSyllables().any { it.note != "" }

        val deltaY = if (haveNotes) {
            val sylFontSize = element.fontSize
            val melodyNoteFontSize = (sylFontSize * Karaoke.melodyNoteHeightCoefficient).toInt()
            val melodyNoteMltTextHeight = Karaoke.melodyNoteFont.copy("C", melodyNoteFontSize).h()
            val noteHeight = (melodyNoteMltTextHeight * Karaoke.melodyNoteHeightOffsetCoefficient).toInt()

            val tabsHeightCoefficient = Karaoke.melodyTabsHeightCoefficient
            val tabsFontSize = (sylFontSize * tabsHeightCoefficient).toInt()
            val tabsFont = Karaoke.melodyTabsFont
            val tabsMltTextHeight = tabsFont.copy("C", tabsFontSize).h()
            val tabsHeightOffsetCoefficient = Karaoke.melodyTabsHeightOffsetCoefficient
            val heightBetweenTabsLines = (tabsMltTextHeight * tabsHeightOffsetCoefficient).toInt()

            val tabsHeight = tabsMltTextHeight + 5 * heightBetweenTabsLines
            noteHeight + tabsHeight
        } else {
            0
        }

        var widthAreaPx= frameWidthPx
        var heightAreaPx= frameHeightPx

        val x = 0
        val y = deltaY

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
