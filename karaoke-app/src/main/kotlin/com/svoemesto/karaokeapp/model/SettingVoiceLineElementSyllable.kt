package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltText
import java.io.Serializable

data class SettingVoiceLineElementSyllable(
    val rootId: Long,
    val text: String,
    val note: String,
    val chord: String,
    val stringlad: String,
    val locklad: String,
    var syllableStartMs: Long, // Начало слога (в мс) - в этот момент слог начинает выделяться (если в элементе предусмотрено выделение)
    var syllableEndMs: Long, // Конец слога (с мс) - в этот момент слог заканчивается выделяться (если в элементе предусмотрено выделение)
    var previous: SettingVoiceLineElementSyllable?, // Для аккорда это должно быть полный текст до него чтобы вычислить положение на экране
) : Serializable {

    fun copy(): SettingVoiceLineElementSyllable {
        return SettingVoiceLineElementSyllable(
            rootId = rootId,
            text = text,
            note = note,
            chord = chord,
            stringlad = stringlad,
            locklad = locklad,
            syllableStartMs = syllableStartMs,
            syllableEndMs = syllableEndMs,
            previous = if (previous == null) null else previous!!.copy(),
        )
    }

    private var _fontSize: Int? = null
    var fontSize: Int
        get() {
            return _fontSize ?: 10
        }
        set(value) {
            _fontSize = value
        }

    private var _groupId: Int? = null
    var groupId: Int
        get() {
            return _groupId ?: 0
        }
        set(value) {
            _groupId = value
        }

    // lineStartMs для линии с индексом indexLineStart
    private var _deltaStartMs: Long? = null
    // deltaStartMs - это время начала видимости родительской линии
    var deltaStartMs: Long
        get() {
            return _deltaStartMs ?: 0
        }
        set(value) {
            _deltaStartMs = value
        }

    var chordId: Int = -1
    private var _chordX: Int? = null
    var chordX: Int
        get() {
            return _chordX ?: 0
        }
        set(value) {
            _chordX = value
        }
    private var _indexChordStart: Int? = null
    var indexChordStart: Int
        get() {
            return _indexChordStart ?: 0
        }
        set(value) {
            _indexChordStart = value
        }

    private var _indexChordEnd: Int? = null
    var indexChordEnd: Int
        get() {
            return _indexChordEnd ?: 0
        }
        set(value) {
            _indexChordEnd = value
        }

    private var _startChordVisibleTime: Long? = null
    var startChordVisibleTime: Long
        get() {
            return _startChordVisibleTime ?: 0L
        }
        set(value) {
            _startChordVisibleTime = value
        }

    private var _endChordVisibleTime: Long? = null
    var endChordVisibleTime: Long
        get() {
            return _endChordVisibleTime ?: -1
        }
        set(value) {
            _endChordVisibleTime = value
        }
    private var _transformChordProperties: List<TransformProperty>? = null
    var transformChordProperties: List<TransformProperty>
        get() {
            return _transformChordProperties ?: emptyList()
        }
        set(value) {
            _transformChordProperties = value
        }

    private var _countChordPictureTracks: Int? = null
    var countChordPictureTracks: Int
        get() {
            return _countChordPictureTracks ?: 0
        }
        set(value) {
            _countChordPictureTracks = value
        }
    val chordPictureTrackId: Int get() {
        return if (countChordPictureTracks > 0) chordId % countChordPictureTracks else -1
    }
    fun chordIsOnScreen(timeMs: Long = 0L): Boolean {
        return timeMs in startChordVisibleTime..endChordVisibleTime
    }
    fun textSyllablesWithPrevious(): String {
        return if (previous == null) {
            text
        } else {
            previous!!.textSyllablesWithPrevious() + text
        }
    }
    fun syllableDurationMs(): Long = syllableEndMs - syllableStartMs
    fun syllableStartMsWithDelta(): Long {
        return syllableStartMs - deltaStartMs
    }
    fun syllableEndMsWithDelta(): Long {
        return syllableEndMs - deltaStartMs
    }
    fun h(): Int = mltText().h()
    fun w(): Int = mltText().w()
    fun x(): Int = mltText().x
    fun deltaStartY(): Int = h() / 7
    fun deltaStartH(): Int = 2 * deltaStartY()
    fun deltaEndY(): Int = (if (isShortSyllable()) 1 else 0) * (h() / 7)
    fun deltaEndH(): Int = 2 * deltaEndY()
    fun isShortSyllable(): Boolean = syllableDurationMs() <= Karaoke.shortSubtitleMs
    var syllableId: Int = -1

    var isFirst: Boolean = false
    var isLast: Boolean = false
    fun mltText(): MltText {
        val mltText = Karaoke.voices[0].groups[groupId].mltText.copy(text.replace("&","&amp;amp;"), fontSize)
        mltText.x = previous?.let {
            val prevMltText = it.mltText()
            prevMltText.x + prevMltText.w()
        } ?: 0
        return mltText
    }

    fun startTransformProperty(): TransformProperty {
        val tpTime = if (isFirst) {
            convertFramesToTimecode(convertMillisecondsToFrames(syllableStartMsWithDelta()) + 2)
        } else {
            convertMillisecondsToTimecode(syllableStartMsWithDelta())
        }
        val tpX = 0
        val tpY = deltaStartY()
        val tpW = x()
        val tpH = h() - deltaStartH()
        val tpOpacity = 1.0
        return TransformProperty(
            time = tpTime,
            x = tpX,
            y = tpY,
            w = tpW,
            h = tpH,
            opacity = tpOpacity
        )
    }

    fun endTransformProperty(): TransformProperty {
        val tpTime = convertMillisecondsToTimecode(syllableEndMsWithDelta())
        val tpX = 0
        val tpY = deltaEndY()
        val tpW = x() + w()
        val tpH = h() - deltaEndH()
        val tpOpacity = 1.0
        return TransformProperty(
            time = tpTime,
            x = tpX,
            y = tpY,
            w = tpW,
            h = tpH,
            opacity = tpOpacity
        )
    }

}
