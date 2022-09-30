import model.MltNode
import model.ProducerType

fun getMltCounterProducer(param: Map<String, Any?>, id: Long, type:ProducerType = ProducerType.COUNTER, groupId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "producer",
        fields = mutableMapOf(
            Pair("id","producer_${type.text}${groupId}${id}"),
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
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clipname")), body = "${type.text.uppercase()}${if (groupId==0) "" else groupId}${id}"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","xmldata")), body = param["${type.text.uppercase()}${groupId}${id}_XML_DATA"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:folderid")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clip_type")), body = 2),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = (param["${type.text.uppercase()}${groupId}${id}_ID"] as Int)+groupId*100),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","force_reload")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.width")), body = Karaoke.frameWidthPx),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.height")), body = Karaoke.frameHeightPx)
        )
    )

    return mlt
}


fun getMltCounterFilePlaylist(param: Map<String, Any?>, id: Long, type:ProducerType = ProducerType.COUNTER, groupId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_${type.text}${groupId}${id}_file")
        ),
        body = mutableListOf(
            MltNode(name = "entry", fields = mutableMapOf(
                Pair("producer","producer_${type.text}${groupId}${id}"),
                Pair("in",param["SONG_START_TIMECODE"].toString()),
                Pair("out",param["SONG_END_TIMECODE"].toString()),
            ), body = mutableListOf(
                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = (param["${type.text.uppercase()}${groupId}${id}_ID"] as Int)+groupId*100),
                MltNode(name = "filter",
                    fields = mutableMapOf(Pair("id","filter_${type.text}${groupId}${id}_qtblend")),
                    body = mutableListOf(
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","rotate_center")), body = 1),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "qtblend"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive_id")), body = "qtblend"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","rect")), body = param["${type.text.uppercase()}${groupId}${id}_PROPERTY_RECT"].toString()),
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

fun getMltCounterTrackPlaylist(param: Map<String, Any?>, id: Long, type:ProducerType = ProducerType.COUNTER, groupId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_${type.text}${groupId}${id}_track")
        )
    )

    return mlt
}

fun getMltCounterTractor(param: Map<String, Any?>, id: Long, type:ProducerType = ProducerType.COUNTER, groupId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "tractor",
        fields = mutableMapOf(
            Pair("id","tractor_${type.text}${groupId}${id}"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:trackheight")), body = 69),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:timeline_active")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:collapsed")), body = 28),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:track_name")), body = "${type.text.uppercase()}${if (groupId==0) "" else groupId}${id}"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:thumbs_format"))),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:audio_rec"))),
            MltNode(name = "track",
                fields = mutableMapOf(
                    Pair("hide",param["HIDE_TRACTOR_${type.text.uppercase()}${groupId}${id}"].toString()),
                    Pair("producer","playlist_${type.text}${groupId}${id}_file"))),
            MltNode(name = "track",
                fields = mutableMapOf(
                    Pair("hide",param["HIDE_TRACTOR_${type.text.uppercase()}${groupId}${id}"].toString()),
                    Pair("producer","playlist_${type.text}${groupId}${id}_track"))),

            )
    )

    return mlt
}

fun getTemplateCounter0(param: Map<String, Any?>): MltNode {
    return MltNode(
        name = "kdenlivetitle",
        fields = mutableMapOf(
            Pair("duration","0"),
            Pair("LC_NUMERIC","C"),
            Pair("width","${Karaoke.frameWidthPx}"),
            Pair("height","${Karaoke.frameHeightPx}"),
            Pair("out","0"),
        ), body = mutableListOf(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsTextItem"),
                    Pair("z-index","0"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","897"),Pair("y","300")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                    MltNode(name = "content", fields = mutableMapOf(
                        Pair("line-spacing","0"),
                        Pair("shadow","1;#64000000;3;3;3"),
                        Pair("font-underline","0"),
                        Pair("box-height","264"),
                        Pair("font","${param["FONT_NAME"]}"),
                        Pair("letter-spacing","0"),
                        Pair("font-pixel-size","200"),
                        Pair("font-italic","0"),
                        Pair("typewriter","0;2;1;0;0"),
                        Pair("alignment","1"),
                        Pair("font-weight","50"),
                        Pair("box-width","120"),
                        Pair("font-color","85,255,0,255")
                    ), body = "GO!"
                    )
                )
            ),
            MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},${Karaoke.frameHeightPx}"))),
            MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},${Karaoke.frameHeightPx}"))),
            MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
        )
    )
}

fun getTemplateCounter1(param: Map<String, Any?>): MltNode {
    return MltNode(
        name = "kdenlivetitle",
        fields = mutableMapOf(
            Pair("duration","0"),
            Pair("LC_NUMERIC","C"),
            Pair("width","${Karaoke.frameWidthPx}"),
            Pair("height","${Karaoke.frameHeightPx}"),
            Pair("out","0"),
        ), body = mutableListOf(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsTextItem"),
                    Pair("z-index","0"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","897"),Pair("y","300")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                    MltNode(name = "content", fields = mutableMapOf(
                        Pair("line-spacing","0"),
                        Pair("shadow","1;#64000000;3;3;3"),
                        Pair("font-underline","0"),
                        Pair("box-height","264"),
                        Pair("font","${param["FONT_NAME"]}"),
                        Pair("letter-spacing","0"),
                        Pair("font-pixel-size","200"),
                        Pair("font-italic","0"),
                        Pair("typewriter","0;2;1;0;0"),
                        Pair("alignment","1"),
                        Pair("font-weight","50"),
                        Pair("box-width","120"),
                        Pair("font-color","255,255,0,255")
                    ), body = "1"
                    )
                )
            ),
            MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},${Karaoke.frameHeightPx}"))),
            MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},${Karaoke.frameHeightPx}"))),
            MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
        )
    )
}

fun getTemplateCounter2(param: Map<String, Any?>): MltNode {
    return MltNode(
        name = "kdenlivetitle",
        fields = mutableMapOf(
            Pair("duration","0"),
            Pair("LC_NUMERIC","C"),
            Pair("width","${Karaoke.frameWidthPx}"),
            Pair("height","${Karaoke.frameHeightPx}"),
            Pair("out","0"),
        ), body = mutableListOf(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsTextItem"),
                    Pair("z-index","0"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","897"),Pair("y","300")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                    MltNode(name = "content", fields = mutableMapOf(
                        Pair("line-spacing","0"),
                        Pair("shadow","1;#64000000;3;3;3"),
                        Pair("font-underline","0"),
                        Pair("box-height","264"),
                        Pair("font","${param["FONT_NAME"]}"),
                        Pair("letter-spacing","0"),
                        Pair("font-pixel-size","200"),
                        Pair("font-italic","0"),
                        Pair("typewriter","0;2;1;0;0"),
                        Pair("alignment","1"),
                        Pair("font-weight","50"),
                        Pair("box-width","120"),
                        Pair("font-color","255,255,0,255")
                    ), body = "2"
                    )
                )
            ),
            MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},${Karaoke.frameHeightPx}"))),
            MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},${Karaoke.frameHeightPx}"))),
            MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
        )
    )
}

fun getTemplateCounter3(param: Map<String, Any?>): MltNode {
    return MltNode(
        name = "kdenlivetitle",
        fields = mutableMapOf(
            Pair("duration","0"),
            Pair("LC_NUMERIC","C"),
            Pair("width","${Karaoke.frameWidthPx}"),
            Pair("height","${Karaoke.frameHeightPx}"),
            Pair("out","0"),
        ), body = mutableListOf(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsTextItem"),
                    Pair("z-index","0"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","897"),Pair("y","300")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                    MltNode(name = "content", fields = mutableMapOf(
                        Pair("line-spacing","0"),
                        Pair("shadow","1;#64000000;3;3;3"),
                        Pair("font-underline","0"),
                        Pair("box-height","264"),
                        Pair("font","${param["FONT_NAME"]}"),
                        Pair("letter-spacing","0"),
                        Pair("font-pixel-size","200"),
                        Pair("font-italic","0"),
                        Pair("typewriter","0;2;1;0;0"),
                        Pair("alignment","1"),
                        Pair("font-weight","50"),
                        Pair("box-width","120"),
                        Pair("font-color","255,0,0,255")
                    ), body = "3"
                    )
                )
            ),
            MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},${Karaoke.frameHeightPx}"))),
            MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},${Karaoke.frameHeightPx}"))),
            MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
        )
    )
}

fun getTemplateCounter4(param: Map<String, Any?>): MltNode {
    return MltNode(
        name = "kdenlivetitle",
        fields = mutableMapOf(
            Pair("duration","0"),
            Pair("LC_NUMERIC","C"),
            Pair("width","${Karaoke.frameWidthPx}"),
            Pair("height","${Karaoke.frameHeightPx}"),
            Pair("out","0"),
        ), body = mutableListOf(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsTextItem"),
                    Pair("z-index","0"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","897"),Pair("y","300")), body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                    MltNode(name = "content", fields = mutableMapOf(
                        Pair("line-spacing","0"),
                        Pair("shadow","1;#64000000;3;3;3"),
                        Pair("font-underline","0"),
                        Pair("box-height","264"),
                        Pair("font","${param["FONT_NAME"]}"),
                        Pair("letter-spacing","0"),
                        Pair("font-pixel-size","200"),
                        Pair("font-italic","0"),
                        Pair("typewriter","0;2;1;0;0"),
                        Pair("alignment","1"),
                        Pair("font-weight","50"),
                        Pair("box-width","120"),
                        Pair("font-color","255,0,0,255")
                    ), body = "4"
                    )
                )
            ),
            MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},${Karaoke.frameHeightPx}"))),
            MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},${Karaoke.frameHeightPx}"))),
            MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
        )
    )
}