package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.SongVersion
import com.svoemesto.karaokeapp.KaraokeFileActionType.*
import com.svoemesto.karaokeapp.KaraokeFileTypeLocations.*
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path

data class KaraokeFileSymlink(
    val folder: String,     // имя папки внутри root_folder-а
    val name: String = "",   // имя файла ссылки. Если пустое - имя совпадает с файлом, на который указывает ссылка.
    val platforms: List<KaraokePlatform> = emptyList(), // Платформы, для которых актуально. Пустой список - для всех.
    val songVersions: List<SongVersion> = emptyList(), // Версии, для которых актуально. Пустой список - для всех.
) {
    fun karaokeFileActions(settings: Settings, karaokeFile: KaraokeFile): MutableList<KaraokeFileAction> {

        val result: MutableList<KaraokeFileAction> = mutableListOf()

        val pathToSymlinkFolder = "${settings.rootFolder}/${folder}"
        val symlinkFileName = if (name == "") File(karaokeFile.pathToFile).name else name
        val pathToSymlinkFile = "$pathToSymlinkFolder/$symlinkFileName"

        // Создание симлинка. Возможно в случае, если файл, на который ссылается ссылка, существует на диске.
        // Проверим наличие папки, в которой надо создавать и создаем ее тоже, если нету.
        // Проверим наличие симлинка и удаляем его перед созданием нового.

        result.add(
            KaraokeFileAction(
                type = CREATE,
                location = LOCAL_FILESYSTEM,
                actions = listOf{
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
                                targetAbsolutePath = karaokeFile.pathToFile,
                                symlinkAbsolutePath = pathToSymlinkFile
                            ).wrapInQuotes(),
                            pathToSymlinkFile
                        )
                    )
                    runCommand(args =  listOf("chmod", "666", pathToSymlinkFile))
                }
            )
        )

        // Удаление симлинка. Удаляем, если есть. После удаления проверяем папку, в которой был симлинк. Если пустая - удаляем.
        result.add(
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


        return result

    }
}