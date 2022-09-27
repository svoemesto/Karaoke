package mlt

import getMltAudioMusicFilePlaylist
import getMltAudioMusicFileProducer
import getMltAudioMusicProducer
import getMltAudioMusicTrackPlaylist
import getMltAudioMusicTractor
import getMltAudioSongFilePlaylist
import getMltAudioSongFileProducer
import getMltAudioSongProducer
import getMltAudioSongTrackPlaylist
import getMltAudioSongTractor
import getMltAudioVocalFilePlaylist
import getMltAudioVocalFileProducer
import getMltAudioVocalProducer
import getMltAudioVocalTrackPlaylist
import getMltAudioVocalTractor
import getMltBackgroundFilePlaylist
import getMltBackgroundProducer
import getMltBackgroundTrackPlaylist
import getMltBackgroundTractor
import getMltBeatFilePlaylist
import getMltBeatProducer
import getMltBeatTrackPlaylist
import getMltBeatTractor
import getMltBlackTrackProducer
import getMltConsumer
import getMltCounterFilePlaylist
import getMltCounterProducer
import getMltCounterTrackPlaylist
import getMltCounterTractor
import getMltFillColorProducer
import getMltFillEvenFilePlaylist
import getMltFillEvenTrackPlaylist
import getMltFillEvenTractor
import getMltFillOddFilePlaylist
import getMltFillOddTrackPlaylist
import getMltFillOddTractor
import getMltHeaderFilePlaylist
import getMltHeaderProducer
import getMltHeaderTrackPlaylist
import getMltHeaderTractor
import getMltHorizonFilePlaylist
import getMltHorizonProducer
import getMltHorizonTrackPlaylist
import getMltHorizonTractor
import getMltLogotypeFilePlaylist
import getMltLogotypeProducer
import getMltLogotypeTrackPlaylist
import getMltLogotypeTractor
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
import getMltWatermarkFilePlaylist
import getMltWatermarkProducer
import getMltWatermarkTrackPlaylist
import getMltWatermarkTractor
import model.MltNode

fun getMlt(param: Map<String, Any?>): MltNode {
    val mlt = MltNode(
        name = "mlt",
        fields = mutableMapOf(
            Pair("LC_NUMERIC","C"),
            Pair("producer","main_bin"),
            Pair("version","7.9.0"),
            Pair("root",param["SONG_ROOT_FOLDER"].toString()),
        ),
        body = mutableListOf(
            getMltProfile(param),
            getMltConsumer(param),
            getMltSongTextProducer(param),
            getMltHorizonProducer(param),
            getMltWatermarkProducer(param),
            getMltProgressProducer(param),
            getMltFillColorProducer(param),
            getMltLogotypeProducer(param),
            getMltHeaderProducer(param),
            getMltBackgroundProducer(param),
            getMltMicrophoneProducer(param),
            getMltCounterProducer(param,4),
            getMltCounterProducer(param,3),
            getMltCounterProducer(param,2),
            getMltCounterProducer(param,1),
            getMltCounterProducer(param,0),
            getMltBeatProducer(param,1),
            getMltBeatProducer(param,2),
            getMltBeatProducer(param,3),
            getMltBeatProducer(param,4),
            getMltAudioSongProducer(param),
            getMltAudioMusicProducer(param),
            getMltAudioVocalProducer(param),
            getMltMainBinPlaylist(param),
            getMltBlackTrackProducer(param),
            getMltAudioVocalFileProducer(param),
            getMltAudioVocalFilePlaylist(param),
            getMltAudioVocalTrackPlaylist(param),
            getMltAudioVocalTractor(param),
            getMltAudioMusicFileProducer(param),
            getMltAudioMusicFilePlaylist(param),
            getMltAudioMusicTrackPlaylist(param),
            getMltAudioMusicTractor(param),
            getMltAudioSongFileProducer(param),
            getMltAudioSongFilePlaylist(param),
            getMltAudioSongTrackPlaylist(param),
            getMltAudioSongTractor(param),
            getMltBackgroundFilePlaylist(param),
            getMltBackgroundTrackPlaylist(param),
            getMltBackgroundTractor(param),
            getMltMicrophoneFilePlaylist(param),
            getMltMicrophoneTrackPlaylist(param),
            getMltMicrophoneTractor(param),
            getMltHorizonFilePlaylist(param),
            getMltHorizonTrackPlaylist(param),
            getMltHorizonTractor(param),
            getMltProgressFilePlaylist(param),
            getMltProgressTrackPlaylist(param),
            getMltProgressTractor(param),
            getMltFillEvenFilePlaylist(param),
            getMltFillEvenTrackPlaylist(param),
            getMltFillEvenTractor(param),
            getMltFillOddFilePlaylist(param),
            getMltFillOddTrackPlaylist(param),
            getMltFillOddTractor(param),
            getMltSongTextFilePlaylist(param),
            getMltSongTextTrackPlaylist(param),
            getMltSongTextTractor(param),
            getMltHeaderFilePlaylist(param),
            getMltHeaderTrackPlaylist(param),
            getMltHeaderTractor(param),
            getMltLogotypeFilePlaylist(param),
            getMltLogotypeTrackPlaylist(param),
            getMltLogotypeTractor(param),
            getMltBeatFilePlaylist(param,4),
            getMltBeatTrackPlaylist(param,4),
            getMltBeatTractor(param,4),
            getMltBeatFilePlaylist(param,3),
            getMltBeatTrackPlaylist(param,3),
            getMltBeatTractor(param,3),
            getMltBeatFilePlaylist(param,2),
            getMltBeatTrackPlaylist(param,2),
            getMltBeatTractor(param,2),
            getMltBeatFilePlaylist(param,1),
            getMltBeatTrackPlaylist(param,1),
            getMltBeatTractor(param,1),
            getMltCounterFilePlaylist(param,4),
            getMltCounterTrackPlaylist(param,4),
            getMltCounterTractor(param,4),
            getMltCounterFilePlaylist(param,3),
            getMltCounterTrackPlaylist(param,3),
            getMltCounterTractor(param,3),
            getMltCounterFilePlaylist(param,2),
            getMltCounterTrackPlaylist(param,2),
            getMltCounterTractor(param,2),
            getMltCounterFilePlaylist(param,1),
            getMltCounterTrackPlaylist(param,1),
            getMltCounterTractor(param,1),
            getMltCounterFilePlaylist(param,0),
            getMltCounterTrackPlaylist(param,0),
            getMltCounterTractor(param,0),
            getMltWatermarkFilePlaylist(param),
            getMltWatermarkTrackPlaylist(param),
            getMltWatermarkTractor(param),
            getMltTimelineTractor(param),
        )
    )
    
    return mlt
}