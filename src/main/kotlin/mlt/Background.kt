import model.MltNode

fun getMltBackgroundProducer(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "producer",
        fields = mutableMapOf(
            Pair("id","producer_background"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","length")), body = param["SONG_LENGTH_MS"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","eof")), body = "pause"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","resource")), body = param["BACKGROUND_PATH"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","ttl")), body = 25),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","progressive")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","aspect_ratio")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "qimage"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:duration")), body = param["SONG_END_TIMECODE"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clipname")), body = "BACKGROUND"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:folderid")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clip_type")), body = 2),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = param["BACKGROUND_ID"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","force_reload")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.width")), body = 4096),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","meta.media.height")), body = 4096)
        )
    )

    return mlt
}

fun getMltBackgroundFilePlaylist(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_background_file")
        ),
        body = mutableListOf(
            MltNode(name = "entry", fields = mutableMapOf(
                Pair("producer","producer_background"),
                Pair("in",param["SONG_START_TIMECODE"].toString()),
                Pair("out",param["SONG_END_TIMECODE"].toString()),
            ), body = mutableListOf(
                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = param["BACKGROUND_ID"]),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:activeeffect")), body = 1),
                MltNode(name = "filter",
                    fields = mutableMapOf(Pair("id","filter_background_lift_gamma_gain")),
                    body = mutableListOf(
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","lift_r")), body = "${param["SONG_START_TIMECODE"].toString()}=-0.199985"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","lift_g")), body = "${param["SONG_START_TIMECODE"].toString()}=-0.199985"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","lift_b")), body = "${param["SONG_START_TIMECODE"].toString()}=-0.199985"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","gamma_r")), body = "${param["SONG_START_TIMECODE"].toString()}=0.724987"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","gamma_g")), body = "${param["SONG_START_TIMECODE"].toString()}=0.724987"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","gamma_b")), body = "${param["SONG_START_TIMECODE"].toString()}=0.724987"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","gain_r")), body = "${param["SONG_START_TIMECODE"].toString()}=1"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","gain_g")), body = "${param["SONG_START_TIMECODE"].toString()}=1"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","gain_b")), body = "${param["SONG_START_TIMECODE"].toString()}=1"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "lift_gamma_gain"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive_id")), body = "lift_gamma_gain"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:collapsed")), body = 0),
                    )
                ),
                MltNode(name = "filter",
                    fields = mutableMapOf(Pair("id","filter_background_qtblend")),
                    body = mutableListOf(
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","rotate_center")), body = 1),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "qtblend"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive_id")), body = "qtblend"),
                        MltNode(name = "property", fields = mutableMapOf(Pair("name","rect")), body = "${param["SONG_START_TIMECODE"].toString()}=0 0 4096 4096 0.000000;${param["SONG_FADEIN_TIMECODE"].toString()}=-13 -18 4096 4096 1.000000;${param["SONG_FADEOUT_TIMECODE"].toString()}=-2163 -2998 4096 4096 1.000000;${param["SONG_END_TIMECODE"].toString()}=-2176 -3016 4096 4096 0.000000"),
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

fun getMltBackgroundTrackPlaylist(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_background_track")
        )
    )

    return mlt
}

fun getMltBackgroundTractor(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "tractor",
        fields = mutableMapOf(
            Pair("id","tractor_background"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:trackheight")), body = 69),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:timeline_active")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:collapsed")), body = 28),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:track_name")), body = "BACKGROUND"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:thumbs_format"))),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:audio_rec"))),
            MltNode(name = "track",
                fields = mutableMapOf(
                    Pair("hide",param["HIDE_TRACTOR_BACKGROUND"].toString()),
                    Pair("producer","playlist_background_file"))),
            MltNode(name = "track",
                fields = mutableMapOf(
                    Pair("hide",param["HIDE_TRACTOR_BACKGROUND"].toString()),
                    Pair("producer","playlist_background_track"))),

        )
    )

    return mlt
}