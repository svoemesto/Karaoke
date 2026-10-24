package com.svoemesto.karaokeapp.services.cleanup

import com.svoemesto.karaokeapp.KaraokeFileType
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * Состояние прогона чистки (`data-model.md` §5).
 *
 * @param counters счётчики прогона: сколько запланировано, удалено, не удалось, освобождено байт;
 * @param journalPath путь журнала — пишется построчно по мере обработки (FR-012).
 * @see specs/544-song-artifact-cleanup/data-model.md
 */
data class CleanupRunStatus(
    val runId: String,
    val classId: String,
    val state: CleanupRunState,
    val plannedCount: Long,
    val deletedCount: Long,
    val failedCount: Long,
    val freedBytes: Long,
    val journalPath: String,
    val approvedBy: String,
    val errors: List<String> = emptyList(),
)

/** Состояния прогона: `PLANNED` → `APPROVED` → `RUNNING` → `DONE|PARTIAL|FAILED`. */
enum class CleanupRunState {
    PLANNED,
    APPROVED,
    RUNNING,
    DONE,
    PARTIAL,
    FAILED,
}

/** Ошибка чистки с кодом HTTP, который должен вернуть контроллер. */
class CleanupException(
    val code: Int,
    val errorCode: String,
    override val message: String,
) : RuntimeException(message)

/**
 * Разовая чистка истории: применение утверждённого отчёта (FR-013, FR-011, FR-012).
 *
 * Инварианты:
 * - защищённые классы (`PROTECTED`, `NOT_TOUCH`) отклоняются **всегда** (FR-010);
 * - `apply` без отпечатка или с изменившимся составом отклоняется (утверждается конкретный набор);
 * - класс `STEMS_MP3_LOCAL` не удаляется, пока не выполнена правка модели ожидаемых файлов (FR-004);
 * - удаление выполняется **вне HTTP-потока** (Constitution IV): `apply` возвращает `runId`, прогресс — в [status];
 * - журнал недоступен → `503`, ничего не удаляется.
 *
 * @see specs/544-song-artifact-cleanup/contracts/cleanup-api.md
 */
class ArtifactCleanupService(
    private val planner: ArtifactCleanupPlanner = ArtifactCleanupPlanner(),
    private val journal: CleanupJournal = CleanupJournal(),
) {
    companion object {
        /** BR-111: сколько отчётов держим в heap (каждый — до десятков тысяч путей). */
        internal const val MAX_RETAINED_PLANS = 32

        /** BR-111: сколько статусов прогонов держим в heap. */
        internal const val MAX_RETAINED_RUNS = 128

        /** BR-111: TTL отчёта; после него `planById` отвечает 404, запись вытесняется. */
        internal const val PLAN_TTL_MINUTES = 30L
    }

    /**
     * Хранилища отчётов и прогонов (BR-111, аудит #336).
     *
     * Раньше это были `ConcurrentHashMap` без удаления: каждый `POST /plan`
     * навсегда оставлял в heap полный список путей (десятки тысяч строк), а
     * `runs` — историю прогонов. Теперь — LRU по числу записей + TTL для
     * отчётов; отчёт удаляется после терминального статуса `apply`.
     * Доступ — под `synchronized` (низкочастотные admin-вызовы, горячего пути нет).
     */
    private val plans = LinkedHashMap<String, CleanupPlan>()
    private val runs = LinkedHashMap<String, CleanupRunStatus>()
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "artifact-cleanup") }

    private fun putPlan(plan: CleanupPlan) {
        synchronized(plans) {
            plans[plan.planId] = plan
            while (plans.size > MAX_RETAINED_PLANS) {
                val eldest = plans.entries.iterator()
                if (!eldest.hasNext()) break
                eldest.next()
                eldest.remove()
            }
        }
    }

    private fun getPlan(planId: String): CleanupPlan? {
        synchronized(plans) {
            val plan = plans[planId] ?: return null
            if (isPlanExpired(plan)) {
                plans.remove(planId)
                return null
            }
            return plan
        }
    }

    private fun isPlanExpired(plan: CleanupPlan): Boolean =
        runCatching {
            val generatedAt = LocalDateTime.parse(plan.generatedAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            generatedAt.plusMinutes(PLAN_TTL_MINUTES).isBefore(LocalDateTime.now())
        }.getOrDefault(false)

    private fun putRun(run: CleanupRunStatus) {
        synchronized(runs) {
            runs[run.runId] = run
            while (runs.size > MAX_RETAINED_RUNS) {
                val eldest = runs.entries.iterator()
                if (!eldest.hasNext()) break
                eldest.next()
                eldest.remove()
            }
        }
    }

    private fun getRun(runId: String): CleanupRunStatus? = synchronized(runs) { runs[runId] }

    private fun removePlan(planId: String) {
        synchronized(plans) { plans.remove(planId) }
    }

    /** Классы реестра для `GET /api/cleanup/classes`. */
    fun classes(): List<ArtifactCleanupClass> = ArtifactCleanupClass.entries.toList()

    /**
     * Сухой прогон по классу: строит и запоминает отчёт.
     *
     * @throws CleanupException `404` — неизвестный класс; `400` — класс защищён;
     *   `503` — недоступен каталог журнала (без журнала чистка невозможна, FR-012).
     */
    fun plan(classIdRaw: String): CleanupPlan {
        val classId = ArtifactCleanupClass.byId(classIdRaw) ?: throw unknownClass(classIdRaw)
        if (classId.isProtected) {
            throw CleanupException(400, "protected_class", "класс ${classId.name} защищён и не может быть удалён (FR-010)")
        }
        if (!journal.journalDirWritable()) {
            throw CleanupException(503, "journal_unavailable", "каталог журнала '${journal.journalDir}' недоступен для записи")
        }
        val plan = planner.plan(classId)
        putPlan(plan)
        return plan
    }

    /** Найти ранее построенный отчёт. */
    fun planById(planId: String): CleanupPlan =
        getPlan(planId) ?: throw CleanupException(404, "unknown_plan", "отчёт '$planId' не найден; постройте новый plan")

    /** Статус прогона. */
    fun status(runId: String): CleanupRunStatus =
        getRun(runId) ?: throw CleanupException(404, "unknown_run", "прогон '$runId' не найден")

    /**
     * Запустить удаление по утверждённому отчёту.
     *
     * @throws CleanupException `400` — `confirm != true` или пустой `approvedBy`;
     *   `404` — неизвестный `planId`; `409` — отпечаток не совпал с текущим составом либо
     *   для `STEMS_MP3_LOCAL` не выполнена правка модели здоровья; `503` — журнал недоступен.
     */
    fun apply(
        planId: String,
        fingerprint: String,
        approvedBy: String,
        confirm: Boolean,
    ): CleanupRunStatus {
        val plan = planById(planId)
        if (!confirm) throw CleanupException(400, "not_confirmed", "требуется явное подтверждение (confirm=true)")
        if (approvedBy.isBlank()) throw CleanupException(400, "no_approver", "поле approvedBy обязательно (FR-012)")
        val classId = ArtifactCleanupClass.byId(plan.classId) ?: throw unknownClass(plan.classId)
        if (classId.isProtected) {
            throw CleanupException(400, "protected_class", "класс ${classId.name} защищён и не может быть удалён (FR-010)")
        }
        if (plan.fileCount == 0L) {
            throw CleanupException(400, "empty_plan", "в отчёте нет ни одного файла — удаление не имеет смысла")
        }
        if (classId == ArtifactCleanupClass.STEMS_MP3_LOCAL && KaraokeFileType.MP3_ACCOMPANIMENT.willBeInFileSystem) {
            throw CleanupException(
                409,
                "model_not_fixed",
                "класс STEMS_MP3_LOCAL требует правки модели ожидаемых файлов (FR-004/FR-013): " +
                    "иначе проверка здоровья начнёт перекодировать стемы заново",
            )
        }
        // Отпечаток — «печать» утверждённого набора: состав не должен измениться.
        val current = planner.plan(classId)
        if (current.fingerprint != plan.fingerprint) {
            throw CleanupException(
                409,
                "fingerprint_mismatch",
                "состав файлов класса изменился с момента отчёта (${plan.fileCount} → ${current.fileCount}); требуется новый plan",
            )
        }
        if (fingerprint != plan.fingerprint) {
            throw CleanupException(409, "fingerprint_mismatch", "отпечаток не совпадает с отчётом; требуется новый plan")
        }
        if (!journal.journalDirWritable()) {
            throw CleanupException(503, "journal_unavailable", "каталог журнала '${journal.journalDir}' недоступен для записи")
        }

        val runId = CleanupJournal.newRunId(classId.name)
        val journalPath = journal.journalPath(classId.name).toString()
        val approved =
            CleanupRunStatus(
                runId = runId,
                classId = classId.name,
                state = CleanupRunState.APPROVED,
                plannedCount = plan.fileCount,
                deletedCount = 0,
                failedCount = 0,
                freedBytes = 0,
                journalPath = journalPath,
                approvedBy = approvedBy,
            )
        putRun(approved)

        // Длительная операция не должна идти в HTTP-потоке (Constitution IV).
        executor.submit { run(plan, classId, approved, approvedBy) }
        return getRun(runId) ?: approved
    }

    private fun run(
        plan: CleanupPlan,
        classId: ArtifactCleanupClass,
        approved: CleanupRunStatus,
        approvedBy: String,
    ) {
        putRun(approved.copy(state = CleanupRunState.RUNNING))
        var deleted = 0L
        var failed = 0L
        var freed = 0L
        val errors = mutableListOf<String>()

        plan.entries.forEach { entry ->
            val size = entry.sizeBytes
            val ok =
                try {
                    Files.deleteIfExists(File(entry.path).toPath())
                } catch (e: Exception) {
                    errors.add("${entry.path}: ${e.message}")
                    false
                }
            if (ok) {
                deleted++
                freed += size
                journal.appendDeleted(entry.path, size, classId.name, approvedBy, approved.runId)
            } else {
                failed++
                journal.appendFailed(entry.path, size, classId.name, approvedBy, approved.runId, "delete failed")
            }
            val current = getRun(approved.runId) ?: approved
            putRun(
                current.copy(
                    state = CleanupRunState.RUNNING,
                    deletedCount = deleted,
                    failedCount = failed,
                    freedBytes = freed,
                    errors = errors.toList(),
                ),
            )
        }

        val finalState =
            when {
                deleted == 0L && failed > 0L -> CleanupRunState.FAILED
                failed > 0L -> CleanupRunState.PARTIAL
                else -> CleanupRunState.DONE
            }
        putRun(
            approved.copy(
                state = finalState,
                deletedCount = deleted,
                failedCount = failed,
                freedBytes = freed,
                errors = errors.toList(),
            ),
        )
        // BR-111: терминальный прогон — отчёт больше не нужен, освобождаем память.
        removePlan(plan.planId)
        println("[cleanup] прогон ${approved.runId} (${classId.name}): $finalState, удалено $deleted, ошибок $failed")
    }

    private fun unknownClass(classIdRaw: String) =
        CleanupException(404, "unknown_class", "неизвестный класс '$classIdRaw'")

    /** Ожидание завершения прогона — для тестов и синхронных сценариев. */
    internal fun awaitRun(
        runId: String,
        timeoutMs: Long = 30_000,
    ): CleanupRunStatus {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val current = getRun(runId) ?: throw CleanupException(404, "unknown_run", "прогон '$runId' не найден")
            if (current.state in setOf(CleanupRunState.DONE, CleanupRunState.PARTIAL, CleanupRunState.FAILED)) return current
            Thread.sleep(20)
        }
        return status(runId)
    }
}
