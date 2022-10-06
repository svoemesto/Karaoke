import model.MltNode
import model.ProducerType
import model.SongVersion

fun getMltMainBinPlaylist(param: Map<String, Any?>): MltNode {

    val songVersion = param["SONG_VERSION"] as SongVersion

    val countGroups = (param["COUNT_VOICES"] as Int)
    val entries = mutableListOf<MltNode>()
//    var type = ProducerType.NONE
    for (groupId in 0 until countGroups) {

        songVersion.producers.forEach { type ->
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                if (type.suffixes.isEmpty() && type.ids.isEmpty()) {
                    entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
                } else if (type.suffixes.isEmpty() && type.ids.isNotEmpty()) {
                    for (id in type.ids) {
                        entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}${id}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
                    }
                } else if (type.suffixes.isNotEmpty() && type.ids.isEmpty()) {
                    for (suffix in type.suffixes) {
                        entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${suffix}${groupId}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
                    }
                } else {
                    for (id in type.ids) {
                        for (suffix in type.suffixes) {
                            entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${suffix}${groupId}${id}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
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
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.guides")), body = param["GUIDES_PROPERTY"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderurl")), body = param["SONG_VIDEO_FILENAME"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","xml_retain")), body = 1),
            entries
        )
    )

    return mlt
}