package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.*
import java.awt.Color
import java.awt.Font

data class MkoSplashStart(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type)

    private val frameWidthPx = mltProp.getFrameWidthPx()
    private val frameHeightPx = mltProp.getFrameHeightPx()
    private val splashLengthMs = mltProp.getSplashLengthMs()
    private val splashStartTimecode = mltProp.getSplashStartTimecode()
    private val splashEndTimecode = mltProp.getSplashEndTimecode()
    private val splashFadeInTimecode = mltProp.getSplashFadeInTimecode()
    private val splashFadeOutTimecode = mltProp.getSplashFadeOutTimecode()
    private val songVersion = mltProp.getSongVersion()
    private val songName = mltProp.getSongName()
    private val logoAuthorBase64 = mltProp.getBase64("LogoAuthor")
    private val logoAlbumBase64 = mltProp.getBase64("LogoAlbum")
    private val capo = mltProp.getSongCapo()
    private val toneBpmDescriptionText = if (songVersion in listOf(SongVersion.KARAOKE, SongVersion.TABS)) {
        "Key: «" + mltProp.getSongTone() + "», bpm: " + mltProp.getSongBpm()
    } else if (songVersion in listOf(SongVersion.CHORDS)) {
        "Key: «" + mltProp.getSongTone() + "», bpm: " + mltProp.getSongBpm() + if (capo > 0) ", каподастр на $capo-м ладу" else ""
    } else {
        ""
    }
    private val cds = mltProp.getSongChordDescription().replace("\n",", ")
    private val chordDescriptionText = if (cds !== "") cds else toneBpmDescriptionText

    override fun producer(): MltNode = mltGenerator
        .producer(
            timecodeIn = splashStartTimecode,
            timecodeOut = splashEndTimecode,
            props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                .propertyName("length", splashLengthMs)
                .propertyName("kdenlive:duration", splashEndTimecode)
                .propertyName("xmldata", template().toString().xmldata())
                .propertyName("meta.media.width", frameWidthPx)
                .propertyName("meta.media.height", frameHeightPx)
                .build()
        )

    override fun filePlaylist(): MltNode {

        val result = mltGenerator.filePlaylist()
        result.body?.let {
            val body = it as MutableList<MltNode>
            body.add(
                mltGenerator.entry(
                    timecodeIn = splashStartTimecode,
                    timecodeOut = splashEndTimecode,
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
        val tpStart = TransformProperty(time = splashStartTimecode, x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 0.0)
        val tpFadeIn = TransformProperty(time = splashFadeInTimecode, x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 1.0)
        val tpFadeOut = TransformProperty(time = splashFadeOutTimecode, x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 1.0)
        val tpEnd = TransformProperty(time = splashEndTimecode, x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 0.0)
        val resultListTp = listOf(tpStart, tpFadeIn, tpFadeOut, tpEnd)
        return resultListTp.joinToString(";")
    }

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor(
        timecodeIn = splashStartTimecode,
        timecodeOut = splashEndTimecode
    )

    override fun template(): MltNode {

        var songnameTextFontMlt = Karaoke.splashstartSongNameFont
        var songnameTextFont = songnameTextFontMlt.font
        val songversionTextFontMlt = Karaoke.splashstartSongVersionFont
        val commentTextFontMlt = Karaoke.splashstartCommentFont
        val chordDescriptionTextFontMlt = Karaoke.splashstartChordDescriptionFont

        val commentText = songVersion.textForDescription
        val songversionText = songVersion.text
        val songnameText = songName

        val border = (frameHeightPx *0.05).toLong()
        val pictureScaleCoeff = frameWidthPx / 1920.0

        val albumPictureX = ((frameWidthPx - (400 * pictureScaleCoeff + border + 1000 * pictureScaleCoeff)) / 2).toLong()
        val albumPictureY = border
        val albumPictureW = 400 * pictureScaleCoeff
        val albumPictureH = 400 * pictureScaleCoeff

        val authorPictureX = albumPictureX + 400 * pictureScaleCoeff + border
        val authorPictureY = border
        val authorPictureW = 1000 * pictureScaleCoeff
        val authorPictureH = 400 * pictureScaleCoeff

        val commentTextW =  (getTextWidthHeightPx(commentText, commentTextFontMlt.font).first).toLong()
        val commentTextH =  (getTextWidthHeightPx(commentText, commentTextFontMlt.font).second).toLong()
        val commentTextX = (frameWidthPx - commentTextW) / 2
        val commentTextY = (frameHeightPx - border/2 - getTextWidthHeightPx("0", commentTextFontMlt.font).second).toLong()

        val chordDescriptionTextW =  (getTextWidthHeightPx(chordDescriptionText, chordDescriptionTextFontMlt.font).first).toLong()
        val chordDescriptionTextH =  (getTextWidthHeightPx(chordDescriptionText, chordDescriptionTextFontMlt.font).second).toLong()
        val chordDescriptionTextX = (frameWidthPx - chordDescriptionTextW) / 2
        val chordDescriptionTextY = if (chordDescriptionText != "") (commentTextY - getTextWidthHeightPx("0", chordDescriptionTextFontMlt.font).second).toLong() else commentTextY

        val songversionTextW =  (getTextWidthHeightPx(songversionText, songversionTextFontMlt.font).first).toLong()
        val songversionTextH =  (getTextWidthHeightPx(songversionText, songversionTextFontMlt.font).second).toLong()
        val songversionTextX = (frameWidthPx - songversionTextW) / 2
        val songversionTextY = (chordDescriptionTextY - getTextWidthHeightPx("0", songversionTextFontMlt.font).second).toLong()

        var songnameTextY = (border + 400 * pictureScaleCoeff).toLong()
        val songnameTextHmax = songversionTextY - songnameTextY
        do {
            songnameTextFont = Font(songnameTextFont.name, songnameTextFont.style, songnameTextFont.size+1)
            val (w,h) = getTextWidthHeightPx(songnameText, songnameTextFont)
        } while (!(h > songnameTextHmax || w > (mltProp.getFrameWidthPx() - 2*border)))
        songnameTextFontMlt.font = songnameTextFont
        val songnameTextW = (getTextWidthHeightPx(songnameText, songnameTextFontMlt.font).first).toLong()
        val songnameTextH = (getTextWidthHeightPx(songnameText, songnameTextFontMlt.font).second).toLong()
        val songnameTextX = (frameWidthPx - songnameTextW) / 2
        songnameTextY = (songversionTextY - songnameTextH - (songversionTextY - songnameTextH - songnameTextY)/2)



        val frameW = 1920
        val frameH = 1080

        val padding = 50
        val textAreaW = frameW - 2*padding
        val textAreaH = 350
        val albumH = 400
        val picAreaH = 2*padding + albumH

        val areaText = Picture(
            params = TextParams(
                w = textAreaW,
                h = textAreaH,
                color = Color(255,255,127,255),
                text = songnameText,
                fontName = MAIN_FONT_NAME,
                fontStyle = 0,
                fontSize = 50,
                isCalculatedSize = true,
                isLineBreak = true
            )
        )

        val area = Picture(
            params = AreaParams(w = frameW, h = frameH, color = Color.BLACK),
            childs = mutableListOf(
                PictureChild(x = padding, y = picAreaH - padding, child = areaText)
            )
        )

        val multiLines = areaText.multiLines()

        val body = mutableListOf<MltNode>()

        body.add(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsPixmapItem"),
                    Pair("z-index","6"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","$authorPictureX"),Pair("y","$authorPictureY")),
                        body = mutableListOf(MltNode(name = "transform", body = "${pictureScaleCoeff},0,0,0,${pictureScaleCoeff},0,0,0,1"))),
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
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","$albumPictureX"),Pair("y","$albumPictureY")),
                        body = mutableListOf(MltNode(name = "transform", body = "${pictureScaleCoeff},0,0,0,${pictureScaleCoeff},0,0,0,1"))),
                    MltNode(name = "content", fields = mutableMapOf(Pair("base64", logoAlbumBase64)))
                )
            )
        )

        multiLines.forEach { multiLine ->
            songnameTextFontMlt.font = Font(multiLine.fontName, multiLine.fontStyle, multiLine.fontSize)
            body.add(
                MltNode(
                    name = "item",
                    fields = mutableMapOf(
                        Pair("type","QGraphicsTextItem"),
                        Pair("z-index","6"),
                    ), body = mutableListOf(
//                        MltNode(name = "position", fields = mutableMapOf(Pair("x","${multiLine.centerX + padding}"),Pair("y","-50")),
                        MltNode(name = "position", fields = mutableMapOf(Pair("x","${multiLine.centerX + padding}"),Pair("y","${multiLine.topY + (picAreaH - padding)}")),
                            body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                        songnameTextFontMlt.mltNode(multiLine.text.replace("&","&amp;"))
                    )
                )
            )

        }

//        body.add(
//            MltNode(
//                name = "item",
//                fields = mutableMapOf(
//                    Pair("type","QGraphicsTextItem"),
//                    Pair("z-index","6"),
//                ), body = mutableListOf(
//                    MltNode(name = "position", fields = mutableMapOf(Pair("x","$songnameTextX"),Pair("y","$songnameTextY")),
//                        body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
//                    songnameTextFontMlt.mltNode(songnameText.replace("&","&amp;"))
//                )
//            )
//        )

        body.add(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsTextItem"),
                    Pair("z-index","6"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","$songversionTextX"),Pair("y","$songversionTextY")),
                        body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                    songversionTextFontMlt.mltNode(songversionText)
                )
            )
        )

        if (chordDescriptionText != "") {
            body.add(
                MltNode(
                    name = "item",
                    fields = mutableMapOf(
                        Pair("type","QGraphicsTextItem"),
                        Pair("z-index","6"),
                    ), body = mutableListOf(
                        MltNode(name = "position", fields = mutableMapOf(Pair("x","$chordDescriptionTextX"),Pair("y","$chordDescriptionTextY")),
                            body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                        chordDescriptionTextFontMlt.mltNode(chordDescriptionText)
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
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","$commentTextX"),Pair("y","$commentTextY")),
                        body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                    commentTextFontMlt.mltNode(commentText)
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
