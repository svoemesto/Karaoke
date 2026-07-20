package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.CountingInputStream
import io.minio.*
import io.minio.errors.ErrorResponseException
import io.minio.errors.MinioException
import io.minio.http.Method
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

interface StorageApiClient {
    fun uploadFile(
        bucketName: String,
        fileName: String,
        pathToFileOnDisk: String,
        onProgress: ((Int) -> Unit)? = null,
    ): String?

    fun uploadFile(
        bucketName: String,
        fileName: String,
        fileContent: ByteArray,
        onProgress: ((Int) -> Unit)? = null,
    ): Mono<String>

    fun getFileUrl(
        bucketName: String,
        fileName: String,
    ): Mono<String>

    fun getPresignedUrl(
        bucketName: String,
        fileName: String,
        expiry: Int = 604800,
    ): Mono<String>

    fun downloadFile(
        bucketName: String,
        fileName: String,
    ): Mono<ByteArray>

    fun downloadFile(
        bucketName: String,
        fileName: String,
        pathToFileOnDisk: String,
    ): File

    fun deleteFile(
        bucketName: String,
        fileName: String,
    ): Mono<String>

    fun listFiles(bucketName: String): Mono<List<String>>

    fun checkIfExists(
        bucketName: String,
        fileName: String,
    ): Mono<Map<String, Boolean>>

    fun fileExists(
        bucketName: String,
        fileName: String,
    ): Boolean

    fun fileIsActual(
        bucketName: String,
        fileName: String,
        pathToFileOnDisk: String,
    ): Boolean

    fun fileIsActual(
        bucketName: String,
        fileName: String,
        storageFileInfo: StorageFileInfo,
    ): Boolean

    fun setBucketPublic(bucketName: String): Mono<String>

    fun setBucketPrivate(bucketName: String): Mono<String>

    fun isBucketPublic(bucketName: String): Mono<Map<String, Boolean>>

    fun getFileStat(
        bucketName: String,
        fileName: String,
    ): Mono<StatObjectResponse>

    fun getFileInfo(
        bucketName: String,
        fileName: String,
    ): Mono<StorageFileInfo>

    fun listFilesInfo(bucketName: String): Mono<List<StorageFileInfo>>
}

/**
 * Клиент УДАЛЁННОГО хранилища (remote store) для karaoke-app.
 *
 * Раньше это была HTTP-прослойка через прод-karaoke-web (`WebClient` на `https://sm-karaoke.ru/api/storage`
 * → `StorageController` → прод-`MinioClient`). Она сломалась, когда MinIO вынесли на отдельный сервер
 * (`89.125.103.63:9000`): прод-karaoke-web больше не может достучаться до переехавшего MinIO
 * (`karaoke-storage:9000` в его сети нет + MTU black-hole), поэтому эндпоинты `/api/storage/…` отдавали 500 на каждый
 * `exists`/`upload`, а вся remote-проверка/заливка (HealthReport, UPLOAD_TO_REMOTE_STORE) не работала.
 *
 * Теперь karaoke-app (admin-машина) ходит в remote-хранилище НАПРЯМУЮ через `MinioClient` — новый MinIO
 * доступен и с хоста, и из контейнера karaoke-app (проверено: health 200 за ~17 мс). Endpoint задаётся
 * `storage.remote-endpoint` (env `STORAGE_REMOTE_ENDPOINT`, дефолт — новый сервер), креды — те же
 * `storage.key`/`storage.secret`, что и у локального хранилища. Интерфейс `StorageApiClient` и все
 * вызывающие места (HealthReport/Utils) не менялись; `Mono`-методы обёрнуты в `Mono.fromCallable`, чтобы
 * реальная работа (и возможная ошибка) происходила на `.block()` — под уже существующим try/catch
 * вызывающего кода.
 */
@Service
class StorageApiClientImpl(
    @Value($$"${storage.remote-endpoint:http://89.125.103.63:9000}") val remoteEndpoint: String,
    @Value($$"${storage.key}") val storageKey: String,
    @Value($$"${storage.secret}") val storageSecret: String,
) : StorageApiClient {
    private val storageClient: MinioClient =
        run {
            val httpClient =
                OkHttpClient
                    .Builder()
                    .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(300, TimeUnit.SECONDS)
                    .build()
            MinioClient
                .builder()
                .endpoint(remoteEndpoint)
                .credentials(storageKey, storageSecret)
                .httpClient(httpClient)
                .build()
        }

    private fun decodeFileNameIfEncoded(fileName: String): String =
        if (fileName.contains("%")) {
            try {
                URLDecoder.decode(fileName, StandardCharsets.UTF_8.toString())
            } catch (_: IllegalArgumentException) {
                fileName
            }
        } else {
            fileName
        }

    private fun createBucketIfNotExists(bucketName: String) {
        val exists = storageClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())
        if (!exists) {
            storageClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build())
        }
    }

    override fun uploadFile(
        bucketName: String,
        fileName: String,
        pathToFileOnDisk: String,
        onProgress: ((Int) -> Unit)?,
    ): String? {
        val file = File(pathToFileOnDisk)
        if (!file.exists()) return null
        val fileContent = file.readBytes()
        return try {
            uploadFile(bucketName = bucketName, fileName = fileName, fileContent = fileContent, onProgress = onProgress).block()
        } catch (e: Exception) {
            println("Ошибка при загрузке файла в удаленное хранилище: ${e.message}")
            null
        }
    }

    override fun uploadFile(
        bucketName: String,
        fileName: String,
        fileContent: ByteArray,
        onProgress: ((Int) -> Unit)?,
    ): Mono<String> =
        Mono.fromCallable {
            val decodedFileName = decodeFileNameIfEncoded(fileName)
            createBucketIfNotExists(bucketName)
            val base = ByteArrayInputStream(fileContent)
            val stream =
                if (onProgress != null && fileContent.isNotEmpty()) {
                    CountingInputStream(base) { bytesRead -> onProgress(((bytesRead * 100) / fileContent.size).toInt()) }
                } else {
                    base
                }
            storageClient.putObject(
                PutObjectArgs
                    .builder()
                    .bucket(bucketName)
                    .`object`(decodedFileName)
                    .stream(stream, fileContent.size.toLong(), -1)
                    .build(),
            )
            decodedFileName
        }

    override fun getFileUrl(
        bucketName: String,
        fileName: String,
    ): Mono<String> =
        Mono.fromCallable {
            val decodedFileName = decodeFileNameIfEncoded(fileName)
            "$remoteEndpoint/$bucketName/$decodedFileName"
        }

    override fun getPresignedUrl(
        bucketName: String,
        fileName: String,
        expiry: Int,
    ): Mono<String> =
        Mono.fromCallable {
            val decodedFileName = decodeFileNameIfEncoded(fileName)
            storageClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs
                    .builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .`object`(decodedFileName)
                    .expiry(expiry)
                    .build(),
            )
        }

    override fun downloadFile(
        bucketName: String,
        fileName: String,
    ): Mono<ByteArray> =
        Mono.fromCallable {
            val decodedFileName = decodeFileNameIfEncoded(fileName)
            storageClient
                .getObject(
                    GetObjectArgs
                        .builder()
                        .bucket(bucketName)
                        .`object`(decodedFileName)
                        .build(),
                ).use { it.readBytes() }
        }

    override fun downloadFile(
        bucketName: String,
        fileName: String,
        pathToFileOnDisk: String,
    ): File {
        val bytes =
            try {
                downloadFile(bucketName = bucketName, fileName = fileName).block()
            } catch (e: Exception) {
                println("Ошибка при получении файла из удаленного хранилища: ${e.message}")
                null
            }
        if (bytes != null) {
            val file = File(pathToFileOnDisk)
            file.writeBytes(bytes)
            return file
        } else {
            throw RuntimeException("Ошибка при получении файла из удаленного хранилища")
        }
    }

    override fun deleteFile(
        bucketName: String,
        fileName: String,
    ): Mono<String> =
        Mono.fromCallable {
            val decodedFileName = decodeFileNameIfEncoded(fileName)
            val exists = storageClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())
            if (exists) {
                storageClient.removeObject(
                    RemoveObjectArgs
                        .builder()
                        .bucket(bucketName)
                        .`object`(decodedFileName)
                        .build(),
                )
            }
            "OK"
        }

    override fun listFiles(bucketName: String): Mono<List<String>> =
        Mono.fromCallable {
            val exists = storageClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())
            if (!exists) {
                emptyList<String>()
            } else {
                storageClient
                    .listObjects(
                        ListObjectsArgs
                            .builder()
                            .bucket(bucketName)
                            .recursive(true)
                            .build(),
                    ).mapNotNull { it.get() }
                    .map { it.objectName() }
            }
        }

    override fun checkIfExists(
        bucketName: String,
        fileName: String,
    ): Mono<Map<String, Boolean>> =
        Mono.fromCallable {
            mapOf("exists" to (statObjectOrNull(bucketName, fileName) != null))
        }

    override fun fileExists(
        bucketName: String,
        fileName: String,
    ): Boolean {
        val result =
            try {
                checkIfExists(bucketName = bucketName, fileName = fileName).block()
            } catch (e: Exception) {
                println("Ошибка при проверке наличия файла в удаленном хранилище: ${e.message}")
                null
            }
        return result?.get("exists") ?: false
    }

    override fun fileIsActual(
        bucketName: String,
        fileName: String,
        pathToFileOnDisk: String,
    ): Boolean {
        var result = true
        val file = File(pathToFileOnDisk)
        if (file.exists()) {
            val fileInfo =
                try {
                    getFileInfo(bucketName = bucketName, fileName = fileName).block()
                } catch (e: Exception) {
                    println("Ошибка при проверке информации о файле в удаленном хранилище: ${e.message}")
                    null
                }
            if (fileInfo != null) {
                result = (file.length() == fileInfo.size)
            }
        }
        return result
    }

    override fun fileIsActual(
        bucketName: String,
        fileName: String,
        storageFileInfo: StorageFileInfo,
    ): Boolean {
        var result = true
        val fileInfo =
            try {
                getFileInfo(bucketName = bucketName, fileName = fileName).block()
            } catch (e: Exception) {
                println("Ошибка при проверке информации о файле в удаленном хранилище: ${e.message}")
                null
            }
        if (fileInfo != null) {
            result = (storageFileInfo.size == fileInfo.size)
        }
        return result
    }

    override fun setBucketPublic(bucketName: String): Mono<String> =
        Mono.fromCallable {
            createBucketIfNotExists(bucketName)
            val policy =
                """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": {"AWS": ["*"]},
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::$bucketName/*"]
                    }
                  ]
                }
                """.trimIndent()
            storageClient.setBucketPolicy(
                SetBucketPolicyArgs
                    .builder()
                    .bucket(bucketName)
                    .config(policy)
                    .build(),
            )
            "OK"
        }

    override fun setBucketPrivate(bucketName: String): Mono<String> =
        Mono.fromCallable {
            createBucketIfNotExists(bucketName)
            storageClient.setBucketPolicy(
                SetBucketPolicyArgs
                    .builder()
                    .bucket(bucketName)
                    .config("")
                    .build(),
            )
            "OK"
        }

    override fun isBucketPublic(bucketName: String): Mono<Map<String, Boolean>> =
        Mono.fromCallable {
            val isPublic =
                try {
                    val policy =
                        storageClient.getBucketPolicy(
                            GetBucketPolicyArgs.builder().bucket(bucketName).build(),
                        )
                    policy.isNotEmpty() && policy.contains("\"Effect\":\"Allow\"") && policy.contains("\"Principal\":{\"AWS\":[\"*\"]}")
                } catch (e: ErrorResponseException) {
                    if (e.errorResponse().code() == "NoSuchBucketPolicy") false else throw e
                }
            mapOf("isPublic" to isPublic)
        }

    override fun getFileStat(
        bucketName: String,
        fileName: String,
    ): Mono<StatObjectResponse> =
        Mono.fromCallable {
            val decodedFileName = decodeFileNameIfEncoded(fileName)
            storageClient.statObject(
                StatObjectArgs
                    .builder()
                    .bucket(bucketName)
                    .`object`(decodedFileName)
                    .build(),
            )
        }

    override fun getFileInfo(
        bucketName: String,
        fileName: String,
    ): Mono<StorageFileInfo> =
        Mono.fromCallable {
            val decodedFileName = decodeFileNameIfEncoded(fileName)
            val stat = statObjectOrNull(bucketName, decodedFileName)
            StorageFileInfo(
                bucketName = bucketName,
                fileName = decodedFileName,
                etag = stat?.etag() ?: "",
                size = stat?.size() ?: -1,
            )
        }

    override fun listFilesInfo(bucketName: String): Mono<List<StorageFileInfo>> =
        Mono.fromCallable {
            (listFiles(bucketName).block() ?: emptyList()).map { fileName ->
                getFileInfo(bucketName = bucketName, fileName = fileName).block()!!
            }
        }

    private fun statObjectOrNull(
        bucketName: String,
        fileName: String,
    ): StatObjectResponse? {
        val decodedFileName = decodeFileNameIfEncoded(fileName)
        return try {
            storageClient.statObject(
                StatObjectArgs
                    .builder()
                    .bucket(bucketName)
                    .`object`(decodedFileName)
                    .build(),
            )
        } catch (_: MinioException) {
            null
        }
    }
}
