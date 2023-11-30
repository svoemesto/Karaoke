import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.model.SongOutputFile
import com.svoemesto.karaokeapp.model.SongVersion

fun getMltMainBinPlaylist(mltProp: MltProp): MltNode {

    val entries = mutableListOf<MltNode>()

    val songVersion = mltProp.getSongVersion()
    val countVoices = mltProp.getCountVoices()
    val countFingerboards = mltProp.getCountFingerboards(0)


    songVersion.producers.forEach { type ->

        for (voiceId in 0 until countVoices) {
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
                if (type == ProducerType.FINGERBOARD) {
                    for (indexFingerboard in (countFingerboards-1) downTo 0 ) {
                        val mltGenerator = MltGenerator(mltProp, type, voiceId, indexFingerboard)
                        entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer", mltGenerator.nameProducer),Pair("in", mltProp.getStartTimecode("Song")),Pair("out", mltProp.getEndTimecode("Song")))))
                    }
                } else {
                    if (type.ids.isEmpty()) {
                        val mltGenerator = MltGenerator(mltProp, type, voiceId)
                        entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer",mltGenerator.nameProducer),Pair("in", mltProp.getStartTimecode("Song")),Pair("out", mltProp.getEndTimecode("Song")))))
                    } else {
                        for (id in type.ids) {
                            val mltGenerator = MltGenerator(mltProp, type, voiceId, id)
                            entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer",mltGenerator.nameProducer),Pair("in", mltProp.getStartTimecode("Song")),Pair("out", mltProp.getEndTimecode("Song")))))
                        }
                    }
                }
            }
        }

    }

    val mlt = MltNode(
        name = "playlist",
        fields = mutableMapOf(Pair("id","main_bin")),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.version")), body = "1.04"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.profile")), body = "atsc_1080p_60"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.rendercategory")), body = "Ultra-High Definition (4K)"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.rendercustomquality")), body = 100),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderendguide")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderexportaudio")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.rendermode")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderplay")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderpreview")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderprofile")), body = "MP4-H265 (HEVC)"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderratio")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderrescale")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderspeed")), body = 8),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderstartguide")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.rendertcoverlay")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.rendertctype")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.rendertwopass")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.guides")), body = mltProp.getGuidesProperty()),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderurl")), body = mltProp.getFileName(
                SongOutputFile.VIDEO)),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","xml_retain")), body = 1),
            entries
        )
    )

    return mlt
}