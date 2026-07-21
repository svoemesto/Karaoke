package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.mlt.MltText
import java.awt.Color
import java.awt.Font
import java.io.Serializable

/**
 * Класс Karaoke Voice.
 *
 * @see docs/features/dual-db-sync.md
 */
data class KaraokeVoice(
    val groups: MutableList<KaraokeVoiceGroup>,
    val fill: KaraokeVoiceFill,
) : Serializable {
    @Suppress("unused")
    fun replaceFontSize(fontSizePt: Int) {
        groups.forEach { group ->
            group.mltText.font = Font(group.mltText.font.name, group.mltText.font.style, fontSizePt)
        }
    }
}

/**
 * Класс Karaoke Voice Group.
 *
 * @see docs/features/dual-db-sync.md
 */
data class KaraokeVoiceGroup(
    var mltText: MltText,
) : Serializable

/**
 * Класс Karaoke Voice Fill.
 *
 * @see docs/features/dual-db-sync.md
 */
data class KaraokeVoiceFill(
    val evenColor: Color,
    val evenOpacity: Double,
    val oddColor: Color,
    val oddOpacity: Double,
) : Serializable
