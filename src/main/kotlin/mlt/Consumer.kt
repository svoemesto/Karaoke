import model.MltNode

fun getMltConsumer(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "consumer",
        fields = mutableMapOf(
            Pair("f","mp4"),
            Pair("properties","x265-medium"),
            Pair("channels","2"),
            Pair("x265-param","crf=%quality"),
            Pair("crf","15"),
            Pair("target",param["SONG_VIDEO_FILENAME"].toString()),
            Pair("mlt_service","avformat"),
            Pair("real_time","-16"),
            Pair("threads","0"),
            Pair("vcodec","libx265"),
            Pair("ab","160k"),
            Pair("preset","ultrafast"),
            Pair("acodec","aac"),
            Pair("in","0"),
            Pair("out",param["TOTAL_LENGTH_FR"].toString())
        )
    )

    return mlt
}