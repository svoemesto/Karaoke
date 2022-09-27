import model.MltNode

fun getMltAudioMusicProducer(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "producer",
        fields = mutableMapOf(
            Pair("id","producer_audio_music"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","length")), body = param["SONG_LENGTH_MS"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","eof")), body = "pause"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","resource")), body = param["AUDIO_MUSIC_PATH"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","seekable")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","audio_index")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","video_index")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","mute_on_pause")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "avformat"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clipname")), body = "A_MUSIC"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:folderid")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clip_type")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = param["AUDIO_MUSIC_ID"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","force_reload")), body = 0)
        )
    )

    return mlt
}


fun getMltAudioMusicFileProducer(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "producer",
        fields = mutableMapOf(
            Pair("id","producer_audio_music_file"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","length")), body = param["SONG_LENGTH_MS"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","eof")), body = "pause"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","resource")), body = param["AUDIO_MUSIC_PATH"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","seekable")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","audio_index")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","video_index")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","mute_on_pause")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "avformat-novalidate"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clipname"))),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:folderid")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:clip_type")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = param["AUDIO_MUSIC_ID"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","xml")), body = "was here"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","set.test_audio")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","set.test_image")), body = 1),
        )
    )

    return mlt
}

fun getMltAudioMusicFilePlaylist(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_audio_music_file")
        ),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:audio_track")), body = 1),
            MltNode(name = "blank", fields = mutableMapOf(Pair("length",param["IN_OFFSET"].toString()))),
            MltNode(name = "entry", fields = mutableMapOf(
                Pair("producer","producer_audio_music_file"),
                Pair("in",param["SONG_START_TIMECODE"].toString()),
                Pair("out",param["SONG_END_TIMECODE"].toString()),
            ), body = mutableListOf(MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:id")), body = param["AUDIO_MUSIC_ID"])))
        )
    )

    return mlt
}

fun getMltAudioMusicTrackPlaylist(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "playlist",
        fields = mutableMapOf(
            Pair("id","playlist_audio_music_track")
        ),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:audio_track")), body = 1)
        )
    )

    return mlt
}

fun getMltAudioMusicTractor(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "tractor",
        fields = mutableMapOf(
            Pair("id","tractor_music"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:audio_track")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:trackheight")), body = 69),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:timeline_active")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:collapsed")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:track_name")), body = "MUSIC"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:thumbs_format"))),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:audio_rec"))),
            MltNode(name = "track",
                fields = mutableMapOf(
                    Pair("hide",param["HIDE_TRACTOR_MUSIC"].toString()),
                    Pair("producer","playlist_audio_music_file"))),
            MltNode(name = "track",
                fields = mutableMapOf(
                    Pair("hide",param["HIDE_TRACTOR_MUSIC"].toString()),
                    Pair("producer","playlist_audio_music_track"))),
            MltNode(name = "filter",
                fields = mutableMapOf(Pair("id","filter_music_volume")),
                body = mutableListOf(
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","window")), body = 75),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","max_gain")), body = "20dB"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "volume"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","internal_added")), body = 237),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","disable")), body = 1),
                )
            ),
            MltNode(name = "filter",
                fields = mutableMapOf(Pair("id","filter_music_panner")),
                body = mutableListOf(
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","channel")), body = -1),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "panner"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","internal_added")), body = 237),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","start")), body = "0.5"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","disable")), body = 1),
                )
            ),
            MltNode(name = "filter",
                fields = mutableMapOf(Pair("id","filter_music_audiolevel")),
                body = mutableListOf(
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","iec_scale")), body = 0),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "audiolevel"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","peak")), body = 1),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","disable")), body = 1),
                )
            ),
        )
    )

    return mlt
}