package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.AlbumCoverService
import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.llm.LyricsFinderService
import com.svoemesto.karaokeapp.services.AutoOneClickSyncScheduler
import com.svoemesto.karaokeapp.services.HealthReportBatchPool
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SNS
import com.svoemesto.karaokeapp.services.SseNotificationService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeapp.services.StorageMetadataCache
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Регрессионный тест «до/после» для BR-060 (аудит #336, OpenProject #368):
 * фоновые задачи API-контроллеров запускались сырым `thread { … }` и НЕ закрывали
 * thread-local соединение потока.
 *
 * **До правки** поток завершался, а физическое соединение оставалось в его
 * `ThreadLocal` до недетерминированной финализации сборщиком мусора: каждый клик по
 * кнопке админки оставлял открытый канал к PostgreSQL (при `max_connections` — отказ БД
 * для всего приложения).
 *
 * **После правки** фоновый поток идёт через `startOneShotThread`, который закрывает
 * соединение потока в `finally` при любом исходе.
 *
 * @see com.svoemesto.karaokeapp.services.startOneShotThread
 */
class ApiControllerOneShotThreadTest {
    /**
     * Контроллер с подменёнными зависимостями: тесту нужна только фоновой потоковая
     * задача, реальная БД/хранилище/SSE не участвуют.
     */
    private fun controller(): ApiController =
        ApiController(
            sseNotificationService = Mockito.mock(SseNotificationService::class.java),
            storageService = Mockito.mock(KaraokeStorageService::class.java),
            storageApiClient = Mockito.mock(StorageApiClient::class.java),
            lyricsFinderService = Mockito.mock(LyricsFinderService::class.java),
            albumCoverService = Mockito.mock(AlbumCoverService::class.java),
            autoOneClickSyncScheduler = Mockito.mock(AutoOneClickSyncScheduler::class.java),
            healthReportBatchPool = Mockito.mock(HealthReportBatchPool::class.java),
            storageMetadataCache = Mockito.mock(StorageMetadataCache::class.java),
        )

    /** Поддельное JDBC-соединение: SELECT отдаёт пустой результат. */
    private fun emptyJdbcConnection(): Connection {
        val jdbc = Mockito.mock(Connection::class.java)
        val statement = Mockito.mock(PreparedStatement::class.java)
        val resultSet = Mockito.mock(ResultSet::class.java)
        Mockito.`when`(jdbc.prepareStatement(Mockito.anyString())).thenReturn(statement)
        Mockito.`when`(statement.executeQuery()).thenReturn(resultSet)
        Mockito.`when`(resultSet.next()).thenReturn(false)
        return jdbc
    }

    @BeforeEach
    fun initSse() {
        // SSE-глобал приложения: в юнит-тесте его нет, а фоновая задача шлёт итог в SSE.
        // Обращение к неинициализированному `lateinit` бросает исключение — этим и
        // проверяем состояние глобала (не подменяем уже настроенный).
        if (runCatching { SNS }.isFailure) {
            SNS = Mockito.mock(SseNotificationService::class.java)
        }
    }

    @Test
    fun `фоновая задача контроллера закрывает thread-local соединение потока`() {
        val db = FakeKaraokeConnection(emptyJdbcConnection())

        controller().autoAssignOriginalAll(null, 95, db)

        val closed = awaitCondition { db.closeCalls.get() > 0 }
        assertTrue(
            closed,
            "соединение одноразового фонового потока должно быть закрыто " +
                "(иначе утечка канала к БД после каждого клика по кнопке)",
        )
    }

    /**
     * Ждёт выполнения условия (не более [timeoutMs]).
     *
     * @return `true`, если условие выполнилось.
     */
    private fun awaitCondition(
        timeoutMs: Long = 15_000,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }
}
