package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.hexRGB
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.ProducerType

data class MkoFillcolorSongtext(val param: Map<String, Any?>,
                                val isEven: Boolean,
                                val voiceId: Int = 0) : MltKaraokeObject {
    val type: ProducerType = ProducerType.FILLCOLORSONGTEXT
    override fun producer(): MltNode {
        if (isEven) {

            val mlt = MltNode(
                type = type,
                name = "producer",
                fields = mutableMapOf(
                    Pair("id","producer_${type.text}even${voiceId}"),
                    Pair("in",param["SONG_START_TIMECODE"].toString()),
                    Pair("out",param["SONG_END_TIMECODE"].toString())
                ),
                body = mutableListOf(
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","length")), body = param["SONG_LENGTH_FR"]),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","eof")), body = "pause"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","resource")), body = Karaoke.voices[0].fill.evenColor.hexRGB()),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","aspect_ratio")), body = 1),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "color"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:duration")), body = param["SONG_END_TIMECODE"]),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clipname")), body = "${type.text.uppercase()}EVEN${if (voiceId==0) "" else voiceId}"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:folderid")), body = -1),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clip_type")), body = 2),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = (param["${type.text.uppercase()}${voiceId}_EVEN_ID"] as Int)+voiceId*1000),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_image_format")), body = "rgb")
                )
            )

            return mlt

        } else {

            val mlt = MltNode(
                type = type,
                name = "producer",
                fields = mutableMapOf(
                    Pair("id","producer_${type.text}odd${voiceId}"),
                    Pair("in",param["SONG_START_TIMECODE"].toString()),
                    Pair("out",param["SONG_END_TIMECODE"].toString())
                ),
                body = mutableListOf(
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","length")), body = param["SONG_LENGTH_FR"]),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","eof")), body = "pause"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","resource")), body = Karaoke.voices[0].fill.oddColor.hexRGB()),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","aspect_ratio")), body = 1),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "color"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:duration")), body = param["SONG_END_TIMECODE"]),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clipname")), body = "${type.text.uppercase()}ODD${if (voiceId==0) "" else voiceId}"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:folderid")), body = -1),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clip_type")), body = 2),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = (param["${type.text.uppercase()}${voiceId}_ODD_ID"] as Int)+voiceId*1000),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_image_format")), body = "rgb")
                )
            )

            return mlt

        }
    }

    override fun fileProducer(): MltNode = MltNode()

    override fun filePlaylist(): MltNode {
        if (isEven) {

            val mlt = MltNode(
                type = type,
                name = "playlist",
                fields = mutableMapOf(
                    Pair("id","playlist_${type.text}even${voiceId}_file")
                ),
                body = mutableListOf(
                    MltNode(name = "blank", fields = mutableMapOf(Pair("length", param["IN_OFFSET_VIDEO"].toString()))),
                    MltNode(name = "entry", fields = mutableMapOf(
                        Pair("producer","producer_${type.text}even${voiceId}"),
                        Pair("in",param["SONG_START_TIMECODE"].toString()),
                        Pair("out",param["SONG_END_TIMECODE"].toString()),
                    ), body = mutableListOf(
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = (param["${type.text.uppercase()}${voiceId}_EVEN_ID"] as Int)+voiceId*1000),
                        MltNode(name = "filter",
                            fields = mutableMapOf(Pair("id","filter_${type.text}even${voiceId}_qtblend")),
                            body = mutableListOf(
                                MltNode(name = "property", fields = mutableMapOf(Pair("name","rotate_center")), body = 1),
                                MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "qtblend"),
                                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive_id")), body = "qtblend"),
                                MltNode(name = "property", fields = mutableMapOf(Pair("name","rect")), body = param["${type.text.uppercase()}${voiceId}_EVEN_PROPERTY_RECT"].toString()),
                                MltNode(name = "property", fields = mutableMapOf(Pair("name","compositing")), body = 0),
                                MltNode(name = "property", fields = mutableMapOf(Pair("name","distort")), body = 1),
                                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:collapsed")), body = 0),
                                MltNode(name = "property", fields = mutableMapOf(Pair("name","rotation")), body = "00:00:00.000=0")
                            )
                        ),
                    ))
                )
            )

            return mlt

        } else {

            val mlt = MltNode(
                type = type,
                name = "playlist",
                fields = mutableMapOf(
                    Pair("id","playlist_${type.text}odd${voiceId}_file")
                ),
                body = mutableListOf(
                    MltNode(name = "blank", fields = mutableMapOf(Pair("length", param["IN_OFFSET_VIDEO"].toString()))),
                    MltNode(name = "entry", fields = mutableMapOf(
                        Pair("producer","producer_${type.text}odd${voiceId}"),
                        Pair("in",param["SONG_START_TIMECODE"].toString()),
                        Pair("out",param["SONG_END_TIMECODE"].toString()),
                    ), body = mutableListOf(
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = (param["${type.text.uppercase()}${voiceId}_ODD_ID"] as Int)+voiceId*1000),
                        MltNode(name = "filter",
                            fields = mutableMapOf(Pair("id","filter_${type.text}odd${voiceId}_qtblend")),
                            body = mutableListOf(
                                MltNode(name = "property", fields = mutableMapOf(Pair("name","rotate_center")), body = 1),
                                MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "qtblend"),
                                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive_id")), body = "qtblend"),
                                MltNode(name = "property", fields = mutableMapOf(Pair("name","rect")), body = param["${type.text.uppercase()}${voiceId}_ODD_PROPERTY_RECT"].toString()),
                                MltNode(name = "property", fields = mutableMapOf(Pair("name","compositing")), body = 0),
                                MltNode(name = "property", fields = mutableMapOf(Pair("name","distort")), body = 1),
                                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:collapsed")), body = 0),
                                MltNode(name = "property", fields = mutableMapOf(Pair("name","rotation")), body = "00:00:00.000=0")
                            )
                        ),
                    ))
                )
            )

            return mlt

        }
    }

    override fun trackPlaylist(): MltNode {
        if (isEven) {

            val mlt = MltNode(
                type = type,
                name = "playlist",
                fields = mutableMapOf(
                    Pair("id","playlist_${type.text}even${voiceId}_track")
                )
            )

            return mlt

        } else {

            val mlt = MltNode(
                type = type,
                name = "playlist",
                fields = mutableMapOf(
                    Pair("id","playlist_${type.text}odd${voiceId}_track")
                )
            )

            return mlt

        }
    }

    override fun tractor(): MltNode {
        if (isEven) {

            val mlt = MltNode(
                type = type,
                name = "tractor",
                fields = mutableMapOf(
                    Pair("id","tractor_${type.text}even${voiceId}"),
                    Pair("in",param["SONG_START_TIMECODE"].toString()),
                    Pair("out",param["SONG_END_TIMECODE"].toString())
                ),
                body = mutableListOf(
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:trackheight")), body = 69),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:timeline_active")), body = 1),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:collapsed")), body = 28),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:track_name")), body = "${type.text.uppercase()}${if (voiceId==0) "" else voiceId}_EVEN"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:thumbs_format"))),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:audio_rec"))),
                    MltNode(name = "track",
                        fields = mutableMapOf(
                            Pair("hide",param["HIDE_TRACTOR_${type.text.uppercase()}${voiceId}_EVEN"].toString()),
                            Pair("producer","playlist_${type.text}even${voiceId}_file"))),
                    MltNode(name = "track",
                        fields = mutableMapOf(
                            Pair("hide",param["HIDE_TRACTOR_${type.text.uppercase()}${voiceId}_EVEN"].toString()),
                            Pair("producer","playlist_${type.text}even${voiceId}_track"))),

                    )
            )

            return mlt

        } else {

            val mlt = MltNode(
                type = type,
                name = "tractor",
                fields = mutableMapOf(
                    Pair("id","tractor_${type.text}odd${voiceId}"),
                    Pair("in",param["SONG_START_TIMECODE"].toString()),
                    Pair("out",param["SONG_END_TIMECODE"].toString())
                ),
                body = mutableListOf(
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:trackheight")), body = 69),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:timeline_active")), body = 1),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:collapsed")), body = 28),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:track_name")), body = "${type.text.uppercase()}${if (voiceId==0) "" else voiceId}_ODD"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:thumbs_format"))),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:audio_rec"))),
                    MltNode(name = "track",
                        fields = mutableMapOf(
                            Pair("hide",param["HIDE_TRACTOR_${type.text.uppercase()}${voiceId}_ODD"].toString()),
                            Pair("producer","playlist_${type.text}odd${voiceId}_file"))),
                    MltNode(name = "track",
                        fields = mutableMapOf(
                            Pair("hide",param["HIDE_TRACTOR_${type.text.uppercase()}${voiceId}_ODD"].toString()),
                            Pair("producer","playlist_${type.text}odd${voiceId}_track"))),

                    )
            )

            return mlt

        }
    }

    override fun template(): MltNode = MltNode()
}
