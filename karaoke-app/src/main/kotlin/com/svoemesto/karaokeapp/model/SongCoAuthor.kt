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
 * Связь «песня × дополнительный автор» (`tbl_song_authors`) — многие-ко-многим, в дополнение к
 * главному автору песни (`Song.author`, свободный текст). Чисто информационная: НЕ влияет на
 * группировку по автору, URL/страницу автора на публичном сайте и на принадлежность альбому
 * (везде используется только главный автор, см. FR-010).
 *
 * Синхронизируется LOCAL↔SERVER через `GenericKaraokeDbTableSyncTarget<SongCoAuthor>`
 * (`key = "songcoauthors"`).
 *
 * @see archive/archive/docs/features/dual-db-sync.md
 * @see specs/011-album-song-rename/data-model.md
 */
@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class SongCoAuthor(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable,
    Comparable<SongCoAuthor>,
    KaraokeDbTable {
    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "song_id")
    var songId: Long = 0

    @KaraokeDbTableField(name = "author_id")
    var authorId: Long = 0

    override fun compareTo(other: SongCoAuthor): Int = compareValuesBy(this, other, { it.songId }, { it.authorId })

    override fun toDTO(): SongCoAuthorDTO =
        SongCoAuthorDTO(
            id = id,
            songId = songId,
            authorId = authorId,
        )

    companion object {
        const val TABLE_NAME = "tbl_song_authors"

        @Suppress("unused")
        fun listHashes(
            database: KaraokeConnection,
            whereText: String = "",
        ): List<RecordHash>? = getListHashes(tableName = TABLE_NAME, database = database, whereText = whereText)

        private fun getWhereList(whereArgs: Map<String, String>): List<String> {
            val where: MutableList<String> = mutableListOf()
            if (whereArgs.containsKey("id")) where += "id=${whereArgs["id"]}"
            if (whereArgs.containsKey("song_id")) where += "song_id=${whereArgs["song_id"]}"
            if (whereArgs.containsKey("author_id")) where += "author_id=${whereArgs["author_id"]}"
            return where
        }

        fun loadList(
            whereArgs: Map<String, String>,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<SongCoAuthor> =
            KaraokeDbTable
                .loadList(
                    clazz = SongCoAuthor::class,
                    tableName = TABLE_NAME,
                    whereList = getWhereList(whereArgs),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    ignoreUseInList = true,
                ).map { it as SongCoAuthor }

        fun getCoAuthorsBySongId(
            songId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<SongCoAuthor> =
            loadList(
                whereArgs = mapOf("song_id" to "$songId"),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )

        fun createNew(
            newSongCoAuthor: SongCoAuthor,
            database: KaraokeConnection,
        ): SongCoAuthor? =
            (
                KaraokeDbTable.createDbInstance(
                    entity = newSongCoAuthor,
                    database = database,
                ) as? SongCoAuthor?
            )

        fun delete(
            id: Long,
            database: KaraokeConnection,
        ): Boolean =
            KaraokeDbTable.delete(
                tableName = TABLE_NAME,
                id = id,
                database = database,
            )
    }
}
