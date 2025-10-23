package com.svoemesto.karaokeapp.services

import io.minio.*
import io.minio.errors.ErrorResponseException
import io.minio.errors.MinioException
import io.minio.http.Method
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.InputStream

interface KaraokeStorageService {
    fun uploadFile(bucketName: String, fileName: String, file: InputStream, size: Long?)
    fun getFileUrl(bucketName: String, fileName: String): String
    fun downloadFile(bucketName: String, fileName: String): InputStream
    fun deleteFile(bucketName: String, fileName: String)
    fun getPresignedUrl(bucketName: String, fileName: String, expiry: Int = 604800): String
    fun bucketExists(bucketName: String): Boolean
    fun objectExists(bucketName: String, fileName: String): Boolean
    fun listFiles(bucketName: String): List<String>
    fun setBucketPublic(bucketName: String)
    fun setBucketPrivate(bucketName: String)
    fun isBucketPublic(bucketName: String): Boolean
}

@Service
class KaraokeStorageServiceImpl(
    @Value("\${storage.key}") val storageKey: String,
    @Value("\${storage.secret}") val storageSecret: String,
    @Value("\${storage.container-name}") val storageContainerName: String,
    @Value("\${storage.port-inside-container}") val storagePortInsideContainer: String,
    @Value("\${storage.port-host}") val storagePortHost: String,
    @Value("\${work-in-container}") val wic: Long
) : KaraokeStorageService {
    private val endpoint: String = if (wic != 0L) "http://${storageContainerName}:${storagePortInsideContainer}" else "http://localhost:${storagePortHost}"
    private val storageClient = MinioClient.builder()
        .endpoint(endpoint)
        .credentials(storageKey, storageSecret)
        .build()

    override fun uploadFile(bucketName: String, fileName: String, file: InputStream, size: Long?) {
        val sizeToUse = size ?: file.available().toLong()
        try {
            storageClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(fileName)
                    .stream(file, sizeToUse, -1)
                    .build()
            )
        } catch (e: MinioException) {
            throw RuntimeException("Failed to upload file: ${e.message}", e)
        }
    }

    override fun getFileUrl(bucketName: String, fileName: String): String {
        return "$endpoint/$bucketName/$fileName"
    }

    override fun getPresignedUrl(bucketName: String, fileName: String, expiry: Int): String {
        return try {
            storageClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .`object`(fileName)
                    .expiry(expiry) // по умолчанию 7 дней (в секундах)
                    .build()
            )
        } catch (e: MinioException) {
            throw RuntimeException("Failed to generate presigned URL: ${e.message}", e)
        }
    }

    override fun downloadFile(bucketName: String, fileName: String): InputStream {
        return try {
            storageClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(fileName)
                    .build()
            )
        } catch (e: MinioException) {
            throw RuntimeException("Failed to download file: ${e.message}", e)
        }
    }

    override fun deleteFile(bucketName: String, fileName: String) {
        try {
            storageClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(fileName)
                    .build()
            )
        } catch (e: MinioException) {
            throw RuntimeException("Failed to delete file: ${e.message}", e)
        }
    }

    override fun bucketExists(bucketName: String): Boolean {
        return try {
            storageClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())
        } catch (e: MinioException) {
            throw RuntimeException("Error checking bucket existence: ${e.message}", e)
        }
    }

    override fun objectExists(bucketName: String, fileName: String): Boolean {
        return try {
            storageClient.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(fileName)
                    .build()
            )
            true
        } catch (e: ErrorResponseException) {
            false
        } catch (e: Exception) {
            throw RuntimeException("Error checking object existence: ${e.message}", e)
        }
    }
    override fun listFiles(bucketName: String): List<String> {
        return try {
            val result = storageClient.listObjects(
                ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .build()
            )
            result.mapNotNull { it.get() }.map { obj -> obj.objectName() }
        } catch (e: MinioException) {
            throw RuntimeException("Failed to list files in bucket: ${e.message}", e)
        }
    }
    override fun setBucketPublic(bucketName: String) {
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

    override fun setBucketPrivate(bucketName: String) {
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

    override fun isBucketPublic(bucketName: String): Boolean {
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