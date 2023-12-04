package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType

data class MkoBackground(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type)

    override fun producer(): MltNode = mltGenerator
        .producer(
            timecodeIn = mltProp.getTotalStartTimecode(),
            timecodeOut = mltProp.getTotalEndTimecode(),
            props = MltNodeBuilder()
                .propertyName("length", mltProp.getTotalLengthFr())
                .propertyName("eof", "pause")
                .propertyName("resource", mltProp.getPath(listOf(type, voiceId)))
                .propertyName("ttl", 25)
                .propertyName("aspect_ratio", 1)
                .propertyName("meta.media.progressive", 1)
                .propertyName("seekable", 1)
                .propertyName("format", 1)
                .propertyName("meta.media.width", 4096)
                .propertyName("meta.media.height", 4096)
                .propertyName("mlt_service", "qimage")
                .propertyName("progressive", 1)
                .propertyName("force_reload", 0)
                .propertyName("kdenlive:duration", mltProp.getTotalEndTimecode())
                .propertyName("kdenlive:clipname", mltGenerator.name)
                .propertyName("kdenlive:folderid", -1)
                .propertyName("kdenlive:clip_type", if (type.isAudio) 1 else 2)
                .propertyName("kdenlive:id", mltGenerator.id)
                .build()
        )


    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
            val body = it as MutableList<MltNode>
            body.add(
                mltGenerator.entry(
                    timecodeIn = mltProp.getTotalStartTimecode(),
                    timecodeOut = mltProp.getTotalEndTimecode(),
                    nodes = MltNodeBuilder()
                        .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
                        .propertyName("kdenlive:activeeffect", 1)
                        .filterGamma(mltGenerator.nameFilterGamma, MltNodeBuilder()
                            .propertyName("lift_r", "${mltProp.getSongStartTimecode()}=-0.199985")
                            .propertyName("lift_g", "${mltProp.getSongStartTimecode()}=-0.199985")
                            .propertyName("lift_b", "${mltProp.getSongStartTimecode()}=-0.199985")
                            .propertyName("gamma_r", "${mltProp.getSongStartTimecode()}=0.724987")
                            .propertyName("gamma_g", "${mltProp.getSongStartTimecode()}=0.724987")
                            .propertyName("gamma_b", "${mltProp.getSongStartTimecode()}=0.724987")
                            .propertyName("gain_r", "${mltProp.getSongStartTimecode()}=1")
                            .propertyName("gain_g", "${mltProp.getSongStartTimecode()}=1")
                            .propertyName("gain_b", "${mltProp.getSongStartTimecode()}=1")
                            .propertyName("mlt_service", "lift_gamma_gain")
                            .propertyName("kdenlive_id", "lift_gamma_gain")
                            .propertyName("kdenlive:collapsed", 0)
                            .propertyName("rotation", "00:00:00.000=0")
                            .build()
                        )
                        .filterQtblend(mltGenerator.nameFilterQtblend, "${mltProp.getTotalStartTimecode()}=0 0 4096 4096 0.000000;${mltProp.getTotalFadeInTimecode()}=-13 -18 4096 4096 1.000000;${mltProp.getTotalFadeOutTimecode()}=-2163 -2998 4096 4096 1.000000;${mltProp.getTotalEndTimecode()}=-2176 -3016 4096 4096 0.000000")
                        .build()
                )
            )
        }
        return result
    }

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator
        .tractor(
            timecodeIn = mltProp.getTotalStartTimecode(),
            timecodeOut = mltProp.getTotalEndTimecode()
        )


}
