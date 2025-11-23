package com.svoemesto.karaokeapp.services

import io.minio.*
import io.minio.errors.ErrorResponseException
import io.minio.errors.MinioException
import io.minio.http.Method
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.Serializable
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

interface KaraokeStorageService {
    fun uploadFile(bucketName: String, fileName: String, file: InputStream, size: Long?)
    fun uploadFile(bucketName: String, fileName: String, pathToFileOnDisk: String)
    fun getFileUrl(bucketName: String, fileName: String): String
    fun downloadFile(bucketName: String, fileName: String): InputStream
    fun downloadFile(bucketName: String, fileName: String, pathToFileOnDisk: String): File
    fun deleteFile(bucketName: String, fileName: String)
    fun getPresignedUrl(bucketName: String, fileName: String, expiry: Int = 604800): String
    fun bucketExists(bucketName: String): Boolean
    fun fileExists(bucketName: String, fileName: String): Boolean
    fun listFiles(bucketName: String): List<String>
    fun setBucketPublic(bucketName: String)
    fun setBucketPrivate(bucketName: String)
    fun isBucketPublic(bucketName: String): Boolean
    fun createBucketIfNotExists(bucketName: String)
    fun deleteAllEmptyBuckets()
    fun getFileStat(bucketName: String, fileName: String): StatObjectResponse?
    fun getFileInfo(bucketName: String, fileName: String): StorageFileInfo
    fun fileIsActual(bucketName: String, fileName: String, pathToFileOnDisk: String): Boolean
    fun fileIsActual(bucketName: String, fileName: String, storageFileInfo: StorageFileInfo): Boolean
    fun listFilesInfo(bucketName: String): List<StorageFileInfo>
}

data class StorageFileInfo(
    val bucketName: String,
    val fileName: String,
    val etag: String,
    val size: Long
) : Serializable

@Service
class KaraokeStorageServiceImpl(
    @Value($$"${storage.key}") val storageKey: String,
    @Value($$"${storage.secret}") val storageSecret: String,
    @Value($$"${storage.container-name}") val storageContainerName: String,
    @Value($$"${storage.port-inside-container}") val storagePortInsideContainer: String,
    @Value($$"${storage.port-host}") val storagePortHost: String,
    @Value($$"${work-in-container}") val wic: Long
) : KaraokeStorageService {
    private val endpoint: String = if (wic != 0L) "http://${storageContainerName}:${storagePortInsideContainer}" else "http://localhost:${storagePortHost}"
    private val storageClient = MinioClient.builder()
        .endpoint(endpoint)
        .credentials(storageKey, storageSecret)
        .build()

    // --- Вспомогательная функция для декодирования имени файла ---
    private fun decodeFileNameIfEncoded(fileName: String): String {
        return if (fileName.contains("%")) { // Простая проверка: содержит ли имя символ '%'
            try {
                URLDecoder.decode(fileName, StandardCharsets.UTF_8.toString())
            } catch (_: IllegalArgumentException) {
                // Если декодирование не удалось (например, '% не в кодировке), возвращаем оригинальное имя
                fileName
            }
        } else {
            // Если нет '%', имя, вероятно, не закодировано
            fileName
        }
    }
    // --- /Вспомогательная функция ---

    override fun listFilesInfo(bucketName: String): List<StorageFileInfo> {
        return listFiles(bucketName = bucketName).map { fileName ->
            getFileInfo(bucketName = bucketName, fileName = fileName)
        }
    }

    override fun getFileInfo(bucketName: String, fileName: String): StorageFileInfo {
        val decodedFileName = decodeFileNameIfEncoded(fileName) // Декодируем перед использованием
        val fileStat = getFileStat(bucketName = bucketName, fileName = decodedFileName)
        return StorageFileInfo(
            bucketName = bucketName,
            fileName = decodedFileName, // Сохраняем декодированное имя в объекте
            etag = fileStat?.etag() ?: "",
            size = fileStat?.size() ?: -1
        )
    }

    override fun getFileStat(bucketName: String, fileName: String): StatObjectResponse? {
        val decodedFileName = decodeFileNameIfEncoded(fileName) // Декодируем перед использованием
        return try {
            storageClient.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(decodedFileName) // Используем декодированное имя
                    .build()
            )
        } catch (_: MinioException) {
            null
        }
    }

    override fun deleteAllEmptyBuckets() {
        try {
            // Получаем список всех бакетов
            val buckets = storageClient.listBuckets()

            for (bucket in buckets) {
                val bucketName = bucket.name()
                println("Checking bucket: $bucketName")

                // Проверяем, пустой ли бакет
                // Для этого попробуем получить список объектов в бакете
                val objects = storageClient.listObjects(
                    ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .recursive(true) // <-- Уже исправлено
                        .maxKeys(1) // Запрашиваем только 1 объект, чтобы проверить наличие
                        .build()
                )

                // Если итератор пуст (next() бросит исключение или вернет null быстро), бакет пуст
                val isEmpty = try {
                    val iterator = objects.iterator()
                    !iterator.hasNext() // Если hasNext() возвращает false, значит, объектов нет
                } catch (e: Exception) {
                    // Если возникла ошибка при итерации (редко, но возможно), считаем бакет не пустым или логируем
                    println("Error checking contents of bucket '$bucketName': ${e.message}")
                    false // Не удаляем, если не уверены
                }

                if (isEmpty) {
                    println("Bucket '$bucketName' is empty. Attempting to delete...")
                    try {
                        // Удаляем пустой бакет
                        storageClient.removeBucket(
                            RemoveBucketArgs.builder()
                                .bucket(bucketName)
                                .build()
                        )
                        println("Bucket '$bucketName' deleted successfully.")
                    } catch (e: MinioException) {
                        throw RuntimeException("Failed to delete bucket '$bucketName': ${e.message}", e)
                    }
                } else {
                    println("Bucket '$bucketName' is not empty. Skipping.")
                }
            }
        } catch (e: MinioException) {
            throw RuntimeException("Failed to list buckets: ${e.message}", e)
        }
    }

    override fun createBucketIfNotExists(bucketName: String) {
        if (!bucketExists(bucketName)) {
            try {
                storageClient.makeBucket(
                    MakeBucketArgs.builder()
                        .bucket(bucketName)
                        .build()
                )
                println("Bucket '$bucketName' created successfully.")
            } catch (e: MinioException) {
                throw RuntimeException("Failed to create bucket '$bucketName': ${e.message}", e)
            }
        }
    }

    override fun uploadFile(bucketName: String, fileName: String, pathToFileOnDisk: String) {
        val file = File(pathToFileOnDisk)
        if (file.exists()) {
            val size = file.length()
            val fileInputStream = file.inputStream()
            uploadFile(
                bucketName = bucketName,
                fileName = fileName, // fileName передаётся в uploadFile как есть, но он должен быть оригинальным именем
                file = fileInputStream,
                size = size
            )
        }
    }

    override fun uploadFile(bucketName: String, fileName: String, file: InputStream, size: Long?) {
        val decodedFileName = decodeFileNameIfEncoded(fileName) // Декодируем перед использованием
        createBucketIfNotExists(bucketName)
        val sizeToUse = size ?: file.available().toLong()
        try {
            storageClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(decodedFileName) // Используем декодированное имя
                    .stream(file, sizeToUse, -1)
                    .build()
            )
        } catch (e: MinioException) {
            throw RuntimeException("Failed to upload file: ${e.message}", e)
        }
    }

    override fun getFileUrl(bucketName: String, fileName: String): String {
        val decodedFileName = decodeFileNameIfEncoded(fileName) // Декодируем перед использованием
        return "$endpoint/$bucketName/$decodedFileName" // Используем декодированное имя в URL
    }

    override fun getPresignedUrl(bucketName: String, fileName: String, expiry: Int): String {
        val decodedFileName = decodeFileNameIfEncoded(fileName) // Декодируем перед использованием
        return try {
            storageClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .`object`(decodedFileName) // Используем декодированное имя
                    .expiry(expiry) // по умолчанию 7 дней (в секундах)
                    .build()
            )
        } catch (e: MinioException) {
            throw RuntimeException("Failed to generate presigned URL: ${e.message}", e)
        }
    }

    override fun downloadFile(bucketName: String, fileName: String, pathToFileOnDisk: String): File {
        val decodedFileName = decodeFileNameIfEncoded(fileName) // Декодируем перед использованием

        val fileInputStream = downloadFile(
            bucketName = bucketName,
            fileName = decodedFileName // Передаём декодированное имя
        )
        val file = File(pathToFileOnDisk)
        FileOutputStream(file).use { outputStream ->
            fileInputStream.use { it.copyTo(outputStream) }
        }

        return file
    }

    override fun downloadFile(bucketName: String, fileName: String): InputStream {
        val decodedFileName = decodeFileNameIfEncoded(fileName) // Декодируем перед использованием
        return try {
            storageClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(decodedFileName) // Используем декодированное имя
                    .build()
            )
        } catch (e: MinioException) {
            throw RuntimeException("Failed to download file: ${e.message}", e)
        }
    }

    override fun deleteFile(bucketName: String, fileName: String) {
        val decodedFileName = decodeFileNameIfEncoded(fileName) // Декодируем перед использованием
        if (bucketExists(bucketName)) {
            try {
                storageClient.removeObject(
                    RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .`object`(decodedFileName) // Используем декодированное имя
                        .build()
                )
            } catch (e: MinioException) {
                throw RuntimeException("Failed to delete file: ${e.message}", e)
            }
        }
    }

    override fun bucketExists(bucketName: String): Boolean {
        return try {
            storageClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())
        } catch (e: MinioException) {
            throw RuntimeException("Error checking bucket existence: ${e.message}", e)
        }
    }

    override fun fileExists(bucketName: String, fileName: String): Boolean {
        if (bucketExists(bucketName)) {
            val decodedFileName = decodeFileNameIfEncoded(fileName) // Декодируем перед использованием
            return try {
                storageClient.statObject(
                    StatObjectArgs.builder()
                        .bucket(bucketName)
                        .`object`(decodedFileName) // Используем декодированное имя
                        .build()
                )
                true
            } catch (_: ErrorResponseException) {
                false
            } catch (e: Exception) {
                throw RuntimeException("Error checking object existence: ${e.message}", e)
            }
        } else {
            return false
        }
    }

    override fun fileIsActual(bucketName: String, fileName: String, pathToFileOnDisk: String): Boolean {
        var result = true
        val file = File(pathToFileOnDisk)
        if (file.exists()) {
            val fileInfo = getFileInfo(bucketName = bucketName, fileName = fileName)
            result = (file.length() == fileInfo.size)
        }
        return result
    }

    override fun fileIsActual(bucketName: String, fileName: String, storageFileInfo: StorageFileInfo): Boolean {

        val fileInfo = getFileInfo(bucketName = bucketName, fileName = fileName)
        return  storageFileInfo.size == fileInfo.size

    }

    override fun listFiles(bucketName: String): List<String> {
        if (bucketExists(bucketName)) {
            return try {
                val result = storageClient.listObjects(
                    ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .recursive(true) // <-- Уже исправлено
                        .build()
                )
                result.mapNotNull { it.get() }.map { obj -> obj.objectName() }
            } catch (e: MinioException) {
                throw RuntimeException("Failed to list files in bucket: ${e.message}", e)
            }
        } else {
            return emptyList()
        }
    }

    override fun setBucketPublic(bucketName: String) {
        createBucketIfNotExists(bucketName)
        if (bucketExists(bucketName)) {
            try {
                val policy = """
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
                    SetBucketPolicyArgs.builder()
                        .bucket(bucketName)
                        .config(policy)
                        .build()
                )
            } catch (e: MinioException) {
                throw RuntimeException("Failed to set bucket as public: ${e.message}", e)
            }
        }
    }

    override fun setBucketPrivate(bucketName: String) {
        createBucketIfNotExists(bucketName)
        if (bucketExists(bucketName)) {
            try {
                // Политика по умолчанию — приватная (без политики)
                storageClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                        .bucket(bucketName)
                        .config("") // пустая политика = приватный доступ
                        .build()
                )
            } catch (e: MinioException) {
                throw RuntimeException("Failed to set bucket as private: ${e.message}", e)
            }
        }
    }

    override fun isBucketPublic(bucketName: String): Boolean {
        createBucketIfNotExists(bucketName)
        return try {
            val policy = storageClient.getBucketPolicy(
                GetBucketPolicyArgs.builder()
                    .bucket(bucketName)
                    .build()
            )
            // Если политика не пуста и содержит разрешение на "s3:GetObject" для "*", то бакет публичный
            policy.isNotEmpty() && policy.contains("\"Effect\":\"Allow\"") && policy.contains("\"Principal\":{\"AWS\":[\"*\"]}")
        } catch (e: ErrorResponseException) {
            if (e.errorResponse().code() == "NoSuchBucketPolicy") {
                false // политики нет — бакет приватный
            } else {
                throw RuntimeException("Failed to get bucket policy: ${e.message}", e)
            }
        } catch (e: MinioException) {
            throw RuntimeException("Failed to check bucket policy: ${e.message}", e)
        }
    }
}