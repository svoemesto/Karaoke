import model.MltNode
import model.ProducerType

fun getMltMainBinPlaylist(param: Map<String, Any?>): MltNode {

//    <property name="kdenlive:docproperties.activeTrack">3</property>
//    <property name="kdenlive:docproperties.audioChannels">2</property>
//    <property name="kdenlive:docproperties.audioTarget">-1</property>
//    <property name="kdenlive:docproperties.compositing">1</property>
//    <property name="kdenlive:docproperties.disablepreview">0</property>
//    <property name="kdenlive:docproperties.documentid">1663318034767</property>
//    <property name="kdenlive:docproperties.enableTimelineZone">0</property>
//    <property name="kdenlive:docproperties.enableexternalproxy">0</property>
//    <property name="kdenlive:docproperties.enableproxy">0</property>
//    <property name="kdenlive:docproperties.externalproxyparams">./;GL;.LRV;./;GX;.MP4;./;GP;.LRV;./;GP;.MP4</property>
//    <property name="kdenlive:docproperties.generateimageproxy">0</property>
//    <property name="kdenlive:docproperties.generateproxy">0</property>
//    <property name="kdenlive:docproperties.groups">[]</property>
//    <property name="kdenlive:docproperties.kdenliveversion">22.08.0</property>
//    <property name="kdenlive:docproperties.position">1340</property>
//    <property name="kdenlive:docproperties.previewextension"/>
//    <property name="kdenlive:docproperties.previewparameters"/>
//    <property name="kdenlive:docproperties.profile">atsc_1080p_60</property>
//    <property name="kdenlive:docproperties.proxyextension"/>
//    <property name="kdenlive:docproperties.proxyimageminsize">2000</property>
//    <property name="kdenlive:docproperties.proxyimagesize">800</property>
//    <property name="kdenlive:docproperties.proxyminsize">1000</property>
//    <property name="kdenlive:docproperties.proxyparams"/>
//    <property name="kdenlive:docproperties.proxyresize">640</property>
//    <property name="kdenlive:docproperties.scrollPos">0</property>
//    <property name="kdenlive:docproperties.seekOffset">30000</property>
//    <property name="kdenlive:docproperties.storagefolder">cachefiles/1663318034767</property>
//    <property name="kdenlive:docproperties.version">1.04</property>
//    <property name="kdenlive:docproperties.verticalzoom">1</property>
//    <property name="kdenlive:docproperties.videoTarget">3</property>
//    <property name="kdenlive:docproperties.zonein">4704</property>
//    <property name="kdenlive:docproperties.zoneout">5678</property>
//    <property name="kdenlive:docproperties.zoom">12</property>
//    <property name="kdenlive:expandedFolders"/>
//    <property name="kdenlive:documentnotes"/>

    val countGroups = (param["COUNT_VOICES"] as Int)
    val entries = mutableListOf<MltNode>()
    var type = ProducerType.NONE
    for (groupId in 0 until countGroups) {

        type = ProducerType.SONGTEXT
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
            }
        }

        type = ProducerType.HORIZON
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
            }
        }
        type = ProducerType.WATERMARK
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
            }
        }
        type = ProducerType.PROGRESS
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
            }
        }

        type = ProducerType.FILLCOLOR
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}even${groupId}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}odd${groupId}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
            }
        }
        type = ProducerType.FADER
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
            }
        }
        type = ProducerType.HEADER
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
            }
        }
        type = ProducerType.BACKGROUND
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
            }
        }
        type = ProducerType.MICROPHONE
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
            }
        }

        type = ProducerType.COUNTER
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}4"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}3"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}2"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}1"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}0"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
            }
        }

        type = ProducerType.BEAT
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}1"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}2"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}3"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}4"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
            }
        }

        type = ProducerType.AUDIOSONG
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
            }
        }

        type = ProducerType.AUDIOMUSIC
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
            }
        }
        type = ProducerType.AUDIOVOCAL
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
            }
        }
        type = ProducerType.AUDIOBASS
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
            }
        }
        type = ProducerType.AUDIODRUMS
        if ((param["${type.text.uppercase()}${groupId}_ENABLED"] as Boolean)) {
            if ((type.onlyOne && groupId == 0) || !type.onlyOne ) {
                entries.add(MltNode(name = "entry", fields = mutableMapOf(Pair("producer","producer_${type.text}${groupId}"),Pair("in", param["SONG_START_TIMECODE"].toString()),Pair("out", param["SONG_END_TIMECODE"].toString()))))
            }
        }
    }

    val mlt = MltNode(
        name = "playlist",
        fields = mutableMapOf(Pair("id","main_bin")),
        body = mutableListOf(
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.version")), body = "1.04"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.profile")), body = "atsc_1080p_60"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.rendercategory")), body = "Ultra-High Definition (4K)"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.rendercustomquality")), body = 100),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderendguide")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderexportaudio")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.rendermode")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderplay")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderpreview")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderprofile")), body = "MP4-H265 (HEVC)"),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderratio")), body = 1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderrescale")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderspeed")), body = 8),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderstartguide")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.rendertcoverlay")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.rendertctype")), body = -1),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.rendertwopass")), body = 0),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.guides")), body = param["GUIDES_PROPERTY"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","kdenlive:docproperties.renderurl")), body = param["SONG_VIDEO_FILENAME"]),
            MltNode(name = "property", fields = mutableMapOf(Pair("name","xml_retain")), body = 1),
            entries
        )
    )

    return mlt
}