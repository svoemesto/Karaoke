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
    val karaokeFileActions: MutableList<KaraokeFileAction> = mutableListOf()
): KaraokeStorage {

    fun getAction(type: KaraokeFileActionType, location: KaraokeFileTypeLocations): () -> Unit {
        return karaokeFileActions.firstOrNull { it.type == type && it.location == location }?.action ?: {}
    }
    @Suppress("unused")
    fun setAction(type: KaraokeFileActionType, location: KaraokeFileTypeLocations, action: () -> Unit) {
        karaokeFileActions.firstOrNull { it.type == type && it.location == location }?.let { oldAction -> karaokeFileActions.remove(oldAction) }
        karaokeFileActions.add(KaraokeFileAction(type = type, location = location, action = action))
    }
    fun filePresentInStorage() = storageService.fileExists(storageBucketName, storageFileName)
    @Suppress("unused")
    fun filePreviewPresentInStorage() = storageService.fileExists(storageBucketName, storageFileNamePreview)
    fun filePresentInFilesystem() = try {
        File(pathToFile).exists()
    } catch (_: Exception) {
        false
    }
    @Suppress("unused")
    fun executeAction(type: KaraokeFileActionType, location: KaraokeFileTypeLocations) {
        getAction(type = type, location = location)()
    }
}