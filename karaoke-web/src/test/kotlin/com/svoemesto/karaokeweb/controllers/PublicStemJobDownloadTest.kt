package com.svoemesto.karaokeweb.controllers

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.StemJob
import com.svoemesto.karaokeapp.model.StemJobMode
import com.svoemesto.karaokeapp.model.StemJobStatus
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.config.SiteAuthInterceptor
import com.svoemesto.karaokeweb.services.StemJobLoader
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
import java.sql.Timestamp

/**
 * Регрессионные тесты «до/после» отдачи приватного файла задания владельцу
 * (спека 561, OpenProject #361, аудит BR-258).
 *
 * **До правки** файл читался анонимным GET через nginx-прокси `/minio/karaoke/<key>` — то есть
 * лежал в ПУБЛИЧНОМ бакете и был доступен любому анонимному клиенту по угадываемому URL.
 * **После правки** файл лежит в приватном каталоге результатов, а отдаётся только владельцу
 * задания: чужим и истёкшим — 404 (факт существования не раскрывается), неизвестный стем — 400.
 *
 * @see specs/561-impl-stem-bucket/data-model.md
 */
class PublicStemJobDownloadTest {
    private val ownerId = 7L
    private val jobId = 42L

    private lateinit var tempDir: File
    private lateinit var resultDir: File

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("stemjobs-temp").toFile()
        resultDir = File(tempDir, "results")
    }

    private fun job(
        status: String = StemJobStatus.DONE,
        expiresInMs: Long = 3_600_000L,
        siteUserId: Long = ownerId,
    ): StemJob {
        val job =
            StemJob(
                database = Mockito.mock(KaraokeConnection::class.java),
                storageService = Mockito.mock(KaraokeStorageService::class.java),
                storageApiClient = Mockito.mock(StorageApiClient::class.java),
            )
        job.id = jobId
        job.siteUserId = siteUserId
        job.mode = StemJobMode.DEMUCS2
        job.status = status
        job.originalExt = "mp3"
        job.originalFileName = "my song.mp3"
        job.expiresAt = Timestamp(System.currentTimeMillis() + expiresInMs)
        return job
    }

    private fun controller(job: StemJob?): PublicStemJobController =
        PublicStemJobController(
            storageService = Mockito.mock(KaraokeStorageService::class.java),
            storageApiClient = Mockito.mock(StorageApiClient::class.java),
            resultFiles = StemJobResultFiles(tempDir.path, resultDir.path),
            jobLoader = StemJobLoader { if (job?.id == jobId) job else null },
            tempDir = tempDir.path,
        )

    private fun request(userId: Long = ownerId): MockHttpServletRequest {
        val user =
            SiteUser(
                database = Mockito.mock(KaraokeConnection::class.java),
                storageService = Mockito.mock(KaraokeStorageService::class.java),
                storageApiClient = Mockito.mock(StorageApiClient::class.java),
            )
        user.id = userId
        return MockHttpServletRequest().apply {
            remoteAddr = "10.0.0.5"
            addHeader("X-Real-IP", "203.0.113.7")
            setAttribute(SiteAuthInterceptor.SITE_USER_ATTR, user)
        }
    }

    private fun storeResult(name: String, content: String): File {
        val dir = File(resultDir, jobId.toString())
        dir.mkdirs()
        val file = File(dir, name)
        file.writeBytes(content.toByteArray())
        return file
    }

    @Test
    fun `владелец задания получает файл и прежние заголовки`() {
        storeResult("vocals.mp3", "stem-content")
        val response = MockHttpServletResponse()

        controller(job()).download(jobId, "vocals", request(), response)

        assertEquals(200, response.status)
        assertArrayEquals("stem-content".toByteArray(), response.contentAsByteArray)
        assertEquals("audio/mpeg", response.contentType)
        assertTrue(
            response.getHeader("Content-Disposition")!!.contains("vocals.mp3"),
            "заголовок Content-Disposition прежнего формата: ${response.getHeader("Content-Disposition")}",
        )
    }

    @Test
    fun `чужое задание отдаёт 404 и не раскрывает факт существования`() {
        storeResult("vocals.mp3", "stem-content")
        val response = MockHttpServletResponse()

        controller(job(siteUserId = ownerId + 1)).download(jobId, "vocals", request(), response)

        assertEquals(404, response.status)
        assertEquals(0, response.contentAsByteArray.size, "содержимое чужого объекта отдавать нельзя")
    }

    @Test
    fun `истёкшее задание отдаёт 404`() {
        storeResult("vocals.mp3", "stem-content")
        val response = MockHttpServletResponse()

        controller(job(expiresInMs = -1000L)).download(jobId, "vocals", request(), response)

        assertEquals(404, response.status)
        assertEquals(0, response.contentAsByteArray.size)
    }

    @Test
    fun `неизвестный стем отдаёт 400`() {
        val response = MockHttpServletResponse()

        controller(job()).download(jobId, "drums", request(), response)

        assertEquals(400, response.status)
    }

    @Test
    fun `отсутствующий файл отдаёт 404 а не содержимое из публичного бакета`() {
        val response = MockHttpServletResponse()

        controller(job()).download(jobId, "vocals", request(), response)

        assertEquals(404, response.status)
        assertEquals(0, response.contentAsByteArray.size)
    }

    @Test
    fun `отказ в отдаче попадает в журнал безопасности без содержимого объекта`() {
        val appender = CapturingAppender()
        appender.start()
        val logger = LoggerFactory.getLogger("infra.stemjobs.security") as ch.qos.logback.classic.Logger
        val previousLevel = logger.level
        logger.level = Level.WARN
        logger.addAppender(appender)
        try {
            storeResult("vocals.mp3", "stem-content")
            controller(job(siteUserId = ownerId + 1)).download(jobId, "vocals", request(), response = MockHttpServletResponse())
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
            appender.stop()
        }

        val denial = appender.messages.firstOrNull { it.contains("stemjobs:security:download-denied") }
        assertNotNull(denial, "в журнале категории infra.stemjobs.security должна быть запись об отказе")
        assertTrue(denial!!.contains("203.0.113.7"), "в записи должен быть адрес источника: $denial")
        assertTrue(denial.contains("vocals"), "в записи должно быть имя объекта: $denial")
        assertFalse(denial.contains("stem-content"), "содержимое объекта в журнал не пишется: $denial")
    }

    /** Собирает отформатированные сообщения журнала категории `infra.stemjobs.security`. */
    private class CapturingAppender : AppenderBase<ILoggingEvent>() {
        val messages = mutableListOf<String>()

        override fun append(event: ILoggingEvent) {
            messages.add(event.formattedMessage)
        }
    }
}
