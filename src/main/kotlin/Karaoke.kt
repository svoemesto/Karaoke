import Converter.Companion.getFontFromString
import Converter.Companion.getStringFromFont
import java.awt.Font
import java.io.File
import java.util.*

class Karaoke {
    companion object {
        private val fileNameXml = "src/main/resources/settings.xml"
        private val props = Properties()

        var frameWidthPx: Long // Ширина экрана в пикселах
            get() {
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("frameWidthPx","1920").toLong()
            }
            set(value) {
                props.setProperty("frameWidthPx", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var frameHeightPx: Long // Высота экрана в пикселах
            get() {
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("frameHeightPx","1080").toLong()
            }
            set(value) {
                props.setProperty("frameHeightPx", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var frameFps: Double // Frames per seconds
            get() {
                props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("frameFps","60").toDouble()
            }
            set(value) {
                props.setProperty("frameFps", value.toString())
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // HEADER

        var headerSongnameFont: Font // Заголовок - Название песни - шрифт
            get() {
                props.loadFromXML(File(fileNameXml).inputStream())
                return getFontFromString(props.getProperty("headerSongnameFont", getStringFromFont(getFontFromString(""))))
            }
            set(value) {
                props.setProperty("headerSongnameFont", getStringFromFont(value))
                props.storeToXML(File(fileNameXml).outputStream(),null)
            }

    }
}