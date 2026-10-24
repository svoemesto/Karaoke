package com.svoemesto.karaokeapp.services

import io.minio.MinioClient
import io.minio.errors.ErrorResponseException
import io.minio.messages.ErrorResponse
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito

/**
 * Тест снятия политики бакета (спека 561 / OpenProject #361, BR-258).
 *
 * **Дефект, который закрывается.** «Приват» бакета делался через
 * `setBucketPolicy(config = "")`. MinIO пустой документ политики отвергает
 * (`InvalidArgument`), поэтому вызов падал, а бакет с уже установленной публичной
 * политикой оставался анонимно читаемым — «приват» молча не срабатывал.
 *
 * Найдено при развёртывании 2026-10-24: в журнале было
 * `stemjobs:storage:policy bucket=stemjobs policy=private failed reason=ReactiveException`.
 * Для нового бакета состояние оказывалось правильным (бакет приватен по умолчанию),
 * поэтому ошибка выглядела безобидно — но на существующем публичном бакете дыра
 * осталась бы.
 */
class StoragePolicyTest {
    private fun error(
        code: String,
        message: String,
    ): ErrorResponseException {
        val requestBuilder = Request.Builder()
        requestBuilder.url("http://localhost/")
        val request = requestBuilder.build()
        val response =
            Response
                .Builder()
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(404)
                .message(message)
                .build()
        val errorResponse =
            ErrorResponse(
                code,
                message,
                "bucket",
                "",
                "",
                "",
                "",
            )
        return ErrorResponseException(errorResponse, response, null)
    }

    @Test
    fun `политика снимается удалением а не пустым документом`() {
        val client = Mockito.mock(MinioClient::class.java)
        deleteBucketPolicyQuietly(client, "karaoke")
        Mockito
            .verify(client)
            .deleteBucketPolicy(ArgumentMatchers.any())
        // Установка политики (в т.ч. пустой) вызываться не должна — MinIO её отвергает
        Mockito
            .verify(client, Mockito.never())
            .setBucketPolicy(ArgumentMatchers.any())
    }

    @Test
    fun `отсутствие политики не считается ошибкой бакет уже приватный`() {
        val client = Mockito.mock(MinioClient::class.java)
        Mockito
            .`when`(client.deleteBucketPolicy(ArgumentMatchers.any()))
            .thenThrow(error("NoSuchBucketPolicy", "The bucket policy does not exist"))
        assertDoesNotThrow { deleteBucketPolicyQuietly(client, "stemjobs") }
    }

    @Test
    fun `другая ошибка MinIO пробрасывается наружу а не проглатывается`() {
        val client = Mockito.mock(MinioClient::class.java)
        Mockito
            .`when`(client.deleteBucketPolicy(ArgumentMatchers.any()))
            .thenThrow(error("AccessDenied", "Access denied"))
        // Молча проглоченная ошибка здесь означала бы анонимно читаемый бакет
        assertThrows(ErrorResponseException::class.java) { deleteBucketPolicyQuietly(client, "karaoke") }
    }
}
