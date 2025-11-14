package com.svoemesto.karaokeapp.services

import io.minio.StatObjectResponse
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.springframework.http.MediaType

@Service
class StorageApiClient(private val webClient: WebClient) { // Предполагается, что WebClient настроен с baseUrl

    // --- POST /api/storage/upload ---
    fun uploadFile(bucketName: String, fileName: String, fileContent: ByteArray): Mono<String> {
        // Создаём MultiValueMap для хранения частей multipart-запроса
        val multipartData: MultiValueMap<String, Any> = LinkedMultiValueMap()

        // --- Ключевое изменение: Создаём ByteArrayResource с переопределением getFilename ---
        val fileResource = object : ByteArrayResource(fileContent) {
            override fun getFilename(): String? {
                // Возвращаем имя файла. Spring WebFlux должен использовать это имя
                // для Content-Disposition заголовка части multipart.
                return fileName
            }
        }

        // Добавляем ресурс файла как часть 'file'
        multipartData.add("file", fileResource)

        // Добавляем строковые параметры как части multipart
        multipartData.add("bucketName", bucketName)
        multipartData.add("fileName", fileName) // Не кодируем имя файла здесь, пусть сервер сам разбирается, если нужно
        // Если сервер строго ожидает закодированное имя в параметрах, раскомментируйте следующую строку:
        // multipartData.add("fileName", URLEncoder.encode(fileName, StandardCharsets.UTF_8))

        // Создаём multipart-тело из MultiValueMap
        val multipartBody = BodyInserters.fromMultipartData(multipartData)

        return webClient
            .post()
            .uri("/upload") // URI больше не содержит query-параметров
            // .header("Authorization", "Bearer YOUR_ACCESS_TOKEN") // Если нужно
            .contentType(MediaType.MULTIPART_FORM_DATA) // Указываем Content-Type
            .body(multipartBody) // Устанавливаем multipart-тело
            .retrieve()
            .bodyToMono<String>()
    }

    // --- GET /api/storage/url ---
    fun getFileUrl(bucketName: String, fileName: String): Mono<String> {
        val encodedBucketName = URLEncoder.encode(bucketName, StandardCharsets.UTF_8)
        val encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)

        return webClient
            .get()
            .uri("/url?bucketName=$encodedBucketName&fileName=$encodedFileName")
            // .header("Authorization", "Bearer YOUR_ACCESS_TOKEN") // Если нужно
            .retrieve()
            .bodyToMono<String>()
    }

    // --- GET /api/storage/presigned-url ---
    fun getPresignedUrl(bucketName: String, fileName: String, expiry: Int = 604800): Mono<String> {
        val encodedBucketName = URLEncoder.encode(bucketName, StandardCharsets.UTF_8)
        val encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)

        return webClient
            .get()
            .uri("/presigned-url?bucketName=$encodedBucketName&fileName=$encodedFileName&expiry=$expiry")
            // .header("Authorization", "Bearer YOUR_ACCESS_TOKEN") // Если нужно
            .retrieve()
            .bodyToMono<String>()
    }

    // --- GET /api/storage/download ---
    fun downloadFile(bucketName: String, fileName: String): Mono<ByteArray> {
        val encodedBucketName = URLEncoder.encode(bucketName, StandardCharsets.UTF_8)
        val encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)

        return webClient
            .get()
            .uri("/download?bucketName=$encodedBucketName&fileName=$encodedFileName")
            // .header("Authorization", "Bearer YOUR_ACCESS_TOKEN") // Если нужно
            .retrieve()
            .bodyToMono<ByteArray>()
    }

    // --- DELETE /api/storage/delete ---
    fun deleteFile(bucketName: String, fileName: String): Mono<String> {
        val encodedBucketName = URLEncoder.encode(bucketName, StandardCharsets.UTF_8)
        val encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)

        return webClient
            .delete()
            .uri("/delete?bucketName=$encodedBucketName&fileName=$encodedFileName")
            // .header("Authorization", "Bearer YOUR_ACCESS_TOKEN") // Если нужно
            .retrieve()
            .bodyToMono<String>()
    }

    // --- GET /api/storage/list ---
    fun listFiles(bucketName: String): Mono<List<String>> {
        val encodedBucketName = URLEncoder.encode(bucketName, StandardCharsets.UTF_8)

        return webClient
            .get()
            .uri("/list?bucketName=$encodedBucketName")
            // .header("Authorization", "Bearer YOUR_ACCESS_TOKEN") // Если нужно
            .retrieve()
            .bodyToMono<Array<String>>() // Сервер возвращает List<String>, WebClient может десериализовать в Array
            .map { it.toList() } // Конвертируем в List
    }

    // --- GET /api/storage/exists ---
    fun checkIfExists(bucketName: String, fileName: String): Mono<Map<String, Boolean>> {
        val encodedBucketName = URLEncoder.encode(bucketName, StandardCharsets.UTF_8)
        val encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)

        return webClient
            .get()
            .uri("/exists?bucketName=$encodedBucketName&fileName=$encodedFileName")
            // .header("Authorization", "Bearer YOUR_ACCESS_TOKEN") // Если нужно
            .retrieve()
            .bodyToMono<Map<String, Boolean>>()
    }

    // --- PUT /api/storage/bucket/public ---
    fun setBucketPublic(bucketName: String): Mono<String> {
        val encodedBucketName = URLEncoder.encode(bucketName, StandardCharsets.UTF_8)

        return webClient
            .put()
            .uri("/bucket/public?bucketName=$encodedBucketName")
            // .header("Authorization", "Bearer YOUR_ACCESS_TOKEN") // Если нужно
            .retrieve()
            .bodyToMono<String>()
    }

    // --- PUT /api/storage/bucket/private ---
    fun setBucketPrivate(bucketName: String): Mono<String> {
        val encodedBucketName = URLEncoder.encode(bucketName, StandardCharsets.UTF_8)

        return webClient
            .put()
            .uri("/bucket/private?bucketName=$encodedBucketName")
            // .header("Authorization", "Bearer YOUR_ACCESS_TOKEN") // Если нужно
            .retrieve()
            .bodyToMono<String>()
    }

    // --- GET /api/storage/bucket/public-status ---
    fun isBucketPublic(bucketName: String): Mono<Map<String, Boolean>> {
        val encodedBucketName = URLEncoder.encode(bucketName, StandardCharsets.UTF_8)

        return webClient
            .get()
            .uri("/bucket/public-status?bucketName=$encodedBucketName")
            // .header("Authorization", "Bearer YOUR_ACCESS_TOKEN") // Если нужно
            .retrieve()
            .bodyToMono<Map<String, Boolean>>()
    }

    // --- POST /api/storage/fileStat ---
    fun getFileStat(bucketName: String, fileName: String): Mono<StatObjectResponse> { // Предполагаем, что StatObjectResponse определён
        val encodedBucketName = URLEncoder.encode(bucketName, StandardCharsets.UTF_8)
        val encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)

        return webClient
            .post()
            .uri("/fileStat?bucketName=$encodedBucketName&fileName=$encodedFileName")
            // .header("Authorization", "Bearer YOUR_ACCESS_TOKEN") // Если нужно
            .retrieve()
            .bodyToMono<StatObjectResponse>()
    }

    // --- POST /api/storage/fileInfo ---
    fun getFileInfo(bucketName: String, fileName: String): Mono<StorageFileInfo> { // Предполагаем, что StorageFileInfo определён
        val encodedBucketName = URLEncoder.encode(bucketName, StandardCharsets.UTF_8)
        val encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)

        return webClient
            .post()
            .uri("/fileInfo?bucketName=$encodedBucketName&fileName=$encodedFileName")
            // .header("Authorization", "Bearer YOUR_ACCESS_TOKEN") // Если нужно
            .retrieve()
            .bodyToMono<StorageFileInfo>()
    }

    // --- POST /api/storage/listInfo ---
    fun listFilesInfo(bucketName: String): Mono<List<StorageFileInfo>> { // Предполагаем, что StorageFileInfo определён
        val encodedBucketName = URLEncoder.encode(bucketName, StandardCharsets.UTF_8)

        return webClient
            .post()
            .uri("/listInfo?bucketName=$encodedBucketName")
            // .header("Authorization", "Bearer YOUR_ACCESS_TOKEN") // Если нужно
            .retrieve()
            .bodyToMono<Array<StorageFileInfo>>() // Сервер возвращает List, WebClient десериализует в Array
            .map { it.toList() } // Конвертируем в List
    }
}