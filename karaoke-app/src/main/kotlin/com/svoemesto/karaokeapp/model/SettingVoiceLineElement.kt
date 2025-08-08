package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.convertFramesToTimecode
import com.svoemesto.karaokeapp.convertMillisecondsToFrames
import com.svoemesto.karaokeapp.convertMillisecondsToTimecode
import com.svoemesto.karaokeapp.mlt.MltObject
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.mko.MkoFill
import com.svoemesto.karaokeapp.mlt.mko.MkoString
import com.svoemesto.karaokeapp.mlt.mko.MltKaraokeObject
import java.io.Serializable

data class SettingVoiceLineElement(
    val rootId: Long,
    val type: SettingVoiceLineElementTypes, // Тип элемента (текст / аккорд / нота / комментарий / пустая строка)
) : Serializable {

    private val _syllables: MutableList<SettingVoiceLineElementSyllable> = mutableListOf()

    fun getSyllables(): List<SettingVoiceLineElementSyllable> = _syllables
    fun getCopySyllables(): List<SettingVoiceLineElementSyllable> = _syllables.map { it.copy() }
    fun addSyllable(syllable: SettingVoiceLineElementSyllable) {
        _syllables.add(syllable)
        actuateChilds()
    }
    fun addSyllables(syllables: List<SettingVoiceLineElementSyllable>) {
        syllables.forEach { syllable -> _syllables.add(syllable) }
        actuateChilds()
    }
    fun actuateChilds() {
        var prevSyllable: SettingVoiceLineElementSyllable? = null
        getSyllables().forEachIndexed { indexSyllable, syllable ->
            syllable.syllableId = indexSyllable
            syllable.isFirst = indexSyllable == 0
            syllable.isLast = (indexSyllable == getSyllables().size - 1)
            if (syllable.previous == null) syllable.previous = prevSyllable
            syllable.deltaStartMs = deltaStartMs
            prevSyllable = syllable
        }
    }

    private var _fontSize: Int? = null

    var fontSize: Int
        get() {
            return _fontSize ?: 10
        }
        set(value) {
            val coeff = if (type == SettingVoiceLineElementTypes.COMMENT) 0.75 else 1.0
            val valueWithCoeff = (value * coeff).toInt()
            _fontSize = value
//            _fontSize = valueWithCoeff
            getSyllables().forEach { it.fontSize = valueWithCoeff }
        }

    private var _groupId: Int? = null
    var groupId: Int
        get() {
            return _groupId ?: 0
        }
        set(value) {
            _groupId = value
            getSyllables().forEach { it.groupId = value }
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
            getSyllables().forEach { it.deltaStartMs = value }
        }

    private var _indexLineStart: Int? = null
    var indexLineStart: Int
        get() {
            return _indexLineStart ?: 0
        }
        set(value) {
            _indexLineStart = value
        }


    fun text(): String = getSyllables().joinToString("") { it.text }
    fun mltText(): MltText {
        val coeff = if (type == SettingVoiceLineElementTypes.COMMENT) 0.75 else 1.0
        val fontSizeWithCoeff = (fontSize * coeff).toInt()
        return Karaoke.voices[0].groups[groupId].mltText.copy(text().replace("&","&amp;amp;"), fontSizeWithCoeff)
    }
    fun listOfMltText(): List<MltText> = getSyllables().map { it.mltText() }
    fun w(): Int = listOfMltText().lastOrNull()?.let { it.x + it.w() } ?: 0
    fun h(): Int = Karaoke.voices[0].groups[groupId].mltText.copy("0", fontSize).h()

    fun h(songVersion: SongVersion): Int {
        val haveNotes = songVersion.producers.contains(ProducerType.MELODYNOTE) && this.getSyllables().any { it.note != "" }
        val haveChords = songVersion.producers.contains(ProducerType.CHORDS) && this.getSyllables().any { it.chord != "" }

        val deltaY = if (haveNotes) {
            val sylFontSize = this.fontSize
            val melodyNoteFontSize = (sylFontSize * Karaoke.melodyNoteHeightCoefficient).toInt()
            val melodyNoteMltTextHeight = Karaoke.melodyNoteFont.copy("C", melodyNoteFontSize).h()
            val noteHeight = (melodyNoteMltTextHeight * Karaoke.melodyNoteHeightOffsetCoefficient).toInt()

            val tabsHeightCoefficient = Karaoke.melodyTabsHeightCoefficient
            val tabsFontSize = (sylFontSize * tabsHeightCoefficient).toInt()
            val tabsFont = Karaoke.melodyTabsFont
            val tabsMltTextHeight = tabsFont.copy("C", tabsFontSize).h()
            val tabsHeightOffsetCoefficient = Karaoke.melodyTabsHeightOffsetCoefficient
            val heightBetweenTabsLines = (tabsMltTextHeight * tabsHeightOffsetCoefficient).toInt()

            val tabsHeight = tabsMltTextHeight + 5 * heightBetweenTabsLines
            noteHeight + tabsHeight
        } else if (haveChords) {
            val sylFontSize = this.fontSize
            val chordsFontSize = (sylFontSize * Karaoke.chordsHeightCoefficient).toInt()
            val chordsMltTextHeight = Karaoke.chordsFont.copy("C", chordsFontSize).h()
            val noteHeight = (chordsMltTextHeight * Karaoke.chordsHeightOffsetCoefficient).toInt()
            noteHeight
        } else {
            0
        }
        return h() + deltaY
    }

    fun lineElementStartMs(): Long = getSyllables().firstOrNull()?.syllableStartMs ?: 0
    fun lineElementEndMs(): Long = getSyllables().lastOrNull()?.syllableEndMs ?: 0

    fun lineElementDurationMs(): Long = lineElementEndMs() - lineElementStartMs()

    var elementId: Int = -1

    fun isCrossing(otherLineElement: SettingVoiceLineElement): Boolean {
        return (this.lineElementDurationMs() + otherLineElement.lineElementDurationMs()) > (Math.max(this.lineElementEndMs(), otherLineElement.lineElementEndMs()) - Math.min(this.lineElementStartMs(), otherLineElement.lineElementStartMs()))
    }

    fun transformProperties(): List<TransformProperty> {
        if (getSyllables().isEmpty() || type == SettingVoiceLineElementTypes.EMPTY || type == SettingVoiceLineElementTypes.NEWLINE|| type == SettingVoiceLineElementTypes.COMMENT) return emptyList()
        val result: MutableList<TransformProperty> = mutableListOf()
        val firstSyllable = getSyllables().first()
        val lastSyllable = getSyllables().last()
        val zeroTp = TransformProperty(
            time = convertMillisecondsToTimecode(0),
            x =  firstSyllable.startTransformProperty().x,
            y =  firstSyllable.startTransformProperty().y,
            w =  firstSyllable.startTransformProperty().w,
            h =  firstSyllable.startTransformProperty().h,
            opacity = 0.0
        )
        val firstTp = TransformProperty(
            time = convertMillisecondsToTimecode(firstSyllable.syllableStartMsWithDelta()),
            x =  firstSyllable.startTransformProperty().x,
            y =  firstSyllable.startTransformProperty().y,
            w =  firstSyllable.startTransformProperty().w,
            h =  firstSyllable.startTransformProperty().h,
            opacity = 0.0
        )
        val lastTp = TransformProperty(
            time = convertMillisecondsToTimecode(lastSyllable.syllableEndMsWithDelta() + 1000),
            x =  lastSyllable.endTransformProperty().x,
            y =  lastSyllable.endTransformProperty().y,
            w =  lastSyllable.endTransformProperty().w,
            h =  lastSyllable.endTransformProperty().h,
            opacity = 0.0
        )
        if (zeroTp.time != firstTp.time) {
            result.add(zeroTp)
        }

        result.add(firstTp)
        getSyllables().forEach { syllable ->
            if (syllable.previous == null || syllable.previous!!.syllableEndMsWithDelta() < syllable.syllableStartMsWithDelta()) {
                result.add(syllable.startTransformProperty())
            }
            result.add(syllable.endTransformProperty())
        }
        result.add(lastTp)
//        return result.joinToString(";")
        return result
    }

}
