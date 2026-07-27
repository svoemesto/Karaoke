package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import java.sql.Timestamp
import java.time.Instant

/**
 * Одноразовый идемпотентный бэкфилл сущности [Album] из уже существующих у песен свободнотекстовых
 * полей автор/год/альбом (`song_author`/`song_year`/`song_album`). См. алгоритм —
 * `specs/011-album-song-rename/research.md` §6.
 *
 * Обрабатывает только строки `tbl_songs` с непустым `song_album` и `album_id IS NULL` — уже
 * привязанные вручную или ранее забэкфилленные песни не трогает (безопасно перезапускать).
 * Группирует по точному совпадению текста `(song_author, song_year, song_album)`; для каждой
 * уникальной группы находит/создаёт [Author] по имени и [Album] (уникальный констрейнт
 * `tbl_albums_author_year_name_key` защищает от дублей при гонках/повторных запусках).
 *
 * @see specs/011-album-song-rename/data-model.md
 * @see docs/features/dual-db-sync.md
 */
object AlbumBackfill {
    data class SongAlbumGroup(
        val author: String,
        val year: Int,
        val album: String,
        val songIds: List<Long>,
    )

    /**
     * @return сводка по результату: число обработанных групп, созданных альбомов, привязанных песен.
     */
    fun run(
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
    ): AlbumBackfillResult {
        val connection =
            database.getConnection() ?: run {
                println("[${Timestamp.from(Instant.now())}] AlbumBackfill: нет соединения с БД ${database.name}")
                return AlbumBackfillResult(0, 0, 0, 0)
            }

        val groups = mutableListOf<SongAlbumGroup>()
        connection
            .prepareStatement(
                """
                SELECT song_author, song_year, song_album, array_agg(id) AS song_ids
                FROM tbl_songs
                WHERE song_album <> '' AND album_id IS NULL
                GROUP BY song_author, song_year, song_album
                """.trimIndent(),
            ).use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val idsArray = rs.getArray("song_ids").array as Array<*>
                        groups.add(
                            SongAlbumGroup(
                                author = rs.getString("song_author") ?: "",
                                year = rs.getInt("song_year"),
                                album = rs.getString("song_album") ?: "",
                                songIds = idsArray.map { (it as Number).toLong() },
                            ),
                        )
                    }
                }
            }

        // Порядок отображения по умолчанию (research.md §6 / assumption spec.md): внутри пары
        // (автор, год) — по алфавиту названия альбома, как и было раньше в Zakroma.kt (вычисляется
        // ниже через sortedBy+forEachIndexed непосредственно в цикле обработки групп).
        var albumsCreated = 0
        var albumsReused = 0
        var songsLinked = 0

        groups
            .groupBy { it.author to it.year }
            .forEach { (authorYear, groupsForAuthorYear) ->
                val (authorName, year) = authorYear
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
                        )
                if (author == null) {
                    println("[${Timestamp.from(Instant.now())}] AlbumBackfill: не удалось найти/создать автора «$authorName», пропуск ${groupsForAuthorYear.size} групп")
                    return@forEach
                }

                groupsForAuthorYear
                    .sortedBy { it.album.lowercase() }
                    .forEachIndexed { index, group ->
                        val existingAlbum =
                            Album.getAlbumByAuthorYearName(
                                authorId = author.id,
                                year = year,
                                name = group.album,
                                database = database,
                                storageService = storageService,
                                storageApiClient = storageApiClient,
                            )
                        val album =
                            existingAlbum ?: Album
                                .createNewAlbum(
                                    newAlbum =
                                        Album(database = database, storageService = storageService, storageApiClient = storageApiClient)
                                            .apply {
                                                this.authorId = author.id
                                                this.year = year
                                                this.name = group.album
                                                this.sortOrder = index
                                            },
                                    database = database,
                                )
                        if (album == null) {
                            println(
                                "[${Timestamp.from(Instant.now())}] AlbumBackfill: не удалось создать альбом " +
                                    "«${group.album}» ($year, автор «$authorName»), пропуск ${group.songIds.size} песен",
                            )
                            return@forEachIndexed
                        }
                        if (existingAlbum == null) albumsCreated++ else albumsReused++

                        connection
                            .prepareStatement(
                                "UPDATE tbl_songs SET album_id = ? WHERE id = ANY(?) AND album_id IS NULL",
                            ).use { ps ->
                                ps.setLong(1, album.id)
                                ps.setArray(2, connection.createArrayOf("bigint", group.songIds.toTypedArray()))
                                songsLinked += ps.executeUpdate()
                            }
                    }
            }

        println(
            "[${Timestamp.from(Instant.now())}] AlbumBackfill: групп=${groups.size}, альбомов создано=$albumsCreated, " +
                "альбомов переиспользовано=$albumsReused, песен привязано=$songsLinked",
        )
        return AlbumBackfillResult(
            groupsProcessed = groups.size,
            albumsCreated = albumsCreated,
            albumsReused = albumsReused,
            songsLinked = songsLinked,
        )
    }
}

data class AlbumBackfillResult(
    val groupsProcessed: Int,
    val albumsCreated: Int,
    val albumsReused: Int,
    val songsLinked: Int,
)
