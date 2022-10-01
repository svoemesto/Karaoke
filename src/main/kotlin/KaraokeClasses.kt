import mlt.MltFont
import java.awt.Color
import java.awt.Font

data class KaraokeVoice (
    val groups: MutableList<KaraokeVoiceGroup>,
    val fill: KaraokeVoiceFill
) {
    fun replaceFontSize(fontSizePt: Int) {
        groups.forEach { group ->
            group.songtextTextMltFont.font =
                Font(group.songtextTextMltFont.font.name, group.songtextTextMltFont.font.style, fontSizePt)
            group.songtextBeatMltFont.font =
                Font(group.songtextBeatMltFont.font.name, group.songtextBeatMltFont.font.style, fontSizePt)
        }
    }
}

data class KaraokeVoiceGroup(
    var songtextTextMltFont: MltFont,
    var songtextBeatMltFont: MltFont
)

data class KaraokeVoiceFill(
    val evenColor: Color,
    val evenOpacity: Double,
    val oddColor: Color,
    val oddOpacity: Double
)