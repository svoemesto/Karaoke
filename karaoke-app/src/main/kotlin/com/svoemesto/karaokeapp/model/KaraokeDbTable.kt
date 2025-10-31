package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.services.SNS
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

/***
 * Таблица обязательно должна иметь поля id, recordhash
 */
interface KaraokeDbTable {

    val database: KaraokeConnection
    var id: Long

    fun getTableName(): String
    fun save() {
        if (id == 0L) {
            createDbInstance(this, database = database)
            return
        }

        val savedEntity = loadById(
            clazz = this::class,
            args = listOf(),
            tableName = this.getTableName(),
            id = id,
            database = database
        )
        val diff = getDiff(this, savedEntity)
        if (diff.isEmpty()) return
        val messageRecordChange = SseNotification.recordChange(
            RecordChangeMessage(
                tableName = this.getTableName(),
                recordId = id,
                diffs = diff,
                databaseName = database.name,
                record = this.toDTO()
            )
        )

        val setStr = diff.filter { it.recordDiffRealField }.joinToString(", ") { "${it.recordDiffName} = ?" }

        if (setStr != "") {

            val sql = "UPDATE ${this.getTableName()} SET $setStr WHERE id = ?"

            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                return
            }
            val ps = connection.prepareStatement(sql)

            var index = 1
            diff.filter{ it.recordDiffRealField }.forEach {
                try {
                    when (it.recordDiffValueNew) {
                        is String -> ps.setString(index, it.recordDiffValueNew)
                        is Long -> ps.setLong(index, it.recordDiffValueNew)
                        is Int -> ps.setInt(index, it.recordDiffValueNew)
                        is Double -> ps.setDouble(index, it.recordDiffValueNew)
                        is Float -> ps.setFloat(index, it.recordDiffValueNew)
                        is Timestamp -> ps.setTimestamp(index, it.recordDiffValueNew)
                        is Boolean -> ps.setBoolean(index, it.recordDiffValueNew)
                        else -> ps.setString(index, it.recordDiffValueNew.toString())
                    }
                } catch (e: Exception) {
                    val errorMessage = "Не удалось сохранить запись в БД. Поле «${it.recordDiffName}» имеет значение «${it.recordDiffValueNew}», несовместимое с форматом данных этого поля в БД. Оригинальный текст ошибки: «${e.message}»"
                    println(errorMessage)
                }
                index++
            }
            ps.setLong(index, id)
            try {
                ps.executeUpdate()
            } catch (e: Exception) {
                val errorMessage = "Не удалось сохранить запись в БД. Оригинальный текст ошибки: «${e.message}»"
                println(errorMessage)
            }
            ps.close()

            try {
                SNS.send(messageRecordChange)
            } catch (e: Exception) {
                println(e.message)
            }
            val saved = loadById(
                clazz = this::class,
                args = listOf(),
                tableName = this.getTableName(),
                id = id,
                database = database
            )
            val diffNew = getDiff(saved, this)
            if (diffNew.isNotEmpty()) {
                val messageRecordChangeNew = SseNotification.recordChange(
                    RecordChangeMessage(
                        tableName = this.getTableName(),
                        recordId = id,
                        diffs = diffNew,
                        databaseName = database.name,
                        record = saved?.toDTO()
                    )
                )
                try {
                    SNS.send(messageRecordChangeNew)
                } catch (e: Exception) {
                    println(e.message)
                }
            }

        }

    }
    fun toDTO(): KaraokeDbTableDto

    fun getSqlToInsert(): String {
        val fieldsValues: MutableList<Pair<String, Any>> = mutableListOf()
        if (this.id > 0) fieldsValues.add(Pair("id", this.id))
        val kClass: KClass<out KaraokeDbTable> = this::class
        for (member in kClass.members) {
            if (member is kotlin.reflect.KProperty<*>) {
                val property = member
                val karaokeDbTableFieldAnnotation = property.findAnnotation<KaraokeDbTableField>()
                if (karaokeDbTableFieldAnnotation != null && !karaokeDbTableFieldAnnotation.isId) {
                    property.getter.call(this)?.let {fieldsValues.add(Pair(karaokeDbTableFieldAnnotation.name, it)) }
                }
            }
        }
        return "INSERT INTO ${getTableName()} (${fieldsValues.joinToString(", ") { it.first }}) OVERRIDING SYSTEM VALUE VALUES(${
            fieldsValues.joinToString(
                ", "
            ) { if (it.second is Long) "${it.second}" else "'${it.second.toString().replace("'", "''")}'" }
        })"
    }

    companion object {
        fun loadList(
            clazz: KClass<*>,
            tableName: String,
            whereList: List<String>,
            limit: Int = 0,
            offset: Int = 0,
            database: KaraokeConnection,
            sync: Boolean = false
        ): List<KaraokeDbTable> {

            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                return emptyList()
            }
            var statement: Statement? = null
            var rs: ResultSet? = null
            var sql: String

            try {
                statement = connection.createStatement()

                sql = "SELECT $tableName${if (sync) "_sync" else ""}.* FROM $tableName"

                if (whereList.isNotEmpty()) sql += " WHERE ${whereList.joinToString(" AND ")}"
                if (limit > 0) sql += " LIMIT $limit"
                if (offset > 0) sql += " OFFSET $offset"

                rs = statement.executeQuery(sql)
                val result: MutableList<KaraokeDbTable> = mutableListOf()
                while (rs.next()) {

                    val constructor = clazz.primaryConstructor
                    if (constructor != null) {
                        val entity = constructor.call(database as Connection) as? KaraokeDbTable
                        if (entity != null ) {
                            val kClass: KClass<out KaraokeDbTable> = entity::class

                            for (member in kClass.members) {
                                if (member is kotlin.reflect.KProperty<*>) {
                                    val property = member
                                    val karaokeDbTableFieldAnnotation = property.findAnnotation<KaraokeDbTableField>()
                                    if (karaokeDbTableFieldAnnotation != null) {
                                        if (property is KMutableProperty<*>) {
                                            val fieldName = karaokeDbTableFieldAnnotation.name
//                                            val fieldValue = property.getter.call(entity)
                                            property.setter.isAccessible = true
                                            val newValue = when (property.returnType.classifier) {
                                                String::class -> rs.getString(fieldName)
                                                Int::class -> rs.getInt(fieldName)
                                                Long::class -> rs.getLong(fieldName)
                                                Double::class -> rs.getDouble(fieldName)
                                                Boolean::class -> rs.getBoolean(fieldName)
                                                Timestamp::class -> rs.getTimestamp(fieldName)
                                                Float::class -> rs.getFloat(fieldName)
                                                else -> rs.getString(fieldName)
                                            }
                                            property.setter.call(entity, newValue)
                                        }
                                    }
                                }
                            }
                            result.add(entity)
                        }


                    }

                }

                return result

            } catch (e: SQLException) {
                e.printStackTrace()
            } finally {
                try {
                    rs?.close() // close result set
                    statement?.close() // close statement
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return emptyList()
        }
        fun loadById(clazz: KClass<*>,
                     args: List<Any?> = emptyList(),
                     tableName: String,
                     id: Long,
                     database: KaraokeConnection,
                     sync: Boolean = false
        ): KaraokeDbTable? {
            return loadList(
                clazz = clazz,
                tableName = tableName,
                whereList = listOf("$tableName${if (sync) "_sync" else ""}.id=$id"),
                database = database,
                sync = sync
            ).firstOrNull()
        }
        fun createDbInstance(entity: KaraokeDbTable, database: KaraokeConnection) : KaraokeDbTable? {
            val sql = entity.getSqlToInsert()

            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                return null
            }
            val ps = connection.prepareStatement(sql)
            try {
                ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS)
            } catch (_: Exception) {
                // Проверяем последнее значение сиквенса и айдишника таблицы
                val statement = connection.createStatement()
                val rsLastId = statement.executeQuery("select max(id) as last_value from ${entity.getTableName()};")
                val rsLastSeq = statement.executeQuery("select last_value from ${entity.getTableName()}_id_seq;")
                val lastId = if (rsLastId.isClosed) {
                    0
                } else {
                    rsLastId.next()
                    rsLastId.getLong("last_value")
                }
                val lastSeq = if (rsLastSeq.isClosed) {
                    0
                } else {
                    rsLastSeq.next()
                    rsLastSeq.getLong("last_value")
                }
                if (lastSeq < lastId) {
                    statement.execute("alter sequence ${entity.getTableName()}_id_seq restart with ${lastId+1};")
                    ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS)
                }
            }
            val rs = ps.generatedKeys

            val result = if (rs.next() && entity.id <= 0) {
                entity.id = rs.getLong(1)
                entity
            } else null

            ps.close()
            result?.let {
                val messageRecordAdd = SseNotification.recordAdd(
                    RecordAddMessage(
                        tableName = entity.getTableName(),
                        recordId = result.id,
                        databaseName = database.name,
                        record = result.toDTO()
                    )
                )
                SNS.send(messageRecordAdd)
            }

            return result
        }
        fun getListHashes(tableName: String, database: KaraokeConnection, whereText: String = ""): List<RecordHash>? {
            var result: MutableList<RecordHash>? = mutableListOf()
            val sql = "SELECT id, recordhash FROM $tableName $whereText"

            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                return null
            }
            var statement: Statement? = null
            var rs: ResultSet? = null

            try {
                statement = connection.createStatement()

                println("[${Timestamp.from(Instant.now())}] Запрос хешей для таблицы $tableName...")
                rs = statement.executeQuery(sql)
                var cnt = 0
                while (rs.next()) {
                    cnt++
                    result!!.add(RecordHash(id = rs.getLong("id"), recordhash = rs.getString("recordhash")))
                }
                println("[${Timestamp.from(Instant.now())}] Получено хешей для таблицы $tableName: $cnt")

            } catch (e: SQLException) {
                e.printStackTrace()
                result = null
            } finally {
                try {
                    rs?.close() // close result set
                    statement?.close() // close statement
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return result
        }

        fun getTotalCount(tableName: String, database: KaraokeConnection): Int {
            val sql = "SELECT COUNT(*) AS total_count FROM $tableName;"
            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                return -1
            }
            var statement: Statement? = null
            var rs: ResultSet? = null

            try {
                statement = connection.createStatement()
                rs = statement.executeQuery(sql)
                while (rs.next()) {
                    return rs.getInt("total_count")
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            } finally {
                try {
                    rs?.close() // close result set
                    statement?.close() // close statement
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return -1
        }

        fun getDiff(entityA: KaraokeDbTable?, entityB: KaraokeDbTable?): List<RecordDiff> {
            val result: MutableList<RecordDiff> = mutableListOf()
            if (entityA != null && entityB != null) {
                val kClassEntityA: KClass<out KaraokeDbTable> = entityA::class
                for (member in kClassEntityA.members) {
                    if (member is kotlin.reflect.KProperty<*>) {
                        val property = member
                        val karaokeDbTableFieldAnnotation = property.findAnnotation<KaraokeDbTableField>()
                        if (karaokeDbTableFieldAnnotation != null) {
                            if (karaokeDbTableFieldAnnotation.useInDiff) {
                                val fieldValueA = property.getter.call(entityA)
                                val fieldValueB = property.getter.call(entityB)
                                if (fieldValueA != fieldValueB) {
                                    result.add(RecordDiff(karaokeDbTableFieldAnnotation.name, fieldValueA, fieldValueB))
                                }
                            }
                        }
                    }
                }
            }
            return result
        }

        fun delete(tableName: String, id: Long, database: KaraokeConnection, sync: Boolean = false): Boolean {

            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                return false
            }
            try {
                val sql = "DELETE FROM $tableName${if (sync) "_sync" else ""} WHERE id = ?"
                val ps = connection.prepareStatement(sql)
                ps.setLong(1, id)
                ps.executeUpdate()
                ps.close()
                return true
            } catch (e: Exception) {
                println(e.message)
            }
            return false

        }
    }
}