package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.model.TransformProperty

data class MkoBackground(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type)

    private val totalLengthFr = mltProp.getBackgroundLengthFr()
    private val songStartTimecode = mltProp.getSongStartTimecode()
    private val totalStartTimecode = mltProp.getTotalStartTimecode()
    private val totalEndTimecode = mltProp.getBackgroundEndTimecode()
    private val totalFadeInTimecode = mltProp.getTotalFadeInTimecode()
    private val totalFadeOutTimecode = mltProp.getTotalFadeOutTimecode()
    private val mkoBackgroundPath = mltProp.getPath(listOf(type))

    override fun producer(): MltNode = mltGenerator
        .producer(
            timecodeIn = totalStartTimecode,
            timecodeOut = totalEndTimecode,
            props = MltNodeBuilder()
                .propertyName("length", totalLengthFr)
                .propertyName("eof", "pause")
                .propertyName("resource", mkoBackgroundPath)
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
                .propertyName("kdenlive:duration", totalEndTimecode)
                .propertyName("kdenlive:clipname", mltGenerator.name)
                .propertyName("kdenlive:folderid", -1)
                .propertyName("kdenlive:clip_type", if (type.isAudio) 1 else 2)
                .propertyName("kdenlive:id", mltGenerator.id)
                .build()
        )


    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
            @Suppress("UNCHECKED_CAST")
            val body = it as MutableList<MltNode>
            body.add(
                mltGenerator.entry(
                    timecodeIn = totalStartTimecode,
                    timecodeOut = totalEndTimecode,
                    nodes = MltNodeBuilder()
                        .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
                        .propertyName("kdenlive:activeeffect", 1)
                        .filterGamma(mltGenerator.nameFilterGamma, MltNodeBuilder()
                            .propertyName("lift_r", "${songStartTimecode}=-0.199985")
                            .propertyName("lift_g", "${songStartTimecode}=-0.199985")
                            .propertyName("lift_b", "${songStartTimecode}=-0.199985")
                            .propertyName("gamma_r", "${songStartTimecode}=0.724987")
                            .propertyName("gamma_g", "${songStartTimecode}=0.724987")
                            .propertyName("gamma_b", "${songStartTimecode}=0.724987")
                            .propertyName("gain_r", "${songStartTimecode}=1")
                            .propertyName("gain_g", "${songStartTimecode}=1")
                            .propertyName("gain_b", "${songStartTimecode}=1")
                            .propertyName("mlt_service", "lift_gamma_gain")
                            .propertyName("kdenlive_id", "lift_gamma_gain")
                            .propertyName("kdenlive:collapsed", 0)
                            .propertyName("rotation", "00:00:00.000=0")
                            .build()
                        )
                        .filterQtblend(mltGenerator.nameFilterQtblend, mainFilePlaylistTransformProperties())
                        .build()
                )
            )
        }
        return result
    }
    override fun mainFilePlaylistTransformProperties(): String {
        val tpStart = TransformProperty(
            time = totalStartTimecode,
            x = 0, y = 0, w = 4096, h = 4096, opacity = 0.0
        )
        val tpFadeIn = TransformProperty(
            time = totalFadeInTimecode,
            x = -13, y = -18, w = 4096, h = 4096, opacity = 1.0
        )
        val tpFadeOut = TransformProperty(
            time = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(totalFadeOutTimecode) - 1000),
            x = -2163, y = -2998, w = 4096, h = 4096, opacity = 1.0
        )
        val tpEnd = TransformProperty(
            time = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(totalEndTimecode) - 1000),
            x = -2176, y = -3016, w = 4096, h = 4096, opacity = 0.0
        )
        val resultListTp = listOf(tpStart, tpFadeIn, tpFadeOut, tpEnd)
        return resultListTp.joinToString(";")
    }

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator
        .tractor(
            timecodeIn = totalStartTimecode,
            timecodeOut = totalEndTimecode
        )


}
