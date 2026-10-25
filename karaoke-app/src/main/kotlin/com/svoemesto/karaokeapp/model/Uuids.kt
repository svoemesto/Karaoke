package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import java.io.Serializable
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

/**
 * Класс Uuids.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
class Uuids(
    var id: Int,
    var uuid: String,
    val database: KaraokeConnection = WORKING_DATABASE,
) : Serializable {
//    var id: Int = 0
//    var uuid: String = ""
    fun save() {
        val connection = database.getConnection()
        if (connection == null) {
            // BR-199 (аудит #336, пакет `PKG-S3-24`): SLF4J вместо println (ADR local-0005).
            log.error("Uuids.save: нет соединения с базой данных {}", database.name)
            return
        }
        // BR-199: в SQL была лишняя запятая («uuid = ?, WHERE id = ?») — запрос был
        // синтаксически невалиден и гарантированно бросал SQLException; statement при
        // этом не закрывался (утечка). Теперь запрос валиден, statement — в `use {}`,
        // ошибка логируется через SLF4J и не уходит молча в UI-поток.
        val sql = "UPDATE tbl_uuids SET uuid = ? WHERE id = ?"
        try {
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, uuid)
                ps.setInt(2, id)
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            log.error("Uuids.save: не удалось обновить uuid для id={} (sqlState={}): {}", id, e.sqlState, e.message, e)
        }
    }

    fun getSqlToInsert(): String {
        val uuids = this
        val fieldsValues: MutableList<Pair<String, Any>> = mutableListOf()

        fieldsValues.add(Pair("id", uuids.id))
        fieldsValues.add(Pair("uuid", uuids.uuid))

        return "INSERT INTO tbl_uuids (${fieldsValues.joinToString(", ") { it.first }}) OVERRIDING SYSTEM VALUE VALUES(${
            fieldsValues.joinToString(
                ", ",
            ) { if (it.second is Long) "${it.second}" else "'${it.second}'" }
        })"
    }

    companion object {
        /**
         * BR-199 (аудит #336, пакет `PKG-S3-24`): логгер по конвенции ADR local-0005.
         */
        private val log = LoggerFactory.getLogger("infra.uuids")

        /**
         * BR-197 (аудит #336, пакет `PKG-S3-24`): кеш пар `(id, uuid)` на процесс.
         *
         * Раньше [getStoredUuid] на КАЖДЫЙ ключ MLT-модели делал отдельный
         * `SELECT … WHERE id = ?` (тысячи round-trip'ов на одну песню), а для новых
         * ключей — ещё и отдельный `INSERT`. Теперь все пары читаются одним запросом
         * (`SELECT id, uuid FROM tbl_uuids` — несколько тысяч строк) и раздаются из
         * памяти; новые ключи добавляются в кеш сразу после вставки.
         */
        private val uuidCache = ConcurrentHashMap<Int, String>()

        /** Флаг «кеш загружен» — чтобы не грузить его повторно на каждый ключ. */
        @Volatile
        private var cacheLoaded = false

        /**
         * Загрузить все пары `(id, uuid)` одним запросом (BR-197).
         *
         * @return число загруженных пар.
         */
        fun loadAll(database: KaraokeConnection): Int {
            val connection = database.getConnection()
            if (connection == null) {
                log.error("Uuids.loadAll: нет соединения с базой данных {}", database.name)
                return 0
            }
            var count = 0
            return try {
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT id, uuid FROM tbl_uuids").use { rs ->
                        while (rs.next()) {
                            uuidCache[rs.getInt("id")] = rs.getString("uuid")
                            count++
                        }
                    }
                }
                cacheLoaded = true
                count
            } catch (e: SQLException) {
                log.error("Uuids.loadAll: SQL-ошибка (sqlState={}): {}", e.sqlState, e.message, e)
                count
            }
        }

        /** Значение uuid по id из кеша (`null` — ключа нет). */
        fun cachedUuid(id: Int): String? = uuidCache[id]

        /** Загружен ли кеш хотя бы раз. */
        fun isCacheLoaded(): Boolean = cacheLoaded

        /** Положить пару `(id, uuid)` в кеш (после вставки/обновления). */
        fun putCached(
            id: Int,
            uuid: String,
        ) {
            uuidCache[id] = uuid
        }

        /** Убрать ключ из кеша (после удаления строки). */
        fun removeCached(id: Int) {
            uuidCache.remove(id)
        }

        fun getDiff(
            uuidA: Uuids?,
            uuidB: Uuids?,
        ): List<RecordDiff> {
            val result: MutableList<RecordDiff> = mutableListOf()
            if (uuidA != null && uuidB != null) {
                if (uuidA.uuid != uuidB.uuid) result.add(RecordDiff("uuid", uuidA.uuid, uuidB.uuid))
            }
            return result
        }

        fun createDbInstance(
            uuid: Uuids,
            database: KaraokeConnection,
        ): Uuids? {
            val sql = uuid.getSqlToInsert()

            val connection = database.getConnection()
            if (connection == null) {
                // BR-199: SLF4J вместо println (ADR local-0005).
                log.error("Uuids.createDbInstance: нет соединения с базой данных {}", database.name)
                return null
            }
            val ps = connection.prepareStatement(sql)
            try {
                ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS) // , Statement.RETURN_GENERATED_KEYS)
            } catch (e: SQLException) {
                // BR-197: конфликт по PK возможен, если uuid уже создан другим
                // процессом — это не ошибка, но её нужно видеть в журнале.
                log.warn("Uuids.createDbInstance: не удалось вставить uuid для id={} (sqlState={}): {}", uuid.id, e.sqlState, e.message)
            } finally {
                ps.close()
            }

            return uuid
        }

        fun loadList(
            args: Map<String, String> = emptyMap(),
            database: KaraokeConnection,
        ): List<Uuids> {
            val connection = database.getConnection()
            if (connection == null) {
                // BR-199: SLF4J вместо println (ADR local-0005).
                log.error("Uuids.loadList: нет соединения с базой данных {}", database.name)
                return emptyList()
            }
            var statement: Statement? = null
            var rs: ResultSet? = null
            var sql: String
            val where: MutableList<String> = mutableListOf()

            try {
                statement = connection.createStatement()
                sql = "SELECT tbl_uuids.*" +
                    " FROM tbl_uuids"
                if (args.containsKey("id")) where += "id=${args["id"]}"
                if (args.containsKey("uuid")) where += "uuid = '${args["uuid"]}'"
                if (where.isNotEmpty()) sql += " WHERE ${where.joinToString(" AND ")}"

                rs = statement.executeQuery(sql)
                val result: MutableList<Uuids> = mutableListOf()
                while (rs.next()) {
                    val picture = Uuids(rs.getInt("id"), rs.getString("uuid"), database)
                    result.add(picture)
                }

                return result
            } catch (e: SQLException) {
                // BR-199: SLF4J + объект исключения (ADR local-0005).
                log.warn("Uuids.loadList: SQL-ошибка (sqlState={}): {}", e.sqlState, e.message, e)
            } finally {
                try {
                    rs?.close() // close result set
                    statement?.close() // close statement
                } catch (e: SQLException) {
                    log.warn("Uuids.loadList: не удалось закрыть ресурсы чтения: {}", e.message, e)
                }
            }
            return emptyList()
        }

        fun delete(
            id: Int,
            database: KaraokeConnection,
        ) {
            val connection = database.getConnection()
            if (connection == null) {
                // BR-199: SLF4J вместо println (ADR local-0005).
                log.error("Uuids.delete: нет соединения с базой данных {}", database.name)
                return
            }
            val sql = "DELETE FROM tbl_uuids WHERE id = ?"
            connection.prepareStatement(sql).use { ps ->
                ps.setInt(1, id)
                ps.executeUpdate()
            }
            // BR-197: удалённый ключ не должен оставаться в кеше.
            removeCached(id)
        }

        fun load(
            id: Int,
            database: KaraokeConnection,
        ): Uuids? = loadList(mapOf(Pair("id", id.toString())), database).firstOrNull()
    }
}
