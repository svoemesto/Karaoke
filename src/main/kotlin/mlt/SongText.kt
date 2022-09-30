import model.SongVoiceLine
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
            MltNode(name = "property", fields = mutableMapOf(Pair("name","xmldata")), body = param["${type.text.uppercase()}${groupId}_XML_DATA"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:folderid")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clip_type")), body = 2),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = (param["${type.text.uppercase()}${groupId}_ID"] as Int)+groupId*100),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","force_reload")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.width")), body = Karaoke.frameWidthPx),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.height")), body = param["${type.text.uppercase()}${groupId}_WORK_AREA_HEIGHT_PX"])
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
                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = (param["${type.text.uppercase()}${groupId}_ID"] as Int)+groupId*100),
                MltNode(name = "filter",
                    fields = mutableMapOf(Pair("id","filter_${type.text}${groupId}_qtblend")),
                    body = mutableListOf(
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","rotate_center")), body = 1),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "qtblend"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive_id")), body = "qtblend"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","rect")), body = param["${type.text.uppercase()}${groupId}_PROPERTY_RECT"].toString()),
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
                    Pair("hide",param["HIDE_TRACTOR_${type.text.uppercase()}${groupId}"].toString()),
                    Pair("producer","playlist_${type.text}${groupId}_file"))),
            MltNode(name = "track",
                fields = mutableMapOf(
                    Pair("hide",param["HIDE_TRACTOR_${type.text.uppercase()}${groupId}"].toString()),
                    Pair("producer","playlist_${type.text}${groupId}_track"))),

            )
    )

    return mlt
}

fun getTemplateSongText(param: Map<String, Any?>, voiceId: Int): MltNode {

    val templateSongTextGroup = mutableListOf<MltNode>()

    val templateSongTextSymbolsGroup = mutableListOf<MltNode>()

    val workAreaHeightPx = param["VOICE${voiceId}_WORK_AREA_HEIGHT_PX"] as Long
    val voiceLines = param["VOICE${voiceId}_VOICELINES"] as MutableList<*>
    val symbolHeightPx = param["SYMBOL_HEIGHT_PX"] as Double
    val boxHeight = symbolHeightPx.toLong()
    val startX = param["TITLE_POSITION_START_X_PX"] as Long
    val startY = param["TITLE_POSITION_START_Y_PX"] as Long
    val voiceSetting = VOICES_SETTINGS[Integer.min(voiceId, VOICES_SETTINGS.size - 1)]

    // Тонкий - font-weight="25"
    // Обычный - font-weight="50"
    // Полужирный - font-weight="63"
    // Жирный - font-weight="75"
    // Очень жирный - font-weight="87"


    voiceLines.forEachIndexed { indexLine, it ->
        val voiceLine = it as SongVoiceLine
        voiceLine.symbols.forEachIndexed { indexSymbol, lineSymbol ->
            val text = lineSymbol.text
            val x = (startX + voiceLine.getSymbolXpx(indexSymbol)).toLong()
            val y = (startY + indexLine*symbolHeightPx).toLong()
            val fontItalic = if (lineSymbol.font.isItalic) 1 else 0
            val fontWeight = if (lineSymbol.font.isBold) 75 else 50
            val boxWidth = lineSymbol.widthPx.toLong()
            val color = if (!lineSymbol.isBeat) voiceSetting[lineSymbol.group].colorText else voiceSetting[lineSymbol.group].colorBeat
            val fontColor = "${color.red}, ${color.green}, ${color.blue}, ${color.alpha}"

            templateSongTextSymbolsGroup.add(
                MltNode(
                    name = "item",
                    fields = mutableMapOf(Pair("type","QGraphicsTextItem"), Pair("z-index","0")),
                    body = mutableListOf(
                        MltNode(
                            name = "position",
                            fields = mutableMapOf(Pair("x","$x"), Pair("y","$y")),
                            body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(), body = "1,0,0,0,1,0,0,0,1"))
                        ),
                        MltNode(
                            name = "content",
                            fields = mutableMapOf(
                                Pair("line-spacing","${param["LINE_SPACING"]}"),             // line-spacing="$LINE_SPACING"
                                Pair("shadow", "${param["SHADOW"]}"),                        // shadow="$SHADOW"
                                Pair("font-underline","${param["FONT_UNDERLINE"]}"),         // font-underline="$FONT_UNDERLINE"
                                Pair("box-height","$boxHeight"),              // box-height="$boxHeightPx"
                                Pair("font", "${param["FONT_NAME"]}"),                       // font="$FONT_NAME"
                                Pair("letter-spacing","0"),                 // letter-spacing="0"
                                Pair("font-pixel-size","${param["FONT_SIZE_PT"]}"),      // font-pixel-size="$fontSizePt"
                                Pair("font-italic","$fontItalic"),         // font-italic="$FONT_ITALIC"
                                Pair("typewriter", "${param["TYPEWRITER"]}"),             // typewriter="$TYPEWRITER"
                                Pair("alignment","${param["ALIGNMENT"]}"),             // alignment="$ALIGNMENT"
                                Pair("font-weight","$fontWeight"),          // font-weight="$FONT_WEIGHT"
                                Pair("box-width","$boxWidth"),            // box-width="$boxWidthPx"
                                Pair("font-color", fontColor), // font-color="${GROUPS_FONT_COLORS_TEXT[indexGroup]}"
                            ),
                            body = text
                        )
                    )
                )
            )
        }
    }

    val templateSongText = MltNode(
        name = "kdenlivetitle",
        fields = mutableMapOf(
            Pair("duration","0"),
            Pair("LC_NUMERIC","C"),
            Pair("width","${Karaoke.frameWidthPx}"),
            Pair("height","$workAreaHeightPx"),
            Pair("out","0"),
        ),
        body = mutableListOf(
            templateSongTextSymbolsGroup,
            MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},$workAreaHeightPx"))),
            MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},$workAreaHeightPx"))),
            MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
        )
    )

    return templateSongText
}