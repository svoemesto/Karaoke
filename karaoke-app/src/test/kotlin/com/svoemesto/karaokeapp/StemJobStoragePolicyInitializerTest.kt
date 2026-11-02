package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.StemJob
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import reactor.core.publisher.Mono

/**
 * KARAOKE-385: политика бакетов — не одноразовое действие на старте, а поддерживаемое состояние.
 *
 * Инцидент 2026-11-02: при старте прод-канал до MinIO был закрыт (смена адреса админ-машины),
 * `setBucketPrivate`/`setBucketPublic` падали с `ReactiveError`, инициализатор писал ERROR и больше
 * не пытался — политики можно было «дожать» только перезапуском контейнера.
 *
 * Тесты фиксируют контракт:
 * - при расхождении политика применяется и состояние перечитывается (не «выстрелили и забыли»);
 * - при совпадении лишних записей нет (идемпотентность);
 * - недоступность MinIO не роняет приложение, но состояние остаётся «не подтверждено»;
 * - кратковременный сбой преодолевается повтором;
 * - самопроверка по расписанию дожимает дрейф без перезапуска, но не чаще интервала.
 *
 * Офлайн: `StorageApiClient` подменён моком, паузы и часы инжектируются.
 */
class StemJobStoragePolicyInitializerTest {
    private val stemjobs = StemJob.STORAGE_BUCKET
    private val publicBucket = StemJob.PUBLIC_STORAGE_BUCKET

    private fun client(): StorageApiClient = Mockito.mock(StorageApiClient::class.java)

    private fun initializer(client: StorageApiClient): StemJobStoragePolicyInitializer =
        StemJobStoragePolicyInitializer(client).apply {
            attempts = 3
            baseDelayMs = 0
            maxDelayMs = 0
            pause = {}
        }

    @Test
    fun `расхождение политик исправляется и перечитывается`() {
        val client = client()
        // stemjobs сейчас public (должен быть private), karaoke сейчас private (должен быть public):
        // после применения читаем фактическое состояние заново.
        Mockito.`when`(client.isBucketPublic(stemjobs)).thenReturn(Mono.just(mapOf("isPublic" to true)), Mono.just(mapOf("isPublic" to false)))
        Mockito.`when`(client.isBucketPublic(publicBucket)).thenReturn(Mono.just(mapOf("isPublic" to false)), Mono.just(mapOf("isPublic" to true)))
        Mockito.`when`(client.setBucketPrivate(stemjobs)).thenReturn(Mono.just("OK"))
        Mockito.`when`(client.setBucketPublic(publicBucket)).thenReturn(Mono.just("OK"))

        val init = initializer(client)
        init.enforceBucketPolicies()

        Mockito.verify(client).setBucketPrivate(stemjobs)
        Mockito.verify(client).setBucketPublic(publicBucket)
        assertTrue(init.isConfirmed(), "после исправления политики считаются подтверждёнными")
    }

    @Test
    fun `совпадающие политики не переписываются`() {
        val client = client()
        Mockito.`when`(client.isBucketPublic(stemjobs)).thenReturn(Mono.just(mapOf("isPublic" to false)))
        Mockito.`when`(client.isBucketPublic(publicBucket)).thenReturn(Mono.just(mapOf("isPublic" to true)))

        val init = initializer(client)
        init.enforceBucketPolicies()

        Mockito.verify(client, Mockito.never()).setBucketPrivate(Mockito.anyString())
        Mockito.verify(client, Mockito.never()).setBucketPublic(Mockito.anyString())
        assertTrue(init.isConfirmed())
    }

    @Test
    fun `недоступность MinIO не роняет старт но оставляет политику неподтверждённой`() {
        val client = client()
        Mockito
            .`when`(client.isBucketPublic(Mockito.anyString()))
            .thenThrow(RuntimeException("ReactiveError: minio недоступен"))

        val init = initializer(client)
        var pauses = 0
        init.pause = { pauses++ }

        assertDoesNotThrow({ init.enforceBucketPolicies() }, "недоступность MinIO не должна блокировать старт")
        assertFalse(init.isConfirmed(), "политика не подтверждена — приложение должно повторить позже")
        assertEquals(4, pauses, "при attempts=3 и двух бакетах между попытками ровно 4 паузы")
    }

    @Test
    fun `кратковременный сбой преодолевается повтором`() {
        val client = client()
        // Первый вызов чтения падает, второй отдаёт корректное состояние.
        Mockito
            .`when`(client.isBucketPublic(stemjobs))
            .thenThrow(RuntimeException("IOException"))
            .thenReturn(Mono.just(mapOf("isPublic" to false)))
        Mockito.`when`(client.isBucketPublic(publicBucket)).thenReturn(Mono.just(mapOf("isPublic" to true)))

        val init = initializer(client)
        init.enforceBucketPolicies()

        assertTrue(init.isConfirmed(), "после успешного повтора политика подтверждена")
    }

    @Test
    fun `самопроверка дожимает дрейф без перезапуска и не чаще интервала`() {
        val client = client()
        Mockito.`when`(client.isBucketPublic(stemjobs)).thenReturn(Mono.just(mapOf("isPublic" to false)))
        Mockito.`when`(client.isBucketPublic(publicBucket)).thenReturn(Mono.just(mapOf("isPublic" to true)))

        var now = 0L
        val init = initializer(client)
        init.clock = { now }
        init.enforceBucketPolicies()
        assertTrue(init.isConfirmed())

        // Дрейф: karaoke стал private. В пределах интервала самопроверка его не трогает.
        Mockito.`when`(client.isBucketPublic(publicBucket)).thenReturn(Mono.just(mapOf("isPublic" to false)), Mono.just(mapOf("isPublic" to true)))
        Mockito.`when`(client.setBucketPublic(publicBucket)).thenReturn(Mono.just("OK"))
        now = StemJobStoragePolicyInitializer.CONFIRMED_INTERVAL_MS / 2
        init.scheduledSelfCheck()
        Mockito.verify(client, Mockito.never()).setBucketPublic(Mockito.anyString())

        // Истёк интервал — проверка срабатывает и дожимает политику.
        now = StemJobStoragePolicyInitializer.CONFIRMED_INTERVAL_MS + 1
        init.scheduledSelfCheck()
        Mockito.verify(client, Mockito.times(1)).setBucketPublic(publicBucket)
        assertTrue(init.isConfirmed(), "дрейф дожат без перезапуска приложения")
    }

    @Test
    fun `backoff растёт и упирается в максимум`() {
        val init =
            StemJobStoragePolicyInitializer(client()).apply {
                baseDelayMs = 2_000
                maxDelayMs = 30_000
            }

        assertEquals(2_000, init.backoffDelayMs(1))
        assertEquals(4_000, init.backoffDelayMs(2))
        assertEquals(8_000, init.backoffDelayMs(3))
        assertEquals(30_000, init.backoffDelayMs(10))
    }
}
