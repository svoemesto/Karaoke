import java.awt.Color
import java.awt.Font

val delimiterVoices = "\n|[VOICE]|\n"
val delimiterGroups = "\n|[GROUP]|\n"
val delimiterFields = "\n|[FIELD]|\n"
val delimiterNames = "|[NAME]|"

const val PRESENT_TEXT_SUBS = true
const val PRESENT_BEAT_SUBS = true
const val PRESENT_COUNTERS = true
const val PRESENT_BACKGROUND = true
const val PRESENT_PROGRESS = true
const val PRESENT_HORIZON = true
const val PRESENT_FILLS = true
const val PRESENT_AUDIO_SONG = true
const val PRESENT_AUDIO_MUSIC = true
const val PRESENT_AUDIO_VOCAL = true
const val PRESENT_HEADER = true
const val PRESENT_MICROPHONE = true
const val PRESENT_LOGOTYPE = true

const val TIME_OFFSET_MS = 170L
const val HORIZON_OFFSET_PX = -7L
const val HEIGHT_CORRECTION = 0.0
const val KLT_ITEM_CONTENT_FONT_SIZE_PT = 100
const val FONT_UNDERLINE = 0L
const val FONT_ITALIC = 0L
const val FONT_WEIGHT = 50L
const val MAX_GROUPS = 3L
const val GROUP_0_FONT_COLOR_TEXT = "255,255,255,255"
const val GROUP_0_FONT_COLOR_BEAT = "155,255,155,255"
const val GROUP_1_FONT_COLOR_TEXT = "255,255,155,255"
const val GROUP_1_FONT_COLOR_BEAT = "105,255,105,255"
const val GROUP_2_FONT_COLOR_TEXT = "85,255,255,255"
const val GROUP_2_FONT_COLOR_BEAT = "105,255,105,255"
const val PROGRESS_COLOR = "255,255,255,255"
const val FONT_NAME = "Lobster"
const val FONT_STYLE = 0 // 0 - plain, 1 - bold, 2 - italic, 3 - bold+italic
const val FONT_SIZE_DEFAULT = 200

data class VoiceSetting(
    var fontText: Font,
    var fontBeat: Font,
    var colorText: Color,
    var colorBeat: Color
)

val VOICES_SETTINGS: List<List<VoiceSetting>> = mutableListOf(
    // Voice 0
    mutableListOf(
        // Voice 0 Group 0
        VoiceSetting(
            fontText = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            fontBeat = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            colorText = Color(255, 255, 255, 255),
            colorBeat = Color(155, 255, 255, 255)
        ),
        // Voice 0 Group 1
        VoiceSetting(
            fontText = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            fontBeat = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            colorText = Color(255, 255, 155, 255),
            colorBeat = Color(105, 255, 105, 255)
        ),
        // Voice 0 Group 2
        VoiceSetting(
            fontText = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            fontBeat = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            colorText = Color(85, 255, 255, 255),
            colorBeat = Color(105, 255, 105, 255)
        )
    ),
    // Voice 1
    mutableListOf(
        // Voice 1 Group 0
        VoiceSetting(
            fontText = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            fontBeat = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            colorText = Color(255, 255, 255, 255),
            colorBeat = Color(155, 255, 255, 255)
        ),
        // Voice 1 Group 1
        VoiceSetting(
            fontText = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            fontBeat = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            colorText = Color(255, 255, 155, 255),
            colorBeat = Color(105, 255, 105, 255)
        ),
        // Voice 1 Group 2
        VoiceSetting(
            fontText = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            fontBeat = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            colorText = Color(85, 255, 255, 255),
            colorBeat = Color(105, 255, 105, 255)
        )
    ),
    // Voice 2
    mutableListOf(
        // Voice 2 Group 0
        VoiceSetting(
            fontText = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            fontBeat = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            colorText = Color(255, 255, 255, 255),
            colorBeat = Color(155, 255, 255, 255)
        ),
        // Voice 2 Group 1
        VoiceSetting(
            fontText = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            fontBeat = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            colorText = Color(255, 255, 155, 255),
            colorBeat = Color(105, 255, 105, 255)
        ),
        // Voice 2 Group 2
        VoiceSetting(
            fontText = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            fontBeat = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            colorText = Color(85, 255, 255, 255),
            colorBeat = Color(105, 255, 105, 255)
        )
    ),
    // Voice 3
    mutableListOf(
        // Voice 3 Group 0
        VoiceSetting(
            fontText = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            fontBeat = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            colorText = Color(255, 255, 255, 255),
            colorBeat = Color(155, 255, 255, 255)
        ),
        // Voice 3 Group 1
        VoiceSetting(
            fontText = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            fontBeat = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            colorText = Color(255, 255, 155, 255),
            colorBeat = Color(105, 255, 105, 255)
        ),
        // Voice 3 Group 2
        VoiceSetting(
            fontText = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            fontBeat = Font(FONT_NAME,FONT_STYLE,FONT_SIZE_DEFAULT),
            colorText = Color(85, 255, 255, 255),
            colorBeat = Color(105, 255, 105, 255)
        )
    )
)

val DEFAULT_FONT = VOICES_SETTINGS[0][0].fontText

val GROUPS_FONT_COLORS_TEXT: Map<Long, String> = mutableMapOf(
    Pair(0, GROUP_0_FONT_COLOR_TEXT),
    Pair(1, GROUP_1_FONT_COLOR_TEXT),
    Pair(2, GROUP_2_FONT_COLOR_TEXT))
val GROUPS_FONT_COLORS_BEAT: Map<Long, String> = mutableMapOf(
    Pair(0, GROUP_0_FONT_COLOR_BEAT),
    Pair(1, GROUP_1_FONT_COLOR_BEAT),
    Pair(2, GROUP_2_FONT_COLOR_BEAT))
val GROUPS_TIMELINE_COLORS: Map<Long, String> = mutableMapOf(
    Pair(-1, "0,255,0,255"),
    Pair(0, "255,255,255,255"),
    Pair(1, "255,255,0,255"),
    Pair(2, "85,255,255,255"))

const val TITLE_POSITION_START_X_PX = 96L
const val TITLE_POSITION_START_Y_PX = 0L
const val TITLE_OFFSET_START_X_PX = -20L
//const val FONT_NAME = "JetBrains Mono"

const val MIN_MS_TO_SCROLL_LINE = 200L

const val LINE_SPACING = 0L
const val SHADOW = "1;#64000000;3;3;3"
const val ALIGNMENT = 0L
const val TYPEWRITER = "0;2;1;0;0"
val POINT_TO_PIXEL = listOf(0,2,3,4,6,7,9,10,11,12,13,15,16,17,18,20,21,22,24,25,26,28,29,30,32,33,34,36,37,38,40,41,42,44,45,46,48,49,50,51,53,54,55,57,58,59,61,62,63,65,66,67,69,70,71,73,74,75,77,78,79,81,82,83,84,86,87,88,90,91,92,94,95,96,98,99,100,102,103,104,106,107,108,110,111,112,114,115,116,117,119,120,121,123,124,125,127,128,129,131,132)
const val LETTERS_VOWEL = "EUIOAeuioaЁУЕЫАОЭЯИЮёуеыаоэяию"
var kdeLogoPath = ""
val kdeBackgroundFolderPath = "/home/nsa/Documents/SpaceBox4096"
var kdeMicrophonePath = ""