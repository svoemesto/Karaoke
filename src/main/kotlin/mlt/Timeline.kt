package mlt

import model.MltNode

fun getMltTimelineTractor(param: Map<String, Any?>): MltNode {

    val mlt = MltNode(
        name = "tractor",
        fields = mutableMapOf(
            Pair("id","tractor_timeline"),
            Pair("in",param["SONG_START_TIMECODE"].toString()),
            Pair("out",param["SONG_END_TIMECODE"].toString())
        ),
        body = mutableListOf(
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","black_track"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_vocal"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_music"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_song"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_background"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_microphone"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_horizon"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_progress"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_fill_odd"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_fill_even"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_song_text"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_header"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_logotype"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_beat4"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_beat3"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_beat2"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_beat1"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_counter4"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_counter3"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_counter2"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_counter1"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_counter0"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","tractor_watermark"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","black_track"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","black_track"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","black_track"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","black_track"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","black_track"))),
            MltNode(name = "track", fields = mutableMapOf(Pair("producer","black_track"))),
            MltNode(name = "filter",
                fields = mutableMapOf(
                    Pair("id","filter_subtitles")
                ),
                body = mutableListOf(
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","mlt_service")), body = "avfilter.subtitles"),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","internal_added")), body = 237),
                    MltNode(name = "property", fields = mutableMapOf(Pair("name","disable")), body = 1),
                )
                ),
        )
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
