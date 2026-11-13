package com.svoemesto.karaokeapp.services

import org.slf4j.LoggerFactory
import java.io.File

/**
 * Перенос объектов между ключами хранилища (KARAOKE-428).
 *
 * Ключ объекта песни — `$автор/$год - $альбом/$имя песни` + суффикс артефакта.
 * Смена альбома, автора или года меняет **префикс** ключа, и объекты оставались под
 * старым путём: накапливались устаревшие копии (745 объектов, 4,38 ГБ, замер
 * 2026-11-12, инвентаризация KARAOKE-418).
 *
 * Старый ключ удаляется только после успешной записи нового. Отсутствие объекта —
 * не ошибка (объект мог быть не создан).
 */
object StorageObjectMigration {
    /** Категория логов. */
    const val CATEGORY = "infra.storage.migration"

    private val log = LoggerFactory.getLogger(CATEGORY)

    /**
     * Перенести один объект из старого ключа в новый в обоих хранилищах.
     *
     * @param oldKey старый ключ.
     * @param newKey новый ключ.
     * @param description что переносим — для сообщения об ошибке.
     * @param localSourcePath локальный файл-источник, если он есть (быстрее, чем получение объекта из хранилища).
     * @param storageService локальное хранилище.
     * @param storageApiClient удалённое хранилище.
     * @return `true`, если объект перенесён хотя бы в одно хранилище.
     */
    fun move(
        oldKey: String,
        newKey: String,
        description: String,
        localSourcePath: String? = null,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
    ): Boolean {
        var moved = false
        // KARAOKE-428: новый ключ может быть уже занят — например, в целевом альбоме
        // есть другая песня с тем же именем файла. Перезаписывать её объекты нельзя:
        // перенос пропускается, старый объект остаётся (его подберёт разовая уборка).
        if (exists(newKey, storageService, storageApiClient)) {
            log.warn(
                "event=storage.migration.skipped reason=target_exists key='{}' — {}",
                newKey,
                description,
            )
            return false
        }
        try {
            if (storageService.fileExists(BUCKET, oldKey)) {
                val source = localSourcePath?.takeIf { File(it).exists() }
                if (source != null) {
                    storageService.uploadFile(BUCKET, newKey, source)
                } else {
                    val tmp = File.createTempFile("storage-migrate-", tempSuffix(oldKey))
                    try {
                        storageService.downloadFile(BUCKET, oldKey, tmp.absolutePath)
                        storageService.uploadFile(BUCKET, newKey, tmp.absolutePath)
                    } finally {
                        tmp.delete()
                    }
                }
                storageService.deleteFile(BUCKET, oldKey)
                moved = true
            }
        } catch (e: Exception) {
            log.warn("event=storage.migration.failed scope=local target='{}' — {}", newKey, e.toString())
        }
        try {
            if (storageApiClient.fileExists(BUCKET, oldKey)) {
                val source = localSourcePath?.takeIf { File(it).exists() }
                if (source != null) {
                    storageApiClient.uploadFile(BUCKET, newKey, source)
                } else {
                    val tmp = File.createTempFile("storage-migrate-remote-", tempSuffix(oldKey))
                    try {
                        storageApiClient.downloadFile(BUCKET, oldKey, tmp.absolutePath)
                        storageApiClient.uploadFile(BUCKET, newKey, tmp.absolutePath)
                    } finally {
                        tmp.delete()
                    }
                }
                storageApiClient.deleteFile(BUCKET, oldKey).block()
                moved = true
            }
        } catch (e: Exception) {
            log.warn("event=storage.migration.failed scope=remote target='{}' — {}", newKey, e.toString())
        }
        if (!moved) {
            log.debug("event=storage.migration.absent key='{}' — {}", oldKey, description)
        }
        return moved
    }

    /**
     * Перенести набор объектов: ключи сопоставляются по индексу.
     *
     * @param oldKeys прежние ключи.
     * @param newKeys новые ключи (того же размера).
     * @param description что переносим — для сообщения.
     */
    fun moveAll(
        oldKeys: List<String>,
        newKeys: List<String>,
        description: String,
        localSourcePaths: Map<String, String> = emptyMap(),
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
    ): Int {
        if (oldKeys.size != newKeys.size) return 0
        var moved = 0
        oldKeys.forEachIndexed { index, oldKey ->
            if (move(
                    oldKey = oldKey,
                    newKey = newKeys[index],
                    description = description,
                    localSourcePath = localSourcePaths[oldKey],
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                )
            ) {
                moved++
            }
        }
        return moved
    }

    /** Есть ли объект по ключу хотя бы в одном из хранилищ. */
    private fun exists(
        key: String,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
    ): Boolean =
        try {
            storageService.fileExists(BUCKET, key) || storageApiClient.fileExists(BUCKET, key)
        } catch (e: Exception) {
            false
        }

    /** Имя бакета с объектами песен. */
    const val BUCKET = "karaoke"

    /** Расширение временного файла — по расширению исходного ключа. */
    private fun tempSuffix(key: String): String {
        val extension = key.substringAfterLast('.', "")
        return if (extension.isBlank() || extension.length > 5) ".bin" else ".$extension"
    }
}
