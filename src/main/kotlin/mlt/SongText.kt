import mlt.MltText
import mlt.mltNode
import model.SongVoiceLine
import model.MltNode
import model.MusicChord
import model.MusicNote
import model.ProducerType
import model.SongVoiceLineType

fun getMltSongTextProducer(param: Map<String, Any?>, type:ProducerType = ProducerType.SONGTEXT, voiceId: Int = 0): MltNode {



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
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.height")), body = param["${type.text.uppercase()}${voiceId}_WORK_AREA_SONGTEXT_HEIGHT_PX"])
        )
    )

    return mlt
}


fun getMltSongTextFilePlaylist(param: Map<String, Any?>, type:ProducerType = ProducerType.SONGTEXT, voiceId: Int = 0): MltNode {

    val voiceLines = param["VOICE${voiceId}_VOICELINES_SONGTEXT"] as List<SongVoiceLine>
    val propRect = voiceLines
        .filter {it.startTp != null && it.endTp != null && (it.type == SongVoiceLineType.TEXT || it == voiceLines.first() || it == voiceLines.last())}
        .map { listOf(it.startTp.toString(), it.endTp.toString()).joinToString(";") }.joinToString(";")
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
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","rect")), body = propRect),
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

fun getMltSongTextTrackPlaylist(param: Map<String, Any?>, type:ProducerType = ProducerType.SONGTEXT, voiceId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_${type.text}${voiceId}_track")
        )
    )

    return mlt
}

fun getMltSongTextTractor(param: Map<String, Any?>, type:ProducerType = ProducerType.SONGTEXT, voiceId: Int = 0): MltNode {

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

fun getTemplateSongText(param: Map<String, Any?>, voiceId: Int, ignoreCapo: Boolean = false): MltNode {

    val voiceLines = param["VOICE${voiceId}_VOICELINES_SONGTEXT"] as List<SongVoiceLine>
    val templateSongTextSymbolsGroup = mutableListOf<MltNode>()
    val workAreaSongtextHeightPx = param["VOICE${voiceId}_WORK_AREA_SONGTEXT_HEIGHT_PX"] as Int
    val capo = param["SONG_CAPO"] as Int

    voiceLines.forEachIndexed { indexLine, it ->
        val voiceLineSongtext = it as SongVoiceLine
        voiceLineSongtext.symbols.forEachIndexed { indexSymbol, lineSymbol ->

            val mltText: MltText = lineSymbol.mltText
            val text = if (voiceLineSongtext.type == SongVoiceLineType.CHORDS) {
                if (capo == 0 || ignoreCapo) {
                    mltText.text.split("|")[0]
                } else {
                    val chordNameAndFret = mltText.text.split("|")
                    val nameChord = chordNameAndFret[0]
                    val fretChord = if (chordNameAndFret.size > 1) chordNameAndFret[1].toInt() else 0
                    val (chord, note) = MusicChord.getChordNote(nameChord)

                    var newIndexNote = MusicNote.values().indexOf(note!!) - capo
                    if (newIndexNote < 0) newIndexNote = MusicNote.values().size + newIndexNote
                    val newNote = MusicNote.values()[newIndexNote]

                    newNote.names.first() + chord!!.names.first()
                }
            } else {
                mltText.text.replace("&","&amp;amp;")
            }

            val x = lineSymbol.xStartPx + Karaoke.songtextStartPositionXpx // (startX + voiceLineSongtext.getSymbolXpx(indexSymbol)).toLong()
            val y = voiceLineSongtext.yPx //(startY + indexLine*symbolSongtextHeightPx + (symbolSongtextHeightPx - mltText.h)).toLong()

            templateSongTextSymbolsGroup.add(
                MltNode(
                    name = "item",
                    fields = mutableMapOf(Pair("type","QGraphicsTextItem"), Pair("z-index","1")),
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
    templateSongTextSymbolsGroup.add(MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},$workAreaSongtextHeightPx"))))
    templateSongTextSymbolsGroup.add(MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},$workAreaSongtextHeightPx"))))
    templateSongTextSymbolsGroup.add(MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0"))))

    val templateSongText = MltNode(
        name = "kdenlivetitle",
        fields = mutableMapOf(
            Pair("duration","0"),
            Pair("LC_NUMERIC","C"),
            Pair("width","${Karaoke.frameWidthPx}"),
            Pair("height","$workAreaSongtextHeightPx"),
            Pair("out","0"),
        ),
        body = templateSongTextSymbolsGroup
    )

    return templateSongText

}

