package com.svoemesto.karaokeapp.mlt

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.convertMillisecondsToTimecode
import com.svoemesto.karaokeapp.convertTimecodeToMilliseconds
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.model.PropertiesMltNodeBuilder

data class MltGenerator(
    var mltProp: MltProp,
    var type: ProducerType,
    var voiceId: Int = 0,
    var childId: Int = 0
) {

    companion object {
        fun name(type: ProducerType, voiceId: Int = 0, childId: Int = 0) = MltGenerator(MltProp(), type, voiceId, childId).name
        fun namePlaylistFile(type: ProducerType, voiceId: Int = 0, childId: Int = 0) = MltGenerator(MltProp(), type, voiceId, childId).namePlaylistFile
        fun namePlaylistTrack(type: ProducerType, voiceId: Int = 0, childId: Int = 0) = MltGenerator(MltProp(), type, voiceId, childId).namePlaylistTrack
        fun nameTractor(type: ProducerType, voiceId: Int = 0, childId: Int = 0) = MltGenerator(MltProp(), type, voiceId, childId).nameTractor
        fun nameProducer(type: ProducerType, voiceId: Int = 0, childId: Int = 0) = MltGenerator(MltProp(), type, voiceId, childId).nameProducer
        fun nameFileProducer(type: ProducerType, voiceId: Int = 0, childId: Int = 0) = MltGenerator(MltProp(), type, voiceId, childId).nameFileProducer
        fun nameFilterVolume(type: ProducerType, voiceId: Int = 0, childId: Int = 0) = MltGenerator(MltProp(), type, voiceId, childId).nameFilterVolume
        fun nameFilterPanner(type: ProducerType, voiceId: Int = 0, childId: Int = 0) = MltGenerator(MltProp(), type, voiceId, childId).nameFilterPanner
        fun nameFilterAudiolevel(type: ProducerType, voiceId: Int = 0, childId: Int = 0) = MltGenerator(MltProp(), type, voiceId, childId).nameFilterAudiolevel
        fun nameFilterQtblend(type: ProducerType, voiceId: Int = 0, childId: Int = 0) = MltGenerator(MltProp(), type, voiceId, childId).nameFilterQtblend
        fun nameFilterGamma(type: ProducerType, voiceId: Int = 0, childId: Int = 0) = MltGenerator(MltProp(), type, voiceId, childId).nameFilterGamma
        fun nameProducerBlackTrack(type: ProducerType, voiceId: Int = 0, childId: Int = 0) = MltGenerator(MltProp(), type, voiceId, childId).nameProducerBlackTrack
    }

    val id: Int get() = (if (type.ids.isEmpty()) mltProp.getId(listOf(type, voiceId)) else mltProp.getId(listOf(type, voiceId, childId))) + voiceId*1000 + childId*10000
    val name: String get() = "${type.text.uppercase()}${if (voiceId==0 && type.coeffVoice==0) "" else "_V${voiceId}"}${if (childId==0 && type.ids.isEmpty()) "" else "_C${childId}"}"

    val namePlaylistFile: String get() = "playlist_${name.lowercase()}${voiceId}_file"
    val namePlaylistTrack: String get() = "playlist_${name.lowercase()}${voiceId}_track"
    val nameTractor: String get() = "tractor_${name.lowercase()}${voiceId}"
    val nameProducer: String get() = "producer_${name.lowercase()}${voiceId}"
    val nameProducerBlackTrack: String get() = "producer_${name.lowercase()}${voiceId}_black_track"
    val nameFileProducer: String get() = "producer_${name.lowercase()}${voiceId}_file"
    val nameFilterVolume: String get() = "filter_${name.lowercase()}${voiceId}_volume"
    val nameFilterPanner: String get() = "filter_${name.lowercase()}${voiceId}_panner"
    val nameFilterAudiolevel: String get() = "filter_${name.lowercase()}${voiceId}_audiolevel"
    val nameFilterQtblend: String get() = "filter_${name.lowercase()}${voiceId}_qtblend"
    val nameFilterGamma: String get() = "filter_${name.lowercase()}${voiceId}_gamma"

    fun defaultProducerPropertiesForMltService(mltServiceName: String) : MutableList<MltNode> {
        return when (mltServiceName) {
            "kdenlivetitle" -> {
                MltNodeBuilder()
                    .propertyName("eof", "pause")
                    .propertyName("resource")
                    .propertyName("meta.media.progressive", 1)
                    .propertyName("aspect_ratio", 1)
                    .propertyName("seekable", 1)
                    .propertyName("mlt_service", "kdenlivetitle")
                    .propertyName("progressive", 1)
                    .propertyName("force_reload", 0)
                    .propertyName("kdenlive:clipname", name)
                    .propertyName("kdenlive:clip_type", if (type.isAudio) 1 else 2)
                    .propertyName("kdenlive:id", id)
                    .build()
            }
            else -> mutableListOf()
        }
    }

    val propsTractor: MutableList<MltNode> get() {
        val result = MltNodeBuilder()
            .propertyName("kdenlive:trackheight", 67)
            .propertyName("kdenlive:timeline_active", 1)
            .propertyName("kdenlive:collapsed", 28)
            .propertyName("kdenlive:track_name", name)
            .propertyName("kdenlive:thumbs_format")
            .propertyName("kdenlive:audio_rec")
            .build()
        if (type.isAudio) result.addAll(MltNodeBuilder().propertyName("kdenlive:audio_track", 1).build())
        return result
    }

    val propProducer: MutableList<MltNode> get() =
        if (type.isAudio) {
            MltNodeBuilder()
                .propertyName("audio_index", 0)
                .propertyName("video_index", -1)
                .propertyName("mute_on_pause", 1)
                .build()
        } else {
            MltNodeBuilder()
                .propertyName("aspect_ratio", 1)
                .build()
        }


    fun entry(
        timecodeIn: String = mltProp.getSongStartTimecode(),
        timecodeOut: String = mltProp.getSongEndTimecode(),
        nodes: MutableList<MltNode> = mutableListOf(),
        id: String? = null
    ): MltNode {
        return MltNode(
            type = type,
            name = "entry",
            fields = PropertiesMltNodeBuilder()
                .producer(id ?: nameProducer)
                .`in`(timecodeIn)
                .`out`(timecodeOut)
                .build(),
            body = nodes,
            comment = "entry $nameProducer"
        )
    }

    fun producer(
        timecodeIn: String = mltProp.getSongStartTimecode(),
        timecodeOut: String = mltProp.getSongEndTimecode(),
        props: MutableList<MltNode> = mutableListOf(),
        id: String? = null
    ): MltNode {
        return MltNode(
            type = type,
            name = "producer",
            fields = PropertiesMltNodeBuilder()
                .id(id ?: nameProducer)
                .`in`(timecodeIn)
                .`out`(timecodeOut)
                .build(),
            body = props,
            comment = "producer $nameProducer"
        )
    }
    fun trackPlaylist(): MltNode = MltNode(
        type = type,
        name = "playlist",
        fields = PropertiesMltNodeBuilder().id(namePlaylistTrack).build(),
        body = if (type.isAudio) {
            MltNodeBuilder().propertyName("kdenlive:audio_track", 1).build()
        } else null,
        comment = "playlist $namePlaylistTrack"
    )

    fun filePlaylist(): MltNode = MltNode(
        type = type,
        name = "playlist",
        fields = PropertiesMltNodeBuilder().id(namePlaylistFile).build(),
        body = if (type.isAudio) {
            MltNodeBuilder().propertyName("kdenlive:audio_track", 1).build()
        } else mutableListOf(),
        comment = "playlist $namePlaylistFile"
    )

    fun tractor(
        timecodeIn: String = mltProp.getTotalStartTimecode(),
        timecodeOut: String = mltProp.getTotalEndTimecode(),
        id: String = nameTractor,
        body: MutableList<MltNode> = tractorBody(),
    ): MltNode = MltNode(
        type = type,
        name = "tractor",
        fields = PropertiesMltNodeBuilder()
            .id(id)
            .`in`(timecodeIn)
            .`out`(timecodeOut)
            .build(),
        body = body,
        comment = "tractor $namePlaylistFile"
    )
    private fun tractorBody(): MutableList<MltNode> {
        val result: MutableList<MltNode> = MltNodeBuilder(propsTractor)
            .track(
                PropertiesMltNodeBuilder()
                    .hideByType(type)
                    .producer(namePlaylistFile)
                    .build())
            .track(
                PropertiesMltNodeBuilder()
                    .hideByType(type)
                    .producer(namePlaylistTrack)
                    .build())
            .build()

        if (type.isAudio) {
            result.addAll(
                MltNodeBuilder()
                    .filterVolume(nameFilterVolume)
                    .filterPanner(nameFilterPanner)
                    .filterAudiolevel(nameFilterAudiolevel)
                    .build()
            )
        }

        return result

    }

}
