import Converter.Companion.getColorFromString
import Converter.Companion.getColorsFromString
import Converter.Companion.getFontFromString
import Converter.Companion.getMltFontFromString
import Converter.Companion.getStringFromVoices
import Converter.Companion.getVoicesFromString
import mlt.MltFont
import mlt.setting
import java.awt.Color
import java.awt.Font
import java.io.File
import java.util.*


class Karaoke {
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

        // Создавать фэйдер
        var createFader: Boolean
            get() {
                val defaultValue = "true"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createFader",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createFader", value.toString())
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
                val defaultValue = "false"
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
                val defaultValue = "false"
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
        var countersColors: MutableList<Color>
            get() {
                val defaultValue = listOf(
                    Color(0,255,0,255),
                    Color(255,255,0,255),
                    Color(255,255,0,255),
                    Color(255,0,0,255),
                    Color(255,0,0,255)
                ).setting()
                props.loadFromXML(File(fileNameXml).inputStream())
                return getColorsFromString(props.getProperty("countersColors", defaultValue))
            }
            set(value) {
                props.setProperty("countersColors", value.setting())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Цвета горизонта для групп
        var horizonColors: MutableList<Color>
            get() {
                val defaultValue = listOf(
                    Color(255,255,255,255),
                    Color(255,255,0,255),
                    Color(85,255,255,255)
                ).setting()
                props.loadFromXML(File(fileNameXml).inputStream())
                return getColorsFromString(props.getProperty("horizonColors", defaultValue))
            }
            set(value) {
                props.setProperty("horizonColors", value.setting())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Цвет горизонта
        var horizonColor: Color
            get() {
                val defaultValue = Color(0,255,0,255).setting()
                props.loadFromXML(File(fileNameXml).inputStream())
                return getColorFromString(props.getProperty("horizonColor", defaultValue))
            }
            set(value) {
                props.setProperty("horizonColor", value.setting())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Настройки текста для голосов - групп
        var voices: MutableList<KaraokeVoice>
            get() {
                val defaultValue = """songtextTextMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",0,80), fontColor = Color(255,255,255,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[FIELD]|
songtextBeatMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",0,80), fontColor = Color(155,255,155,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[GROUP]|
songtextTextMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",2,80), fontColor = Color(255,255,155,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[FIELD]|
songtextBeatMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",2,80), fontColor = Color(105,255,105,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[GROUP]|
songtextTextMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",0,80), fontColor = Color(155,255,255,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[FIELD]|
songtextBeatMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",0,80), fontColor = Color(105,255,105,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[GROUP]|
songtextTextMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",2,80), fontColor = Color(255,255,255,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[FIELD]|
songtextBeatMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",2,80), fontColor = Color(155,255,155,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[VOICEFIELDS]|
evenColor|[NAME]|${Color(255,128,0,255).setting()}
|[FIELD]|
evenOpacity|[NAME]|0.6
|[FIELD]|
oddColor|[NAME]|${Color(255,128,0,255).setting()}
|[FIELD]|
oddOpacity|[NAME]|0.6
|[VOICE]|
songtextTextMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",0,80), fontColor = Color(255,255,255,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[FIELD]|
songtextBeatMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",0,80), fontColor = Color(155,255,155,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[GROUP]|
songtextTextMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",2,80), fontColor = Color(255,255,155,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[FIELD]|
songtextBeatMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",2,80), fontColor = Color(105,255,105,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[GROUP]|
songtextTextMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",0,80), fontColor = Color(155,255,255,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[FIELD]|
songtextBeatMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",0,80), fontColor = Color(105,255,105,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[GROUP]|
songtextTextMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",2,80), fontColor = Color(255,255,255,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[FIELD]|
songtextBeatMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",2,80), fontColor = Color(155,255,155,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[VOICEFIELDS]|
evenColor|[NAME]|${Color(255,128,0,255).setting()}
|[FIELD]|
evenOpacity|[NAME]|0.6
|[FIELD]|
oddColor|[NAME]|${Color(255,128,0,255).setting()}
|[FIELD]|
oddOpacity|[NAME]|0.6
|[VOICE]|
songtextTextMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",0,80), fontColor = Color(255,255,255,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[FIELD]|
songtextBeatMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",0,80), fontColor = Color(155,255,155,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[GROUP]|
songtextTextMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",2,80), fontColor = Color(255,255,155,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[FIELD]|
songtextBeatMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",2,80), fontColor = Color(105,255,105,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[GROUP]|
songtextTextMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",0,80), fontColor = Color(155,255,255,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[FIELD]|
songtextBeatMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",0,80), fontColor = Color(105,255,105,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[GROUP]|
songtextTextMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",2,80), fontColor = Color(255,255,255,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[FIELD]|
songtextBeatMltFont|[NAME]|${MltFont(font = Font("Montserrat SemiBold",2,80), fontColor = Color(155,255,155,255), fontOutlineColor = Color(0,0,0,255) , fontOutline = 1, fontUnderline = 0).setting()}
|[VOICEFIELDS]|
evenColor|[NAME]|${Color(255,128,0,255).setting()}
|[FIELD]|
evenOpacity|[NAME]|0.6
|[FIELD]|
oddColor|[NAME]|${Color(255,128,0,255).setting()}
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
        var headerSongnameFont: MltFont
            get() {
                val defaultValue = MltFont(
                    font = Font("Montserrat SemiBold", 0, 80),
                    fontColor = Color(255,255,127,255),
                    fontOutlineColor = Color(0,0,0,255),
                    fontOutline = 0,
                    fontUnderline = 0
                ).setting()
                props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("headerSongnameFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerSongnameFont", value.setting())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Максимальная позиция по X до которой должна быть надпись названия песни, чтобы не перекрывать логотип
        var headerSongnameMaxX: Long
            get() {
                val defaultValue = "1200"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("headerSongnameMaxX",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("headerSongnameMaxX", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        // Заголовок - Автор - шрифт
        var headerAuthorFont: MltFont
            get() {
                val defaultValue = MltFont(
                    font = Font("Montserrat SemiBold", 0, 30),
                    fontColor = Color(255,255,127,255),
                    fontOutlineColor = Color(0,0,0,255),
                    fontOutline = 0,
                    fontUnderline = 0
                ).setting()
                props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("headerAuthorFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerAuthorFont", value.setting())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var headerAuthorNameFont: MltFont
            get() {
                val defaultValue = MltFont(
                    font = Font("Montserrat SemiBold", 0, 30),
                    fontColor = Color(85,255,255,255),
                    fontOutlineColor = Color(0,0,0,255),
                    fontOutline = 0,
                    fontUnderline = 0
                ).setting()
                props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("headerAuthorNameFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerAuthorNameFont", value.setting())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var headerAuthorName: String
            get() {
                val defaultValue = "Исполнитель: "
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("headerAuthorName",defaultValue)
            }
            set(value) {
                props.setProperty("headerAuthorName", value)
                props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        // Заголовок - Название альбома - шрифт
        var headerAlbumFont: MltFont
            get() {
                val defaultValue = MltFont(
                    font = Font("Montserrat SemiBold", 0, 30),
                    fontColor = Color(255,255,127,255),
                    fontOutlineColor = Color(0,0,0,255),
                    fontOutline = 0,
                    fontUnderline = 0
                ).setting()
                props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("headerAlbumFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerAlbumFont", value.setting())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var headerAlbumNameFont: MltFont
            get() {
                val defaultValue = MltFont(
                    font = Font("Montserrat SemiBold", 0, 30),
                    fontColor = Color(85,255,255,255),
                    fontOutlineColor = Color(0,0,0,255),
                    fontOutline = 0,
                    fontUnderline = 0
                ).setting()
                props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("headerAlbumNameFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerAlbumNameFont", value.setting())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var headerAlbumName: String
            get() {
                val defaultValue = "Альбом: "
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("headerAlbumName",defaultValue)
            }
            set(value) {
                props.setProperty("headerAlbumName", value)
                props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        // Заголовок - Тональность - шрифт
        var headerToneFont: MltFont
            get() {
                val defaultValue = MltFont(
                    font = Font("Montserrat SemiBold", 0, 30),
                    fontColor = Color(255,255,127,255),
                    fontOutlineColor = Color(0,0,0,255),
                    fontOutline = 0,
                    fontUnderline = 0
                ).setting()
                props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("headerToneFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerAlbumFont", value.setting())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var headerToneNameFont: MltFont
            get() {
                val defaultValue = MltFont(
                    font = Font("Montserrat SemiBold", 0, 30),
                    fontColor = Color(85,255,255,255),
                    fontOutlineColor = Color(0,0,0,255),
                    fontOutline = 0,
                    fontUnderline = 0
                ).setting()
                props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("headerToneNameFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerToneNameFont", value.setting())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var headerToneName: String
            get() {
                val defaultValue = "Тональность: "
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("headerToneName",defaultValue)
            }
            set(value) {
                props.setProperty("headerToneName", value)
                props.storeToXML(File(fileNameXml).outputStream(), null)
            }


        // Заголовок - Темп - шрифт
        var headerBpmFont: MltFont
            get() {
                val defaultValue = MltFont(
                    font = Font("Montserrat SemiBold", 0, 30),
                    fontColor = Color(255,255,127,255),
                    fontOutlineColor = Color(0,0,0,255),
                    fontOutline = 0,
                    fontUnderline = 0
                ).setting()
                props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("headerBpmFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerBpmFont", value.setting())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var headerBpmNameFont: MltFont
            get() {
                val defaultValue = MltFont(
                    font = Font("Montserrat SemiBold", 0, 30),
                    fontColor = Color(85,255,255,255),
                    fontOutlineColor = Color(0,0,0,255),
                    fontOutline = 0,
                    fontUnderline = 0
                ).setting()
                props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("headerBpmNameFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerBpmNameFont", value.setting())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var headerBpmName: String
            get() {
                val defaultValue = "Темп: "
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("headerBpmName",defaultValue)
            }
            set(value) {
                props.setProperty("headerBpmName", value)
                props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        // Прогрессометр - шрифт
        var progressFont: MltFont
            get() {
                val defaultValue = MltFont(
                    font = Font("Tahoma", 0, 20),
                    fontColor = Color(255,255,255,255),
                    fontOutlineColor = Color(0,0,0,255),
                    fontOutline = 0,
                    fontUnderline = 0
                ).setting()
                props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("progressFont", defaultValue))
            }
            set(value) {
                props.setProperty("progressFont", value.setting())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var progressSymbol: String
            get() {
                val defaultValue = "▲"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("progressSymbol",defaultValue)
            }
            set(value) {
                props.setProperty("progressSymbol", value)
                props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        // Водяной знак - шрифт
        var watermarkFont: MltFont
            get() {
                val defaultValue = MltFont(
                    font = Font("Montserrat SemiBold", 0, 10),
                    fontColor = Color(255,255,255,127),
                    fontOutlineColor = Color(0,0,0,255),
                    fontOutline = 0,
                    fontUnderline = 0
                    ).setting()
                props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("watermarkFont", defaultValue))
            }
            set(value) {
                props.setProperty("watermarkFont", value.setting())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Текст водяного знака
        var watermarkText: String
            get() {
                val defaultValue = "https://github.com/svoemesto/Karaoke"
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("watermarkText",defaultValue)
            }
            set(value) {
                props.setProperty("watermarkText", value)
                props.storeToXML(File(fileNameXml).outputStream(), null)
            }
    }
}