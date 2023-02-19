import SeleniumSettings.Companion.urlYoutubeStudio
import org.openqa.selenium.chrome.ChromeDriver
import java.io.File
import java.util.*

const val PATH_TO_SELENIUM_SETTINGS = "/home/nsa/Documents/Караоке/SeleniumProperties.xml"
//const val PATH_TO_SELENIUM_SETTINGS = "/home/nsa/Documents/Караоке/SeleniumProperties.txt"
const val WEBDRIVER_CHROMEDRIVER = "webdriver.chrome.driver"
const val PATH_TO_CHROMEDRIVER = "/usr/local/bin/chromedriver"

fun main() {
//    System.setProperty(WEBDRIVER_CHROMEDRIVER, PATH_TO_CHROMEDRIVER)
//    val driver = ChromeDriver()
//    driver.get(urlYoutubeStudio)

    SeleniumSettings.urlYoutubeStudio = "https://studio.youtube.com/234"
//    SeleniumSettings.login = "https://studio.youtube.com/"
    println(SeleniumSettings.login)
    println(SeleniumSettings.password)

}

class SeleniumSettings {
    companion object {
        private val fileNameXml = PATH_TO_SELENIUM_SETTINGS
        private val props = Properties()

        fun getProp(name: String, defaultValue: String): String {
            props.loadFromXML(File(fileNameXml).inputStream())
            return if (props.stringPropertyNames().contains(name)) {
                props.getProperty(name)
            } else {
                setProp(name,defaultValue)
                defaultValue
            }
        }

        fun setProp(name: String, value: String) {
            props.loadFromXML(File(fileNameXml).inputStream())
            if (props.getProperty(name) != value) {
                props.stringPropertyNames().forEach{
                    props.setProperty(it, props.getProperty(it))
                }
                props.setProperty(name, value)
                props.storeToXML(File(fileNameXml).outputStream(), null)
            }
        }

        var urlYoutubeStudio: String
            get() = getProp("urlYoutubeStudio", "https://studio.youtube.com/")
            set(value) = setProp("urlYoutubeStudio",value)

        var login: String
            get() = getProp("login", "nsa")
            set(value) = setProp("login",value)

        var password: String
            get() = getProp("password", "password")
            set(value) = setProp("password",value)

    }
}

