package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.mlt.mko.*
import com.svoemesto.karaokeapp.mlt.mko2.*
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.textfiledictionary.CensoredWordsDictionary
import com.svoemesto.karaokeapp.textfiledictionary.TestDictionary
import com.svoemesto.karaokeapp.textfiledictionary.YoWordsDictionary
import org.springframework.messaging.simp.SimpMessagingTemplate

val idProducerSongText = ProducerType.SONGTEXT.ordinal * 10
val idProducerSongTextLine = ProducerType.SONGTEXTLINE.ordinal * 10
val idProducerHorizon = ProducerType.HORIZON.ordinal * 10
val idProducerFillColorSongtextEven = ProducerType.FILLCOLORSONGTEXT.ordinal * 10
val idProducerFillColorSongtextOdd = ProducerType.FILLCOLORSONGTEXT.ordinal * 10 + 1
val idProducerHeader = ProducerType.HEADER.ordinal * 10
val idProducerBackground = ProducerType.BACKGROUND.ordinal * 10
val idProducerCounter4 = ProducerType.COUNTER.ordinal * 10 + 4
val idProducerCounter3 = ProducerType.COUNTER.ordinal * 10 + 3
val idProducerCounter2 = ProducerType.COUNTER.ordinal * 10 + 2
val idProducerCounter1 = ProducerType.COUNTER.ordinal * 10 + 1
val idProducerCounter0 = ProducerType.COUNTER.ordinal * 10
val idProducerAudioSong = ProducerType.AUDIOSONG.ordinal * 10
val idProducerAudioMusic = ProducerType.AUDIOMUSIC.ordinal * 10
val idProducerAudioVocal = ProducerType.AUDIOVOCAL.ordinal * 10
val idProducerAudioBass = ProducerType.AUDIOBASS.ordinal * 10
val idProducerAudioDrums = ProducerType.AUDIODRUMS.ordinal * 10
val idProducerProgress = ProducerType.PROGRESS.ordinal * 10 + 1
val idProducerWatermark = ProducerType.WATERMARK.ordinal * 10 + 1
val idProducerFaderText = ProducerType.FADERTEXT.ordinal * 10 + 1
val idProducerFaderChords = ProducerType.FADERCHORDS.ordinal * 10 + 1
val idProducerBackChords = ProducerType.BACKCHORDS.ordinal * 10 + 1
val idProducerFingerboard = ProducerType.FINGERBOARD.ordinal * 10 + 1
val idProducerSplashstart = ProducerType.SPLASHSTART.ordinal * 10 + 1
val idProducerBoosty = ProducerType.BOOSTY.ordinal * 10 + 1
val idProducerFlash = ProducerType.FLASH.ordinal * 10 + 1


val delimiterVoices = "|[VOICE]|"
val delimiterGroups = "|[GROUP]|"
val delimiterFields = "|[FIELD]|"
val delimiterVoiceFields = "|[VOICEFIELDS]|"
val delimiterNames = "|[NAME]|"

const val MAIN_FONT_NAME = "Roboto"
//const val MAIN_FONT_NAME = "Roboto Black"
//const val MAIN_FONT_NAME = "Ubuntu Light"
//const val MAIN_FONT_NAME = "Montserrat SemiBold"
const val LINE_SPACING = 0L
const val SHADOW = "1;#64000000;3;3;3"
const val ALIGNMENT = 0L
const val TYPEWRITER = "0;2;1;0;0"
const val LETTERS_VOWEL = "EUIOAeuioaЁУЕЫАОЭЯИЮёуеыаоэяиюѣ"
const val NOTES_SYMBOLS = "♬♩♪△▲⬦⬥"

const val DEMUCS_MODEL_NAME = "hdemucs_mmi"

const val PATH_TO_FFMPEG = "/bin/ffmpeg"
const val PATH_TO_FFPLAY = "/bin/ffplay"
const val PATH_TO_FFPROBE = "/bin/ffprobe"
const val PATH_TO_PROFILES = "/usr/share/mlt-7/profiles"
const val PATH_TO_MELT = "/bin/melt"
const val PATH_TO_MEDIAINFO = "/bin/mediainfo"
const val PATH_TO_ODS = "/home/nsa/Documents/Караоке/Караоке.ods"

const val ODS_COLUMN_DATE = "Дата"
const val ODS_COLUMN_TIME = "Время"
const val ODS_COLUMN_AUTHOR = "Исполнитель"
const val ODS_COLUMN_YEAR = "Год"
const val ODS_COLUMN_ALBUM = "Альбом"
const val ODS_COLUMN_TRACK = "Трек"
const val ODS_COLUMN_SONG = "Композиция"
const val ODS_COLUMN_TONE = "Тональность"
const val ODS_COLUMN_BPM = "Темп"
const val ODS_COLUMN_FORMAT = "Формат"
const val ODS_COLUMN_BOOSTY = "Boosty"
const val ODS_COLUMN_YOUTUBE_LIRIC = "Lyric"
const val ODS_COLUMN_YOUTUBE_LIRIC_BT = "Lyric BT"
const val ODS_COLUMN_YOUTUBE_KARAOKE = "Karaoke"
const val ODS_COLUMN_YOUTUBE_KARAOKE_BT = "Karaoke BT"
const val ODS_COLUMN_YOUTUBE_CHORDS = "Chords"
const val ODS_COLUMN_YOUTUBE_CHORDS_BT = "Chords BT"
const val URL_PREFIX_BOOSTY = "https://boosty.to/svoemesto/posts/{REPLACE}"
const val URL_PREFIX_YOUTUBE_PLAY = "https://dzen.ru/video/watch/{REPLACE}"
const val URL_PREFIX_YOUTUBE_EDIT = "https://dzen.ru/profile/editor/svoemesto/publications?videoEditorPublicationId={REPLACE}"
const val URL_PREFIX_VK_PLAY = "https://vk.com/video{REPLACE}"
const val URL_PREFIX_VK_EDIT = "https://vk.com/video{REPLACE}"
const val URL_PREFIX_VK = "https://vk.com/wall-{REPLACE}"

const val URL_PREFIX_TELEGRAM_PLAY = "https://t.me/svoemestokaraoke/{REPLACE}"
const val URL_PREFIX_TELEGRAM_EDIT = "https://t.me/svoemestokaraoke/{REPLACE}"

const val CONNECTION_URL = "jdbc:postgresql://localhost:5430/karaoke?currentSchema=public"
const val CONNECTION_USER = "postgres"
const val CONNECTION_PASSWORD = "postgres"

const val PATH_TO_STORE_FOLDER = "/clouds/Yandex.Disk/Karaoke"

val PROJECT_ROOT_FOLDERS = listOf(
    "/home/nsa/Documents/Караоке",
    "/media/nsa/FilesSSD1Tb/KaraokeDone",
    "/clouds/KaraokeDone"
)

const val YO_FILE_PATH = "/home/nsa/Documents/Караоке/Слова_с_буквой_ё.txt"
const val CENSORED_FILE_PATH = "/home/nsa/Documents/Караоке/censored.txt"
const val TESTDICT_FILE_PATH = "/home/nsa/Documents/Караоке/test_dict.txt"

val TEXT_FILE_DICTS = mapOf(
    "Слова с Ё" to YoWordsDictionary::class.java,
    "Censored" to CensoredWordsDictionary::class.java,
    "Тестовый словарь" to TestDictionary::class.java
)

lateinit var WEBSOCKET: SimpMessagingTemplate

val producerTypeClass = mapOf(
    ProducerType.AUDIOVOCAL to MkoAudio::class.java,
    ProducerType.AUDIOMUSIC to MkoAudio::class.java,
    ProducerType.AUDIOSONG to MkoAudio::class.java,
    ProducerType.AUDIOBASS to MkoAudio::class.java,
    ProducerType.AUDIODRUMS to MkoAudio::class.java,
    ProducerType.BACKGROUND to MkoBackground::class.java,
    ProducerType.HORIZON to MkoHorizon::class.java,
    ProducerType.FLASH to MkoFlash::class.java,
    ProducerType.PROGRESS to MkoProgress::class.java,
    ProducerType.FILLCOLORSONGTEXT to MkoFillcolorSongtext::class.java,
    ProducerType.SONGTEXT to MkoSongText::class.java,
    ProducerType.SONGTEXTLINE to MkoSongTextLine::class.java,
    ProducerType.COUNTER to MkoCounter::class.java,
    ProducerType.FADERTEXT to MkoFaderText::class.java,
    ProducerType.FADERCHORDS to MkoFaderChords::class.java,
    ProducerType.BACKCHORDS to MkoBackChords::class.java,
    ProducerType.FINGERBOARD to MkoFingerboard::class.java,
    ProducerType.HEADER to MkoHeader::class.java,
    ProducerType.WATERMARK to MkoWatermark::class.java,
    ProducerType.SPLASHSTART to MkoSplashStart::class.java,
    ProducerType.BOOSTY to MkoBoosty::class.java,
    ProducerType.MAINBIN to MkoMainBin::class.java,
    ProducerType.BLACKTRACK to MkoBlackTrack::class.java,
    ProducerType.VOICES to MkoVoices::class.java,
    ProducerType.VOICE to MkoVoice::class.java,
    ProducerType.COUNTERS to MkoCounters::class.java,
    ProducerType.FILLCOLORSONGTEXTS to MkoFillcolorSongtexts::class.java,

)

//val producerTypeClass2 = mapOf(
//    ProducerType.AUDIOVOCAL to Mko2Audio::class.java,
//    ProducerType.AUDIOMUSIC to Mko2Audio::class.java,
//    ProducerType.AUDIOSONG to Mko2Audio::class.java,
//    ProducerType.AUDIOBASS to Mko2Audio::class.java,
//    ProducerType.AUDIODRUMS to Mko2Audio::class.java,
//    ProducerType.BACKGROUND to Mko2Background::class.java,
//    ProducerType.HORIZON to Mko2Horizon::class.java,
//    ProducerType.FLASH to Mko2Flash::class.java,
//    ProducerType.PROGRESS to Mko2Progress::class.java,
//    ProducerType.FILLCOLORSONGTEXT to Mko2FillcolorSongtext::class.java,
//    ProducerType.SONGTEXT to Mko2SongText::class.java,
//    ProducerType.SONGTEXTLINE to Mko2SongTextLine::class.java,
//    ProducerType.COUNTER to Mko2Counter::class.java,
//    ProducerType.FADERTEXT to Mko2FaderText::class.java,
//    ProducerType.FADERCHORDS to Mko2FaderChords::class.java,
//    ProducerType.BACKCHORDS to Mko2BackChords::class.java,
//    ProducerType.FINGERBOARD to Mko2Fingerboard::class.java,
//    ProducerType.HEADER to Mko2Header::class.java,
//    ProducerType.WATERMARK to Mko2Watermark::class.java,
//    ProducerType.SPLASHSTART to Mko2SplashStart::class.java,
//    ProducerType.BOOSTY to Mko2Boosty::class.java
//)