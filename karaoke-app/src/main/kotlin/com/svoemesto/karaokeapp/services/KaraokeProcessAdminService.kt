package com.svoemesto.karaokeapp.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.KaraokeProcessStatuses
import com.svoemesto.karaokeapp.KaraokeProcessThread
import com.svoemesto.karaokeapp.KaraokeProcessWorker
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.dto.admin.KaraokeProcessAdminDTO
import com.svoemesto.karaokeapp.dto.admin.ProcessAuditDTO
import com.svoemesto.karaokeapp.dto.admin.ProcessListResult
import org.springframework.context.annotation.DependsOn
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/**
 * Админ-сервис процессов (specs/315-admin-ui-karaoke-process-v5).
 *
 * Реализует US1..US5: просмотр с фильтрацией + parent-child, edit, delete, retry, audit.
 * Работает напрямую с `tbl_processes` через raw JDBC (KaraokeConnection), НЕ через
 * `KaraokeProcess.save()` — чтобы не писать `process_deleted_at`/`process_chain_id`
 * (FR-019 race protection, Lesson #5 iter #1).
 *
 * @DependsOn("karaokeAppService") — Lesson #4 iter #1: сервис читает WORKING_DATABASE,
 * который инициализируется в KaraokeAppService. Без этого возможен NPE при старте.
 *
 * @see specs/315-admin-ui-karaoke-process-v5/spec.md
 */
@Service
@DependsOn("karaokeAppService")
class KaraokeProcessAdminService {
    private val mapper = ObjectMapper()

    /** Фильтры списка процессов (GET /api/admin/processes). */
    data class ProcessFilters(
        val status: List<String> = emptyList(),
        val type: List<String> = emptyList(),
        val threadId: Int? = null,
        val chainId: Long? = null,
        val includeDeleted: Boolean = false,
        val name: String? = null,
        val topLevelOnly: Boolean = true,
        val parentId: Long? = null,
        val limit: Int = 100,
        val offset: Int = 0,
    )

    /** Процесс не найден → 404. */
    class ProcessNotFoundException(
        id: Int,
    ) : RuntimeException(
            "Process not found: id=$id",
        )

    /** Невалидный переход статуса → 400 (Error Format contracts/admin-process-rest-api.md). */
    class InvalidStatusTransitionException(
        val currentStatus: String,
        val requestedStatus: String,
    ) : RuntimeException(
            "Cannot transition from $currentStatus to $requestedStatus",
        )

    /**
     * Редактируемые колонки (FR-008). НЕ включает `process_deleted_at`/`process_chain_id`
     * (FR-019 race protection). Ключ — имя поля DTO/JSON, значение — реальная колонка.
     */
    private val editableColumns: Map<String, String> =
        mapOf(
            "name" to "process_name",
            "status" to "process_status",
            "order" to "process_order",
            "priority" to "process_priority",
            "command" to "process_command",
            "args" to "process_args",
            "envs" to "process_envs",
            "description" to "process_description",
            "songId" to "song_id",
            "type" to "process_type",
            "startedAt" to "process_start",
            "endedAt" to "process_end",
            "prioritet" to "process_prioritet",
            "withoutControl" to "without_control",
            "threadId" to "thread_id",
        )

    /** Базовый SELECT с camelCase-алиасами (реальные колонки, НЕ created_at/updated_at). */
    private val baseSelect =
        """
        SELECT id,
               process_name AS name,
               process_status AS status,
               process_type AS type,
               process_command AS command,
               process_args AS args,
               process_envs AS envs,
               process_description AS description,
               song_id AS songId,
               process_order AS "order",
               process_priority AS priority,
               process_prioritet AS prioritet,
               without_control AS withoutControl,
               thread_id AS threadId,
               process_chain_id AS processChainId,
               process_deleted_at AS processDeletedAt,
               last_update AS updatedAt,
               process_start AS startedAt,
               process_end AS endedAt,
               recordhash
        FROM tbl_processes
        """.trimIndent()

    /**
     * Список процессов с фильтрацией (FR-001/002/004/005/006).
     *
     * @param database рабочая БД
     * @param filters фильтры (status/type/threadId/chainId/includeDeleted/name/topLevelOnly/parentId/limit/offset)
     */
    fun loadProcesses(
        database: KaraokeConnection,
        filters: ProcessFilters,
    ): ProcessListResult {
        val connection =
            database.getConnection()
                ?: throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot connect to database ${database.name}",
                )
        val where = mutableListOf<String>()
        val params = mutableListOf<Any>()

        if (filters.status.isNotEmpty()) {
            where += "process_status IN (${filters.status.joinToString(",") { "?" }})"
            params.addAll(filters.status)
        }
        if (filters.type.isNotEmpty()) {
            where += "process_type IN (${filters.type.joinToString(",") { "?" }})"
            params.addAll(filters.type)
        }
        if (filters.threadId != null) {
            where += "thread_id = ?"
            params += filters.threadId
        }
        if (filters.chainId != null) {
            where += "process_chain_id = ?"
            params += filters.chainId
        }
        if (!filters.includeDeleted) {
            where += "process_deleted_at IS NULL"
        }
        if (!filters.name.isNullOrBlank()) {
            where += "process_name ILIKE '%' || ? || '%'"
            params += filters.name
        }
        if (filters.parentId != null) {
            // lazy load детей конкретного parent (FR-004)
            where += "process_chain_id = ?"
            params += filters.parentId
        } else if (filters.chainId == null && filters.topLevelOnly) {
            // top-level: только head-процессы (process_chain_id IS NULL)
            where += "process_chain_id IS NULL"
        }

        val whereSql = if (where.isEmpty()) "" else " WHERE ${where.joinToString(" AND ")}"
        val limit = filters.limit.coerceIn(1, 1000)
        val offset = filters.offset.coerceAtLeast(0)

        val total = countProcesses(connection, whereSql, params)
        val items = mutableListOf<KaraokeProcessAdminDTO>()

        val sql =
            baseSelect +
                whereSql +
                " ORDER BY process_priority, process_order, id" +
                " LIMIT $limit OFFSET $offset"
        connection.prepareStatement(sql).use { ps ->
            setParams(ps, params)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    items += toAdminDto(rs)
                }
            }
        }
        return ProcessListResult(total = total, items = items)
    }

    private fun countProcesses(
        connection: Connection,
        whereSql: String,
        params: List<Any>,
    ): Int {
        val sql = "SELECT COUNT(*) FROM tbl_processes$whereSql"
        connection.prepareStatement(sql).use { ps ->
            setParams(ps, params)
            ps.executeQuery().use { rs ->
                if (rs.next()) return rs.getInt(1)
            }
        }
        return 0
    }

    /**
     * Один процесс (GET /api/admin/processes/{id}).
     *
     * @return DTO или null если не найден
     */
    fun loadProcess(
        id: Int,
        database: KaraokeConnection,
    ): KaraokeProcessAdminDTO? {
        val connection =
            database.getConnection()
                ?: throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot connect to database ${database.name}",
                )
        val sql = baseSelect + " WHERE id = ?"
        connection.prepareStatement(sql).use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs ->
                if (rs.next()) return toAdminDto(rs)
            }
        }
        return null
    }

    /**
     * Редактирование процесса (POST /api/admin/processes/{id}/edit).
     *
     * Обновляет только переданные поля (FR-008), валидирует переход статуса (FR-017),
     * НЕ пишет `process_deleted_at`/`process_chain_id` (FR-019), пишет audit EDIT (FR-016).
     *
     * @param changes Map<String, Any?> — только редактируемые поля
     * @param actor имя администратора (для audit)
     */
    fun editProcess(
        id: Int,
        changes: Map<String, Any?>,
        actor: String,
        database: KaraokeConnection,
    ): KaraokeProcessAdminDTO {
        val current = loadProcess(id, database) ?: throw ProcessNotFoundException(id)
        if (current.processDeletedAt != null) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Process is already deleted: id=$id",
            )
        }

        // Валидация перехода статуса (FR-017)
        val requestedStatus = changes["status"] as? String
        if (requestedStatus != null && requestedStatus != current.status) {
            validateTransition(current.status, requestedStatus)
        }

        // Строим UPDATE только для переданных редактируемых полей
        val sets = mutableListOf<String>()
        val values = mutableListOf<Any?>()
        for ((field, column) in editableColumns) {
            if (changes.containsKey(field)) {
                sets += "$column = ?"
                values += coerceValue(field, changes[field])
            }
        }
        if (sets.isEmpty()) {
            // ничего не меняется — просто возвращаем текущий
            return current
        }

        val connection =
            database.getConnection()
                ?: throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot connect to database ${database.name}",
                )
        val sql = "UPDATE tbl_processes SET ${sets.joinToString(", ")} WHERE id = ?"
        connection.prepareStatement(sql).use { ps ->
            var index = 1
            for (value in values) {
                setParam(ps, index, value)
                index++
            }
            ps.setInt(index, id)
            ps.executeUpdate()
        }

        // Audit EDIT (FR-016, T025): old/new diff раздельно
        val oldMap = currentValue(current)
        val newMap = buildNewMap(current, changes)
        val oldDiff = oldMap.filter { (k, v) -> newMap[k] != v }
        val newDiff = newMap.filter { (k, v) -> oldMap[k] != v }
        insertAudit(connection, id, actor, "EDIT", oldDiff, newDiff)

        return loadProcess(id, database) ?: current
    }

    /**
     * Удаление процесса (POST /api/admin/processes/{id}/delete).
     *
     * Логика по статусу (FR-010..FR-013): DONE/ERROR — только soft-delete;
     * WAITING/CREATING — cancel из threadsMap + soft-delete; WORKING — interrupt +
     * 5 сек grace (R-008) + destroyForcibly + soft-delete. БЕЗ cascade (FR-013).
     */
    fun deleteProcess(
        id: Int,
        actor: String,
        database: KaraokeConnection,
    ) {
        val current = loadProcess(id, database) ?: throw ProcessNotFoundException(id)
        if (current.processDeletedAt != null) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Process is already deleted: id=$id",
            )
        }

        val connection =
            database.getConnection()
                ?: throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot connect to database ${database.name}",
                )

        var timeout = false
        when (current.status) {
            KaraokeProcessStatuses.WORKING.name -> {
                // interrupt + 5 сек grace + destroyForcibly (FR-012, R-008)
                val thread = findThread(id)
                thread?.interrupt()
                val deadline = System.currentTimeMillis() + GRACE_PERIOD_MS
                while (System.currentTimeMillis() < deadline && thread != null && thread.isAlive) {
                    Thread.sleep(100)
                }
                if (thread != null && thread.isAlive) {
                    timeout = true
                    runCatching { thread.osProcess?.destroyForcibly() }
                }
            }
            KaraokeProcessStatuses.WAITING.name,
            KaraokeProcessStatuses.CREATING.name,
            -> {
                // cancel из механизма исполнения (R-019: pickup фильтрует process_deleted_at IS NULL)
                findThread(id)?.interrupt()
            }
            else -> {
                // DONE/ERROR — только soft-delete
            }
        }

        // targeted soft-delete (FR-010), НЕ через save()
        val sql = "UPDATE tbl_processes SET process_deleted_at = NOW() WHERE id = ? AND process_deleted_at IS NULL"
        connection.prepareStatement(sql).use { ps ->
            ps.setInt(1, id)
            ps.executeUpdate()
        }

        // Audit DELETE (FR-016, T030): oldValue.status + флаги, newValue.process_deleted_at
        val oldValue =
            mutableMapOf<String, Any?>(
                "status" to current.status,
                "processDeletedAt" to current.processDeletedAt,
            )
        if (timeout) oldValue["timeout"] = true
        val newValue =
            mutableMapOf<String, Any?>(
                "process_deleted_at" to Timestamp.from(Instant.now()),
            )
        insertAudit(connection, id, actor, "DELETE", oldValue, newValue)
    }

    /**
     * Retry ERROR-процесса (POST /api/admin/processes/{id}/retry).
     *
     * Только ERROR + process_deleted_at IS NULL (FR-014, T048 iter #3). Отдельный
     * targeted UPDATE (НЕ helper setWorkingToWaiting — это WORKING→WAITING-сброс,
     * урок Т-3 Кирилла iter #4).
     */
    fun retryProcess(
        id: Int,
        actor: String,
        database: KaraokeConnection,
    ): KaraokeProcessAdminDTO {
        val current = loadProcess(id, database) ?: throw ProcessNotFoundException(id)
        if (current.status != KaraokeProcessStatuses.ERROR.name) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot retry process in status ${current.status}",
            )
        }
        if (current.processDeletedAt != null) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Process is deleted, cannot retry: id=$id",
            )
        }

        val connection =
            database.getConnection()
                ?: throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot connect to database ${database.name}",
                )
        val sql =
            "UPDATE tbl_processes SET process_status = ? WHERE id = ? AND process_status = ? AND process_deleted_at IS NULL"
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, KaraokeProcessStatuses.WAITING.name)
            ps.setInt(2, id)
            ps.setString(3, KaraokeProcessStatuses.ERROR.name)
            ps.executeUpdate()
        }

        // Audit RETRY (FR-015)
        insertAudit(
            connection,
            id,
            actor,
            "RETRY",
            mapOf("status" to KaraokeProcessStatuses.ERROR.name),
            mapOf("status" to KaraokeProcessStatuses.WAITING.name),
        )

        return loadProcess(id, database) ?: current
    }

    /**
     * Audit-лог процесса (GET /api/admin/processes/{id}/audit).
     *
     * Фильтр: `created_at > NOW() - make_interval(days => ?)` (FR-016/018).
     * ⚠️ НЕ использовать INTERVAL с плейсхолдером в строковом литерале — урок RC-2 iter #3.
     */
    fun loadAudit(
        processId: Int,
        days: Int,
        database: KaraokeConnection,
    ): List<ProcessAuditDTO> {
        val connection =
            database.getConnection()
                ?: throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot connect to database ${database.name}",
                )
        val sql =
            """
            SELECT id, process_id, actor, action, old_value, new_value, created_at
            FROM tbl_processes_audit
            WHERE process_id = ? AND created_at > NOW() - make_interval(days => ?)
            ORDER BY created_at DESC
            """.trimIndent()
        val result = mutableListOf<ProcessAuditDTO>()
        connection.prepareStatement(sql).use { ps ->
            ps.setInt(1, processId)
            ps.setInt(2, days.coerceIn(1, 30))
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    result +=
                        ProcessAuditDTO(
                            id = rs.getInt("id"),
                            processId = rs.getInt("process_id"),
                            actor = rs.getString("actor"),
                            action = rs.getString("action"),
                            oldValue = parseJsonb(rs.getString("old_value")),
                            newValue = parseJsonb(rs.getString("new_value")),
                            createdAt = rs.getTimestamp("created_at"),
                        )
                }
            }
        }
        return result
    }

    /**
     * Retention cron: удаляет audit-записи старше 30 дней (FR-018).
     * Хардкод `INTERVAL '30 days'` — политика фиксирована (T047).
     */
    @Scheduled(fixedDelay = 24 * 60 * 60 * 1000, initialDelay = 60 * 1000)
    fun cleanupOldAudit() {
        val connection = WORKING_DATABASE.getConnection() ?: return
        val sql = "DELETE FROM tbl_processes_audit WHERE created_at < NOW() - INTERVAL '30 days'"
        connection.prepareStatement(sql).use { ps ->
            ps.executeUpdate()
        }
    }

    // --- helpers ---

    private fun findThread(id: Int): KaraokeProcessThread? =
        KaraokeProcessWorker.threadsMap.values.firstOrNull { it?.karaokeProcess?.id == id.toLong() }

    private fun validateTransition(
        current: String,
        requested: String,
    ) {
        val allowed: Set<String> =
            when (current) {
                KaraokeProcessStatuses.CREATING.name ->
                    setOf(
                        KaraokeProcessStatuses.WAITING.name,
                        KaraokeProcessStatuses.WORKING.name,
                        KaraokeProcessStatuses.ERROR.name,
                    )
                KaraokeProcessStatuses.WAITING.name ->
                    setOf(KaraokeProcessStatuses.WORKING.name)
                KaraokeProcessStatuses.WORKING.name ->
                    setOf(
                        KaraokeProcessStatuses.DONE.name,
                        KaraokeProcessStatuses.ERROR.name,
                    )
                else -> emptySet()
            }
        if (requested !in allowed) {
            throw InvalidStatusTransitionException(current, requested)
        }
    }

    private fun coerceValue(
        field: String,
        value: Any?,
    ): Any? =
        when (field) {
            "order", "priority", "prioritet", "songId", "threadId" ->
                (value as? Number)?.toInt() ?: value
            "withoutControl" -> value as? Boolean ?: value
            "startedAt", "endedAt" -> parseTimestamp(value)
            else -> value
        }

    private fun parseTimestamp(value: Any?): Timestamp? {
        if (value == null) return null
        if (value is Timestamp) return value
        if (value is Number) return Timestamp(value.toLong())
        val s = value.toString()
        if (s.isBlank()) return null
        return try {
            Timestamp.valueOf(s.replace('T', ' '))
        } catch (_: Exception) {
            try {
                Timestamp.from(Instant.parse(s))
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun setParams(
        ps: PreparedStatement,
        params: List<Any>,
    ) {
        var index = 1
        for (value in params) {
            setParam(ps, index, value)
            index++
        }
    }

    private fun setParam(
        ps: PreparedStatement,
        index: Int,
        value: Any?,
    ) {
        when (value) {
            null -> ps.setNull(index, java.sql.Types.NULL)
            is Int -> ps.setInt(index, value)
            is Long -> ps.setLong(index, value)
            is Boolean -> ps.setBoolean(index, value)
            is Timestamp -> ps.setTimestamp(index, value)
            else -> ps.setString(index, value.toString())
        }
    }

    private fun toAdminDto(rs: ResultSet): KaraokeProcessAdminDTO =
        KaraokeProcessAdminDTO(
            id = rs.getInt("id"),
            name = rs.getString("name"),
            status = rs.getString("status"),
            type = rs.getString("type"),
            command = rs.getString("command"),
            args = rs.getString("args"),
            envs = rs.getString("envs"),
            description = rs.getString("description"),
            songId = rs.getInt("songId").takeIf { !rs.wasNull() },
            order = rs.getInt("order"),
            priority = rs.getInt("priority"),
            prioritet = rs.getInt("prioritet"),
            withoutControl = rs.getBoolean("withoutControl"),
            threadId = rs.getInt("threadId"),
            processChainId = rs.getLong("processChainId").takeIf { !rs.wasNull() },
            processDeletedAt = rs.getTimestamp("processDeletedAt"),
            updatedAt = rs.getTimestamp("updatedAt"),
            startedAt = rs.getTimestamp("startedAt"),
            endedAt = rs.getTimestamp("endedAt"),
            recordhash = rs.getString("recordhash"),
        )

    /** Текущие значения редактируемых полей (для audit diff). */
    private fun currentValue(dto: KaraokeProcessAdminDTO): Map<String, Any?> =
        mapOf(
            "name" to dto.name,
            "status" to dto.status,
            "order" to dto.order,
            "priority" to dto.priority,
            "command" to dto.command,
            "args" to dto.args,
            "envs" to dto.envs,
            "description" to dto.description,
            "songId" to dto.songId,
            "type" to dto.type,
            "startedAt" to dto.startedAt,
            "endedAt" to dto.endedAt,
            "prioritet" to dto.prioritet,
            "withoutControl" to dto.withoutControl,
            "threadId" to dto.threadId,
        )

    /** Новые значения после применения изменений (для audit diff). */
    private fun buildNewMap(
        current: KaraokeProcessAdminDTO,
        changes: Map<String, Any?>,
    ): Map<String, Any?> {
        val result = currentValue(current).toMutableMap()
        for ((field, column) in editableColumns) {
            if (changes.containsKey(field)) {
                result[field] = coerceValue(field, changes[field])
            }
        }
        return result
    }

    private fun insertAudit(
        connection: Connection,
        processId: Int,
        actor: String,
        action: String,
        oldValue: Map<String, Any?>,
        newValue: Map<String, Any?>,
    ) {
        val sql =
            "INSERT INTO tbl_processes_audit (process_id, actor, action, old_value, new_value) VALUES (?, ?, ?, ?::jsonb, ?::jsonb)"
        connection.prepareStatement(sql).use { ps ->
            ps.setInt(1, processId)
            ps.setString(2, actor)
            ps.setString(3, action)
            ps.setString(4, mapper.writeValueAsString(oldValue))
            ps.setString(5, mapper.writeValueAsString(newValue))
            ps.executeUpdate()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseJsonb(json: String?): Map<String, Any?>? {
        if (json.isNullOrBlank()) return null
        return try {
            mapper.readValue(json, Map::class.java) as Map<String, Any?>
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val GRACE_PERIOD_MS = 5000L
    }
}
