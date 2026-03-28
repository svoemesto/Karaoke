package com.svoemesto.karaokeapp.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.KaraokeDbTable.Companion.getListHashes
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeapp.toTimestamp
import java.io.Serializable
import java.sql.Timestamp
import java.time.LocalDateTime

@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class SearchAsync(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable, Comparable<SearchAsync>, KaraokeDbTable {

    override fun compareTo(other: SearchAsync): Int {
        return id.compareTo(other.id)
    }

    override fun getTableName() = "tbl_search_async"

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "song_id")
    var songId: Long = 0

    @KaraokeDbTableField(name = "url")
    var url: String = ""

    @KaraokeDbTableField(name = "iam_token")
    var iamToken: String = ""

    @KaraokeDbTableField(name = "query")
    var query: String = ""

    @KaraokeDbTableField(name = "body")
    var body: String = ""

    @KaraokeDbTableField(name = "response_format")
    var responseFormat: String = "FORMAT_XML"

    @KaraokeDbTableField(name = "operation_id")
    var operationId: String = ""

    @KaraokeDbTableField(name = "done")
    var done: Boolean = false

    @KaraokeDbTableField(name = "raw_data")
    var rawData: String = ""

    @KaraokeDbTableField(name = "last_requested_at")
    var lastRequestedAt: Timestamp = LocalDateTime.now().toTimestamp()

    override fun toDTO(): SearchAsyncDTO {
        return SearchAsyncDTO(
            id = id,
            songId = songId,
            url = url,
            iamToken = iamToken,
            query = query,
            body = body,
            responseFormat = responseFormat,
            operationId = operationId,
            done = done,
            rawData = rawData,
            lastRequestedAt = lastRequestedAt,
        )
    }

    companion object {

        const val TABLE_NAME = "tbl_search_async"

        @Suppress("unused")
        fun listHashes(database: KaraokeConnection, whereText: String = ""): List<RecordHash>? = getListHashes(tableName = TABLE_NAME, database = database, whereText = whereText)

        private fun getWhereList(whereArgs: Map<String, String>): List<String> {
            val where: MutableList<String> = mutableListOf()

            if (whereArgs.containsKey("id")) where += "id = ${whereArgs["id"]}"
            if (whereArgs.containsKey("song_id")) where += "song_id = ${whereArgs["song_id"]}"
            if (whereArgs.containsKey("url")) where += "url = '${whereArgs["url"]}'"
            if (whereArgs.containsKey("iam_token")) where += "iam_token = '${whereArgs["iam_token"]}'"
            if (whereArgs.containsKey("query")) where += "query = '${whereArgs["query"]}'"
            if (whereArgs.containsKey("body")) where += "body = '${whereArgs["body"]}'"
            if (whereArgs.containsKey("response_format")) where += "response_format = '${whereArgs["response_format"]}'"
            if (whereArgs.containsKey("operation_id")) where += "operation_id = '${whereArgs["operation_id"]}'"
            if (whereArgs.containsKey("raw_data")) where += "raw_data = '${whereArgs["raw_data"]}'"
            if (whereArgs.containsKey("done")) {
                if (whereArgs["done"] == "+" || whereArgs["done"] == "true") {
                    where += "done = true"
                } else if (whereArgs["done"] == "-" || whereArgs["done"] == "false") {
                    where += "done = false"
                }
            }
            if (whereArgs.containsKey("timeout")) where += "NOW() - last_requested_at > INTERVAL '1 millisecond' * ${whereArgs["timeout"]}"

            return where
        }

        fun loadList(whereArgs: Map<String, String>,
                     limit: Int = 0,
                     offset: Int = 0,
                     database: KaraokeConnection,
                     storageService: KaraokeStorageService,
                     storageApiClient: StorageApiClient,
                     ignoreUseInList: Boolean
        ): List<SearchAsync> {
            return KaraokeDbTable.loadList(
                clazz = SearchAsync::class,
                tableName = TABLE_NAME,
                whereList = getWhereList(whereArgs),
                limit = limit,
                offset = offset,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
                ignoreUseInList = ignoreUseInList
            ).map { it as SearchAsync }
        }

        fun delete(id: Long, database: KaraokeConnection): Boolean {
            return KaraokeDbTable.delete(
                tableName = TABLE_NAME,
                id = id,
                database = database
            )
        }

        fun createNewSearchAsync(newSearchAsync: SearchAsync, database: KaraokeConnection): SearchAsync? {
            val newSearchAsyncInDb = KaraokeDbTable.createDbInstance(
                entity = newSearchAsync,
                database = database
            ) as? SearchAsync?
            newSearchAsyncInDb?.let {
                return it
            }
            return null
        }

        fun getSearchAsyncById(id: Long, database: KaraokeConnection, storageService: KaraokeStorageService, storageApiClient: StorageApiClient): SearchAsync? {
            return KaraokeDbTable.loadById(
                clazz = SearchAsync::class,
                tableName = TABLE_NAME,
                id = id,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient
            ) as? SearchAsync?
        }

        fun getSearchAsyncListBySongId(songId: Long, database: KaraokeConnection, storageService: KaraokeStorageService, storageApiClient: StorageApiClient): List<SearchAsync> {
            return loadList(
                whereArgs = mapOf(Pair("song_id", songId.toString())),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
                ignoreUseInList = true
            )
        }

        fun getSearchAsyncListNotDone(database: KaraokeConnection, storageService: KaraokeStorageService, storageApiClient: StorageApiClient): List<SearchAsync> {
            return loadList(
                whereArgs = mapOf(Pair("done", "false")),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
                ignoreUseInList = true
            )
        }

        fun getSearchAsyncListNotDoneAndTimeout(timeoutMs: Long = 30_000L, database: KaraokeConnection, storageService: KaraokeStorageService, storageApiClient: StorageApiClient): List<SearchAsync> {
            return loadList(
                whereArgs = mapOf("done" to "false", "timeout" to timeoutMs.toString()),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
                ignoreUseInList = true
            )
        }

        fun getSearchAsyncFirstNotDoneAndTimeout(timeoutMs: Long = 30_000L, database: KaraokeConnection, storageService: KaraokeStorageService, storageApiClient: StorageApiClient): SearchAsync? {
            val resultList= loadList(
                whereArgs = mapOf("done" to "false", "timeout" to timeoutMs.toString()),
                limit = 1,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
                ignoreUseInList = true
            )
            if (resultList.isNotEmpty()) return resultList.first()
            return null
        }
    }
    
}