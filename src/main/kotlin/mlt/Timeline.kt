package mlt

import model.MltNode
import model.ProducerType
import model.SongVersion

fun getMltTimelineTractor(param: Map<String, Any?>): MltNode {

    val songVersion = param["SONG_VERSION"] as SongVersion
    val countVoices = (param["COUNT_VOICES"] as Int)
    val body = mutableListOf<MltNode>()
    body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","black_track"))))

//    var type = ProducerType.NONE
    for (groupId in 0 until countVoices) {

        songVersion.producers.forEach { type ->
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                if (type.suffixes.isEmpty() && type.ids.isEmpty()) {
                    body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer", "tractor_${type.text}${groupId}"))))
                } else if (type.suffixes.isEmpty() && type.ids.isNotEmpty()) {
                    for (id in type.ids) {
                        body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer", "tractor_${type.text}${groupId}${id}"))))
                    }
                } else if (type.suffixes.isNotEmpty() && type.ids.isEmpty()) {
                    for (suffix in type.suffixes) {
                        body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer", "tractor_${type.text}${suffix}${groupId}"))))
                    }
                } else {
                    for (id in type.ids) {
                        for (suffix in type.suffixes) {
                            body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer", "tractor_${type.text}${suffix}${groupId}${id}"))))
                        }
                    }
                }

            }
        }
    }

    body.add(
        MltNode(name = "filter",
            fields = mutableMapOf(
                Pair("id","filter_subtitles")
            ),
            body = mutableListOf(
                MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "avfilter.subtitles"),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","internal_added")), body = 237),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","disable")), body = 1),
            )
        )
    )

    val mlt = MltNode(
        name = "tractor",
        fields = mutableMapOf(
            Pair("id","tractor_timeline"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = body
    )

    return mlt
}

