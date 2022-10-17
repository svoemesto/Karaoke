package mlt

import getMltAudioFilePlaylist
import getMltAudioFileProducer
import getMltAudioProducer
import getMltAudioTrackPlaylist
import getMltAudioTractor
import getMltBackChordsFilePlaylist
import getMltBackChordsProducer
import getMltBackChordsTrackPlaylist
import getMltBackChordsTractor
import getMltBackgroundFilePlaylist
import getMltBackgroundProducer
import getMltBackgroundTrackPlaylist
import getMltBackgroundTractor
import getMltBlackTrackProducer
import getMltConsumer
import getMltCounterFilePlaylist
import getMltCounterProducer
import getMltCounterTrackPlaylist
import getMltCounterTractor
import getMltFaderChordsFilePlaylist
import getMltFaderChordsProducer
import getMltFaderChordsTrackPlaylist
import getMltFaderChordsTractor
import getMltFaderTextFilePlaylist
import getMltFaderTextProducer
import getMltFaderTextTrackPlaylist
import getMltFaderTextTractor
import getMltFillColorSongtextEvenProducer
import getMltFillColorSongtextOddProducer
import getMltFillSongtextEvenFilePlaylist
import getMltFillSongtextEvenTrackPlaylist
import getMltFillSongtextEvenTractor
import getMltFillSongtextOddFilePlaylist
import getMltFillSongtextOddTrackPlaylist
import getMltFillSongtextOddTractor
import getMltFingerboardFilePlaylist
import getMltFingerboardProducer
import getMltFingerboardTrackPlaylist
import getMltFingerboardTractor
import getMltFlashFilePlaylist
import getMltFlashProducer
import getMltFlashTrackPlaylist
import getMltFlashTractor
import getMltHeaderFilePlaylist
import getMltHeaderProducer
import getMltHeaderTrackPlaylist
import getMltHeaderTractor
import getMltHorizonFilePlaylist
import getMltHorizonProducer
import getMltHorizonTrackPlaylist
import getMltHorizonTractor
import getMltMainBinPlaylist
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
    val countFingerboards = param["VOICE0_COUNT_FINGERBOARDS"] as Int

    val body = mutableListOf<MltNode>()

    body.add(getMltProfile(param))
    body.add(getMltConsumer(param))

    for (voiceId in 0 until countVoices) {
        songVersion.producers.forEach { type ->
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
                when(type) {
                    ProducerType.SPLASHSTART -> body.add(getMltSplashstartProducer(param, type, voiceId))
                    ProducerType.SONGTEXT -> body.add(getMltSongTextProducer(param, type, voiceId))
                    ProducerType.HORIZON -> body.add(getMltHorizonProducer(param, type, voiceId))
                    ProducerType.FLASH -> body.add(getMltFlashProducer(param, type, voiceId))
                    ProducerType.WATERMARK -> body.add(getMltWatermarkProducer(param, type, voiceId))
                    ProducerType.PROGRESS -> body.add(getMltProgressProducer(param, type, voiceId))
                    ProducerType.FADERTEXT -> body.add(getMltFaderTextProducer(param, type, voiceId))
                    ProducerType.FADERCHORDS -> body.add(getMltFaderChordsProducer(param, type, voiceId))
                    ProducerType.BACKCHORDS -> body.add(getMltBackChordsProducer(param, type, voiceId))
                    ProducerType.FINGERBOARD -> {
                        for (indexFingerboard in 0 until countFingerboards) {
                            body.add(getMltFingerboardProducer(param, type, voiceId, indexFingerboard))
                        }
                    }
                    ProducerType.HEADER -> body.add(getMltHeaderProducer(param, type, voiceId))
                    ProducerType.BACKGROUND -> body.add(getMltBackgroundProducer(param, type, voiceId))
                    ProducerType.AUDIOVOCAL -> body.add(getMltAudioProducer(param, type, voiceId))
                    ProducerType.AUDIOMUSIC -> body.add(getMltAudioProducer(param, type, voiceId))
                    ProducerType.AUDIOSONG -> body.add(getMltAudioProducer(param, type, voiceId))
                    ProducerType.AUDIOBASS -> body.add(getMltAudioProducer(param, type, voiceId))
                    ProducerType.AUDIODRUMS -> body.add(getMltAudioProducer(param, type, voiceId))
                    ProducerType.FILLCOLORSONGTEXT -> {
                        body.add(getMltFillColorSongtextEvenProducer(param, type, voiceId))
                        body.add(getMltFillColorSongtextOddProducer(param, type, voiceId))
                    }
                    ProducerType.COUNTER -> {
                        body.add(getMltCounterProducer(param, 4, type, voiceId))
                        body.add(getMltCounterProducer(param, 3, type, voiceId))
                        body.add(getMltCounterProducer(param, 2, type, voiceId))
                        body.add(getMltCounterProducer(param, 1, type, voiceId))
                        body.add(getMltCounterProducer(param, 0, type, voiceId))
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

                    ProducerType.HORIZON -> {
                        body.add(getMltHorizonFilePlaylist(param, type, voiceId))
                        body.add(getMltHorizonTrackPlaylist(param, type, voiceId))
                        body.add(getMltHorizonTractor(param, type, voiceId))
                    }
                    ProducerType.FLASH -> {
                        body.add(getMltFlashFilePlaylist(param, type, voiceId))
                        body.add(getMltFlashTrackPlaylist(param, type, voiceId))
                        body.add(getMltFlashTractor(param, type, voiceId))
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
                    ProducerType.FADERTEXT -> {
                        body.add(getMltFaderTextFilePlaylist(param, type, voiceId))
                        body.add(getMltFaderTextTrackPlaylist(param, type, voiceId))
                        body.add(getMltFaderTextTractor(param, type, voiceId))
                    }
                    ProducerType.FADERCHORDS -> {
                        body.add(getMltFaderChordsFilePlaylist(param, type, voiceId))
                        body.add(getMltFaderChordsTrackPlaylist(param, type, voiceId))
                        body.add(getMltFaderChordsTractor(param, type, voiceId))
                    }
                    ProducerType.BACKCHORDS -> {
                        body.add(getMltBackChordsFilePlaylist(param, type, voiceId))
                        body.add(getMltBackChordsTrackPlaylist(param, type, voiceId))
                        body.add(getMltBackChordsTractor(param, type, voiceId))
                    }
                    ProducerType.FINGERBOARD -> {
                        for (indexFingerboard in 0 until countFingerboards) {
                            body.add(getMltFingerboardFilePlaylist(param, type, voiceId, indexFingerboard))
                            body.add(getMltFingerboardTrackPlaylist(param, type, voiceId, indexFingerboard))
                            body.add(getMltFingerboardTractor(param, type, voiceId, indexFingerboard))
                        }
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
//
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