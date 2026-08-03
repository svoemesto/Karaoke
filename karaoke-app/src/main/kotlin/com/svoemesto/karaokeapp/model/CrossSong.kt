package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import java.io.Serializable

/**
 * Класс Cross Song Row.
 *
 * @see docs/features/dual-db-sync.md
 */
data class CrossSongRow(
    val csrId: Int,
    val csrName: String,
    val csrCells: List<CrossSongCell>,
) : Serializable,
    Comparable<CrossSongRow> {
    override fun compareTo(other: CrossSongRow): Int = sortString.compareTo(other.sortString)

    private val sortString: String get() {
        val result =
            if (csrName.contains(".")) {
                csrName.split(".").asReversed().joinToString("")
            } else {
                "%015d".format(csrId)
            }
        return result
    }
}

/**
 * Класс Cross Song Cell.
 *
 * @see docs/features/dual-db-sync.md
 */
data class CrossSongCell(
    val cscIs: Int,
    val cscName: String,
    var songDTO: SongDTO? = null,
) : Serializable,
    Comparable<CrossSongCell> {
    override fun compareTo(other: CrossSongCell): Int = cscIs.compareTo(other.cscIs)
}

// fun main() {
//    APP_WORK_IN_CONTAINER = false
//    val listOfSongs = Song.loadListFromDb(mapOf(Pair("song_author", "Ундервуд")), WORKING_DATABASE)
//    CrossSong.publications(
//        listOfSongs = listOfSongs
//    )
//
//    CrossSong.unpublications(
//        listOfSongs = listOfSongs
//    )
//
// }

/**
 * Кросс-настройки (между `Song` и связанными сущностями).
 *
 * Содержит методы для поиска «перекрёстных» данных:
 * - По `id` найти `Author`, `Album` (через `SongAssignment`/`Picture`).
 * - По `idAuthor` найти все его песни.
 * - По `idSiteUser` найти все его `Subscription`/`CartItem`.
 *
 * Используется для UI-фильтров и отчётов.
 *
 * @see docs/features/dual-db-sync.md
 */
class CrossSong {
    companion object {
        fun publications(
            listOfSongs: List<Song>,
            rowField: SongField = SongField.DATE,
            columnField: SongField = SongField.TIME,
        ): List<CrossSongRow> {
            val columns =
                listOfSongs
                    .map { sett ->
                        val fields = sett.javaClass.getDeclaredField("fields")
                        fields.isAccessible = true
                        (fields.get(sett) as Map<*, *>)[columnField] as String
                    }.distinct()
                    .sortedBy {
                        if (columnField == SongField.DATE) {
                            it.split(".").asReversed().joinToString("")
                        } else {
                            it
                        }
                    }

            val rows =
                listOfSongs
                    .map { sett ->
                        val fields = sett.javaClass.getDeclaredField("fields")
                        fields.isAccessible = true
                        (fields.get(sett) as Map<*, *>)[rowField] as String
                    }.distinct()

            val listCSR =
                rows.mapIndexed { rowIndex, rowName ->
                    CrossSongRow(
                        csrId = rowIndex,
                        csrName = rowName,
                        csrCells =
                            columns.mapIndexed { columnIndex, columnName ->
                                CrossSongCell(cscIs = columnIndex, cscName = columnName)
                            },
                    )
                }

            listOfSongs.forEach { sett ->
                val fields = sett.javaClass.getDeclaredField("fields")
                fields.isAccessible = true
                val fldRow = (fields.get(sett) as Map<*, *>)[rowField] as String
                val fldCol = (fields.get(sett) as Map<*, *>)[columnField] as String
                listCSR
                    .first { it.csrName == fldRow }
                    .csrCells
                    .first { it.cscName == fldCol }
                    .songDTO = sett.toDTO()
            }

//            println(listCSR)

            return listCSR.sorted()
        }

        fun unpublications(
            listOfSongs: List<Song>,
            columnField: SongField = SongField.AUTHOR,
        ): List<CrossSongRow> {
            val skipedAuthors =
                Author
                    .loadList(
                        whereArgs = mapOf("skip" to "true"),
                        database = WORKING_DATABASE,
                        storageService = KSS_APP,
                        storageApiClient = SAC_APP,
                        ignoreUseInList = true,
                    ).map { it.author }
            val columns =
                listOfSongs
                    .filter { it.author !in skipedAuthors }
                    .map { sett ->
                        val fields = sett.javaClass.getDeclaredField("fields")
                        fields.isAccessible = true
                        (fields.get(sett) as Map<*, *>)[columnField] as String
                    }.distinct()
                    .sortedBy {
                        if (columnField == SongField.DATE) {
                            it.split(".").asReversed().joinToString("")
                        } else {
                            it
                        }
                    }

            val countRows =
                listOfSongs
                    .groupBy {
                        val fields = it.javaClass.getDeclaredField("fields")
                        fields.isAccessible = true
                        (fields.get(it) as Map<*, *>)[columnField] as String
                    }.map { it.value.size }
                    .max()

            val rows = (1..countRows).map { it.toString() }

            val listCSR =
                rows.mapIndexed { rowIndex, rowName ->
                    CrossSongRow(
                        csrId = rowIndex,
                        csrName = rowName,
                        csrCells =
                            columns.mapIndexed { columnIndex, columnName ->
                                CrossSongCell(cscIs = columnIndex, cscName = columnName)
                            },
                    )
                }

            columns.forEach { col ->
                listOfSongs
                    .filter { sett ->
                        val fields = sett.javaClass.getDeclaredField("fields")
                        fields.isAccessible = true
                        val fldCol = (fields.get(sett) as Map<*, *>)[columnField] as String
                        fldCol == col
                    }.forEachIndexed { index, song ->
                        listCSR
                            .first { it.csrId == index }
                            .csrCells
                            .first { it.cscName == col }
                            .songDTO = song.toDTO()
                    }
            }

//            println(listCSR)

            return listCSR.sorted()
        }

        fun skiped(
            listOfSongs: List<Song>,
            columnField: SongField = SongField.AUTHOR,
        ): List<CrossSongRow> {
            val skipedAuthors =
                Author
                    .loadList(
                        whereArgs = mapOf("skip" to "true"),
                        database = WORKING_DATABASE,
                        storageService = KSS_APP,
                        storageApiClient = SAC_APP,
                        ignoreUseInList = true,
                    ).map { it.author }
            val columns =
                listOfSongs
                    .filter { it.author in skipedAuthors }
                    .map { sett ->
                        val fields = sett.javaClass.getDeclaredField("fields")
                        fields.isAccessible = true
                        (fields.get(sett) as Map<*, *>)[columnField] as String
                    }.distinct()
                    .sortedBy {
                        if (columnField == SongField.DATE) {
                            it.split(".").asReversed().joinToString("")
                        } else {
                            it
                        }
                    }

            val countRows =
                listOfSongs
                    .groupBy {
                        val fields = it.javaClass.getDeclaredField("fields")
                        fields.isAccessible = true
                        (fields.get(it) as Map<*, *>)[columnField] as String
                    }.map { it.value.size }
                    .max()

            val rows = (1..countRows).map { it.toString() }

            val listCSR =
                rows.mapIndexed { rowIndex, rowName ->
                    CrossSongRow(
                        csrId = rowIndex,
                        csrName = rowName,
                        csrCells =
                            columns.mapIndexed { columnIndex, columnName ->
                                CrossSongCell(cscIs = columnIndex, cscName = columnName)
                            },
                    )
                }

            columns.forEach { col ->
                listOfSongs
                    .filter { sett ->
                        val fields = sett.javaClass.getDeclaredField("fields")
                        fields.isAccessible = true
                        val fldCol = (fields.get(sett) as Map<*, *>)[columnField] as String
                        fldCol == col
                    }.forEachIndexed { index, song ->
                        listCSR
                            .first { it.csrId == index }
                            .csrCells
                            .first { it.cscName == col }
                            .songDTO = song.toDTO()
                    }
            }

//            println(listCSR)

            return listCSR.sorted()
        }
    }
}
