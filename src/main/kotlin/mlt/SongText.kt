import model.LyricLine
import model.MltNode
import model.ProducerType

fun getMltSongTextProducer(param: Map<String, Any?>, type:ProducerType = ProducerType.SONGTEXT, groupId: Int = 0): MltNode {

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
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.height")), body = param["${type.text.uppercase()}_WORK_AREA_HEIGHT_PX"])
        )
    )

    return mlt
}


fun getMltSongTextFilePlaylist(param: Map<String, Any?>, type:ProducerType = ProducerType.SONGTEXT, groupId: Int = 0): MltNode {

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
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","rect")), body = param["${type.text.uppercase()}_PROPERTY_RECT"].toString()),
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

fun getMltSongTextTrackPlaylist(param: Map<String, Any?>, type:ProducerType = ProducerType.SONGTEXT, groupId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_${type.text}${groupId}_track")
        )
    )

    return mlt
}

fun getMltSongTextTractor(param: Map<String, Any?>, type:ProducerType = ProducerType.SONGTEXT, groupId: Int = 0): MltNode {

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
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:track_name")), body = "SONG_TEXT"),
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

fun getTemplateSongText(param: Map<String, Any?>): MltNode {

    val templateSongTextGroup = mutableListOf<MltNode>()
    for (indexGroup in 0L until (param["MAX_GROUPS"] as Long)) {
        templateSongTextGroup.add(
            MltNode( //<item type="QGraphicsTextItem" z-index="0">
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsTextItem"),
                    Pair("z-index","0"),
                ),
                body = mutableListOf(
                    MltNode( // <position x="$TITLE_POSITION_START_X_PX" y="$TITLE_POSITION_START_Y_PX">
                        name = "position",
                        fields = mutableMapOf(
                            Pair("x","${param["TITLE_POSITION_START_X_PX"]}"),
                            Pair("y","${param["TITLE_POSITION_START_Y_PX"]}")
                        ),
                        body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(), body = "1,0,0,0,1,0,0,0,1")) //<transform>1,0,0,0,1,0,0,0,1</transform>
                    ),
                    MltNode(
                        name = "content",
                        fields = mutableMapOf(
                            Pair("line-spacing","${param["LINE_SPACING"]}"),             // line-spacing="$LINE_SPACING"
                            Pair("shadow", "${param["SHADOW"]}"),                        // shadow="$SHADOW"
                            Pair("font-underline","${param["FONT_UNDERLINE"]}"),         // font-underline="$FONT_UNDERLINE"
                            Pair("box-height","${param["BOX_HEIGHT_PX"]}"),              // box-height="$boxHeightPx"
                            Pair("font", "${param["FONT_NAME"]}"),                       // font="$FONT_NAME"
                            Pair("letter-spacing","0"),                 // letter-spacing="0"
                            Pair("font-pixel-size","${param["FONT_SIZE_PT"]}"),      // font-pixel-size="$fontSizePt"
                            Pair("font-italic","${param["FONT_ITALIC"]}"),         // font-italic="$FONT_ITALIC"
                            Pair("typewriter", "${param["TYPEWRITER"]}"),             // typewriter="$TYPEWRITER"
                            Pair("alignment","${param["ALIGNMENT"]}"),             // alignment="$ALIGNMENT"
                            Pair("font-weight","${param["FONT_WEIGHT"]}"),          // font-weight="$FONT_WEIGHT"
                            Pair("box-width","${param["BOX_WIDTH_PX"]}"),            // box-width="$boxWidthPx"
                            Pair("font-color","${(param["GROUPS_FONT_COLORS_TEXT"] as Map<*, *>)[indexGroup]}"), // font-color="${GROUPS_FONT_COLORS_TEXT[indexGroup]}"
                        ),
                        body = "${(param["LYRIC_LINES_FULL_TEXT_GROUPS"] as MutableMap<Long, MutableList<LyricLine>>)[indexGroup]?.joinToString("\n") { it.text }}" // ${resultLyricLinesFullTextGroups[indexGroup]?.map { it.text }?.joinToString("\n")}
                    )
                )
            )
        )
        templateSongTextGroup.add(
            MltNode( // <item type="QGraphicsTextItem" z-index="0">
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsTextItem"),
                    Pair("z-index","0"),
                ),
                body = mutableListOf(
                    MltNode( // <position x="$TITLE_POSITION_START_X_PX" y="$TITLE_POSITION_START_Y_PX">
                        name = "position",
                        fields = mutableMapOf(
                            Pair("x","${param["TITLE_POSITION_START_X_PX"]}"),
                            Pair("y","${param["TITLE_POSITION_START_Y_PX"]}")
                        ),
                        body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(), body = "1,0,0,0,1,0,0,0,1")) // <transform>1,0,0,0,1,0,0,0,1</transform>
                    ),
                    MltNode(
                        name = "content",
                        fields = mutableMapOf(
                            Pair("line-spacing","${param["LINE_SPACING"]}"),           // line-spacing="$LINE_SPACING"
                            Pair("shadow", "${param["SHADOW"]}"),                         // shadow="$SHADOW"
                            Pair("font-underline","${param["FONT_UNDERLINE"]}"),       // font-underline="$FONT_UNDERLINE"
                            Pair("box-height","${param["BOX_HEIGHT_PX"]}"),              // box-height="$boxHeightPx"
                            Pair("font", "${param["FONT_NAME"]}"),                        // font="$FONT_NAME"
                            Pair("letter-spacing","0"),                     // letter-spacing="0"
                            Pair("font-pixel-size","${param["FONT_SIZE_PT"]}"),          // font-pixel-size="$fontSizePt"
                            Pair("font-italic","${param["FONT_ITALIC"]}"),             // font-italic="$FONT_ITALIC"
                            Pair("typewriter", "${param["TYPEWRITER"]}"),                 // typewriter="$TYPEWRITER"
                            Pair("alignment","${param["ALIGNMENT"]}"),                 // alignment="$ALIGNMENT"
                            Pair("font-weight","${param["FONT_WEIGHT"]}"),              // font-weight="$FONT_WEIGHT"
                            Pair("box-width","${param["BOX_WIDTH_PX"]}"),                // box-width="$boxWidthPx"
                            Pair("font-color","${(param["GROUPS_FONT_COLORS_BEAT"] as Map<*, *>)[indexGroup]}"), // font-color="${GROUPS_FONT_COLORS_BEAT[indexGroup]}"
                        ),
                        body = "${(param["LYRIC_LINES_BEAT_TEXT_GROUPS"] as MutableMap<Long, MutableList<LyricLine>>)[indexGroup]?.joinToString("\n") { it.text }}" // ${resultLyricLinesBeatTextGroups[indexGroup]?.map { it.text }?.joinToString("\n")}
                    )
                )
            )
        )
    }


    val templateSongText = MltNode(
        name = "kdenlivetitle",
        fields = mutableMapOf(
            Pair("duration","0"),
            Pair("LC_NUMERIC","C"),
            Pair("width","${param["FRAME_WIDTH_PX"]}"),
            Pair("height","${param["WORK_AREA_HEIGHT_PX"]}"),
            Pair("out","0"),
        ),
        body = mutableListOf(
            templateSongTextGroup,
            MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${param["FRAME_WIDTH_PX"]},${param["WORK_AREA_HEIGHT_PX"]}"))),
            MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${param["FRAME_WIDTH_PX"]},${param["WORK_AREA_HEIGHT_PX"]}"))),
            MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
        )
    )

    return templateSongText
}