import mlt.MltText
import java.awt.Color
import java.awt.Font

data class KaraokeVoice (
    val groups: MutableList<KaraokeVoiceGroup>,
    val fill: KaraokeVoiceFill
) {
    fun replaceFontSize(fontSizePt: Int) {
        groups.forEach { group ->
            group.songtextTextMltText.font = Font(group.songtextTextMltText.font.name, group.songtextTextMltText.font.style, fontSizePt)
            group.songtextBeatMltText.font = Font(group.songtextBeatMltText.font.name, group.songtextBeatMltText.font.style, fontSizePt)
        }
    }
}

data class KaraokeVoiceGroup(
    var songtextTextMltText: MltText,
    var songtextBeatMltText: MltText
)

data class KaraokeVoiceFill(
    val evenColor: Color,
    val evenOpacity: Double,
    val oddColor: Color,
    val oddOpacity: Double
)