package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.HealthReportStatus.*
import com.svoemesto.karaokeapp.KaraokeFileTypeLocations.*
import com.svoemesto.karaokeapp.KaraokeFileActionType.*
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.SongVersion
import java.io.File
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.String

data class HealthReport(
    val settings: Settings,
    val status: HealthReportStatus,
    val description: String,
    val customCode: String? = null,
    val customActions: List<() -> Unit> = emptyList(),
    val karaokePlatform: KaraokePlatform?,
    val songVersion: SongVersion?,
    val karaokeFileType: KaraokeFileType? = null,
    val karaokeFileActionType: KaraokeFileActionType? = null,
    val karaokeFileTypeLocations: KaraokeFileTypeLocations? = null,
) {
    fun toDTO(): HealthReportDTO {
        return HealthReportDTO(
            healthReportStatusName = status.name,
            color = status.color,
            description = description,
            settingsId = settings.id,
            customCode = customCode ?: "",
            songVersionName = songVersion?.name ?: "",
            karaokePlatformName = karaokePlatform?.name ?: "",
            karaokeFileTypeName = karaokeFileType?.name ?: "",
            karaokeFileActionTypeName = karaokeFileActionType?.name ?: "",
            karaokeFileTypeLocationsName = karaokeFileTypeLocations?.name ?: ""
        )
    }

    fun executeCustomActions() = customActions.forEach { customAction -> customAction() }
    fun executeKaraokeFileActions(type: KaraokeFileActionType, location: KaraokeFileTypeLocations) {
        val karaokeFiles = settings.karaokeFiles()
        settings.karaokeFiles().firstOrNull { it.type == karaokeFileType && it.songVersion == songVersion && it.karaokePlatform == karaokePlatform }?.let { karaokeFile ->
            karaokeFile.karaokeFileActions.firstOrNull { it.type == type && it.location == location}?. let { karaokeFileAction ->
                karaokeFileAction.actions.forEach { action ->
                    action()
                }
            }
        }
    }

    companion object {

        fun getHealthReport(settings: Settings, dto: HealthReportDTO): HealthReport? {
            val healthReportList = getHealthReportList(settings = settings)
            val result = healthReportList.firstOrNull { healthReport ->
                healthReport.status.name == dto.healthReportStatusName &&
                        (healthReport.customCode ?: "") == dto.customCode &&
                        (healthReport.songVersion?.name ?: "") == dto.songVersionName &&
                        (healthReport.karaokeFileType?.name ?: "") == dto.karaokeFileTypeName &&
                        (healthReport.karaokeFileActionType?.name ?: "") == dto.karaokeFileActionTypeName &&
                        (healthReport.karaokeFileTypeLocations?.name ?: "") == dto.karaokeFileTypeLocationsName
            }
            println("getHealthReport = $result")
            return result
        }

        fun getHealthReportList(settings: Settings): List<HealthReport> {
            val result: MutableList<HealthReport> = mutableListOf()

            with(settings) {
                karaokeFiles().forEach { karaokeFile ->
                    val karaokeFileResult: MutableList<HealthReport> = mutableListOf()
                    val textFile = karaokeFile.type.name
                    val textPlatforma = karaokeFile.karaokePlatform?.name
                    val textSongVersion = karaokeFile.songVersion?.name
                    val description = listOfNotNull(textFile, textPlatforma, textSongVersion).joinToString("/")
                    val type = karaokeFile.type

                    // Файл может быть
                    if (karaokeFile.canBe) {
                        // Файл должен быть на диске, путь к файлу не пустой, файла на диске нет
                        if (type.willBeInFileSystem && karaokeFile.pathToFile != "" && !karaokeFile.filePresentInFilesystem()) {
                            // Для файла уже есть процесс на его создание в процессе ожидания
                            val haveWaitingCreateProcessForFile = KaraokeFile.waitingCreateProcessForFile(settings = settings, karaokeFileType = type) != null
                            if (haveWaitingCreateProcessForFile) {
                                karaokeFileResult.add(
                                    HealthReport(
                                        settings = this,
                                        status = IN_PROGRESS,
                                        description = "$description - отсутствует файл на диске '${karaokeFile.pathToFile}', но есть задание в процессах",
                                        karaokeFileType = type,
                                        songVersion = karaokeFile.songVersion,
                                        karaokePlatform = karaokeFile.karaokePlatform
                                    )
                                )
                            } else {
                                val karaokeFileCanBeCreate = karaokeFile.canBeCreateKaraokeFile(settings = this)
                                val canBeCreateKaraokeFile = karaokeFileCanBeCreate.canBeCreate
                                val reason = karaokeFileCanBeCreate.reason
                                // Может ли быть файл создан?
                                if (canBeCreateKaraokeFile) {
                                    karaokeFileResult.add(
                                        HealthReport(
                                            settings = this,
                                            status = ERROR,
                                            description = "$description - отсутствует файл на диске '${karaokeFile.pathToFile}'. $reason",
                                            karaokeFileType = type,
                                            songVersion = karaokeFile.songVersion,
                                            karaokePlatform = karaokeFile.karaokePlatform,
                                            karaokeFileActionType = CREATE,
                                            karaokeFileTypeLocations = LOCAL_FILESYSTEM,
                                            customActions = karaokeFile.getActions(type = CREATE, location = LOCAL_FILESYSTEM)
                                        )
                                    )
                                } else {
                                    karaokeFileResult.add(
                                        HealthReport(
                                            settings = this,
                                            status = FATAL_ERROR,
                                            description = "$description - отсутствует файл на диске '${karaokeFile.pathToFile}'. $reason",
                                            karaokeFileType = type,
                                            songVersion = karaokeFile.songVersion,
                                            karaokePlatform = karaokeFile.karaokePlatform
                                        )
                                    )
                                }
                            }
                            // Файл должен быть на диске, путь к файлу не пустой, файл на диске есть - надо проверить его симлинки
                        } else if (type.willBeInFileSystem && karaokeFile.pathToFile != "" && karaokeFile.filePresentInFilesystem()) {
                            type.symlinks.forEachIndexed { indexSymlink, symlink ->

                                val pathToSymlinkFolder = "$rootFolder/${symlink.folder}"
                                val symlinkFileName = if (symlink.name == "") File(karaokeFile.pathToFile).name else symlink.name
                                val pathToSymlinkFile = "$pathToSymlinkFolder/$symlinkFileName"

                                var needToCreateSymlink = false
                                var reason = ""
                                // Проверяем наличие самого файла symlink на диске
                                if (File(pathToSymlinkFile).exists()) {
                                    // Проверяем, является ли файл на диске действительно символьной ссылкой
                                    val symlinkPath = Paths.get(pathToSymlinkFile)

                                    if (!Files.isSymbolicLink(symlinkPath)) {
                                        println("Указанный путь '$pathToSymlinkFile' не является символической ссылкой.")
                                    } else {
                                        // Относительный путь, куда указывает ссылка
                                        val targetRelativePath: Path = Files.readSymbolicLink(symlinkPath)

                                        // Абсолютный путь, куда указывает ссылка
                                        val targetAbsolutePath = calculateAbsolutePathFromSymlink(targetRelativePath.toString(), pathToSymlinkFile)

                                        // Существует ли файл, на который ссылается ссылка
                                        val exists = File(targetAbsolutePath).exists()

                                        val symlinkAbsolutePath = symlinkPath.toAbsolutePath()
                                        symlinkAbsolutePath.parent
                                        val isAbsolute = targetRelativePath.isAbsolute

                                        if (!exists) {
                                            // Если файл, на который указывает ссылка не существует (чего быть не может, если ссылка указывает на нужный нам файл)
                                            reason = "Символьная ссылка $pathToSymlinkFile должна указывать на файл ${karaokeFile.pathToFile}, а указывает на несуществующий файл $targetAbsolutePath. Удаляем неправильную ссылку и создаём правильную."
                                            needToCreateSymlink = true // Надо создать файл symlink
                                        } else {
                                            // Файл, на который указывает ссылка, существует - проверим, что указывает на нужный нам файл
                                            if (targetAbsolutePath == karaokeFile.pathToFile) {
                                                if (isAbsolute) {
                                                    reason = "Символьная ссылка $pathToSymlinkFile должна указывать на файл ${karaokeFile.pathToFile} по относительному пути, а указывает по абсолютному. Удаляем неправильную ссылку и создаём правильную."
                                                    needToCreateSymlink = true // Надо создать файл symlink
                                                } else {
                                                    // Всё хорошо, ссылка ссылается на правильный и существующий файл
                                                }
                                            } else {
                                                reason = "Символьная ссылка $pathToSymlinkFile должна указывать на файл ${karaokeFile.pathToFile}, а указывает на файл $targetAbsolutePath. Удаляем неправильную ссылку и создаём правильную."
                                                needToCreateSymlink = true // Надо создать файл symlink
                                            }
                                        }

                                    }
                                } else {
                                    reason = "Символьная ссылка $pathToSymlinkFile на файл ${karaokeFile.pathToFile} отсутствует. Создаём."
                                    needToCreateSymlink = true // Надо создать файл symlink
                                }

                                val karaokeFileActions = symlink.karaokeFileActions(settings = this, karaokeFile = karaokeFile)

                                if (needToCreateSymlink) {
                                    karaokeFileResult.add(
                                        HealthReport(
                                            settings = this,
                                            status = ERROR,
                                            description = "$description - починка symlinka'. $reason",
                                            karaokeFileType = type,
                                            songVersion = karaokeFile.songVersion,
                                            karaokePlatform = karaokeFile.karaokePlatform,
                                            customCode = "SYMLINK_REPAIR;$indexSymlink",
                                            customActions = karaokeFileActions.firstOrNull { it.type == CREATE && it.location == LOCAL_FILESYSTEM }?.actions ?: emptyList()
                                        )
                                    )
                                }
                            }
                        }
                        if (type.willBeInLocalStorage && karaokeFile.pathToFile != "" && !karaokeFile.filePresentInStorage()) {
                            karaokeFileResult.add(
                                HealthReport(
                                    settings = this,
                                    status = ERROR,
                                    description = "$description - отсутствует файл в хранилище '${karaokeFile.storageBucketName}/${karaokeFile.storageFileName}'",
                                    karaokeFileType = type,
                                    songVersion = karaokeFile.songVersion,
                                    karaokePlatform = karaokeFile.karaokePlatform,
                                    karaokeFileActionType = CREATE,
                                    karaokeFileTypeLocations = LOCAL_STORAGE,
                                    customActions = karaokeFile.getActions(type = CREATE, location = LOCAL_STORAGE)
                                )
                            )
                        }
                    } else {
                        // Файлов быть не должно. Проверим, есть ли они (для PROJECT) и удалим, если есть.
                        if (type.karaokeFileTypeKind == KaraokeFileTypeKind.PROJECT) {
                            if (karaokeFile.filePresentInFilesystem()) {
                                karaokeFileResult.add(
                                    HealthReport(
                                        settings = this,
                                        status = ERROR,
                                        description = "$description - есть файл на диске '${karaokeFile.pathToFile}', но не должен быть.",
                                        karaokeFileType = type,
                                        songVersion = karaokeFile.songVersion,
                                        karaokePlatform = karaokeFile.karaokePlatform,
                                        karaokeFileActionType = DELETE,
                                        karaokeFileTypeLocations = LOCAL_FILESYSTEM,
                                        customActions = karaokeFile.getActions(type = DELETE, location = LOCAL_FILESYSTEM)
                                    )
                                )
                            }
                        }
                    }

                    if (karaokeFileResult.isEmpty()) {
                        karaokeFileResult.add(
                            HealthReport(
                                settings = this,
                                status = OK,
                                description = "$description - проблем не обнаружено",
                                karaokeFileType = type,
                                songVersion = karaokeFile.songVersion,
                                karaokePlatform = karaokeFile.karaokePlatform
                            )
                        )
                    }

                    result.addAll(karaokeFileResult)

                }
            }

            return result.toList()
        }

    }

}

data class HealthReportDTO(
    val healthReportStatusName: String,
    val color: String,
    val description: String,
    val settingsId: Long,
    val customCode: String,
    val songVersionName: String,
    val karaokePlatformName: String,
    val karaokeFileTypeName: String,
    val karaokeFileActionTypeName: String,
    val karaokeFileTypeLocationsName: String
) : Serializable