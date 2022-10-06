import mlt.mltNode
import model.MltNode
import model.ProducerType
import model.SongVersion
import java.awt.Font
import java.util.DoubleSummaryStatistics

fun getMltSplashstartProducer(param: Map<String, Any?>, type:ProducerType = ProducerType.SPLASHSTART, voiceId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "producer",
        fields = mutableMapOf(
            Pair("id","producer_${type.text}${voiceId}"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SPLASHSTART_END_TIMECODE"].toString())
        ),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","length")), body = Karaoke.timeSplashScreenStartMs.toString()),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","eof")), body = "pause"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","resource"))),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","progressive")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","aspect_ratio")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","seekable")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "kdenlivetitle"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:duration")), body = param["SPLASHSTART_END_TIMECODE"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clipname")), body = "${type.text.uppercase()}${if (voiceId==0) "" else voiceId}"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","xmldata")), body = param["${type.text.uppercase()}${voiceId}_XML_DATA"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:folderid")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clip_type")), body = 2),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = (param["${type.text.uppercase()}${voiceId}_ID"] as Int)+voiceId*1000),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","force_reload")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.width")), body = Karaoke.frameWidthPx),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.height")), body = Karaoke.frameHeightPx)
        )
    )

    return mlt
}


fun getMltSplashstartFilePlaylist(param: Map<String, Any?>, type:ProducerType = ProducerType.SPLASHSTART, voiceId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_${type.text}${voiceId}_file")
        ),
        body = mutableListOf(
            MltNode(name = "entry", fields = mutableMapOf(
                Pair("producer","producer_${type.text}${voiceId}"),
                Pair("in",param["SONG_START_TIMECODE"].toString()),
                Pair("out",param["SONG_END_TIMECODE"].toString()),
            ), body = mutableListOf(
                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = (param["${type.text.uppercase()}${voiceId}_ID"] as Int)+voiceId*1000),
                MltNode(name = "filter",
                    fields = mutableMapOf(Pair("id","filter_${type.text}${voiceId}_qtblend")),
                    body = mutableListOf(
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","rotate_center")), body = 1),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "qtblend"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive_id")), body = "qtblend"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","rect")), body = "${param["SONG_START_TIMECODE"]}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.000000;${param["SONG_FADEIN_TIMECODE"]}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.000000;${param["SPLASHSTART_FADEOUT_TIMECODE"]}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.000000;${param["SPLASHSTART_END_TIMECODE"]}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.000000"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","compositing")), body = 0),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","distort")), body = 0),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:collapsed")), body = 0),
                    )
                ),
            ))
        )
    )

    return mlt
}

fun getMltSplashstartTrackPlaylist(param: Map<String, Any?>, type:ProducerType = ProducerType.SPLASHSTART, voiceId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_${type.text}${voiceId}_track")
        )
    )

    return mlt
}

fun getMltSplashstartTractor(param: Map<String, Any?>, type:ProducerType = ProducerType.SPLASHSTART, voiceId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "tractor",
        fields = mutableMapOf(
            Pair("id","tractor_${type.text}${voiceId}"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:trackheight")), body = 69),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:timeline_active")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:collapsed")), body = 28),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:track_name")), body = "${type.text.uppercase()}${if (voiceId==0) "" else voiceId}"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:thumbs_format"))),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:audio_rec"))),
            MltNode(name = "track",
                fields = mutableMapOf(
                    Pair("hide",param["HIDE_TRACTOR_${type.text.uppercase()}${voiceId}"].toString()),
                    Pair("producer","playlist_${type.text}${voiceId}_file"))),
            MltNode(name = "track",
                fields = mutableMapOf(
                    Pair("hide",param["HIDE_TRACTOR_${type.text.uppercase()}${voiceId}"].toString()),
                    Pair("producer","playlist_${type.text}${voiceId}_track"))),

            )
    )

    return mlt
}

fun getTemplateSplashstart(param: Map<String, Any?>): MltNode {

    val songVersion = param["SONG_VERSION"] as SongVersion
    val isBluetoothDelay = param["ID_BLUETOOTH_DELAY"] as Boolean

    var songnameTextFontMlt = Karaoke.splashstartSongNameFont
    var songnameTextFont = songnameTextFontMlt.font
    val songversionTextFontMlt = Karaoke.splashstartSongVersionFont
    val commentTextFontMlt = Karaoke.splashstartCommentFont

    val commentText = if (isBluetoothDelay) "для Bluetooth-колонок, задержка видео ${Karaoke.timeOffsetBluetoothSpeakerMs} ms" else ""
    val songversionText = songVersion.text
    val songnameText = param["SONG_NAME"] as String

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

    val songversionTextW =  (getTextWidthHeightPx(songversionText, songversionTextFontMlt.font).first).toLong()
    val songversionTextH =  (getTextWidthHeightPx(songversionText, songversionTextFontMlt.font).second).toLong()
    val songversionTextX = (Karaoke.frameWidthPx - songversionTextW) / 2
    val songversionTextY = (commentTextY - getTextWidthHeightPx("0", songversionTextFontMlt.font).second).toLong()

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
                MltNode(name = "content", fields = mutableMapOf(Pair("url", param["LOGOAUTHOR_PATH"].toString())))
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
                MltNode(name = "content", fields = mutableMapOf(Pair("url", param["LOGOALBUM_PATH"].toString())))
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