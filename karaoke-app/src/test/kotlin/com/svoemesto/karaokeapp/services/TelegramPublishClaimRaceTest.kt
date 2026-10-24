package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.model.Song
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Регрессия на гонку публикации (BR-062, BR-138; аудит #336, пакет `PKG-RACE-PUBLISH`).
 *
 * **Что воспроизводим.** Два участника — HTTP-поток ручного endpoint'а
 * `/api/song/publishToTelegramNow` и поток `TelegramAutoPublishScheduler` — вызывают
 * `publishToTelegram` для одной песни. До правки проверка «уже опубликовано/уже публикуется»
 * читала загруженный в память объект, поэтому оба проходили её и оба вызывали `sendVideo`
 * (два поста в канале).
 *
 * **Шов.** [TelegramAutoPublishService.acquirePublishClaim] — production-функция, которую
 * `publishFile` вызывает перед `sendVideo`. Её зависимость — [TelegramPublishClaimStore];
 * тест подменяет хранилище на in-memory реализацию с тем же контрактом и настоящей
 * взаимоисключающей секцией (per-song lock), какую в проде даёт `SELECT … FOR NO KEY UPDATE`.
 * Решение принимает production-политика [TelegramPublishClaimPolicy].
 *
 * **До/после.** Тест `старый check-then-act по снапшоту пропускает обоих` фиксирует окно
 * гонки (оба читают пустое состояние до записи и оба получают Claimed). Тест
 * `acquirePublishClaim допускает ровно одного` — целевое поведение после правки: второй
 * поток получает Busy и не идёт в сеть.
 *
 * @see specs/366-pkg-race-publish/report.md
 */
class TelegramPublishClaimRaceTest {
    private val songId = 4242L

    @AfterEach
    fun restoreProductionStore() {
        TelegramAutoPublishService.claimStore = JdbcTelegramPublishClaimStore
    }

    /** Снимок строки `tbl_songs`, который в проде читается под блокировкой. */
    private data class RowSnapshot(
        val idTelegramDemo: String = "",
        val state: String = "",
        val lastAttemptAt: String = "",
    )

    /**
     * In-memory хранилище с контрактом [TelegramPublishClaimStore]: загрузка снимка, решение
     * политики и запись `publishing` выполняются под per-song lock (аналог блокировки строки).
     */
    private class InMemoryClaimStore(
        initial: Map<Long, RowSnapshot>,
    ) : TelegramPublishClaimStore {
        private val rows = ConcurrentHashMap(initial)
        private val locks = ConcurrentHashMap<Long, Any>()

        override fun claim(
            songId: Long,
            nowMs: Long,
            nowIso: String,
        ): TelegramPublishClaim =
            synchronized(locks.computeIfAbsent(songId) { Any() }) {
                val row = rows[songId] ?: return@synchronized TelegramPublishClaim.NotFound
                when (
                    val decision =
                        TelegramPublishClaimPolicy.decide(
                            idTelegramDemo = row.idTelegramDemo,
                            state = row.state,
                            lastAttemptAt = row.lastAttemptAt,
                            nowMs = nowMs,
                        )
                ) {
                    TelegramPublishClaim.Claimed -> {
                        rows[songId] = row.copy(state = TelegramAutoPublishState.PUBLISHING.code, lastAttemptAt = nowIso)
                        decision
                    }
                    else -> decision
                }
            }

        fun snapshot(songId: Long): RowSnapshot? = rows[songId]
    }

    private fun newSong(): Song =
        Song(
            database = FakeKaraokeConnection(),
            storageService = mock(KaraokeStorageService::class.java),
            storageApiClient = mock(StorageApiClient::class.java),
        ).also { it.id = songId }

    @Test
    fun `acquirePublishClaim допускает ровно одного из конкурирующих участников`() {
        val store = InMemoryClaimStore(mapOf(songId to RowSnapshot()))
        TelegramAutoPublishService.claimStore = store

        val threads = 8
        val startGate = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val winners = AtomicInteger(0)
        val busy = AtomicInteger(0)
        val failures = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(threads)
        val nowMs = 1_790_000_000_000L

        repeat(threads) {
            pool.submit {
                startGate.await()
                try {
                    val result = TelegramAutoPublishService.acquirePublishClaim(newSong(), nowMs)
                    if (result == null) {
                        winners.incrementAndGet()
                    } else if (result.state == TelegramAutoPublishState.PUBLISHING) {
                        busy.incrementAndGet()
                    } else {
                        failures.incrementAndGet()
                    }
                } finally {
                    done.countDown()
                }
            }
        }
        startGate.countDown()
        check(done.await(10, TimeUnit.SECONDS)) { "concurrent claim test timed out" }
        pool.shutdown()

        assertEquals(1, winners.get(), "в sendVideo должен уйти ровно один поток, победителей=${winners.get()}")
        assertEquals(threads - 1, busy.get(), "остальные обязаны получить Busy без отправки")
        assertEquals(0, failures.get())
        assertEquals(TelegramAutoPublishState.PUBLISHING.code, store.snapshot(songId)?.state)
        assertEquals("2026-09-21T17:13:20+03:00", store.snapshot(songId)?.lastAttemptAt)
    }

    @Test
    fun `свежий publishing занят, протухший по TTL перехватывается`() {
        val nowMs = 1_790_000_000_000L
        val freshBusy =
            TelegramPublishClaimPolicy.decide(
                idTelegramDemo = "",
                state = TelegramAutoPublishState.PUBLISHING.code,
                lastAttemptAt = "2026-09-21T17:13:20+03:00",
                nowMs = nowMs + 60_000L,
            )
        assertTrue(freshBusy is TelegramPublishClaim.Busy)

        val staleReclaim =
            TelegramPublishClaimPolicy.decide(
                idTelegramDemo = "",
                state = TelegramAutoPublishState.PUBLISHING.code,
                lastAttemptAt = "2026-09-21T17:13:20+03:00",
                nowMs = nowMs + TelegramPublishClaimPolicy.CLAIM_TTL_MS,
            )
        assertEquals(TelegramPublishClaim.Claimed, staleReclaim)

        val noTimestampReclaim =
            TelegramPublishClaimPolicy.decide(
                idTelegramDemo = "",
                state = TelegramAutoPublishState.PUBLISHING.code,
                lastAttemptAt = "",
                nowMs = nowMs,
            )
        assertEquals(TelegramPublishClaim.Claimed, noTimestampReclaim)
    }

    @Test
    fun `уже опубликованная песня не уходит в сеть, message_id синхронизируется`() {
        val store =
            InMemoryClaimStore(
                mapOf(songId to RowSnapshot(idTelegramDemo = "777")),
            )
        TelegramAutoPublishService.claimStore = store
        val song = newSong()

        val result = TelegramAutoPublishService.acquirePublishClaim(song, 1_790_000_000_000L)

        assertNotNull(result)
        assertEquals(TelegramAutoPublishState.PUBLISHED, result?.state)
        assertEquals("777", result?.messageId)
        assertEquals("777", song.idTelegramDemo, "свежий id должен попасть в объект до saveToDbLocked()")
    }

    @Test
    fun `отказ захвата (SQL-ошибка) закрывает публикацию, а не отправляет повторно`() {
        TelegramAutoPublishService.claimStore =
            TelegramPublishClaimStore { _, _, _ -> TelegramPublishClaim.Failed("lock_timeout") }

        val result = TelegramAutoPublishService.acquirePublishClaim(newSong(), 1_790_000_000_000L)

        assertEquals(TelegramAutoPublishState.SEND_FAILED, result?.state)
        assertTrue(result?.error?.contains("lock_timeout") == true)
    }

    @Test
    fun `старый check-then-act по снапшоту пропускает обоих — окно гонки воспроизведено`() {
        // Модель старого кода: обе операции читают одну и ту же строку ДО записи состояния.
        val snapshot = RowSnapshot()
        val bothRead = CountDownLatch(2)
        val winners = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(2)

        repeat(2) {
            pool.submit {
                val decision =
                    TelegramPublishClaimPolicy.decide(
                        idTelegramDemo = snapshot.idTelegramDemo,
                        state = snapshot.state,
                        lastAttemptAt = snapshot.lastAttemptAt,
                        nowMs = 1_790_000_000_000L,
                    )
                bothRead.countDown()
                bothRead.await()
                if (decision == TelegramPublishClaim.Claimed) winners.incrementAndGet()
            }
        }
        pool.shutdown()
        check(pool.awaitTermination(5, TimeUnit.SECONDS))

        assertEquals(2, winners.get(), "без блокировки строки оба потока проходят проверку — это и есть BR-062/BR-138")
        // …тогда как атомарный захват в acquirePublishClaim оставляет одного (см. первый тест).
    }
}
