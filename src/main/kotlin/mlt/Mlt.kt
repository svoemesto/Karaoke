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
import getMltConsumer
import getMltCounterFilePlaylist
import getMltCounterProducer
import getMltCounterTrackPlaylist
import getMltCounterTractor
import getMltFillColorEvenProducer
import getMltFillColorOddProducer
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
import model.ProducerType

fun getMlt(param: Map<String, Any?>): MltNode {

    val countGroups = (param["COUNT_VOICES"] as Int)
    var type = ProducerType.NONE

    val body = mutableListOf<MltNode>()

    body.add(getMltProfile(param))
    body.add(getMltConsumer(param))

    for (groupId in 0 until countGroups) {
        type = ProducerType.SONGTEXT
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(getMltSongTextProducer(param, type, groupId))
            }
        }

        type = ProducerType.HORIZON
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(getMltHorizonProducer(param, type, groupId))
            }
        }

        type = ProducerType.WATERMARK
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(getMltWatermarkProducer(param, type, groupId))
            }
        }

        type = ProducerType.PROGRESS
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(getMltProgressProducer(param, type, groupId))
            }
        }

        type = ProducerType.FILLCOLOR
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(getMltFillColorEvenProducer(param, type, groupId))
                body.add(getMltFillColorOddProducer(param, type, groupId))
            }
        }

        type = ProducerType.LOGOTYPE
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(getMltLogotypeProducer(param, type, groupId))
            }
        }

        type = ProducerType.HEADER
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(getMltHeaderProducer(param, type, groupId))
            }
        }

        type = ProducerType.BACKGROUND
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(getMltBackgroundProducer(param, type, groupId))
            }
        }

        type = ProducerType.MICROPHONE
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(getMltMicrophoneProducer(param, type, groupId))
            }
        }

        type = ProducerType.COUNTER
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(getMltCounterProducer(param, 4, type, groupId))
                body.add(getMltCounterProducer(param, 3, type, groupId))
                body.add(getMltCounterProducer(param, 2, type, groupId))
                body.add(getMltCounterProducer(param, 1, type, groupId))
                body.add(getMltCounterProducer(param, 0, type, groupId))
            }
        }

        type = ProducerType.BEAT
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(getMltBeatProducer(param, 1, type, groupId))
                body.add(getMltBeatProducer(param, 2, type, groupId))
                body.add(getMltBeatProducer(param, 3, type, groupId))
                body.add(getMltBeatProducer(param, 4, type, groupId))
            }
        }

        type = ProducerType.AUDIOVOCAL
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(getMltAudioProducer(param, type, groupId))
            }
        }

        type = ProducerType.AUDIOMUSIC
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(getMltAudioProducer(param, type, groupId))
            }
        }

        type = ProducerType.AUDIOSONG
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(getMltAudioProducer(param, type, groupId))
            }
        }

    }

    body.add(getMltMainBinPlaylist(param))
    body.add(getMltBlackTrackProducer(param))

    for (voiceId in 0 until countGroups) {

        type = ProducerType.AUDIOVOCAL
        if ((param["${type.text.uppercase()}${voiceId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
                body.add(getMltAudioFileProducer(param, type, voiceId))
                body.add(getMltAudioFilePlaylist(param, type, voiceId))
                body.add(getMltAudioTrackPlaylist(param, type, voiceId))
                body.add(getMltAudioTractor(param, type, voiceId))
            }
        }

        type = ProducerType.AUDIOMUSIC
        if ((param["${type.text.uppercase()}${voiceId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
                body.add(getMltAudioFileProducer(param, type, voiceId))
                body.add(getMltAudioFilePlaylist(param, type, voiceId))
                body.add(getMltAudioTrackPlaylist(param, type, voiceId))
                body.add(getMltAudioTractor(param, type, voiceId))
            }
        }

        type = ProducerType.AUDIOSONG
        if ((param["${type.text.uppercase()}${voiceId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
                body.add(getMltAudioFileProducer(param, type, voiceId))
                body.add(getMltAudioFilePlaylist(param, type, voiceId))
                body.add(getMltAudioTrackPlaylist(param, type, voiceId))
                body.add(getMltAudioTractor(param, type, voiceId))
            }
        }

        type = ProducerType.BACKGROUND
        if ((param["${type.text.uppercase()}${voiceId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
                body.add(getMltBackgroundFilePlaylist(param, type, voiceId))
                body.add(getMltBackgroundTrackPlaylist(param, type, voiceId))
                body.add(getMltBackgroundTractor(param, type, voiceId))
            }
        }

        type = ProducerType.MICROPHONE
        if ((param["${type.text.uppercase()}${voiceId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
                body.add(getMltMicrophoneFilePlaylist(param, type, voiceId))
                body.add(getMltMicrophoneTrackPlaylist(param, type, voiceId))
                body.add(getMltMicrophoneTractor(param, type, voiceId))
            }
        }

        type = ProducerType.HORIZON
        if ((param["${type.text.uppercase()}${voiceId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
                body.add(getMltHorizonFilePlaylist(param, type, voiceId))
                body.add(getMltHorizonTrackPlaylist(param, type, voiceId))
                body.add(getMltHorizonTractor(param, type, voiceId))
            }
        }

        type = ProducerType.PROGRESS
        if ((param["${type.text.uppercase()}${voiceId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
                body.add(getMltProgressFilePlaylist(param, type, voiceId))
                body.add(getMltProgressTrackPlaylist(param, type, voiceId))
                body.add(getMltProgressTractor(param, type, voiceId))
            }
        }

        type = ProducerType.FILLCOLOR
        if ((param["${type.text.uppercase()}${voiceId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
                body.add(getMltFillEvenFilePlaylist(param, type, voiceId))
                body.add(getMltFillEvenTrackPlaylist(param, type, voiceId))
                body.add(getMltFillEvenTractor(param, type, voiceId))
                body.add(getMltFillOddFilePlaylist(param, type, voiceId))
                body.add(getMltFillOddTrackPlaylist(param, type, voiceId))
                body.add(getMltFillOddTractor(param, type, voiceId))
            }
        }

        type = ProducerType.SONGTEXT
        if ((param["${type.text.uppercase()}${voiceId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
                body.add(getMltSongTextFilePlaylist(param, type, voiceId))
                body.add(getMltSongTextTrackPlaylist(param, type, voiceId))
                body.add(getMltSongTextTractor(param, type, voiceId))
            }
        }

        type = ProducerType.HEADER
        if ((param["${type.text.uppercase()}${voiceId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
                body.add(getMltHeaderFilePlaylist(param, type, voiceId))
                body.add(getMltHeaderTrackPlaylist(param, type, voiceId))
                body.add(getMltHeaderTractor(param, type, voiceId))
            }
        }

        type = ProducerType.LOGOTYPE
        if ((param["${type.text.uppercase()}${voiceId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
                body.add(getMltLogotypeFilePlaylist(param, type, voiceId))
                body.add(getMltLogotypeTrackPlaylist(param, type, voiceId))
                body.add(getMltLogotypeTractor(param, type, voiceId))
            }
        }

        type = ProducerType.BEAT
        if ((param["${type.text.uppercase()}${voiceId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
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
        }

        type = ProducerType.COUNTER
        if ((param["${type.text.uppercase()}${voiceId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
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
        }

        type = ProducerType.WATERMARK
        if ((param["${type.text.uppercase()}${voiceId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
                body.add(getMltWatermarkFilePlaylist(param, type, voiceId))
                body.add(getMltWatermarkTrackPlaylist(param, type, voiceId))
                body.add(getMltWatermarkTractor(param, type, voiceId))
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