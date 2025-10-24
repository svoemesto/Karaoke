package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.*
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO

data class MkoChordPictureImage(
    val mltProp: MltProp,
    val type: ProducerType = ProducerType.CHORDPICTUREIMAGE,
    val voiceId: Int = 0,
    val lineId: Int = 0,
    val elementId: Int = 0
): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type, voiceId, lineId)

    private val frameWidthPx = mltProp.getFrameWidthPx()
    private val frameHeightPx = mltProp.getFrameHeightPx()
    private val chordWidthPx = frameHeightPx / 4
    private val chordHeightPx = chordWidthPx

//    private val songVersion = mltProp.getSongVersion()
//    private val settings = mltProp.getSettings()
    private val songEndTimecode  = mltProp.getSongEndTimecode()
    private var chordDurationOnScreen = mltProp.getDurationOnScreen(listOf(ProducerType.CHORDPICTURELINE, voiceId, lineId))
    private var folderIdChordPictures = mltProp.getId(listOf(ProducerType.CHORDPICTUREIMAGE, voiceId))
    private val chordEndTimecode = if (chordDurationOnScreen > 0) {
        convertMillisecondsToTimecode(chordDurationOnScreen)
    } else {
        chordDurationOnScreen = convertTimecodeToMilliseconds(songEndTimecode)
        songEndTimecode
    }
    private val chords = mltProp.getChords()
    private val chord = chords[lineId]
    private val capo = mltProp.getSongCapo()

    override fun producer(): MltNode {
        val widthAreaPx= chordWidthPx
        val heightAreaPx= chordHeightPx

        return mltGenerator
            .producer(
                timecodeOut = chordEndTimecode,
                props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                    .propertyName("kdenlive:folderid", folderIdChordPictures)
                    .propertyName("length", convertMillisecondsToFrames(chordDurationOnScreen))
                    .propertyName("kdenlive:duration", chordEndTimecode)
                    .propertyName("xmldata", template().toString().xmldata())
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
                    timecodeOut = chordEndTimecode,
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

    override fun tractor(): MltNode = mltGenerator.tractor(timecodeOut = chordEndTimecode)

    override fun template(): MltNode {

        val body: MutableList<MltNode> = mutableListOf()

        val layouts = generateChordLayout(chord.chord, capo)
        val bi = getChordLayoutPicture(layouts)
        val os = ByteArrayOutputStream()
        ImageIO.write(bi, "png", os)
        val base64 = Base64.getEncoder().encodeToString(os.toByteArray())

        body.add(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsRectItem"),
                    Pair("z-index","0"),
                ),
                body = mutableListOf(
                    MltNode(
                        name = "position",
                        fields = mutableMapOf(
                            Pair("x","0"),
                            Pair("y","0")
                        ),
                        body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(Pair("zoom","100")), body = "1,0,0,0,1,0,0,0,1"))
                    ),
                    MltNode(
                        name = "content",
                        fields = mutableMapOf(
                            Pair("brushcolor","0,0,0,255"),
                            Pair("pencolor","0,0,0,255"),
                            Pair("penwidth","0"),
                            Pair("penwidth","0"),
                            Pair("rect","0,0,$frameWidthPx, $chordHeightPx")
                        )
                    )
                )
            )
        )

        body.add(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsPixmapItem"),
                    Pair("z-index","0"),
                ),
                body = mutableListOf(
                    MltNode(
                        name = "position",
                        fields = mutableMapOf(
                            Pair("x","0"),
                            Pair("y","0")
                        )
                    ),
                    MltNode(
                        name = "content",
                        fields = mutableMapOf(
                            Pair("base64",base64)
                        )
                    )
                )
            )
        )

        body.add(MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,$frameWidthPx,$chordHeightPx"))))
        body.add(MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,$frameWidthPx,$chordHeightPx"))))
        body.add(MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0"))))

        return MltNode(
            name = "kdenlivetitle",
            fields = PropertiesMltNodeBuilder()
                .duration(convertMillisecondsToFrames(chordDurationOnScreen).toString())
                .lcNumeric("C")
                .width("$frameWidthPx")
                .height("$chordHeightPx")
                .`out`((convertMillisecondsToFrames(chordDurationOnScreen)-1).toString())
                .build(),
            body = body
        )
    }
}
