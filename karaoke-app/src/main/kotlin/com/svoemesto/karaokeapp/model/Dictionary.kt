package com.svoemesto.karaokeapp.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.KaraokeDbTable.Companion.getListHashes
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import java.io.File
import java.io.Serializable

/**
 * Единая таблица словарей, заменяющая файловые TextFileDictionary (текстовые файлы в /sm-karaoke/system).
 * Одна запись = одно значение одного словаря (dict_name, dict_value). Уникальность пары
 * гарантирует индекс uq_tbl_dictionaries_name_value (deploy/karaoke-db/17_dictionaries.sql).
 * Синхронизируется LOCAL->SERVER через SyncTarget "dictionaries" (см. sync/SyncTarget.kt).
 */
@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class Dictionary(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable, Comparable<Dictionary>, KaraokeDbTable {

    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "dict_name")
    var dictName: String = ""

    @KaraokeDbTableField(name = "dict_value")
    var dictValue: String = ""

    override fun compareTo(other: Dictionary): Int {
        val byName = dictName.compareTo(other.dictName)
        return if (byName != 0) byName else dictValue.compareTo(other.dictValue)
    }

    override fun toDTO(): DictionaryDto {
        return DictionaryDto(
            id = id,
            dictName = dictName,
            dictValue = dictValue
        )
    }

    companion object {

        const val TABLE_NAME = "tbl_dictionaries"

        fun listHashes(database: KaraokeConnection, whereText: String = ""): List<RecordHash>? =
            getListHashes(tableName = TABLE_NAME, database = database, whereText = whereText)

        private fun getWhereList(whereArgs: Map<String, String>): List<String> {
            val where: MutableList<String> = mutableListOf()
            if (whereArgs.containsKey("id")) where += "id=${whereArgs["id"]}"
            if (whereArgs.containsKey("dict_name")) where += "dict_name = '${whereArgs["dict_name"]?.replace("'", "''")}'"
            if (whereArgs.containsKey("dict_value")) where += "dict_value = '${whereArgs["dict_value"]?.replace("'", "''")}'"
            return where
        }

        fun loadList(
            whereArgs: Map<String, String>,
            limit: Int = 0,
            offset: Int = 0,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
            ignoreUseInList: Boolean = true
        ): List<Dictionary> {
            return KaraokeDbTable.loadList(
                clazz = Dictionary::class,
                tableName = TABLE_NAME,
                whereList = getWhereList(whereArgs),
                limit = limit,
                offset = offset,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
                ignoreUseInList = ignoreUseInList
            ).map { it as Dictionary }
        }

        fun delete(id: Long, database: KaraokeConnection): Boolean {
            return KaraokeDbTable.delete(
                tableName = TABLE_NAME,
                id = id,
                database = database
            )
        }

        fun createNewDictionaryItem(newItem: Dictionary, database: KaraokeConnection): Dictionary? {
            val existing = getItem(dictName = newItem.dictName, dictValue = newItem.dictValue, database = database)
            if (existing != null) return existing
            // Уникальный индекс uq_tbl_dictionaries_name_value гарантирует отсутствие дублей на
            // уровне БД; try/catch — защита от гонки (как Pictures.createNewPicture).
            val newItemInDb = try {
                KaraokeDbTable.createDbInstance(entity = newItem, database = database) as? Dictionary?
            } catch (e: Exception) {
                return getItem(dictName = newItem.dictName, dictValue = newItem.dictValue, database = database)
            }
            newItemInDb?.let { return it }
            return null
        }

        fun getItem(dictName: String, dictValue: String, database: KaraokeConnection): Dictionary? {
            return loadList(
                whereArgs = mapOf("dict_name" to dictName, "dict_value" to dictValue),
                database = database,
                storageService = KSS_APP,
                storageApiClient = SAC_APP,
                ignoreUseInList = true
            ).firstOrNull()
        }

        // ==========================================================================================
        // API уровня словаря — используется фасадом TextFileDictionary (совместимость со старым кодом)
        // ==========================================================================================

        fun loadValues(dictName: String, database: KaraokeConnection = WORKING_DATABASE): List<String> {
            return loadList(
                whereArgs = mapOf("dict_name" to dictName),
                database = database,
                storageService = KSS_APP,
                storageApiClient = SAC_APP,
                ignoreUseInList = true
            ).map { it.dictValue }.sorted()
        }

        fun addValues(dictName: String, values: List<String>, database: KaraokeConnection = WORKING_DATABASE) {
            for (value in values) {
                if (value == "") continue
                createNewDictionaryItem(
                    newItem = Dictionary(database = database).apply {
                        this.dictName = dictName
                        this.dictValue = value
                    },
                    database = database
                )
            }
        }

        fun removeValues(dictName: String, values: List<String>, database: KaraokeConnection = WORKING_DATABASE) {
            for (value in values) {
                getItem(dictName = dictName, dictValue = value, database = database)?.let {
                    delete(id = it.id, database = database)
                }
            }
        }

        fun clear(dictName: String, database: KaraokeConnection = WORKING_DATABASE) {
            loadList(
                whereArgs = mapOf("dict_name" to dictName),
                database = database,
                storageService = KSS_APP,
                storageApiClient = SAC_APP,
                ignoreUseInList = true
            ).forEach { delete(id = it.id, database = database) }
        }

        fun have(dictName: String, value: String, database: KaraokeConnection = WORKING_DATABASE): Boolean {
            return getItem(dictName = dictName, dictValue = value, database = database) != null
        }

        /**
         * Разовый импорт значений словаря из старого текстового файла (текстовый файл в /sm-karaoke/system) в БД.
         * Идемпотентно — дубли отсекаются уникальным индексом. Возвращает число реально добавленных строк.
         */
        fun importFromFile(dictName: String, filePath: String, database: KaraokeConnection = WORKING_DATABASE): Int {
            val values = try {
                File(filePath).readText().split("\n").filter { it != "" }
            } catch (e: Exception) {
                return 0
            }
            val before = loadValues(dictName, database).toSet()
            addValues(dictName, values, database)
            val after = loadValues(dictName, database).toSet()
            return (after - before).size
        }
    }
}
