package com.svoemesto.karaokeapp.services.cleanup

import com.svoemesto.karaokeapp.KaraokeProperties
import java.io.File
import java.nio.file.Files

/**
 * Политика автоматического удаления на шаге жизненного цикла (FR-011).
 *
 * Вынесена в значение, а не читается напрямую в момент удаления, чтобы:
 * - удаление было проверяемо unit-тестом без записи в
 *   `/sm-karaoke/system/Karaoke.properties` (`KaraokeProperties.set` персистит файл);
 * - все три флага приходили из одного места.
 *
 * @param enabled `cleanupEnabled` — автоматическое удаление включено (дефолт `false`).
 * @param dryRun `cleanupDryRun` — только сообщаем, что было бы удалено (дефолт `true`).
 * @param journalDir `cleanupJournalDir` — каталог журнала удалений.
 * @see specs/544-song-artifact-cleanup/data-model.md
 */
data class CleanupPolicy(
    val enabled: Boolean,
    val dryRun: Boolean,
    val journalDir: String,
) {
    companion object {
        /** Политика из `KaraokeProperties` с безопасными дефолтами (FR-011). */
        fun fromProperties(): CleanupPolicy =
            try {
                CleanupPolicy(
                    enabled = KaraokeProperties.getBoolean("cleanupEnabled"),
                    dryRun = KaraokeProperties.getBoolean("cleanupDryRun"),
                    journalDir = CleanupJournal.defaultJournalDir(),
                )
            } catch (_: Exception) {
                CleanupPolicy(enabled = false, dryRun = true, journalDir = CleanupJournal.DEFAULT_JOURNAL_DIR)
            }
    }
}

/**
 * Итог удаления группы файлов на шаге жизненного цикла.
 *
 * @param planned сколько путей было предъявлено;
 * @param deleted сколько реально удалено;
 * @param failed сколько удалить не удалось (причины — в журнале);
 * @param freedBytes суммарный размер удалённого;
 * @param skippedByPolicy пути, пропущенные из-за выключенной уборки/сухого прогона;
 * @param dryRun был ли это сухой прогон.
 * @see specs/544-song-artifact-cleanup/data-model.md
 */
data class CleanupStageOutcome(
    val planned: Int,
    val deleted: Int,
    val failed: Int,
    val freedBytes: Long,
    val skippedByPolicy: Int,
    val dryRun: Boolean,
) {
    val isNoop: Boolean get() = deleted == 0 && failed == 0
}

/**
 * Удаление артефактов на шаге жизненного цикла (FR-001/FR-005/FR-003).
 *
 * Отдельный от [ArtifactCleanupService] вход: разовая чистка истории работает по отчёту
 * сухого прогона и утверждению владельца, а здесь удаление выполняется как часть
 * пайплайна (конец шага публикации, успешная загрузка в оба хранилища). Общее у них —
 * реестр классов, политика (FR-011) и журнал (FR-012).
 *
 * Гарантии:
 * - при `cleanupEnabled=false` не удаляется ничего (поведение идентично прежнему, FR-011);
 * - при `cleanupDryRun=true` файлы не удаляются, а только логируются (FR-011);
 * - защищённые классы ([ArtifactCleanupClass.isProtected]) не удаляются даже при
 *   включённой уборке (FR-010);
 * - если журнал недоступен для записи — удаление НЕ выполняется (журнал не должен
 *   оказаться беднее фактического состояния, `data-model.md` §4).
 *
 * @see specs/544-song-artifact-cleanup/data-model.md
 */
object ArtifactCleanupAutomation {
    /** Утвердивший для автоматических удалений: класс признан «не хранить» спекой. */
    const val AUTO_APPROVER = "auto:544-song-artifact-cleanup"

    /**
     * Что удаляется в конце шага публикации: демо-ролик — всегда (и при успехе, и при
     * провале, FR-001), публикационные картинки — только когда успех пришёл с обоих
     * каналов (при неудаче они нужны повторной попытке, FR-005).
     *
     * Вынесено отдельной чистой функцией, чтобы это решение было проверяемо без БД.
     */
    fun stageClassesAfterPublish(
        tgSuccess: Boolean,
        vkSuccess: Boolean,
    ): List<ArtifactCleanupClass> =
        buildList {
            add(ArtifactCleanupClass.DEMO_MP4)
            if (tgSuccess && vkSuccess) add(ArtifactCleanupClass.PUBLICATION_PNG)
        }

    /**
     * Удалять ли локальную копию mp3-стема (FR-003).
     *
     * Только когда загрузка в оба хранилища успешна. Fail-closed: неизвестность
     * (проверка второго хранилища не удалась) означает «не удалять» — иначе можно
     * уничтожить единственную оставшуюся копию.
     */
    fun shouldDeleteLocalCopy(
        deleteWhenBothStoresHave: Boolean,
        counterpartStoreHasFile: Boolean,
    ): Boolean = deleteWhenBothStoresHave && counterpartStoreHasFile

    /**
     * Удалить локальную копию, если объект уже есть в обоих хранилищах (FR-003).
     *
     * @param localPath путь локальной копии (mp3-стем в папке песни);
     * @param deleteWhenBothStoresHave признак того, что локальная копия этого типа не
     *   обязана храниться (модель `KaraokeFileType.locations` без `LOCAL_FILESYSTEM`);
     * @param counterpartStoreHasFile есть ли объект во втором хранилище;
     * @return `true`, если файл был удалён.
     */
    fun deleteLocalCopyIfBothStoresHave(
        localPath: String,
        deleteWhenBothStoresHave: Boolean,
        counterpartStoreHasFile: Boolean,
    ): Boolean {
        if (!shouldDeleteLocalCopy(deleteWhenBothStoresHave, counterpartStoreHasFile)) {
            if (deleteWhenBothStoresHave) {
                println("[cleanup] локальная копия '$localPath' оставлена: объект есть не во всех целевых хранилищах (FR-003)")
            }
            return false
        }
        return try {
            val deleted = Files.deleteIfExists(File(localPath).toPath())
            if (deleted) println("[cleanup] локальная копия удалена (объект есть в обоих хранилищах): $localPath")
            deleted
        } catch (e: Exception) {
            println("[cleanup] локальную копию '$localPath' удалить не удалось: ${e.message}")
            false
        }
    }

    /** Удалить один путь на шаге жизненного цикла. */
    fun deleteAfterStage(
        classId: ArtifactCleanupClass,
        path: String,
        approvedBy: String = AUTO_APPROVER,
        policy: CleanupPolicy = CleanupPolicy.fromProperties(),
    ): CleanupStageOutcome = deleteAfterStage(classId, listOf(path), approvedBy, policy)

    /**
     * Удалить набор путей на шаге жизненного цикла.
     *
     * @param classId класс из реестра — определяет защиту, имя журнала и причину;
     * @param paths абсолютные пути (отсутствующие молча пропускаются — это не ошибка);
     * @param approvedBy кто утвердил удаление; для автоматических удалений —
     *   [AUTO_APPROVER]: утверждение владельцем здесь не требуется, класс уже признан
     *   «не хранить» решением спеки.
     * @param policy флаги уборки (FR-011).
     */
    fun deleteAfterStage(
        classId: ArtifactCleanupClass,
        paths: List<String>,
        approvedBy: String = AUTO_APPROVER,
        policy: CleanupPolicy = CleanupPolicy.fromProperties(),
    ): CleanupStageOutcome {
        if (classId.isProtected) {
            println("[cleanup] ${classId.name}: класс защищён — удаление пропущено (FR-010)")
            return CleanupStageOutcome(paths.size, 0, 0, 0L, paths.size, dryRun = false)
        }
        val existing = paths.filter { it.isNotEmpty() && File(it).exists() }
        if (existing.isEmpty()) {
            return CleanupStageOutcome(0, 0, 0, 0L, 0, dryRun = policy.dryRun)
        }
        if (!policy.enabled) {
            println("[cleanup] ${classId.name}: уборка выключена (cleanupEnabled=false) — ${existing.size} путей оставлено")
            return CleanupStageOutcome(existing.size, 0, 0, 0L, existing.size, dryRun = false)
        }
        if (policy.dryRun) {
            existing.forEach { println("[cleanup] dry-run: удалил бы ($classId) $it") }
            return CleanupStageOutcome(existing.size, 0, 0, 0L, existing.size, dryRun = true)
        }

        val journal = CleanupJournal(policy.journalDir)
        if (!journal.journalDirWritable()) {
            println("[cleanup] ${classId.name}: каталог журнала недоступен — удаление пропущено, чтобы не потерять след (FR-012)")
            return CleanupStageOutcome(existing.size, 0, 0, 0L, existing.size, dryRun = false)
        }

        val runId = CleanupJournal.newRunId(classId.name)
        var deleted = 0
        var failed = 0
        var freed = 0L
        existing.forEach { path ->
            val size = File(path).length()
            val ok =
                try {
                    Files.deleteIfExists(File(path).toPath())
                } catch (e: Exception) {
                    println("[cleanup] ${classId.name}: не удалось удалить '$path': ${e.message}")
                    false
                }
            if (ok) {
                deleted++
                freed += size
                journal.appendDeleted(path, size, classId.name, approvedBy, runId)
            } else {
                failed++
                journal.appendFailed(path, size, classId.name, approvedBy, runId, "delete failed")
            }
        }
        println("[cleanup] ${classId.name}: удалено $deleted, ошибок $failed, освобождено $freed байт (runId=$runId)")
        return CleanupStageOutcome(existing.size, deleted, failed, freed, 0, dryRun = false)
    }
}
