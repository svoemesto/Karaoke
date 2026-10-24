package com.svoemesto.karaokeapp.services

import io.minio.DeleteBucketPolicyArgs
import io.minio.MinioClient
import io.minio.errors.ErrorResponseException

/**
 * Снятие политики бакета — единственный способ сделать бакет приватным в MinIO.
 *
 * **Почему не `setBucketPolicy` с пустым документом.** MinIO отвергает пустой
 * документ политики (`InvalidArgument`), поэтому «приват» через
 * `setBucketPolicy(config = "")` **не выполняется**: вызов падает, а бакет с уже
 * установленной публичной политикой остаётся анонимно читаемым. Это нашлось при
 * развёртывании (2026-10-24, спека 561 / OpenProject #361): инициализатор политики
 * писал в журнал `policy=private failed reason=ReactiveException`, а состояние
 * бакета оставалось прежним (для нового бакета это маскировалось тем, что он и так
 * приватен по умолчанию).
 *
 * **Что делаем.** `deleteBucketPolicy` снимает политику; если её нет
 * (`NoSuchBucketPolicy`) — бакет уже приватный, это не ошибка. Любая другая ошибка
 * пробрасывается наружу: молча проглоченная ошибка здесь означала бы анонимно
 * читаемый бакет.
 */
internal fun deleteBucketPolicyQuietly(
    client: MinioClient,
    bucketName: String,
) {
    try {
        client.deleteBucketPolicy(
            DeleteBucketPolicyArgs
                .builder()
                .bucket(bucketName)
                .build(),
        )
    } catch (e: ErrorResponseException) {
        if (e.errorResponse().code() != "NoSuchBucketPolicy") throw e
    }
}
