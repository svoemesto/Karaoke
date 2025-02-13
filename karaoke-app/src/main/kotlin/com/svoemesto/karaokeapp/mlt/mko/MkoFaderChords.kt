package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.KaraokeVoice
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.xmldata
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType

data class MkoFaderChords(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type)

    private val frameWidthPx = mltProp.getFrameWidthPx()
    private val frameHeightPx = mltProp.getFrameHeightPx()
    private val songEndTimecode = mltProp.getSongEndTimecode()
    private val songLengthFr = mltProp.getSongLengthFr()
    private val fingerboardH = mltProp.getFingerboardH(0)
    private val mkoFaderChordsProducerRect = mltProp.getRect(listOf(type, voiceId))
    private val inOffsetVideo = mltProp.getInOffsetVideo()
    private val songCapo = mltProp.getSongCapo()
    private val songChordDescription = mltProp.getSongChordDescription()

    override fun producer(): MltNode = mltGenerator
        .producer(
            props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                .propertyName("length", songLengthFr)
                .propertyName("kdenlive:duration", songEndTimecode)
                .propertyName("xmldata", template().toString().xmldata())
                .propertyName("meta.media.width", frameWidthPx)
                .propertyName("meta.media.height", fingerboardH + 50)
                .filterQtblend(mltGenerator.nameFilterQtblend, mkoFaderChordsProducerRect)
                .build()
        )

    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
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
        //    val fingerboardW = param["VOICE0_FINGERBOARD_W"] as Int

        val capo = songCapo
        val chordDescription = songChordDescription

        val w = frameWidthPx / 4
        val h = frameHeightPx / 4
        val x = 0
        val y = 0
        val xRight = frameWidthPx - w
        val yBottom = frameHeightPx - h
        val chordsCapoMltFont = Karaoke.chordsCapoFont

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
                            Pair("x","$x"),
                            Pair("y","$y")
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
                            Pair("rect","0,0,$w,$h")
                        )
                    )
                )
            )
        )
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
                            Pair("x","${x+w}"),
                            Pair("y","$y")
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
                            Pair("rect","0,0,$w,$h"),
                            Pair("gradient","#ff000000;#00bf4040;0;100;0")
                        )
                    )
                )
            )
        )
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
                            Pair("x","${xRight-w}"),
                            Pair("y","$y")
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
                            Pair("rect","0,0,$w,$h"),
                            Pair("gradient","#ff000000;#00bf4040;0;100;180")
                        )
                    )
                )
            )
        )
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
                            Pair("x","${xRight}"),
                            Pair("y","$y")
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
                            Pair("rect","0,0,$w,$h")
                        )
                    )
                )
            )
        )
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
                            Pair("y","$h")
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
                            Pair("rect","0,0,${frameWidthPx},50"),
                            Pair("gradient","#ff000000;#00bf4040;0;100;90")
                        )
                    )
                )
            )
        )
        body.add(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsTextItem"),
                    Pair("z-index","6"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","10"),Pair("y","10")), body = mutableListOf(
                        MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1")
                    )),
                    chordsCapoMltFont.mltNode(chordDescription)
                )
            )
        )

        body.add(MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${frameWidthPx},${fingerboardH+50}"))))
        body.add(MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${frameWidthPx},${fingerboardH+50}"))))
        body.add(MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0"))))

        return MltNode(
            name = "kdenlivetitle",
            fields = mutableMapOf(
                Pair("duration","0"),
                Pair("LC_NUMERIC","C"),
                Pair("width","$frameWidthPx"),
                Pair("height","${fingerboardH+50}"),
                Pair("out","0"),
            ),
            body = body
        )
    }
}
