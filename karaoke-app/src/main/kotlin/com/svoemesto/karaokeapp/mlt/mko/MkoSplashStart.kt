package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.getTextWidthHeightPx
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.xmldata
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.*
import java.awt.Font

data class MkoSplashStart(
    val mltProp: MltProp) : MltKaraokeObject {
    val type: ProducerType = ProducerType.SPLASHSTART
    val voiceId: Int = 0
    val mltGenerator = MltGenerator(mltProp, type)


    override fun producer(): MltNode = mltGenerator
        .producer(
            timecodeIn = mltProp.getStartTimecode("Song"),
            timecodeOut = mltProp.getEndTimecode(ProducerType.SPLASHSTART),
            props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                .propertyName("length", Karaoke.timeSplashScreenStartMs.toString())
                .propertyName("kdenlive:duration", mltProp.getEndTimecode(ProducerType.SPLASHSTART))
                .propertyName("xmldata", mltProp.getXmlData(listOf(type, voiceId)).toString().xmldata())
                .propertyName("meta.media.width", Karaoke.frameWidthPx)
                .propertyName("meta.media.height", Karaoke.frameHeightPx)
                .build()
        )

    override fun fileProducer(): MltNode = MltNode()


    override fun filePlaylist(): MltNode {

        val result = mltGenerator.filePlaylist()
        result.body?.let {
            val body = it as MutableList<MltNode>
            body.add(
                mltGenerator.entry(
                    nodes = MltNodeBuilder()
                        .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
                        .filterQtblend(mltGenerator.nameFilterQtblend, "${mltProp.getStartTimecode("Song")}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.000000;${mltProp.getFadeInTimecode("Song")}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.000000;${mltProp.getFadeOutTimecode(ProducerType.SPLASHSTART)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.000000;${mltProp.getEndTimecode(ProducerType.SPLASHSTART)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.000000")
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
        val chordDescriptionText = mltProp.getSongChordDescription().replace("\n",", ")

        var songnameTextFontMlt = Karaoke.splashstartSongNameFont
        var songnameTextFont = songnameTextFontMlt.font
        val songversionTextFontMlt = Karaoke.splashstartSongVersionFont
        val commentTextFontMlt = Karaoke.splashstartCommentFont
        val chordDescriptionTextFontMlt = Karaoke.splashstartChordDescriptionFont

        val commentText = "${songVersion.textForDescription}"
        val songversionText = songVersion.text
        val songnameText = mltProp.getSongName()

        val border = (Karaoke.frameHeightPx *0.05).toLong()
        val pictureScaleCoeff = Karaoke.frameWidthPx / 1920.0

        val albumPictureX = ((Karaoke.frameWidthPx - (400 * pictureScaleCoeff + border + 1000 * pictureScaleCoeff)) / 2).toLong()
        val albumPictureY = border
        val albumPictureW = 400 * pictureScaleCoeff
        val albumPictureH = 400 * pictureScaleCoeff

        val authorPictureX = albumPictureX + 400 * pictureScaleCoeff + border
        val authorPictureY = border
        val authorPictureW = 1000 * pictureScaleCoeff
        val authorPictureH = 400 * pictureScaleCoeff

        val commentTextW =  (getTextWidthHeightPx(commentText, commentTextFontMlt.font).first).toLong()
        val commentTextH =  (getTextWidthHeightPx(commentText, commentTextFontMlt.font).second).toLong()
        val commentTextX = (Karaoke.frameWidthPx - commentTextW) / 2
        val commentTextY = (Karaoke.frameHeightPx - border/2 - getTextWidthHeightPx("0", commentTextFontMlt.font).second).toLong()

        val chordDescriptionTextW =  (getTextWidthHeightPx(chordDescriptionText, chordDescriptionTextFontMlt.font).first).toLong()
        val chordDescriptionTextH =  (getTextWidthHeightPx(chordDescriptionText, chordDescriptionTextFontMlt.font).second).toLong()
        val chordDescriptionTextX = (Karaoke.frameWidthPx - chordDescriptionTextW) / 2
        val chordDescriptionTextY = if (chordDescriptionText != "") (commentTextY - getTextWidthHeightPx("0", chordDescriptionTextFontMlt.font).second).toLong() else commentTextY

        val songversionTextW =  (getTextWidthHeightPx(songversionText, songversionTextFontMlt.font).first).toLong()
        val songversionTextH =  (getTextWidthHeightPx(songversionText, songversionTextFontMlt.font).second).toLong()
        val songversionTextX = (Karaoke.frameWidthPx - songversionTextW) / 2
        val songversionTextY = (chordDescriptionTextY - getTextWidthHeightPx("0", songversionTextFontMlt.font).second).toLong()

        var songnameTextY = (border + 400 * pictureScaleCoeff).toLong()
        val songnameTextHmax = songversionTextY - songnameTextY
        do {
            songnameTextFont = Font(songnameTextFont.name, songnameTextFont.style, songnameTextFont.size+1)
            val (w,h) = getTextWidthHeightPx(songnameText, songnameTextFont)
        } while (!(h > songnameTextHmax || w > (Karaoke.frameWidthPx - 2*border)))
        songnameTextFontMlt.font = songnameTextFont
        val songnameTextW = (getTextWidthHeightPx(songnameText, songnameTextFontMlt.font).first).toLong()
        val songnameTextH = (getTextWidthHeightPx(songnameText, songnameTextFontMlt.font).second).toLong()
        val songnameTextX = (Karaoke.frameWidthPx - songnameTextW) / 2
        songnameTextY = (songversionTextY - songnameTextH - (songversionTextY - songnameTextH - songnameTextY)/2)

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
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","$albumPictureX"),Pair("y","$albumPictureY")),
                        body = mutableListOf(MltNode(name = "transform", body = "${pictureScaleCoeff},0,0,0,${pictureScaleCoeff},0,0,0,1"))),
                    MltNode(name = "content", fields = mutableMapOf(Pair("base64", mltProp.getBase64("LogoAlbum"))))
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
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","$songnameTextX"),Pair("y","$songnameTextY")),
                        body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                    songnameTextFontMlt.mltNode(songnameText)
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
