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
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant

/**
 * Сущность «Автор» (исполнитель) — отдельная таблица `tbl_authors` для
 * переиспользования (один автор — много песен).
 *
 * Содержит:
 * - `id`, `author` — имя.
 * - `ymId`, `vkId` — ID на Яндекс.Музыке и VK (для парсинга).
 * - `pathToFolder` — папка с исходниками (audio/text/chords).
 * - `firstAlbumId` — ID первого альбома (для UI группировки).
 *
 * Синхронизируется LOCAL↔SERVER через `SyncTarget<Author>`.
 *
 * @see docs/features/dual-db-sync.md
 * @see docs/features/llm-lyrics-search.md (использование `ymId` для парсинга)
 */
@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class Author(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable,
    Comparable<Author>,
    KaraokeDbTable {
    override fun getTableName() = "tbl_authors"

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "author")
    var author: String = "Author name"

    @KaraokeDbTableField(name = "ym_id")
    var ymId: String = ""

    @KaraokeDbTableField(name = "vk_id")
    var vkId: String = ""

    @KaraokeDbTableField(name = "last_album_ym")
    var lastAlbumYm: String = ""

    @KaraokeDbTableField(name = "last_album_vk")
    var lastAlbumVk: String = ""

    @KaraokeDbTableField(name = "last_album_processed")
    var lastAlbumProcessed: String = ""

    @KaraokeDbTableField(name = "watched")
    var watched: Boolean = false

    @KaraokeDbTableField(name = "skip")
    var skip: Boolean = false

    @KaraokeDbTableField(name = "aliases")
    var aliases: String = ""

    val haveNewAlbum: Boolean get() =
        watched &&
            (ymId != "" || vkId != "") &&
            (lastAlbumYm != lastAlbumProcessed || lastAlbumVk != lastAlbumProcessed)

    override fun compareTo(other: Author): Int = author.compareTo(other.author)

    override fun toDTO(): AuthorDTO {
        val picture =
            Pictures
                .loadList(
                    whereArgs = mapOf("name" to author),
                    limit = 1,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    ignoreUseInList = true,
                ).firstOrNull()
        val (pictureId, picturePreviewUrl) =
            picture?.let {
                Pair(
                    it.id,
                    "/api/picture/file?file=${java.net.URLEncoder.encode(
                        it.storageFileNamePreview,
                        java.nio.charset.StandardCharsets.UTF_8,
                    )}",
                )
            } ?: Pair(0L, "")
        return AuthorDTO(
            id = id,
            author = author,
            ymId = ymId,
            vkId = vkId,
            lastAlbumYm = lastAlbumYm,
            lastAlbumVk = lastAlbumVk,
            lastAlbumProcessed = lastAlbumProcessed,
            watched = watched,
            skip = skip,
            aliases = aliases,
            haveNewAlbum = haveNewAlbum,
            pictureId = pictureId,
            picturePreview = "",
            picturePreviewUrl = picturePreviewUrl,
        )
    }

    companion object {
        const val TABLE_NAME = "tbl_authors"

        @Suppress("unused")
        fun listHashes(
            database: KaraokeConnection,
            whereText: String = "",
        ): List<RecordHash>? = getListHashes(tableName = TABLE_NAME, database = database, whereText = whereText)

        private fun getWhereList(whereArgs: Map<String, String>): List<String> {
            val where: MutableList<String> = mutableListOf()

            if (whereArgs.containsKey("id")) where += "id=${whereArgs["id"]}"
            if (whereArgs.containsKey("author")) where += "author = '${whereArgs["author"]}'"
            if (whereArgs.containsKey("ym_id")) where += "ym_id = '${whereArgs["ym_id"]}'"
            if (whereArgs.containsKey("vk_id")) where += "vk_id = '${whereArgs["vk_id"]}'"
            if (whereArgs.containsKey("last_album_ym")) where += "last_album_ym = '${whereArgs["last_album_ym"]}'"
            if (whereArgs.containsKey("last_album_vk")) where += "last_album_vk = '${whereArgs["last_album_vk"]}'"
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
                    where +=
                        "(watched = true AND (ym_id <> '' OR vk_id <> '') AND (last_album_ym <> last_album_processed OR last_album_vk <> last_album_processed))"
                } else if (whereArgs["haveNewAlbum"] == "-" || whereArgs["haveNewAlbum"] == "false") {
                    where +=
                        "(watched = false OR (ym_id = '' AND vk_id = '') OR (last_album_ym = last_album_processed OR last_album_vk = last_album_processed))"
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

        fun loadList(
            whereArgs: Map<String, String>,
            limit: Int = 0,
            offset: Int = 0,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
            ignoreUseInList: Boolean,
        ): List<Author> =
            KaraokeDbTable
                .loadList(
                    clazz = Author::class,
                    tableName = TABLE_NAME,
                    whereList = getWhereList(whereArgs),
                    limit = limit,
                    offset = offset,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    ignoreUseInList = ignoreUseInList,
                ).map { it as Author }

        fun delete(
            id: Long,
            database: KaraokeConnection,
        ): Boolean =
            KaraokeDbTable.delete(
                tableName = TABLE_NAME,
                id = id,
                database = database,
            )

        fun createNewAuthor(
            newAuthor: Author,
            database: KaraokeConnection,
        ): Author? {
            val newAuthorInDb =
                KaraokeDbTable.createDbInstance(
                    entity = newAuthor,
                    database = database,
                ) as? Author?
            newAuthorInDb?.let {
                return it
            }
            return null
        }

        fun getAuthorById(
            id: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Author? =
            KaraokeDbTable.loadById(
                clazz = Author::class,
                tableName = TABLE_NAME,
                id = id,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) as? Author?

        fun getAuthorsByIds(
            ids: List<Long>,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Map<Long, Author> =
            KaraokeDbTable
                .loadByIds(
                    clazz = Author::class,
                    tableName = TABLE_NAME,
                    ids = ids,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).filterIsInstance<Author>()
                .associateBy { it.id }

        fun getAuthorByName(
            author: String,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Author? =
            loadList(
                whereArgs = mapOf(Pair("author", author)),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
                ignoreUseInList = true,
            ).firstOrNull()

        /**
         * Ищет авторов, у которых term совпадает с реальным именем ЛИБО с одним из алиасов
         * (солист/участник группы). Лёгкий raw-SELECT только по tbl_authors — безопасно
         * вызывать и из karaoke-web (не создаёт полноценных сущностей Author, не трогает storage).
         * matchedAliases в результате — только те алиасы, по которым term реально совпал
         * (пусто, если совпадение произошло по самому имени автора).
         */
        fun resolveByTerm(
            term: String,
            database: KaraokeConnection,
        ): List<AuthorAliasMatch> {
            val result: MutableList<AuthorAliasMatch> = mutableListOf()
            val termLower = term.trim().lowercase()
            if (termLower.isEmpty()) return result

            val connection = database.getConnection() ?: return result
            val sql = "SELECT author, aliases FROM $TABLE_NAME WHERE LOWER(author) = ? OR LOWER(aliases) LIKE ?"
            try {
                connection.prepareStatement(sql).use { ps ->
                    ps.setString(1, termLower)
                    ps.setString(2, "%$termLower%")
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            val authorName = rs.getString("author") ?: continue
                            val aliasesRaw = rs.getString("aliases") ?: ""
                            val matched =
                                aliasesRaw
                                    .split(";")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() && it.lowercase().contains(termLower) }
                            result.add(AuthorAliasMatch(author = authorName, matchedAliases = matched))
                        }
                    }
                }
            } catch (e: SQLException) {
                println("[${Timestamp.from(Instant.now())}] Author.resolveByTerm SQLException: ${e.message}")
            }
            return result
        }
    }
}

/**
 * Класс Author Alias Match.
 *
 * @see docs/features/dual-db-sync.md
 */
data class AuthorAliasMatch(
    val author: String,
    val matchedAliases: List<String>,
)
