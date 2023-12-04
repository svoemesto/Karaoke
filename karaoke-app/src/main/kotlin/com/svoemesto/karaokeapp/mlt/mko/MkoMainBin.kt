package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.xmldata
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.*

data class MkoMainBin(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type)

    override fun producerBlackTrack(): MltNode = mltGenerator
        .producer(
            timecodeIn = mltProp.getTimelineStartTimecode(),
            timecodeOut = mltProp.getTimelineEndTimecode(),
            id = MltGenerator.nameProducerBlackTrack(type, voiceId),
            props = MltNodeBuilder()
                .propertyName("length", 2147483647)
//                .propertyName("length", mltProp.getTimelineLengthFr())
                .propertyName("eof", "pause")
                .propertyName("resource", "black")
                .propertyName("aspect_ratio", 1)
                .propertyName("mlt_service", "color")
                .propertyName("kdenlive:duration", mltProp.getTotalEndTimecode())
                .propertyName("mlt_image_format", "rgba")
                .propertyName("kdenlive:playlistid", "black_track")
                .propertyName("set.test_audio", 0)
                .build()
        )

    override fun trackPlaylist(): MltNode = MltNode(
        type = type,
        name = "playlist",
        fields = PropertiesMltNodeBuilder().id("main_bin").build(),
        body = MltNodeBuilder()
            .propertyName("kdenlive:folder.-1.21", "Клипы")
            .propertyName("kdenlive:sequenceFolder", 21)
            .propertyName("kdenlive:folder.-1.${mltProp.getId(ProducerType.VOICES)}", "${ProducerType.VOICES.name}")
            .nodes({
                    val result = MltNodeBuilder()
                    for (voiceI in 0 until mltProp.getCountVoices()) {
                        result.propertyName("kdenlive:folder.${mltProp.getId(ProducerType.VOICES)}.${mltProp.getId(listOf(ProducerType.VOICE, voiceI))}", "${ProducerType.VOICE.name}${voiceI}")
                        result.propertyName("kdenlive:folder.${mltProp.getId(listOf(ProducerType.VOICE, voiceI))}.${mltProp.getId(listOf(ProducerType.COUNTERS, voiceI))}", "${ProducerType.COUNTERS.name}")
                        result.propertyName("kdenlive:folder.${mltProp.getId(listOf(ProducerType.VOICE, voiceI))}.${mltProp.getId(listOf(ProducerType.FILLCOLORSONGTEXTS, voiceI))}", "${ProducerType.FILLCOLORSONGTEXTS.name}")
                    }
                    result.build()
                    result.nodes
                }()
            )
            .propertyName("kdenlive:docproperties.audioChannels", 2)
            .propertyName("kdenlive:docproperties.browserurl", mltProp.getRootFolder("Song"))
            .propertyName("kdenlive:docproperties.compositing", 1)
            .propertyName("kdenlive:docproperties.guides", "[]")
            .propertyName("kdenlive:docproperties.guidesCategories", "[]")
            .propertyName("kdenlive:docproperties.kdenliveversion", "23.08.3")
            .propertyName("kdenlive:docproperties.previewextension")
            .propertyName("kdenlive:docproperties.previewparameters")
            .propertyName("kdenlive:docproperties.profile", "atsc_1080p_60")
            .propertyName("kdenlive:docproperties.rendercategory", "Ultra-High Definition (4K)")
            .propertyName("kdenlive:docproperties.rendercustomquality", 100)
            .propertyName("kdenlive:docproperties.renderendguide", -1)
            .propertyName("kdenlive:docproperties.renderexportaudio", 0)
            .propertyName("kdenlive:docproperties.rendermode", 0)
            .propertyName("kdenlive:docproperties.renderplay", 0)
            .propertyName("kdenlive:docproperties.renderpreview", 0)
            .propertyName("kdenlive:docproperties.renderprofile", "MP4-H265 (HEVC)")
            .propertyName("kdenlive:docproperties.renderratio", 1)
            .propertyName("kdenlive:docproperties.renderrescale", 0)
            .propertyName("kdenlive:docproperties.renderspeed", 8)
            .propertyName("kdenlive:docproperties.renderstartguide", -1)
            .propertyName("kdenlive:docproperties.rendertcoverlay", 0)
            .propertyName("kdenlive:docproperties.rendertctype", -1)
            .propertyName("kdenlive:docproperties.rendertwopass", 0)
            .propertyName("kdenlive:docproperties.seekOffset", 30000)
            .propertyName("kdenlive:docproperties.uuid", "{${mltProp.getUUID(listOf(ProducerType.MAINBIN, voiceId))}}")
            .propertyName("kdenlive:docproperties.version", "1.1")
            .propertyName("kdenlive:binZoom", 4)
            .propertyName("kdenlive:documentnotes")
            .propertyName("kdenlive:docproperties.opensequences",
                ProducerType.values()
                    .filter { it.isSequence }
                    .map { "{${mltProp.getUUID(listOf(it, 0))}}" }
                    .toList()
                    .joinToString(";")
                )
            .propertyName("kdenlive:docproperties.activetimeline", "{${mltProp.getUUID(listOf(ProducerType.MAINBIN, voiceId))}}")
            .propertyName("xml_retain", 1)


            .nodes(
                {
                    val result: MutableList<MltNode> = mutableListOf()
                    mltProp.getSongVersion().producers.sortedByLevelsDesc().forEach { typeI ->
                        for (voiceI in 0 until mltProp.getCountVoices()) {
                            for (childI in 0 until if (typeI.ids.isEmpty()) 1 else typeI.ids.size) {
                                if ((typeI.onlyOne && voiceI == 0) || !typeI.onlyOne ) {
                                    result.add(MltNode(name = "entry", fields = mutableMapOf(
                                        "producer" to if (typeI.isSequence) "{${mltProp.getUUID(listOf(typeI, voiceI))}}" else MltGenerator.nameProducer(typeI, voiceI, childI),
                                        "in" to "00:00:00.000", "out" to (if (typeI==ProducerType.MAINBIN) mltProp.getTotalEndTimecode() else mltProp.getSongEndTimecode()))))
                                }
                            }
                        }


                    }
                    result
                }()
            )
            .build()

    )

    override fun tractor():  MltNode = MltNode(
        type = type,
        name = "tractor",
        fields = PropertiesMltNodeBuilder()
            .id("tractor_project")
            .`in`("00:00:00.000")
            .`out`(mltProp.getTotalEndTimecode())
            .build(),
        body = MltNodeBuilder()
            .propertyName("kdenlive:projectTractor", 1)
            .node(
                MltNode(name = "track", fields = mutableMapOf(
                    "producer" to "{${mltProp.getUUID(listOf(ProducerType.MAINBIN, voiceId))}}",
                    "in" to "00:00:00.000", "out" to mltProp.getTotalEndTimecode()))
            )
            .build()
        )

    override fun tractorSequence(): MltNode = mltGenerator
        .tractor(
            id = "{${mltProp.getUUID(listOf(type, voiceId))}}",
            timecodeIn = mltProp.getTimelineStartTimecode(),
            timecodeOut = mltProp.getTimelineEndTimecode(),
            body = MltNodeBuilder()
                .propertyName("kdenlive:sequenceproperties.hasAudio", 1)
                .propertyName("kdenlive:sequenceproperties.hasVideo", 1)
                .propertyName("kdenlive:clip_type", 2)
                .propertyName("kdenlive:duration", mltProp.getTotalEndTimecode())
                .propertyName("kdenlive:clipname", "Основной клип")
                .propertyName("kdenlive:description")
                .propertyName("kdenlive:uuid", "{${mltProp.getUUID(listOf(type, voiceId))}}")
                .propertyName("kdenlive:producer_type", 17)
                .propertyName("kdenlive:folderid", -1)
                .propertyName("kdenlive:id", mltGenerator.id)
                .propertyName("kdenlive:sequenceproperties.activeTrack", 0)
                .propertyName("kdenlive:sequenceproperties.documentuuid", "{${mltProp.getUUID(listOf(ProducerType.MAINBIN, voiceId))}}")
                .propertyName("kdenlive:sequenceproperties.tracks", mltProp.getCountAllTracks())
                .propertyName("kdenlive:sequenceproperties.tracksCount", mltProp.getCountAllTracks())
                .propertyName("kdenlive:sequenceproperties.verticalzoom", 1)
                .propertyName("kdenlive:sequenceproperties.zonein", 0)
                .propertyName("kdenlive:sequenceproperties.zoneout", 75)
                .propertyName("kdenlive:sequenceproperties.zoom", 8)
                .propertyName("kdenlive:sequenceproperties.groups", "[]")
                .propertyName("kdenlive:sequenceproperties.guides", "[]")
                .propertyName("kdenlive:docproperties.renderurl", mltProp.getFileName(SongOutputFile.VIDEO))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameProducerBlackTrack(ProducerType.MAINBIN, voiceId))))

                .nodes(
                    {
                        val result: MutableList<MltNode> = mutableListOf()
                        mltProp.getSongVersion().producersInMainBin. forEach {
                            result.add(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(it, voiceId))))
                        }
                        result
                    }()
                )

//                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.AUDIOMUSIC, voiceId))))
//                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.AUDIOVOCAL, voiceId))))
//                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.BACKGROUND, voiceId))))
//                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.SPLASHSTART, voiceId))))
//                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.BOOSTY, voiceId))))
//                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.HORIZON, voiceId))))
//                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.FLASH, voiceId))))
//                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.PROGRESS, voiceId))))
//                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.VOICES, voiceId))))
//                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.FADERTEXT, voiceId))))
//                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.HEADER, voiceId))))
//                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.WATERMARK, voiceId))))

                .transitionsAndFilters(mltGenerator.name, mltProp.getCountAudioTracks(), mltProp.getCountAllTracks() - mltProp.getCountAudioTracks())
                .build()
        )

}
