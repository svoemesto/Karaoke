package com.svoemesto.karaokeweb.services

import java.io.File
import java.io.InputStream

interface KaraokeStorage {
    val storageService: KaraokeStorageService
    val storageBucketName: String
    val storageFileName: String
    val storageFileNamePreview: String
    var storageBucketIsPublic: Boolean
        get() = storageService.isBucketPublic(storageBucketName)
        set(value) {
            if (value) {
                storageService.setBucketPublic(storageBucketName)
            } else {
                storageService.setBucketPrivate(storageBucketName)
            }
        }

    fun storageUploadFile(pathToFileOnDisk: String) {
        storageService.uploadFile(
            bucketName = storageBucketName,
            fileName = storageFileName,
            pathToFileOnDisk = pathToFileOnDisk
        )
    }

    fun storageUploadFilePreview(pathToFileOnDisk: String) {
        storageService.uploadFile(
            bucketName = storageBucketName,
            fileName = storageFileNamePreview,
            pathToFileOnDisk = pathToFileOnDisk
        )
    }

    fun storageUploadFile(file: InputStream, size: Long?) {
        storageService.uploadFile(
            bucketName = storageBucketName,
            fileName = storageFileName,
            file = file,
            size = size
        )
    }

    fun storageUploadFilePreview(file: InputStream, size: Long?) {
        storageService.uploadFile(
            bucketName = storageBucketName,
            fileName = storageFileNamePreview,
            file = file,
            size = size
        )
    }

    fun storageGetFileUrl(): String {
        return storageService.getFileUrl(
            bucketName = storageBucketName,
            fileName = storageFileName
        )
    }

    fun storageGetFilePreviewUrl(): String {
        return storageService.getFileUrl(
            bucketName = storageBucketName,
            fileName = storageFileNamePreview
        )
    }

    fun storageDownloadFile(pathToFileOnDisk: String): File {
        return storageService.downloadFile(
            bucketName = storageBucketName,
            fileName = storageFileName,
            pathToFileOnDisk = pathToFileOnDisk
        )
    }

    fun storageDownloadFilePreview(pathToFileOnDisk: String): File {
        return storageService.downloadFile(
            bucketName = storageBucketName,
            fileName = storageFileNamePreview,
            pathToFileOnDisk = pathToFileOnDisk
        )
    }

    fun storageDownloadFile(): InputStream {
        return storageService.downloadFile(
            bucketName = storageBucketName,
            fileName = storageFileName
        )
    }

    fun storageDownloadFilePreview(): InputStream {
        return storageService.downloadFile(
            bucketName = storageBucketName,
            fileName = storageFileNamePreview
        )
    }

    fun storageDeleteFile() {
        storageService.deleteFile(
            bucketName = storageBucketName,
            fileName = storageFileName
        )
    }

    fun storageDeleteFilePreview() {
        storageService.deleteFile(
            bucketName = storageBucketName,
            fileName = storageFileNamePreview
        )
    }

    fun storageGetPresignedUrl(expiry: Int = 604800): String {
        return storageService.getPresignedUrl(
            bucketName = storageBucketName,
            fileName = storageFileName,
            expiry = expiry
        )
    }

    fun storageGetPresignedUrlPreview(expiry: Int = 604800): String {
        return storageService.getPresignedUrl(
            bucketName = storageBucketName,
            fileName = storageFileNamePreview,
            expiry = expiry
        )
    }

    fun storageFileExists(): Boolean {
        return storageService.fileExists(
            bucketName = storageBucketName,
            fileName = storageFileName
        )
    }

    fun storageFilePreviewExists(): Boolean {
        return storageService.fileExists(
            bucketName = storageBucketName,
            fileName = storageFileNamePreview
        )
    }

}