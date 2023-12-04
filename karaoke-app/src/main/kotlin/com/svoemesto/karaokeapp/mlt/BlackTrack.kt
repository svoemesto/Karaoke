import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.MltNode

fun getMltBlackTrackProducer(mltProp: MltProp): MltNode {

    val mlt = MltNode(
        name = "producer",
        fields = mutableMapOf(
            Pair("id","black_track"),
            Pair("in",mltProp.getSongStartTimecode()),
            Pair("out",mltProp.getSongEndTimecode())
        ),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","length")), body = mltProp.getLengthFr("Song")),
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