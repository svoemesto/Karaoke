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
            id = MltGenerator.nameProducerBlackTrack(type, voiceId),
            props = MltNodeBuilder()
                .propertyName("length", mltProp.getLengthFr("Song"))
                .propertyName("eof", "pause")
                .propertyName("resource", 0)
                .propertyName("aspect_ratio", 1)
                .propertyName("mlt_service", "color")
                .propertyName("kdenlive:duration", mltProp.getEndTimecode("Song"))
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
//            .nodes(
//                {
//                    val result: MutableList<MltNode> = mutableListOf()
//                    mltProp.getSongVersion().producers.filter { !it.isSequence }. forEach { typeI ->
//                        for (voiceI in 0 until mltProp.getCountVoices()) {
//                            for (childI in 0 until if (typeI.ids.isEmpty()) 1 else typeI.ids.size) {
//                                if ((typeI.onlyOne && voiceI == 0) || !typeI.onlyOne ) {
//                                    result.add(MltNode(name = "entry", fields = mutableMapOf(
//                                        "producer" to MltGenerator.nameProducer(typeI, voiceI, childI),
//                                        "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song"))))
//                                }
//                            }
//                        }
//
//
//                    }
//                    result
//                }()
//            )

            .node(MltNode(name = "entry", fields = mutableMapOf(
                "producer" to MltGenerator.nameProducer(ProducerType.BACKGROUND, voiceId),
                "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song"))))
            .node(MltNode(name = "entry", fields = mutableMapOf(
                "producer" to MltGenerator.nameProducer(ProducerType.BOOSTY, voiceId),
                "in" to "00:00:00.000", "out" to mltProp.getEndTimecode(listOf(ProducerType.BOOSTY, voiceId)))))
            .node(MltNode(name = "entry", fields = mutableMapOf(
                "producer" to MltGenerator.nameProducer(ProducerType.HORIZON, voiceId),
                "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song"))))
            .node(MltNode(name = "entry", fields = mutableMapOf(
                "producer" to MltGenerator.nameProducer(ProducerType.FLASH, voiceId),
                "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song"))))
            .node(MltNode(name = "entry", fields = mutableMapOf(
                "producer" to MltGenerator.nameProducer(ProducerType.PROGRESS, voiceId),
                "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song"))))
            .node(MltNode(name = "entry", fields = mutableMapOf(
                "producer" to MltGenerator.nameProducer(ProducerType.FADERTEXT, voiceId),
                "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song"))))
            .node(MltNode(name = "entry", fields = mutableMapOf(
                "producer" to MltGenerator.nameProducer(ProducerType.HEADER, voiceId),
                "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song"))))
            .node(MltNode(name = "entry", fields = mutableMapOf(
                "producer" to MltGenerator.nameProducer(ProducerType.WATERMARK, voiceId),
                "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song"))))
            .node(MltNode(name = "entry", fields = mutableMapOf(
                "producer" to MltGenerator.nameProducer(ProducerType.SPLASHSTART, voiceId),
                "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song"))))
            .node(MltNode(name = "entry", fields = mutableMapOf(
                "producer" to MltGenerator.nameProducer(ProducerType.AUDIOMUSIC, voiceId),
                "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song"))))
            .node(MltNode(name = "entry", fields = mutableMapOf(
                "producer" to MltGenerator.nameProducer(ProducerType.AUDIOVOCAL, voiceId),
                "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song"))))
            .nodes(
                (0 until mltProp.getCountVoices()).map {
                    MltNode(name = "entry", fields = mutableMapOf(
                        "producer" to MltGenerator.nameProducer(ProducerType.SONGTEXT, it),
                        "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song")))
                }
            )
            .nodes(
                (0 until mltProp.getCountVoices()).map {
                    MltNode(name = "entry", fields = mutableMapOf(
                        "producer" to MltGenerator.nameProducer(ProducerType.FILLCOLORSONGTEXT, it, 0),
                        "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song")))
                }
            )
            .nodes(
                (0 until mltProp.getCountVoices()).map {
                    MltNode(name = "entry", fields = mutableMapOf(
                        "producer" to MltGenerator.nameProducer(ProducerType.FILLCOLORSONGTEXT, it, 1),
                        "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song")))
                }
            )
            .nodes(
                (0 until mltProp.getCountVoices()).map {
                    MltNode(name = "entry", fields = mutableMapOf(
                        "producer" to MltGenerator.nameProducer(ProducerType.COUNTER, it, 0),
                        "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song")))
                }
            )
            .nodes(
                (0 until mltProp.getCountVoices()).map {
                    MltNode(name = "entry", fields = mutableMapOf(
                        "producer" to MltGenerator.nameProducer(ProducerType.COUNTER, it, 1),
                        "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song")))
                }
            )
            .nodes(
                (0 until mltProp.getCountVoices()).map {
                    MltNode(name = "entry", fields = mutableMapOf(
                        "producer" to MltGenerator.nameProducer(ProducerType.COUNTER, it, 2),
                        "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song")))
                }
            )
            .nodes(
                (0 until mltProp.getCountVoices()).map {
                    MltNode(name = "entry", fields = mutableMapOf(
                        "producer" to MltGenerator.nameProducer(ProducerType.COUNTER, it, 3),
                        "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song")))
                }
            )
            .nodes(
                (0 until mltProp.getCountVoices()).map {
                    MltNode(name = "entry", fields = mutableMapOf(
                        "producer" to MltGenerator.nameProducer(ProducerType.COUNTER, it, 4),
                        "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song")))
                }
            )


            .nodes(
                (0 until mltProp.getCountVoices()).map {
                    MltNode(name = "entry", fields = mutableMapOf(
                        "producer" to "{${mltProp.getUUID(listOf(ProducerType.VOICE, it))}}",
                        "in" to "00:00:00.000", "out" to "00:00:00.000"))
                }
            )
            .nodes(
                (0 until mltProp.getCountVoices()).map {
                    MltNode(name = "entry", fields = mutableMapOf(
                        "producer" to "{${mltProp.getUUID(listOf(ProducerType.COUNTERS, it))}}",
                        "in" to "00:00:00.000", "out" to "00:00:00.000"))
                }
            )
            .nodes(
                (0 until mltProp.getCountVoices()).map {
                    MltNode(name = "entry", fields = mutableMapOf(
                        "producer" to "{${mltProp.getUUID(listOf(ProducerType.FILLCOLORSONGTEXTS, it))}}",
                        "in" to "00:00:00.000", "out" to "00:00:00.000"))
                }
            )
            .node(
                MltNode(name = "entry", fields = mutableMapOf(
                    "producer" to "{${mltProp.getUUID(listOf(ProducerType.VOICES, voiceId))}}",
                    "in" to "00:00:00.000", "out" to "00:00:00.000"))
            )
            .node(
                MltNode(name = "entry", fields = mutableMapOf(
                    "producer" to "{${mltProp.getUUID(listOf(ProducerType.MAINBIN, voiceId))}}",
                    "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song")))
            )
            .build()

    )

    override fun tractor():  MltNode = MltNode(
        type = type,
        name = "tractor",
        fields = PropertiesMltNodeBuilder()
            .id("tractor_project")
            .`in`("00:00:00.000")
            .`out`(mltProp.getEndTimecode("Song"))
            .build(),
        body = MltNodeBuilder()
            .propertyName("kdenlive:projectTractor", 1)
            .node(
                MltNode(name = "track", fields = mutableMapOf(
                    "producer" to "{${mltProp.getUUID(listOf(ProducerType.MAINBIN, voiceId))}}",
                    "in" to "00:00:00.000", "out" to mltProp.getEndTimecode("Song")))
            )
            .build()
        )

    override fun tractorSequence(): MltNode = mltGenerator
        .tractor(
            id = "{${mltProp.getUUID(listOf(type, voiceId))}}",
            body = MltNodeBuilder()
                .propertyName("kdenlive:sequenceproperties.hasAudio", 1)
                .propertyName("kdenlive:sequenceproperties.hasVideo", 1)
                .propertyName("kdenlive:clip_type", 2)
                .propertyName("kdenlive:duration", mltProp.getEndTimecode("Song"))
                .propertyName("kdenlive:clipname", "Основной клип")
                .propertyName("kdenlive:description")
                .propertyName("kdenlive:uuid", "{${mltProp.getUUID(listOf(type, voiceId))}}")
                .propertyName("kdenlive:producer_type", 17)
                .propertyName("kdenlive:folderid", -1)
                .propertyName("kdenlive:id", mltGenerator.id)
                .propertyName("kdenlive:sequenceproperties.activeTrack", 0)
                .propertyName("kdenlive:sequenceproperties.documentuuid", "{${mltProp.getUUID(listOf(ProducerType.MAINBIN, voiceId))}}")
                .propertyName("kdenlive:sequenceproperties.tracks", 12)
                .propertyName("kdenlive:sequenceproperties.tracksCount", 12)
                .propertyName("kdenlive:sequenceproperties.verticalzoom", 1)
                .propertyName("kdenlive:sequenceproperties.zonein", 0)
                .propertyName("kdenlive:sequenceproperties.zoneout", 75)
                .propertyName("kdenlive:sequenceproperties.zoom", 8)
                .propertyName("kdenlive:sequenceproperties.groups", "[]")
                .propertyName("kdenlive:sequenceproperties.guides", "[]")
                .propertyName("kdenlive:docproperties.renderurl", mltProp.getFileName(SongOutputFile.VIDEO))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameProducerBlackTrack(ProducerType.MAINBIN, voiceId))))
//                .nodes(
//                    {
//                        val result: MutableList<MltNode> = mutableListOf()
//                        mltProp.getSongVersion().producers.filter { !it.isSequence }. forEach {
//                            result.add(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(it, voiceId))))
//                        }
//                        result
//                    }()
//                )

                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.AUDIOMUSIC, voiceId))))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.AUDIOVOCAL, voiceId))))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.BACKGROUND, voiceId))))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.SPLASHSTART, voiceId))))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.BOOSTY, voiceId))))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.HORIZON, voiceId))))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.FLASH, voiceId))))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.PROGRESS, voiceId))))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.VOICES, voiceId))))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.FADERTEXT, voiceId))))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.HEADER, voiceId))))
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(ProducerType.WATERMARK, voiceId))))

                .transitionsAndFilters(mltGenerator.name, 12)
                .build()
        )

}
