package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.mlt.MltObjectType
import com.svoemesto.karaokeapp.mlt.MltShape
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.mko.*
import com.svoemesto.karaokeapp.mlt.setting
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.textfiledictionary.CensoredWordsDictionary
import com.svoemesto.karaokeapp.textfiledictionary.TestDictionary
import com.svoemesto.karaokeapp.textfiledictionary.YoWordsDictionary
import java.awt.Color
import java.awt.Font

const val CURRENT_RESULT_VERSION = 10L
const val COUNT_HISTORY_LINES = 10

const val COLOR_ALL_DONE = "#7FFFD4"        // Полностью готово
const val COLOR_OVERDUE = "#BDB76B"         // Публикация прошла, но не все ссылки заполнены
const val COLOR_TODAY = "#FFFF00"           // Сегодня
const val COLOR_ALL_UPLOADED = "#DCDCDC"    // Готово к публикации (всё загружено)
const val COLOR_WO_TG = "#87CEFA"           // Нет TG
const val COLOR_WO_VK = "#FFDAB9"           // Нет VK
const val COLOR_WO_DZEN = "#FF8000"         // Нет DZEN
const val COLOR_WO_VKG = "#FFC880"         // Нет VKG


val delimiterVoices = "|[VOICE]|"
val delimiterGroups = "|[GROUP]|"
val delimiterFields = "|[FIELD]|"
val delimiterVoiceFields = "|[VOICEFIELDS]|"
val delimiterNames = "|[NAME]|"

//const val MAIN_FONT_NAME = "Roboto"
const val MAIN_FONT_NAME = "Roboto Black"
const val CHORDS_FONT_NAME = "Fira Sans Extra Condensed Medium"
const val CHORDS_CAPO_FONT_NAME = "Fira Sans Extra Condensed Medium"
const val MELODY_NOTE_FONT_NAME = "Fira Sans Extra Condensed Medium"
const val MELODY_OCTAVE_FONT_NAME = "Fira Sans Extra Condensed Medium"
//const val MAIN_FONT_NAME = "Ubuntu Light"
//const val MAIN_FONT_NAME = "Montserrat SemiBold"
const val LINE_SPACING = 0L
const val SHADOW = "1;#64000000;3;3;3"
const val ALIGNMENT = 0L
const val TYPEWRITER = "0;2;1;0;0"
val vovels = "ёуеыаоэяиюeuioaїієѣ"
val LETTERS_VOWEL = "${vovels}${vovels.uppercase()}♪"
const val NOTES_SYMBOLS = "♬♩♪△▲⬦⬥"
const val RUSSIN_LETTERS = "ЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮЁйцукенгшщзхъфывапролджэячсмитьбюё"
const val DEMUCS_MODEL_NAME = "hdemucs_mmi"

const val PATH_TO_FFMPEG = "/bin/ffmpeg"
const val PATH_TO_FFPLAY = "/bin/ffplay"
const val PATH_TO_FFPROBE = "/bin/ffprobe"
const val PATH_TO_PROFILES = "/usr/share/mlt-7/profiles"
const val PATH_TO_MELT = "/bin/melt"
const val PATH_TO_MEDIAINFO = "/bin/mediainfo"
const val PATH_TO_ODS = "/home/nsa/Documents/Караоке/Караоке.ods"
const val PATH_TO_LOGS = "/home/nsa/Documents/Караоке/logs"

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
const val URL_PREFIX_SM = "https://sm-karaoke.ru/song?id={REPLACE}"
const val URL_PREFIX_BOOSTY = "https://boosty.to/svoemesto/posts/{REPLACE}"
const val URL_PREFIX_YOUTUBE_PLAY = "https://dzen.ru/video/watch/{REPLACE}"
const val URL_PREFIX_YOUTUBE_EDIT = "https://dzen.ru/profile/editor/svoemesto/publications?videoEditorPublicationId={REPLACE}"
const val URL_PREFIX_VK_PLAY = "https://vkvideo.ru/video{REPLACE}"
const val URL_PREFIX_VK_EDIT = "https://vkvideo.ru/video{REPLACE}"
const val URL_PREFIX_VK = "https://vk.com/wall-{REPLACE}"

const val URL_PREFIX_TELEGRAM_PLAY = "https://t.me/svoemestokaraoke/{REPLACE}"
const val URL_PREFIX_TELEGRAM_EDIT = "https://t.me/svoemestokaraoke/{REPLACE}"

const val URL_PREFIX_PL_PLAY = "https://plvideo.ru/watch?v={REPLACE}"
const val URL_PREFIX_PL_EDIT = "https://studio.plvideo.ru/channel/bbj0HWC8H7ii/video/{REPLACE}/edit"

const val URL_PREFIX_SPONSR_PLAY = "https://sponsr.ru/smkaraoke/manage/post/{REPLACE}/"
const val URL_PREFIX_SPONSR_EDIT = "https://sponsr.ru/smkaraoke/{REPLACE}/"

//const val CONNECTION_URL = "jdbc:postgresql://localhost:5430/karaoke?currentSchema=public"
//const val CONNECTION_USER = "postgres"
//const val CONNECTION_PASSWORD = "postgres"

const val PATH_TO_STORE_FOLDER = "/clouds/Yandex.Disk/Karaoke"

val PROJECT_ROOT_FOLDERS = listOf(
    "/home/nsa/Documents/Караоке",
    "/media/nsa/FilesSSD1Tb/KaraokeDone",
    "/clouds/KaraokeDone"
)

const val YO_FILE_PATH = "/home/nsa/Documents/Караоке/Слова_с_буквой_ё.txt"
const val CENSORED_FILE_PATH = "/home/nsa/Documents/Караоке/censored.txt"
const val TESTDICT_FILE_PATH = "/home/nsa/Documents/Караоке/test_dict.txt"
const val SONGS_HISTORY_FILE_PATH = "/home/nsa/Documents/Караоке/songs_history.txt"

val TEXT_FILE_DICTS = mapOf(
    "Слова с Ё" to YoWordsDictionary::class.java,
    "Censored" to CensoredWordsDictionary::class.java,
    "Тестовый словарь" to TestDictionary::class.java
)

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
    ProducerType.VOICES to MkoVoices::class.java,
    ProducerType.VOICE to MkoVoice::class.java,
    ProducerType.COUNTERS to MkoCounters::class.java,
    ProducerType.SCROLLERS to MkoScrollers::class.java,
    ProducerType.SCROLLER to MkoScroller::class.java,
    ProducerType.SCROLLERTRACK to MkoScrollerTrack::class.java,
    ProducerType.FILLCOLORSONGTEXTS to MkoFillcolorSongtexts::class.java,
    ProducerType.LINES to MkoLines::class.java,
    ProducerType.LINETRACK to MkoLineTrack::class.java,
    ProducerType.LINE to MkoLine::class.java,
    ProducerType.ELEMENT to MkoElement::class.java,
    ProducerType.STRING to MkoString::class.java,
    ProducerType.FILL to MkoFill::class.java,
    ProducerType.SEPAR to MkoSepar::class.java,
    ProducerType.MELODYNOTE to MkoMelodyNote::class.java,
    ProducerType.MELODYTABS to MkoMelodyTabs::class.java,

)

val WORKING_DATABASE = Connection.local()
//val WORKING_DATABASE = Connection.LOCAL
//val WORKING_DATABASE = Connection.REMOTE
