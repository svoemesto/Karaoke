package com.svoemesto.karaokeapp.mlt

import com.svoemesto.karaokeapp.KaraokeVoice
import com.svoemesto.karaokeapp.SHADOW
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.SongVersion
import com.svoemesto.karaokeapp.model.SongVoiceLine
import com.svoemesto.karaokeapp.model.SongVoiceLineSymbol


data class MltProp(
    val props: MutableMap<Any, Any> = mutableMapOf()
) {
    enum class KEYS {ROOT, SONG_VERSION, OFFSET, ID, COUNTER, VOICE_SETTING, RECT, XML_DATA, SONG_CAPO, SONG_CHORD_DESCRIPTION, SONG_NAME,
        COUNT_VOICES, LINE_SPACING, SHADOW, TYPEWRITER, ALIGNMENT, WORK_AREA_HEIGHT_PX, VOICELINES,
        SYMBOL_HEIGHT_PX, POSITION_Y_PX, POSITION_X_PX, UUID, AUTHOR, TONE, BPM, ALBUM, YEAR, TRACK,
        FONT_SIZE_PT, PATH, BASE64, FINGERBOARD_H, FINGERBOARD_W, CHORD_W, CHORD_H, COUNT_FINGERBOARDS, CHORDS, ROOT_FOLDER,
        START_TIMECODE, END_TIMECODE, FADEIN_TIMECODE, FADEOUT_TIMECODE, LENGTH_MS, LENGTH_FR, LENGTH_TIMECODE, GUIDES_PROPERTY,
        IN_OFFSET_AUDIO, IN_OFFSET_VIDEO, ENABLED, VOLUME, FILE_NAME, IGNORE_CAPO

    }
    private fun propsNode(key: Any = KEYS.ROOT): MutableMap<Any, Any> {
        return if (props.containsKey(key)) {
            props[key] as MutableMap<Any, Any>
        } else {
            mutableMapOf()
        }
    }


    fun getLengthTimecode(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.LENGTH_TIMECODE)]?.let { it as String } ?: ""
    fun setLengthTimecode(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.LENGTH_TIMECODE)] = value}

    fun getUUID(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.UUID)]?.let { it as String } ?: ""
    fun setUUID(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.UUID)] = value}

    fun getFileName(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.FILE_NAME)]?.let { it as String } ?: ""
    fun setFileName(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.FILE_NAME)] = value}

    fun getVolume(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.VOLUME)]?.let { it as String } ?: ""
    fun setVolume(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.VOLUME)] = value}


    fun getIgnoreCapo(key: Any = KEYS.ROOT): Boolean = props[key.convertToList(KEYS.IGNORE_CAPO)]?.let { it as Boolean } ?: false
    fun setIgnoreCapo(value: Boolean, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.IGNORE_CAPO)] = value}
    fun getEnabled(key: Any = KEYS.ROOT): Boolean = props[key.convertToList(KEYS.ENABLED)]?.let { it as Boolean } ?: false
    fun setEnabled(value: Boolean, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.ENABLED)] = value}

    fun getInOffsetVideo(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.IN_OFFSET_VIDEO)]?.let { it as String } ?: ""
    fun setInOffsetVideo(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.IN_OFFSET_VIDEO)] = value}

    fun getInOffsetAudio(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.IN_OFFSET_AUDIO)]?.let { it as String } ?: ""
    fun setInOffsetAudio(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.IN_OFFSET_AUDIO)] = value}

    fun getGuidesProperty(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.GUIDES_PROPERTY)]?.let { it as String } ?: ""
    fun setGuidesProperty(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.GUIDES_PROPERTY)] = value}

    fun getLengthFr(key: Any = KEYS.ROOT): Long = props[key.convertToList(KEYS.LENGTH_FR)]?.let { it as Long } ?: 0
    fun setLengthFr(value: Long, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.LENGTH_FR)] = value}

    fun getLengthMs(key: Any = KEYS.ROOT): Long = props[key.convertToList(KEYS.LENGTH_MS)]?.let { it as Long } ?: 0
    fun setLengthMs(value: Long, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.LENGTH_MS)] = value}

    fun getFadeOutTimecode(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.FADEOUT_TIMECODE)]?.let { it as String } ?: ""
    fun setFadeOutTimecode(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.FADEOUT_TIMECODE)] = value}

    fun getFadeInTimecode(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.FADEIN_TIMECODE)]?.let { it as String } ?: ""
    fun setFadeInTimecode(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.FADEIN_TIMECODE)] = value}

    fun getEndTimecode(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.END_TIMECODE)]?.let { it as String } ?: ""
    fun setEndTimecode(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.END_TIMECODE)] = value}

    fun getStartTimecode(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.START_TIMECODE)]?.let { it as String } ?: ""
    fun setStartTimecode(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.START_TIMECODE)] = value}

    fun getRootFolder(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.ROOT_FOLDER)]?.let { it as String } ?: ""
    fun setRootFolder(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.ROOT_FOLDER)] = value}

    fun getSongVersion(key: Any = KEYS.ROOT): SongVersion = props[key.convertToList(KEYS.SONG_VERSION)]?.let { it as SongVersion } ?: SongVersion.LYRICS
    fun setSongVersion(value: SongVersion, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.SONG_VERSION)] = value}

    fun getSongCapo(key: Any = KEYS.ROOT): Int = props[key.convertToList(KEYS.SONG_CAPO)]?.let { it as Int } ?: 0
    fun setSongCapo(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.SONG_CAPO)] = value}

    fun getSongChordDescription(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.SONG_CHORD_DESCRIPTION)]?.let { it as String } ?: ""
    fun setSongChordDescription(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.SONG_CHORD_DESCRIPTION)] = value}


    fun getAuthor(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.AUTHOR)]?.let { it as String } ?: ""
    fun setAuthor(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.AUTHOR)] = value}

    fun getTone(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.TONE)]?.let { it as String } ?: ""
    fun setTone(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.TONE)] = value}
    fun getAlbum(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.ALBUM)]?.let { it as String } ?: ""
    fun setAlbum(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.ALBUM)] = value}

    fun getBpm(key: Any = KEYS.ROOT): Long = props[key.convertToList(KEYS.BPM)]?.let { it as Long } ?: 0
    fun setBpm(value: Long, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.BPM)] = value}
    fun getYear(key: Any = KEYS.ROOT): Long = props[key.convertToList(KEYS.YEAR)]?.let { it as Long } ?: 0
    fun setYear(value: Long, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.YEAR)] = value}

    fun getTrack(key: Any = KEYS.ROOT): Long = props[key.convertToList(KEYS.TRACK)]?.let { it as Long } ?: 0
    fun setTrack(value: Long, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.TRACK)] = value}


    fun getSongName(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.SONG_NAME)]?.let { it as String } ?: ""
    fun setSongName(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.SONG_NAME)] = value}

    fun getCountVoices(key: Any = KEYS.ROOT): Int = props[key.convertToList(KEYS.COUNT_VOICES)]?.let { it as Int } ?: 0
    fun setCountVoices(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.COUNT_VOICES)] = value}
    fun getLineSpacing(key: Any = KEYS.ROOT): Long = props[key.convertToList(KEYS.LINE_SPACING)]?.let { it as Long } ?: 0
    fun setLineSpacing(value: Long, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.LINE_SPACING)] = value}

    fun getShadow(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.SHADOW)]?.let { it as String } ?: ""
    fun setShadow(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.SHADOW)] = value}
    fun getPath(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.PATH)]?.let { it as String } ?: ""
    fun setPath(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.PATH)] = value}
    fun getBase64(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.BASE64)]?.let { it as String } ?: ""
    fun setBase64(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.BASE64)] = value}
    fun getTypeWriter(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.TYPEWRITER)]?.let { it as String } ?: ""
    fun setTypeWriter(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.TYPEWRITER)] = value}

    fun getAlignment(key: Any = KEYS.ROOT): Long = props[key.convertToList(KEYS.ALIGNMENT)]?.let { it as Long } ?: 0
    fun setAlignment(value: Long, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.ALIGNMENT)] = value}
    fun getVoiceSetting(key: Any = KEYS.ROOT): KaraokeVoice? = props[key.convertToList(KEYS.VOICE_SETTING)]?.let { it as KaraokeVoice }
    fun setVoiceSetting(value: KaraokeVoice, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.VOICE_SETTING)] = value}

    fun getWorkAreaHeightPx(key: Any = KEYS.ROOT): Long = props[key.convertToList(KEYS.WORK_AREA_HEIGHT_PX)]?.let { it as Long } ?: 0
    fun setWorkAreaHeightPx(value: Long, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.WORK_AREA_HEIGHT_PX)] = value}

    fun getVoicelines(key: Any = KEYS.ROOT): MutableList<SongVoiceLine> = props[key.convertToList(KEYS.VOICELINES)]?.let { it as MutableList<SongVoiceLine> } ?: mutableListOf()
    fun setVoicelines(value: MutableList<SongVoiceLine>, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.VOICELINES)] = value}

    fun getSymbolHeightPx(key: Any = KEYS.ROOT): Int = props[key.convertToList(KEYS.SYMBOL_HEIGHT_PX)]?.let { it as Int } ?: 0
    fun setSymbolHeightPx(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.SYMBOL_HEIGHT_PX)] = value}

    fun getPositionYPx(key: Any = KEYS.ROOT): Long = props[key.convertToList(KEYS.POSITION_Y_PX)]?.let { it as Long } ?: 0
    fun setPositionYPx(value: Long, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.POSITION_Y_PX)] = value}

    fun getPositionXPx(key: Any = KEYS.ROOT): Long = props[key.convertToList(KEYS.POSITION_X_PX)]?.let { it as Long } ?: 0
    fun setPositionXPx(value: Long, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.POSITION_X_PX)] = value}

    fun getFontSizePt(key: Any = KEYS.ROOT): Int = props[key.convertToList(KEYS.FONT_SIZE_PT)]?.let { it as Int } ?: 0
    fun setFontSizePt(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.FONT_SIZE_PT)] = value}

    fun getOffset(key: Any = KEYS.ROOT): Int = props[key.convertToList(KEYS.OFFSET)]?.let { it as Int } ?: 0
    fun setOffset(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.OFFSET)] = value}

    fun getFingerboardH(key: Any = KEYS.ROOT): Int = props[key.convertToList(KEYS.FINGERBOARD_H)]?.let { it as Int } ?: 0
    fun setFingerboardH(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.FINGERBOARD_H)] = value}

    fun getFingerboardW(key: Any = KEYS.ROOT): Int? = props[key.convertToList(KEYS.FINGERBOARD_W)]?.let { it as Int }
    fun setFingerboardW(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.FINGERBOARD_W)] = value}

    fun getChordW(key: Any = KEYS.ROOT): Int = props[key.convertToList(KEYS.CHORD_W)]?.let { it as Int } ?: 0
    fun setChordW(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.CHORD_W)] = value}


    fun getChordH(key: Any = KEYS.ROOT): Int = props[key.convertToList(KEYS.CHORD_H)]?.let { it as Int } ?: 0
    fun setChordH(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.CHORD_H)] = value}
    fun getCountFingerboards(key: Any = KEYS.ROOT): Int = props[key.convertToList(KEYS.COUNT_FINGERBOARDS)]?.let { it as Int } ?: 0
    fun setCountFingerboards(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.COUNT_FINGERBOARDS)] = value}

    fun getChords(key: Any = KEYS.ROOT): MutableList<SongVoiceLineSymbol> = props[key.convertToList(KEYS.CHORDS)]?.let { it as MutableList<SongVoiceLineSymbol> } ?: mutableListOf()
    fun setChords(value: MutableList<SongVoiceLineSymbol>, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.CHORDS)] = value}


    fun getId(key: Any = KEYS.ROOT): Int = props[key.convertToList(KEYS.ID)]?.let { it as Int } ?: 0
    fun setId(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.ID)] = value}

    fun getRect(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.RECT)]?.let { it as String } ?: ""
    fun setRect(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.RECT)] = value}

    fun getXmlData(key: Any = KEYS.ROOT): MltNode = props[key.convertToList(KEYS.XML_DATA)]?.let { it as MltNode } ?: MltNode()
    fun setXmlData(value: MltNode, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.XML_DATA)] = value}

    private fun Any.convertToList(value: Any): List<Any> =
        if (this is List<*>) {
            val result = (this as List<Any>).toMutableList()
            result.add(value)
            result
        } else {
            listOf(this, value)
        }

}