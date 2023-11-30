import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.SongOutputFile

fun getMltConsumer(mltProp: MltProp): MltNode {


    val mlt = MltNode(
        name = "consumer",
        fields = mutableMapOf(
            Pair("f","mp4"),
            Pair("properties","x265-medium"),
            Pair("channels","2"),
            Pair("x265-param","crf=%quality"),
            Pair("crf","15"),
            Pair("target",mltProp.getFileName(SongOutputFile.VIDEO)),
            Pair("mlt_service","avformat"),
            Pair("real_time","-16"),
            Pair("threads","0"),
            Pair("vcodec","libx265"),
            Pair("ab","160k"),
            Pair("preset","ultrafast"),
            Pair("acodec","aac"),
            Pair("in","0"),
            Pair("out",mltProp.getLengthFr("Total").toString())
        )
    )

    return mlt
}