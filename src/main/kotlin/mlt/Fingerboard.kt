import model.Chord
import model.MltNode
import model.ProducerType
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO

fun getMltFingerboardProducer(param: Map<String, Any?>, type:ProducerType = ProducerType.FINGERBOARD, voiceId: Int = 0): MltNode {

    val fingerboardW = param["VOICE0_FINGERBOARD_W"] as Int
    val fingerboardH = param["VOICE0_FINGERBOARD_H"] as Int

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
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = (param["${type.text.uppercase()}${voiceId}_ID"] as Int)+voiceId*1000),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","force_reload")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.width")), body = fingerboardW),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.height")), body = fingerboardH)
        )
    )

    return mlt
}


fun getMltFingerboardFilePlaylist(param: Map<String, Any?>, type:ProducerType = ProducerType.FINGERBOARD, voiceId: Int = 0): MltNode {

    val fingerboardW = param["VOICE0_FINGERBOARD_W"] as Int
    val fingerboardH = param["VOICE0_FINGERBOARD_H"] as Int

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
                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = (param["${type.text.uppercase()}${voiceId}_ID"] as Int)+voiceId*1000),
            ))
        )
    )

    return mlt
}

fun getMltFingerboardTrackPlaylist(param: Map<String, Any?>, type:ProducerType = ProducerType.FINGERBOARD, voiceId: Int = 0): MltNode {

    val mlt = MltNode(
        type = type,
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_${type.text}${voiceId}_track")
        )
    )

    return mlt
}

fun getMltFingerboardTractor(param: Map<String, Any?>, type:ProducerType = ProducerType.FINGERBOARD, voiceId: Int = 0): MltNode {

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

fun getTemplateFingerboard(param: Map<String, Any?>): MltNode {

    val voiceSetting = param["VOICE0_SETTING"] as KaraokeVoice
    val fingerboardW = param["VOICE0_FINGERBOARD_W"] as Int
    val fingerboardH = param["VOICE0_FINGERBOARD_H"] as Int
    val chordW = param["VOICE0_CHORD_W"] as Int
    val chordH = param["VOICE0_CHORD_H"] as Int
    val chords = param["VOICE0_CHORDS"] as List<Chord>
    val startChordX = (Karaoke.frameWidthPx / 2 - chordW /2 + chordW).toInt()

    val body: MutableList<MltNode> = mutableListOf()

    chords.forEachIndexed{ indexChord, chord ->
        val chordX = startChordX + indexChord * chordW
        val layouts = generateChordLayout(chord.text)
        val bi = getChordLayoutPicture(layouts)
        val os = ByteArrayOutputStream()
        ImageIO.write(bi, "png", os)
        val base64 = Base64.getEncoder().encodeToString(os.toByteArray())

        body.add(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsPixmapItem"),
                    Pair("z-index","0"),
                ),
                body = mutableListOf(
                    MltNode(
                        name = "position",
                        fields = mutableMapOf(
                            Pair("x","${chordX}"),
                            Pair("y","0")
                        )
                    ),
                    MltNode(
                        name = "content",
                        fields = mutableMapOf(
                            Pair("base64",base64)
                        )
                    )
                )
            )
        )
    }


    return MltNode(
        name = "kdenlivetitle",
        fields = mutableMapOf(
            Pair("duration","0"),
            Pair("LC_NUMERIC","C"),
            Pair("width","${fingerboardW}"),
            Pair("height","${fingerboardH}"),
            Pair("out","0"),
        ),
        body = mutableListOf(
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
                            Pair("y","0")
                        ),
                        body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(Pair("zoom","100")), body = "1,0,0,0,1,0,0,0,1"))
                    ),
                    MltNode(
                        name = "content",
                        fields = mutableMapOf(
                            Pair("brushcolor","0,0,0,255"),
                            Pair("pencolor","0,0,0,255"),
                            Pair("penwidth","0"),
                            Pair("penwidth","0"),
                            Pair("rect","0,0,$fingerboardW,$fingerboardH")
                        )
                    )
                )
            ),
            body,
            MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${fingerboardW},${fingerboardH}"))),
            MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${fingerboardW},${fingerboardH}"))),
            MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0")))
        )
    )

}