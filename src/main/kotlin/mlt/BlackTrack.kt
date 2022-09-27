import model.MltNode

fun getMltBlackTrackProducer(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "producer",
        fields = mutableMapOf(
            Pair("id","black_track"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","length")), body = param["SONG_LENGTH_MS"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","eof")), body = "continue"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","resource")), body = "black"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","aspect_ratio")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "color"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_image_format")), body = "rgba"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","set.test_audio")), body = 0)
        )
    )

    return mlt
}