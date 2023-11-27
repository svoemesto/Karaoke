package com.svoemesto.karaokeapp

import org.junit.jupiter.api.*
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FirstScriptTest {
    private lateinit var driver: WebDriver

    @BeforeAll
    fun setupAll() {
        System.setProperty(WEBDRIVER_CHROMEDRIVER, PATH_TO_CHROMEDRIVER)
    }

    @BeforeEach
    fun setup() {
        val options = ChromeOptions()
        options.addArguments("--remote-allow-origins=*")
        driver = ChromeDriver(options)
    }

    @AfterEach
    fun teardown() {
        driver.quit()
    }

    @Test
    fun eightComponents() {
        driver.get("https://www.selenium.dev/selenium/web/web-form.html")

        val title = driver.title
        Assertions.assertEquals("Web form", title)

        driver.manage().timeouts().implicitlyWait(500, TimeUnit.MILLISECONDS)

        var textBox = driver.findElement(By.name("my-text"))
        val submitButton = driver.findElement(By.cssSelector("button"))

        textBox.sendKeys("Selenium")
        submitButton.click()

        val message = driver.findElement(By.id("message"))
        val value = message.getText()
        Assertions.assertEquals("Received!", value)
    }

}