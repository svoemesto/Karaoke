package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SNS
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.postgresql.util.PSQLException
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

/**
 * Базовый интерфейс для всех персистентных сущностей Karaoke
 * (Settings, Author, Picture, KaraokeProcess, и др.).
 *
 * Реализует:
 * 1. **Reflection-based persistence** — поля класса напрямую мапятся
 *    на колонки БД через reflection ([getDiff], [loadById]). Нет
 *    явного SQL-mapper'а.
 * 2. **Diff-based save** — [save] загружает текущее состояние из БД,
 *    вычисляет field-level [RecordDiff], генерирует `UPDATE` только
 *    для изменённых колонок.
 * 3. **SSE-уведомления** — после `save()` рассылается
 *    `SseNotification.recordChange` всем подписанным UI-вкладкам.
 * 4. **Sync-готовность** — наличие колонки `recordhash` (md5 от
 *    канонизированной строки) позволяет участвовать в
 *    двух-БД sync LOCAL↔SERVER.
 *
 * **Требования к таблице БД**:
 * - Должна иметь `id BIGINT PRIMARY KEY` (auto-generated).
 * - Должна иметь `recordhash VARCHAR(32) NOT NULL`.
 * - Триггер `tg_<table>_recordhash` поддерживает `recordhash` на
 *   INSERT/UPDATE/DELETE.
 *
 * **Ловушки (см. DEVELOPMENT.md):**
 * - `loadList` бросает NPE на `SQL NULL`, если Kotlin-поле объявлено
 *   non-null. **Nullable-колонки → nullable-поля в Kotlin**.
 * - `save()` молча проглатывает UNIQUE-конфликты и другие SQLException
 *   в `try/catch` вокруг `executeUpdate()`. Для сущностей с UNIQUE-индексом
 *   проверяйте конфликт ДО [save] в контроллере.
 * - На больших таблицах (18k+ записей) `save()` с diff может быть
 *   медленным из-за reflection — для batch-операций используйте
 *   прямой SQL.
 *
 * @property database подключение к БД (local/remote/virtual) — DI через конструктор.
 * @property storageService MinIO-клиент — DI через конструктор.
 * @property storageApiClient HTTP-клиент для remote-операций — DI через конструктор.
 * @property id первичный ключ (`0L` означает «новая запись, ещё не в БД»).
 * @see docs/features/dual-db-sync.md
 * @see docs/architecture-notes-archive.md «reflection-loader и nullable-колонки»
 */

/**
 * Интерфейс для karaoke db table.
 *
 * @see docs/features/dual-db-sync.md
 */
interface KaraokeDbTable {
    val database: KaraokeConnection
    val storageService: KaraokeStorageService
    val storageApiClient: StorageApiClient
    var id: Long

    /**
     * Имя таблицы в БД (например, `"tbl_settings"`, `"tbl_authors"`).
     * Используется reflection-загрузчиком и sync-механизмом.
     */
    fun getTableName(): String

    /**
     * Сохранить текущее состояние сущности в БД.
     *
     * Алгоритм:
     * 1. Если `id == 0L` — новая запись: [createDbInstance] вставляет
     *    строку через `INSERT ... RETURNING id`, обновляет in-memory `id`.
     * 2. Если `id != 0L` — загружается актуальное состояние через
     *    [loadById], вычисляется [getDiff] (сравнение текущего и
     *    актуального состояний), генерируется `UPDATE` только для
     *    изменённых полей.
     * 3. После `UPDATE` рассылается SSE [SseNotification.recordChange]
     *    с [RecordDiff] — UI (`webvue3`) обновляет представление.
     *
     * **Ловушка**: исключения из `executeUpdate()` логируются и НЕ
     * пробрасываются. UNIQUE-конфликты проглатываются. Для сущностей
     * с UNIQUE-индексом проверяйте конфликт ДО [save] в контроллере.
     *
     * @see getDiff
     * @see createDbInstance
     * @see docs/features/dual-db-sync.md
     */
    fun save() {
        if (id == 0L) {
            createDbInstance(this, database = database)
            return
        }

        val savedEntity =
            loadById(
                clazz = this::class,
                args = listOf(),
                tableName = this.getTableName(),
                id = id,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val diff = getDiff(this, savedEntity)
        if (diff.isEmpty()) return
        val messageRecordChange =
            SseNotification.recordChange(
                RecordChangeMessage(
                    tableName = this.getTableName(),
                    recordId = id,
                    diffs = diff,
                    databaseName = database.name,
                    record = this.toDTO(),
                ),
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
            diff.filter { it.recordDiffRealField }.forEach {
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
                    val errorMessage =
                        "Не удалось сохранить запись в БД. Поле «${it.recordDiffName}» имеет значение " +
                            "«${it.recordDiffValueNew}», несовместимое с форматом данных этого поля в БД. " +
                            "Оригинальный текст ошибки: «${e.message}»"
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
            val saved =
                loadById(
                    clazz = this::class,
                    args = listOf(),
                    tableName = this.getTableName(),
                    id = id,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                )
            val diffNew = getDiff(saved, this)
            if (diffNew.isNotEmpty()) {
                val messageRecordChangeNew =
                    SseNotification.recordChange(
                        RecordChangeMessage(
                            tableName = this.getTableName(),
                            recordId = id,
                            diffs = diffNew,
                            databaseName = database.name,
                            record = saved?.toDTO(),
                        ),
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
                    property.getter.call(this)?.let { fieldsValues.add(Pair(karaokeDbTableFieldAnnotation.name, it)) }
                }
            }
        }
        return "INSERT INTO ${getTableName()} (${fieldsValues.joinToString(", ") { it.first }}) OVERRIDING SYSTEM VALUE VALUES(${
            fieldsValues.joinToString(
                ", ",
            ) { if (it.second is Long) "${it.second}" else "'${it.second.toString().replace("'", "''")}'" }
        })"
    }

    companion object {
        private fun columns(
            tableName: String,
            database: KaraokeConnection,
        ): List<String> {
            val sql = "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = '$tableName'"
            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                return emptyList()
            }
            var statement: Statement? = null
            var rs: ResultSet? = null

            try {
                statement = connection.createStatement()

                rs = statement.executeQuery(sql)
                val result: MutableList<String> = mutableListOf()
                while (rs.next()) {
                    result.add(rs.getString("column_name"))
                }
                return result
            } catch (e: SQLException) {
                e.printStackTrace()
            } finally {
                try {
                    rs?.close()
                    statement?.close()
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return emptyList()
        }

        fun loadList(
            clazz: KClass<*>,
            tableName: String,
            whereList: List<String>,
            limit: Int = 0,
            offset: Int = 0,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
            sync: Boolean = false,
            ignoreUseInList: Boolean = false,
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

                val notInSelectList: MutableList<String> = mutableListOf()
                if (!ignoreUseInList) {
                    (clazz.primaryConstructor?.call(database as Connection, storageService, storageApiClient) as? KaraokeDbTable)?.let { tmpEntry ->
                        for (property in tmpEntry::class.members) {
                            (property.findAnnotation<KaraokeDbTableField>())?.let { karaokeDbTableFieldAnnotation ->
                                if (property is KMutableProperty<*>) {
                                    val fieldName = karaokeDbTableFieldAnnotation.name
                                    if (!karaokeDbTableFieldAnnotation.useInList) notInSelectList.add(fieldName)
                                }
                            }
                        }
                    }
                }

                val sqlSelect =
                    if (notInSelectList.isNotEmpty()) {
                        "SELECT ${
                            columns(tableName, database)
                                .filter { it !in notInSelectList }
                                .joinToString(", ") { "$tableName${if (sync) "_sync" else ""}.$it" }
                        }"
                    } else {
                        "SELECT $tableName${if (sync) "_sync" else ""}.*"
                    }
                val sqlFrom = "FROM $tableName${if (sync) "_sync" else ""}"
                sql = "$sqlSelect $sqlFrom"

                if (whereList.isNotEmpty()) sql += " WHERE ${whereList.joinToString(" AND ")}"
                if (limit > 0) sql += " LIMIT $limit"
                if (offset > 0) sql += " OFFSET $offset"

                rs = statement.executeQuery(sql)
                val result: MutableList<KaraokeDbTable> = mutableListOf()
                while (rs.next()) {
                    val constructor = clazz.primaryConstructor
                    if (constructor != null) {
                        val entity = constructor.call(database as Connection, storageService, storageApiClient) as? KaraokeDbTable
                        if (entity != null) {
                            val kClass: KClass<out KaraokeDbTable> = entity::class

                            for (member in kClass.members) {
                                if (member is kotlin.reflect.KProperty<*>) {
                                    val property = member
                                    val karaokeDbTableFieldAnnotation = property.findAnnotation<KaraokeDbTableField>()
                                    if (karaokeDbTableFieldAnnotation != null) {
                                        if (property is KMutableProperty<*>) {
                                            val fieldName = karaokeDbTableFieldAnnotation.name

                                            if (!karaokeDbTableFieldAnnotation.useInList) notInSelectList.add(fieldName)
//                                            val fieldValue = property.getter.call(entity)
                                            property.setter.isAccessible = true
                                            try {
                                                // getInt()/getLong() возвращают 0 на SQL NULL (примитивы не могут
                                                // представить null) — для nullable Kotlin-полей (Int?/Long?) это
                                                // молча подменяет реальный null на 0, из-за чего save() потом видит
                                                // ложный diff (0 vs null) и падает на попытке записать "null" в
                                                // числовую колонку (см. баг с Subscription.idSong=null у SITE-
                                                // подписок — вся строка тихо переставала обновляться). Для полей,
                                                // не допускающих null в Kotlin, поведение не меняется.
                                                val newValue =
                                                    when (property.returnType.classifier) {
                                                        String::class -> rs.getString(fieldName)
                                                        Int::class ->
                                                            rs.getInt(fieldName).let {
                                                                if (rs.wasNull() &&
                                                                    property.returnType.isMarkedNullable
                                                                ) {
                                                                    null
                                                                } else {
                                                                    it
                                                                }
                                                            }
                                                        Long::class ->
                                                            rs.getLong(fieldName).let {
                                                                if (rs.wasNull() &&
                                                                    property.returnType.isMarkedNullable
                                                                ) {
                                                                    null
                                                                } else {
                                                                    it
                                                                }
                                                            }
                                                        Double::class -> rs.getDouble(fieldName)
                                                        Boolean::class -> rs.getBoolean(fieldName)
                                                        Timestamp::class -> rs.getTimestamp(fieldName)
                                                        Float::class -> rs.getFloat(fieldName)
                                                        else -> rs.getString(fieldName)
                                                    }
                                                property.setter.call(entity, newValue)
                                            } catch (_: PSQLException) {
                                            }
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

        fun loadById(
            clazz: KClass<*>,
            args: List<Any?> = emptyList(),
            tableName: String,
            id: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
            sync: Boolean = false,
        ): KaraokeDbTable? =
            loadList(
                clazz = clazz,
                tableName = tableName,
                whereList = listOf("$tableName${if (sync) "_sync" else ""}.id=$id"),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
                sync = sync,
                ignoreUseInList = true,
            ).firstOrNull()

        fun loadByIds(
            clazz: KClass<*>,
            tableName: String,
            ids: List<Long>,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
            sync: Boolean = false,
        ): List<KaraokeDbTable> {
            if (ids.isEmpty()) return emptyList()
            return loadList(
                clazz = clazz,
                tableName = tableName,
                whereList = listOf("$tableName${if (sync) "_sync" else ""}.id IN (${ids.joinToString(",")})"),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
                sync = sync,
                ignoreUseInList = true,
            )
        }

        fun createDbInstance(
            entity: KaraokeDbTable,
            database: KaraokeConnection,
        ): KaraokeDbTable? {
            val sql = entity.getSqlToInsert()

            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                return null
            }
            val ps = connection.prepareStatement(sql)
            try {
                ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS)
            } catch (e: Exception) {
                println("[${Timestamp.from(Instant.now())}] createDbInstance: сбой вставки в ${entity.getTableName()}: ${e.message}")
                // Проверяем последнее значение сиквенса и айдишника таблицы. Важно: каждый executeQuery —
                // на СВОЁМ Statement, иначе второй executeQuery на том же Statement закрывает ResultSet
                // первого (по контракту JDBC) и lastId всегда читался бы как 0 — самолечение дрейфа
                // сиквенса (после SERVER_TO_LOCAL sync с OVERRIDING SYSTEM VALUE) никогда не срабатывало.
                val lastId =
                    connection.createStatement().use { st ->
                        st.executeQuery("select max(id) as last_value from ${entity.getTableName()};").use { rs ->
                            if (rs.next()) rs.getLong("last_value") else 0
                        }
                    }
                val lastSeq =
                    connection.createStatement().use { st ->
                        st.executeQuery("select last_value from ${entity.getTableName()}_id_seq;").use { rs ->
                            if (rs.next()) rs.getLong("last_value") else 0
                        }
                    }
                if (lastSeq < lastId) {
                    connection.createStatement().use {
                        it.execute("alter sequence ${entity.getTableName()}_id_seq restart with ${lastId + 1};")
                    }
                    ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS)
                }
            }
            val rs = ps.generatedKeys

            val result =
                if (rs.next() && entity.id <= 0) {
                    entity.id = rs.getLong(1)
                    entity
                } else {
                    null
                }

            ps.close()
            result?.let {
                val messageRecordAdd =
                    SseNotification.recordAdd(
                        RecordAddMessage(
                            tableName = entity.getTableName(),
                            recordId = result.id,
                            databaseName = database.name,
                            record = result.toDTO(),
                        ),
                    )
                SNS.send(messageRecordAdd)
            }

            return result
        }

        fun getListHashes(
            tableName: String,
            database: KaraokeConnection,
            whereText: String = "",
        ): List<RecordHash>? {
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

        @Suppress("unused")
        fun getTotalCount(
            tableName: String,
            database: KaraokeConnection,
        ): Int {
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

        fun getDiff(
            entityA: KaraokeDbTable?,
            entityB: KaraokeDbTable?,
        ): List<RecordDiff> {
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

        fun delete(
            tableName: String,
            id: Long,
            database: KaraokeConnection,
            sync: Boolean = false,
        ): Boolean {
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
