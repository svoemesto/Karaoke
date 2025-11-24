package com.svoemesto.karaokeapp.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.KaraokeDbTable.Companion.getListHashes
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import java.io.Serializable

@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class Author(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable, Comparable<Author>, KaraokeDbTable {

    override fun getTableName() = "tbl_authors"

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "author")
    var author: String = "Author name"

    @KaraokeDbTableField(name = "ym_id")
    var ymId: String = ""

    @KaraokeDbTableField(name = "last_album_ym")
    var lastAlbumYm: String = ""

    @KaraokeDbTableField(name = "last_album_processed")
    var lastAlbumProcessed: String = ""

    @KaraokeDbTableField(name = "watched")
    var watched: Boolean = false

    @KaraokeDbTableField(name = "skip")
    var skip: Boolean = false

    val haveNewAlbum: Boolean get() = watched && ymId != "" && lastAlbumYm != lastAlbumProcessed
    override fun compareTo(other: Author): Int {
        return author.compareTo(other.author)
    }

    override fun toDTO(): AuthorDTO {
        val picture = Pictures.loadList(
            whereArgs = mapOf("name" to author),
            limit = 1,
            database = database,
            storageService = storageService,
            storageApiClient = storageApiClient,
            ignoreUseInList = true
        ).firstOrNull()
        val (pictureId, picturePreview) = picture?.let { Pair(it.id, it.preview) } ?: Pair(0L, "")
        return AuthorDTO(
                id = id,
                author = author,
                ymId = ymId,
                lastAlbumYm = lastAlbumYm,
                lastAlbumProcessed = lastAlbumProcessed,
                watched = watched,
                skip = skip,
                haveNewAlbum = haveNewAlbum,
                pictureId = pictureId,
                picturePreview = picturePreview
        )
    }

    companion object {

        const val TABLE_NAME = "tbl_authors"

        @Suppress("unused")
        fun listHashes(database: KaraokeConnection, whereText: String = ""): List<RecordHash>? = getListHashes(tableName = TABLE_NAME, database = database, whereText = whereText)

        private fun getWhereList(whereArgs: Map<String, String>): List<String> {
            val where: MutableList<String> = mutableListOf()

            if (whereArgs.containsKey("id")) where += "id=${whereArgs["id"]}"
            if (whereArgs.containsKey("author")) where += "author = '${whereArgs["author"]}'"
            if (whereArgs.containsKey("ym_id")) where += "ym_id = '${whereArgs["ym_id"]}'"
            if (whereArgs.containsKey("last_album_ym")) where += "last_album_ym = '${whereArgs["last_album_ym"]}'"
            if (whereArgs.containsKey("last_album_processed")) where += "last_album_processed = '${whereArgs["last_album_processed"]}'"
            if (whereArgs.containsKey("watched")) {
                if (whereArgs["watched"] == "+" || whereArgs["watched"] == "true") {
                    where += "watched = true"
                } else if (whereArgs["watched"] == "-" || whereArgs["watched"] == "false") {
                    where += "watched = false"
                }
            }
            if (whereArgs.containsKey("haveNewAlbum")) {
                if (whereArgs["haveNewAlbum"] == "+" || whereArgs["haveNewAlbum"] == "true") {
                    where += "(watched = true AND ym_id <> '' AND last_album_ym <> last_album_processed)"
                } else if (whereArgs["haveNewAlbum"] == "-" || whereArgs["haveNewAlbum"] == "false") {
                    where += "(watched = false OR ym_id = '' OR last_album_ym = last_album_processed)"
                }
            }
            if (whereArgs.containsKey("skip")) {
                if (whereArgs["skip"] == "+" || whereArgs["skip"] == "true") {
                    where += "skip = true"
                } else if (whereArgs["skip"] == "-" || whereArgs["skip"] == "false") {
                    where += "skip = false"
                }
            }

            return where
        }

        fun loadList(whereArgs: Map<String, String>,
                     limit: Int = 0,
                     offset: Int = 0,
                     database: KaraokeConnection,
                     storageService: KaraokeStorageService,
                     storageApiClient: StorageApiClient,
                     ignoreUseInList: Boolean
        ): List<Author> {
            return KaraokeDbTable.loadList(
                clazz = Author::class,
                tableName = TABLE_NAME,
                whereList = getWhereList(whereArgs),
                limit = limit,
                offset = offset,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
                ignoreUseInList = ignoreUseInList
            ).map { it as Author }
        }

        fun delete(id: Long, database: KaraokeConnection): Boolean {
            return KaraokeDbTable.delete(
                tableName = TABLE_NAME,
                id = id,
                database = database
            )
        }

        fun createNewAuthor(newAuthor: Author, database: KaraokeConnection): Author? {
            val newAuthorInDb = KaraokeDbTable.createDbInstance(
                entity = newAuthor,
                database = database
            ) as? Author?
            newAuthorInDb?.let {
                return it
            }
            return null
        }

        fun getAuthorById(id: Long, database: KaraokeConnection, storageService: KaraokeStorageService, storageApiClient: StorageApiClient): Author? {
            return KaraokeDbTable.loadById(
                clazz = Author::class,
                tableName = TABLE_NAME,
                id = id,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient
            ) as? Author?
        }

        fun getAuthorByName(author: String, database: KaraokeConnection, storageService: KaraokeStorageService, storageApiClient: StorageApiClient): Author? {
            return loadList(
                whereArgs = mapOf(Pair("author", author)),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
                ignoreUseInList = true
            ).firstOrNull()
        }
    }
}