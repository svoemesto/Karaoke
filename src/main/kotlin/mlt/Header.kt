import mlt.mltNode
import model.MltNode
import model.ProducerType
import java.awt.Font
import java.util.DoubleSummaryStatistics

fun getMltHeaderProducer(param: Map<String, Any?>, type:ProducerType = ProducerType.HEADER, voiceId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "producer",
        fields = mutableMapOf(
            Pair("id","producer_${type.text}${voiceId}"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = mutableListOf(
            MltNode(
                name = "filter",
                fields = mutableMapOf(Pair("id","filter_${type.text}${voiceId}_qtblend1")),
                body = mutableListOf(
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","rotate_center")), body = 1),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "qtblend"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive_id")), body = "qtblend"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","rect")), body = param["${type.text.uppercase()}${voiceId}_PROPERTY_RECT"]),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","compositing")), body = 0),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","distort")), body = 0),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:collapsed")), body = 0)
                )),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","length")), body = param["SONG_LENGTH_MS"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","eof")), body = "pause"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","resource"))),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","progressive")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","aspect_ratio")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","seekable")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "kdenlivetitle"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:duration")), body = param["SONG_END_TIMECODE"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clipname")), body = "${type.text.uppercase()}${if (voiceId==0) "" else voiceId}"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","xmldata")), body = param["${type.text.uppercase()}${voiceId}_XML_DATA"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:folderid")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clip_type")), body = 2),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = (param["${type.text.uppercase()}${voiceId}_ID"] as Int)+voiceId*100),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","force_reload")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.width")), body = Karaoke.frameWidthPx),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.height")), body = Karaoke.frameHeightPx)
        )
    )

    return mlt
}


fun getMltHeaderFilePlaylist(param: Map<String, Any?>, type:ProducerType = ProducerType.HEADER, voiceId: Int = 0): MltNode {

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
                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = (param["${type.text.uppercase()}${voiceId}_ID"] as Int)+voiceId*100),
                MltNode(name = "filter",
                    fields = mutableMapOf(Pair("id","filter_${type.text}${voiceId}_qtblend")),
                    body = mutableListOf(
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","rotate_center")), body = 1),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "qtblend"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive_id")), body = "qtblend"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","rect")), body = "${param["SONG_START_TIMECODE"]}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.000000;${param["SONG_FADEIN_TIMECODE"]}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.000000;${param["SONG_FADEOUT_TIMECODE"]}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.000000;${param["SONG_END_TIMECODE"]}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.000000"),
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

fun getMltHeaderTrackPlaylist(param: Map<String, Any?>, type:ProducerType = ProducerType.HEADER, voiceId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_${type.text}${voiceId}_track")
        )
    )

    return mlt
}

fun getMltHeaderTractor(param: Map<String, Any?>, type:ProducerType = ProducerType.HEADER, voiceId: Int = 0): MltNode {

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

fun getTemplateHeader(param: Map<String, Any?>): MltNode {

    val offsetX = Karaoke.songtextStartPositionXpx
    val maxSongnameW = Karaoke.headerSongnameMaxX - offsetX
    val songnameNameMltFont = Karaoke.headerSongnameFont

    var (songnameW, songnameH1) = getTextWidthHeightPx(param["HEADER_SONG_NAME"].toString(), songnameNameMltFont.font)
    val (authorW, authorH) = getTextWidthHeightPx(Karaoke.headerAuthorName, Karaoke.headerAuthorFont.font)
    val (albumW, albumH) = getTextWidthHeightPx(Karaoke.headerAlbumName, Karaoke.headerAlbumFont.font)
    val (toneW, toneH) = getTextWidthHeightPx(Karaoke.headerToneName, Karaoke.headerToneFont.font)
    val (bpmW,bpmH) = getTextWidthHeightPx(Karaoke.headerBpmName, Karaoke.headerBpmFont.font)
    var songnameH: Double = songnameH1

    while (songnameW > maxSongnameW) {
        songnameNameMltFont.font = Font(songnameNameMltFont.font.name, songnameNameMltFont.font.style, songnameNameMltFont.font.size -1)
        songnameW = getTextWidthHeightPx(param["HEADER_SONG_NAME"].toString(), songnameNameMltFont.font).first
        songnameH = getTextWidthHeightPx(param["HEADER_SONG_NAME"].toString(), songnameNameMltFont.font).second
    }

    val songnameY = songnameH1 - songnameH
    val authorY = songnameY + (getTextWidthHeightPx(param["HEADER_SONG_NAME"].toString(), Karaoke.headerSongnameFont.font).second).toLong()
    val albumY = authorY + (getTextWidthHeightPx(param["HEADER_AUTHOR"].toString(), Karaoke.headerAuthorFont.font).second).toLong()
    val toneY = albumY + (getTextWidthHeightPx(param["HEADER_ALBUM"].toString(), Karaoke.headerAlbumFont.font).second).toLong()
    val bpmY = toneY + (getTextWidthHeightPx(param["HEADER_TONE"].toString(), Karaoke.headerToneFont.font).second).toLong()


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
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","${(Karaoke.frameWidthPx * 0.6385).toLong()}"),Pair("y","36")), body = mutableListOf(MltNode(name = "transform", body = "${Karaoke.frameWidthPx*0.00025},0,0,0,${Karaoke.frameWidthPx*0.00025},0,0,0,1"))),
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
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","${(Karaoke.frameWidthPx * 0.8927).toLong()}"),Pair("y","36")), body = mutableListOf(MltNode(name = "transform", body = "${Karaoke.frameWidthPx*0.00025},0,0,0,${Karaoke.frameWidthPx*0.00025},0,0,0,1"))),
                    MltNode(name = "content", fields = mutableMapOf(Pair("url", param["LOGOALBUM_PATH"].toString())))
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
                MltNode(name = "position", fields = mutableMapOf(Pair("x","$offsetX"),Pair("y","${songnameY.toLong()}")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                songnameNameMltFont.mltNode(param["HEADER_SONG_NAME"].toString())
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
                MltNode(name = "position", fields = mutableMapOf(Pair("x","${authorX.toLong()}"),Pair("y","${authorY.toLong()}")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
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
                MltNode(name = "position", fields = mutableMapOf(Pair("x","${valueX.toLong()}"),Pair("y","${authorY.toLong()}")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                Karaoke.headerAuthorFont.mltNode(param["HEADER_AUTHOR"].toString())
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
                MltNode(name = "position", fields = mutableMapOf(Pair("x","${albumX.toLong()}"),Pair("y","${albumY.toLong()}")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
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
                MltNode(name = "position", fields = mutableMapOf(Pair("x","${valueX.toLong()}"),Pair("y","${albumY.toLong()}")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                Karaoke.headerAlbumFont.mltNode(param["HEADER_ALBUM"].toString())
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
                MltNode(name = "position", fields = mutableMapOf(Pair("x","${toneX.toLong()}"),Pair("y","${toneY.toLong()}")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
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
                MltNode(name = "position", fields = mutableMapOf(Pair("x","${valueX.toLong()}"),Pair("y","${toneY.toLong()}")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                Karaoke.headerToneFont.mltNode(param["HEADER_TONE"].toString())
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
                MltNode(name = "position", fields = mutableMapOf(Pair("x","${bpmX.toLong()}"),Pair("y","${bpmY.toLong()}")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
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
                MltNode(name = "position", fields = mutableMapOf(Pair("x","${valueX.toLong()}"),Pair("y","${bpmY.toLong()}")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                Karaoke.headerBpmFont.mltNode("${param["HEADER_BPM"].toString()} bpm")
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
                MltNode(name = "position", fields = mutableMapOf(Pair("x","0"),Pair("y","0")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
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
                MltNode(name = "position", fields = mutableMapOf(Pair("x","0"),Pair("y","246")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
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