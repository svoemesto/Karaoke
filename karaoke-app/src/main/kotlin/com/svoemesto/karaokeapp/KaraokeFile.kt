package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.KaraokeFileActionType.CREATE
import com.svoemesto.karaokeapp.KaraokeFileActionType.DELETE
import com.svoemesto.karaokeapp.KaraokeFileTypeLocations.LOCAL_FILESYSTEM
import com.svoemesto.karaokeapp.KaraokeFileTypeLocations.LOCAL_STORAGE
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.SongOutputFile
import com.svoemesto.karaokeapp.model.SongVersion
import com.svoemesto.karaokeapp.services.KaraokeStorage
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.properties.Delegates

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

    fun getActions(type: KaraokeFileActionType, location: KaraokeFileTypeLocations): List<() -> Unit> {
        return karaokeFileActions.firstOrNull { it.type == type && it.location == location }?.actions ?: emptyList()
    }
    @Suppress("unused")
    fun setAction(type: KaraokeFileActionType, location: KaraokeFileTypeLocations, actions: List<() -> Unit>) {
        karaokeFileActions.firstOrNull { it.type == type && it.location == location }?.let { oldKaraokeFileAction -> karaokeFileActions.remove(oldKaraokeFileAction) }
        karaokeFileActions.add(KaraokeFileAction(type = type, location = location, actions = actions))
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
    fun executeActions(type: KaraokeFileActionType, location: KaraokeFileTypeLocations) {
        getActions(type = type, location = location).forEach { action ->
            action()
        }
    }

    fun canBeCreateKaraokeFile(settings: Settings): KaraokeFileCanBeCreate {
        val karaokeFile = this
        with(settings) {
            return when (karaokeFile.type) {
                KaraokeFileType.AUDIO_SONG,
                KaraokeFileType.PICTURE_ALBUM,
                KaraokeFileType.PICTURE_AUTHOR -> {
                    KaraokeFileCanBeCreate(
                        canBeCreate = false,
                        reason = "Невозможно создать файл. Он должен существовать изначально."
                    )
                }

                KaraokeFileType.MP3_SONG,
                KaraokeFileType.AUDIO_ACCOMPANIMENT,
                KaraokeFileType.AUDIO_VOICE,
                KaraokeFileType.AUDIO_BASS,
                KaraokeFileType.AUDIO_DRUMS,
                KaraokeFileType.AUDIO_OTHER -> {
                    if (File(fileAbsolutePath).exists()) {
                        KaraokeFileCanBeCreate(
                            canBeCreate = true,
                            reason = "Файл может быть создан"
                        )
                    } else {
                        KaraokeFileCanBeCreate(
                            canBeCreate = false,
                            reason = "Файл не может быть создан. Должен существовать файл типа 'AUDIO_SONG'"
                        )
                    }
                }

                KaraokeFileType.MP3_ACCOMPANIMENT -> {
                    val parentKaraokeFileType = KaraokeFileType.AUDIO_ACCOMPANIMENT
                    if (File(newNoStemNameFlac).exists()) {
                        KaraokeFileCanBeCreate(
                            canBeCreate = true,
                            reason = "Файл может быть создан"
                        )
                    } else {
                        val waitingCreateProcessForFile = waitingCreateProcessForFile(
                            settings = settings,
                            karaokeFileType = parentKaraokeFileType
                        )
                        if (waitingCreateProcessForFile == null) {
                            KaraokeFileCanBeCreate(
                                canBeCreate = false,
                                reason = "Файл не может быть создан. Должен существовать файл типа '${parentKaraokeFileType.name}' или задание на его создание."
                            )
                        } else {
                            KaraokeFileCanBeCreate(
                                canBeCreate = true,
                                reason = "Файл может быть создан, т.к. есть задание на создание файла типа '${parentKaraokeFileType.name}'",
                                threadId = waitingCreateProcessForFile.threadId,
                                processPriority = waitingCreateProcessForFile.priority
                            )
                        }

                    }
                }

                KaraokeFileType.PICTURE_ALBUM_PREVIEW -> {
                    val parentKaraokeFileType = KaraokeFileType.PICTURE_ALBUM
                    if (File(pathToFileLogoAlbum).exists()) {
                        KaraokeFileCanBeCreate(
                            canBeCreate = true,
                            reason = "Файл может быть создан"
                        )
                    } else {
                        KaraokeFileCanBeCreate(
                            canBeCreate = false,
                            reason = "Файл не может быть создан. Должен существовать файл типа '${parentKaraokeFileType.name}'"
                        )
                    }
                }

                KaraokeFileType.PICTURE_AUTHOR_PREVIEW -> {
                    val parentKaraokeFileType = KaraokeFileType.PICTURE_AUTHOR
                    if (File(pathToFileLogoAuthor).exists()) {
                        KaraokeFileCanBeCreate(
                            canBeCreate = true,
                            reason = "Файл может быть создан"
                        )
                    } else {
                        KaraokeFileCanBeCreate(
                            canBeCreate = false,
                            reason = "Файл не может быть создан. Должен существовать файл типа '${parentKaraokeFileType.name}'"
                        )
                    }
                }

                KaraokeFileType.PICTURE_PUBLICATION -> {
                    KaraokeFileCanBeCreate(
                        canBeCreate = true,
                        reason = "Файл может быть создан"
                    )
                }
                KaraokeFileType.PICTURE_SONGVERSION -> {
                    KaraokeFileCanBeCreate(
                        canBeCreate = true,
                        reason = "Файл может быть создан"
                    )
                }
                KaraokeFileType.VIDEO_SONGVERSION_1080P -> {
                    if (settings.idStatus >= 2) {
                        KaraokeFileCanBeCreate(
                            canBeCreate = true,
                            reason = "Файл может быть создан"
                        )
                    } else {
                        KaraokeFileCanBeCreate(
                            canBeCreate = false,
                            reason = "Файл не может быть создан. ID статуса песни должен быть >= 2"
                        )
                    }

                }

                KaraokeFileType.VIDEO_SONGVERSION_720P -> {
                    val parentKaraokeFileType = KaraokeFileType.VIDEO_SONGVERSION_1080P
                    when (karaokeFile.songVersion) {
                        SongVersion.KARAOKE -> {
                            if (File(pathToFileKaraoke).exists()) {
                                Pair(true, "Файл может быть создан")
                                KaraokeFileCanBeCreate(
                                    canBeCreate = true,
                                    reason = "Файл может быть создан"
                                )
                            } else {
                                val waitingCreateProcessForFile = waitingCreateProcessForFile(
                                    settings = settings,
                                    karaokeFileType = parentKaraokeFileType,
                                    karaokeFileSongVersion = karaokeFile.songVersion
                                )
                                if (waitingCreateProcessForFile == null) {
                                    KaraokeFileCanBeCreate(
                                        canBeCreate = false,
                                        reason = "Файл не может быть создан. Должен существовать файл типа '${parentKaraokeFileType.name}' для версии '${karaokeFile.songVersion.name}' или задание на его создание."
                                    )
                                } else {
                                    KaraokeFileCanBeCreate(
                                        canBeCreate = true,
                                        reason = "Файл может быть создан, т.к. есть задание на создание файла типа '${parentKaraokeFileType.name}' для версии '${karaokeFile.songVersion.name}'",
                                        threadId = waitingCreateProcessForFile.threadId,
                                        processPriority = waitingCreateProcessForFile.priority
                                    )
                                }
                            }
                        }
                        SongVersion.LYRICS -> {
                            if (File(pathToFileLyrics).exists()) {
                                Pair(true, "Файл может быть создан")
                                KaraokeFileCanBeCreate(
                                    canBeCreate = true,
                                    reason = "Файл может быть создан"
                                )
                            } else {
                                val waitingCreateProcessForFile = waitingCreateProcessForFile(
                                    settings = settings,
                                    karaokeFileType = parentKaraokeFileType,
                                    karaokeFileSongVersion = karaokeFile.songVersion
                                )
                                if (waitingCreateProcessForFile == null) {
                                    KaraokeFileCanBeCreate(
                                        canBeCreate = false,
                                        reason = "Файл не может быть создан. Должен существовать файл типа '${parentKaraokeFileType.name}' для версии '${karaokeFile.songVersion.name}' или задание на его создание."
                                    )
                                } else {
                                    KaraokeFileCanBeCreate(
                                        canBeCreate = true,
                                        reason = "Файл может быть создан, т.к. есть задание на создание файла типа '${parentKaraokeFileType.name}' для версии '${karaokeFile.songVersion.name}'",
                                        threadId = waitingCreateProcessForFile.threadId,
                                        processPriority = waitingCreateProcessForFile.priority
                                    )
                                }
                            }
                        }
                        SongVersion.CHORDS,
                        SongVersion.TABS,
                        null -> {
                            KaraokeFileCanBeCreate(
                                canBeCreate = false,
                                reason = "Файл не может быть создан отдельно, только в процессе работы с проектом."
                            )
                        }
                    }
                }

                KaraokeFileType.PROJECT_ALL_RUN,
                KaraokeFileType.PROJECT_ALL_WO_LYRICS_RUN,
                KaraokeFileType.PROJECT_SONGVERSION_RUN,
                KaraokeFileType.PROJECT_SONGVERSION_MLT,
                KaraokeFileType.PROJECT_SONGVERSION_KDENLIVE,
                KaraokeFileType.PROJECT_SONGVERSION_TXT -> {
                    KaraokeFileCanBeCreate(
                        canBeCreate = false,
                        reason = "Файл не может быть создан отдельно, только в процессе работы с проектом."
                    )
                }
            }
        }

    }

    companion object {

        fun waitingCreateProcessForFile(
            settings: Settings,
            karaokeFileType: KaraokeFileType,
            karaokeFileSongVersion: SongVersion? = null
        ): KaraokeProcess? {
            with(settings) {
                return when (karaokeFileType) {
                    KaraokeFileType.VIDEO_SONGVERSION_1080P -> {
                        val whereArgs = when (karaokeFileSongVersion) {
                            SongVersion.KARAOKE -> mapOf(
                                "settings_id" to this.id.toString(),
                                "process_status" to "WAITING",
                                "process_type" to KaraokeProcessTypes.MELT_KARAOKE.name
                            )
                            SongVersion.LYRICS ->  mapOf(
                                "settings_id" to this.id.toString(),
                                "process_status" to "WAITING",
                                "process_type" to KaraokeProcessTypes.MELT_LYRICS.name
                            )
                            SongVersion.CHORDS ->  mapOf(
                                "settings_id" to this.id.toString(),
                                "process_status" to "WAITING",
                                "process_type" to KaraokeProcessTypes.MELT_CHORDS.name
                            )
                            SongVersion.TABS ->  mapOf(
                                "settings_id" to this.id.toString(),
                                "process_status" to "WAITING",
                                "process_type" to KaraokeProcessTypes.MELT_TABS.name
                            )
                            null -> null
                        }
                        whereArgs?.let {
                            KaraokeProcess.loadList(args = whereArgs, database = this.database).firstOrNull()
                        } ?: null
                    }
                    KaraokeFileType.VIDEO_SONGVERSION_720P -> {
                        val whereArgs = when (karaokeFileSongVersion) {
                            SongVersion.KARAOKE -> {
                                mapOf(
                                    "settings_id" to this.id.toString(),
                                    "process_status" to "WAITING",
                                    "process_type" to KaraokeProcessTypes.FF_720_KAR.name
                                )
                            }
                            SongVersion.LYRICS -> {
                                mapOf(
                                    "settings_id" to this.id.toString(),
                                    "process_status" to "WAITING",
                                    "process_type" to KaraokeProcessTypes.FF_720_LYR.name
                                )
                            }
                            SongVersion.CHORDS,
                            SongVersion.TABS,
                            null -> null
                        }
                        whereArgs?.let {
                            KaraokeProcess.loadList(args = whereArgs, database = this.database).firstOrNull()
                        } ?: null
                    }

                    KaraokeFileType.AUDIO_ACCOMPANIMENT,
                    KaraokeFileType.AUDIO_VOICE -> {
                        val whereArgsDemux2 = mapOf(
                            "settings_id" to this.id.toString(),
                            "process_status" to "WAITING",
                            "process_type" to KaraokeProcessTypes.DEMUCS2.name
                        )

                        val processDemux2 = KaraokeProcess.loadList(args = whereArgsDemux2, database = this.database).firstOrNull()

                        if (processDemux2 == null) {
                            val whereArgsDemux5 = mapOf(
                                "settings_id" to this.id.toString(),
                                "process_status" to "WAITING",
                                "process_type" to KaraokeProcessTypes.DEMUCS5.name
                            )
                            KaraokeProcess.loadList(args = whereArgsDemux5, database = this.database).firstOrNull()
                        } else processDemux2
                    }

                    KaraokeFileType.AUDIO_BASS,
                    KaraokeFileType.AUDIO_DRUMS,
                    KaraokeFileType.AUDIO_OTHER -> {
                        val whereArgsDemux5 = mapOf(
                            "settings_id" to this.id.toString(),
                            "process_status" to "WAITING",
                            "process_type" to KaraokeProcessTypes.DEMUCS5.name
                        )
                        KaraokeProcess.loadList(args = whereArgsDemux5, database = this.database).firstOrNull()
                    }

                    KaraokeFileType.MP3_SONG -> {
                        val whereArgs = mapOf(
                            "settings_id" to this.id.toString(),
                            "process_status" to "WAITING",
                            "process_type" to KaraokeProcessTypes.FF_MP3_LYR.name
                        )
                        KaraokeProcess.loadList(args = whereArgs, database = this.database).firstOrNull()
                    }

                    KaraokeFileType.MP3_ACCOMPANIMENT -> {
                        val whereArgs = mapOf(
                            "settings_id" to this.id.toString(),
                            "process_status" to "WAITING",
                            "process_type" to KaraokeProcessTypes.FF_MP3_KAR.name
                        )
                        KaraokeProcess.loadList(args = whereArgs, database = this.database).firstOrNull()
                    }

                    else -> null
                }
            }

        }

        fun getKaraokeFiles(settings: Settings): List<KaraokeFile> {
            val result: MutableList<KaraokeFile> = mutableListOf()
            with(settings) {
                KaraokeFileType.entries.forEach { karaokeFileType ->
                    val karaokeFileActions: MutableList<KaraokeFileAction> = mutableListOf()
                    when (karaokeFileType.karaokeFileTypeFor) {
                        KaraokeFileTypeFor.SONG -> {

                            lateinit var pathToFile: String
                            var canBe by Delegates.notNull<Boolean>()
                            lateinit var storageFileName: String

                            when(karaokeFileType) {

                                KaraokeFileType.AUDIO_SONG -> {
                                    pathToFile = fileAbsolutePath
                                    canBe = true
                                    storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_STORAGE,
                                            actions = listOf {
                                                {
                                                    storageService.uploadFile(
                                                        bucketName = storageBucketName,
                                                        fileName = storageFileName,
                                                        pathToFileOnDisk = pathToFile
                                                    )
                                                }
                                            }
                                        )
                                    )
                                }
                                KaraokeFileType.MP3_SONG -> {
                                    pathToFile = pathToFileMP3Lyrics
                                    canBe = true
                                    storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                    val parentKaraokeFileType = KaraokeFileType.AUDIO_ACCOMPANIMENT
                                    val waitingCreateProcessForFile = waitingCreateProcessForFile(
                                        settings = settings,
                                        karaokeFileType = parentKaraokeFileType
                                    )
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_FILESYSTEM,
                                            actions = listOf {
                                                createProcessMp3Song(
                                                    threadId = waitingCreateProcessForFile?.threadId,
                                                    prior = waitingCreateProcessForFile?.priority
                                                )
                                            }
                                        )
                                    )
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_STORAGE,
                                            actions = listOf {
                                                {
                                                    storageService.uploadFile(
                                                        bucketName = storageBucketName,
                                                        fileName = storageFileName,
                                                        pathToFileOnDisk = pathToFile
                                                    )
                                                }
                                            }
                                        )
                                    )
                                }
                                KaraokeFileType.AUDIO_ACCOMPANIMENT -> {
                                    pathToFile = newNoStemNameFlac
                                    canBe = true
                                    storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"

                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_FILESYSTEM,
                                            actions = listOf{ createProcessDemux2(threadId = null, prior = null) }
                                        )
                                    )
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_STORAGE,
                                            actions = listOf {
                                                {
                                                    storageService.uploadFile(
                                                        bucketName = storageBucketName,
                                                        fileName = storageFileName,
                                                        pathToFileOnDisk = pathToFile
                                                    )
                                                }
                                            }
                                        )
                                    )
                                }
                                KaraokeFileType.MP3_ACCOMPANIMENT -> {
                                    pathToFile = pathToFileMP3Karaoke
                                    canBe = true
                                    storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                    val parentKaraokeFileType = KaraokeFileType.AUDIO_ACCOMPANIMENT
                                    val waitingCreateProcessForFile = waitingCreateProcessForFile(
                                        settings = settings,
                                        karaokeFileType = parentKaraokeFileType
                                    )
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_FILESYSTEM,
                                            actions = listOf{
                                                createProcessMp3Accompaniment(
                                                    threadId = waitingCreateProcessForFile?.threadId,
                                                    prior = waitingCreateProcessForFile?.priority
                                                )
                                            }
                                        )
                                    )
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_STORAGE,
                                            actions = listOf {
                                                {
                                                    storageService.uploadFile(
                                                        bucketName = storageBucketName,
                                                        fileName = storageFileName,
                                                        pathToFileOnDisk = pathToFile
                                                    )
                                                }
                                            }
                                        )
                                    )
                                }
                                KaraokeFileType.AUDIO_VOICE -> {
                                    pathToFile = vocalsNameFlac
                                    canBe = true
                                    storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_FILESYSTEM,
                                            actions = listOf{ createProcessDemux2(threadId = null, prior = null) }
                                        )
                                    )
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_STORAGE,
                                            actions = listOf {
                                                {
                                                    storageService.uploadFile(
                                                        bucketName = storageBucketName,
                                                        fileName = storageFileName,
                                                        pathToFileOnDisk = pathToFile
                                                    )
                                                }
                                            }
                                        )
                                    )
                                }
                                KaraokeFileType.AUDIO_BASS -> {
                                    pathToFile = bassNameFlac
                                    canBe = (hasChords || hasMelody)
                                    storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_FILESYSTEM,
                                            actions = listOf{ createProcessDemux5(threadId = null, prior = null) }
                                        )
                                    )
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_STORAGE,
                                            actions = listOf {
                                                {
                                                    storageService.uploadFile(
                                                        bucketName = storageBucketName,
                                                        fileName = storageFileName,
                                                        pathToFileOnDisk = pathToFile
                                                    )
                                                }
                                            }
                                        )
                                    )
                                }
                                KaraokeFileType.AUDIO_DRUMS -> {
                                    pathToFile = drumsNameFlac
                                    canBe = (hasChords || hasMelody)
                                    storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_FILESYSTEM,
                                            actions = listOf{ createProcessDemux5(threadId = null, prior = null) }
                                        )
                                    )
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_STORAGE,
                                            actions = listOf {
                                                {
                                                    storageService.uploadFile(
                                                        bucketName = storageBucketName,
                                                        fileName = storageFileName,
                                                        pathToFileOnDisk = pathToFile
                                                    )
                                                }
                                            }
                                        )
                                    )
                                }
                                KaraokeFileType.AUDIO_OTHER -> {
                                    pathToFile = otherNameFlac
                                    canBe = (hasChords || hasMelody)
                                    storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_FILESYSTEM,
                                            actions = listOf{ createProcessDemux5(threadId = null, prior = null) }
                                        )
                                    )
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_STORAGE,
                                            actions = listOf {
                                                {
                                                    storageService.uploadFile(
                                                        bucketName = storageBucketName,
                                                        fileName = storageFileName,
                                                        pathToFileOnDisk = pathToFile
                                                    )
                                                }
                                            }
                                        )
                                    )
                                }
                                KaraokeFileType.PICTURE_ALBUM -> {
                                    pathToFile = pathToFileLogoAlbum
                                    canBe = true
                                    storageFileName = "$author/$year - $album/$author - $year - $album${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_STORAGE,
                                            actions = listOf {
                                                {
                                                    storageService.uploadFile(
                                                        bucketName = storageBucketName,
                                                        fileName = storageFileName,
                                                        pathToFileOnDisk = pathToFile
                                                    )
                                                }
                                            }
                                        )
                                    )
                                }
                                KaraokeFileType.PICTURE_AUTHOR -> {
                                    pathToFile = pathToFileLogoAuthor
                                    canBe = true
                                    storageFileName = "$author/$author${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_STORAGE,
                                            actions = listOf {
                                                {
                                                    storageService.uploadFile(
                                                        bucketName = storageBucketName,
                                                        fileName = storageFileName,
                                                        pathToFileOnDisk = pathToFile
                                                    )
                                                }
                                            }
                                        )
                                    )
                                }
                                KaraokeFileType.PICTURE_ALBUM_PREVIEW -> {
                                    pathToFile = ""
                                    canBe = true
                                    storageFileName = "$author/$year - $album/$author - $year - $album${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_STORAGE,
                                            actions = listOf {
                                                if (pathToFileLogoAlbum != "") {
                                                    val pictureBites = File(pathToFileLogoAlbum).inputStream().readAllBytes()
                                                    val bi = ImageIO.read(ByteArrayInputStream(pictureBites))
                                                    val previewBi = resizeBufferedImage(bi, newW = 50, newH = 50)
                                                    val iosPreview = ByteArrayOutputStream()
                                                    ImageIO.write(previewBi, "png", iosPreview)
                                                    val bais = ByteArrayInputStream(iosPreview.toByteArray())
                                                    storageService.uploadFile(
                                                        bucketName = storageBucketName,
                                                        fileName = storageFileName,
                                                        file = bais,
                                                        size = bais.available().toLong()
                                                    )
                                                }
                                            }
                                        )
                                    )
                                }
                                KaraokeFileType.PICTURE_AUTHOR_PREVIEW -> {
                                    pathToFile = ""
                                    canBe = true
                                    storageFileName = "$author/$author${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_STORAGE,
                                            actions = listOf {
                                                if (pathToFileLogoAuthor != "") {
                                                    val pictureBites = File(pathToFileLogoAuthor).inputStream().readAllBytes()
                                                    val bi = ImageIO.read(ByteArrayInputStream(pictureBites))
                                                    val previewBi = resizeBufferedImage(bi, newW = 125, newH = 50)
                                                    val iosPreview = ByteArrayOutputStream()
                                                    ImageIO.write(previewBi, "png", iosPreview)
                                                    val bais = ByteArrayInputStream(iosPreview.toByteArray())
                                                    storageService.uploadFile(
                                                        bucketName = storageBucketName,
                                                        fileName = storageFileName,
                                                        file = bais,
                                                        size = bais.available().toLong()
                                                    )
                                                }
                                            }
                                        )
                                    )
                                }
                                KaraokeFileType.PICTURE_PUBLICATION -> {
                                    pathToFile = ""
                                    canBe = false
                                    storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                }
                                KaraokeFileType.PICTURE_SONGVERSION -> {
                                    pathToFile = ""
                                    canBe = false
                                    storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                }
                                KaraokeFileType.VIDEO_SONGVERSION_1080P -> {
                                    pathToFile = ""
                                    canBe = false
                                    storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                }
                                KaraokeFileType.VIDEO_SONGVERSION_720P -> {
                                    pathToFile = ""
                                    canBe = false
                                    storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                }
                                KaraokeFileType.PROJECT_ALL_RUN -> {
                                    pathToFile = getOutputFilename(SongOutputFile.RUNALL, SongVersion.LYRICS).replace("[lyrics]","[ALL]")
                                    canBe = (idStatus >= 3 && idStatus < 6)
                                    storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                }
                                KaraokeFileType.PROJECT_ALL_WO_LYRICS_RUN -> {
                                    pathToFile = getOutputFilename(SongOutputFile.RUNALL, SongVersion.LYRICS).replace("[lyrics]","[ALLwoLYRICS]")
                                    canBe = (idStatus >= 3 && idStatus < 6)
                                    storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                }
                                KaraokeFileType.PROJECT_SONGVERSION_RUN -> {
                                    pathToFile = ""
                                    canBe = false
                                    storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                }
                                KaraokeFileType.PROJECT_SONGVERSION_MLT -> {
                                    pathToFile = ""
                                    canBe = false
                                    storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                }
                                KaraokeFileType.PROJECT_SONGVERSION_KDENLIVE -> {
                                    pathToFile = ""
                                    canBe = false
                                    storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                }
                                KaraokeFileType.PROJECT_SONGVERSION_TXT -> {
                                    pathToFile = ""
                                    canBe = false
                                    storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                }

                            }

                            if (pathToFile != "") {

                                karaokeFileActions.add(
                                    KaraokeFileAction(
                                        type = DELETE,
                                        location = LOCAL_FILESYSTEM,
                                        actions = listOf{
                                            if (File(pathToFile).exists()) {
                                                val folder = File(pathToFile).parent
                                                println("Удаляем файл: '$pathToFile'")
                                                runCommand(args = listOf("rm", "-f", pathToFile))
                                                if (Files.list(Path(folder)).findFirst().isEmpty) {
                                                    Files.deleteIfExists(Path(folder))
                                                }
                                            }
                                        }
                                    )
                                )

                                karaokeFileType.symlinks.forEachIndexed { indexSymlink, symlink ->

                                    val pathToSymlinkFolder = "${this.rootFolder}/${symlink.folder}"
                                    val symlinkFileName = if (symlink.name == "") File(pathToFile).name else symlink.name
                                    val pathToSymlinkFile = "$pathToSymlinkFolder/$symlinkFileName"

                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = DELETE,
                                            location = LOCAL_FILESYSTEM,
                                            actions = listOf{
                                                val symlinkFolder = File(pathToSymlinkFile).parent

                                                if (File(pathToSymlinkFile).exists()) {
                                                    println("Удаляем symlink: '$pathToFile'")
                                                    runCommand(args = listOf("rm", "-f", pathToSymlinkFile))
                                                    if (File(symlinkFolder).exists()) {
                                                        if (Files.list(Path(symlinkFolder)).findFirst().isEmpty) {
                                                            Files.deleteIfExists(Path(symlinkFolder))
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    )
                                }
                            }

                            result.add(
                                KaraokeFile(
                                    type = karaokeFileType,
                                    songVersion = null,
                                    storageService = storageService,
                                    storageBucketName = storageBucketName,
                                    storageFileName = storageFileName,
                                    pathToFile = pathToFile.rightFileName(),
                                    canBe = canBe,
                                    karaokeFileActions = karaokeFileActions
                                )
                            )
                        }

                        KaraokeFileTypeFor.PLATFORM -> {
                            KaraokePlatform.entries.forEach { karaokePlatform ->
                                val pathToFile = if (karaokePlatform.forAllVersions) {
                                    val subFolder = if (karaokeFileType.karaokeFileTypeKind == KaraokeFileTypeKind.PROJECT) {
                                        "done_projects"
                                    } else {
                                        "done_files"
                                    }
                                    "$rootFolder/$subFolder/$fileName${karaokePlatform.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                } else ""

                                val canBe = if (karaokeFileType.karaokeFileTypeKind == KaraokeFileTypeKind.PROJECT) {
                                    (idStatus >= 3 && idStatus < 6)
                                } else {
                                    (idStatus >= 6) && (!karaokePlatform.onAirPublications || !onAir)
                                }

                                if (karaokePlatform.forAllVersions) {
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_FILESYSTEM,
                                            actions = listOf {
                                                karaokePlatform.actionToCreatePicture(
                                                    settings = this,
                                                    pathToFile = pathToFile
                                                )
                                            }
                                        )
                                    )
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = CREATE,
                                            location = LOCAL_STORAGE,
                                            actions = listOf {
                                                storageService.uploadFile(
                                                    bucketName = storageBucketName,
                                                    fileName = "$storageFileName${karaokePlatform.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}",
                                                    pathToFileOnDisk = pathToFile
                                                )
                                            }
                                        )
                                    )
                                }

                                if (pathToFile != "") {

                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = DELETE,
                                            location = LOCAL_FILESYSTEM,
                                            actions = listOf{
                                                if (File(pathToFile).exists()) {
                                                    val folder = File(pathToFile).parent
                                                    println("Удаляем файл: '$pathToFile'")
                                                    runCommand(args = listOf("rm", "-f", pathToFile))
                                                    if (Files.list(Path(folder)).findFirst().isEmpty) {
                                                        Files.deleteIfExists(Path(folder))
                                                    }
                                                }
                                            }
                                        )
                                    )

                                    karaokeFileType.symlinks.forEachIndexed { indexSymlink, symlink ->

                                        val pathToSymlinkFolder = "${this.rootFolder}/${symlink.folder}"
                                        val symlinkFileName = if (symlink.name == "") File(pathToFile).name else symlink.name
                                        val pathToSymlinkFile = "$pathToSymlinkFolder/$symlinkFileName"

                                        karaokeFileActions.add(
                                            KaraokeFileAction(
                                                type = DELETE,
                                                location = LOCAL_FILESYSTEM,
                                                actions = listOf{
                                                    val symlinkFolder = File(pathToSymlinkFile).parent

                                                    if (File(pathToSymlinkFile).exists()) {
                                                        runCommand(args = listOf("rm", "-f", pathToSymlinkFile))
                                                        if (File(symlinkFolder).exists()) {
                                                            if (Files.list(Path(symlinkFolder)).findFirst().isEmpty) {
                                                                Files.deleteIfExists(Path(symlinkFolder))
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                        )
                                    }
                                }

                                result.add(
                                    KaraokeFile(
                                        type = karaokeFileType,
                                        songVersion = null,
                                        storageService = storageService,
                                        storageBucketName = storageBucketName,
                                        storageFileName = "$storageFileName${karaokePlatform.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}",
                                        karaokePlatform = karaokePlatform,
                                        pathToFile = pathToFile.rightFileName(),
                                        canBe = canBe,
                                        karaokeFileActions = karaokeFileActions
                                    )
                                )
                            }
                        }

                        KaraokeFileTypeFor.SONGVERSION -> {
                            SongVersion.entries.forEach { songVersion ->

                                val pathToFile = when (karaokeFileType) {
                                    KaraokeFileType.VIDEO_SONGVERSION_720P -> {
                                        when(songVersion) {
                                            SongVersion.KARAOKE -> pathToFile720Karaoke
                                            SongVersion.LYRICS -> pathToFile720Lyrics
                                            SongVersion.CHORDS,
                                            SongVersion.TABS -> ""
                                        }
                                    }
                                    else -> {
                                        val subFolder = if (karaokeFileType.karaokeFileTypeKind == KaraokeFileTypeKind.PROJECT) {
                                            "done_projects"
                                        } else {
                                            "done_files"
                                        }
                                        "$rootFolder/$subFolder/$fileName${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                                    }
                                }

                                val canBe = when(songVersion) {
                                    SongVersion.KARAOKE,
                                    SongVersion.LYRICS -> {
                                        if (karaokeFileType.karaokeFileTypeKind == KaraokeFileTypeKind.PROJECT) {
                                            (idStatus >= 3 && idStatus < 6)
                                        } else {
                                            (idStatus >= 6)
                                        }
                                    }
                                    SongVersion.CHORDS -> {
                                        if (karaokeFileType.karaokeFileTypeKind == KaraokeFileTypeKind.PROJECT) {
                                            (idStatus >= 3 && idStatus < 6)
                                        } else {
                                            (idStatus >= 6) && hasChords
                                        }
                                    }
                                    SongVersion.TABS -> {
                                        if (karaokeFileType.karaokeFileTypeKind == KaraokeFileTypeKind.PROJECT) {
                                            (idStatus >= 3 && idStatus < 6)
                                        } else {
                                            (idStatus >= 6) && hasMelody
                                        }
                                    }
                                }

                                when(karaokeFileType) {

                                    KaraokeFileType.AUDIO_SONG,
                                    KaraokeFileType.MP3_SONG,
                                    KaraokeFileType.AUDIO_ACCOMPANIMENT,
                                    KaraokeFileType.MP3_ACCOMPANIMENT,
                                    KaraokeFileType.AUDIO_VOICE,
                                    KaraokeFileType.AUDIO_BASS,
                                    KaraokeFileType.AUDIO_DRUMS,
                                    KaraokeFileType.AUDIO_OTHER,
                                    KaraokeFileType.PICTURE_ALBUM,
                                    KaraokeFileType.PICTURE_AUTHOR,
                                    KaraokeFileType.PICTURE_ALBUM_PREVIEW,
                                    KaraokeFileType.PICTURE_AUTHOR_PREVIEW,
                                    KaraokeFileType.PICTURE_PUBLICATION -> {}

                                    KaraokeFileType.PICTURE_SONGVERSION -> {
                                        karaokeFileActions.add(
                                            KaraokeFileAction(
                                                type = CREATE,
                                                location = LOCAL_FILESYSTEM,
                                                actions = listOf { { createSongPicture(settings = this, songVersion = songVersion) } }
                                            )
                                        )
                                        karaokeFileActions.add(
                                            KaraokeFileAction(
                                                type = CREATE,
                                                location = LOCAL_STORAGE,
                                                actions = listOf {
                                                    storageService.uploadFile(
                                                        bucketName = storageBucketName,
                                                        fileName = "$storageFileName${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}",
                                                        pathToFileOnDisk = pathToFile
                                                    )
                                                }
                                            )
                                        )
                                    }
                                    KaraokeFileType.VIDEO_SONGVERSION_1080P -> {
                                        karaokeFileActions.add(
                                            KaraokeFileAction(
                                                type = CREATE,
                                                location = LOCAL_FILESYSTEM,
                                                actions = listOf { { createKaraoke(songVersion = songVersion) } }
                                            )
                                        )
                                        karaokeFileActions.add(
                                            KaraokeFileAction(
                                                type = CREATE,
                                                location = LOCAL_STORAGE,
                                                actions = listOf {
                                                    storageService.uploadFile(
                                                        bucketName = storageBucketName,
                                                        fileName = "$storageFileName${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}",
                                                        pathToFileOnDisk = pathToFile
                                                    )
                                                }
                                            )
                                        )
                                    }
                                    KaraokeFileType.VIDEO_SONGVERSION_720P -> {
                                        karaokeFileActions.add(
                                            KaraokeFileAction(
                                                type = CREATE,
                                                location = LOCAL_FILESYSTEM,
                                                actions = listOf { { createProcessVideo720(songVersion = songVersion) } }
                                            )
                                        )
                                        karaokeFileActions.add(
                                            KaraokeFileAction(
                                                type = CREATE,
                                                location = LOCAL_STORAGE,
                                                actions = listOf {
                                                    storageService.uploadFile(
                                                        bucketName = storageBucketName,
                                                        fileName = "$storageFileName${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}",
                                                        pathToFileOnDisk = pathToFile
                                                    )
                                                }
                                            )
                                        )
                                    }

                                    KaraokeFileType.PROJECT_ALL_RUN,
                                    KaraokeFileType.PROJECT_ALL_WO_LYRICS_RUN,
                                    KaraokeFileType.PROJECT_SONGVERSION_RUN,
                                    KaraokeFileType.PROJECT_SONGVERSION_MLT,
                                    KaraokeFileType.PROJECT_SONGVERSION_KDENLIVE,
                                    KaraokeFileType.PROJECT_SONGVERSION_TXT -> {}
                                }

                                if (pathToFile != "") {

//                                    println("Для типа файла $karaokeFileType версии файла $songVersion добавляем удаление файла '$pathToFile'")
                                    karaokeFileActions.add(
                                        KaraokeFileAction(
                                            type = DELETE,
                                            location = LOCAL_FILESYSTEM,
                                            actions = listOf{
                                                println("DELETE LOCAL_FILESYSTEM ${songVersion.name}: '$pathToFile'")
                                                if (File(pathToFile).exists()) {
                                                    val folder = File(pathToFile).parent
                                                    println("Удаляем файл: '$pathToFile'")
                                                    runCommand(args = listOf("rm", "-f", pathToFile))
                                                    if (Files.list(Path(folder)).findFirst().isEmpty) {
                                                        Files.deleteIfExists(Path(folder))
                                                    }
                                                }
                                            }
                                        )
                                    )

                                    karaokeFileType.symlinks.forEachIndexed { indexSymlink, symlink ->

                                        val pathToSymlinkFolder = "${this.rootFolder}/${symlink.folder}"
                                        val symlinkFileName = if (symlink.name == "") File(pathToFile).name else symlink.name
                                        val pathToSymlinkFile = "$pathToSymlinkFolder/$symlinkFileName"

                                        karaokeFileActions.add(
                                            KaraokeFileAction(
                                                type = DELETE,
                                                location = LOCAL_FILESYSTEM,
                                                actions = listOf{
                                                    val symlinkFolder = File(pathToSymlinkFile).parent

                                                    if (File(pathToSymlinkFile).exists()) {
                                                        runCommand(args = listOf("rm", "-f", pathToSymlinkFile))
                                                        if (File(symlinkFolder).exists()) {
                                                            if (Files.list(Path(symlinkFolder)).findFirst().isEmpty) {
                                                                Files.deleteIfExists(Path(symlinkFolder))
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                        )
                                    }
                                }

                                result.add(
                                    KaraokeFile(
                                        type = karaokeFileType,
                                        storageService = storageService,
                                        storageBucketName = storageBucketName,
                                        storageFileName = "$storageFileName${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}",
                                        songVersion = songVersion,
                                        pathToFile = pathToFile.rightFileName(),
                                        canBe = canBe,
                                        karaokeFileActions = karaokeFileActions
                                    )
                                )
                            }
                        }
                    }
                }
            }
            return result
        }


    }
}