package com.svoemesto.karaokeapp.model

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Переименование локальных артефактов песни при смене имени файла (issue #323).
 *
 * Вынесено из [Song] отдельным объектом, потому что экземпляр `Song` в юнит-тесте не создать
 * (конструктор требует БД и хранилища), а правило переименования обязано быть проверяемым.
 *
 * **Что покрывается.** Все файлы, чьё имя построено от имени файла песни: исходный аудиофайл,
 * `[key].json`, стемы в `hdemucs_mmi`, вложения `done_files` (картинки, тексты, видео),
 * `done_chords`, `sheetsage`, а также символические ссылки в каталогах `symlink_*`
 * (пересоздаются с новым именем и перенаправленной целью).
 *
 * **Правило совпадения.** Файл подходит, если его имя начинается со старого имени файла песни,
 * а остаток — продолжение артефакта: пусто, либо начинается с `.` (расширение), `-`
 * (суффикс стема) или ` [` (метка: ` [karaoke].png`, ` [key].json`). Это защищает соседнюю песню,
 * имя которой начинается с переименовываемого: при переименовании `… - Пой` файл
 * `… - Пой, фестиваль.flac` не тронут — его остаток начинается с `,`.
 *
 * @see specs/323-complete-song-rename
 */
object SongArtifactRenamer {
    /** Подкаталоги папки песни, где лежат её артефакты. */
    val ARTIFACT_SUB_FOLDERS: List<String> = listOf("hdemucs_mmi", "done_files", "done_chords", "sheetsage")

    /** Каталоги символических ссылок: `symlink_mp4`, `symlink_png`, `symlink_sponsr`, … */
    const val SYMLINK_FOLDER_PREFIX: String = "symlink_"

    /**
     * Остаток имени файла — продолжение артефакта, а не другое имя песни.
     *
     * Допустимые продолжения: пусто, `.расширение`, `-суффикс-стема` и ` [метка]`
     * (метка обязательно в квадратных скобках: ` [karaoke].png`, ` [key].json`).
     * Именно `" ["`, а не любой пробел: иначе соседняя песня «… - Пой, фестиваль
     * длинная версия» подходила бы под имя «… - Пой, фестиваль».
     *
     * @param rest часть имени после старого имени файла песни.
     */
    fun isArtifactContinuation(rest: String): Boolean =
        rest.isEmpty() || rest.startsWith(".") || rest.startsWith("-") || rest.startsWith(" [")

    /**
     * Переименовать локальные артефакты песни.
     *
     * @param rootFolder папка альбома (`Song.rootFolder`).
     * @param oldFileName прежнее имя файла песни (без расширения).
     * @param newFileName новое имя файла песни.
     * @return список выполненных переименований «было → стало» (пути абсолютные).
     */
    fun renameLocal(
        rootFolder: String,
        oldFileName: String,
        newFileName: String,
    ): List<Pair<String, String>> {
        if (oldFileName == newFileName) return emptyList()
        val root = File(rootFolder)
        if (!root.isDirectory) return emptyList()

        val renamed = mutableListOf<Pair<String, String>>()
        artifactDirs(root).forEach { dir ->
            val entries: List<File> = dir.listFiles()?.sortedBy { it.name }.orEmpty()
            entries.forEach { file ->
                if (file.isDirectory) return@forEach
                if (!file.name.startsWith(oldFileName)) return@forEach
                val rest = file.name.removePrefix(oldFileName)
                if (!isArtifactContinuation(rest)) return@forEach

                val target = File(dir, newFileName + rest)
                if (target.exists()) return@forEach
                val ok =
                    if (Files.isSymbolicLink(file.toPath())) {
                        retargetSymlink(file, target, oldFileName, newFileName)
                    } else {
                        file.renameTo(target)
                    }
                if (ok) renamed.add(file.absolutePath to target.absolutePath)
            }
        }
        return renamed
    }

    /** Каталоги, в которых ищем артефакты: корень песни, её подкаталоги и каталоги ссылок. */
    private fun artifactDirs(root: File): List<File> {
        val dirs = mutableListOf(root)
        ARTIFACT_SUB_FOLDERS.forEach { name ->
            val dir = File(root, name)
            if (dir.isDirectory) dirs.add(dir)
        }
        val symlinkDirs: List<File> =
            root
                .listFiles { f -> f.isDirectory && f.name.startsWith(SYMLINK_FOLDER_PREFIX) }
                ?.sortedBy { it.name }
                .orEmpty()
        dirs.addAll(symlinkDirs)
        return dirs
    }

    /**
     * Пересоздать символическую ссылку под новым именем, перенаправив цель на новое имя файла.
     *
     * Цель ссылки хранится относительной (`../hdemucs_mmi/<файл>-accompaniment.flac`), поэтому
     * в ней достаточно заменить старое имя файла на новое — структура каталогов не меняется.
     */
    private fun retargetSymlink(
        link: File,
        target: File,
        oldFileName: String,
        newFileName: String,
    ): Boolean =
        try {
            val oldTarget = Files.readSymbolicLink(link.toPath()).toString()
            val newTarget = oldTarget.replace(oldFileName, newFileName)
            Files.deleteIfExists(link.toPath())
            Files.createSymbolicLink(target.toPath(), Paths.get(newTarget))
            true
        } catch (e: Exception) {
            println("[SongArtifactRenamer] не удалось пересоздать ссылку '${link.name}': ${e.message}")
            false
        }
}
