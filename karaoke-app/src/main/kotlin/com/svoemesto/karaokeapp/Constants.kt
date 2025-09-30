package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.mlt.mko.*
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.textfiledictionary.CensoredWordsDictionary
import com.svoemesto.karaokeapp.textfiledictionary.TestDictionary
import com.svoemesto.karaokeapp.textfiledictionary.YoWordsDictionary
const val LIMIT_ROWS_SETTINGS_TABLE_UPDATE = 1000
const val LIMIT_ROWS_PICTURES_TABLE_UPDATE = 100
const val CURRENT_RESULT_VERSION = 10L
const val COUNT_HISTORY_LINES = 30
const val PATH_TO_TEMP_DEMUCS_FOLDER = "/sm-karaoke/system/demucs-docker/tmp"
const val COLOR_ALL_DONE = "#7FFFD4"        // Полностью готово
const val COLOR_OVERDUE = "#BDB76B"         // Публикация прошла, но не все ссылки заполнены
const val COLOR_TODAY = "#FFFF00"           // Сегодня
const val COLOR_ALL_UPLOADED = "#DCDCDC"    // Готово к публикации (всё загружено)
const val COLOR_WO_TG = "#87CEFA"           // Нет TG
const val COLOR_WO_VK = "#FFDAB9"           // Нет VK
const val COLOR_WO_DZEN = "#FF8000"         // Нет DZEN
const val COLOR_WO_VKG = "#FFC880"         // Нет VKG

val symbolsWeightCoeff: Map<String, Double> = mapOf(
    "●" to 1.0,
    "∙" to 1.0,
    "◉" to 1.0,
    "♪" to 1.0,
)

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
const val ENGLISH_LETTERS = "QWERTYUIOPASDFGHJKLZXCVBNMqwertyuiopasdfghjklzxcvbnm"
const val DEMUCS_MODEL_NAME = "hdemucs_mmi"

const val PATH_TO_FFMPEG = "/bin/ffmpeg"
const val PATH_TO_FFPLAY = "/bin/ffplay"
const val PATH_TO_FFPROBE = "/bin/ffprobe"
const val PATH_TO_PROFILES = "/usr/share/mlt-7/profiles"
const val PATH_TO_MELT = "/bin/melt"
const val PATH_TO_MEDIAINFO = "/bin/mediainfo"
const val PATH_TO_ODS = "/sm-karaoke/system/Караоке.ods"
const val PATH_TO_LOGS = "/sm-karaoke/system/logs"

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
const val ODS_COLUMN_DZEN_LIRIC = "Lyric"
const val ODS_COLUMN_DZEN_LIRIC_BT = "Lyric BT"
const val ODS_COLUMN_DZEN_KARAOKE = "Karaoke"
const val ODS_COLUMN_DZEN_KARAOKE_BT = "Karaoke BT"
const val ODS_COLUMN_DZEN_CHORDS = "Chords"
const val ODS_COLUMN_DZEN_CHORDS_BT = "Chords BT"
const val URL_PREFIX_SM = "https://sm-karaoke.ru/song?id={REPLACE}"
const val URL_PREFIX_BOOSTY = "https://boosty.to/svoemesto/posts/{REPLACE}"
const val URL_PREFIX_DZEN_PLAY = "https://dzen.ru/video/watch/{REPLACE}"
const val URL_PREFIX_DZEN_EDIT = "https://dzen.ru/profile/editor/svoemesto/publications?videoEditorPublicationId={REPLACE}"
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
    "/sm-karaoke/work",
    "/sm-karaoke/done1",
    "/sm-karaoke/done2"
)

const val YO_FILE_PATH = "/sm-karaoke/system/Слова_с_буквой_ё.txt"
const val CENSORED_FILE_PATH = "/sm-karaoke/system/censored.txt"
const val TESTDICT_FILE_PATH = "/sm-karaoke/system/test_dict.txt"
const val SONGS_HISTORY_FILE_PATH = "/sm-karaoke/system/songs_history.txt"
const val WEBVUE_PROPERTIES_FILE_PATH = "/sm-karaoke/system/webvue_properties.txt"

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
    ProducerType.CHORDPICTUREFADER to MkoChordPictureFader::class.java,
    ProducerType.BACKCHORDS to MkoBackChords::class.java,
    ProducerType.FINGERBOARD to MkoFingerboard::class.java,
    ProducerType.CHORDSBOARD to MkoChordBoard::class.java,
    ProducerType.CHORDPICTURELINES to MkoChordPictureLines::class.java,
    ProducerType.CHORDPICTURELINETRACK to MkoChordPictureLineTrack::class.java,
    ProducerType.CHORDPICTURELINE to MkoChordPictureLine::class.java,
    ProducerType.CHORDPICTUREELEMENT to MkoChordPictureElement::class.java,
    ProducerType.CHORDPICTUREIMAGE to MkoChordPictureImage::class.java,
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
    ProducerType.CHORDS to MkoChords::class.java,

)

val WORKING_DATABASE = Connection.local()
//val WORKING_DATABASE = Connection.LOCAL
//val WORKING_DATABASE = Connection.REMOTE

val USER_AGENTS = listOf(
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.2 Safari/605.1.15",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:89.0) Gecko/20100101 Firefox/89.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/91.0.4472.124 Safari/605.1.15",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.131 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.2 Safari/605.1.15",
    "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:89.0) Gecko/20100101 Firefox/89.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.1 Safari/605.1.15",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Edg/91.0.864.59",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 Edg/90.0.818.56",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36 Edg/89.0.774.68",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36 Edg/88.0.705.74",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36 Edg/87.0.664.66",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 OPR/77.0.4054.277",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 OPR/76.0.4017.107",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36 OPR/75.0.3969.149",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36 OPR/74.0.3911.198",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36 OPR/73.0.3856.287",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Vivaldi/4.0.2311.172",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 Vivaldi/4.0.2295.81",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36 Vivaldi/4.0.2271.76",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36 Vivaldi/4.0.2256.48",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36 Vivaldi/4.0.2246.88",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 YaBrowser/21.6.0.1012 Yowser/2.5",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 YaBrowser/21.5.2.98 Yowser/2.5",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36 YaBrowser/21.4.2.104 Yowser/2.5",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36 YaBrowser/21.3.0.1084 Yowser/2.5",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36 YaBrowser/21.2.3.108 Yowser/2.5",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 SAMSUNG Browser/14.2",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 SAMSUNG Browser/13.2",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36 SAMSUNG Browser/13.1",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36 SAMSUNG Browser/13.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36 SAMSUNG Browser/12.1",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 SamsungBrowser/14.2",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 SamsungBrowser/13.2",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36 SamsungBrowser/13.1",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36 SamsungBrowser/13.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36 SamsungBrowser/12.1",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Brave/1.27.114",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 Brave/1.26.117",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36 Brave/1.25.72",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36 Brave/1.24.89",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36 Brave/1.23.77",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Chrome/91.0.4472.124",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 Chrome/90.0.4430.212",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36 Chrome/89.0.4389.114",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36 Chrome/88.0.4324.182",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36 Chrome/87.0.4280.141",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Firefox/89.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 Firefox/88.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36 Firefox/87.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36 Firefox/86.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36 Firefox/85.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Edge/91.0.864.59",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 Edge/90.0.818.56",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36 Edge/89.0.774.68",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36 Edge/88.0.705.74",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36 Edge/87.0.664.66",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Opera/77.0.4054.277",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 Opera/76.0.4017.107",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36 Opera/75.0.3969.149",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36 Opera/74.0.3911.198",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36 Opera/73.0.3856.287",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Vivaldi/4.0.2311.172",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 Vivaldi/4.0.2295.81",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36 Vivaldi/4.0.2271.76",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36 Vivaldi/4.0.2256.48",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36 Vivaldi/4.0.2246.88",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 YaBrowser/21.6.0.1012 Yowser/2.5",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 YaBrowser/21.5.2.98 Yowser/2.5",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36 YaBrowser/21.4.2.104 Yowser/2.5",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36 YaBrowser/21.3.0.1084 Yowser/2.5",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36 YaBrowser/21.2.3.108 Yowser/2.5",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 SAMSUNG Browser/14.2",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 SAMSUNG Browser/13.2",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36 SAMSUNG Browser/13.1",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36 SAMSUNG Browser/13.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36 SAMSUNG Browser/12.1",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 SamsungBrowser/14.2",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 SamsungBrowser/13.2",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36 SamsungBrowser/13.1",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36 SamsungBrowser/13.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36 SamsungBrowser/12.1",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Brave/1.27.114",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 Brave/1.26.117",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36 Brave/1.25.72",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36 Brave/1.24.89",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36 Brave/1.23.77"
)