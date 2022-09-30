import Converter.Companion.getColorFromString
import Converter.Companion.getFontFromString
import Converter.Companion.getStringFromColor
import Converter.Companion.getStringFromFont
import Converter.Companion.getStringFromVoices
import Converter.Companion.getVoicesFromString
import java.awt.Color
import java.awt.Font
import java.io.File
import java.util.*

class Karaoke {

    data class KaraokeVoice (
        val groups: MutableList<KaraokeVoiceGroup>
    )
    data class KaraokeVoiceGroup(
        var songtextTextFont: Font,
        val songtextTextColor: Color,
        var songtextBeatFont: Font,
        val songtextBeatColor: Color
    )
    companion object {
        private val fileNameXml = "src/main/resources/settings.xml"
        private val props = Properties()
        var voices: MutableList<KaraokeVoice>
            get() {
                val defaultValue = """songtextTextFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextTextColor|[NAME]|r=255;g=255;b=255;a=255
|[FIELD]|
songtextBeatFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextBeatColor|[NAME]|r=155;g=255;b=255;a=255
|[GROUP]|
songtextTextFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextTextColor|[NAME]|r=255;g=255;b=155;a=255
|[FIELD]|
songtextBeatFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextBeatColor|[NAME]|r=105;g=255;b=105;a=255
|[GROUP]|
songtextTextFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextTextColor|[NAME]|r=85;g=255;b=155;a=255
|[FIELD]|
songtextBeatFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextBeatColor|[NAME]|r=105;g=255;b=105;a=255
|[VOICE]|
songtextTextFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextTextColor|[NAME]|r=255;g=255;b=255;a=255
|[FIELD]|
songtextBeatFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextBeatColor|[NAME]|r=155;g=255;b=255;a=255
|[GROUP]|
songtextTextFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextTextColor|[NAME]|r=255;g=255;b=155;a=255
|[FIELD]|
songtextBeatFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextBeatColor|[NAME]|r=105;g=255;b=105;a=255
|[GROUP]|
songtextTextFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextTextColor|[NAME]|r=85;g=255;b=155;a=255
|[FIELD]|
songtextBeatFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextBeatColor|[NAME]|r=105;g=255;b=105;a=255
|[VOICE]|
songtextTextFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextTextColor|[NAME]|r=255;g=255;b=255;a=255
|[FIELD]|
songtextBeatFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextBeatColor|[NAME]|r=155;g=255;b=255;a=255
|[GROUP]|
songtextTextFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextTextColor|[NAME]|r=255;g=255;b=155;a=255
|[FIELD]|
songtextBeatFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextBeatColor|[NAME]|r=105;g=255;b=105;a=255
|[GROUP]|
songtextTextFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextTextColor|[NAME]|r=85;g=255;b=155;a=255
|[FIELD]|
songtextBeatFont|[NAME]|name=Tahoma;style=0;size=80
|[FIELD]|
songtextBeatColor|[NAME]|r=105;g=255;b=105;a=255""".trimIndent()
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

        // Заголовок - Название песни - шрифт
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

        // Заголовок - Название песни - цвет
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

    }
}