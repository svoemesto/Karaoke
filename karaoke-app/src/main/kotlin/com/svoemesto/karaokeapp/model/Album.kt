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

/**
 * Сущность «Альбом» — отдельная таблица `tbl_albums` (один автор — много альбомов,
 * один альбом — много песен через `Song.albumId`).
 *
 * Содержит:
 * - `id`, `authorId` (FK на `tbl_authors.id`, `ON DELETE RESTRICT`).
 * - `year`, `name` — год выпуска и название.
 * - `albumType` — тип альбома, хранится как `AlbumType.dbValue` (строка); типизированный доступ
 *   через [albumTypeEnum] (reflection-слой `KaraokeDbTable` не поддерживает enum-поля напрямую —
 *   только скалярные типы, см. `KaraokeDbTable.kt`).
 * - `sortOrder` — порядок отображения внутри пары (`authorId`, `year`), меньше = раньше.
 *
 * Синхронизируется LOCAL↔SERVER через `GenericKaraokeDbTableSyncTarget<Album>` (`key = "albums"`).
 *
 * @see docs/features/dual-db-sync.md
 * @see specs/011-album-song-rename/data-model.md
 */
@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class Album(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable,
    Comparable<Album>,
    KaraokeDbTable {
    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "author_id")
    var authorId: Long = 0

    @KaraokeDbTableField(name = "year")
    var year: Int = 0

    @KaraokeDbTableField(name = "name")
    var name: String = ""

    @KaraokeDbTableField(name = "album_type")
    var albumType: String = AlbumType.STUDIO.dbValue

    @KaraokeDbTableField(name = "sort_order")
    var sortOrder: Int = 0

    var albumTypeEnum: AlbumType
        get() = AlbumType.fromDb(albumType)
        set(value) {
            albumType = value.dbValue
        }

    override fun compareTo(other: Album): Int =
        compareValuesBy(this, other, { it.authorId }, { it.year }, { it.sortOrder }, { it.name })

    override fun toDTO(): AlbumDTO =
        AlbumDTO(
            id = id,
            authorId = authorId,
            year = year,
            name = name,
            albumType = albumType,
            sortOrder = sortOrder,
        )

    companion object {
        const val TABLE_NAME = "tbl_albums"

        @Suppress("unused")
        fun listHashes(
            database: KaraokeConnection,
            whereText: String = "",
        ): List<RecordHash>? = getListHashes(tableName = TABLE_NAME, database = database, whereText = whereText)

        private fun getWhereList(whereArgs: Map<String, String>): List<String> {
            val where: MutableList<String> = mutableListOf()
            if (whereArgs.containsKey("id")) where += "id=${whereArgs["id"]}"
            if (whereArgs.containsKey("author_id")) where += "author_id=${whereArgs["author_id"]}"
            if (whereArgs.containsKey("year")) where += "year=${whereArgs["year"]}"
            if (whereArgs.containsKey("name")) where += "name = '${whereArgs["name"]?.replace("'", "''")}'"
            if (whereArgs.containsKey("album_type")) where += "album_type = '${whereArgs["album_type"]}'"
            return where
        }

        fun loadList(
            whereArgs: Map<String, String>,
            limit: Int = 0,
            offset: Int = 0,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
            ignoreUseInList: Boolean = true,
        ): List<Album> =
            KaraokeDbTable
                .loadList(
                    clazz = Album::class,
                    tableName = TABLE_NAME,
                    whereList = getWhereList(whereArgs),
                    limit = limit,
                    offset = offset,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    ignoreUseInList = ignoreUseInList,
                ).map { it as Album }

        fun delete(
            id: Long,
            database: KaraokeConnection,
        ): Boolean =
            KaraokeDbTable.delete(
                tableName = TABLE_NAME,
                id = id,
                database = database,
            )

        fun createNewAlbum(
            newAlbum: Album,
            database: KaraokeConnection,
        ): Album? =
            (
                KaraokeDbTable.createDbInstance(
                    entity = newAlbum,
                    database = database,
                ) as? Album?
            )

        fun getAlbumById(
            id: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Album? =
            KaraokeDbTable.loadById(
                clazz = Album::class,
                tableName = TABLE_NAME,
                id = id,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) as? Album?

        fun getAlbumsByIds(
            ids: List<Long>,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Map<Long, Album> =
            KaraokeDbTable
                .loadByIds(
                    clazz = Album::class,
                    tableName = TABLE_NAME,
                    ids = ids,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).filterIsInstance<Album>()
                .associateBy { it.id }

        /**
         * Найти альбом по (авторId, год, название) — для идемпотентного бэкфилла и для UI-проверки
         * дублей при ручном создании (см. также уникальный констрейнт `tbl_albums_author_year_name_key`).
         */
        fun getAlbumByAuthorYearName(
            authorId: Long,
            year: Int,
            name: String,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Album? =
            loadList(
                whereArgs = mapOf("author_id" to "$authorId", "year" to "$year", "name" to name),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ).firstOrNull()

        fun getAlbumsByAuthorId(
            authorId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<Album> =
            loadList(
                whereArgs = mapOf("author_id" to "$authorId"),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )

        /**
         * Найти или создать альбом по свободнотекстовым автору/году/названию — используется как
         * при импорте новых песен из папки ([Song.createFromPath]), так и одноразовым бэкфиллом
         * ([AlbumBackfill]). Создаёт (или переиспользует) [Author] по имени. Пустое название альбома
         * — валидный случай "без альбома" (сингл), возвращает `null`, альбом не создаётся (FR-006).
         * Новый альбом получает `sortOrder` = "в конец" (максимум среди уже существующих альбомов
         * этого автора+года плюс один) — не пытается угадать алфавитную позицию среди ещё не
         * загруженных альбомов, чтобы не переставлять уже сохранённый вручную порядок соседей.
         */
        fun findOrCreateForSongImport(
            authorName: String,
            year: Int,
            albumName: String,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Album? {
            if (albumName.isBlank()) return null
            val author =
                Author.getAuthorByName(
                    author = authorName,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ) ?: Author
                    .createNewAuthor(
                        newAuthor =
                            Author(database = database, storageService = storageService, storageApiClient = storageApiClient).apply {
                                this.author = authorName
                            },
                        database = database,
                    ) ?: return null

            getAlbumByAuthorYearName(
                authorId = author.id,
                year = year,
                name = albumName,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { return it }

            val nextSortOrder =
                getAlbumsByAuthorId(author.id, database, storageService, storageApiClient)
                    .filter { it.year == year }
                    .maxOfOrNull { it.sortOrder }
                    ?.plus(1) ?: 0

            return createNewAlbum(
                newAlbum =
                    Album(database = database, storageService = storageService, storageApiClient = storageApiClient).apply {
                        this.authorId = author.id
                        this.year = year
                        this.name = albumName
                        this.sortOrder = nextSortOrder
                    },
                database = database,
            )
        }
    }
}
