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
            timecodeIn = mltProp.getStartTimecode("Song"),
            timecodeOut = mltProp.getEndTimecode("Total"),
            props = MltNodeBuilder()
                .propertyName("length", mltProp.getLengthFr("Total"))
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
                .propertyName("kdenlive:duration", convertMillisecondsToTimecode(Karaoke.timeSplashScreenLengthMs + Karaoke.timeBoostyLengthMs + convertTimecodeToMilliseconds(mltProp.getEndTimecode("Song"))))
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
                    timecodeIn = mltProp.getStartTimecode("Song"),
                    timecodeOut = mltProp.getEndTimecode("Total"),
                    nodes = MltNodeBuilder()
                        .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
                        .propertyName("kdenlive:activeeffect", 1)
                        .filterGamma(mltGenerator.nameFilterGamma, MltNodeBuilder()
                            .propertyName("lift_r", "${mltProp.getStartTimecode("Song")}=-0.199985")
                            .propertyName("lift_g", "${mltProp.getStartTimecode("Song")}=-0.199985")
                            .propertyName("lift_b", "${mltProp.getStartTimecode("Song")}=-0.199985")
                            .propertyName("gamma_r", "${mltProp.getStartTimecode("Song")}=0.724987")
                            .propertyName("gamma_g", "${mltProp.getStartTimecode("Song")}=0.724987")
                            .propertyName("gamma_b", "${mltProp.getStartTimecode("Song")}=0.724987")
                            .propertyName("gain_r", "${mltProp.getStartTimecode("Song")}=1")
                            .propertyName("gain_g", "${mltProp.getStartTimecode("Song")}=1")
                            .propertyName("gain_b", "${mltProp.getStartTimecode("Song")}=1")
                            .propertyName("mlt_service", "lift_gamma_gain")
                            .propertyName("kdenlive_id", "lift_gamma_gain")
                            .propertyName("kdenlive:collapsed", 0)
                            .propertyName("rotation", "00:00:00.000=0")
                            .build()
                        )
                        .filterQtblend(mltGenerator.nameFilterQtblend, "${mltProp.getStartTimecode("Song")}=0 0 4096 4096 0.000000;${mltProp.getFadeInTimecode("Song")}=-13 -18 4096 4096 1.000000;${convertMillisecondsToTimecode(Karaoke.timeSplashScreenLengthMs + Karaoke.timeBoostyLengthMs - 1000 + convertTimecodeToMilliseconds(mltProp.getFadeOutTimecode("Song")))}=-2163 -2998 4096 4096 1.000000;${convertMillisecondsToTimecode(Karaoke.timeSplashScreenLengthMs + Karaoke.timeBoostyLengthMs + convertTimecodeToMilliseconds(mltProp.getEndTimecode("Song")))}=-2176 -3016 4096 4096 0.000000")
                        .build()
                )
            )
        }
        return result
    }

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator
        .tractor(
            timecodeIn = mltProp.getStartTimecode("Song"),
            timecodeOut = mltProp.getEndTimecode("Total")
        )


}
