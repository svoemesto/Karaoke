package com.svoemesto.karaokeapp

import java.io.File
import java.util.*
import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.util.concurrent.TimeUnit

const val PATH_TO_SELENIUM_SETTINGS = "/sm-karaoke/system/SeleniumProperties.xml"
//const val PATH_TO_SELENIUM_SETTINGS = "/sm-karaoke/system/SeleniumProperties.txt"
const val WEBDRIVER_CHROMEDRIVER = "webdriver.chrome.driver"
const val PATH_TO_CHROMEDRIVER = "/usr/local/bin/chromedriver"

fun mainSeleniumUtils() {
    System.setProperty(WEBDRIVER_CHROMEDRIVER, PATH_TO_CHROMEDRIVER)
    val options = ChromeOptions()
    options.addArguments("--remote-allow-origins=*")
    val driver = ChromeDriver(options)
    driver.get(SeleniumSettings.urlBoosty)

    driver.manage().timeouts().implicitlyWait(500, TimeUnit.MILLISECONDS)


    try {
        val element = driver.findElement(By.xpath("//button[contains(@class,'ContainedButton_colorDefault_fJta6')]"))
        println("Кнопка найдена")
        element.click()
        driver.manage().timeouts().implicitlyWait(1000, TimeUnit.MILLISECONDS)
    } catch (e: Exception) {
        println("Кнопка не найдена")
    }


    driver.quit()


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

        var urlBoosty: String
            get() = getProp("urlBoosty", "https://boosty.to/svoemesto")
            set(value) = setProp("urlBoosty",value)

        var loginBoosty: String
            get() = getProp("loginBoosty", "nsa")
            set(value) = setProp("loginBoosty",value)

        var passwordBoosty: String
            get() = getProp("passwordBoosty", "password")
            set(value) = setProp("passwordBoosty",value)

    }
}

