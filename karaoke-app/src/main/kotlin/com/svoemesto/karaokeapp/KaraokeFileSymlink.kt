package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.SongVersion
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path

data class KaraokeFileSymlink(
    val folder: String,     // имя папки внутри root_folder-а
    val name: String = "",   // имя файла ссылки. Если пустое - имя совпадает с файлом, на который указывает ссылка.
    val platforms: List<KaraokePlatform> = emptyList(), // Платформы, для которых актуально. Пустой список - для всех.
    val songVersions: List<SongVersion> = emptyList(), // Версии, для которых актуально. Пустой список - для всех.
) {

    fun pathToSymlinkFile(rootFolder: String, pathToTargetFile: String): String {
        val pathToSymlinkFolder = "$rootFolder/$folder"
        val symlinkFileName = if (name == "") File(pathToTargetFile).name else name
        val pathToSymlinkFile = "$pathToSymlinkFolder/$symlinkFileName"
        return pathToSymlinkFile
    }

    // Проверка наличия файла симлинка на диске
    fun exists(rootFolder: String, pathToTargetFile: String, description: String = ""): Boolean {
        val pathToSymlinkFolder = "$rootFolder/$folder"
        val symlinkFileName = if (name == "") File(pathToTargetFile).name else name
        val pathToSymlinkFile = "$pathToSymlinkFolder/$symlinkFileName"
        return File(pathToSymlinkFile).exists()
    }

    // Проверка на корректность. Файл должен ссылаться на правильный существующий файл
    fun broken(rootFolder: String, pathToTargetFile: String): Boolean {

        var broken: Boolean
        if (!exists(rootFolder = rootFolder, pathToTargetFile = pathToTargetFile)) return false

        val pathToSymlinkFolder = "$rootFolder/$folder"
        val symlinkFileName = if (name == "") File(pathToTargetFile).name else name
        val pathToSymlinkFile = "$pathToSymlinkFolder/$symlinkFileName"

        // Проверяем, является ли файл на диске действительно символьной ссылкой
        val symlinkPath = Paths.get(pathToSymlinkFile)
        if (!Files.isSymbolicLink(symlinkPath)) {
            broken = true
        } else {
            // Относительный путь, куда указывает ссылка
            val targetRelativePath: Path = Files.readSymbolicLink(symlinkPath)
            // Абсолютный путь, куда указывает ссылка
            val targetAbsolutePath = calculateAbsolutePathFromSymlink(targetRelativePath.toString(), pathToSymlinkFile)
            // Существует ли файл, на который ссылается ссылка
            val existsTargetFile = File(targetAbsolutePath).exists()
            val isAbsolute = targetRelativePath.isAbsolute
            if (!existsTargetFile) {
                broken = true
            } else {
                // Файл, на который указывает ссылка, существует - проверим, что указывает на нужный нам файл
                if (targetAbsolutePath == pathToTargetFile) {
                    if (isAbsolute) {
                        broken = true
                    } else {
                        broken = false
                    }
                } else {
                    broken = true
                }
            }
        }
        return broken
    }

    fun actionToCreate(rootFolder: String, pathToTargetFile: String): () -> Unit {
        val pathToSymlinkFolder = "$rootFolder/$folder"
        val symlinkFileName = if (name == "") File(pathToTargetFile).name else name
        val pathToSymlinkFile = "$pathToSymlinkFolder/$symlinkFileName"
        return {
            val symlinkFolder = File(pathToSymlinkFile).parent

            if (!File(symlinkFolder).exists()) {
                Files.createDirectories(Path(symlinkFolder))
                runCommand(listOf("chmod", "777", symlinkFolder))
            }

            if (File(pathToSymlinkFile).exists()) {
                runCommand(args = listOf("rm", "-f", pathToSymlinkFile))
            }

            runCommand(
                args = listOf(
                    "ln", "-s",
                    calculateRelativePathForSymlink(
                        targetAbsolutePath = pathToTargetFile,
                        symlinkAbsolutePath = pathToSymlinkFile
                    ).wrapInQuotes(),
                    pathToSymlinkFile
                )
            )
            runCommand(args = listOf("chmod", "666", pathToSymlinkFile))
        }
    }

    fun actionToDelete(rootFolder: String, pathToTargetFile: String): () -> Unit {
        val pathToSymlinkFolder = "$rootFolder/$folder"
        val symlinkFileName = if (name == "") File(pathToTargetFile).name else name
        val pathToSymlinkFile = "$pathToSymlinkFolder/$symlinkFileName"
        return actionToDeleteFileAndFolderIfFolderEmpty(pathToFile = pathToSymlinkFile)
    }

}