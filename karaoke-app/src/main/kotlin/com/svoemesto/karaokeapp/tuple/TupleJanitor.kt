package com.svoemesto.karaokeapp.tuple

import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Уборка осиротевших временных каталогов кортежей.
 *
 * Удаляется подкаталог корня, имя которого — числовой `scopeId`, если:
 * - для scope нет живых строк (`WAITING/WORKING/CREATING`, не soft-deleted);
 * - возраст каталога (mtime) больше TTL.
 *
 * Корень, нечисловые каталоги и каталоги живых кортежей не трогаются. Ошибка удаления —
 * лог и продолжение (fail-open), как у `cleanupOldLogs`.
 *
 * @see specs/674-kortege-execution/contracts/tuple-cli.md §5
 */
class TupleJanitor(
    private val roots: List<String>,
    private val ttlHours: Long,
    private val isLive: (Long) -> Boolean,
    private val now: () -> Instant = Instant::now,
    private val log: (String) -> Unit = { println(it) },
) {
    /** @return пути удалённых каталогов (для теста/лога). */
    fun sweep(): List<String> {
        val removed = mutableListOf<String>()
        val threshold = now().minus(ttlHours, ChronoUnit.HOURS)
        roots.forEach { rootPath ->
            val root = File(rootPath)
            if (!root.isDirectory) return@forEach
            root.listFiles()?.forEach { candidate ->
                if (!candidate.isDirectory) return@forEach
                val scope = candidate.name.toLongOrNull() ?: return@forEach
                if (isLive(scope)) {
                    log("TupleJanitor: пропущен живой $candidate")
                    return@forEach
                }
                val mtime = Instant.ofEpochMilli(candidate.lastModified())
                if (mtime.isAfter(threshold)) return@forEach
                try {
                    if (candidate.deleteRecursively()) {
                        removed += candidate.absolutePath
                        log("TupleJanitor: удалён ${candidate.absolutePath} (возраст > $ttlHours ч)")
                    }
                } catch (e: Exception) {
                    log("TupleJanitor: не удалось удалить ${candidate.absolutePath}: ${e.message}")
                }
            }
        }
        return removed
    }
}
