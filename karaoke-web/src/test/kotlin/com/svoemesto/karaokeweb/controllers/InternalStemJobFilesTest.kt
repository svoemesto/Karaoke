package com.svoemesto.karaokeweb.controllers

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.services.StemJobResultFiles
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.io.File
import java.nio.file.Files

/**
 * Регрессионные тесты server-to-server канала передачи приватных объектов задания
 * (спека 561, OpenProject #361, аудит BR-258).
 *
 * **До правки** таких эндпоинтов не существовало, а приватные объекты читались анонимно через
 * `/minio/karaoke/...`. **После правки** объект принимается только приложением со знанием
 * общего секрета `X-Internal-Secret` (fail-safe: пустой секрет закрывает канал), имя файла
 * проверяется по allow-list, отказ попадает в журнал `infra.stemjobs.security` без содержимого.
 *
 * @see specs/561-impl-stem-bucket/contracts/stemjob-private-files-contract.md
 */
class InternalStemJobFilesTest {
    private val secret = "test-internal-secret"
    private val jobId = 42L

    private lateinit var tempDir: File
    private lateinit var resultDir: File

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("stemjobs-temp").toFile()
        resultDir = File(tempDir, "results")
    }

    private fun controller(internalSecret: String = secret): InternalStemJobController =
        InternalStemJobController(
            storageService = Mockito.mock(KaraokeStorageService::class.java),
            storageApiClient = Mockito.mock(StorageApiClient::class.java),
            resultFiles = StemJobResultFiles(tempDir.path, resultDir.path),
            tempDir = tempDir.path,
            internalSecret = internalSecret,
        )

    private fun request(secretHeader: String? = null): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            remoteAddr = "10.0.0.5"
            addHeader("X-Real-IP", "203.0.113.7")
            secretHeader?.let { addHeader("X-Internal-Secret", it) }
        }

    private fun storedFile(name: String): File = File(File(resultDir, jobId.toString()), name)

    @Test
    fun `объект без секрета не принимается и не сохраняется`() {
        val response = MockHttpServletResponse()

        controller().putFile(jobId, "vocals.mp3", "audio".toByteArray(), request(), response)

        assertEquals(403, response.status)
        assertFalse(storedFile("vocals.mp3").exists(), "файл не должен сохраняться без секрета")
    }

    @Test
    fun `неверный секрет отклоняется`() {
        val response = MockHttpServletResponse()

        controller().putFile(jobId, "vocals.mp3", "audio".toByteArray(), request("wrong-secret"), response)

        assertEquals(403, response.status)
        assertFalse(storedFile("vocals.mp3").exists())
    }

    @Test
    fun `пустой настроенный секрет и пустой заголовок дают 403 (fail-safe)`() {
        val response = MockHttpServletResponse()

        controller(internalSecret = "").putFile(jobId, "vocals.mp3", "audio".toByteArray(), request(""), response)

        assertEquals(403, response.status)
        assertFalse(storedFile("vocals.mp3").exists())
    }

    @Test
    fun `с корректным секретом объект сохраняется в приватный каталог`() {
        val response = MockHttpServletResponse()
        val bytes = "stem-content".toByteArray()

        controller().putFile(jobId, "vocals.mp3", bytes, request(secret), response)

        assertEquals(200, response.status)
        val stored = storedFile("vocals.mp3")
        assertTrue(stored.exists(), "объект должен быть сохранён в приватном каталоге результатов")
        assertArrayEquals(bytes, stored.readBytes())
        assertTrue(
            stored.absolutePath.startsWith(resultDir.absolutePath),
            "файл должен лежать внутри приватного каталога результатов: ${stored.absolutePath}",
        )
    }

    @Test
    fun `недопустимое имя файла отклоняется`() {
        val response = MockHttpServletResponse()

        controller().putFile(jobId, "../escape.mp3", "audio".toByteArray(), request(secret), response)

        assertEquals(400, response.status)
        assertFalse(File(resultDir.parentFile, "escape.mp3").exists())
    }

    @Test
    fun `purge удаляет локальные копии задания`() {
        controller().putFile(jobId, "vocals.mp3", "audio".toByteArray(), request(secret), MockHttpServletResponse())
        assertTrue(storedFile("vocals.mp3").exists())

        val response = MockHttpServletResponse()
        controller().purge(jobId, request(secret), response)

        assertEquals(200, response.status)
        assertFalse(File(resultDir, jobId.toString()).exists(), "каталог задания должен быть удалён")
    }

    @Test
    fun `purge без секрета отклоняется`() {
        controller().putFile(jobId, "vocals.mp3", "audio".toByteArray(), request(secret), MockHttpServletResponse())

        val response = MockHttpServletResponse()
        controller().purge(jobId, request(), response)

        assertEquals(403, response.status)
        assertTrue(storedFile("vocals.mp3").exists(), "без секрета копии не удаляются")
    }

    @Test
    fun `отказ попадает в журнал безопасности без содержимого объекта`() {
        val appender = CapturingAppender()
        appender.start()
        val logger = LoggerFactory.getLogger("infra.stemjobs.security") as ch.qos.logback.classic.Logger
        val previousLevel = logger.level
        logger.level = Level.WARN
        logger.addAppender(appender)
        try {
            controller().putFile(
                jobId,
                "vocals.mp3",
                "audio".toByteArray(),
                request(),
                MockHttpServletResponse(),
            )
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
            appender.stop()
        }

        val denial = appender.messages.firstOrNull { it.contains("stemjobs:security:denied") }
        assertNotNull(denial, "в журнале категории infra.stemjobs.security должна быть запись об отказе")
        assertTrue(denial!!.contains("missing_secret"), "в записи должна быть причина отказа: $denial")
        assertTrue(denial.contains("203.0.113.7"), "в записи должен быть адрес источника: $denial")
        assertTrue(denial.contains("vocals.mp3"), "в записи должно быть имя объекта: $denial")
        assertFalse(denial.contains("audio"), "содержимое объекта в журнал не пишется: $denial")
    }

    /** Собирает отформатированные сообщения журнала категории `infra.stemjobs.security`. */
    private class CapturingAppender : AppenderBase<ILoggingEvent>() {
        val messages = mutableListOf<String>()

        override fun append(event: ILoggingEvent) {
            messages.add(event.formattedMessage)
        }
    }
}
