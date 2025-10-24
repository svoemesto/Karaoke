package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.*
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO

data class MkoFingerboard(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type, voiceId, childId)

    private val songLengthFr = mltProp.getSongLengthFr()

    private val mkoFingerboardProducerRect = mltProp.getRect(listOf(type, voiceId, childId))
    private val inOffsetVideo = mltProp.getInOffsetVideo()
    private val songCapo = mltProp.getSongCapo()
    private val chordW = 270 // mltProp.getChordW(0)
    private val chords = mltProp.getChords()
    private val fingerboardW = 270 * chords.size //mltProp.getFingerboardW(listOf(0, childId))!!
    private val fingerboardH = 270 // mltProp.getFingerboardH(0)
    override fun producer(): MltNode = mltGenerator
        .producer(
            props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                .propertyName("kdenlive:duration", songLengthFr)
                .propertyName("xmldata", template().toString().xmldata())
                .propertyName("meta.media.width", fingerboardW)
                .propertyName("meta.media.height", fingerboardH + 50)
                .filterQtblend(mltGenerator.nameFilterQtblend, mkoFingerboardProducerRect)
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

    override fun tractor(): MltNode = mltGenerator.tractor()

    override fun template(): MltNode {
        val capo = songCapo
        val startChordX = 0 // (mltProp.getFrameWidthPx() / 2 - chordW /2 + chordW).toInt()

        val body: MutableList<MltNode> = mutableListOf()

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
                            Pair("rect","0,0,$fingerboardW,$fingerboardH")
                        )
                    )
                )
            )
        )

        chords.forEachIndexed{ indexChord, chord ->
            val chordX = startChordX + indexChord * chordW
            val layouts = generateChordLayout(chord.chord, capo)
            val bi = getChordLayoutPicture(layouts)
            val os = ByteArrayOutputStream()
            ImageIO.write(bi, "png", os)
            val base64 = Base64.getEncoder().encodeToString(os.toByteArray())

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
                                Pair("x","$chordX"),
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
        }

        body.add(MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${fingerboardW},${fingerboardH+50}"))))
        body.add(MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${fingerboardW},${fingerboardH+50}"))))
        body.add(MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0"))))

        return MltNode(
            name = "kdenlivetitle",
            fields = mutableMapOf(
                Pair("duration","0"),
                Pair("LC_NUMERIC","C"),
                Pair("width","$fingerboardW"),
                Pair("height","${fingerboardH+50}"),
                Pair("out","0"),
            ),
            body = body
        )
    }
}

