import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.MltNode

fun getMltProfile(): MltNode {

    val mlt = MltNode(
        name = "profile",
        fields = mutableMapOf(
            Pair("frame_rate_num","60"),
            Pair("sample_aspect_num","1"),
            Pair("display_aspect_den","9"),
            Pair("colorspace","709"),
            Pair("progressive","1"),
            Pair("description","HD 1080p 60 fps"),
            Pair("display_aspect_num","16"),
            Pair("frame_rate_den","1"),
            Pair("width","${Karaoke.frameWidthPx}"),
            Pair("height","${Karaoke.frameHeightPx}"),
            Pair("sample_aspect_den","1")
        )
    )

    return mlt
}