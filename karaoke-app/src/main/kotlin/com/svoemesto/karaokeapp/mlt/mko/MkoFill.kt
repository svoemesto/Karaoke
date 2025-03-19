package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.*

data class MkoFill(
    val mltProp: MltProp,
    val type: ProducerType = ProducerType.FILL,
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
    private var folderIdLines = mltProp.getId(listOf(ProducerType.LINES, voiceId))
    private var lineDurationOnScreen = mltProp.getDurationOnScreen(listOf(ProducerType.LINE, voiceId, lineId))
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
                props = MltNodeBuilder()
                    .propertyName("length", convertMillisecondsToFrames(lineDurationOnScreen))
                    .propertyName("eof", "pause")
                    .propertyName("resource", Karaoke.voices[0].fill.evenColor.hexRGB())
                    .propertyName("aspect_ratio", 1)
                    .propertyName("mlt_service", "color")
                    .propertyName("kdenlive:duration", lineEndTimecode)
                    .propertyName("mlt_image_format", "rgb")
                    .propertyName("kdenlive:clipname", mltGenerator.name)
                    .propertyName("kdenlive:folderid", folderIdLines)
                    .propertyName("kdenlive:clip_type", if (type.isAudio) 1 else 2)
                    .propertyName("kdenlive:id", mltGenerator.id)

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
                        .filterQtblend(mltGenerator.nameFilterQtblend, mainFilePlaylistTransformProperties(), distort = 1)
                        .build()
                )
            )


        }
        return result
    }

    override fun mainFilePlaylistTransformProperties(): String {
        val elementTransformProperties = settings?.voicesForMlt?.get(voiceId)?.getLines()?.get(lineId)?.getElements(songVersion)?.get(elementId)?.transformProperties() ?: emptyList()
        val default = "00:00:00.000=0 0 1 1 0.0"
        val sett = settings ?: return default
        val element = try {
            sett.voicesForMlt[voiceId].getLines()[lineId].getElements(songVersion).filter { it.type == SettingVoiceLineElementTypes.TEXT }.first()
        } catch (e: Exception) {
            return default
        }

        val haveNotes = songVersion.producers.contains(ProducerType.MELODYNOTE) && element.getSyllables().any { it.note != "" }

        val deltaY = if (haveNotes) {
            val sylFontSize = element.fontSize
            val melodyNoteFontSize = (sylFontSize * Karaoke.melodyNoteHeightCoefficient).toInt()
            val melodyNoteMltTextHeight = Karaoke.melodyNoteFont.copy("C", melodyNoteFontSize).h()
            (melodyNoteMltTextHeight * Karaoke.melodyNoteHeightOffsetCoefficient).toInt()
        } else {
            0
        }

        val result = elementTransformProperties.map {
            TransformProperty(
                time = it.time,
                x = it.x,
                y = it.y + deltaY,
                w = it.w,
                h = it.h,
                opacity = it.opacity
            )
        }

        return if (result.isNotEmpty()) result.joinToString(";") else default
    }

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()
    override fun tractor(): MltNode = mltGenerator.tractor(timecodeOut = lineEndTimecode)

}
