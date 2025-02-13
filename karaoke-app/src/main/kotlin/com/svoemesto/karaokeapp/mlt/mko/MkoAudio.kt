package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.model.PropertiesMltNodeBuilder

data class MkoAudio(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type)

    private val songLengthFr = mltProp.getSongLengthFr()
    private val audioLengthFr = mltProp.getAudioLengthFr()
    private val mkoAudioPath = mltProp.getPath(listOf(type))
    private val volume = mltProp.getVolume(listOf(type))
    private val songStartTimecode = mltProp.getSongStartTimecode()
    private val audioEndTimecode = mltProp.getAudioEndTimecode()
    private val voiceBlankTimecode = mltProp.getVoiceBlankTimecode()
    override fun producer(): MltNode = mltGenerator
        .producer(
            props = MltNodeBuilder()
                .propertyName("length", songLengthFr)
                .propertyName("eof", "pause")
                .propertyName("resource", mkoAudioPath)
                .propertyName("seekable", 1)
                .propertyName("audio_index", 0)
                .propertyName("video_index", -1)
                .propertyName("mute_on_pause", 0)
                .propertyName("mlt_service", "avformat")
                .propertyName("kdenlive:clipname", mltGenerator.name)
                .propertyName("kdenlive:folderid", -1)
                .propertyName("kdenlive:clip_type", if (type.isAudio) 1 else 2)
                .propertyName("kdenlive:id", mltGenerator.id)
                .propertyName("kdenlive:audio_max0", 185)
                .propertyName("astream", 0)
                .build()
        )

    override fun fileProducer(): MltNode {
        val mlt = MltNode(
            type = type,
            name = "producer",
            fields = PropertiesMltNodeBuilder()
                .id(mltGenerator.nameFileProducer)
                .`in`(songStartTimecode)
                .`out`(audioEndTimecode)
                .build(),
            body = MltNodeBuilder()
                .propertyName("length", audioLengthFr)
                .propertyName("eof", "pause")
                .propertyName("resource", mkoAudioPath)
                .propertyName("seekable", 1)
                .propertyName("audio_index", 0)
                .propertyName("video_index", -1)
                .propertyName("mute_on_pause", 0)
                .propertyName("mlt_service", "avformat-novalidate")
                .propertyName("kdenlive:clipname")
                .propertyName("kdenlive:folderid", -1)
                .propertyName("kdenlive:clip_type", 1)
                .propertyName("kdenlive:id", mltGenerator.id)
                .propertyName("xml", "was here")
                .propertyName("set.test_audio", 0)
                .propertyName("set.test_image", 1)
                .build()
        )

        return mlt
    }

    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
            val body = it as MutableList<MltNode>
            body.addAll(MltNodeBuilder().blank(voiceBlankTimecode).build())
            body.add(
                mltGenerator.entry(
                    id = mltGenerator.nameFileProducer,
                    nodes = MltNodeBuilder()
                        .propertyName("kdenlive:id", mltGenerator.id)
                        .propertyName("kdenlive:activeeffect", 0)
                        .filterVolume(mltGenerator.nameFilterVolume, mainFilePlaylistTransformProperties())
                        .build()
                )
            )
        }
        return result
    }
    override fun mainFilePlaylistTransformProperties(): String {
        return volume
    }
    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()


}

