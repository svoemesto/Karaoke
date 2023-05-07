package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.mlt.MltText
import java.awt.Color
import java.awt.Font
import java.io.Serializable

data class KaraokeVoice (
    val groups: MutableList<KaraokeVoiceGroup>,
    val fill: KaraokeVoiceFill
) : Serializable {
    fun replaceFontSize(fontSizePt: Int) {
        groups.forEach { group ->
            group.mltText.font = Font(group.mltText.font.name, group.mltText.font.style, fontSizePt)
        }
    }
}

data class KaraokeVoiceGroup(
    var mltText: MltText
) : Serializable

data class KaraokeVoiceFill(
    val evenColor: Color,
    val evenOpacity: Double,
    val oddColor: Color,
    val oddOpacity: Double
) : Serializable