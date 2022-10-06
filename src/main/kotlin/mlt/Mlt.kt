package mlt

import getMltAudioFilePlaylist
import getMltAudioFileProducer
import getMltAudioProducer
import getMltAudioTrackPlaylist
import getMltAudioTractor
import getMltBackgroundFilePlaylist
import getMltBackgroundProducer
import getMltBackgroundTrackPlaylist
import getMltBackgroundTractor
import getMltBeatFilePlaylist
import getMltBeatProducer
import getMltBeatTrackPlaylist
import getMltBeatTractor
import getMltBlackTrackProducer
import getMltChordsFilePlaylist
import getMltChordsTrackPlaylist
import getMltChordsTractor
import getMltConsumer
import getMltCounterFilePlaylist
import getMltCounterProducer
import getMltCounterTrackPlaylist
import getMltCounterTractor
import getMltFaderFilePlaylist
import getMltFaderProducer
import getMltFaderTrackPlaylist
import getMltFaderTractor
import getMltFillChordsEvenFilePlaylist
import getMltFillChordsEvenTrackPlaylist
import getMltFillChordsEvenTractor
import getMltFillChordsOddFilePlaylist
import getMltFillChordsOddTrackPlaylist
import getMltFillChordsOddTractor
import getMltFillColorChordsEvenProducer
import getMltFillColorChordsOddProducer
import getMltFillColorSongtextEvenProducer
import getMltFillColorSongtextOddProducer
import getMltFillSongtextEvenFilePlaylist
import getMltFillSongtextEvenTrackPlaylist
import getMltFillSongtextEvenTractor
import getMltFillSongtextOddFilePlaylist
import getMltFillSongtextOddTrackPlaylist
import getMltFillSongtextOddTractor
import getMltHeaderFilePlaylist
import getMltHeaderProducer
import getMltHeaderTrackPlaylist
import getMltHeaderTractor
import getMltHorizonFilePlaylist
import getMltHorizonProducer
import getMltHorizonTrackPlaylist
import getMltHorizonTractor
import getMltMainBinPlaylist
import getMltMicrophoneFilePlaylist
import getMltMicrophoneProducer
import getMltMicrophoneTrackPlaylist
import getMltMicrophoneTractor
import getMltProfile
import getMltProgressFilePlaylist
import getMltProgressProducer
import getMltProgressTrackPlaylist
import getMltProgressTractor
import getMltSongTextFilePlaylist
import getMltSongTextProducer
import getMltSongTextTrackPlaylist
import getMltSongTextTractor
import getMltSplashstartFilePlaylist
import getMltSplashstartProducer
import getMltSplashstartTrackPlaylist
import getMltSplashstartTractor
import getMltWatermarkFilePlaylist
import getMltWatermarkProducer
import getMltWatermarkTrackPlaylist
import getMltWatermarkTractor
import model.MltNode
import model.ProducerType
import model.SongVersion

fun getMlt(param: Map<String, Any?>): MltNode {

    val songVersion = param["SONG_VERSION"] as SongVersion
    val countVoices = (param["COUNT_VOICES"] as Int)
//    var type = ProducerType.NONE

    val body = mutableListOf<MltNode>()

    body.add(getMltProfile(param))
    body.add(getMltConsumer(param))

    for (voiceId in 0 until countVoices) {
        songVersion.producers.forEach { type ->
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
                when(type) {
                    ProducerType.SPLASHSTART -> body.add(getMltSplashstartProducer(param, type, voiceId))
                    ProducerType.SONGTEXT -> body.add(getMltSongTextProducer(param, type, voiceId))
                    ProducerType.CHORDS -> body.add(getMltSongTextProducer(param, type, voiceId))
                    ProducerType.HORIZON -> body.add(getMltHorizonProducer(param, type, voiceId))
                    ProducerType.WATERMARK -> body.add(getMltWatermarkProducer(param, type, voiceId))
                    ProducerType.PROGRESS -> body.add(getMltProgressProducer(param, type, voiceId))
                    ProducerType.FADER -> body.add(getMltFaderProducer(param, type, voiceId))
                    ProducerType.HEADER -> body.add(getMltHeaderProducer(param, type, voiceId))
                    ProducerType.BACKGROUND -> body.add(getMltBackgroundProducer(param, type, voiceId))
                    ProducerType.MICROPHONE -> body.add(getMltMicrophoneProducer(param, type, voiceId))
                    ProducerType.AUDIOVOCAL -> body.add(getMltAudioProducer(param, type, voiceId))
                    ProducerType.AUDIOMUSIC -> body.add(getMltAudioProducer(param, type, voiceId))
                    ProducerType.AUDIOSONG -> body.add(getMltAudioProducer(param, type, voiceId))
                    ProducerType.AUDIOBASS -> body.add(getMltAudioProducer(param, type, voiceId))
                    ProducerType.AUDIODRUMS -> body.add(getMltAudioProducer(param, type, voiceId))
                    ProducerType.FILLCOLORSONGTEXT -> {
                        body.add(getMltFillColorSongtextEvenProducer(param, type, voiceId))
                        body.add(getMltFillColorSongtextOddProducer(param, type, voiceId))
                    }
                    ProducerType.FILLCOLORCHORDS -> {
                        body.add(getMltFillColorChordsEvenProducer(param, type, voiceId))
                        body.add(getMltFillColorChordsOddProducer(param, type, voiceId))
                    }
                    ProducerType.COUNTER -> {
                        body.add(getMltCounterProducer(param, 4, type, voiceId))
                        body.add(getMltCounterProducer(param, 3, type, voiceId))
                        body.add(getMltCounterProducer(param, 2, type, voiceId))
                        body.add(getMltCounterProducer(param, 1, type, voiceId))
                        body.add(getMltCounterProducer(param, 0, type, voiceId))
                    }
                    ProducerType.BEAT -> {
                        body.add(getMltBeatProducer(param, 1, type, voiceId))
                        body.add(getMltBeatProducer(param, 2, type, voiceId))
                        body.add(getMltBeatProducer(param, 3, type, voiceId))
                        body.add(getMltBeatProducer(param, 4, type, voiceId))
                    }
                    else -> {}
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
                    ProducerType.SPLASHSTART -> {
                        body.add(getMltSplashstartFilePlaylist(param, type, voiceId))
                        body.add(getMltSplashstartTrackPlaylist(param, type, voiceId))
                        body.add(getMltSplashstartTractor(param, type, voiceId))
                    }
                    ProducerType.SONGTEXT -> {
                        body.add(getMltSongTextFilePlaylist(param, type, voiceId))
                        body.add(getMltSongTextTrackPlaylist(param, type, voiceId))
                        body.add(getMltSongTextTractor(param, type, voiceId))
                    }
                    ProducerType.CHORDS -> {
                        body.add(getMltChordsFilePlaylist(param, type, voiceId))
                        body.add(getMltChordsTrackPlaylist(param, type, voiceId))
                        body.add(getMltChordsTractor(param, type, voiceId))
                    }
                    ProducerType.HORIZON -> {
                        body.add(getMltHorizonFilePlaylist(param, type, voiceId))
                        body.add(getMltHorizonTrackPlaylist(param, type, voiceId))
                        body.add(getMltHorizonTractor(param, type, voiceId))
                    }
                    ProducerType.WATERMARK -> {
                        body.add(getMltWatermarkFilePlaylist(param, type, voiceId))
                        body.add(getMltWatermarkTrackPlaylist(param, type, voiceId))
                        body.add(getMltWatermarkTractor(param, type, voiceId))
                    }
                    ProducerType.PROGRESS -> {
                        body.add(getMltProgressFilePlaylist(param, type, voiceId))
                        body.add(getMltProgressTrackPlaylist(param, type, voiceId))
                        body.add(getMltProgressTractor(param, type, voiceId))
                    }
                    ProducerType.FADER -> {
                        body.add(getMltFaderFilePlaylist(param, type, voiceId))
                        body.add(getMltFaderTrackPlaylist(param, type, voiceId))
                        body.add(getMltFaderTractor(param, type, voiceId))
                    }
                    ProducerType.HEADER -> {
                        body.add(getMltHeaderFilePlaylist(param, type, voiceId))
                        body.add(getMltHeaderTrackPlaylist(param, type, voiceId))
                        body.add(getMltHeaderTractor(param, type, voiceId))
                    }
                    ProducerType.BACKGROUND -> {
                        body.add(getMltBackgroundFilePlaylist(param, type, voiceId))
                        body.add(getMltBackgroundTrackPlaylist(param, type, voiceId))
                        body.add(getMltBackgroundTractor(param, type, voiceId))
                    }
                    ProducerType.MICROPHONE -> {
                        body.add(getMltMicrophoneFilePlaylist(param, type, voiceId))
                        body.add(getMltMicrophoneTrackPlaylist(param, type, voiceId))
                        body.add(getMltMicrophoneTractor(param, type, voiceId))
                    }
                    ProducerType.AUDIOVOCAL -> {
                        body.add(getMltAudioFileProducer(param, type, voiceId))
                        body.add(getMltAudioFilePlaylist(param, type, voiceId))
                        body.add(getMltAudioTrackPlaylist(param, type, voiceId))
                        body.add(getMltAudioTractor(param, type, voiceId))
                    }
                    ProducerType.AUDIOMUSIC -> {
                        body.add(getMltAudioFileProducer(param, type, voiceId))
                        body.add(getMltAudioFilePlaylist(param, type, voiceId))
                        body.add(getMltAudioTrackPlaylist(param, type, voiceId))
                        body.add(getMltAudioTractor(param, type, voiceId))
                    }
                    ProducerType.AUDIOSONG -> {
                        body.add(getMltAudioFileProducer(param, type, voiceId))
                        body.add(getMltAudioFilePlaylist(param, type, voiceId))
                        body.add(getMltAudioTrackPlaylist(param, type, voiceId))
                        body.add(getMltAudioTractor(param, type, voiceId))
                    }
                    ProducerType.AUDIOBASS -> {
                        body.add(getMltAudioFileProducer(param, type, voiceId))
                        body.add(getMltAudioFilePlaylist(param, type, voiceId))
                        body.add(getMltAudioTrackPlaylist(param, type, voiceId))
                        body.add(getMltAudioTractor(param, type, voiceId))
                    }
                    ProducerType.AUDIODRUMS -> {
                        body.add(getMltAudioFileProducer(param, type, voiceId))
                        body.add(getMltAudioFilePlaylist(param, type, voiceId))
                        body.add(getMltAudioTrackPlaylist(param, type, voiceId))
                        body.add(getMltAudioTractor(param, type, voiceId))
                    }
                    ProducerType.FILLCOLORSONGTEXT -> {
                        body.add(getMltFillSongtextEvenFilePlaylist(param, type, voiceId))
                        body.add(getMltFillSongtextEvenTrackPlaylist(param, type, voiceId))
                        body.add(getMltFillSongtextEvenTractor(param, type, voiceId))
                        body.add(getMltFillSongtextOddFilePlaylist(param, type, voiceId))
                        body.add(getMltFillSongtextOddTrackPlaylist(param, type, voiceId))
                        body.add(getMltFillSongtextOddTractor(param, type, voiceId))
                    }
                    ProducerType.FILLCOLORCHORDS -> {
                        body.add(getMltFillChordsEvenFilePlaylist(param, type, voiceId))
                        body.add(getMltFillChordsEvenTrackPlaylist(param, type, voiceId))
                        body.add(getMltFillChordsEvenTractor(param, type, voiceId))
                        body.add(getMltFillChordsOddFilePlaylist(param, type, voiceId))
                        body.add(getMltFillChordsOddTrackPlaylist(param, type, voiceId))
                        body.add(getMltFillChordsOddTractor(param, type, voiceId))
                    }
                    ProducerType.COUNTER -> {
                        body.add(getMltCounterFilePlaylist(param, 4, type, voiceId))
                        body.add(getMltCounterTrackPlaylist(param, 4, type, voiceId))
                        body.add(getMltCounterTractor(param, 4, type, voiceId))
                        body.add(getMltCounterFilePlaylist(param, 3, type, voiceId))
                        body.add(getMltCounterTrackPlaylist(param, 3, type, voiceId))
                        body.add(getMltCounterTractor(param, 3, type, voiceId))
                        body.add(getMltCounterFilePlaylist(param, 2, type, voiceId))
                        body.add(getMltCounterTrackPlaylist(param, 2, type, voiceId))
                        body.add(getMltCounterTractor(param, 2, type, voiceId))
                        body.add(getMltCounterFilePlaylist(param, 1, type, voiceId))
                        body.add(getMltCounterTrackPlaylist(param, 1, type, voiceId))
                        body.add(getMltCounterTractor(param, 1, type, voiceId))
                        body.add(getMltCounterFilePlaylist(param, 0, type, voiceId))
                        body.add(getMltCounterTrackPlaylist(param, 0, type, voiceId))
                        body.add(getMltCounterTractor(param, 0, type, voiceId))
                    }
                    ProducerType.BEAT -> {
                        body.add(getMltBeatFilePlaylist(param, 4, type, voiceId))
                        body.add(getMltBeatTrackPlaylist(param, 4, type, voiceId))
                        body.add(getMltBeatTractor(param, 4, type, voiceId))
                        body.add(getMltBeatFilePlaylist(param, 3, type, voiceId))
                        body.add(getMltBeatTrackPlaylist(param, 3, type, voiceId))
                        body.add(getMltBeatTractor(param, 3, type, voiceId))
                        body.add(getMltBeatFilePlaylist(param, 2, type, voiceId))
                        body.add(getMltBeatTrackPlaylist(param, 2, type, voiceId))
                        body.add(getMltBeatTractor(param, 2, type, voiceId))
                        body.add(getMltBeatFilePlaylist(param, 1, type, voiceId))
                        body.add(getMltBeatTrackPlaylist(param, 1, type, voiceId))
                        body.add(getMltBeatTractor(param, 1, type, voiceId))
                    }
                    else -> {}
                }
            }
        }
    }

    body.add(getMltTimelineTractor(param))

    val mlt = MltNode(
        name = "mlt",
        fields = mutableMapOf(
            Pair("LC_NUMERIC","C"),
            Pair("producer","main_bin"),
            Pair("version","7.9.0"),
            Pair("root",param["SONG_ROOT_FOLDER"].toString()),
        ),
        body = body
    )
    
    return mlt
}