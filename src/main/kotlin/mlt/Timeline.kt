package mlt

import model.MltNode
import model.ProducerType

fun getMltTimelineTractor(param: Map<String, Any?>): MltNode {

    val countVoices = (param["COUNT_VOICES"] as Int)
    val body = mutableListOf<MltNode>()
    body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","black_track"))))

    var type = ProducerType.NONE
    for (groupId in 0 until countVoices) {
        type = ProducerType.AUDIOVOCAL
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne) {
                body.add(
                    MltNode(
                        name = "track",
                        fields = mutableMapOf(Pair("producer", "tractor_${type.text}${groupId}"))
                    )
                )
            }
        }
    }
    for (groupId in 0 until countVoices) {
        type = ProducerType.AUDIOMUSIC
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne) {
                body.add(
                    MltNode(
                        name = "track",
                        fields = mutableMapOf(Pair("producer", "tractor_${type.text}${groupId}"))
                    )
                )
            }
        }
    }
    for (groupId in 0 until countVoices) {
        type = ProducerType.AUDIOSONG
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne) {
                body.add(
                    MltNode(
                        name = "track",
                        fields = mutableMapOf(Pair("producer", "tractor_${type.text}${groupId}"))
                    )
                )
            }
        }
    }
    for (groupId in 0 until countVoices) {
        type = ProducerType.BACKGROUND
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_${type.text}${groupId}"))))
            }
        }
    }
    for (groupId in 0 until countVoices) {
        type = ProducerType.HORIZON
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_${type.text}${groupId}"))))
            }
        }
    }
    for (groupId in 0 until countVoices) {
        type = ProducerType.PROGRESS
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_${type.text}${groupId}"))))
            }
        }
    }
    for (voiceId in 0 until countVoices) {
        type = ProducerType.FILLCOLOR
        if ((param["${type.text.uppercase()}${voiceId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && voiceId == 0) || !type.onlyOne ) {
                body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_${type.text}odd${voiceId}"))))
                body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_${type.text}even${voiceId}"))))
            }
        }
    }
    for (groupId in 0 until countVoices) {
        type = ProducerType.SONGTEXT
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_${type.text}${groupId}"))))
            }
        }
    }
    for (groupId in 0 until countVoices) {
        type = ProducerType.HEADER
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_${type.text}${groupId}"))))
            }
        }
    }
    for (groupId in 0 until countVoices) {
        type = ProducerType.MICROPHONE
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_${type.text}${groupId}"))))
            }
        }
    }
    for (groupId in 0 until countVoices) {
        type = ProducerType.BEAT
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_${type.text}${groupId}4"))))
                body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_${type.text}${groupId}3"))))
                body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_${type.text}${groupId}2"))))
                body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_${type.text}${groupId}1"))))
            }
        }
    }
    for (groupId in 0 until countVoices) {
        type = ProducerType.COUNTER
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_${type.text}${groupId}4"))))
                body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_${type.text}${groupId}3"))))
                body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_${type.text}${groupId}2"))))
                body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_${type.text}${groupId}1"))))
                body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_${type.text}${groupId}0"))))
            }
        }
    }
    for (groupId in 0 until countVoices) {
        type = ProducerType.WATERMARK
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                body.add(MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_${type.text}${groupId}"))))
            }
        }
    }

    body.add(
        MltNode(name = "filter",
            fields = mutableMapOf(
                Pair("id","filter_subtitles")
            ),
            body = mutableListOf(
                MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "avfilter.subtitles"),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","internal_added")), body = 237),
                MltNode(name = "property", fields = mutableMapOf(Pair("name","disable")), body = 1),
            )
        )
    )

    val mlt = MltNode(
        name = "tractor",
        fields = mutableMapOf(
            Pair("id","tractor_timeline"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = body
    )

    return mlt
}

//<transition id="transition0">
//<property name="a_track">0</property>
//<property name="b_track">1</property>
//<property name="mlt_service">mix</property>
//<property name="kdenlive_id">mix</property>
//<property name="internal_added">237</property>
//<property name="always_active">1</property>
//<property name="accepts_blanks">1</property>
//<property name="sum">1</property>
//</transition>
//<transition id="transition1">
//<property name="a_track">0</property>
//<property name="b_track">2</property>
//<property name="mlt_service">mix</property>
//<property name="kdenlive_id">mix</property>
//<property name="internal_added">237</property>
//<property name="always_active">1</property>
//<property name="accepts_blanks">1</property>
//<property name="sum">1</property>
//</transition>
//<transition id="transition2">
//<property name="a_track">0</property>
//<property name="b_track">3</property>
//<property name="mlt_service">mix</property>
//<property name="kdenlive_id">mix</property>
//<property name="internal_added">237</property>
//<property name="always_active">1</property>
//<property name="accepts_blanks">1</property>
//<property name="sum">1</property>
//</transition>
//<transition id="transition3">
//<property name="a_track">0</property>
//<property name="b_track">4</property>
//<property name="version">0.1</property>
//<property name="mlt_service">frei0r.cairoblend</property>
//<property name="always_active">1</property>
//<property name="internal_added">237</property>
//</transition>
//<transition id="transition4">
//<property name="a_track">0</property>
//<property name="b_track">5</property>
//<property name="version">0.1</property>
//<property name="mlt_service">frei0r.cairoblend</property>
//<property name="always_active">1</property>
//<property name="internal_added">237</property>
//</transition>
//<transition id="transition5">
//<property name="a_track">0</property>
//<property name="b_track">6</property>
//<property name="version">0.1</property>
//<property name="mlt_service">frei0r.cairoblend</property>
//<property name="always_active">1</property>
//<property name="internal_added">237</property>
//</transition>
//<transition id="transition6">
//<property name="a_track">0</property>
//<property name="b_track">7</property>
//<property name="version">0.1</property>
//<property name="mlt_service">frei0r.cairoblend</property>
//<property name="always_active">1</property>
//<property name="internal_added">237</property>
//</transition>
//<transition id="transition7">
//<property name="a_track">0</property>
//<property name="b_track">8</property>
//<property name="version">0.1</property>
//<property name="mlt_service">frei0r.cairoblend</property>
//<property name="always_active">1</property>
//<property name="internal_added">237</property>
//</transition>
//<transition id="transition8">
//<property name="a_track">0</property>
//<property name="b_track">9</property>
//<property name="version">0.1</property>
//<property name="mlt_service">frei0r.cairoblend</property>
//<property name="always_active">1</property>
//<property name="internal_added">237</property>
//</transition>
//<transition id="transition9">
//<property name="a_track">0</property>
//<property name="b_track">10</property>
//<property name="version">0.1</property>
//<property name="mlt_service">frei0r.cairoblend</property>
//<property name="always_active">1</property>
//<property name="internal_added">237</property>
//</transition>
//<transition id="transition10">
//<property name="a_track">0</property>
//<property name="b_track">11</property>
//<property name="version">0.1</property>
//<property name="mlt_service">frei0r.cairoblend</property>
//<property name="always_active">1</property>
//<property name="internal_added">237</property>
//</transition>
//<transition id="transition11">
//<property name="a_track">0</property>
//<property name="b_track">12</property>
//<property name="version">0.1</property>
//<property name="mlt_service">frei0r.cairoblend</property>
//<property name="always_active">1</property>
//<property name="internal_added">237</property>
//</transition>
//<transition id="transition12">
//<property name="a_track">0</property>
//<property name="b_track">13</property>
//<property name="version">0.1</property>
//<property name="mlt_service">frei0r.cairoblend</property>
//<property name="always_active">1</property>
//<property name="internal_added">237</property>
//</transition>
//<transition id="transition13">
//<property name="a_track">0</property>
//<property name="b_track">14</property>
//<property name="version">0.1</property>
//<property name="mlt_service">frei0r.cairoblend</property>
//<property name="always_active">1</property>
//<property name="internal_added">237</property>
//</transition>
//<transition id="transition14">
//<property name="a_track">0</property>
//<property name="b_track">15</property>
//<property name="version">0.1</property>
//<property name="mlt_service">frei0r.cairoblend</property>
//<property name="always_active">1</property>
//<property name="internal_added">237</property>
//</transition>
//<transition id="transition15">
//<property name="a_track">0</property>
//<property name="b_track">16</property>
//<property name="version">0.1</property>
//<property name="mlt_service">frei0r.cairoblend</property>
//<property name="always_active">1</property>
//<property name="internal_added">237</property>
//</transition>
//<filter id="filter24">
//<property name="window">75</property>
//<property name="max_gain">20dB</property>
//<property name="mlt_service">volume</property>
//<property name="internal_added">237</property>
//<property name="disable">1</property>
//</filter>
//<filter id="filter25">
//<property name="channel">-1</property>
//<property name="mlt_service">panner</property>
//<property name="internal_added">237</property>
//<property name="start">0.5</property>
//<property name="disable">1</property>
//</filter>
//<filter id="filter10">
//<property name="mlt_service">avfilter.subtitles</property>
//<property name="internal_added">237</property>
//<property name="av.filename">/tmp/1663318034767.srt</property>
//<property name="disable">1</property>
//</filter>
//<filter id="filter11">
//<property name="iec_scale">0</property>
//<property name="mlt_service">audiolevel</property>
//<property name="peak">1</property>
//<property name="disable">1</property>
//</filter>
