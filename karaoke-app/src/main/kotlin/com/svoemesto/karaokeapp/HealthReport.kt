package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.HealthReportStatus.*
import com.svoemesto.karaokeapp.HealthReportType.CONSISTENCY_VIOLATION
import com.svoemesto.karaokeapp.HealthReportType.FILE_VIOLATION
import com.svoemesto.karaokeapp.KaraokeFileTypeLocations.*
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.SongOutputFile
import com.svoemesto.karaokeapp.model.SongVersion
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import java.io.File
import java.io.Serializable
import java.nio.file.Files
import kotlin.properties.Delegates

data class HealthReport(
    val settings: Settings,
    val description: String = "",
    val healthReportType: HealthReportType,
    val healthReportStatus: HealthReportStatus,
    val canResolve: Boolean = false,
    val problemText: String = "",
    val solutionText: String = "",
    val solutionActions: List<() -> Unit> = emptyList()
) {
    fun toDTO(): HealthReportDTO {
        return HealthReportDTO(
            settingsId = settings.id,
            description = description,
            healthReportTypeName = healthReportType.name,
            healthReportStatusName = healthReportStatus.name,
            color = healthReportStatus.color,
            canResolve = canResolve,
            problemText = problemText,
            solutionText = solutionText
        )
    }

    fun executeSolutionActions() = solutionActions.forEach { action -> action() }

    companion object {

        private fun actions(
            karaokeFileType: KaraokeFileType,
            settings: Settings,
            rootFolder: String,
            pathToFile: String,
            description: String,
            existsInLocalFileSystem: Boolean,
            canBe: Boolean,
            canResolve: Boolean,
            canCreate: Boolean,
            karaokeProcessTypesToCreate: KaraokeProcessTypes?,
            actionToCreate: () -> Unit,
            storageBucketName: String,
            storageFileName: String,
            location: KaraokeFileTypeLocations,
            inProgressOwnArgs: Map<String, String>,
            inProgressParentArgs: Map<String, String>,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient
        ): List<HealthReport> {

            return when (location) {
                LOCAL_FILESYSTEM -> actionsLocalFileSystem(
                    karaokeFileType = karaokeFileType,
                    settings = settings,
                    rootFolder = rootFolder,
                    pathToFile = pathToFile,
                    description = description,
                    existsInLocalFileSystem = existsInLocalFileSystem,
                    canBe = canBe,
                    willBeInLocation = karaokeFileType.willBeInFileSystem,
                    canResolve = canResolve,
                    canCreate = canCreate,
                    karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                    actionToCreate = actionToCreate,
                    storageBucketName = storageBucketName,
                    storageFileName = storageFileName,
                    inProgressOwnArgs = inProgressOwnArgs,
                    inProgressParentArgs = inProgressParentArgs,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient
                )
                LOCAL_STORAGE -> actionsLocalStorage(
                    karaokeFileType = karaokeFileType,
                    settings = settings,
                    rootFolder = rootFolder,
                    pathToFile = pathToFile,
                    description = description,
                    existsInLocalFileSystem = existsInLocalFileSystem,
                    canBe = canBe,
                    willBeInLocation = karaokeFileType.willBeInLocalStorage,
                    canResolve = canResolve,
                    canCreate = canCreate,
                    karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                    actionToCreate = actionToCreate,
                    storageBucketName = storageBucketName,
                    storageFileName = storageFileName,
                    inProgressOwnArgs = inProgressOwnArgs,
                    inProgressParentArgs = inProgressParentArgs,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient
                )
                REMOTE_STORAGE -> actionsRemoteStorage(
                    karaokeFileType = karaokeFileType,
                    settings = settings,
                    rootFolder = rootFolder,
                    pathToFile = pathToFile,
                    description = description,
                    existsInLocalFileSystem = existsInLocalFileSystem,
                    canBe = canBe,
                    willBeInLocation = karaokeFileType.willBeInRemoteStorage,
                    canResolve = canResolve,
                    canCreate = canCreate,
                    karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                    actionToCreate = actionToCreate,
                    storageBucketName = storageBucketName,
                    storageFileName = storageFileName,
                    inProgressOwnArgs = inProgressOwnArgs,
                    inProgressParentArgs = inProgressParentArgs,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient
                )
            }

        }

        private fun actionsLocalFileSystem(
            karaokeFileType: KaraokeFileType,
            settings: Settings,
            rootFolder: String,
            pathToFile: String,
            description: String,
            existsInLocalFileSystem: Boolean,
            canBe: Boolean,
            willBeInLocation: Boolean,
            canResolve: Boolean,
            canCreate: Boolean,
            karaokeProcessTypesToCreate: KaraokeProcessTypes?,
            actionToCreate: () -> Unit,
            storageBucketName: String,
            storageFileName: String,
            inProgressOwnArgs: Map<String, String>,
            inProgressParentArgs: Map<String, String>,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient
        ): List<HealthReport> {
            val result: MutableList<HealthReport> = mutableListOf()
            if (!willBeInLocation) return emptyList()

            /*
            Если должен быть (canBe):
                Если файл реально есть (existsInLocalFileSystem):
                    Если у файла должны быть симлинки:
                        Если симлинки реально есть:
                            Если симлинки исправны:
                                ОК
                            Если симлинки неисправны:
                                Задача на удаление симлинков
                                Задача на создание симлинков
                                ERROR
                        Если симлинков реально нет:
                            Задача на создание симлинков
                            ERROR
                    Если у файла не должны быть симлики:
                        ОК

                Если файла реально нет (!existsInLocalFileSystem)
                    Если возможно решить проблему (canResolve)
                        Есть задача в процессах (inProgressOwn)
                            IN_PROGRESS
                        Нет задачи в процессах (!inProgressOwn)
                            Возможно создать файл (canCreate)
                                Действия по созданию файла и симлинков
                                ERROR
                            Невозможно создать файл (canCreate)
                                Есть задача на создание родителя в процессах (inProgressParent)
                                    Действия по созданию файла и симлинков в очереди после родителя
                                    ERROR
                                Нет задачи на создание родителя в процессах (!inProgressParent)
                                    FATAL_ERROR
                    Если не возможно решить проблему (!canResolve)
                        FATAL_ERROR

            Если файла быть не должно (!canBe):
                Если файл реально есть (existsInLocalFileSystem)
                    Задача на удаление файла с симлинками
                    ERROR
                Если файла реально нет (!existsInLocalFileSystem)
                    Если у файла есть симлинки:
                        Задача на удаление симлинков
                        ERROR
                    Если у файла нет симлинков:
                        OK
             */

            val actions: MutableList<() -> Unit> = mutableListOf()
            lateinit var problemText: String
            lateinit var solutionText: String
            lateinit var healthReportStatus: HealthReportStatus
            var canBeResolved by Delegates.notNull<Boolean>()

            healthReportStatus = OK
            problemText = "Проблем нет"
            solutionText = ""

            canBeResolved = canResolve
            if (canBe) { // Файл должен быть
                if (existsInLocalFileSystem) { // Файл реально существует
                    if (karaokeFileType.symlinks.isNotEmpty()) { // Если у файла должны быть симлинки
                        karaokeFileType.symlinks.forEach { symlink ->
                            val pathToSymlinkFile = symlink.pathToSymlinkFile(rootFolder = rootFolder, pathToTargetFile = pathToFile)

                            var needToDelete = false
                            var needToCreate = false
                            var broken = false
                            val exists = symlink.exists(rootFolder = rootFolder, pathToTargetFile = pathToFile)
                            if (exists) { // Симлинк реально есть
                                broken = symlink.broken(rootFolder = rootFolder, pathToTargetFile = pathToFile)
                                if (broken) {
                                    needToDelete = true
                                    needToCreate = true
                                }
                            } else { // Симлинка нет
                                needToCreate = true
                            }

                            if (needToDelete) {
                                actions.add { println("actionsLocalFileSystem [Удаление симлинка] >>>") }
                                actions.add { actionToDeleteFileAndFolderIfFolderEmpty(pathToFile = pathToSymlinkFile) }
                                actions.add { println("actionsLocalFileSystem [Удаление симлинка] <<<") }
                            }

                            if (needToCreate) {
                                actions.add { println("actionsLocalFileSystem [Создание симлинка] >>>") }
                                actions.add { symlink.actionToCreate(rootFolder = rootFolder, pathToTargetFile = pathToFile) }
                                actions.add { println("actionsLocalFileSystem [Создание симлинка] <<<") }
                            }

                            if (needToDelete || needToCreate) {
                                healthReportStatus = ERROR
                                problemText = "Файл на диске есть, но есть проблемы с его симлинками"
                                solutionText = "Удаление битых симлинков, создание правильных симлинков '$pathToSymlinkFile' exists = $exists broken = $broken"
                            }

                        }
                    }

                } else { // Если файла реально нет (!existsInLocalFileSystem)

                    /*
                    Если возможно решить проблему (canResolve)
                        Есть задача в процессах (inProgressOwn)
                            IN_PROGRESS
                        Нет задачи в процессах (!inProgressOwn)
                            Возможно создать файл (canCreate)
                                Действия по созданию файла и симлинков
                                ERROR
                            Невозможно создать файл (!canCreate)
                                Есть задача на создание родителя в процессах (inProgressParent)
                                    Действия по созданию файла и симлинков в очереди после родителя
                                    ERROR
                                Нет задачи на создание родителя в процессах (!inProgressParent)
                                    Файл есть в локальном хранилище
                                        Восстановить файл из локального хранилища
                                        ERROR
                                    Файла нет в локальном хранилище
                                        Файл есть в удаленном хранилище
                                            Восстановить файл из удаленного хоанилища
                                            ERROR
                                        Файла нет в удаленном хранилище
                                            FATAL_ERROR
                    Если не возможно решить проблему (!canResolve)
                        Файл есть в локальном хранилище
                            Восстановить файл из локального хранилища
                            ERROR
                        Файла нет в локальном хранилище
                            Файл есть в удаленном хранилище
                                Восстановить файл из удаленного хоанилища
                                ERROR
                            Файла нет в удаленном хранилище
                                FATAL_ERROR
                    */

                    var tryRestoreFromStorage = false
                    if (canResolve) { // Если возможно решить проблему (canResolve)

                        val inProgressOwn = KaraokeProcess.loadList(args = inProgressOwnArgs, database = database).isNotEmpty()
                        if (inProgressOwn) { // Есть задача в процессах (inProgressOwn)

                            healthReportStatus = IN_PROGRESS
                            problemText = "Файл отсутствует на диске"
                            solutionText = "Уже есть задание на создание файла"

                        } else { // Нет задачи в процессах (!inProgressOwn)

                            var threadId = 0
                            var priority = 0
                            var needCreateProcessOrAction = true
                            if (!canCreate) { // Невозможно создать файл (!canCreate)
                                val inProgressParentProcess = if (inProgressParentArgs.isNotEmpty()) {
                                    KaraokeProcess.loadList(args = inProgressParentArgs, database = database)
                                } else emptyList()
                                val inProgressParent = inProgressParentProcess.isNotEmpty()
                                if (inProgressParent) { // Есть задача на создание родителя в процессах (inProgressParent)

                                    threadId = inProgressParentProcess.first().threadId
                                    priority = inProgressParentProcess.first().priority

                                } else { // Нет задачи на создание родителя в процессах (!inProgressParent)

                                    tryRestoreFromStorage = true

                                    needCreateProcessOrAction = false
                                    healthReportStatus = FATAL_ERROR
                                    problemText = "Файл отсутствует на диске"
                                    solutionText = "Невозможно автоматически решить эту проблему"

                                }
                            }
                            if (needCreateProcessOrAction) { // Если нужно создать процесс или выполнить действие

                                if (karaokeProcessTypesToCreate != null) { // Есть тип процесса - создаем процесс с заданным приоритетом и тредом
                                    solutionText = "Создание файла (process)"
                                    actions.add { println("actionsLocalFileSystem [$solutionText] >>>") }
                                    actions.add ( {
                                        KaraokeProcess.createProcess(
                                            settings = settings,
                                            action = karaokeProcessTypesToCreate,
                                            doWait = true,
                                            prior = priority,
                                            threadId = threadId
                                        )
                                    } )
                                    actions.add { println("actionsLocalFileSystem [$solutionText] <<<") }

                                } else { // Если нет типа процесса - создаем действие

                                    solutionText = "Создание файла (action)"
                                    actions.add { println("actionsLocalFileSystem [$solutionText] >>>") }
                                    actions.add ( actionToCreate )
                                    actions.add { println("actionsLocalFileSystem [$solutionText] <<<") }
                                }

                                healthReportStatus = ERROR
                                problemText = "Файл отсутствует на диске"

                            }
                        }

                    } else { // Если не возможно решить проблему (!canResolve)

                        tryRestoreFromStorage = true

                        healthReportStatus = FATAL_ERROR
                        problemText = "Файл отсутствует на диске"
                        solutionText = "Невозможно автоматически решить эту проблему"

                    }

                    if (tryRestoreFromStorage) {
                        if (storageService.fileExists(bucketName = storageBucketName, fileName = storageFileName)) { // Файл есть в локальном хранилище
                            healthReportStatus = ERROR
                            problemText = "Файл отсутствует на диске"
                            solutionText = "Восстановление файла из локального хранилища"
                            actions.add { println("actionsLocalFileSystem [$solutionText] >>>") }
                            actions.add ( {
                                storageService.downloadFile(bucketName = storageBucketName, fileName = storageFileName, pathToFileOnDisk = pathToFile)
                            } )
                            actions.add { println("actionsLocalFileSystem [$solutionText] <<<") }

                        } else { // Файла нет в локальном хранилище
                            if (storageApiClient.fileExists(bucketName = storageBucketName, fileName = storageFileName)) {  // Файл есть в удалённом хранилище
                                healthReportStatus = ERROR
                                problemText = "Файл отсутствует на диске"
                                solutionText = "Восстановление файла из удалённого хранилища"
                                actions.add { println("actionsLocalFileSystem [$solutionText] >>>") }
                                actions.add ( {
                                    storageApiClient.downloadFile(bucketName = storageBucketName, fileName = storageFileName, pathToFileOnDisk = pathToFile)
                                } )
                                actions.add { println("actionsLocalFileSystem [$solutionText] <<<") }
                            } else { // Файла нет в удалённом хранилище
                                healthReportStatus = FATAL_ERROR
                                problemText = "Файл отсутствует на диске"
                                solutionText = "Невозможно автоматически решить эту проблему"
                            }
                        }
                    }
                }
            } else { // Файла быть не должно
                if (existsInLocalFileSystem) { // Файл реально существует

                    // Проверить и удалить симлинки, если они есть
                    if (karaokeFileType.symlinks.isNotEmpty()) { // Если у файла должны быть симлинки
                        karaokeFileType.symlinks.forEach { symlink ->
                            val pathToSymlinkFile = symlink.pathToSymlinkFile(rootFolder = rootFolder, pathToTargetFile = pathToFile)
                            val needToDelete = symlink.exists(rootFolder = rootFolder, pathToTargetFile = pathToFile)

                            if (needToDelete) {
                                actions.add { println("actionsLocalFileSystem [Удаление симлинка] >>>") }
                                actions.add ( actionToDeleteFileAndFolderIfFolderEmpty(pathToFile = pathToSymlinkFile) )
                                actions.add { println("actionsLocalFileSystem [Удаление симлинка] <<<") }
                            }

                        }
                    }

                    // Удалить файл
                    val actionToDeleteFile = actionToDeleteFileAndFolderIfFolderEmpty(pathToFile = pathToFile)
                    solutionText = "Удаление файла '$pathToFile'"
                    actions.add { println("actionsLocalFileSystem [$solutionText] >>>") }
                    actions.add ( actionToDeleteFile )
                    actions.add { println("actionsLocalFileSystem [$solutionText] <<<") }

                    healthReportStatus = ERROR
                    problemText = "Наличие файла на диске, когда его быть не должно"

                } else { // Файл реально не существует
                    // Проверить и удалить симлинки, если они есть
//                    if (karaokeFileType.symlinks.isNotEmpty()) { // Если у файла должны быть симлинки
//                        karaokeFileType.symlinks.forEach { symlink ->
//                            val pathToSymlinkFile = symlink.pathToSymlinkFile(rootFolder = rootFolder, pathToTargetFile = pathToFile)
//                            val needToDelete = symlink.exists(rootFolder = rootFolder, pathToTargetFile = pathToFile, description = description)
//                            if (needToDelete) {
//                                solutionText = "Удаление симлинка"
//                                actions.add { println("actionsLocalFileSystem [$solutionText] >>>") }
//                                actions.add { actionToDeleteFileAndFolderIfFolderEmpty(pathToFile = pathToSymlinkFile) }
//                                actions.add { println("actionsLocalFileSystem [$solutionText] <<<") }
//
//                                healthReportStatus = ERROR
//                                problemText = "Файла нет на диске (и не должно быть), а симлинки для него есть: '$pathToSymlinkFile'"
//
//                            }
//                        }
//                    }
                }

            }

            val healthReport = HealthReport(
                healthReportType = FILE_VIOLATION,
                settings = settings,
                description = description,
                healthReportStatus = healthReportStatus,
                canResolve = canBeResolved,
                problemText = problemText,
                solutionText = solutionText,
                solutionActions = actions
            )
            result.add(healthReport)

            return result
        }

        private fun actionsLocalStorage(
            karaokeFileType: KaraokeFileType,
            settings: Settings,
            rootFolder: String,
            pathToFile: String,
            description: String,
            existsInLocalFileSystem: Boolean,
            canBe: Boolean,
            willBeInLocation: Boolean,
            canResolve: Boolean,
            canCreate: Boolean,
            karaokeProcessTypesToCreate: KaraokeProcessTypes?,
            actionToCreate: () -> Unit,
            storageBucketName: String,
            storageFileName: String,
            inProgressOwnArgs: Map<String, String>,
            inProgressParentArgs: Map<String, String>,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient
        ): List<HealthReport> {
            val result: MutableList<HealthReport> = mutableListOf()
            if (!willBeInLocation) return emptyList()

            /*
            Если должен быть (canBe):
                Проверить, есть ли в хранилище - заполнить переменную existsInLocalStore
                Файл реально есть в хранилище (existsInLocalStore):
                    Файл реально есть на диске (existsInLocalFileSystem):
                        Файл актуальный
                            ОК
                        Файл не актуальный
                            Удалить старый файл из хранилища
                            Загрузить файл в хранилище
                            ERROR
                    Файла реально нет на диске (existsInLocalFileSystem)
                        WARNING
                Файла реально нет в хранилище (!existsInLocalStore):
                    Если файл реально есть на диске (existsInLocalFileSystem):
                        Загрузить файл в хранилище
                        ERROR
                    Если файла реально нет на диске (existsInLocalFileSystem):
                        Файл есть в удалённом хранилище
                            FATAL_ERROR
                        Файла нет в удалённом хранилище
                            FATAL_ERROR
            Если файла быть не должно (!canBe):
                Если файл реально есть в хранилище (existsInLocalStore):
                    Удалить файл из хранилища
                    ERROR
                Если файла реально нет в хранилище (!existsInLocalStore):
                    ОК
             */

            val actions: MutableList<() -> Unit> = mutableListOf()
            lateinit var problemText: String
            lateinit var solutionText: String
            lateinit var healthReportStatus: HealthReportStatus
            var canBeResolved by Delegates.notNull<Boolean>()

            healthReportStatus = OK
            problemText = "Проблем нет"
            solutionText = ""

            canBeResolved = canResolve

            val existsInLocalStore = storageService.fileExists(bucketName = storageBucketName, fileName = storageFileName)

            if (canBe) { // Файл должен быть
                if (existsInLocalStore) { // Файл реально есть в хранилище (existsInLocalStore)
                    if (existsInLocalFileSystem) { // Файл реально есть на диске (existsInLocalFileSystem)
                       val fileIsActual =  storageService.fileIsActual(bucketName = storageBucketName, fileName = storageFileName, pathToFileOnDisk = pathToFile)
                       if (!fileIsActual) { // Файл не актуальный
                           // Удалить старый и загрузить новый файл

                           healthReportStatus = ERROR
                           problemText = "Файл в локальном хранилище неактуальный"
                           solutionText = "Удалить неактуальный файл из локального хранилища и загрузить актуальный"

                           actions.add { println("actionsLocalStorage [Удаление неактуального файла из локального хранилища] >>>") }
                           actions.add {
                               storageService.deleteFile(
                                   bucketName = storageBucketName,
                                   fileName = storageFileName
                               )
                           }
                           actions.add { println("actionsLocalStorage [Удаление неактуального файла из локального хранилища] <<<") }

                           actions.add { println("actionsLocalStorage [Загрузка файла с диска в локальное хранилище] >>>") }
                           actions.add { storageService.uploadFile(bucketName = storageBucketName, fileName = storageFileName, pathToFileOnDisk = pathToFile) }
                           actions.add { println("actionsLocalStorage [Загрузка файла с диска в локальное хранилище] <<<") }

                       }

                    } else { // Файла реально нет на диске (existsInLocalFileSystem)

                        val existsInRemoteStore = storageApiClient.fileExists(bucketName = storageBucketName, fileName = storageFileName)
                        if (existsInRemoteStore) {
                            
                            val storageFileInfo = storageService.getFileInfo(bucketName = storageBucketName, fileName = storageFileName)
                            val fileIsActual =  storageApiClient.fileIsActual(bucketName = storageBucketName, fileName = storageFileName, storageFileInfo = storageFileInfo)
                            
                            if (!fileIsActual) {
                                healthReportStatus = WARNING
                                problemText = "Файл в локальном хранилище есть, но невозможно проверить его актуальность, т.к. отсутствует файл на диске, а с файлом в удалённом хранилище он не совпадает"
                                solutionText = "Решить проблему в рамках другого задания"
                            }
                            
                        } else {
                            healthReportStatus = WARNING
                            problemText = "Файл в локальном хранилище есть, но невозможно проверить его актуальность, т.к. отсутствует файл на диске и в удалённом хранилище"
                            solutionText = "Решить проблему в рамках другого задания"
                        }
                        
                    }
                } else { // Файла реально нет в хранилище (!existsInLocalStore)
                    if (existsInLocalFileSystem) { // Файл реально есть на диске (existsInLocalFileSystem)
                        // Загружаем файл в хранилище

                        healthReportStatus = ERROR
                        problemText = "Файл в локальном хранилище отсутствует"
                        solutionText = "Загрузка файла с диска в локальное хранилище"

                        actions.add { println("actionsLocalStorage [$solutionText] >>>") }
                        actions.add { storageService.uploadFile(bucketName = storageBucketName, fileName = storageFileName, pathToFileOnDisk = pathToFile) }
                        actions.add { println("actionsLocalStorage [$solutionText] <<<") }

                    } else { // Файла реально нет на диске (existsInLocalFileSystem)

                        val existsInRemoteStore = storageApiClient.fileExists(bucketName = storageBucketName, fileName = storageFileName)
                        if (existsInRemoteStore) { // Файл есть в удалённом хранилище

                            canBeResolved = false
                            healthReportStatus = ERROR
                            problemText = "Файл отсутствует в локальном хранилище и на диске, но есть в удалённом хранилище"
                            solutionText = "Загрузка файла из удалённого хранилища в локальное хранилище"

                            val tempFile = getTempFilePath(prefix = "temp", suffix = ".${karaokeFileType.extention}")

                            actions.add { println("actionsLocalStorage [Загрузка файла из удалённого хранилища] >>>") }
                            actions.add { storageApiClient.downloadFile(bucketName = storageBucketName, fileName = storageFileName, pathToFileOnDisk = tempFile.toString()) }
                            actions.add { println("actionsLocalStorage [Загрузка файла из удалённого хранилища] <<<") }

                            actions.add { println("actionsLocalStorage [Загрузка файла с диска в локальное хранилище] >>>") }
                            actions.add { storageService.uploadFile(bucketName = storageBucketName, fileName = storageFileName, pathToFileOnDisk = tempFile.toString()) }
                            actions.add { println("actionsLocalStorage [Загрузка файла с диска в локальное хранилище] <<<") }

                            actions.add { println("actionsLocalStorage [Удаление скачанного временного файла с диска] >>>") }
                            actions.add { Files.deleteIfExists(tempFile) }
                            actions.add { println("actionsLocalStorage [Удаление скачанного временного файла с диска] <<<") }

                        } else { // Файла нет в удалённом хранилище

                            canBeResolved = false
                            healthReportStatus = FATAL_ERROR
                            problemText = "Файл отсутствует в локальном хранилище, в удалённом хранилище и на диске"
                            solutionText = "Невозможно автоматически решить эту проблему. '$storageFileName'"

                        }

                    }
                }

            } else { // Файла быть не должно

                if (existsInLocalStore) { // Файл реально есть в хранилище (existsInLocalStore)
                    // Удалить файл из хранилища
                    healthReportStatus = ERROR
                    problemText = "Файл есть в локальном хранилище, а быть не должно"
                    solutionText = "Удаление файла из локального хранилища"

                    actions.add { println("actionsLocalStorage [$solutionText] >>>") }
                    actions.add { storageService.deleteFile(bucketName = storageBucketName, fileName = storageFileName) }
                    actions.add { println("actionsLocalStorage [$solutionText] <<<") }
                }

            }

            val healthReport = HealthReport(
                healthReportType = FILE_VIOLATION,
                settings = settings,
                description = description,
                healthReportStatus = healthReportStatus,
                canResolve = canBeResolved,
                problemText = problemText,
                solutionText = solutionText,
                solutionActions = actions
            )
            result.add(healthReport)

            return result
        }

        private fun actionsRemoteStorage(
            karaokeFileType: KaraokeFileType,
            settings: Settings,
            rootFolder: String,
            pathToFile: String,
            description: String,
            existsInLocalFileSystem: Boolean,
            canBe: Boolean,
            willBeInLocation: Boolean,
            canResolve: Boolean,
            canCreate: Boolean,
            karaokeProcessTypesToCreate: KaraokeProcessTypes?,
            actionToCreate: () -> Unit,
            storageBucketName: String,
            storageFileName: String,
            inProgressOwnArgs: Map<String, String>,
            inProgressParentArgs: Map<String, String>,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient
        ): List<HealthReport> {
            val result: MutableList<HealthReport> = mutableListOf()
            if (!willBeInLocation) return emptyList()

            /*
            Если должен быть (canBe):
                Проверить, есть ли в хранилище - заполнить переменную existsInRemoteStore
                Файл реально есть в хранилище (existsInRemoteStore):
                    Файл реально есть на диске (existsInLocalFileSystem):
                        Файл актуальный
                            ОК
                        Файл не актуальный
                            Удалить старый файл из хранилища
                            Загрузить файл в хранилище
                            ERROR
                    Файла реально нет на диске (existsInLocalFileSystem)
                        WARNING
                Файла реально нет в хранилище (!existsInRemoteStore):
                    Если файл реально есть на диске (existsInLocalFileSystem):
                        Загрузить файл в хранилище
                        ERROR
                    Если файла реально нет на диске (existsInLocalFileSystem):
                        Файл есть в локальном хранилище
                            FATAL_ERROR
                        Файла нет в локальном хранилище
                            FATAL_ERROR
            Если файла быть не должно (!canBe):
                Если файл реально есть в хранилище (existsInRemoteStore):
                    Удалить файл из хранилища
                    ERROR
                Если файла реально нет в хранилище (!existsInRemoteStore):
                    ОК
             */

            val actions: MutableList<() -> Unit> = mutableListOf()
            lateinit var problemText: String
            lateinit var solutionText: String
            lateinit var healthReportStatus: HealthReportStatus
            var canBeResolved by Delegates.notNull<Boolean>()

            healthReportStatus = OK
            problemText = "Проблем нет"
            solutionText = ""

            canBeResolved = canResolve

            val existsInRemoteStore = storageApiClient.fileExists(bucketName = storageBucketName, fileName = storageFileName)

            if (canBe) { // Файл должен быть
                if (existsInRemoteStore) { // Файл реально есть в хранилище (existsInLocalStore)
                    if (existsInLocalFileSystem) { // Файл реально есть на диске (existsInLocalFileSystem)
                        val fileIsActual =  storageApiClient.fileIsActual(bucketName = storageBucketName, fileName = storageFileName, pathToFileOnDisk = pathToFile)
                        if (!fileIsActual) { // Файл не актуальный
                            // Удалить старый и загрузить новый файл

                            healthReportStatus = ERROR
                            problemText = "Файл в удалённом хранилище неактуальный"
                            solutionText = "Удалить неактуальный файл из удалённого хранилища и загрузить актуальный"

                            actions.add { println("actionsRemoteStorage [Удаление неактуального файла из удалённого хранилища] >>>") }
                            actions.add { storageApiClient.deleteFile(bucketName = storageBucketName, fileName = storageFileName) }
                            actions.add { println("actionsRemoteStorage [Удаление неактуального файла из удалённого хранилища] <<<") }

                            actions.add { println("actionsRemoteStorage [Загрузка файла с диска в удалённое хранилище] >>>") }
                            actions.add { storageApiClient.uploadFile(bucketName = storageBucketName, fileName = storageFileName, pathToFileOnDisk = pathToFile) }
                            actions.add { println("actionsRemoteStorage [Загрузка файла с диска в удалённое хранилище] <<<") }

                        }

                    } else { // Файла реально нет на диске (existsInLocalFileSystem)
                        
                        val existsInLocalStore = storageService.fileExists(bucketName = storageBucketName, fileName = storageFileName)
                        if (existsInLocalStore) {

                            val storageFileInfo = storageService.getFileInfo(bucketName = storageBucketName, fileName = storageFileName)
                            val fileIsActual =  storageApiClient.fileIsActual(bucketName = storageBucketName, fileName = storageFileName, storageFileInfo = storageFileInfo)

                            if (!fileIsActual) {
                                healthReportStatus = WARNING
                                problemText = "Файл в удалённом хранилище есть, но невозможно проверить его актуальность, т.к. отсутствует файл на диске, а с файлом в локальном хранилище он не совпадает"
                                solutionText = "Решить проблему в рамках другого задания"
                            }

                        } else {
                            healthReportStatus = WARNING
                            problemText = "Файл в удалённом хранилище есть, но невозможно проверить его актуальность, т.к. отсутствует файл на диске и в локальном хранилище"
                            solutionText = "Решить проблему в рамках другого задания"
                        }
                        
                    }
                } else { // Файла реально нет в хранилище (!existsInLocalStore)
                    if (existsInLocalFileSystem) { // Файл реально есть на диске (existsInLocalFileSystem)
                        // Загружаем файл в хранилище

                        healthReportStatus = ERROR
                        problemText = "Файл в удалённом хранилище отсутствует"
                        solutionText = "Загрузка файла с диска в удалённое хранилище"

                        actions.add { println("actionsRemoteStorage [$solutionText] >>>") }
                        actions.add { storageApiClient.uploadFile(bucketName = storageBucketName, fileName = storageFileName, pathToFileOnDisk = pathToFile) }
                        actions.add { println("actionsRemoteStorage [$solutionText] <<<") }

                    } else { // Файла реально нет на диске (existsInLocalFileSystem)

                        val existsInLocalStore = storageService.fileExists(bucketName = storageBucketName, fileName = storageFileName)
                        if (existsInLocalStore) { // Файл есть в удалённом хранилище

                            canBeResolved = false
                            healthReportStatus = ERROR
                            problemText = "Файл отсутствует в удалённом хранилище и на диске, но есть в локальном хранилище"
                            solutionText = "Загрузка файла из локального хранилища в удалённое хранилище"

                            val tempFile = getTempFilePath(prefix = "temp", suffix = ".${karaokeFileType.extention}")

                            actions.add { println("actionsRemoteStorage [Загрузка файла из локального хранилища] >>>") }
                            actions.add { storageService.downloadFile(bucketName = storageBucketName, fileName = storageFileName, pathToFileOnDisk = tempFile.toString()) }
                            actions.add { println("actionsRemoteStorage [Загрузка файла из локального хранилища] <<<") }

                            actions.add { println("actionsRemoteStorage [Загрузка файла с диска в удалённое хранилище] >>>") }
                            actions.add { storageApiClient.uploadFile(bucketName = storageBucketName, fileName = storageFileName, pathToFileOnDisk = tempFile.toString()) }
                            actions.add { println("actionsRemoteStorage [Загрузка файла с диска в удалённое хранилище] <<<") }

                            actions.add { println("actionsRemoteStorage [Удаление скачанного временного файла с диска] >>>") }
                            actions.add { Files.deleteIfExists(tempFile) }
                            actions.add { println("actionsRemoteStorage [Удаление скачанного временного файла с диска] <<<") }

                        } else { // Файла нет в удалённом хранилище

                            canBeResolved = false
                            healthReportStatus = FATAL_ERROR
                            problemText = "Файл отсутствует в удалённом хранилище, в локальном хранилище и на диске"
                            solutionText = "Невозможно автоматически решить эту проблему"

                        }

                    }
                }

            } else { // Файла быть не должно

                if (existsInRemoteStore) { // Файл реально есть в хранилище (existsInLocalStore)
                    // Удалить файл из хранилища
                    healthReportStatus = ERROR
                    problemText = "Файл есть в удалённом хранилище, а быть не должно"
                    solutionText = "Удаление файла из удалённого хранилища"

                    actions.add { println("actionsRemoteStorage [$solutionText] >>>") }
                    actions.add { storageApiClient.deleteFile(bucketName = storageBucketName, fileName = storageFileName) }
                    actions.add { println("actionsRemoteStorage [$solutionText] <<<") }
                }

            }

            val healthReport = HealthReport(
                healthReportType = FILE_VIOLATION,
                settings = settings,
                description = description,
                healthReportStatus = healthReportStatus,
                canResolve = canBeResolved,
                problemText = problemText,
                solutionText = solutionText,
                solutionActions = actions
            )
            result.add(healthReport)

            return result
        }

        fun getHealthReport(settings: Settings, dto: HealthReportDTO): HealthReport? {
            val healthReportList = getHealthReportList(settings = settings)
            val result = healthReportList.firstOrNull { healthReport ->
                healthReport.healthReportStatus.name == dto.healthReportStatusName &&
                healthReport.healthReportType.name == dto.healthReportTypeName &&
                healthReport.description == dto.description
            }
            println("getHealthReport = $result")
            return result
        }

        fun getHealthReportList(settings: Settings): List<HealthReport> {
            val result: MutableList<HealthReport> = mutableListOf()
            val database = settings.database
            val storageService = settings.storageService
            val storageApiClient = settings.storageApiClient

            // CONSISTENCY_VIOLATION

            // Если у песни нету тональности
            if (settings.key == "") {
                val problemText = "У песни отсутствует тональность"
                val canResolve = File(settings.fileAbsolutePath).exists()
                if (canResolve) {
                    val processArgs = mapOf(
                        "settings_id" to settings.id.toString(),
                        "process_status" to KaraokeProcessStatuses.WAITING.name,
                        "process_type" to KaraokeProcessTypes.KEY_BPM_FROM_FILE.name
                    )
                    val inProgress = KaraokeProcess.loadList(args = processArgs, database = database).isNotEmpty()
                    if (inProgress) {
                        result.add(
                            HealthReport(
                                healthReportType = CONSISTENCY_VIOLATION,
                                settings = settings,
                                healthReportStatus = IN_PROGRESS,
                                canResolve = true,
                                problemText = problemText,
                                solutionText = "Уже есть задание для решения этой проблемы",
                                solutionActions = listOf(
                                    { println("HealthReportSolutionActions >>>") },
                                    { println("HealthReportSolutionActions: Ничего делать не надо. (Зачем тогда вызывали?)") },
                                    { println("HealthReportSolutionActions <<<") }
                                )
                            )
                        )
                    } else {
                        result.add(
                            HealthReport(
                                healthReportType = CONSISTENCY_VIOLATION,
                                settings = settings,
                                healthReportStatus = ERROR,
                                canResolve = true,
                                problemText = problemText,
                                solutionText = "Создать задание для автоматического определения тональности",
                                solutionActions = listOf(
                                    { println("HealthReportSolutionActions >>>") },
                                    {
                                        println("HealthReportSolutionActions: Создание задания для автоматического определения тональности для песни '${settings.fileName}'")
                                        KaraokeProcess.createProcess(
                                            settings = settings,
                                            action = KaraokeProcessTypes.KEY_BPM_FROM_FILE,
                                            doWait = true,
                                            threadId = 1
                                        )
                                    },
                                    { println("HealthReportSolutionActions <<<") }
                                )
                            )
                        )
                    }
                } else {
                    result.add(
                        HealthReport(
                            healthReportType = CONSISTENCY_VIOLATION,
                            settings = settings,
                            healthReportStatus = FATAL_ERROR,
                            canResolve = false,
                            problemText = problemText,
                            solutionText = "Для решение проблемы нужно, чтобы у песни был файл типа '${KaraokeFileType.AUDIO_SONG}'",
                            solutionActions = listOf(
                                { println("HealthReportSolutionActions >>>") },
                                { println("HealthReportSolutionActions: Ничего сделать нельзя! (Зачем тогда вызывали?)") },
                                { println("HealthReportSolutionActions <<<") }
                            )
                        )
                    )
                }

            }

            // FILE_VIOLATION

            /*
            Для каждого файла нужно определить:
                - путь к нему в локальной файловой системе
                - имя бакета в хранилище
                - имя файла в хранилище

            Нужно определить, должен ли быть файл физически (локально на диске / в локальном хранилище / в удаленном хранилище)
            Если должен быть:
                Если файл реально есть:
                     Проверить, должны ли быть у файла симлинки.
                        Если симлинк должен быть, есть и исправен - ОК
                        Если симлинк должен быть, есть и неисправен - удалить и создать правильный
                        Если симлинк должен быть, а его нет - создать
                Если файла реально нет - его (и его симлинки) нужно создать / загрузить
            Если файла быть не должно:
                Если файл реально есть - его надо удалить вместе с симлинками (если они есть)
                Если файла реально нет - проверить есть ли его симлинки и удалить их
             */

            KaraokeFileType.entries.forEach { karaokeFileType ->

                val textFile = karaokeFileType.name
                val canResolve = karaokeFileType.canResolve
                val rootFolder = settings.rootFolder

                lateinit var pathToFile: String
                var canBe by Delegates.notNull<Boolean>()
                var canCreate by Delegates.notNull<Boolean>()
                var existsInLocalFileSystem by Delegates.notNull<Boolean>()
                val storageBucketName = settings.storageBucketName
                lateinit var storageFileName: String
                lateinit var description: String
                var inProgressOwnArgs: Map<String, String> = emptyMap()
                var inProgressParentArgs: Map<String, String> = emptyMap()
                var karaokeProcessTypesToCreate: KaraokeProcessTypes? = null
                var actionToCreate: () -> Unit = { println("Заглушка функции 'actionToCreate'") }
                canCreate = canResolve

                when (karaokeFileType) {

                    // Исходный аудио файл
                    KaraokeFileType.AUDIO_SONG -> {
                        pathToFile = settings.fileAbsolutePath
                        existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                        canBe = true // файл должен быть на диске
                        canCreate = false // файл невозможно создать автоматически
                        storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
//                        inProgressOwnArgs = emptyMap() // нет аргументов для процесса, т.к. файл нельзя создать автоматически
//                        inProgressParentArgs = emptyMap() // нет аргументов для процесса, т.к. файл нельзя создать автоматически
//                        karaokeProcessTypesToCreate = null // нет типа процесса для создания, т.к. файл нельзя создать автоматически
//                        actionToCreate = { println("Заглушка функции 'actionToCreate'") }
                        description = karaokeFileType.name

                        karaokeFileType.locations.forEach { location ->
                            val actions = actions(
                                karaokeFileType = karaokeFileType,
                                settings = settings,
                                rootFolder = rootFolder,
                                pathToFile = pathToFile,
                                description = description,
                                existsInLocalFileSystem = existsInLocalFileSystem,
                                canBe = canBe,
                                canResolve = canResolve,
                                canCreate = canCreate,
                                karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                actionToCreate = actionToCreate,
                                location = location,
                                storageBucketName = storageBucketName,
                                storageFileName = storageFileName,
                                inProgressOwnArgs = inProgressOwnArgs,
                                inProgressParentArgs = inProgressParentArgs,
                                database = database,
                                storageService = storageService,
                                storageApiClient = storageApiClient
                            )
                            result.addAll(actions)
                        }
                    }

                    // Исходный аудио файл в формате mp3
                    KaraokeFileType.MP3_SONG -> {
                        pathToFile = settings.pathToFileMP3Lyrics
                        existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                        canBe = true // Файл должнен быть

                        storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                        description = karaokeFileType.name

                        // файл возможно создать автоматически, если есть файл типа AUDIO_SONG
                        val patentFileType = KaraokeFileType.AUDIO_SONG
                        val pathToParentFile = settings.fileAbsolutePath
                        val parentFileExistsInLocalFileSystem = if (pathToParentFile != "") File(pathToParentFile).exists() else false
                        canCreate = parentFileExistsInLocalFileSystem

                        if (canCreate) {
                            inProgressOwnArgs = mapOf(
                                "settings_id" to settings.id.toString(),
                                "process_status" to KaraokeProcessStatuses.WAITING.name,
                                "process_type" to KaraokeProcessTypes.FF_MP3_LYR.name
                            )
                            karaokeProcessTypesToCreate = KaraokeProcessTypes.FF_MP3_LYR
                        }
//                        inProgressOwnArgs = emptyMap() // нет аргументов для процесса, т.к. файл нельзя создать автоматически
//                        inProgressParentArgs = emptyMap() // нет аргументов для процесса, т.к. файл нельзя создать автоматически
//                        karaokeProcessTypesToCreate = null // нет типа процесса для создания, т.к. файл нельзя создать автоматически
//                        actionToCreate = { println("Заглушка функции 'actionToCreate'") }

                        karaokeFileType.locations.forEach { location ->
                            val actions = actions(
                                karaokeFileType = karaokeFileType,
                                settings = settings,
                                rootFolder = rootFolder,
                                pathToFile = pathToFile,
                                description = description,
                                existsInLocalFileSystem = existsInLocalFileSystem,
                                canBe = canBe,
                                canResolve = canResolve,
                                canCreate = canCreate,
                                karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                actionToCreate = actionToCreate,
                                location = location,
                                storageBucketName = storageBucketName,
                                storageFileName = storageFileName,
                                inProgressOwnArgs = inProgressOwnArgs,
                                inProgressParentArgs = inProgressParentArgs,
                                database = database,
                                storageService = storageService,
                                storageApiClient = storageApiClient
                            )
                            result.addAll(actions)
                        }
                    }

                    // Минусовка
                    KaraokeFileType.AUDIO_ACCOMPANIMENT -> {
                        pathToFile = settings.accompanimentNameFlac
                        existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                        canBe = true
                        storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                        description = karaokeFileType.name

                        // файл возможно создать автоматически, если есть файл типа AUDIO_SONG
                        val patentFileType = KaraokeFileType.AUDIO_SONG
                        val pathToParentFile = settings.fileAbsolutePath
                        val parentFileExistsInLocalFileSystem = if (pathToParentFile != "") File(pathToParentFile).exists() else false
                        canCreate = parentFileExistsInLocalFileSystem

                        if (canCreate) {
                            inProgressOwnArgs = mapOf(
                                "settings_id" to settings.id.toString(),
                                "process_status" to KaraokeProcessStatuses.WAITING.name,
                                "process_type" to KaraokeProcessTypes.DEMUCS2.name
                            )
                            karaokeProcessTypesToCreate = KaraokeProcessTypes.DEMUCS2
                        }

                        karaokeFileType.locations.forEach { location ->
                            val actions = actions(
                                karaokeFileType = karaokeFileType,
                                settings = settings,
                                rootFolder = rootFolder,
                                pathToFile = pathToFile,
                                description = description,
                                existsInLocalFileSystem = existsInLocalFileSystem,
                                canBe = canBe,
                                canResolve = canResolve,
                                canCreate = canCreate,
                                karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                actionToCreate = actionToCreate,
                                location = location,
                                storageBucketName = storageBucketName,
                                storageFileName = storageFileName,
                                inProgressOwnArgs = inProgressOwnArgs,
                                inProgressParentArgs = inProgressParentArgs,
                                database = database,
                                storageService = storageService,
                                storageApiClient = storageApiClient
                            )
                            result.addAll(actions)
                        }
                    }

                    // Минусовка в формате mp3
                    KaraokeFileType.MP3_ACCOMPANIMENT -> {
                        pathToFile = settings.pathToFileMP3Karaoke
                        existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                        canBe = true
                        storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                        description = karaokeFileType.name

                        // файл возможно создать автоматически, если есть файл типа AUDIO_ACCOMPANIMENT
                        val patentFileType = KaraokeFileType.AUDIO_ACCOMPANIMENT
                        val pathToParentFile = settings.accompanimentNameFlac
                        val parentFileExistsInLocalFileSystem = if (pathToParentFile != "") File(pathToParentFile).exists() else false
                        canCreate = parentFileExistsInLocalFileSystem

                        if (canCreate) {
                            inProgressOwnArgs = mapOf(
                                "settings_id" to settings.id.toString(),
                                "process_status" to KaraokeProcessStatuses.WAITING.name,
                                "process_type" to KaraokeProcessTypes.FF_MP3_KAR.name
                            )
                            karaokeProcessTypesToCreate = KaraokeProcessTypes.FF_MP3_KAR
                        }

                        karaokeFileType.locations.forEach { location ->
                            val actions = actions(
                                karaokeFileType = karaokeFileType,
                                settings = settings,
                                rootFolder = rootFolder,
                                pathToFile = pathToFile,
                                description = description,
                                existsInLocalFileSystem = existsInLocalFileSystem,
                                canBe = canBe,
                                canResolve = canResolve,
                                canCreate = canCreate,
                                karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                actionToCreate = actionToCreate,
                                location = location,
                                storageBucketName = storageBucketName,
                                storageFileName = storageFileName,
                                inProgressOwnArgs = inProgressOwnArgs,
                                inProgressParentArgs = inProgressParentArgs,
                                database = database,
                                storageService = storageService,
                                storageApiClient = storageApiClient
                            )
                            result.addAll(actions)
                        }
                    }

                    // Чистый голос
                    KaraokeFileType.AUDIO_VOICE -> {
                        pathToFile = settings.vocalsNameFlac
                        existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                        canBe = true
                        storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                        description = karaokeFileType.name

                        // файл возможно создать автоматически, если есть файл типа AUDIO_SONG
                        val patentFileType = KaraokeFileType.AUDIO_SONG
                        val pathToParentFile = settings.fileAbsolutePath
                        val parentFileExistsInLocalFileSystem = if (pathToParentFile != "") File(pathToParentFile).exists() else false
                        canCreate = parentFileExistsInLocalFileSystem

                        if (canCreate) {
                            inProgressOwnArgs = mapOf(
                                "settings_id" to settings.id.toString(),
                                "process_status" to KaraokeProcessStatuses.WAITING.name,
                                "process_type" to KaraokeProcessTypes.DEMUCS2.name
                            )
                            karaokeProcessTypesToCreate = KaraokeProcessTypes.DEMUCS2
                        }

                        karaokeFileType.locations.forEach { location ->
                            val actions = actions(
                                karaokeFileType = karaokeFileType,
                                settings = settings,
                                rootFolder = rootFolder,
                                pathToFile = pathToFile,
                                description = description,
                                existsInLocalFileSystem = existsInLocalFileSystem,
                                canBe = canBe,
                                canResolve = canResolve,
                                canCreate = canCreate,
                                karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                actionToCreate = actionToCreate,
                                location = location,
                                storageBucketName = storageBucketName,
                                storageFileName = storageFileName,
                                inProgressOwnArgs = inProgressOwnArgs,
                                inProgressParentArgs = inProgressParentArgs,
                                database = database,
                                storageService = storageService,
                                storageApiClient = storageApiClient
                            )
                            result.addAll(actions)
                        }
                    }

                    // Бас
                    KaraokeFileType.AUDIO_BASS -> {
                        pathToFile = settings.bassNameFlac
                        existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                        canBe = (settings.hasChords || settings.hasMelody)
                        storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                        description = karaokeFileType.name

                        // файл возможно создать автоматически, если есть файл типа AUDIO_SONG
                        val patentFileType = KaraokeFileType.AUDIO_SONG
                        val pathToParentFile = settings.fileAbsolutePath
                        val parentFileExistsInLocalFileSystem = if (pathToParentFile != "") File(pathToParentFile).exists() else false
                        canCreate = parentFileExistsInLocalFileSystem

                        if (canCreate) {
                            inProgressOwnArgs = mapOf(
                                "settings_id" to settings.id.toString(),
                                "process_status" to KaraokeProcessStatuses.WAITING.name,
                                "process_type" to KaraokeProcessTypes.DEMUCS5.name
                            )
                            karaokeProcessTypesToCreate = KaraokeProcessTypes.DEMUCS5
                        }

                        karaokeFileType.locations.forEach { location ->
                            val actions = actions(
                                karaokeFileType = karaokeFileType,
                                settings = settings,
                                rootFolder = rootFolder,
                                pathToFile = pathToFile,
                                description = description,
                                existsInLocalFileSystem = existsInLocalFileSystem,
                                canBe = canBe,
                                canResolve = canResolve,
                                canCreate = canCreate,
                                karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                actionToCreate = actionToCreate,
                                location = location,
                                storageBucketName = storageBucketName,
                                storageFileName = storageFileName,
                                inProgressOwnArgs = inProgressOwnArgs,
                                inProgressParentArgs = inProgressParentArgs,
                                database = database,
                                storageService = storageService,
                                storageApiClient = storageApiClient
                            )
                            result.addAll(actions)
                        }
                    }

                    // Ударные
                    KaraokeFileType.AUDIO_DRUMS -> {
                        pathToFile = settings.drumsNameFlac
                        existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                        canBe = (settings.hasChords || settings.hasMelody)
                        storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                        description = karaokeFileType.name

                        // файл возможно создать автоматически, если есть файл типа AUDIO_SONG
                        val patentFileType = KaraokeFileType.AUDIO_SONG
                        val pathToParentFile = settings.fileAbsolutePath
                        val parentFileExistsInLocalFileSystem = if (pathToParentFile != "") File(pathToParentFile).exists() else false
                        canCreate = parentFileExistsInLocalFileSystem

                        if (canCreate) {
                            inProgressOwnArgs = mapOf(
                                "settings_id" to settings.id.toString(),
                                "process_status" to KaraokeProcessStatuses.WAITING.name,
                                "process_type" to KaraokeProcessTypes.DEMUCS5.name
                            )
                            karaokeProcessTypesToCreate = KaraokeProcessTypes.DEMUCS5
                        }

                        karaokeFileType.locations.forEach { location ->
                            val actions = actions(
                                karaokeFileType = karaokeFileType,
                                settings = settings,
                                rootFolder = rootFolder,
                                pathToFile = pathToFile,
                                description = description,
                                existsInLocalFileSystem = existsInLocalFileSystem,
                                canBe = canBe,
                                canResolve = canResolve,
                                canCreate = canCreate,
                                karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                actionToCreate = actionToCreate,
                                location = location,
                                storageBucketName = storageBucketName,
                                storageFileName = storageFileName,
                                inProgressOwnArgs = inProgressOwnArgs,
                                inProgressParentArgs = inProgressParentArgs,
                                database = database,
                                storageService = storageService,
                                storageApiClient = storageApiClient
                            )
                            result.addAll(actions)
                        }
                    }

                    // Мелодия без баса и ударных
                    KaraokeFileType.AUDIO_OTHER -> {
                        pathToFile = settings.otherNameFlac
                        existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                        canBe = (settings.hasChords || settings.hasMelody)
                        storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                        description = karaokeFileType.name

                        // файл возможно создать автоматически, если есть файл типа AUDIO_SONG
                        val patentFileType = KaraokeFileType.AUDIO_SONG
                        val pathToParentFile = settings.fileAbsolutePath
                        val parentFileExistsInLocalFileSystem = if (pathToParentFile != "") File(pathToParentFile).exists() else false
                        canCreate = parentFileExistsInLocalFileSystem

                        if (canCreate) {
                            inProgressOwnArgs = mapOf(
                                "settings_id" to settings.id.toString(),
                                "process_status" to KaraokeProcessStatuses.WAITING.name,
                                "process_type" to KaraokeProcessTypes.DEMUCS5.name
                            )
                            karaokeProcessTypesToCreate = KaraokeProcessTypes.DEMUCS5
                        }

                        karaokeFileType.locations.forEach { location ->
                            val actions = actions(
                                karaokeFileType = karaokeFileType,
                                settings = settings,
                                rootFolder = rootFolder,
                                pathToFile = pathToFile,
                                description = description,
                                existsInLocalFileSystem = existsInLocalFileSystem,
                                canBe = canBe,
                                canResolve = canResolve,
                                canCreate = canCreate,
                                karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                actionToCreate = actionToCreate,
                                location = location,
                                storageBucketName = storageBucketName,
                                storageFileName = storageFileName,
                                inProgressOwnArgs = inProgressOwnArgs,
                                inProgressParentArgs = inProgressParentArgs,
                                database = database,
                                storageService = storageService,
                                storageApiClient = storageApiClient
                            )
                            result.addAll(actions)
                        }
                    }

                    // Картинка альбома
                    KaraokeFileType.PICTURE_ALBUM -> {
                        pathToFile = settings.pathToFileLogoAlbum
                        existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                        canBe = true
                        storageFileName = "${settings.author}/${settings.year} - ${settings.album}/${settings.author} - ${settings.year} - ${settings.album}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                        description = karaokeFileType.name
                        canCreate = false // файл невозможно создать автоматически

                        karaokeFileType.locations.forEach { location ->
                            val actions = actions(
                                karaokeFileType = karaokeFileType,
                                settings = settings,
                                rootFolder = rootFolder,
                                pathToFile = pathToFile,
                                description = description,
                                existsInLocalFileSystem = existsInLocalFileSystem,
                                canBe = canBe,
                                canResolve = canResolve,
                                canCreate = canCreate,
                                karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                actionToCreate = actionToCreate,
                                location = location,
                                storageBucketName = storageBucketName,
                                storageFileName = storageFileName,
                                inProgressOwnArgs = inProgressOwnArgs,
                                inProgressParentArgs = inProgressParentArgs,
                                database = database,
                                storageService = storageService,
                                storageApiClient = storageApiClient
                            )
                            result.addAll(actions)
                        }
                    }

                    // Картинка альбома (preview)
                    KaraokeFileType.PICTURE_ALBUM_PREVIEW -> {
                        pathToFile = ""
                        existsInLocalFileSystem = false
                        canBe = true
                        storageFileName = "${settings.author}/${settings.year} - ${settings.album}/${settings.author} - ${settings.year} - ${settings.album}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                        description = karaokeFileType.name

                        // файл возможно создать автоматически, если есть файл типа PICTURE_ALBUM
                        val patentFileType = KaraokeFileType.PICTURE_ALBUM
                        val pathToParentFile = settings.pathToFileLogoAlbum
                        val parentFileExistsInLocalFileSystem = if (pathToParentFile != "") File(pathToParentFile).exists() else false
                        canCreate = parentFileExistsInLocalFileSystem

                        karaokeFileType.locations.forEach { location ->
                            val actions = actions(
                                karaokeFileType = karaokeFileType,
                                settings = settings,
                                rootFolder = rootFolder,
                                pathToFile = pathToFile,
                                description = description,
                                existsInLocalFileSystem = existsInLocalFileSystem,
                                canBe = canBe,
                                canResolve = canResolve,
                                canCreate = canCreate,
                                karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                actionToCreate = actionToCreate,
                                location = location,
                                storageBucketName = storageBucketName,
                                storageFileName = storageFileName,
                                inProgressOwnArgs = inProgressOwnArgs,
                                inProgressParentArgs = inProgressParentArgs,
                                database = database,
                                storageService = storageService,
                                storageApiClient = storageApiClient
                            )
                            result.addAll(actions)
                        }
                    }

                    // Картинка автора
                    KaraokeFileType.PICTURE_AUTHOR -> {
                        pathToFile = settings.pathToFileLogoAuthor
                        existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                        canBe = true
                        storageFileName = "${settings.author}/${settings.author}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                        description = karaokeFileType.name
                        canCreate = false // файл невозможно создать автоматически

                        karaokeFileType.locations.forEach { location ->
                            val actions = actions(
                                karaokeFileType = karaokeFileType,
                                settings = settings,
                                rootFolder = rootFolder,
                                pathToFile = pathToFile,
                                description = description,
                                existsInLocalFileSystem = existsInLocalFileSystem,
                                canBe = canBe,
                                canResolve = canResolve,
                                canCreate = canCreate,
                                karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                actionToCreate = actionToCreate,
                                location = location,
                                storageBucketName = storageBucketName,
                                storageFileName = storageFileName,
                                inProgressOwnArgs = inProgressOwnArgs,
                                inProgressParentArgs = inProgressParentArgs,
                                database = database,
                                storageService = storageService,
                                storageApiClient = storageApiClient
                            )
                            result.addAll(actions)
                        }

                    }

                    // Картинка автора (preview)
                    KaraokeFileType.PICTURE_AUTHOR_PREVIEW -> {
                        pathToFile = ""
                        existsInLocalFileSystem = false
                        canBe = true
                        storageFileName = "${settings.author}/${settings.author}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                        description = karaokeFileType.name

                        // файл возможно создать автоматически, если есть файл типа PICTURE_AUTHOR
                        val patentFileType = KaraokeFileType.PICTURE_AUTHOR
                        val pathToParentFile = settings.pathToFileLogoAuthor
                        val parentFileExistsInLocalFileSystem = if (pathToParentFile != "") File(pathToParentFile).exists() else false
                        canCreate = parentFileExistsInLocalFileSystem

                        karaokeFileType.locations.forEach { location ->
                            val actions = actions(
                                karaokeFileType = karaokeFileType,
                                settings = settings,
                                rootFolder = rootFolder,
                                pathToFile = pathToFile,
                                description = description,
                                existsInLocalFileSystem = existsInLocalFileSystem,
                                canBe = canBe,
                                canResolve = canResolve,
                                canCreate = canCreate,
                                karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                actionToCreate = actionToCreate,
                                location = location,
                                storageBucketName = storageBucketName,
                                storageFileName = storageFileName,
                                inProgressOwnArgs = inProgressOwnArgs,
                                inProgressParentArgs = inProgressParentArgs,
                                database = database,
                                storageService = storageService,
                                storageApiClient = storageApiClient
                            )
                            result.addAll(actions)
                        }
                    }

                    // Картинка публикации
                    KaraokeFileType.PICTURE_PUBLICATION -> {
                        KaraokePlatform.entries.forEach { karaokePlatform ->
                            pathToFile = if (karaokePlatform.forAllVersions) {
                                val subFolder = if (karaokeFileType.karaokeFileTypeKind == KaraokeFileTypeKind.PROJECT) {
                                    "done_projects"
                                } else {
                                    "done_files"
                                }
                                "${settings.rootFolder}/$subFolder/${settings.fileName}${karaokePlatform.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                            } else ""
                            existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                            canBe = if (karaokeFileType.karaokeFileTypeKind == KaraokeFileTypeKind.PROJECT) {
                                (settings.idStatus >= 3 && settings.idStatus < 6)
                            } else {
                                (settings.idStatus >= 6) && (!karaokePlatform.onAirPublications || !settings.onAir)
                            }
                            storageFileName = "${settings.storageFileName}${karaokePlatform.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                            description = "${karaokeFileType.name}/${karaokePlatform.name}"
                            canCreate = true

                            when(karaokePlatform) {
                                KaraokePlatform.SPONSR -> { actionToCreate = { createSponsrTeaserPicture(settings = settings) } }
                                KaraokePlatform.VKGROUP -> { actionToCreate = { createVKLinkPicture(settings = settings) } }

                                KaraokePlatform.DZEN,
                                KaraokePlatform.VKVIDEO,
                                KaraokePlatform.PLATFORMA,
                                KaraokePlatform.TELEGRAM -> {}
                            }

                            karaokeFileType.locations.forEach { location ->
                                val actions = actions(
                                    karaokeFileType = karaokeFileType,
                                    settings = settings,
                                    rootFolder = rootFolder,
                                    pathToFile = pathToFile,
                                    description = description,
                                    existsInLocalFileSystem = existsInLocalFileSystem,
                                    canBe = canBe,
                                    canResolve = canResolve,
                                    canCreate = canCreate,
                                    karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                    actionToCreate = actionToCreate,
                                    location = location,
                                    storageBucketName = storageBucketName,
                                    storageFileName = storageFileName,
                                    inProgressOwnArgs = inProgressOwnArgs,
                                    inProgressParentArgs = inProgressParentArgs,
                                    database = database,
                                    storageService = storageService,
                                    storageApiClient = storageApiClient
                                )
                                result.addAll(actions)
                            }

                        }
                    }

                    // Картинка для видео конкретной версии песни
                    KaraokeFileType.PICTURE_SONGVERSION -> {
                        SongVersion.entries.forEach { songVersion ->
                            pathToFile = "${settings.rootFolder}/done_files/${settings.fileName}${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                            existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                            storageFileName = "${settings.storageFileName}${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                            description = "${karaokeFileType.name}/${songVersion.name}"
                            when(songVersion) {
                                SongVersion.LYRICS -> {
                                    canBe = (settings.idStatus >= 3)
                                }
                                SongVersion.KARAOKE -> {
                                    canBe = (settings.idStatus >= 3)
                                }
                                SongVersion.CHORDS -> {
                                    canBe = (settings.idStatus >= 3) && settings.hasChords
                                }
                                SongVersion.TABS -> {
                                    canBe = (settings.idStatus >= 3) && settings.hasMelody
                                }
                            }
                            canCreate = true
                            actionToCreate = { createSongPicture(settings = settings, songVersion = songVersion) }

                            karaokeFileType.locations.forEach { location ->
                                val actions = actions(
                                    karaokeFileType = karaokeFileType,
                                    settings = settings,
                                    rootFolder = rootFolder,
                                    pathToFile = pathToFile,
                                    description = description,
                                    existsInLocalFileSystem = existsInLocalFileSystem,
                                    canBe = canBe,
                                    canResolve = canResolve,
                                    canCreate = canCreate,
                                    karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                    actionToCreate = actionToCreate,
                                    location = location,
                                    storageBucketName = storageBucketName,
                                    storageFileName = storageFileName,
                                    inProgressOwnArgs = inProgressOwnArgs,
                                    inProgressParentArgs = inProgressParentArgs,
                                    database = database,
                                    storageService = storageService,
                                    storageApiClient = storageApiClient
                                )
                                result.addAll(actions)
                            }

                        }
                    }

                    // Видео конкретной версии песни в разрешении 1080p/60fps
                    KaraokeFileType.VIDEO_SONGVERSION_1080P ->  {

                        SongVersion.entries.forEach { songVersion ->
                            pathToFile = "${settings.rootFolder}/done_files/${settings.fileName}${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                            existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                            storageFileName = "${settings.storageFileName}${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                            description = "${karaokeFileType.name}/${songVersion.name}"
                            when(songVersion) {
                                SongVersion.LYRICS -> {
                                    canBe = (settings.idStatus >= 6)
                                    // файл возможно создать автоматически, если есть файлы типа AUDIO_SONG
                                    val patentFileType = KaraokeFileType.AUDIO_SONG
                                    val pathToParentFile = settings.fileAbsolutePath
                                    val parentFileExistsInLocalFileSystem = if (pathToParentFile != "") File(pathToParentFile).exists() else false
                                    canCreate = parentFileExistsInLocalFileSystem
                                    if (canCreate) {
                                        inProgressOwnArgs = mapOf(
                                            "settings_id" to settings.id.toString(),
                                            "process_status" to KaraokeProcessStatuses.WAITING.name,
                                            "process_type" to KaraokeProcessTypes.MELT_LYRICS.name
                                        )
                                        actionToCreate = {
                                            settings.createKaraoke(songVersion = songVersion)
                                            KaraokeProcess.createProcess(
                                                settings = settings,
                                                action = KaraokeProcessTypes.MELT_LYRICS,
                                                doWait = true,
                                                prior = 0,
                                                threadId = 0)
                                        }
                                    }

                                }
                                SongVersion.KARAOKE -> {
                                    canBe = (settings.idStatus >= 6)
                                    // файл возможно создать автоматически, если есть файлы AUDIO_ACCOMPANIMENT, AUDIO_VOCALS
                                    val pathsToParentFiles = listOf(
                                        settings.vocalsNameFlac,
                                        settings.accompanimentNameFlac
                                    )
                                    val parentFileExistsInLocalFileSystem = pathsToParentFiles.all { pathToParentFile -> if (pathToParentFile != "") File(pathToParentFile).exists() else false }
                                    canCreate = parentFileExistsInLocalFileSystem
                                    if (canCreate) {
                                        inProgressOwnArgs = mapOf(
                                            "settings_id" to settings.id.toString(),
                                            "process_status" to KaraokeProcessStatuses.WAITING.name,
                                            "process_type" to KaraokeProcessTypes.MELT_KARAOKE.name
                                        )
                                        actionToCreate = {
                                            settings.createKaraoke(songVersion = songVersion)
                                            KaraokeProcess.createProcess(
                                                settings = settings,
                                                action = KaraokeProcessTypes.MELT_KARAOKE,
                                                doWait = true,
                                                prior = 0,
                                                threadId = 0)
                                        }
                                    }
                                }
                                SongVersion.CHORDS -> {
                                    canBe = (settings.idStatus >= 6) && settings.hasChords
                                    // файл возможно создать автоматически, если есть AUDIO_VOCALS, AUDIO_OTHER, AUDIO_BASS, AUDIO_DRUMS
                                    val pathsToParentFiles = listOf(
                                        settings.vocalsNameFlac,
                                        settings.otherNameFlac,
                                        settings.bassNameFlac,
                                        settings.drumsNameFlac
                                    )
                                    val parentFileExistsInLocalFileSystem = pathsToParentFiles.all { pathToParentFile -> if (pathToParentFile != "") File(pathToParentFile).exists() else false }
                                    canCreate = parentFileExistsInLocalFileSystem
                                    if (canCreate) {
                                        inProgressOwnArgs = mapOf(
                                            "settings_id" to settings.id.toString(),
                                            "process_status" to KaraokeProcessStatuses.WAITING.name,
                                            "process_type" to KaraokeProcessTypes.MELT_CHORDS.name
                                        )
                                        actionToCreate = {
                                            settings.createKaraoke(songVersion = songVersion)
                                            KaraokeProcess.createProcess(
                                                settings = settings,
                                                action = KaraokeProcessTypes.MELT_CHORDS,
                                                doWait = true,
                                                prior = 0,
                                                threadId = 0)
                                        }
                                    }
                                }
                                SongVersion.TABS -> {
                                    canBe = (settings.idStatus >= 6) && settings.hasMelody
                                    // файл возможно создать автоматически, если есть AUDIO_VOCALS, AUDIO_OTHER, AUDIO_BASS, AUDIO_DRUMS
                                    val pathsToParentFiles = listOf(
                                        settings.vocalsNameFlac,
                                        settings.otherNameFlac,
                                        settings.bassNameFlac,
                                        settings.drumsNameFlac
                                    )
                                    val parentFileExistsInLocalFileSystem = pathsToParentFiles.all { pathToParentFile -> if (pathToParentFile != "") File(pathToParentFile).exists() else false }
                                    canCreate = parentFileExistsInLocalFileSystem
                                    if (canCreate) {
                                        inProgressOwnArgs = mapOf(
                                            "settings_id" to settings.id.toString(),
                                            "process_status" to KaraokeProcessStatuses.WAITING.name,
                                            "process_type" to KaraokeProcessTypes.MELT_TABS.name
                                        )
                                        actionToCreate = {
                                            settings.createKaraoke(songVersion = songVersion)
                                            KaraokeProcess.createProcess(
                                                settings = settings,
                                                action = KaraokeProcessTypes.MELT_TABS,
                                                doWait = true,
                                                prior = 0,
                                                threadId = 0)
                                        }
                                    }
                                }
                            }

                            karaokeFileType.locations.forEach { location ->
                                val actions = actions(
                                    karaokeFileType = karaokeFileType,
                                    settings = settings,
                                    rootFolder = rootFolder,
                                    pathToFile = pathToFile,
                                    description = description,
                                    existsInLocalFileSystem = existsInLocalFileSystem,
                                    canBe = canBe,
                                    canResolve = canResolve,
                                    canCreate = canCreate,
                                    karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                    actionToCreate = actionToCreate,
                                    location = location,
                                    storageBucketName = storageBucketName,
                                    storageFileName = storageFileName,
                                    inProgressOwnArgs = inProgressOwnArgs,
                                    inProgressParentArgs = inProgressParentArgs,
                                    database = database,
                                    storageService = storageService,
                                    storageApiClient = storageApiClient
                                )
                                result.addAll(actions)
                            }

                        }
                    }

                    // Видео конкретной версии песни в разрешении 720p/30fps
                    KaraokeFileType.VIDEO_SONGVERSION_720P ->  {
                        SongVersion.entries.forEach { songVersion ->
                            storageFileName = "${settings.storageFileName}${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                            description = "${karaokeFileType.name}/${songVersion.name}"

                            // файл возможно создать автоматически, если есть файлы типа VIDEO_SONGVERSION_1080P
                            val patentFileType = KaraokeFileType.VIDEO_SONGVERSION_1080P
                            val pathToParentFile = "${settings.rootFolder}/done_files/${settings.fileName}${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                            val parentFileExistsInLocalFileSystem = if (pathToParentFile != "") File(pathToParentFile).exists() else false
                            canCreate = parentFileExistsInLocalFileSystem

                            when(songVersion) {
                                SongVersion.LYRICS -> {
                                    pathToFile = settings.pathToFile720Lyrics
                                    existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                                    canBe = (settings.idStatus >= 6)
                                    if (canCreate) {
                                        inProgressOwnArgs = mapOf(
                                            "settings_id" to settings.id.toString(),
                                            "process_status" to KaraokeProcessStatuses.WAITING.name,
                                            "process_type" to KaraokeProcessTypes.FF_720_LYR.name
                                        )
                                        inProgressParentArgs = mapOf(
                                            "settings_id" to settings.id.toString(),
                                            "process_status" to KaraokeProcessStatuses.WAITING.name,
                                            "process_type" to KaraokeProcessTypes.MELT_LYRICS.name
                                        )
                                        karaokeProcessTypesToCreate = KaraokeProcessTypes.FF_720_LYR
                                    }
                                }
                                SongVersion.KARAOKE -> {
                                    pathToFile = settings.pathToFile720Karaoke
                                    existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                                    canBe = (settings.idStatus >= 6)
                                    if (canCreate) {
                                        inProgressOwnArgs = mapOf(
                                            "settings_id" to settings.id.toString(),
                                            "process_status" to KaraokeProcessStatuses.WAITING.name,
                                            "process_type" to KaraokeProcessTypes.FF_720_KAR.name
                                        )
                                        inProgressParentArgs = mapOf(
                                            "settings_id" to settings.id.toString(),
                                            "process_status" to KaraokeProcessStatuses.WAITING.name,
                                            "process_type" to KaraokeProcessTypes.MELT_KARAOKE.name
                                        )
                                        karaokeProcessTypesToCreate = KaraokeProcessTypes.FF_720_KAR
                                    }
                                }
                                SongVersion.CHORDS -> {
                                    pathToFile = ""
                                    existsInLocalFileSystem = false
                                    canBe = false
                                }
                                SongVersion.TABS -> {
                                    pathToFile = ""
                                    existsInLocalFileSystem = false
                                    canBe = false
                                }
                            }

                            karaokeFileType.locations.forEach { location ->
                                val actions = actions(
                                    karaokeFileType = karaokeFileType,
                                    settings = settings,
                                    rootFolder = rootFolder,
                                    pathToFile = pathToFile,
                                    description = description,
                                    existsInLocalFileSystem = existsInLocalFileSystem,
                                    canBe = canBe,
                                    canResolve = canResolve,
                                    canCreate = canCreate,
                                    karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                    actionToCreate = actionToCreate,
                                    location = location,
                                    storageBucketName = storageBucketName,
                                    storageFileName = storageFileName,
                                    inProgressOwnArgs = inProgressOwnArgs,
                                    inProgressParentArgs = inProgressParentArgs,
                                    database = database,
                                    storageService = storageService,
                                    storageApiClient = storageApiClient
                                )
                                result.addAll(actions)
                            }

                        }
                    }

                    // Скрипт для рендера всех версий
                    KaraokeFileType.PROJECT_ALL_RUN -> {
                        pathToFile = settings.getOutputFilename(SongOutputFile.RUNALL, SongVersion.LYRICS).replace("[lyrics]","[ALL]")
                        existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                        canBe = (settings.idStatus >= 3 && settings.idStatus < 6)
                        storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                        description = karaokeFileType.name
                        canCreate = false

                        karaokeFileType.locations.forEach { location ->
                            val actions = actions(
                                karaokeFileType = karaokeFileType,
                                settings = settings,
                                rootFolder = rootFolder,
                                pathToFile = pathToFile,
                                description = description,
                                existsInLocalFileSystem = existsInLocalFileSystem,
                                canBe = canBe,
                                canResolve = canResolve,
                                canCreate = canCreate,
                                karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                actionToCreate = actionToCreate,
                                location = location,
                                storageBucketName = storageBucketName,
                                storageFileName = storageFileName,
                                inProgressOwnArgs = inProgressOwnArgs,
                                inProgressParentArgs = inProgressParentArgs,
                                database = database,
                                storageService = storageService,
                                storageApiClient = storageApiClient
                            )
                            result.addAll(actions)
                        }

                    }

                    // Скрипт для рендера всех версий, кроме LYRICS
                    KaraokeFileType.PROJECT_ALL_WO_LYRICS_RUN -> {
                        pathToFile = settings.getOutputFilename(SongOutputFile.RUNALL, SongVersion.LYRICS).replace("[lyrics]","[ALLwoLYRICS]")
                        existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                        canBe = (settings.idStatus >= 3 && settings.idStatus < 6)
                        storageFileName = "${settings.storageFileName}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                        description = karaokeFileType.name
                        canCreate = false

                        karaokeFileType.locations.forEach { location ->
                            val actions = actions(
                                karaokeFileType = karaokeFileType,
                                settings = settings,
                                rootFolder = rootFolder,
                                pathToFile = pathToFile,
                                description = description,
                                existsInLocalFileSystem = existsInLocalFileSystem,
                                canBe = canBe,
                                canResolve = canResolve,
                                canCreate = canCreate,
                                karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                actionToCreate = actionToCreate,
                                location = location,
                                storageBucketName = storageBucketName,
                                storageFileName = storageFileName,
                                inProgressOwnArgs = inProgressOwnArgs,
                                inProgressParentArgs = inProgressParentArgs,
                                database = database,
                                storageService = storageService,
                                storageApiClient = storageApiClient
                            )
                            result.addAll(actions)
                        }

                    }

                    // Скрипт для рендера конкретной версии
                    KaraokeFileType.PROJECT_SONGVERSION_RUN ->  {
                        SongVersion.entries.forEach { songVersion ->
                            pathToFile = "${settings.rootFolder}/done_projects/${settings.fileName}${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                            existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                            storageFileName = "${settings.storageFileName}${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                            description = "${karaokeFileType.name}/${songVersion.name}"
                            canCreate = false
                            when(songVersion) {
                                SongVersion.LYRICS -> {
                                    canBe = (settings.idStatus >= 3 && settings.idStatus < 6)
                                }
                                SongVersion.KARAOKE -> {
                                    canBe = (settings.idStatus >= 3 && settings.idStatus < 6)
                                }
                                SongVersion.CHORDS -> {
                                    canBe = (settings.idStatus >= 3 && settings.idStatus < 6) && settings.hasChords
                                }
                                SongVersion.TABS -> {
                                    canBe = (settings.idStatus >= 3 && settings.idStatus < 6) && settings.hasMelody
                                }
                            }

                            karaokeFileType.locations.forEach { location ->
                                val actions = actions(
                                    karaokeFileType = karaokeFileType,
                                    settings = settings,
                                    rootFolder = rootFolder,
                                    pathToFile = pathToFile,
                                    description = description,
                                    existsInLocalFileSystem = existsInLocalFileSystem,
                                    canBe = canBe,
                                    canResolve = canResolve,
                                    canCreate = canCreate,
                                    karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                    actionToCreate = actionToCreate,
                                    location = location,
                                    storageBucketName = storageBucketName,
                                    storageFileName = storageFileName,
                                    inProgressOwnArgs = inProgressOwnArgs,
                                    inProgressParentArgs = inProgressParentArgs,
                                    database = database,
                                    storageService = storageService,
                                    storageApiClient = storageApiClient
                                )
                                result.addAll(actions)
                            }

                        }
                    }

                    // MLT-файл для конкретной версии
                    KaraokeFileType.PROJECT_SONGVERSION_MLT ->  {
                        SongVersion.entries.forEach { songVersion ->
                            pathToFile = "${settings.rootFolder}/done_projects/${settings.fileName}${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                            existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                            storageFileName = "${settings.storageFileName}${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                            description = "${karaokeFileType.name}/${songVersion.name}"
                            canCreate = false
                            when(songVersion) {
                                SongVersion.LYRICS -> {
                                    canBe = (settings.idStatus >= 3 && settings.idStatus < 6)
                                }
                                SongVersion.KARAOKE -> {
                                    canBe = (settings.idStatus >= 3 && settings.idStatus < 6)
                                }
                                SongVersion.CHORDS -> {
                                    canBe = (settings.idStatus >= 3 && settings.idStatus < 6) && settings.hasChords
                                }
                                SongVersion.TABS -> {
                                    canBe = (settings.idStatus >= 3 && settings.idStatus < 6) && settings.hasMelody
                                }
                            }

                            karaokeFileType.locations.forEach { location ->
                                val actions = actions(
                                    karaokeFileType = karaokeFileType,
                                    settings = settings,
                                    rootFolder = rootFolder,
                                    pathToFile = pathToFile,
                                    description = description,
                                    existsInLocalFileSystem = existsInLocalFileSystem,
                                    canBe = canBe,
                                    canResolve = canResolve,
                                    canCreate = canCreate,
                                    karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                    actionToCreate = actionToCreate,
                                    location = location,
                                    storageBucketName = storageBucketName,
                                    storageFileName = storageFileName,
                                    inProgressOwnArgs = inProgressOwnArgs,
                                    inProgressParentArgs = inProgressParentArgs,
                                    database = database,
                                    storageService = storageService,
                                    storageApiClient = storageApiClient
                                )
                                result.addAll(actions)
                            }

                        }
                    }

                    // KDENLIVE-файл для конкретной версии
                    KaraokeFileType.PROJECT_SONGVERSION_KDENLIVE ->  {
                        SongVersion.entries.forEach { songVersion ->
                            pathToFile = "${settings.rootFolder}/done_projects/${settings.fileName}${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                            existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                            storageFileName = "${settings.storageFileName}${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                            description = "${karaokeFileType.name}/${songVersion.name}"
                            canCreate = false
                            when(songVersion) {
                                SongVersion.LYRICS -> {
                                    canBe = (settings.idStatus >= 3 && settings.idStatus < 6)
                                }
                                SongVersion.KARAOKE -> {
                                    canBe = (settings.idStatus >= 3 && settings.idStatus < 6)
                                }
                                SongVersion.CHORDS -> {
                                    canBe = (settings.idStatus >= 3 && settings.idStatus < 6) && settings.hasChords
                                }
                                SongVersion.TABS -> {
                                    canBe = (settings.idStatus >= 3 && settings.idStatus < 6) && settings.hasMelody
                                }
                            }

                            karaokeFileType.locations.forEach { location ->
                                val actions = actions(
                                    karaokeFileType = karaokeFileType,
                                    settings = settings,
                                    rootFolder = rootFolder,
                                    pathToFile = pathToFile,
                                    description = description,
                                    existsInLocalFileSystem = existsInLocalFileSystem,
                                    canBe = canBe,
                                    canResolve = canResolve,
                                    canCreate = canCreate,
                                    karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                    actionToCreate = actionToCreate,
                                    location = location,
                                    storageBucketName = storageBucketName,
                                    storageFileName = storageFileName,
                                    inProgressOwnArgs = inProgressOwnArgs,
                                    inProgressParentArgs = inProgressParentArgs,
                                    database = database,
                                    storageService = storageService,
                                    storageApiClient = storageApiClient
                                )
                                result.addAll(actions)
                            }

                        }
                    }

                    // TXT-файл для конкретной версии
                    KaraokeFileType.PROJECT_SONGVERSION_TXT ->  {
                        SongVersion.entries.forEach { songVersion ->
                            pathToFile = "${settings.rootFolder}/done_projects/${settings.fileName}${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                            existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
                            storageFileName = "${settings.storageFileName}${songVersion.suffix}${karaokeFileType.suffix}.${karaokeFileType.extention}"
                            description = "${karaokeFileType.name}/${songVersion.name}"
                            canCreate = false
                            when(songVersion) {
                                SongVersion.LYRICS -> {
                                    canBe = (settings.idStatus >= 3 && settings.idStatus < 6)
                                }
                                SongVersion.KARAOKE -> {
                                    canBe = (settings.idStatus >= 3 && settings.idStatus < 6)
                                }
                                SongVersion.CHORDS -> {
                                    canBe = (settings.idStatus >= 3 && settings.idStatus < 6) && settings.hasChords
                                }
                                SongVersion.TABS -> {
                                    canBe = (settings.idStatus >= 3 && settings.idStatus < 6) && settings.hasMelody
                                }
                            }

                            karaokeFileType.locations.forEach { location ->
                                val actions = actions(
                                    karaokeFileType = karaokeFileType,
                                    settings = settings,
                                    rootFolder = rootFolder,
                                    pathToFile = pathToFile,
                                    description = description,
                                    existsInLocalFileSystem = existsInLocalFileSystem,
                                    canBe = canBe,
                                    canResolve = canResolve,
                                    canCreate = canCreate,
                                    karaokeProcessTypesToCreate = karaokeProcessTypesToCreate,
                                    actionToCreate = actionToCreate,
                                    location = location,
                                    storageBucketName = storageBucketName,
                                    storageFileName = storageFileName,
                                    inProgressOwnArgs = inProgressOwnArgs,
                                    inProgressParentArgs = inProgressParentArgs,
                                    database = database,
                                    storageService = storageService,
                                    storageApiClient = storageApiClient
                                )
                                result.addAll(actions)
                            }

                        }
                    }

                }

            }


            return result
        }
    }

}

data class HealthReportDTO(
    val settingsId: Long,
    val description: String,
    val healthReportTypeName: String,
    val healthReportStatusName: String,
    val color: String = "",
    val canResolve: Boolean = false,
    val problemText: String = "",
    val solutionText: String = "",
//    val customCode: String,
//    val songVersionName: String,
//    val karaokePlatformName: String,
//    val karaokeFileTypeName: String,
//    val karaokeFileActionTypeName: String,
//    val karaokeFileTypeLocationsName: String
) : Serializable