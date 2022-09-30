import Converter.Companion.getColorFromString
import Converter.Companion.getFontFromString
import Converter.Companion.getStringFromColor
import Converter.Companion.getStringFromFont
import java.awt.Color
import java.awt.Font
import java.io.File
import java.util.*

class Karaoke {
    companion object {
        private val fileNameXml = "src/main/resources/settings.xml"
        private val props = Properties()

        // Ширина экрана в пикселах
        var frameWidthPx: Long
            get() {
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("frameWidthPx","1920").toLong()
            }
            set(value) {
                props.setProperty("frameWidthPx", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Высота экрана в пикселах
        var frameHeightPx: Long
            get() {
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("frameHeightPx","1080").toLong()
            }
            set(value) {
                props.setProperty("frameHeightPx", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Frames per seconds
        var frameFps: Double
            get() {
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("frameFps","60").toDouble()
            }
            set(value) {
                props.setProperty("frameFps", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // HEADER

        // Заголовок - Название песни - шрифт
        var headerSongnameFont: Font
            get() {
                props.loadFromXML(File(fileNameXml).inputStream())
                return getFontFromString(props.getProperty("headerSongnameFont", "name=Tahoma;style=0;size=80"))
            }
            set(value) {
                props.setProperty("headerSongnameFont", getStringFromFont(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Название песни - шрифт
        var headerSongnameColor: Color
            get() {
                props.loadFromXML(File(fileNameXml).inputStream())
                return getColorFromString(props.getProperty("headerSongnameColor", "r=255;g=255;b=127;a=255"))
            }
            set(value) {
                props.setProperty("headerSongnameColor", getStringFromColor(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Название песни - шрифт
        var headerAuthorFont: Font
            get() {
                props.loadFromXML(File(fileNameXml).inputStream())
                return getFontFromString(props.getProperty("headerAuthorFont", "name=Tahoma;style=0;size=30"))
            }
            set(value) {
                props.setProperty("headerAuthorFont", getStringFromFont(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Название песни - цвет
        var headerAuthorColor: Color
            get() {
                props.loadFromXML(File(fileNameXml).inputStream())
                return getColorFromString(props.getProperty("headerAuthorColor", "r=85;g=255;b=255;a=255"))
            }
            set(value) {
                props.setProperty("headerAuthorColor", getStringFromColor(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Название альбома - шрифт
        var headerAlbumFont: Font
            get() {
                props.loadFromXML(File(fileNameXml).inputStream())
                return getFontFromString(props.getProperty("headerAlbumFont", "name=Tahoma;style=0;size=30"))
            }
            set(value) {
                props.setProperty("headerAlbumFont", getStringFromFont(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Название альбома - цвет
        var headerAlbumColor: Color
            get() {
                props.loadFromXML(File(fileNameXml).inputStream())
                return getColorFromString(props.getProperty("headerAlbumColor", "r=85;g=255;b=255;a=255"))
            }
            set(value) {
                props.setProperty("headerAuthorColor", getStringFromColor(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Тональность - шрифт
        var headerToneFont: Font
            get() {
                props.loadFromXML(File(fileNameXml).inputStream())
                return getFontFromString(props.getProperty("headerToneFont", "name=Tahoma;style=0;size=30"))
            }
            set(value) {
                props.setProperty("headerAlbumFont", getStringFromFont(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Тональность - цвет
        var headerToneColor: Color
            get() {
                props.loadFromXML(File(fileNameXml).inputStream())
                return getColorFromString(props.getProperty("headerToneColor", "r=85;g=255;b=255;a=255"))
            }
            set(value) {
                props.setProperty("headerToneColor", getStringFromColor(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Темп - шрифт
        var headerBpmFont: Font
            get() {
                props.loadFromXML(File(fileNameXml).inputStream())
                return getFontFromString(props.getProperty("headerBpmFont", "name=Tahoma;style=0;size=30"))
            }
            set(value) {
                props.setProperty("headerBpmFont", getStringFromFont(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Темп - цвет
        var headerBpmColor: Color
            get() {
                props.loadFromXML(File(fileNameXml).inputStream())
                return getColorFromString(props.getProperty("headerBpmColor", "r=85;g=255;b=255;a=255"))
            }
            set(value) {
                props.setProperty("headerBpmColor", getStringFromColor(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }
    }
}