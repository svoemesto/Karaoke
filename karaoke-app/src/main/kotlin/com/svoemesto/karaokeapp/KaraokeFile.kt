package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.SongVersion
import com.svoemesto.karaokeapp.services.KaraokeStorage
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import java.io.File

data class KaraokeFile(
    val type: KaraokeFileType,
    override val storageService: KaraokeStorageService,
    override val storageBucketName: String,
    override val storageFileName: String,
    override val storageFileNamePreview: String = "",
    val karaokePlatform: KaraokePlatform? = null,
    val songVersion: SongVersion? = null,
    val pathToFile: String,
    val canBe: Boolean,
    val actionToCreate: () -> Unit = {},
    val actionToUploadInLocalStorage: () -> Unit = {},
    val actionToUploadInRemoteStorage: () -> Unit = {}
): KaraokeStorage {

    fun filePresentInStorage() = storageService.fileExists(storageBucketName, storageFileName)
    fun filePreviewPresentInStorage() = storageService.fileExists(storageBucketName, storageFileNamePreview)
    fun filePresentInFilesystem() = try {
        File(pathToFile).exists()
    } catch (_: Exception) {
        false
    }
    fun executeActionToCreate() { actionToCreate() }
}