package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.isValidFileName
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/storage")
class StorageController(
    private val karaokeStorageService: KaraokeStorageService
) {
    private val logger: Logger = LoggerFactory.getLogger(StorageController::class.java)

    @Suppress("unused")
    @Value($$"${app.max-file-size}") // можно задать в application.yml
    private val maxFileSize: String = "100MB"

    @PostMapping("/upload")
    fun uploadFile(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("bucketName") bucketName: String,
        @RequestParam("fileName", required = false) fileName: String? = null
    ): ResponseEntity<String> {
        logger.info("Received upload request for bucket: $bucketName, file: ${file.originalFilename}")

        if (file.isEmpty) {
            logger.warn("Upload failed: file is empty")
            return ResponseEntity.badRequest().body("File is empty")
        }

        val actualFileName = fileName ?: file.originalFilename ?: throw IllegalArgumentException("File name is required")

        if (!isValidFileName(actualFileName)) {
            logger.warn("Invalid file name: $actualFileName")
            return ResponseEntity.badRequest().body("Invalid file name")
        }

        if (!karaokeStorageService.bucketExists(bucketName)) {
            logger.warn("Bucket does not exist: $bucketName")
            return ResponseEntity.badRequest().body("Bucket does not exist: $bucketName")
        }

        val inputStream = file.inputStream
        val size = file.size

        try {
            karaokeStorageService.uploadFile(bucketName, actualFileName, inputStream, size)
            logger.info("File uploaded successfully: $actualFileName to bucket: $bucketName")
            return ResponseEntity.ok("File uploaded successfully: $actualFileName")
        } catch (e: Exception) {
            logger.error("Upload failed for file: $actualFileName in bucket: $bucketName", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Upload failed: ${e.message}")
        }
    }

    @GetMapping("/url")
    fun getFileUrl(
        @RequestParam("bucketName") bucketName: String,
        @RequestParam("fileName") fileName: String,
        request: HttpServletRequest
    ): ResponseEntity<String> {
        if (!isValidFileName(fileName)) {
            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
            return ResponseEntity.badRequest().body("Invalid file name")
        }

        if (!karaokeStorageService.objectExists(bucketName, fileName)) {
            logger.info("File not found: $fileName in bucket: $bucketName")
            return ResponseEntity.notFound().build()
        }

        val url = karaokeStorageService.getFileUrl(bucketName, fileName)
        logger.info("URL requested for file: $fileName in bucket: $bucketName")
        return ResponseEntity.ok(url)
    }

    @GetMapping("/presigned-url")
    fun getPresignedUrl(
        @RequestParam("bucketName") bucketName: String,
        @RequestParam("fileName") fileName: String,
        @RequestParam("expiry", required = false, defaultValue = "604800") expiry: Int,
        request: HttpServletRequest
    ): ResponseEntity<String> {
        if (!isValidFileName(fileName)) {
            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
            return ResponseEntity.badRequest().body("Invalid file name")
        }

        if (!karaokeStorageService.objectExists(bucketName, fileName)) {
            logger.info("File not found: $fileName in bucket: $bucketName")
            return ResponseEntity.notFound().build()
        }

        val url = karaokeStorageService.getPresignedUrl(bucketName, fileName, expiry)
        logger.info("Presigned URL generated for file: $fileName in bucket: $bucketName")
        return ResponseEntity.ok(url)
    }

    @GetMapping("/download")
    fun downloadFile(
        @RequestParam("bucketName") bucketName: String,
        @RequestParam("fileName") fileName: String,
        request: HttpServletRequest
    ): ResponseEntity<ByteArray> {
        if (!isValidFileName(fileName)) {
            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
            return ResponseEntity.badRequest().build()
        }

        if (!karaokeStorageService.objectExists(bucketName, fileName)) {
            logger.info("File not found: $fileName in bucket: $bucketName")
            return ResponseEntity.notFound().build()
        }

        try {
            val inputStream = karaokeStorageService.downloadFile(bucketName, fileName)
            val bytes = inputStream.readAllBytes()

            logger.info("File downloaded: $fileName from bucket: $bucketName")

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$fileName\"")
                .body(bytes)
        } catch (e: Exception) {
            logger.error("Download failed for file: $fileName in bucket: $bucketName", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @DeleteMapping("/delete")
    fun deleteFile(
        @RequestParam("bucketName") bucketName: String,
        @RequestParam("fileName") fileName: String,
        request: HttpServletRequest
    ): ResponseEntity<String> {
        if (!isValidFileName(fileName)) {
            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
            return ResponseEntity.badRequest().body("Invalid file name")
        }

        if (!karaokeStorageService.objectExists(bucketName, fileName)) {
            logger.info("File not found: $fileName in bucket: $bucketName")
            return ResponseEntity.notFound().build()
        }

        try {
            karaokeStorageService.deleteFile(bucketName, fileName)
            logger.info("File deleted: $fileName from bucket: $bucketName")
            return ResponseEntity.ok("File deleted successfully: $fileName")
        } catch (e: Exception) {
            logger.error("Deletion failed for file: $fileName in bucket: $bucketName", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Deletion failed: ${e.message}")
        }
    }

    @GetMapping("/list")
    fun listFiles(
        @RequestParam("bucketName") bucketName: String,
        request: HttpServletRequest
    ): ResponseEntity<List<String>> {
        if (!karaokeStorageService.bucketExists(bucketName)) {
            logger.info("Bucket not found: $bucketName")
            return ResponseEntity.notFound().build()
        }

        logger.info("Listing files in bucket: $bucketName from IP: ${request.remoteAddr}")

        val files = karaokeStorageService.listFiles(bucketName)
        return ResponseEntity.ok(files)
    }

    @GetMapping("/exists")
    fun checkIfExists(
        @RequestParam("bucketName") bucketName: String,
        @RequestParam("fileName") fileName: String,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Boolean>> {
        if (!isValidFileName(fileName)) {
            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
            return ResponseEntity.badRequest().build()
        }

        val exists = karaokeStorageService.objectExists(bucketName, fileName)
        logger.info("Check exists: file=$fileName, bucket=$bucketName, exists=$exists")
        return ResponseEntity.ok(mapOf("exists" to exists))
    }

    @PutMapping("/bucket/public")
    fun setBucketPublic(
        @RequestParam("bucketName") bucketName: String
    ): ResponseEntity<String> {
        if (!karaokeStorageService.bucketExists(bucketName)) {
            return ResponseEntity.notFound().build()
        }

        try {
            karaokeStorageService.setBucketPublic(bucketName)
            logger.info("Bucket set to public: $bucketName")
            return ResponseEntity.ok("Bucket '$bucketName' is now public")
        } catch (e: Exception) {
            logger.error("Failed to set bucket as public: $bucketName", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to set bucket as public: ${e.message}")
        }
    }

    @PutMapping("/bucket/private")
    fun setBucketPrivate(
        @RequestParam("bucketName") bucketName: String
    ): ResponseEntity<String> {
        if (!karaokeStorageService.bucketExists(bucketName)) {
            return ResponseEntity.notFound().build()
        }

        try {
            karaokeStorageService.setBucketPrivate(bucketName)
            logger.info("Bucket set to private: $bucketName")
            return ResponseEntity.ok("Bucket '$bucketName' is now private")
        } catch (e: Exception) {
            logger.error("Failed to set bucket as private: $bucketName", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to set bucket as private: ${e.message}")
        }
    }

    @GetMapping("/bucket/public-status")
    fun isBucketPublic(
        @RequestParam("bucketName") bucketName: String
    ): ResponseEntity<Map<String, Boolean>> {
        if (!karaokeStorageService.bucketExists(bucketName)) {
            return ResponseEntity.notFound().build()
        }

        val isPublic = karaokeStorageService.isBucketPublic(bucketName)
        logger.info("Bucket public status checked: $bucketName -> $isPublic")
        return ResponseEntity.ok(mapOf("isPublic" to isPublic))
    }
}