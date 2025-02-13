package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.getTextWidthHeightPx
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.xmldata
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.*
import java.awt.Font

data class MkoHeader(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type)

    private val frameWidthPx = mltProp.getFrameWidthPx()
    private val frameHeightPx = mltProp.getFrameHeightPx()
    private val songLengthFr = mltProp.getSongLengthFr()
    private val songStartTimecode = mltProp.getSongStartTimecode()
    private val songEndTimecode = mltProp.getSongEndTimecode()
    private val songFadeInTimecode = mltProp.getSongFadeInTimecode()
    private val songFadeOutTimecode = mltProp.getSongFadeOutTimecode()
    private val mkoHeaderProducerRect = mltProp.getRect(listOf(type))
    private val inOffsetVideo = mltProp.getInOffsetVideo()
    private val songVersion = mltProp.getSongVersion()
    private val songName = mltProp.getSongName(ProducerType.HEADER)
    private val author = mltProp.getAuthor(ProducerType.HEADER)
    private val album = mltProp.getAlbum(ProducerType.HEADER)
    private val tone = mltProp.getTone(ProducerType.HEADER)
    private val year = mltProp.getYear(ProducerType.HEADER)
    private val bpm = mltProp.getBpm(ProducerType.HEADER)
    private val logoAuthorBase64 = mltProp.getBase64("LogoAuthor")
    private val logoAlbumBase64 = mltProp.getBase64("LogoAlbum")
    override fun producer(): MltNode = mltGenerator
        .producer(
            props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                .propertyName("length", songLengthFr)
                .propertyName("kdenlive:duration", songEndTimecode)
                .propertyName("xmldata", template().toString().xmldata())
                .propertyName("meta.media.width", frameWidthPx)
                .propertyName("meta.media.height", frameHeightPx)
                .filterQtblend(mltGenerator.nameFilterQtblend, mkoHeaderProducerRect)
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
                        .filterQtblend(mltGenerator.nameFilterQtblend, mainFilePlaylistTransformProperties())
                        .build()
                )
            )
        }
        return result
    }
    override fun mainFilePlaylistTransformProperties(): String {
        val tpStart = TransformProperty(time = songStartTimecode, x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 0.0)
        val tpFadeIn = TransformProperty(time = songFadeInTimecode, x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 1.0)
        val tpFadeOut = TransformProperty(time = songFadeOutTimecode, x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 1.0)
        val tpEnd = TransformProperty(time = songEndTimecode, x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 0.0)
        val resultListTp = listOf(tpStart, tpFadeIn, tpFadeOut, tpEnd)
        return resultListTp.joinToString(";")
    }
    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()

    override fun template(): MltNode {

        val offsetX = Karaoke.songtextStartPositionXpx
        val maxSongnameW = Karaoke.headerSongnameMaxX - offsetX
        val songnameNameMltFont = Karaoke.headerSongnameFont

        var (songnameW, songnameH1) = getTextWidthHeightPx(songName, songnameNameMltFont.font)
        val (authorW, authorH) = getTextWidthHeightPx(Karaoke.headerAuthorName, Karaoke.headerAuthorFont.font)
        val (albumW, albumH) = getTextWidthHeightPx(Karaoke.headerAlbumName, Karaoke.headerAlbumFont.font)
        val (toneW, toneH) = getTextWidthHeightPx(Karaoke.headerToneName, Karaoke.headerToneFont.font)
        val (bpmW,bpmH) = getTextWidthHeightPx(Karaoke.headerBpmName, Karaoke.headerBpmFont.font)
        var songnameH: Double = songnameH1

        while (songnameW > maxSongnameW) {
            songnameNameMltFont.font = Font(songnameNameMltFont.font.name, songnameNameMltFont.font.style, songnameNameMltFont.font.size -1)
            songnameW = getTextWidthHeightPx(songName, songnameNameMltFont.font).first
            songnameH = getTextWidthHeightPx(songName, songnameNameMltFont.font).second
        }

        val songnameY = songnameH1 - songnameH
        val authorY = songnameY + (getTextWidthHeightPx(songName, Karaoke.headerSongnameFont.font).second).toLong()
        val albumY = authorY + (getTextWidthHeightPx(author, Karaoke.headerAuthorFont.font).second).toLong()
        val toneY = albumY + (getTextWidthHeightPx(album, Karaoke.headerAlbumFont.font).second).toLong()
        val bpmY = toneY + (getTextWidthHeightPx(tone, Karaoke.headerToneFont.font).second).toLong()


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
                        MltNode(name = "position", fields = mutableMapOf(Pair("x","${(frameWidthPx * 0.6385).toLong()}"),Pair("y","36")), body = mutableListOf(
                            MltNode(name = "transform", body = "${frameWidthPx*0.00025},0,0,0,${frameWidthPx*0.00025},0,0,0,1")
                        )),
                        MltNode(name = "content", fields = mutableMapOf(Pair("base64", logoAuthorBase64)))
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
                        MltNode(name = "position", fields = mutableMapOf(Pair("x","${(frameWidthPx * 0.8927).toLong()}"),Pair("y","36")), body = mutableListOf(
                            MltNode(name = "transform", body = "${frameWidthPx*0.00025},0,0,0,${frameWidthPx*0.00025},0,0,0,1")
                        )),
                        MltNode(name = "content", fields = mutableMapOf(Pair("base64", logoAlbumBase64)))
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
                    songnameNameMltFont.mltNode(songName)
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
                    Karaoke.headerAuthorFont.mltNode(author)
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
                    Karaoke.headerAlbumFont.mltNode("$album (${year})")
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
                        Karaoke.headerToneFont.mltNode(tone)
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
                        Karaoke.headerBpmFont.mltNode("$bpm bpm")
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
                        Pair("rect","0,0,${frameWidthPx},246"))
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
                        Pair("rect","0,0,${frameWidthPx},246"),
                        Pair("gradient","#ff000000;#00bf4040;0;100;90"))
                    )
                )
            )
        )

        body.add(MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${frameWidthPx},${frameHeightPx}"))))
        body.add(MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${frameWidthPx},${frameHeightPx}"))))
        body.add(MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0"))))

        return MltNode(
            name = "kdenlivetitle",
            fields = mutableMapOf(
                Pair("duration","0"),
                Pair("LC_NUMERIC","C"),
                Pair("width","$frameWidthPx"),
                Pair("height","$frameHeightPx"),
                Pair("out","0"),
            ),
            body = body
        )
    }
}

