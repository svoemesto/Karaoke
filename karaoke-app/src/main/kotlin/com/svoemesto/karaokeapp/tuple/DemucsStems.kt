package com.svoemesto.karaokeapp.tuple

import java.io.File

/**
 * Перенос стемов demucs из временной папки кортежа в папку результата.
 *
 * ИНЦИДЕНТ 2026-11-03 (песня 30428, «Песня друзей»): перенос делали отдельными
 * шагами цепочки (`mv` + `chmod`) **после** шага `docker run`. Когда demucs ничего
 * не создавал — типичный случай «GPU занят, `torch.cuda.OutOfMemoryError`» — цепочка
 * рвалась на первом же `mv` («No such file or directory»), и финальный шаг
 * `finalizeDemucs` не доходил никогда. А он-то и ставит повтор без GPU
 * (`argsDemucs*RetryCpu`, спека 674 FR-009) и дописывает длительность песни
 * (`song_ms`). Поэтому повтор «на CPU» не запускался, длительность не определялась,
 * а починка HR упиралась в тот же сбой.
 *
 * Теперь перенос — часть проверки: сначала переносим (что есть), потом проверяем
 * содержимое стемов (`ArtifactIntegrity.verifyDemucsStems`) и решаем судьбу кортежа.
 */
object DemucsStems {
    /** Результат переноса: что перенесли и чего не хватило. */
    data class MoveResult(
        val moved: List<String>,
        val missing: List<String>,
    ) {
        val complete: Boolean get() = missing.isEmpty()
    }

    /**
     * Переносит `file-<stem>.flac` из [scopeDir] в целевые файлы [destinations].
     *
     * @param scopeDir временная папка кортежа (`$PATH_TO_TEMP_DEMUCS_FOLDER/<scope>`).
     * @param destinations имя стема → целевой файл (абсолютный путь).
     */
    fun move(
        scopeDir: File,
        destinations: Map<String, File>,
    ): MoveResult {
        if (!scopeDir.isDirectory) {
            return MoveResult(emptyList(), destinations.keys.toList())
        }
        val moved = mutableListOf<String>()
        val missing = mutableListOf<String>()
        destinations.forEach { (stem, destination) ->
            val source = File(scopeDir, "file-$stem.flac")
            if (!source.isFile || source.length() == 0L) {
                missing += stem
                return@forEach
            }
            val ok =
                try {
                    destination.parentFile?.mkdirs()
                    if (source.renameTo(destination)) {
                        true
                    } else {
                        source.copyTo(destination, overwrite = true)
                        true
                    }
                } catch (e: Exception) {
                    false
                }
            if (ok) {
                // Права 666 — как у прежних шагов `chmod 666`: файлы читает и переносит
                // соседний процесс (загрузка в хранилище), работающий от другого uid.
                destination.setReadable(true, false)
                destination.setWritable(true, false)
                moved += stem
            } else {
                missing += stem
            }
        }
        return MoveResult(moved, missing)
    }
}
