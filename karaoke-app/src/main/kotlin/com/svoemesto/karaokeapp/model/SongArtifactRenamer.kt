package com.svoemesto.karaokeapp.model

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * BR-131 (аудит #336, пакет `PKG-S3-23`, OpenProject #395): категория логов каскадного
 * переименования артефактов песни.
 */
private val renamerLog = LoggerFactory.getLogger("infra.catalog.rename")

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
     * BR-131 (аудит #336, пакет `PKG-S3-23`, OpenProject #395): неудача переименования
     * одного артефакта.
     *
     * @property path путь к артефакту, который НЕ удалось переименовать.
     * @property reason причина (кратко).
     */
    data class RenameFailure(
        val path: String,
        val reason: String,
    )

    /**
     * BR-131: результат каскадного переименования — что переименовалось, а что нет.
     *
     * Раньше `renameLocal` возвращал только успешные переименования, а неудачный
     * `file.renameTo(target)` (нет прав на каталог, read-only mount, ошибка ФС) ТИХО
     * пропускался: в логе не было ничего, а песня сохранялась в БД с новым `fileName`
     * — при том что часть артефактов осталась под старым именем.
     */
    data class RenameOutcome(
        val renamed: List<Pair<String, String>> = emptyList(),
        val failures: List<RenameFailure> = emptyList(),
    ) {
        val hasFailures: Boolean get() = failures.isNotEmpty()
    }

    /**
     * Переименовать локальные артефакты песни.
     *
     * @param rootFolder папка альбома (`Song.rootFolder`).
     * @param oldFileName прежнее имя файла песни (без расширения).
     * @param newFileName новое имя файла песни.
     * @return [RenameOutcome]: список выполненных переименований «было → стало»
     *   (пути абсолютные) И список неудач с причиной.
     */
    fun renameLocal(
        rootFolder: String,
        oldFileName: String,
        newFileName: String,
    ): RenameOutcome {
        if (oldFileName == newFileName) return RenameOutcome()
        val root = File(rootFolder)
        if (!root.isDirectory) return RenameOutcome()

        val renamed = mutableListOf<Pair<String, String>>()
        val failures = mutableListOf<RenameFailure>()
        artifactDirs(root).forEach { dir ->
            val entries: List<File> = dir.listFiles()?.sortedBy { it.name }.orEmpty()
            entries.forEach { file ->
                if (file.isDirectory) return@forEach
                if (!file.name.startsWith(oldFileName)) return@forEach
                val rest = file.name.removePrefix(oldFileName)
                if (!isArtifactContinuation(rest)) return@forEach

                val target = File(dir, newFileName + rest)
                if (target.exists()) {
                    // BR-131: цель уже существует — это ТОЖЕ неудача (артефакт останется
                    // под старым именем), раньше и она не попадала ни в лог, ни в результат.
                    failures.add(RenameFailure(file.absolutePath, "цель уже существует: ${target.absolutePath}"))
                    renamerLog.warn("renameLocal: цель уже существует, артефакт не переименован: '{}'", file.absolutePath)
                    return@forEach
                }
                val ok =
                    if (Files.isSymbolicLink(file.toPath())) {
                        retargetSymlink(file, target, oldFileName, newFileName)
                    } else {
                        file.renameTo(target)
                    }
                if (ok) {
                    renamed.add(file.absolutePath to target.absolutePath)
                } else {
                    // BR-131: `renameTo` вернул false — причина не диагностируется,
                    // файл остаётся под старым именем. Пишем WARN и отдаём неудачу наружу.
                    val reason = "не удалось переименовать (нет прав на каталог, read-only mount или ошибка ФС)"
                    failures.add(RenameFailure(file.absolutePath, reason))
                    renamerLog.warn("renameLocal: {} — '{}'", reason, file.absolutePath)
                }
            }
        }
        return RenameOutcome(renamed = renamed, failures = failures)
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
            // BR-131: причина сбоя — в SLF4J (категория + Throwable), а не в stdout.
            renamerLog.warn("retargetSymlink: не удалось пересоздать ссылку '{}'", link.name, e)
            false
        }
}
