import Converter.Companion.getColorFromString
import Converter.Companion.getColorsFromString
import Converter.Companion.getFontFromString
import Converter.Companion.getStringFromColor
import Converter.Companion.getStringFromColors
import Converter.Companion.getStringFromFont
import Converter.Companion.getStringFromVoices
import Converter.Companion.getVoicesFromString
import java.awt.Color
import java.awt.Font
import java.io.File
import java.util.*

class Karaoke {

    data class KaraokeVoice (
        val groups: MutableList<KaraokeVoiceGroup>,
        val fill: KaraokeVoiceFill
    )
    data class KaraokeVoiceFill(
        val evenColor: Color,
        val evenOpacity: Double,
        val oddColor: Color,
        val oddOpacity: Double
    )
    data class KaraokeVoiceGroup(
        var songtextTextFont: Font,
        var songtextTextFontUnderline: Long,
        val songtextTextColor: Color,
        var songtextBeatFont: Font,
        var songtextBeatFontUnderline: Long,
        val songtextBeatColor: Color
    )
    companion object {
        private val fileNameXml = "src/main/resources/settings.xml"
        private val props = Properties()

        // Путь к папке с фонами
        var backgroundFolderPath: String
            get() {
                val defaultValue = "/home/nsa/Documents/SpaceBox4096"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("backgroundFolderPath",defaultValue)
            }
            set(value) {
                props.setProperty("backgroundFolderPath", value)
                props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        // Создавать логотип
        var createLogotype: Boolean
            get() {
                val defaultValue = "true"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createLogotype",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createLogotype", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(), null)
            }


        // Создавать микрофон
        var createMicrophone: Boolean
            get() {
                val defaultValue = "true"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createMicrophone",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createMicrophone", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(), null)
            }


        // Создавать заголовок
        var createHeader: Boolean
            get() {
                val defaultValue = "true"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createHeader",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createHeader", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(), null)
            }


        // Создавать аудио вокала
        var createAudioVocal: Boolean
            get() {
                val defaultValue = "true"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createAudioVocal",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createAudioVocal", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(), null)
            }


        // Создавать аудио музыки
        var createAudioMusic: Boolean
            get() {
                val defaultValue = "true"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createAudioMusic",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createAudioMusic", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        // Создавать аудио песни
        var createAudioSong: Boolean
            get() {
                val defaultValue = "true"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createAudioSong",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createAudioSong", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Создавать заливки
        var createFills: Boolean
            get() {
                val defaultValue = "true"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createFills",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createFills", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Раскрашивать горизонт
        var paintHorizon: Boolean
            get() {
                val defaultValue = "true"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("paintHorizon",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("paintHorizon", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Создавать горизонт
        var createHorizon: Boolean
            get() {
                val defaultValue = "true"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createHorizon",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createHorizon", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Создавать прогрессометр
        var createProgress: Boolean
            get() {
                val defaultValue = "true"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createProgress",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createProgress", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Создавать фон
        var createBackground: Boolean
            get() {
                val defaultValue = "true"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createBackground",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createBackground", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Создавать водяной знак
        var createWatermark: Boolean
            get() {
                val defaultValue = "true"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createWatermark",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createWatermark", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Создавать счётчики
        var createCounters: Boolean
            get() {
                val defaultValue = "true"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createCounters",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createCounters", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Создавать такты
        var createBeats: Boolean
            get() {
                val defaultValue = "true"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createBeats",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createBeats", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Создавать текст песни
        var createSongtext: Boolean
            get() {
                val defaultValue = "true"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createSongtext",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createSongtext", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Время (в миллисекундах) задержки звука от начала анимации
        var timeOffsetMs: Long
            get() {
                val defaultValue = "170"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("timeOffsetMs",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("timeOffsetMs", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Минимальное время (в миллисекундах) между линиями, меньше которого заливка последнего титра будет во время смещения линии
        var transferMinimumMsBetweenLinesToScroll: Long
            get() {
                val defaultValue = "200"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("transferMinimumMsBetweenLinesToScroll",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("transferMinimumMsBetweenLinesToScroll", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Отступ начала заливки от первого символа в строке (в пикселах)
        var songtextStartOffsetXpx: Long
            get() {
                val defaultValue = "20"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("songtextStartOffsetXpx",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("songtextStartOffsetXpx", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }


        // Начальная позиция по Х текста песни на экране (в % от ширины экрана)
        var songtextStartPositionXpercent: Double
            get() {
                val defaultValue = "5.0"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("songtextStartPositionXpercent",defaultValue).toDouble()
            }
            set(value) {
                props.setProperty("songtextStartPositionXpercent", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Начальная позиция по Х текста песни на экране (в пикселах)
        val songtextStartPositionXpx: Long
            get () {
                return (songtextStartPositionXpercent * frameWidthPx / 100).toLong()
            }

        // Смещение горизонта (в пикселах)
        var horizonOffsetPx: Long
            get() {
                val defaultValue = "-7"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("horizonOffsetPx",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("horizonOffsetPx", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Цвета горизонта для групп
        var horizonColors: MutableList<Color>
            get() {
                val defaultValue = "r=255;g=255;b=255;a=255|r=255;g=255;b=0;a=255|r=85;g=255;b=255;a=255"
                props.loadFromXML(File(fileNameXml).inputStream())
                return getColorsFromString(props.getProperty("horizonColors", defaultValue))
            }
            set(value) {
                props.setProperty("horizonColors", getStringFromColors(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Цвет горизонта
        var horizonColor: Color
            get() {
                val defaultValue = "r=0;g=255;b=0;a=255"
                props.loadFromXML(File(fileNameXml).inputStream())
                return getColorFromString(props.getProperty("horizonColor", defaultValue))
            }
            set(value) {
                props.setProperty("horizonColor", getStringFromColor(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Настройки текста для голосов - групп
        var voices: MutableList<KaraokeVoice>
            get() {
                val defaultValue = """songtextTextFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextTextFontUnderline|[NAME]|0
|[FIELD]|
songtextTextColor|[NAME]|r=255;g=255;b=255;a=255
|[FIELD]|
songtextBeatFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextBeatFontUnderline|[NAME]|0
|[FIELD]|
songtextBeatColor|[NAME]|r=155;g=255;b=255;a=255
|[GROUP]|
songtextTextFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextTextFontUnderline|[NAME]|0
|[FIELD]|
songtextTextColor|[NAME]|r=255;g=255;b=155;a=255
|[FIELD]|
songtextBeatFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextBeatFontUnderline|[NAME]|0
|[FIELD]|
songtextBeatColor|[NAME]|r=105;g=255;b=105;a=255
|[GROUP]|
songtextTextFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextTextFontUnderline|[NAME]|0
|[FIELD]|
songtextTextColor|[NAME]|r=85;g=255;b=155;a=255
|[FIELD]|
songtextBeatFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextBeatFontUnderline|[NAME]|0
|[FIELD]|
songtextBeatColor|[NAME]|r=105;g=255;b=105;a=255
|[VOICEFIELDS]|
evenColor|[NAME]|r=255;g=128;b=0;a=255
|[FIELD]|
evenOpacity|[NAME]|0.6
|[FIELD]|
oddColor|[NAME]|r=255;g=128;b=0;a=255
|[FIELD]|
oddOpacity|[NAME]|0.6
|[VOICE]|
songtextTextFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextTextFontUnderline|[NAME]|0
|[FIELD]|
songtextTextColor|[NAME]|r=255;g=255;b=255;a=255
|[FIELD]|
songtextBeatFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextBeatFontUnderline|[NAME]|0
|[FIELD]|
songtextBeatColor|[NAME]|r=155;g=255;b=255;a=255
|[GROUP]|
songtextTextFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextTextFontUnderline|[NAME]|0
|[FIELD]|
songtextTextColor|[NAME]|r=255;g=255;b=155;a=255
|[FIELD]|
songtextBeatFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextBeatFontUnderline|[NAME]|0
|[FIELD]|
songtextBeatColor|[NAME]|r=105;g=255;b=105;a=255
|[GROUP]|
songtextTextFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextTextFontUnderline|[NAME]|0
|[FIELD]|
songtextTextColor|[NAME]|r=85;g=255;b=155;a=255
|[FIELD]|
songtextBeatFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextBeatFontUnderline|[NAME]|0
|[FIELD]|
songtextBeatColor|[NAME]|r=105;g=255;b=105;a=255
|[VOICEFIELDS]|
evenColor|[NAME]|r=255;g=128;b=0;a=255
|[FIELD]|
evenOpacity|[NAME]|0.6
|[FIELD]|
oddColor|[NAME]|r=255;g=128;b=0;a=255
|[FIELD]|
oddOpacity|[NAME]|0.6
|[VOICE]|
songtextTextFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextTextFontUnderline|[NAME]|0
|[FIELD]|
songtextTextColor|[NAME]|r=255;g=255;b=255;a=255
|[FIELD]|
songtextBeatFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextBeatFontUnderline|[NAME]|0
|[FIELD]|
songtextBeatColor|[NAME]|r=155;g=255;b=255;a=255
|[GROUP]|
songtextTextFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextTextFontUnderline|[NAME]|0
|[FIELD]|
songtextTextColor|[NAME]|r=255;g=255;b=155;a=255
|[FIELD]|
songtextBeatFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextBeatFontUnderline|[NAME]|0
|[FIELD]|
songtextBeatColor|[NAME]|r=105;g=255;b=105;a=255
|[GROUP]|
songtextTextFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextTextFontUnderline|[NAME]|0
|[FIELD]|
songtextTextColor|[NAME]|r=85;g=255;b=155;a=255
|[FIELD]|
songtextBeatFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextBeatFontUnderline|[NAME]|0
|[FIELD]|
songtextBeatColor|[NAME]|r=105;g=255;b=105;a=255
|[VOICEFIELDS]|
evenColor|[NAME]|r=255;g=128;b=0;a=255
|[FIELD]|
evenOpacity|[NAME]|0.6
|[FIELD]|
oddColor|[NAME]|r=255;g=128;b=0;a=255
|[FIELD]|
oddOpacity|[NAME]|0.6""".trimIndent()
                props.loadFromXML(File(fileNameXml).inputStream())
                return getVoicesFromString(props.getProperty("voices", defaultValue))
            }
            set(value) {
                props.setProperty("voices", getStringFromVoices(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Ширина экрана в пикселах
        var frameWidthPx: Long
            get() {
                val defaultValue = "1920"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("frameWidthPx",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("frameWidthPx", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Высота экрана в пикселах
        var frameHeightPx: Long
            get() {
                val defaultValue = "1080"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("frameHeightPx",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("frameHeightPx", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Frames per seconds
        var frameFps: Double
            get() {
                val defaultValue = "60"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("frameFps",defaultValue).toDouble()
            }
            set(value) {
                props.setProperty("frameFps", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // HEADER

        // Заголовок - Название песни - шрифт
        var headerSongnameFont: Font
            get() {
                val defaultValue = "name=Tahoma;style=0;size=80"
                props.loadFromXML(File(fileNameXml).inputStream())
                return getFontFromString(props.getProperty("headerSongnameFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerSongnameFont", getStringFromFont(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Название песни - шрифт подчеркнутый (0 - нет, 1 - да)
        var headerSongnameFontUnderline: Long
            get() {
                val defaultValue = "0"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("headerSongnameFontUnderline",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("headerSongnameFontUnderline", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }


        // Заголовок - Название песни - шрифт
        var headerSongnameColor: Color
            get() {
                val defaultValue = "r=255;g=255;b=127;a=255"
                props.loadFromXML(File(fileNameXml).inputStream())
                return getColorFromString(props.getProperty("headerSongnameColor", defaultValue))
            }
            set(value) {
                props.setProperty("headerSongnameColor", getStringFromColor(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Автор - шрифт
        var headerAuthorFont: Font
            get() {
                val defaultValue = "name=Tahoma;style=0;size=30"
                props.loadFromXML(File(fileNameXml).inputStream())
                return getFontFromString(props.getProperty("headerAuthorFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerAuthorFont", getStringFromFont(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Автор - шрифт подчеркнутый (0 - нет, 1 - да)
        var headerAuthorFontUnderline: Long
            get() {
                val defaultValue = "0"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("headerAuthorFontUnderline",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("headerAuthorFontUnderline", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Автор - цвет
        var headerAuthorColor: Color
            get() {
                val defaultValue = "r=85;g=255;b=255;a=255"
                props.loadFromXML(File(fileNameXml).inputStream())
                return getColorFromString(props.getProperty("headerAuthorColor", defaultValue))
            }
            set(value) {
                props.setProperty("headerAuthorColor", getStringFromColor(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Название альбома - шрифт
        var headerAlbumFont: Font
            get() {
                val defaultValue = "name=Tahoma;style=0;size=30"
                props.loadFromXML(File(fileNameXml).inputStream())
                return getFontFromString(props.getProperty("headerAlbumFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerAlbumFont", getStringFromFont(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Название альбома - шрифт подчеркнутый (0 - нет, 1 - да)
        var headerAlbumFontUnderline: Long
            get() {
                val defaultValue = "0"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("headerAlbumFontUnderline",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("headerAlbumFontUnderline", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }


        // Заголовок - Название альбома - цвет
        var headerAlbumColor: Color
            get() {
                val defaultValue = "r=85;g=255;b=255;a=255"
                props.loadFromXML(File(fileNameXml).inputStream())
                return getColorFromString(props.getProperty("headerAlbumColor", defaultValue))
            }
            set(value) {
                props.setProperty("headerAuthorColor", getStringFromColor(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Тональность - шрифт
        var headerToneFont: Font
            get() {
                val defaultValue = "name=Tahoma;style=0;size=30"
                props.loadFromXML(File(fileNameXml).inputStream())
                return getFontFromString(props.getProperty("headerToneFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerAlbumFont", getStringFromFont(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Тональность - шрифт подчеркнутый (0 - нет, 1 - да)
        var headerToneFontUnderline: Long
            get() {
                val defaultValue = "0"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("headerToneFontUnderline",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("headerToneFontUnderline", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }


        // Заголовок - Тональность - цвет
        var headerToneColor: Color
            get() {
                val defaultValue = "r=85;g=255;b=255;a=255"
                props.loadFromXML(File(fileNameXml).inputStream())
                return getColorFromString(props.getProperty("headerToneColor", defaultValue))
            }
            set(value) {
                props.setProperty("headerToneColor", getStringFromColor(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Темп - шрифт
        var headerBpmFont: Font
            get() {
                val defaultValue = "name=Tahoma;style=0;size=30"
                props.loadFromXML(File(fileNameXml).inputStream())
                return getFontFromString(props.getProperty("headerBpmFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerBpmFont", getStringFromFont(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Темп - шрифт подчеркнутый (0 - нет, 1 - да)
        var headerBpmFontUnderline: Long
            get() {
                val defaultValue = "0"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("headerBpmFontUnderline",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("headerBpmFontUnderline", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Темп - цвет
        var headerBpmColor: Color
            get() {
                val defaultValue = "r=85;g=255;b=255;a=255"
                props.loadFromXML(File(fileNameXml).inputStream())
                return getColorFromString(props.getProperty("headerBpmColor", defaultValue))
            }
            set(value) {
                props.setProperty("headerBpmColor", getStringFromColor(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Прогрессометр - шрифт
        var progressFont: Font
            get() {
                val defaultValue = "name=Tahoma;style=0;size=30"
                props.loadFromXML(File(fileNameXml).inputStream())
                return getFontFromString(props.getProperty("progressFont", defaultValue))
            }
            set(value) {
                props.setProperty("progressFont", getStringFromFont(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Прогрессометр - Цвет
        var progressColor: Color
            get() {
                val defaultValue = "r=85;g=255;b=255;a=255"
                props.loadFromXML(File(fileNameXml).inputStream())
                return getColorFromString(props.getProperty("progressColor", defaultValue))
            }
            set(value) {
                props.setProperty("progressColor", getStringFromColor(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Прогрессометр - прозрачность (0.0 - полная прозрачность, 1.0 - полностью непрозрачный)
        var progressOpacity: Double
            get() {
                val defaultValue = "1.0"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("progressOpacity",defaultValue).toDouble()
            }
            set(value) {
                props.setProperty("progressOpacity", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Водяной знак - шрифт
        var watermarkFont: Font
            get() {
                val defaultValue = "name=Tahoma;style=0;size=10"
                props.loadFromXML(File(fileNameXml).inputStream())
                return getFontFromString(props.getProperty("watermarkFont", defaultValue))
            }
            set(value) {
                props.setProperty("watermarkFont", getStringFromFont(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Водяной знак - шрифт подчеркнутый (0 - нет, 1 - да)
        var watermarkFontUnderline: Long
            get() {
                val defaultValue = "0"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("watermarkFontUnderline",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("watermarkFontUnderline", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Водяной знак - Цвет
        var watermarkColor: Color
            get() {
                val defaultValue = "r=255;g=255;b=255;a=255"
                props.loadFromXML(File(fileNameXml).inputStream())
                return getColorFromString(props.getProperty("watermarkColor", defaultValue))
            }
            set(value) {
                props.setProperty("watermarkColor", getStringFromColor(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Водяной знак - прозрачность (0.0 - полная прозрачность, 1.0 - полностью непрозрачный)
        var watermarkOpacity: Double
            get() {
                val defaultValue = "0.5"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("watermarkOpacity",defaultValue).toDouble()
            }
            set(value) {
                props.setProperty("watermarkOpacity", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

    }
}