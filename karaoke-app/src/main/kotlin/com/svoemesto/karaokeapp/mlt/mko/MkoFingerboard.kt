package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.model.SongVoiceLineSymbol
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO

data class MkoFingerboard(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type, voiceId, childId)

    override fun producer(): MltNode = mltGenerator
        .producer(
            props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                .propertyName("kdenlive:duration", mltProp.getEndTimecode("Song"))
                .propertyName("xmldata", mltProp.getXmlData(listOf(type, voiceId)).toString().xmldata())
                .propertyName("meta.media.width", mltProp.getFingerboardW(listOf(0, childId))!!)
                .propertyName("meta.media.height", mltProp.getFingerboardH(0) + 50)
                .filterQtblend(mltGenerator.nameFilterQtblend, mltProp.getRect(listOf(type, voiceId)))
                .build()
        )

    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
            val body = it as MutableList<MltNode>
            body.addAll(MltNodeBuilder().blank(mltProp.getInOffsetVideo()).build())
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
    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()

    override fun template(): MltNode {
        val voiceSetting = mltProp.getVoiceSetting(0)
        val fingerboardW = mltProp.getFingerboardW(listOf(0, childId))!!
        val capo = mltProp.getSongCapo()
        val fingerboardH = mltProp.getFingerboardH(0)
        val chordW = mltProp.getChordW(0)
        val chordH = mltProp.getChordH(0)
        val chords = mltProp.getChords(listOf(0, childId))
        val startChordX = 0 // (Karaoke.frameWidthPx / 2 - chordW /2 + chordW).toInt()

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
            val layouts = generateChordLayout(chord.mltText.text, capo)
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
                                Pair("x","${chordX}"),
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
                Pair("width","${fingerboardW}"),
                Pair("height","${fingerboardH+50}"),
                Pair("out","0"),
            ),
            body = body
        )
    }
}

