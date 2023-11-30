package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.getTextWidthHeightPx
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.xmldata
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.model.SongVersion
import java.awt.Font

data class MkoHeader(
    val mltProp: MltProp) : MltKaraokeObject {
    val type: ProducerType = ProducerType.HEADER
    val voiceId: Int = 0
    val mltGenerator = MltGenerator(mltProp, type)

    override fun producer(): MltNode = mltGenerator
        .producer(
            props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                .propertyName("length", mltProp.getLengthFr("Song"))
                .propertyName("kdenlive:duration", mltProp.getEndTimecode("Song"))
                .propertyName("xmldata", mltProp.getXmlData(listOf(type, voiceId)).toString().xmldata())
                .propertyName("meta.media.width", Karaoke.frameWidthPx)
                .propertyName("meta.media.height", Karaoke.frameHeightPx)
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
                        .filterQtblend(mltGenerator.nameFilterQtblend, "${mltProp.getStartTimecode("Song")}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.000000;${mltProp.getFadeInTimecode("Song")}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.000000;${mltProp.getFadeOutTimecode("Song")}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.000000;${mltProp.getEndTimecode("Song")}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.000000")
                        .build()
                )
            )
        }
        return result
    }
    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()

    override fun template(): MltNode {
        val songVersion = mltProp.getSongVersion()

        val offsetX = Karaoke.songtextStartPositionXpx
        val maxSongnameW = Karaoke.headerSongnameMaxX - offsetX
        val songnameNameMltFont = Karaoke.headerSongnameFont

        var (songnameW, songnameH1) = getTextWidthHeightPx(mltProp.getSongName(ProducerType.HEADER), songnameNameMltFont.font)
        val (authorW, authorH) = getTextWidthHeightPx(Karaoke.headerAuthorName, Karaoke.headerAuthorFont.font)
        val (albumW, albumH) = getTextWidthHeightPx(Karaoke.headerAlbumName, Karaoke.headerAlbumFont.font)
        val (toneW, toneH) = getTextWidthHeightPx(Karaoke.headerToneName, Karaoke.headerToneFont.font)
        val (bpmW,bpmH) = getTextWidthHeightPx(Karaoke.headerBpmName, Karaoke.headerBpmFont.font)
        var songnameH: Double = songnameH1

        while (songnameW > maxSongnameW) {
            songnameNameMltFont.font = Font(songnameNameMltFont.font.name, songnameNameMltFont.font.style, songnameNameMltFont.font.size -1)
            songnameW = getTextWidthHeightPx(mltProp.getSongName(ProducerType.HEADER), songnameNameMltFont.font).first
            songnameH = getTextWidthHeightPx(mltProp.getSongName(ProducerType.HEADER), songnameNameMltFont.font).second
        }

        val songnameY = songnameH1 - songnameH
        val authorY = songnameY + (getTextWidthHeightPx(mltProp.getSongName(ProducerType.HEADER).toString(), Karaoke.headerSongnameFont.font).second).toLong()
        val albumY = authorY + (getTextWidthHeightPx(mltProp.getAuthor(ProducerType.HEADER), Karaoke.headerAuthorFont.font).second).toLong()
        val toneY = albumY + (getTextWidthHeightPx(mltProp.getAlbum(ProducerType.HEADER), Karaoke.headerAlbumFont.font).second).toLong()
        val bpmY = toneY + (getTextWidthHeightPx(mltProp.getTone(ProducerType.HEADER), Karaoke.headerToneFont.font).second).toLong()


        val maxW = listOf(authorW, albumW, toneW, bpmW).maxBy { it }
        val authorX = offsetX + maxW - authorW
        val albumX = offsetX + maxW - albumW
        val toneX = offsetX + maxW - toneW
        val bpmX = offsetX + maxW - bpmW
        val valueX = offsetX + maxW

        val body = mutableListOf<MltNode>()
        if (Karaoke.createLogotype) {
            body.add(
                MltNode(
                    name = "item",
                    fields = mutableMapOf(
                        Pair("type","QGraphicsPixmapItem"),
                        Pair("z-index","6"),
                    ), body = mutableListOf(
                        MltNode(name = "position", fields = mutableMapOf(Pair("x","${(Karaoke.frameWidthPx * 0.6385).toLong()}"),Pair("y","36")), body = mutableListOf(
                            MltNode(name = "transform", body = "${Karaoke.frameWidthPx*0.00025},0,0,0,${Karaoke.frameWidthPx*0.00025},0,0,0,1")
                        )),
                        MltNode(name = "content", fields = mutableMapOf(Pair("base64", mltProp.getBase64("LogoAuthor"))))
                    )
                )
            )
            body.add(
                MltNode(
                    name = "item",
                    fields = mutableMapOf(
                        Pair("type","QGraphicsPixmapItem"),
                        Pair("z-index","6"),
                    ), body = mutableListOf(
                        MltNode(name = "position", fields = mutableMapOf(Pair("x","${(Karaoke.frameWidthPx * 0.8927).toLong()}"),Pair("y","36")), body = mutableListOf(
                            MltNode(name = "transform", body = "${Karaoke.frameWidthPx*0.00025},0,0,0,${Karaoke.frameWidthPx*0.00025},0,0,0,1")
                        )),
                        MltNode(name = "content", fields = mutableMapOf(Pair("base64", mltProp.getBase64("LogoAlbum"))))
                    )
                )
            )
        }

        body.add(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsTextItem"),
                    Pair("z-index","6"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","$offsetX"),Pair("y","${songnameY.toLong()}")), body = mutableListOf(
                        MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1")
                    )),
                    songnameNameMltFont.mltNode(mltProp.getSongName(ProducerType.HEADER))
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
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","${authorX.toLong()}"),Pair("y","${authorY.toLong()}")), body = mutableListOf(
                        MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1")
                    )),
                    Karaoke.headerAuthorNameFont.mltNode(Karaoke.headerAuthorName)
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
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","${valueX.toLong()}"),Pair("y","${authorY.toLong()}")), body = mutableListOf(
                        MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1")
                    )),
                    Karaoke.headerAuthorFont.mltNode(mltProp.getAuthor(ProducerType.HEADER))
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
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","${albumX.toLong()}"),Pair("y","${albumY.toLong()}")), body = mutableListOf(
                        MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1")
                    )),
                    Karaoke.headerAlbumNameFont.mltNode(Karaoke.headerAlbumName)
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
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","${valueX.toLong()}"),Pair("y","${albumY.toLong()}")), body = mutableListOf(
                        MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1")
                    )),
                    Karaoke.headerAlbumFont.mltNode("${mltProp.getAlbum(ProducerType.HEADER)} (${mltProp.getYear(ProducerType.HEADER)})")
                )
            )
        )

        if (songVersion == SongVersion.CHORDS) {
            body.add(
                MltNode(
                    name = "item",
                    fields = mutableMapOf(
                        Pair("type","QGraphicsTextItem"),
                        Pair("z-index","6"),
                    ), body = mutableListOf(
                        MltNode(name = "position", fields = mutableMapOf(Pair("x","${toneX.toLong()}"),Pair("y","${toneY.toLong()}")), body = mutableListOf(
                            MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1")
                        )),
                        Karaoke.headerToneNameFont.mltNode(Karaoke.headerToneName)
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
                        MltNode(name = "position", fields = mutableMapOf(Pair("x","${valueX.toLong()}"),Pair("y","${toneY.toLong()}")), body = mutableListOf(
                            MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1")
                        )),
                        Karaoke.headerToneFont.mltNode(mltProp.getTone(ProducerType.HEADER))
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
                        MltNode(name = "position", fields = mutableMapOf(Pair("x","${bpmX.toLong()}"),Pair("y","${bpmY.toLong()}")), body = mutableListOf(
                            MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1")
                        )),
                        Karaoke.headerBpmNameFont.mltNode(Karaoke.headerBpmName)
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
                        MltNode(name = "position", fields = mutableMapOf(Pair("x","${valueX.toLong()}"),Pair("y","${bpmY.toLong()}")), body = mutableListOf(
                            MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1")
                        )),
                        Karaoke.headerBpmFont.mltNode("${mltProp.getBpm(ProducerType.HEADER)} bpm")
                    )
                )
            )
        }



        body.add(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsRectItem"),
                    Pair("z-index","5"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","0"),Pair("y","0")), body = mutableListOf(
                        MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1")
                    )),
                    MltNode(name = "content", fields = mutableMapOf(
                        Pair("brushcolor","0,0,0,255"),
                        Pair("pencolor","0,0,0,255"),
                        Pair("penwidth","0"),
                        Pair("rect","0,0,${Karaoke.frameWidthPx},246"))
                    )
                )
            )
        )

        body.add(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsRectItem"),
                    Pair("z-index","5"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","0"),Pair("y","246")), body = mutableListOf(
                        MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1")
                    )),
                    MltNode(name = "content", fields = mutableMapOf(
                        Pair("brushcolor","0,0,0,255"),
                        Pair("pencolor","0,0,0,255"),
                        Pair("penwidth","0"),
                        Pair("rect","0,0,${Karaoke.frameWidthPx},246"),
                        Pair("gradient","#ff000000;#00bf4040;0;100;90"))
                    )
                )
            )
        )

        body.add(MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},${Karaoke.frameHeightPx}"))))
        body.add(MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},${Karaoke.frameHeightPx}"))))
        body.add(MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0"))))

        return MltNode(
            name = "kdenlivetitle",
            fields = mutableMapOf(
                Pair("duration","0"),
                Pair("LC_NUMERIC","C"),
                Pair("width","${Karaoke.frameWidthPx}"),
                Pair("height","${Karaoke.frameHeightPx}"),
                Pair("out","0"),
            ),
            body = body
        )
    }
}

