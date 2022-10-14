import mlt.MltText
import mlt.mltNode
import model.SongVoiceLine
import model.MltNode
import model.ProducerType

fun getMltChordsProducer(param: Map<String, Any?>, type:ProducerType = ProducerType.CHORDS, voiceId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "producer",
        fields = mutableMapOf(
            Pair("id","producer_${type.text}${voiceId}"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","length")), body = param["SONG_LENGTH_FR"]),
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
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.height")), body = param["${type.text.uppercase()}${voiceId}_WORK_AREA_CHORDS_HEIGHT_PX"])
        )
    )

    return mlt
}


fun getMltChordsFilePlaylist(param: Map<String, Any?>, type:ProducerType = ProducerType.CHORDS, voiceId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_${type.text}${voiceId}_file")
        ),
        body = mutableListOf(
            MltNode(name = "blank", fields = mutableMapOf(Pair("length", param["IN_OFFSET_VIDEO"].toString()))),
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
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","rect")), body = param["${type.text.uppercase()}${voiceId}_PROPERTY_RECT"].toString()),
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

fun getMltChordsTrackPlaylist(param: Map<String, Any?>, type:ProducerType = ProducerType.CHORDS, voiceId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_${type.text}${voiceId}_track")
        )
    )

    return mlt
}

fun getMltChordsTractor(param: Map<String, Any?>, type:ProducerType = ProducerType.CHORDS, voiceId: Int = 0): MltNode {

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

fun getTemplateChords(param: Map<String, Any?>, voiceId: Int): MltNode {

    val templateChordsSymbolsGroup = mutableListOf<MltNode>()
    val voiceSetting = param["VOICE${voiceId}_SETTING"] as KaraokeVoice
    val workAreaChordsHeightPx = param["VOICE${voiceId}_WORK_AREA_CHORDS_HEIGHT_PX"] as Long
    val voiceLinesSongchords = param["VOICE${voiceId}_VOICELINES_SONGCHORDS"] as MutableList<*>
    val voiceLinesChords = param["VOICE${voiceId}_VOICELINES_CHORDS"] as MutableList<*>
    val symbolSongtextHeightPx = param["SYMBOL_SONGTEXT_HEIGHT_PX"] as Int
    val symbolChordsHeightPx = param["SYMBOL_CHORDS_HEIGHT_PX"] as Int
    val startX = Karaoke.songtextStartPositionXpx
    val startYsongchords = symbolChordsHeightPx
    val startYchords = symbolSongtextHeightPx * 0.1


    voiceLinesSongchords.forEachIndexed { indexLine, it ->
        val voiceLineSongchords = it as SongVoiceLine
        voiceLineSongchords.symbols.forEachIndexed { indexSymbol, lineSymbol ->

            val mltText: MltText = lineSymbol.mltText
//            val mltText: MltText = if (!lineSymbol.isBeat) voiceSetting.groups[lineSymbol.group].songtextTextMltText else voiceSetting.groups[lineSymbol.group].songtextBeatMltText
            val text = lineSymbol.mltText.text
            val x = (startX + voiceLineSongchords.getSymbolXpx(indexSymbol)).toLong()
            val y = (startYsongchords + indexLine*(symbolSongtextHeightPx+symbolChordsHeightPx) + (symbolSongtextHeightPx - mltText.h)).toLong()

            templateChordsSymbolsGroup.add(
                MltNode(
                    name = "item",
                    fields = mutableMapOf(Pair("type","QGraphicsTextItem"), Pair("z-index","0")),
                    body = mutableListOf(
                        MltNode(
                            name = "position",
                            fields = mutableMapOf(Pair("x","$x"), Pair("y","$y")),
                            body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(), body = "1,0,0,0,1,0,0,0,1"))
                        ),
                        mltText.mltNode(text)
                    )
                )
            )
        }
    }

    voiceLinesChords.forEachIndexed { indexLine, it ->

        val voiceLineChords = it as SongVoiceLine
        val voiceLineSongchords = voiceLinesSongchords[indexLine] as SongVoiceLine

        voiceLineChords.symbols.forEachIndexed { indexSymbol, lineSymbol ->

//            val mltText = MltText(
//                font = lineSymbol.mltText,
//                shapeColor = Karaoke.chordsFont.shapeColor,
//                fontUnderline = Karaoke.chordsFont.fontUnderline,
//                shapeOutline = Karaoke.chordsFont.shapeOutline,
//                shapeOutlineColor = Karaoke.chordsFont.shapeOutlineColor
//            )

            val mltText = lineSymbol.mltText
            val text = mltText.text
            val textBeforeChord = lineSymbol.mltTextBefore.text
            val x = (startX + getTextWidthHeightPx(textBeforeChord, voiceLineSongchords.mltText.font).first).toLong()
            val y = (startYchords + indexLine*(symbolSongtextHeightPx+symbolChordsHeightPx)).toLong()

            templateChordsSymbolsGroup.add(
                MltNode(
                    name = "item",
                    fields = mutableMapOf(Pair("type","QGraphicsTextItem"), Pair("z-index","0")),
                    body = mutableListOf(
                        MltNode(
                            name = "position",
                            fields = mutableMapOf(Pair("x","$x"), Pair("y","$y")),
                            body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(), body = "1,0,0,0,1,0,0,0,1"))
                        ),
                        mltText.mltNode(text)
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
            Pair("height","$workAreaChordsHeightPx"),
            Pair("out","0"),
        ),
        body = mutableListOf(
            templateChordsSymbolsGroup,
            MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},$workAreaChordsHeightPx"))),
            MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},$workAreaChordsHeightPx"))),
            MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
        )
    )

    return templateSongText
}

