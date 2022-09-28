import model.LyricLine
import model.MltNode
import model.ProducerType

fun getMltHorizonProducer(param: Map<String, Any?>, type:ProducerType = ProducerType.HORIZON, groupId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "producer",
        fields = mutableMapOf(
            Pair("id","producer_${type.text}${groupId}"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","length")), body = param["SONG_LENGTH_MS"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","eof")), body = "pause"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","resource"))),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","progressive")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","aspect_ratio")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","seekable")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "kdenlivetitle"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:duration")), body = param["SONG_END_TIMECODE"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clipname")), body = "${type.text.uppercase()}${if (groupId==0) "" else groupId}"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","xmldata")), body = param["${type.text.uppercase()}_XML_DATA"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:folderid")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clip_type")), body = 2),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = (param["${type.text.uppercase()}_ID"] as Int)+groupId*100),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","force_reload")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.width")), body = param["FRAME_WIDTH_PX"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.height")), body = param["FRAME_HEIGHT_PX"])
        )
    )

    return mlt
}


fun getMltHorizonFilePlaylist(param: Map<String, Any?>, type:ProducerType = ProducerType.HORIZON, groupId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_${type.text}${groupId}_file")
        ),
        body = mutableListOf(
            MltNode(name = "entry", fields = mutableMapOf(
                Pair("producer","producer_${type.text}${groupId}"),
                Pair("in",param["SONG_START_TIMECODE"].toString()),
                Pair("out",param["SONG_END_TIMECODE"].toString()),
            ), body = mutableListOf(
                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = (param["${type.text.uppercase()}_ID"] as Int)+groupId*100),
                MltNode(name = "filter",
                    fields = mutableMapOf(Pair("id","filter_${type.text}${groupId}_qtblend")),
                    body = mutableListOf(
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","rotate_center")), body = 1),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "qtblend"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive_id")), body = "qtblend"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","rect")), body = "${param["SONG_START_TIMECODE"].toString()}=0 0 ${param["FRAME_WIDTH_PX"].toString()} ${param["FRAME_HEIGHT_PX"].toString()} 0.000000;${param["SONG_FADEIN_TIMECODE"].toString()}=0 0 ${param["FRAME_WIDTH_PX"].toString()} ${param["FRAME_HEIGHT_PX"].toString()} 1.000000;${param["SONG_FADEOUT_TIMECODE"].toString()}=0 0 ${param["FRAME_WIDTH_PX"].toString()} ${param["FRAME_HEIGHT_PX"].toString()} 1.000000;${param["SONG_END_TIMECODE"].toString()}=0 0 ${param["FRAME_WIDTH_PX"].toString()} ${param["FRAME_HEIGHT_PX"].toString()} 0.000000"),
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

fun getMltHorizonTrackPlaylist(param: Map<String, Any?>, type:ProducerType = ProducerType.HORIZON, groupId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_${type.text}${groupId}_track")
        )
    )

    return mlt
}

fun getMltHorizonTractor(param: Map<String, Any?>, type:ProducerType = ProducerType.HORIZON, groupId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "tractor",
        fields = mutableMapOf(
            Pair("id","tractor_${type.text}${groupId}"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:trackheight")), body = 69),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:timeline_active")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:collapsed")), body = 28),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:track_name")), body = "${type.text.uppercase()}${if (groupId==0) "" else groupId}"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:thumbs_format"))),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:audio_rec"))),
            MltNode(name = "track",
                fields = mutableMapOf(
                    Pair("hide",param["HIDE_TRACTOR_${type.text.uppercase()}"].toString()),
                    Pair("producer","playlist_${type.text}${groupId}_file"))),
            MltNode(name = "track",
                fields = mutableMapOf(
                    Pair("hide",param["HIDE_TRACTOR_${type.text.uppercase()}"].toString()),
                    Pair("producer","playlist_${type.text}${groupId}_track"))),

            )
    )

    return mlt
}

fun getTemplateHorizon(param: Map<String, Any?>): MltNode {

    val templateHorizonGroup = mutableListOf<MltNode>()

    (param["LYRIC_LINES_FULL_TEXT"] as MutableList<LyricLine>).forEach { lyricLine ->
        if (!lyricLine.isEmptyLine) {
            val lineStartMs = convertTimecodeToMilliseconds(lyricLine.start)
            val lineEndMs = convertTimecodeToMilliseconds(lyricLine.end)
            val lineX = ((lineStartMs.toDouble() / (param["SONG_LENGTH_MS"] as Long)) * (param["FRAME_WIDTH_PX"] as Long)).toLong()
            val lineW = ((lineEndMs.toDouble() / (param["SONG_LENGTH_MS"] as Long)) * (param["FRAME_WIDTH_PX"] as Long)).toLong() - lineX

            templateHorizonGroup.add(
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
                                Pair("y","${param["HORIZON_POSITION_PX"]}"),
                            ),
                            body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(Pair("zoom","100")), body = "1,0,0,0,1,0,0,0,1"))
                        ),
                        MltNode(
                            name = "content",
                            fields = mutableMapOf(
                                Pair("brushcolor","${(param["GROUPS_TIMELINE_COLORS"] as Map<Long, String>)[lyricLine.subtitles.first().group]}"),
                                Pair("pencolor","0,0,0,255"),
                                Pair("penwidth","0"),
                                Pair("rect","$lineX,0,$lineW,3")
                            )
                        )
                    )
                )
            )
        }

    }

    val templateHorizon = MltNode(
        type = ProducerType.HORIZON,
        name = "kdenlivetitle",
        fields = mutableMapOf(
            Pair("duration","0"),
            Pair("LC_NUMERIC","C"),
            Pair("width","${param["FRAME_WIDTH_PX"]}"),
            Pair("height","${param["FRAME_HEIGHT_PX"]}"),
            Pair("out","0"),
        ),
        body = mutableListOf(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsRectItem"),
                    Pair("index","0"),
                ),
                body = mutableListOf(
                    MltNode(
                        name = "position",
                        fields = mutableMapOf(
                            Pair("x","0"),
                            Pair("y","${param["HORIZON_POSITION_PX"]}")
                        ),
                        body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(Pair("zoom","100")), body = "1,0,0,0,1,0,0,0,1"))
                    ),
                    MltNode(
                        name = "content",
                        fields = mutableMapOf(
                            Pair("brushcolor","${(param["GROUPS_TIMELINE_COLORS"] as Map<Long, String>)[-1]}"),
                            Pair("pencolor", "0,0,0,255"),
                            Pair("penwidth","0"),
                            Pair("rect","0,0,${param["FRAME_WIDTH_PX"]},3")
                        )
                    )
                )
            ),
            templateHorizonGroup,
            MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${param["FRAME_WIDTH_PX"]},${param["FRAME_HEIGHT_PX"]}"))),
            MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${param["FRAME_WIDTH_PX"]},${param["FRAME_HEIGHT_PX"]}"))),
            MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
        )
    )

    return templateHorizon
}