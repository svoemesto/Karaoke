package com.svoemesto.karaokeapp.mlt

import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.model.SongVersion

fun getMltTimelineTractor(mltProp: MltProp): MltNode {


    val songVersion = mltProp.getSongVersion()
    val countVoices = mltProp.getCountVoices()
    val countFingerboards = mltProp.getCountFingerboards(0)

    val body = mutableListOf<MltNode>()
    body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","black_track"))))

//    var type = ProducerType.NONE

    songVersion.producers.forEach { type ->
        for (voiceId in 0 until countVoices) {
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
                if (type == ProducerType.FINGERBOARD) {
                    for (indexFingerboard in (countFingerboards-1) downTo 0 ) {
                        val mltGenerator = MltGenerator(mltProp, type, voiceId, indexFingerboard)
                        body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer", mltGenerator.nameTractor))))
                    }
                } else {
                    if (type.ids.isEmpty()) {
                        val mltGenerator = MltGenerator(mltProp, type, voiceId)
                        body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer", mltGenerator.nameTractor))))
                    } else {
                        for (id in type.ids) {
                            val mltGenerator = MltGenerator(mltProp, type, voiceId, id)
                            body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer", mltGenerator.nameTractor))))
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
            Pair("in",mltProp.getStartTimecode("Song")),
            Pair("out",mltProp.getEndTimecode("Song"))
        ),
        body = body
    )

    return mlt
}

