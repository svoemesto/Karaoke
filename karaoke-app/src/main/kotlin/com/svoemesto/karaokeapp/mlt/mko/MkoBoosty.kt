package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.xmldata
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.ProducerType

data class MkoBoosty(val param: Map<String, Any?>) : MltKaraokeObject {
    val type: ProducerType = ProducerType.BOOSTY
    val voiceId: Int = 0
    override fun producer(): MltNode {
        val mlt = MltNode(
            type = type,
            name = "producer",
            fields = mutableMapOf(
                Pair("id","producer_${type.text}${voiceId}"),
                Pair("in",param["SPLASHSTART_END_TIMECODE"].toString()),
                Pair("out",param["SONG_END_TIMECODE"].toString())
            ),
            body = mutableListOf(
                MltNode(name = "property", fields = mutableMapOf(Pair("name","length")), body = Karaoke.timeBoostyStartMs.toString()),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","eof")), body = "pause"),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","resource"))),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","progressive")), body = 1),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","aspect_ratio")), body = 1),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","seekable")), body = 1),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "kdenlivetitle"),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:duration")), body = param["BOOSTY_END_TIMECODE"]),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clipname")), body = "${type.text.uppercase()}${if (voiceId==0) "" else voiceId}"),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","xmldata")), body = param["${type.text.uppercase()}${voiceId}_XML_DATA"].toString().xmldata()),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:folderid")), body = -1),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clip_type")), body = 2),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = (param["${type.text.uppercase()}${voiceId}_ID"] as Int)+voiceId*1000),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","force_reload")), body = 0),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.width")), body = Karaoke.frameWidthPx),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.height")), body = Karaoke.frameHeightPx)
            )
        )

        return mlt
    }

    override fun fileProducer(): MltNode = MltNode()

    override fun filePlaylist(): MltNode {
        val mlt = MltNode(
            type = type,
            name = "playlist",
            fields = mutableMapOf(
                Pair("id","playlist_${type.text}${voiceId}_file")
            ),
            body = mutableListOf(
                MltNode(name = "blank", fields = mutableMapOf(Pair("length", param["SPLASHSTART_END_TIMECODE"].toString()))),
                MltNode(name = "entry", fields = mutableMapOf(
                    Pair("producer","producer_${type.text}${voiceId}"),
                    Pair("in",param["SPLASHSTART_END_TIMECODE"].toString()),
                    Pair("out",param["BOOSTY_END_TIMECODE"].toString()),
                ), body = mutableListOf(
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = (param["${type.text.uppercase()}${voiceId}_ID"] as Int)+voiceId*1000),
                    MltNode(name = "filter",
                        fields = mutableMapOf(Pair("id","filter_${type.text}${voiceId}_qtblend")),
                        body = mutableListOf(
                            MltNode(name = "property", fields = mutableMapOf(Pair("name","rotate_center")), body = 1),
                            MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "qtblend"),
                            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive_id")), body = "qtblend"),
                            MltNode(name = "property", fields = mutableMapOf(Pair("name","rect")), body = "${param["SPLASHSTART_END_TIMECODE"]}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.000000;${param["BOOSTY_FADEIN_TIMECODE"]}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.000000;${param["BOOSTY_FADEOUT_TIMECODE"]}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.000000;${param["BOOSTY_END_TIMECODE"]}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.000000"),
                            MltNode(name = "property", fields = mutableMapOf(Pair("name","compositing")), body = 0),
                            MltNode(name = "property", fields = mutableMapOf(Pair("name","distort")), body = 0),
                            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:collapsed")), body = 0),
                            MltNode(name = "property", fields = mutableMapOf(Pair("name","rotation")), body = "00:00:00.000=0")
                        )
                    ),
                ))
            )
        )

        return mlt
    }

    override fun trackPlaylist(): MltNode {
        val mlt = MltNode(
            type = type,
            name = "playlist",
            fields = mutableMapOf(
                Pair("id","playlist_${type.text}${voiceId}_track")
            )
        )

        return mlt
    }

    override fun tractor(): MltNode {
        val mlt = MltNode(
            type = type,
            name = "tractor",
            fields = mutableMapOf(
                Pair("id","tractor_${type.text}${voiceId}"),
                Pair("in",param["SPLASHSTART_END_TIMECODE"].toString()),
                Pair("out",param["BOOSTY_END_TIMECODE"].toString())
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

    override fun template(): MltNode {
        val body = mutableListOf<MltNode>()

        body.add(
            MltNode(
                name = "item",
                fields = mutableMapOf(
                    Pair("type","QGraphicsPixmapItem"),
                    Pair("z-index","6"),
                ), body = mutableListOf(
                    MltNode(name = "position", fields = mutableMapOf(Pair("x","0"),Pair("y","0")),
                        body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1"))),
                    MltNode(name = "content", fields = mutableMapOf(Pair("url", param["SPLASH_PATH"].toString())))
                )
            )
        )

        return MltNode(
            name = "kdenlivetitle",
            fields = mutableMapOf(
                Pair("duration","0"),
                Pair("LC_NUMERIC","C"),
                Pair("width","${Karaoke.frameWidthPx}"),
                Pair("height","${Karaoke.frameHeightPx}"),
                Pair("out","0"),
            ),
            body = body
        )
    }

}
