package com.svoemesto.karaokeapp.mlt

import com.svoemesto.karaokeapp.KaraokeVoice
import com.svoemesto.karaokeapp.mlt.mko.*
import com.svoemesto.karaokeapp.model.*


data class MltProp(
    val props: MutableMap<Any, Any> = mutableMapOf()
) {
    enum class KEYS {ROOT, SETTINGS, FRAME_WIDTH_PX, FRAME_HEIGHT_PX, START_LINE_OFFSET_MS, SONG_VERSION, OFFSET, ID, COUNTER, VOICE_SETTING, RECT, XML_DATA, SONG_CAPO, SONG_CHORD_DESCRIPTION, SONG_NAME,
        COUNT_VOICES, COUNT_AUDIO_TRACKS, COUNT_ALL_TRACKS, LINE_SPACING, SHADOW, TYPEWRITER, ALIGNMENT, WORK_AREA_HEIGHT_PX, VOICELINES,
        SYMBOL_HEIGHT_PX, POSITION_Y_PX, POSITION_X_PX, UUID, AUTHOR, TONE, BPM, ALBUM, YEAR, TRACK,
        FONT_SIZE_PT, PATH, BASE64, FINGERBOARD_H, FINGERBOARD_W, CHORD_W, CHORD_H, COUNT_FINGERBOARDS, CHORDS, ROOT_FOLDER,
        START_TIMECODE, END_TIMECODE, FADEIN_TIMECODE, FADEOUT_TIMECODE, LENGTH_MS, LENGTH_FR, LENGTH_TIMECODE, GUIDES_PROPERTY,
        IN_OFFSET_AUDIO, IN_OFFSET_VIDEO, ENABLED, VOLUME, FILE_NAME, IGNORE_CAPO, TIMECODE, HEIGHT_PX_PER_MS_COEFF, WIDTH_PX_PER_MS_COEFF, SCROLL_LINES,
        COUNT_CHILDS, HEIGHT_SCROLLER_PX, SCROLL_LINE_TRACK_ID, SCROLL_LINE_START_MS, SCROLL_LINE_END_MS, SCROLL_LINE_DURATION_MS, SCROLL_TRACK,
        TIME_TO_SCROLL_SCREEN_MS, INDEX_LINE_TRACK_ID, DURATION_ON_SCREEN, FONT_SIZE, SONG_TONE, SONG_BPM

    }
    private fun propsNode(key: Any = KEYS.ROOT): MutableMap<Any, Any> {
        return if (props.containsKey(key)) {
            props[key] as MutableMap<Any, Any>
        } else {
            mutableMapOf()
        }
    }

    fun getSettings(key: Any = KEYS.ROOT): Settings? = props[key.convertToList(KEYS.SETTINGS)]?.let { it as Settings }
    fun setSettings(value: Settings, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.SETTINGS)] = value}

    fun getScrollTrack(key: Any = KEYS.ROOT): MutableList<Pair<SongVoiceLine, Int>> = props[key.convertToList(KEYS.SCROLL_TRACK)]?.let { it as MutableList<Pair<SongVoiceLine, Int>> } ?: mutableListOf()
    fun setScrollTrack(value: MutableList<Pair<SongVoiceLine, Int>>, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.SCROLL_TRACK)] = value}
    fun getScrollLineDurationMs(key: Any = KEYS.ROOT): Long = props[key.convertToList(KEYS.SCROLL_LINE_DURATION_MS)]?.let { it as Long } ?: 0L
    fun setScrollLineDurationMs(value: Long, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.SCROLL_LINE_DURATION_MS)] = value}

    fun getScrollLineEndMs(key: Any = KEYS.ROOT): Long = props[key.convertToList(KEYS.SCROLL_LINE_END_MS)]?.let { it as Long } ?: 0L
    fun setScrollLineEndMs(value: Long, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.SCROLL_LINE_END_MS)] = value}

    fun getScrollLineStartMs(key: Any = KEYS.ROOT): Long = props[key.convertToList(KEYS.SCROLL_LINE_START_MS)]?.let { it as Long } ?: 0L
    fun setScrollLineStartMs(value: Long, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.SCROLL_LINE_START_MS)] = value}

    fun getTimeToScrollScreenMs(): Long = props[KEYS.ROOT.convertToList(KEYS.TIME_TO_SCROLL_SCREEN_MS)]?.let { it as Long } ?: 0L
    fun setTimeToScrollScreenMs(value: Long) {props[KEYS.ROOT.convertToList(KEYS.TIME_TO_SCROLL_SCREEN_MS)] = value}


    fun getIndexLineToTrackId(key: Any = KEYS.ROOT): Int = props[key.convertToList(KEYS.INDEX_LINE_TRACK_ID)]?.let { it as Int } ?: 0
    fun setIndexLineToTrackId(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.INDEX_LINE_TRACK_ID)] = value}

    fun getScrollLineTrackId(key: Any = KEYS.ROOT): Int = props[key.convertToList(KEYS.SCROLL_LINE_TRACK_ID)]?.let { it as Int } ?: 0
    fun setScrollLineTrackId(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.SCROLL_LINE_TRACK_ID)] = value}
    fun getHeightScrollerPx(): Long = props[KEYS.ROOT.convertToList(KEYS.HEIGHT_SCROLLER_PX)]?.let { it as Long } ?: 100L
    fun setHeightScrollerPx(value: Long) {props[KEYS.ROOT.convertToList(KEYS.HEIGHT_SCROLLER_PX)] = value}

    fun getDurationOnScreen(key: Any = KEYS.ROOT): Long = props[key.convertToList(KEYS.DURATION_ON_SCREEN)]?.let { it as Long } ?: 0
    fun setDurationOnScreen(value: Long, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.DURATION_ON_SCREEN)] = value}

    fun getCountChilds(key: Any = KEYS.ROOT): Int = props[key.convertToList(KEYS.COUNT_CHILDS)]?.let { it as Int } ?: 0
    fun setCountChilds(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.COUNT_CHILDS)] = value}
    fun getScrollLines(key: Any = KEYS.ROOT): List<SongVoiceLine> = props[key.convertToList(KEYS.SCROLL_LINES)]?.let { it as List<SongVoiceLine> } ?: listOf()
    fun setScrollLines(value: List<SongVoiceLine>, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.SCROLL_LINES)] = value}
    fun getWidthPxPerMsCoeff(): Double = props[KEYS.ROOT.convertToList(KEYS.WIDTH_PX_PER_MS_COEFF)]?.let { it as Double } ?: 1.0
    fun setWidthPxPerMsCoeff(value: Double) {props[KEYS.ROOT.convertToList(KEYS.WIDTH_PX_PER_MS_COEFF)] = value}
    fun getHeightPxPerMsCoeff(): Double = props[KEYS.ROOT.convertToList(KEYS.HEIGHT_PX_PER_MS_COEFF)]?.let { it as Double } ?: 1.0
    fun setHeightPxPerMsCoeff(value: Double) {props[KEYS.ROOT.convertToList(KEYS.HEIGHT_PX_PER_MS_COEFF)] = value}

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

    fun getTimelineLengthFr(): Long = props["Timeline".convertToList(KEYS.LENGTH_FR)]?.let { it as Long } ?: 0
    fun setTimelineLengthFr(value: Long) {props["Timeline".convertToList(KEYS.LENGTH_FR)] = value}
    fun getSplashLengthFr(): Long = props["Splash".convertToList(KEYS.LENGTH_FR)]?.let { it as Long } ?: 0
    fun setSplashLengthFr(value: Long) {props["Splash".convertToList(KEYS.LENGTH_FR)] = value}
    fun getBoostyLengthFr(): Long = props["Boosty".convertToList(KEYS.LENGTH_FR)]?.let { it as Long } ?: 0
    fun setBoostyLengthFr(value: Long) {props["Boosty".convertToList(KEYS.LENGTH_FR)] = value}
    fun getBackgroundLengthFr(): Long = props["Background".convertToList(KEYS.LENGTH_FR)]?.let { it as Long } ?: 0
    fun setBackgroundLengthFr(value: Long) {props["Background".convertToList(KEYS.LENGTH_FR)] = value}
    fun getTotalLengthFr(): Long = props["Total".convertToList(KEYS.LENGTH_FR)]?.let { it as Long } ?: 0
    fun setTotalLengthFr(value: Long) {props["Total".convertToList(KEYS.LENGTH_FR)] = value}
    fun getSongLengthFr(): Long = props["Song".convertToList(KEYS.LENGTH_FR)]?.let { it as Long } ?: 0
    fun setSongLengthFr(value: Long) {props["Song".convertToList(KEYS.LENGTH_FR)] = value}
    fun getAudioLengthFr(): Long = props["Audio".convertToList(KEYS.LENGTH_FR)]?.let { it as Long } ?: 0
    fun setAudioLengthFr(value: Long) {props["Audio".convertToList(KEYS.LENGTH_FR)] = value}
    fun getLengthFr(key: Any = KEYS.ROOT): Long = props[key.convertToList(KEYS.LENGTH_FR)]?.let { it as Long } ?: 0
    fun setLengthFr(value: Long, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.LENGTH_FR)] = value}

    fun getBoostyLengthMs(): Long = props["Boosty".convertToList(KEYS.LENGTH_MS)]?.let { it as Long } ?: 0
    fun setBoostyLengthMs(value: Long) {props["Boosty".convertToList(KEYS.LENGTH_MS)] = value}

    fun getSplashLengthMs(): Long = props["Splash".convertToList(KEYS.LENGTH_MS)]?.let { it as Long } ?: 0
    fun setSplashLengthMs(value: Long) {props["Splash".convertToList(KEYS.LENGTH_MS)] = value}

    fun getTimelineLengthMs(): Long = props["Timeline".convertToList(KEYS.LENGTH_MS)]?.let { it as Long } ?: 0
    fun setTimelineLengthMs(value: Long) {props["Timeline".convertToList(KEYS.LENGTH_MS)] = value}

    fun getTotalLengthMs(): Long = props["Total".convertToList(KEYS.LENGTH_MS)]?.let { it as Long } ?: 0
    fun setTotalLengthMs(value: Long) {props["Total".convertToList(KEYS.LENGTH_MS)] = value}
    fun getSongLengthMs(): Long = props["Song".convertToList(KEYS.LENGTH_MS)]?.let { it as Long } ?: 0
    fun setSongLengthMs(value: Long) {props["Song".convertToList(KEYS.LENGTH_MS)] = value}
    fun getFadeLengthMs(): Long = props["Fade".convertToList(KEYS.LENGTH_MS)]?.let { it as Long } ?: 0
    fun setFadeLengthMs(value: Long) {props["Fade".convertToList(KEYS.LENGTH_MS)] = value}
    fun getLengthMs(key: Any = KEYS.ROOT): Long = props[key.convertToList(KEYS.LENGTH_MS)]?.let { it as Long } ?: 0
    fun setLengthMs(value: Long, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.LENGTH_MS)] = value}

    fun getTimecode(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setTimecode(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.TIMECODE)] = value}

    fun getSongStartTimecode(): String = props["SongStart".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setSongStartTimecode(value: String) {props["SongStart".convertToList(KEYS.TIMECODE)] = value}

    fun getSongEndTimecode(): String = props["SongEnd".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setSongEndTimecode(value: String) {props["SongEnd".convertToList(KEYS.TIMECODE)] = value}

    fun getAudioEndTimecode(): String = props["AudioEnd".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setAudioEndTimecode(value: String) {props["AudioEnd".convertToList(KEYS.TIMECODE)] = value}

    fun getTotalStartTimecode(): String = props["TotalStart".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setTotalStartTimecode(value: String) {props["TotalStart".convertToList(KEYS.TIMECODE)] = value}

    fun getTotalEndTimecode(): String = props["TotalEnd".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setTotalEndTimecode(value: String) {props["TotalEnd".convertToList(KEYS.TIMECODE)] = value}

    fun getBackgroundEndTimecode(): String = props["BackgroundEnd".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setBackgroundEndTimecode(value: String) {props["BackgroundEnd".convertToList(KEYS.TIMECODE)] = value}

    fun getBoostyStartTimecode(): String = props["BoostyStart".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setBoostyStartTimecode(value: String) {props["BoostyStart".convertToList(KEYS.TIMECODE)] = value}

    fun getBoostyEndTimecode(): String = props["BoostyEnd".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setBoostyEndTimecode(value: String) {props["BoostyEnd".convertToList(KEYS.TIMECODE)] = value}

    fun getSplashStartTimecode(): String = props["SplashStart".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setSplashStartTimecode(value: String) {props["SplashStart".convertToList(KEYS.TIMECODE)] = value}

    fun getSplashEndTimecode(): String = props["SplashEnd".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setSplashEndTimecode(value: String) {props["SplashEnd".convertToList(KEYS.TIMECODE)] = value}

    fun getTimelineStartTimecode(): String = props["Timeline".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setTimelineStartTimecode(value: String) {props["Timeline".convertToList(KEYS.TIMECODE)] = value}

    fun getTimelineEndTimecode(): String = props["Timeline".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setTimelineEndTimecode(value: String) {props["Timeline".convertToList(KEYS.TIMECODE)] = value}

    fun getVoiceStartTimecode(): String = props["VoiceStart".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setVoiceStartTimecode(value: String) {props["VoiceStart".convertToList(KEYS.TIMECODE)] = value}

    fun getVoiceEndTimecode(): String = props["VoiceEnd".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setVoiceEndTimecode(value: String) {props["VoiceEnd".convertToList(KEYS.TIMECODE)] = value}

    fun getSongFadeInTimecode(): String = props["SongFadeIn".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setSongFadeInTimecode(value: String) {props["SongFadeIn".convertToList(KEYS.TIMECODE)] = value}

    fun getSongFadeOutTimecode(): String = props["SongFadeOut".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setSongFadeOutTimecode(value: String) {props["SongFadeOut".convertToList(KEYS.TIMECODE)] = value}

    fun getTotalFadeInTimecode(): String = props["TotalFadeIn".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setTotalFadeInTimecode(value: String) {props["TotalFadeIn".convertToList(KEYS.TIMECODE)] = value}

    fun getTotalFadeOutTimecode(): String = props["TotalFadeOut".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setTotalFadeOutTimecode(value: String) {props["TotalFadeOut".convertToList(KEYS.TIMECODE)] = value}

    fun getBoostyFadeInTimecode(): String = props["BoostyFadeIn".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setBoostyFadeInTimecode(value: String) {props["BoostyFadeIn".convertToList(KEYS.TIMECODE)] = value}

    fun getBoostyFadeOutTimecode(): String = props["BoostyFadeOut".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setBoostyFadeOutTimecode(value: String) {props["BoostyFadeOut".convertToList(KEYS.TIMECODE)] = value}

    fun getSplashFadeInTimecode(): String = props["SplashFadeIn".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setSplashFadeInTimecode(value: String) {props["SplashFadeIn".convertToList(KEYS.TIMECODE)] = value}

    fun getSplashFadeOutTimecode(): String = props["SplashFadeOut".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setSplashFadeOutTimecode(value: String) {props["SplashFadeOut".convertToList(KEYS.TIMECODE)] = value}

    fun getVoiceFadeInTimecode(): String = props["VoiceFadeIn".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setVoiceFadeInTimecode(value: String) {props["VoiceFadeIn".convertToList(KEYS.TIMECODE)] = value}

    fun getVoiceFadeOutTimecode(): String = props["VoiceFadeOut".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setVoiceFadeOutTimecode(value: String) {props["VoiceFadeOut".convertToList(KEYS.TIMECODE)] = value}

    fun getBoostyBlankTimecode(): String = props["BoostyBlank".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setBoostyBlankTimecode(value: String) {props["BoostyBlank".convertToList(KEYS.TIMECODE)] = value}

    fun getVoiceBlankTimecode(): String = props["VoiceBlank".convertToList(KEYS.TIMECODE)]?.let { it as String } ?: ""
    fun setVoiceBlankTimecode(value: String) {props["VoiceBlank".convertToList(KEYS.TIMECODE)] = value}






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

    fun getStartSilentOffsetMs(key: Any = KEYS.ROOT): Long = props[key.convertToList(KEYS.START_LINE_OFFSET_MS)]?.let { it as Long } ?: 0
    fun setStartSilentOffsetMs(value: Long, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.START_LINE_OFFSET_MS)] = value}

    fun getFrameWidthPx(key: Any = KEYS.ROOT): Int = props[key.convertToList(KEYS.FRAME_WIDTH_PX)]?.let { it as Int } ?: 0
    fun setFrameWidthPx(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.FRAME_WIDTH_PX)] = value}
    fun getFrameHeightPx(key: Any = KEYS.ROOT): Int = props[key.convertToList(KEYS.FRAME_HEIGHT_PX)]?.let { it as Int } ?: 0
    fun setFrameHeightPx(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.FRAME_HEIGHT_PX)] = value}

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

    fun getSongBpm(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.SONG_BPM)]?.let { it as String } ?: ""
    fun setSongBpm(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.SONG_BPM)] = value}
    fun getSongTone(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.SONG_TONE)]?.let { it as String } ?: ""
    fun setSongTone(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.SONG_TONE)] = value}
    fun getSongName(key: Any = KEYS.ROOT): String = props[key.convertToList(KEYS.SONG_NAME)]?.let { it as String } ?: ""
    fun setSongName(value: String, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.SONG_NAME)] = value}

    fun getCountAllTracks(key: Any = KEYS.ROOT): Int = props[key.convertToList(KEYS.COUNT_ALL_TRACKS)]?.let { it as Int } ?: 0
    fun setCountAllTracks(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.COUNT_ALL_TRACKS)] = value}

    fun getCountAudioTracks(key: Any = KEYS.ROOT): Int = props[key.convertToList(KEYS.COUNT_AUDIO_TRACKS)]?.let { it as Int } ?: 0
    fun setCountAudioTracks(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.COUNT_AUDIO_TRACKS)] = value}
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

    fun getFontSize(key: Any = KEYS.ROOT): Int = props[key.convertToList(KEYS.FONT_SIZE)]?.let { it as Int } ?: 0
    fun setFontSize(value: Int, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.FONT_SIZE)] = value}

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

    fun getChords(key: Any = KEYS.ROOT): MutableList<SettingVoiceLineElementSyllable> = props[key.convertToList(KEYS.CHORDS)]?.let { it as MutableList<SettingVoiceLineElementSyllable> } ?: mutableListOf()
    fun setChords(value: MutableList<SettingVoiceLineElementSyllable>, key: Any = KEYS.ROOT) {props[key.convertToList(KEYS.CHORDS)] = value}


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