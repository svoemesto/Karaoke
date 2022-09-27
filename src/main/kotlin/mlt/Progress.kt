import model.MltNode

fun getMltProgressProducer(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "producer",
        fields = mutableMapOf(
            Pair("id","producer_progress"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = mutableListOf(
            MltNode(
                name = "filter",
                fields = mutableMapOf(Pair("id","karaoke_progress")),
                body = mutableListOf(
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","rotate_center")), body = 1),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "qtblend"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive_id")), body = "qtblend"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","rect")), body = param["PROGRESS_PROPERTY_RECT"]),
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
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clipname")), body = "PROGRESS"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","xmldata")), body = param["PROGRESS_XML_DATA"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:folderid")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clip_type")), body = 2),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = param["PROGRESS_ID"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","force_reload")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.width")), body = param["FRAME_WIDTH_PX"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.height")), body = param["FRAME_HEIGHT_PX"])
        )
    )

    return mlt
}


fun getMltProgressFilePlaylist(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_progress_file")
        ),
        body = mutableListOf(
            MltNode(name = "entry", fields = mutableMapOf(
                Pair("producer","producer_progress"),
                Pair("in",param["SONG_START_TIMECODE"].toString()),
                Pair("out",param["SONG_END_TIMECODE"].toString()),
            ), body = mutableListOf(
                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = param["PROGRESS_ID"]),
                MltNode(name = "filter",
                    fields = mutableMapOf(Pair("id","filter_progress_qtblend")),
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

fun getMltProgressTrackPlaylist(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_progress_track")
        )
    )

    return mlt
}

fun getMltProgressTractor(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "tractor",
        fields = mutableMapOf(
            Pair("id","tractor_progress"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:trackheight")), body = 69),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:timeline_active")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:collapsed")), body = 28),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:track_name")), body = "PROGRESS"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:thumbs_format"))),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:audio_rec"))),
            MltNode(name = "track",
                fields = mutableMapOf(
                    Pair("hide",param["HIDE_TRACTOR_PROGRESS"].toString()),
                    Pair("producer","playlist_progress_file"))),
            MltNode(name = "track",
                fields = mutableMapOf(
                    Pair("hide",param["HIDE_TRACTOR_PROGRESS"].toString()),
                    Pair("producer","playlist_progress_track"))),

            )
    )

    return mlt
}

fun getTemplateProgress(param: Map<String, Any?>): MltNode {

    return MltNode(
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
                    Pair("type","QGraphicsTextItem"),
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
                            Pair("line-spacing","$LINE_SPACING"),
                            Pair("shadow", "0"),
                            Pair("font-underline","0"),
                            Pair("box-height","100"),
                            Pair("font", FONT_NAME),
                            Pair("letter-spacing","0"),
                            Pair("font-pixel-size","${param["FONT_SIZE_PROGRESS"]}"),
                            Pair("font-italic","0"),
                            Pair("typewriter", TYPEWRITER),
                            Pair("alignment","$ALIGNMENT"),
                            Pair("font-weigh","$FONT_WEIGHT"),
                            Pair("box-width","10"),
                            Pair("font-color", PROGRESS_COLOR),
                        ),
                        body = "▲"
                    )
                )
            ),
            MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"))),
            MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"))),
            MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
        )
    )
}