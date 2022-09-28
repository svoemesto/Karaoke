import model.MltNode

fun getMltHeaderProducer(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "producer",
        fields = mutableMapOf(
            Pair("id","producer_header"),
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
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clipname")), body = "HEADER"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","xmldata")), body = param["HEADER_XML_DATA"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:folderid")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clip_type")), body = 2),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = param["HEADER_ID"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","force_reload")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.width")), body = param["FRAME_WIDTH_PX"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.height")), body = param["FRAME_HEIGHT_PX"])
        )
    )

    return mlt
}


fun getMltHeaderFilePlaylist(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_header_file")
        ),
        body = mutableListOf(
            MltNode(name = "entry", fields = mutableMapOf(
                Pair("producer","producer_header"),
                Pair("in",param["SONG_START_TIMECODE"].toString()),
                Pair("out",param["SONG_END_TIMECODE"].toString()),
            ), body = mutableListOf(
                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = param["HEADER_ID"]),
                MltNode(name = "filter",
                    fields = mutableMapOf(Pair("id","filter_header_qtblend")),
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

fun getMltHeaderTrackPlaylist(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_header_track")
        )
    )

    return mlt
}

fun getMltHeaderTractor(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "tractor",
        fields = mutableMapOf(
            Pair("id","tractor_header"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:trackheight")), body = 69),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:timeline_active")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:collapsed")), body = 28),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:track_name")), body = "HEADER"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:thumbs_format"))),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:audio_rec"))),
            MltNode(name = "track",
                fields = mutableMapOf(
                    Pair("hide",param["HIDE_TRACTOR_HEADER"].toString()),
                    Pair("producer","playlist_header_file"))),
            MltNode(name = "track",
                fields = mutableMapOf(
                    Pair("hide",param["HIDE_TRACTOR_HEADER"].toString()),
                    Pair("producer","playlist_header_track"))),

            )
    )

    return mlt
}

fun getTemplateHeader(param: Map<String, Any?>): MltNode {
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
                    Pair("z-index","6"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","96"),Pair("y","96")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                    MltNode(name = "content", fields = mutableMapOf(
                        Pair("line-spacing","0"),
                        Pair("shadow","1;#64000000;3;3;3"),
                        Pair("font-underline","0"),
                        Pair("box-height","40"),
                        Pair("font","${param["FONT_NAME"]}"),
                        Pair("letter-spacing","0"),
                        Pair("font-pixel-size","30"),
                        Pair("font-italic","0"),
                        Pair("typewriter","0;2;1;0;0"),
                        Pair("alignment","1"),
                        Pair("font-weight","50"),
                        Pair("box-width","233.797"),
                        Pair("font-color","85,255,255,255")
                    ), body = param["HEADER_AUTHOR"]
                    )
                )
            ),
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsTextItem"),
                    Pair("z-index","6"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","223"),Pair("y","201")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                    MltNode(name = "content", fields = mutableMapOf(
                        Pair("line-spacing","0"),
                        Pair("shadow","1;#64000000;3;3;3"),
                        Pair("font-underline","0"),
                        Pair("box-height","40"),
                        Pair("font","${param["FONT_NAME"]}"),
                        Pair("letter-spacing","0"),
                        Pair("font-pixel-size","30"),
                        Pair("font-italic","0"),
                        Pair("typewriter","0;2;1;0;0"),
                        Pair("alignment","1"),
                        Pair("font-weight","50"),
                        Pair("box-width","233.797"),
                        Pair("font-color","85,255,255,255")
                    ), body = param["HEADER_BPM"]
                    )
                )
            ),
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsTextItem"),
                    Pair("z-index","6"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","96"),Pair("y","169")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                    MltNode(name = "content", fields = mutableMapOf(
                        Pair("line-spacing","0"),
                        Pair("shadow","1;#64000000;3;3;3"),
                        Pair("font-underline","0"),
                        Pair("box-height","40"),
                        Pair("font","${param["FONT_NAME"]}"),
                        Pair("letter-spacing","0"),
                        Pair("font-pixel-size","30"),
                        Pair("font-italic","0"),
                        Pair("typewriter","0;2;1;0;0"),
                        Pair("alignment","1"),
                        Pair("font-weight","50"),
                        Pair("box-width","359.688"),
                        Pair("font-color","85,255,255,255")
                    ), body = param["HEADER_TONE"]
                    )
                )
            ),
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsTextItem"),
                    Pair("z-index","6"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","185"),Pair("y","132")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                    MltNode(name = "content", fields = mutableMapOf(
                        Pair("line-spacing","0"),
                        Pair("shadow","1;#64000000;3;3;3"),
                        Pair("font-underline","0"),
                        Pair("box-height","40"),
                        Pair("font","${param["FONT_NAME"]}"),
                        Pair("letter-spacing","0"),
                        Pair("font-pixel-size","30"),
                        Pair("font-italic","0"),
                        Pair("typewriter","0;2;1;0;0"),
                        Pair("alignment","1"),
                        Pair("font-weight","50"),
                        Pair("box-width","395.656"),
                        Pair("font-color","85,255,255,255")
                    ), body = param["HEADER_ALBUM"]
                    )
                )
            ),
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsTextItem"),
                    Pair("z-index","6"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","96"),Pair("y","0")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                    MltNode(name = "content", fields = mutableMapOf(
                        Pair("line-spacing","0"),
                        Pair("shadow","1;#64000000;3;3;3"),
                        Pair("font-underline","0"),
                        Pair("box-height","106"),
                        Pair("font","${param["FONT_NAME"]}"),
                        Pair("letter-spacing","0"),
                        Pair("font-pixel-size","${param["HEADER_SONG_NAME_FONT_SIZE"]}"),
                        Pair("font-italic","0"),
                        Pair("typewriter","0;2;1;0;0"),
                        Pair("alignment","1"),
                        Pair("font-weight","50"),
                        Pair("box-width","818.734"),
                        Pair("font-color","255,255,127,255")
                    ), body = param["HEADER_SONG_NAME"]
                    )
                )
            ),
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
                        Pair("rect","0,0,${param["FRAME_WIDTH_PX"]},246"))
                    )
                )
            ),
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
                        Pair("rect","0,0,${param["FRAME_WIDTH_PX"]},246"),
                        Pair("gradient","#ff000000;#00bf4040;0;100;90"))
                    )
                )
            ),
            MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${param["FRAME_WIDTH_PX"]},${param["FRAME_HEIGHT_PX"]}"))),
            MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${param["FRAME_WIDTH_PX"]},${param["FRAME_HEIGHT_PX"]}"))),
            MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
        )
    )
}