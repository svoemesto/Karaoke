import model.MltNode

fun getMltBeatProducer(param: Map<String, Any?>, id: Long): MltNode {

    val mlt = MltNode(
        name = "producer",
        fields = mutableMapOf(
            Pair("id","producer_beat${id}"),
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
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clipname")), body = "BEAT${id}"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","xmldata")), body = param["BEAT${id}_XML_DATA"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:folderid")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clip_type")), body = 2),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = param["BEAT${id}_ID"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","force_reload")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.width")), body = param["FRAME_WIDTH_PX"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.height")), body = param["FRAME_HEIGHT_PX"])
        )
    )

    return mlt
}


fun getMltBeatFilePlaylist(param: Map<String, Any?>, id: Long): MltNode {

    val mlt = MltNode(
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_beat${id}_file")
        ),
        body = mutableListOf(
            MltNode(name = "entry", fields = mutableMapOf(
                Pair("producer","producer_beat${id}"),
                Pair("in",param["SONG_START_TIMECODE"].toString()),
                Pair("out",param["SONG_END_TIMECODE"].toString()),
            ), body = mutableListOf(
                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = param["BEAT${id}_ID"]),
                MltNode(name = "filter",
                    fields = mutableMapOf(Pair("id","filter_beat${id}_qtblend")),
                    body = mutableListOf(
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","rotate_center")), body = 1),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "qtblend"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive_id")), body = "qtblend"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","rect")), body = param["BEAT${id}_PROPERTY_RECT"].toString()),
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

fun getMltBeatTrackPlaylist(param: Map<String, Any?>, id: Long): MltNode {

    val mlt = MltNode(
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_beat${id}_track")
        )
    )

    return mlt
}

fun getMltBeatTractor(param: Map<String, Any?>, id: Long): MltNode {

    val mlt = MltNode(
        name = "tractor",
        fields = mutableMapOf(
            Pair("id","tractor_beat${id}"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:trackheight")), body = 69),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:timeline_active")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:collapsed")), body = 28),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:track_name")), body = "BEAT${id}"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:thumbs_format"))),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:audio_rec"))),
            MltNode(name = "track",
                fields = mutableMapOf(
                    Pair("hide",param["HIDE_TRACTOR_BEAT${id}"].toString()),
                    Pair("producer","playlist_beat${id}_file"))),
            MltNode(name = "track",
                fields = mutableMapOf(
                    Pair("hide",param["HIDE_TRACTOR_BEAT${id}"].toString()),
                    Pair("producer","playlist_beat${id}_track"))),

            )
    )

    return mlt
}

fun getTemplateBeat1(param: Map<String, Any?>): MltNode {
    return MltNode(
        name = "kdenlivetitle",
        fields = mutableMapOf(
            Pair("duration","0"),
            Pair("LC_NUMERIC","C"),
            Pair("width","${param["FRAME_WIDTH_PX"]}"),
            Pair("height","${param["FRAME_HEIGHT_PX"]}"),
            Pair("out","0"),
        ), body = mutableListOf(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsRectItem"),
                    Pair("z-index","0"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","860"),Pair("y","180")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                    MltNode(name = "content", fields = mutableMapOf(
                        Pair("brushcolor","0,255,0,255"),
                        Pair("pencolor","0,0,0,255"),
                        Pair("penwidth","0"),
                        Pair("rect","0,0,200,50"))
                    )
                )
            ),
            MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${param["FRAME_WIDTH_PX"]},${param["FRAME_HEIGHT_PX"]}"))),
            MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${param["FRAME_WIDTH_PX"]},${param["FRAME_HEIGHT_PX"]}"))),
            MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
        )
    )
}

fun getTemplateBeat2(param: Map<String, Any?>): MltNode {
    return MltNode(
        name = "kdenlivetitle",
        fields = mutableMapOf(
            Pair("duration","0"),
            Pair("LC_NUMERIC","C"),
            Pair("width","${param["FRAME_WIDTH_PX"]}"),
            Pair("height","${param["FRAME_HEIGHT_PX"]}"),
            Pair("out","0"),
        ), body = mutableListOf(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsRectItem"),
                    Pair("z-index","0"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","910"),Pair("y","180")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                    MltNode(name = "content", fields = mutableMapOf(
                        Pair("brushcolor","0,255,0,255"),
                        Pair("pencolor","0,0,0,255"),
                        Pair("penwidth","0"),
                        Pair("rect","0,0,150,50"))
                    )
                )
            ),
            MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${param["FRAME_WIDTH_PX"]},${param["FRAME_HEIGHT_PX"]}"))),
            MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${param["FRAME_WIDTH_PX"]},${param["FRAME_HEIGHT_PX"]}"))),
            MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
        )
    )
}

fun getTemplateBeat3(param: Map<String, Any?>): MltNode {
    return MltNode(
        name = "kdenlivetitle",
        fields = mutableMapOf(
            Pair("duration","0"),
            Pair("LC_NUMERIC","C"),
            Pair("width","${param["FRAME_WIDTH_PX"]}"),
            Pair("height","${param["FRAME_HEIGHT_PX"]}"),
            Pair("out","0"),
        ), body = mutableListOf(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsRectItem"),
                    Pair("z-index","0"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","960"),Pair("y","180")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                    MltNode(name = "content", fields = mutableMapOf(
                        Pair("brushcolor","0,255,0,255"),
                        Pair("pencolor","0,0,0,255"),
                        Pair("penwidth","0"),
                        Pair("rect","0,0,100,50"))
                    )
                )
            ),
            MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${param["FRAME_WIDTH_PX"]},${param["FRAME_HEIGHT_PX"]}"))),
            MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${param["FRAME_WIDTH_PX"]},${param["FRAME_HEIGHT_PX"]}"))),
            MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
        )
    )
}

fun getTemplateBeat4(param: Map<String, Any?>): MltNode {
    return MltNode(
        name = "kdenlivetitle",
        fields = mutableMapOf(
            Pair("duration","0"),
            Pair("LC_NUMERIC","C"),
            Pair("width","${param["FRAME_WIDTH_PX"]}"),
            Pair("height","${param["FRAME_HEIGHT_PX"]}"),
            Pair("out","0"),
        ), body = mutableListOf(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsRectItem"),
                    Pair("z-index","0"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","1010"),Pair("y","180")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                    MltNode(name = "content", fields = mutableMapOf(
                        Pair("brushcolor","0,255,0,255"),
                        Pair("pencolor","0,0,0,255"),
                        Pair("penwidth","0"),
                        Pair("rect","0,0,50,50"))
                    )
                )
            ),
            MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${param["FRAME_WIDTH_PX"]},${param["FRAME_HEIGHT_PX"]}"))),
            MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${param["FRAME_WIDTH_PX"]},${param["FRAME_HEIGHT_PX"]}"))),
            MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
        )
    )
}