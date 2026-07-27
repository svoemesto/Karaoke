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
 * - `sortOrder` — порядок отображения альбомов автора, сквозной (не привязан к году), меньше = раньше.
 *   Переупорядочивается драг-дропом в модалке "Альбомы автора" (webvue3, компонент Authors).
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

    /** Краткий текст-факт об альбоме (без разметки/выделения), показывается в тултипе на Закромах. */
    @KaraokeDbTableField(name = "description")
    var description: String = ""

    /** Короткая пометка после названия альбома на Закромах (напр. "Remastered 2018"). */
    @KaraokeDbTableField(name = "short_description")
    var shortDescription: String = ""

    /** Предупреждение, показываемое красным над названием альбома на Закромах. */
    @KaraokeDbTableField(name = "warning")
    var warning: String = ""

    var albumTypeEnum: AlbumType
        get() = AlbumType.fromDb(albumType)
        set(value) {
            albumType = value.dbValue
        }

    override fun compareTo(other: Album): Int =
        compareValuesBy(this, other, { it.authorId }, { it.sortOrder }, { it.year }, { it.name })

    override fun toDTO(): AlbumDTO {
        // Превью автора/альбома ищутся в tbl_pictures по имени — тот же паттерн, что и
        // Author.toDTO() (по имени автора) и Zakroma.kt (по "$author - $year - $name" для
        // альбома). Пустое имя автора (не должно случаться при корректном authorId) — без превью.
        val author = Author.getAuthorById(authorId, database, storageService, storageApiClient)
        val authorName = author?.author ?: ""
        val authorPicture =
            authorName
                .takeIf { it.isNotEmpty() }
                ?.let { Pictures.getPictureByName(name = it, database = database, storageService = storageService, storageApiClient = storageApiClient) }
        val albumPicture =
            authorName
                .takeIf { it.isNotEmpty() }
                ?.let {
                    Pictures.getPictureByName(
                        name = "$it - $year - $name",
                        database = database,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                }
        return AlbumDTO(
            id = id,
            authorId = authorId,
            authorName = authorName,
            year = year,
            name = name,
            albumType = albumType,
            sortOrder = sortOrder,
            description = description,
            shortDescription = shortDescription,
            warning = warning,
            authorPictureId = authorPicture?.id ?: 0,
            authorPicturePreviewUrl = authorPicture?.toDTO()?.previewUrl ?: "",
            albumPictureId = albumPicture?.id ?: 0,
            albumPicturePreviewUrl = albumPicture?.toDTO()?.previewUrl ?: "",
        )
    }

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
            // Частичный поиск по названию для UI-фильтра (в отличие от точного "name" выше,
            // используемого getAlbumByAuthorYearName/findOrCreateForSongImport — их менять нельзя).
            if (whereArgs.containsKey("name_search")) {
                where += "LOWER(name) LIKE '%${whereArgs["name_search"]?.lowercase()?.replace("'", "''")}%'"
            }
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
         * Батч-подсчёт количества песен по списку album_id.
         * Один SQL-запрос с `album_id = ANY(?)` + `GROUP BY album_id` (см. [AlbumBackfill]
         * — тот же паттерн setArray/createArrayOf). Используется в [apisAlbumsDigest] для
         * денормализации `AlbumDTO.songsCount` (UI-таблица "Альбомы" в webvue3).
         *
         * @return map albumId -> songsCount; альбомы без песен в map не попадают
         * (потребитель должен дефолтить на 0 — см. ApiController).
         */
        fun countSongsByAlbumIds(
            ids: List<Long>,
            database: KaraokeConnection,
        ): Map<Long, Int> {
            if (ids.isEmpty()) return emptyMap()
            val connection = database.getConnection() ?: return emptyMap()
            val result = mutableMapOf<Long, Int>()
            val sql = "SELECT album_id, COUNT(*) AS cnt FROM tbl_songs WHERE album_id = ANY(?) GROUP BY album_id"
            connection.prepareStatement(sql).use { ps ->
                ps.setArray(1, connection.createArrayOf("bigint", ids.toTypedArray()))
                val rs = ps.executeQuery()
                while (rs.next()) {
                    val albumId = rs.getLong("album_id")
                    if (!rs.wasNull()) {
                        result[albumId] = rs.getInt("cnt")
                    }
                }
            }
            return result
        }

        /**
         * Возвращает id «репрезентативной» песни альбома — песни с минимальным id в этом альбоме.
         * `null`, если у альбома нет песен.
         *
         * Используется в админском компоненте `webvue3/src/components/Albums/AlbumsTable.vue`
         * для контекста [AlbumCoverModal]: модалка привязана к конкретной песне через
         * `currentSongId` (нужен [Song.rootFolder] для чтения/записи `LogoAlbum.png`).
         * Возвращаемый id — это «репрезентативная» песня альбома, через которую модалка
         * получает доступ к обложке. Никак не модифицирует данные.
         *
         * **Замечание:** в [Song] есть in-memory поле [Song.firstSongInAlbum] (Boolean), но
         * оно **не сохраняется в БД** (нет колонки «first_song_in_album» в «tbl_songs» —
         * см. миграции в «deploy/karaoke-db/», нет упоминания в [Song.getSqlToInsert]).
         * Поэтому «семантический» вариант `WHERE first_song_in_album = TRUE` невозможен —
         * используем `MIN(id)` как единственный стабильный критерий.
         *
         * @return id песни альбома или `null`, если у альбома нет ни одной песни
         * @see specs/014-album-cell-album-cover-modal/contracts/api.md
         */
        fun getFirstSongId(
            albumId: Long,
            database: KaraokeConnection,
        ): Long? {
            val connection = database.getConnection() ?: return null

            // Репрезентативная песня альбома — с минимальным id. Это стабильный
            // критерий, не зависящий от того, выставлен ли first_song_in_album (который
            // сейчас не сохраняется в БД — см. KDoc).
            val sql =
                """
                SELECT id FROM tbl_songs
                WHERE album_id = ?
                ORDER BY id
                LIMIT 1
                """.trimIndent()
            connection.prepareStatement(sql).use { ps ->
                ps.setLong(1, albumId)
                val rs = ps.executeQuery()
                if (rs.next()) {
                    val id = rs.getLong("id")
                    if (!rs.wasNull()) return id
                }
            }

            return null
        }

        /**
         * Переупорядочить альбомы (например, после drag-and-drop в модалке "Альбомы автора"):
         * `orderedIds` — id альбомов в желаемом порядке отображения (сквозном, не по годам),
         * каждому присваивается `sortOrder` = его индекс в списке. Альбомы, чей `sortOrder` уже
         * совпадает с целевым, не пересохраняются. Id, не найденные в БД, молча пропускаются.
         */
        fun reorderAlbums(
            orderedIds: List<Long>,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ) {
            val albumsById = getAlbumsByIds(orderedIds, database, storageService, storageApiClient)
            orderedIds.forEachIndexed { index, id ->
                albumsById[id]?.let { album ->
                    if (album.sortOrder != index) {
                        album.sortOrder = index
                        album.save()
                    }
                }
            }
        }

        /**
         * Одноразовая (идемпотентная) миграция: раньше `sortOrder` был порядковым номером ВНУТРИ
         * пары (автор, год) (см. [AlbumBackfill], старый компаратор), теперь — сквозной по автору
         * (см. [reorderAlbums]). Пересчитывает `sortOrder` = индекс при сортировке по (год, старый
         * `sortOrder`, название) — то есть сохраняет прежний видимый порядок альбомов, просто
         * "разворачивая" его в сквозную нумерацию. Повторный запуск на уже нормализованных данных
         * ничего не меняет.
         *
         * @return число обновлённых записей.
         */
        fun normalizeSortOrderAcrossYears(
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Int {
            var updated = 0
            loadList(
                whereArgs = emptyMap(),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ).groupBy { it.authorId }
                .forEach { (_, albumsOfAuthor) ->
                    albumsOfAuthor
                        .sortedWith(compareBy({ it.year }, { it.sortOrder }, { it.name }))
                        .forEachIndexed { index, album ->
                            if (album.sortOrder != index) {
                                album.sortOrder = index
                                album.save()
                                updated++
                            }
                        }
                }
            return updated
        }

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
         * Новый альбом получает `sortOrder` = "в конец" (максимум среди ВСЕХ уже существующих
         * альбомов этого автора плюс один — порядок сквозной по автору, не привязан к году) — не
         * пытается угадать алфавитную позицию среди ещё не загруженных альбомов, чтобы не
         * переставлять уже сохранённый вручную порядок соседей.
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
