package com.svoemesto.karaokeapp.mlt

import com.svoemesto.karaokeapp.mlt.mko.*
import getMltBlackTrackProducer
import getMltConsumer
import getMltMainBinPlaylist
import getMltProfile
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.model.SongVersion
import com.svoemesto.karaokeapp.producerTypeClass

fun getMlt(mltProp: MltProp): MltNode {

    val songVersion = mltProp.getSongVersion()
    val countVoices = mltProp.getCountVoices()
    val countFingerboards = mltProp.getCountFingerboards(0)
    
    val countAudioTracks = songVersion.producers.filter { it.isAudio }.sumOf { it.coeffStatic + it.coeffVoice * countVoices }
    val countVideoTracks = songVersion.producers.filter { it.isVideo }.sumOf { it.coeffStatic + it.coeffVoice * countVoices }

    val body = mutableListOf<MltNode>()

    body.add(getMltProfile())
    body.add(getMltConsumer(mltProp))

    for (voiceId in 0 until countVoices) {
        songVersion.producers.forEach { type ->
            for (childId in 0 until if (type.ids.isEmpty()) 1 else type.ids.size) {
                if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {

                    val pct = producerTypeClass[type] ?: return@forEach
                    val pctInstance = pct
                        .getDeclaredConstructor(*arrayOf(MltProp::class.java, ProducerType::class.java, Int::class.java, Int::class.java))
                        .newInstance(*arrayOf(mltProp, type, voiceId, childId))
                    val resultProducer = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "producer" }?.let {it.invoke(pctInstance) as MltNode? }
                    val resultProducerBlackTrack = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "producerBlackTrack" }?.let {it.invoke(pctInstance) as MltNode? }
                    val resultFileProducer = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "fileProducer" }?.let {it.invoke(pctInstance) as MltNode? }
                    val resultFilePlaylist = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "filePlaylist" }?.let {it.invoke(pctInstance) as MltNode? }
                    val resultTrackPlaylist = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "trackPlaylist" }?.let {it.invoke(pctInstance) as MltNode? }
                    val resultTractor = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "tractor" }?.let {it.invoke(pctInstance) as MltNode? }
                    val resultTractorSequence = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "tractorSequence" }?.let {it.invoke(pctInstance) as MltNode? }

                    resultProducer?.let { body.add(it) }
                    resultProducerBlackTrack?.let { body.add(it) }
                    resultFileProducer?.let { body.add(it) }
                    resultTractorSequence?.let { body.add(it) }
                    resultFilePlaylist?.let { body.add(it) }
                    resultTrackPlaylist?.let { body.add(it) }
                    resultTractor?.let { body.add(it) }

                }


            }


        }
    }

    val mlt = MltNode(
        name = "mlt",
        fields = mutableMapOf(
            Pair("LC_NUMERIC","C"),
            Pair("producer","main_bin"),
            Pair("version","7.21.0"),
            Pair("root",mltProp.getRootFolder("Song")),
        ),
        body = body
    )

    return mlt
}

fun getMltTransitions(countAudioTracks: Int, countVideoTracks: Int): List<MltNode> {
    val trans = mutableListOf<MltNode>()
    var index = 0
    for (audioTrack in 0 until countAudioTracks) {
        trans.add(
            MltNode(
                name = "transition",
                fields = mutableMapOf(Pair("id","transition$index")),
                body = mutableListOf(
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","a_track")), body = 0),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","b_track")), body = index+1),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "mix"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive_id")), body = "mix"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","internal_added")), body = 237),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","always_active")), body = 1),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","accepts_blanks")), body = 1),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","sum")), body = 1)
                )
            )
        )
        index++
    }

    for (videoTrack in 0 until countVideoTracks) {
        trans.add(
            MltNode(
                name = "transition",
                fields = mutableMapOf(Pair("id","transition$index")),
                body = mutableListOf(
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","a_track")), body = 0),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","b_track")), body = index+1),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","version")), body = "0.1"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "frei0r.cairoblend"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","always_active")), body = 1),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","internal_added")), body = 237),
                )
            )
        )
        index++
    }

    return trans
}
