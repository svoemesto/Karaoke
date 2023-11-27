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

fun getMlt(param: Map<String, Any?>): MltNode {

    val songVersion = param["SONG_VERSION"] as SongVersion
    val countVoices = (param["COUNT_VOICES"] as Int)
    val countFingerboards = param["VOICE0_COUNT_FINGERBOARDS"] as Int

    val countAudioTracks = songVersion.producers.filter { it.isAudio }.sumOf { it.coeffStatic + it.coeffVoice * countVoices }
    val countVideoTracks = songVersion.producers.filter { it.isVideo }.sumOf { it.coeffStatic + it.coeffVoice * countVoices }

    val body = mutableListOf<MltNode>()

    body.add(getMltProfile(param))
    body.add(getMltConsumer(param))

    for (voiceId in 0 until countVoices) {
        songVersion.producers.forEach { type ->
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {

                when(type) {
                    ProducerType.FINGERBOARD -> {
                        for (indexFingerboard in (countFingerboards-1) downTo 0 ) {
                            val pct = producerTypeClass[type] ?: return@forEach
                            val pctInstance = pct
                                .getDeclaredConstructor(*arrayOf(Map::class.java, Int::class.java))
                                .newInstance(*arrayOf(param, indexFingerboard))
                            val producer = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "producer" } ?: return@forEach
                            val resultProducer = producer.invoke(pctInstance) as MltNode
                            body.add(resultProducer)
                        }
                    }
                    ProducerType.AUDIOVOCAL,
                    ProducerType.AUDIOMUSIC,
                    ProducerType.AUDIOSONG,
                    ProducerType.AUDIOBASS,
                    ProducerType.AUDIODRUMS -> {
                        val pct = producerTypeClass[type] ?: return@forEach
                        val pctInstance = pct
                            .getDeclaredConstructor(*arrayOf(Map::class.java, ProducerType::class.java))
                            .newInstance(*arrayOf(param, type))
                        val producer = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "producer" } ?: return@forEach
                        val resultProducer = producer.invoke(pctInstance) as MltNode
                        body.add(resultProducer)
                    }
                    ProducerType.FILLCOLORSONGTEXT -> {
                        val pctEven = producerTypeClass[type] ?: return@forEach
                        val pctOdd = producerTypeClass[type] ?: return@forEach
                        val pctInstanceEven = pctEven
                            .getDeclaredConstructor(*arrayOf(Map::class.java, Boolean::class.java, Int::class.java))
                            .newInstance(*arrayOf(param, true, voiceId))
                        val pctInstanceOdd = pctOdd
                            .getDeclaredConstructor(*arrayOf(Map::class.java, Boolean::class.java, Int::class.java))
                            .newInstance(*arrayOf(param, false, voiceId))

                        val producerEven = pctInstanceEven.javaClass.declaredMethods.firstOrNull() { it.name == "producer" } ?: return@forEach
                        val resultProducerEven = producerEven.invoke(pctInstanceEven) as MltNode
                        body.add(resultProducerEven)

                        val producerOdd = pctInstanceOdd.javaClass.declaredMethods.firstOrNull() { it.name == "producer" } ?: return@forEach
                        val resultProducerOdd = producerOdd.invoke(pctInstanceOdd) as MltNode
                        body.add(resultProducerOdd)
                    }
                    ProducerType.COUNTER -> {
                        for (id in 4 downTo 0 ) {
                            val pct = producerTypeClass[type] ?: return@forEach
                            val pctInstance = pct
                                .getDeclaredConstructor(*arrayOf(Map::class.java, Int::class.java, Int::class.java))
                                .newInstance(*arrayOf(param, id, voiceId))
                            val producer = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "producer" } ?: return@forEach
                            val resultProducer = producer.invoke(pctInstance) as MltNode
                            body.add(resultProducer)
                        }
                    }
                    else -> {
                        val pct = producerTypeClass[type] ?: return@forEach
                        val pctInstance = when(type) {
                            ProducerType.SONGTEXT -> {
                                pct
                                    .getDeclaredConstructor(*arrayOf(Map::class.java, Int::class.java, Boolean::class.java))
                                    .newInstance(*arrayOf(param, voiceId, false))
                            }
                            else -> {
                                pct
                                    .getDeclaredConstructor(*arrayOf(Map::class.java))
                                    .newInstance(*arrayOf(param))
                            }
                        }

                        val producer = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "producer" } ?: return@forEach
                        val resultProducer = producer.invoke(pctInstance) as MltNode
                        body.add(resultProducer)
                    }
                }

            }
        }
    }

    body.add(getMltMainBinPlaylist(param))
    body.add(getMltBlackTrackProducer(param))

    for (voiceId in 0 until countVoices) {
        songVersion.producers.forEach { type ->
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {

                when(type) {
                    ProducerType.AUDIOVOCAL,
                    ProducerType.AUDIOMUSIC,
                    ProducerType.AUDIOSONG,
                    ProducerType.AUDIOBASS,
                    ProducerType.AUDIODRUMS -> {
                        val pct = producerTypeClass[type] ?: return@forEach
                        val pctInstance = pct
                            .getDeclaredConstructor(*arrayOf(Map::class.java, ProducerType::class.java))
                            .newInstance(*arrayOf(param, type))
                        val fileProducer = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "fileProducer" } ?: return@forEach
                        val filePlaylist = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "filePlaylist" } ?: return@forEach
                        val trackPlaylist = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "trackPlaylist" } ?: return@forEach
                        val tractor = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "tractor" } ?: return@forEach
                        val resultFileProducer = fileProducer.invoke(pctInstance) as MltNode
                        val resultFilePlaylist = filePlaylist.invoke(pctInstance) as MltNode
                        val resultTrackPlaylist = trackPlaylist.invoke(pctInstance) as MltNode
                        val resultTractor = tractor.invoke(pctInstance) as MltNode
                        body.add(resultFileProducer)
                        body.add(resultFilePlaylist)
                        body.add(resultTrackPlaylist)
                        body.add(resultTractor)
                    }
                    ProducerType.FINGERBOARD -> {
                        for (indexFingerboard in (countFingerboards-1) downTo 0 ) {

                            val pct = producerTypeClass[type] ?: return@forEach
                            val pctInstance = pct
                                .getDeclaredConstructor(*arrayOf(Map::class.java, Int::class.java))
                                .newInstance(*arrayOf(param, indexFingerboard))
                            val filePlaylist = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "filePlaylist" } ?: return@forEach
                            val trackPlaylist = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "trackPlaylist" } ?: return@forEach
                            val tractor = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "tractor" } ?: return@forEach
                            val resultFilePlaylist = filePlaylist.invoke(pctInstance) as MltNode
                            val resultTrackPlaylist = trackPlaylist.invoke(pctInstance) as MltNode
                            val resultTractor = tractor.invoke(pctInstance) as MltNode
                            body.add(resultFilePlaylist)
                            body.add(resultTrackPlaylist)
                            body.add(resultTractor)

                        }
                    }
                    ProducerType.FILLCOLORSONGTEXT -> {
                        val pctEven = producerTypeClass[type] ?: return@forEach
                        val pctOdd = producerTypeClass[type] ?: return@forEach
                        val pctInstanceEven = pctEven
                            .getDeclaredConstructor(*arrayOf(Map::class.java, Boolean::class.java, Int::class.java))
                            .newInstance(*arrayOf(param, true, voiceId))
                        val pctInstanceOdd = pctOdd
                            .getDeclaredConstructor(*arrayOf(Map::class.java, Boolean::class.java, Int::class.java))
                            .newInstance(*arrayOf(param, false, voiceId))

                        val filePlaylistEven = pctInstanceEven.javaClass.declaredMethods.firstOrNull() { it.name == "filePlaylist" } ?: return@forEach
                        val trackPlaylistEven = pctInstanceEven.javaClass.declaredMethods.firstOrNull() { it.name == "trackPlaylist" } ?: return@forEach
                        val tractorEven = pctInstanceEven.javaClass.declaredMethods.firstOrNull() { it.name == "tractor" } ?: return@forEach
                        val resultFilePlaylistEven = filePlaylistEven.invoke(pctInstanceEven) as MltNode
                        val resultTrackPlaylistEven = trackPlaylistEven.invoke(pctInstanceEven) as MltNode
                        val resultTractorEven = tractorEven.invoke(pctInstanceEven) as MltNode
                        body.add(resultFilePlaylistEven)
                        body.add(resultTrackPlaylistEven)
                        body.add(resultTractorEven)

                        val filePlaylistOdd = pctInstanceOdd.javaClass.declaredMethods.firstOrNull() { it.name == "filePlaylist" } ?: return@forEach
                        val trackPlaylistOdd = pctInstanceOdd.javaClass.declaredMethods.firstOrNull() { it.name == "trackPlaylist" } ?: return@forEach
                        val tractorOdd = pctInstanceOdd.javaClass.declaredMethods.firstOrNull() { it.name == "tractor" } ?: return@forEach
                        val resultFilePlaylistOdd = filePlaylistOdd.invoke(pctInstanceOdd) as MltNode
                        val resultTrackPlaylistOdd = trackPlaylistOdd.invoke(pctInstanceOdd) as MltNode
                        val resultTractorOdd = tractorOdd.invoke(pctInstanceOdd) as MltNode
                        body.add(resultFilePlaylistOdd)
                        body.add(resultTrackPlaylistOdd)
                        body.add(resultTractorOdd)

                    }
                    ProducerType.COUNTER -> {
                        for (id in 4 downTo 0 ) {
                            val pct = producerTypeClass[type] ?: return@forEach
                            val pctInstance = pct
                                .getDeclaredConstructor(*arrayOf(Map::class.java, Int::class.java, Int::class.java))
                                .newInstance(*arrayOf(param, id, voiceId))
                            val filePlaylist = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "filePlaylist" } ?: return@forEach
                            val trackPlaylist = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "trackPlaylist" } ?: return@forEach
                            val tractor = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "tractor" } ?: return@forEach
                            val resultFilePlaylist = filePlaylist.invoke(pctInstance) as MltNode
                            val resultTrackPlaylist = trackPlaylist.invoke(pctInstance) as MltNode
                            val resultTractor = tractor.invoke(pctInstance) as MltNode
                            body.add(resultFilePlaylist)
                            body.add(resultTrackPlaylist)
                            body.add(resultTractor)
                        }
                    }
                    else -> {
                        val pct = producerTypeClass[type] ?: return@forEach
                        val pctInstance = when(type) {
                            ProducerType.SONGTEXT -> {
                                pct
                                    .getDeclaredConstructor(*arrayOf(Map::class.java, Int::class.java, Boolean::class.java))
                                    .newInstance(*arrayOf(param, voiceId, false))
                            }
                            else -> {
                                pct
                                    .getDeclaredConstructor(*arrayOf(Map::class.java))
                                    .newInstance(*arrayOf(param))
                            }
                        }

                        val filePlaylist = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "filePlaylist" } ?: return@forEach
                        val trackPlaylist = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "trackPlaylist" } ?: return@forEach
                        val tractor = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "tractor" } ?: return@forEach
                        val resultFilePlaylist = filePlaylist.invoke(pctInstance) as MltNode
                        val resultTrackPlaylist = trackPlaylist.invoke(pctInstance) as MltNode
                        val resultTractor = tractor.invoke(pctInstance) as MltNode
                        body.add(resultFilePlaylist)
                        body.add(resultTrackPlaylist)
                        body.add(resultTractor)

                    }
                }



                when(type) {

//                    ProducerType.SPLASHSTART -> {
//                        val pct = producerTypeClass[type] ?: return@forEach
//                        val pctInstance = pct
//                            .getDeclaredConstructor(*arrayOf(Map::class.java))
//                            .newInstance(*arrayOf(param))
//                        val filePlaylist = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "filePlaylist" } ?: return@forEach
//                        val trackPlaylist = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "trackPlaylist" } ?: return@forEach
//                        val tractor = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "tractor" } ?: return@forEach
//                        val resultFilePlaylist = filePlaylist.invoke(pctInstance) as MltNode
//                        val resultTrackPlaylist = trackPlaylist.invoke(pctInstance) as MltNode
//                        val resultTractor = tractor.invoke(pctInstance) as MltNode
//                        body.add(resultFilePlaylist)
//                        body.add(resultTrackPlaylist)
//                        body.add(resultTractor)
//                    }

//                    ProducerType.BOOSTY -> {
//                        val mko = MkoBoosty(param)
//                        body.add(mko.filePlaylist())
//                        body.add(mko.trackPlaylist())
//                        body.add(mko.tractor())
//                    }

//                    ProducerType.SONGTEXT -> {
//                        val mko = MkoSongText(param, voiceId)
//                        body.add(mko.filePlaylist())
//                        body.add(mko.trackPlaylist())
//                        body.add(mko.tractor())
//                    }
//
//                    ProducerType.HORIZON -> {
//                        val mko = MkoHorizon(param)
//                        body.add(mko.filePlaylist())
//                        body.add(mko.trackPlaylist())
//                        body.add(mko.tractor())
//                    }
//                    ProducerType.FLASH -> {
//                        val mko = MkoFlash(param)
//                        body.add(mko.filePlaylist())
//                        body.add(mko.trackPlaylist())
//                        body.add(mko.tractor())
//                    }
//                    ProducerType.WATERMARK -> {
//                        val mko = MkoWatermark(param)
//                        body.add(mko.filePlaylist())
//                        body.add(mko.trackPlaylist())
//                        body.add(mko.tractor())
//                    }
//                    ProducerType.PROGRESS -> {
//                        val mko = MkoProgress(param)
//                        body.add(mko.filePlaylist())
//                        body.add(mko.trackPlaylist())
//                        body.add(mko.tractor())
//                    }
//                    ProducerType.FADERTEXT -> {
//                        val mko = MkoFaderText(param)
//                        body.add(mko.filePlaylist())
//                        body.add(mko.trackPlaylist())
//                        body.add(mko.tractor())
//                    }
//                    ProducerType.FADERCHORDS -> {
//                        val mko = MkoFaderChords(param)
//                        body.add(mko.filePlaylist())
//                        body.add(mko.trackPlaylist())
//                        body.add(mko.tractor())
//                    }
//                    ProducerType.BACKCHORDS -> {
//                        val mko = MkoBackChords(param)
//                        body.add(mko.filePlaylist())
//                        body.add(mko.trackPlaylist())
//                        body.add(mko.tractor())
//                    }
//                    ProducerType.FINGERBOARD -> {
//                        for (indexFingerboard in (countFingerboards-1) downTo 0 ) {
//                            val mko = MkoFingerboard(param, indexFingerboard)
//                            body.add(mko.filePlaylist())
//                            body.add(mko.trackPlaylist())
//                            body.add(mko.tractor())
//                        }
//                    }
//                    ProducerType.HEADER -> {
//                        val mko = MkoHeader(param)
//                        body.add(mko.filePlaylist())
//                        body.add(mko.trackPlaylist())
//                        body.add(mko.tractor())
//                    }
//                    ProducerType.BACKGROUND -> {
//                        val mko = MkoBackground(param)
//                        body.add(mko.filePlaylist())
//                        body.add(mko.trackPlaylist())
//                        body.add(mko.tractor())
//                    }
//                    ProducerType.AUDIOVOCAL,
//                    ProducerType.AUDIOMUSIC,
//                    ProducerType.AUDIOSONG,
//                    ProducerType.AUDIOBASS,
//                    ProducerType.AUDIODRUMS -> {
//                        val mko = MkoAudio(param, type)
//                        body.add(mko.fileProducer())
//                        body.add(mko.filePlaylist())
//                        body.add(mko.trackPlaylist())
//                        body.add(mko.tractor())
//                    }

//                    ProducerType.FILLCOLORSONGTEXT -> {
//                        val mkoEven = MkoFillcolorSongtext(param, isEven = true, voiceId)
//                        val mkoOdd = MkoFillcolorSongtext(param, isEven = false, voiceId)
//                        body.add(mkoEven.filePlaylist())
//                        body.add(mkoEven.trackPlaylist())
//                        body.add(mkoEven.tractor())
//                        body.add(mkoOdd.filePlaylist())
//                        body.add(mkoOdd.trackPlaylist())
//                        body.add(mkoOdd.tractor())
//                    }

//                    ProducerType.COUNTER -> {
//                        for (id in 4 downTo 0 ) {
//                            val mko = MkoCounter(param, id, voiceId)
//                            body.add(mko.filePlaylist())
//                            body.add(mko.trackPlaylist())
//                            body.add(mko.tractor())
//                        }
//                    }

                    else -> {}
                }
            }
        }
    }

    body.add(getMltTimelineTractor(param))
    body.addAll(getMltTransitions(countAudioTracks, countVideoTracks))

    val mlt = MltNode(
        name = "mlt",
        fields = mutableMapOf(
            Pair("LC_NUMERIC","C"),
            Pair("producer","main_bin"),
            Pair("version","7.13.0"),
            Pair("root",param["SONG_ROOT_FOLDER"].toString()),
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
