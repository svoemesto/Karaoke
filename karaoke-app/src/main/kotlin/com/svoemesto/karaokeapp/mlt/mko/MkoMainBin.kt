package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.mlt.getMisList
import com.svoemesto.karaokeapp.model.*

data class MkoMainBin(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type)

    private val timelineStartTimecode = mltProp.getTimelineStartTimecode()
    private val timelineEndTimecode = mltProp.getTimelineEndTimecode()
    private val totalEndTimecode = mltProp.getBackgroundEndTimecode()
    private val songEndTimecode = mltProp.getSongEndTimecode()
    private val countAllTracks = mltProp.getCountAllTracks()
    private val countAudioTracks = mltProp.getCountAudioTracks()
    private val songRootFolder = mltProp.getRootFolder("Song")
    private var mainBinUUID = mltProp.getUUID(listOf(ProducerType.MAINBIN))
    private var songOutputFileName = mltProp.getFileName(SongOutputFile.VIDEO)
    private var songVersion = mltProp.getSongVersion()

    override fun producerBlackTrack(): MltNode = mltGenerator
        .producer(
            timecodeIn = timelineStartTimecode,
            timecodeOut = timelineEndTimecode,
            id = MltGenerator.nameProducerBlackTrack(type),
            props = MltNodeBuilder()
                .propertyName("length", 2147483647)
                .propertyName("eof", "pause")
                .propertyName("resource", "black")
                .propertyName("aspect_ratio", 1)
                .propertyName("mlt_service", "color")
                .propertyName("kdenlive:duration", totalEndTimecode)
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
//                        result.propertyName("kdenlive:folder.${mltProp.getId(listOf(ProducerType.VOICE, voiceI))}.${mltProp.getId(listOf(ProducerType.SCROLLERS, voiceI))}", "${ProducerType.SCROLLERS.name}")
//                        result.propertyName("kdenlive:folder.${mltProp.getId(listOf(ProducerType.VOICE, voiceI))}.${mltProp.getId(listOf(ProducerType.FILLCOLORSONGTEXTS, voiceI))}", "${ProducerType.FILLCOLORSONGTEXTS.name}")
                        result.propertyName("kdenlive:folder.${mltProp.getId(listOf(ProducerType.VOICE, voiceI))}.${mltProp.getId(listOf(ProducerType.LINES, voiceI))}", "${ProducerType.LINES.name}")
                    }
                    result.build()
                    result.nodes
                }()
            )
            .propertyName("kdenlive:docproperties.audioChannels", 2)
            .propertyName("kdenlive:docproperties.browserurl", songRootFolder)
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
            .propertyName("kdenlive:docproperties.uuid", "{${mainBinUUID}}")
            .propertyName("kdenlive:docproperties.version", "1.1")
            .propertyName("kdenlive:binZoom", 4)
            .propertyName("kdenlive:documentnotes")
            .propertyName("kdenlive:docproperties.opensequences",
                ProducerType.values()
                    .filter { it.isSequence && it.onlyOne }
                    .map { "{${mltProp.getUUID(listOf(it))}}" }
                    .toList()
                    .joinToString(";")
                )
            .propertyName("kdenlive:docproperties.activetimeline", "{${mainBinUUID}}")
            .propertyName("xml_retain", 1)


            .nodes(
                {
                    val result: MutableList<MltNode> = mutableListOf()
                    val misList = getMisList(mltProp)
                    misList.forEach { mis ->

                        val type = mis.type
                        val voiceId = mis.voiceId
                        val childId = mis.childId
                        val elementId = mis.elementId

                        val uuid = if (type.isSequence) {
                            if (elementId >= 0) {
                                mltProp.getUUID(listOf(type, voiceId, childId, elementId))
                            } else if (childId >=0) {
                                mltProp.getUUID(listOf(type, voiceId, childId))
                            } else if (voiceId >=0) {
                                mltProp.getUUID(listOf(type, voiceId))
                            } else {
                                mltProp.getUUID(listOf(type))
                            }
                        } else ""
                        if (type.isSequence && uuid == "") {
                            println("ПУСТОЙ UUID для ${type.name} voice = ${voiceId} child = ${childId} element = ${elementId}")
                        }
                        result.add(MltNode(name = "entry", fields = mutableMapOf(
                            "producer" to if (type.isSequence) "{${uuid}}" else MltGenerator.nameProducer(type, Integer.max(mis.voiceId, 0), Integer.max(mis.childId, 0), Integer.max(mis.elementId, 0)),
                            "in" to "00:00:00.000", "out" to (if (type==ProducerType.MAINBIN) totalEndTimecode else songEndTimecode))))

                    }
//                    mltProp.getSongVersion().producers.sortedByLevelsDesc().forEach { typeI ->
//                        for (voiceI in 0 until mltProp.getCountVoices()) {
//                            val countChilds = if (!typeI.isCalculatedCount) {
//                                if (typeI.ids.isEmpty()) 1 else typeI.ids.size
//                            } else {
//                                mltProp.getCountChilds(listOf(typeI, voiceId))
//                            }
//                            for (childI in 0 until countChilds) {
//                                if ((typeI.onlyOne && voiceI == 0) || !typeI.onlyOne ) {
//                                    val uuid = if (typeI.isSequence) {
//                                        val tmp = mltProp.getUUID(listOf(typeI, voiceI, childI))
//                                        if (tmp == "") {
//                                            val tmp2 = mltProp.getUUID(listOf(typeI, voiceI))
//                                            if (tmp2 == "") {
//                                                mltProp.getUUID(listOf(typeI))
//                                            } else {
//                                                tmp2
//                                            }
//                                        } else {
//                                            tmp
//                                        }
//                                    } else ""
//                                    if (typeI.isSequence && uuid == "") {
//                                        println("ПУСТОЙ UUID для ${typeI.name} voice = ${voiceI} child = ${childI}")
//                                    }
//                                    result.add(MltNode(name = "entry", fields = mutableMapOf(
//                                        "producer" to if (typeI.isSequence) "{${uuid}}" else MltGenerator.nameProducer(typeI, voiceI, childI),
//                                        "in" to "00:00:00.000", "out" to (if (typeI==ProducerType.MAINBIN) mltProp.getTotalEndTimecode() else mltProp.getSongEndTimecode()))))
//                                }
//                            }
//                        }
//
//
//                    }
                    result
                }()
            )
            .build()

    )

    override fun mainFilePlaylistTransformProperties(): String = ""
    override fun tractor():  MltNode = MltNode(
        type = type,
        name = "tractor",
        fields = PropertiesMltNodeBuilder()
            .id("tractor_project")
            .`in`("00:00:00.000")
            .`out`(totalEndTimecode)
            .build(),
        body = MltNodeBuilder()
            .propertyName("kdenlive:projectTractor", 1)
            .node(
                MltNode(name = "track", fields = mutableMapOf(
                    "producer" to "{${mainBinUUID}}",
                    "in" to "00:00:00.000", "out" to totalEndTimecode))
            )
            .build()
        )

    override fun tractorSequence(): MltNode = mltGenerator
        .tractor(
            id = "{${mainBinUUID}}",
            timecodeIn = timelineStartTimecode,
            timecodeOut = timelineEndTimecode,
            body = MltNodeBuilder()
                .propertyName("kdenlive:sequenceproperties.hasAudio", 1)
                .propertyName("kdenlive:sequenceproperties.hasVideo", 1)
                .propertyName("kdenlive:clip_type", 2)
                .propertyName("kdenlive:duration", totalEndTimecode)
                .propertyName("kdenlive:clipname", "Основной клип")
                .propertyName("kdenlive:description")
                .propertyName("kdenlive:uuid", "{${mainBinUUID}}")
                .propertyName("kdenlive:producer_type", 17)
                .propertyName("kdenlive:folderid", -1)
                .propertyName("kdenlive:id", mltGenerator.id)
                .propertyName("kdenlive:sequenceproperties.activeTrack", 0)
                .propertyName("kdenlive:sequenceproperties.documentuuid", "{${mainBinUUID}}")
                .propertyName("kdenlive:sequenceproperties.tracks", countAllTracks)
                .propertyName("kdenlive:sequenceproperties.tracksCount", countAllTracks)
                .propertyName("kdenlive:sequenceproperties.verticalzoom", 1)
                .propertyName("kdenlive:sequenceproperties.zonein", 0)
                .propertyName("kdenlive:sequenceproperties.zoneout", 75)
                .propertyName("kdenlive:sequenceproperties.zoom", 8)
                .propertyName("kdenlive:sequenceproperties.groups", "[]")
                .propertyName("kdenlive:sequenceproperties.guides", "[]")
                .propertyName("kdenlive:docproperties.renderurl", songOutputFileName)
                .node(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameProducerBlackTrack(ProducerType.MAINBIN))))

                .nodes(
                    {
                        val result: MutableList<MltNode> = mutableListOf()
                        songVersion.producersInMainBin. forEach {
                            result.add(MltNode(name = "track", fields = mutableMapOf("producer" to MltGenerator.nameTractor(it, voiceId))))
                        }
                        result
                    }()
                )


                .transitionsAndFilters(mltGenerator.name, countAudioTracks, countAllTracks - countAudioTracks)
                .build()
        )

}
