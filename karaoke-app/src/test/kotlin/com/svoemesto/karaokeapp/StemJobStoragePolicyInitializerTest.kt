package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.StemJob
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import reactor.core.publisher.Mono

/**
 * Регрессионный тест инварианта закрытости (спека 561, OpenProject #361, аудит BR-258,
 * решение владельца №2): при старте `karaoke-app` сам приводит свои бакеты к безопасному
 * состоянию — приватный бакет закрыт, публичный остаётся открытым. Fail-safe: ошибка связи с
 * хранилищем не роняет старт приложения.
 *
 * @see specs/561-impl-stem-bucket/research.md
 */
class StemJobStoragePolicyInitializerTest {
    private fun client(
        privateFailure: Boolean = false,
    ): StorageApiClient {
        val client = Mockito.mock(StorageApiClient::class.java)
        val answer: org.mockito.stubbing.Answer<Mono<String>> =
            if (privateFailure) {
                org.mockito.stubbing.Answer { Mono.error(RuntimeException("minio недоступен")) }
            } else {
                org.mockito.stubbing.Answer { Mono.just("OK") }
            }
        Mockito.`when`(client.setBucketPrivate(Mockito.anyString())).thenAnswer(answer)
        Mockito.`when`(client.setBucketPublic(Mockito.anyString())).thenAnswer(answer)
        return client
    }

    @Test
    fun `при старте приватный бакет закрывается а публичный остаётся открытым`() {
        val client = client()

        StemJobStoragePolicyInitializer(client).enforceBucketPolicies()

        Mockito.verify(client).setBucketPrivate(StemJob.STORAGE_BUCKET)
        Mockito.verify(client).setBucketPublic(StemJob.PUBLIC_STORAGE_BUCKET)
    }

    @Test
    fun `ошибка связи с хранилищем не роняет старт приложения`() {
        val client = client(privateFailure = true)

        assertDoesNotThrow(
            { StemJobStoragePolicyInitializer(client).enforceBucketPolicies() },
            "недоступность MinIO не должна блокировать старт karaoke-app",
        )
    }
}
