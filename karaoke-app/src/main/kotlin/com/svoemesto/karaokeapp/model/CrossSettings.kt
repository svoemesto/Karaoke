package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import java.io.Serializable

data class CrossSettingsRow (
    val csrId: Int,
    val csrName: String,
    val csrCells: List<CrossSettingsCell>
): Serializable, Comparable<CrossSettingsRow> {
    override fun compareTo(other: CrossSettingsRow): Int {
        return csrId.compareTo(other.csrId)
    }
}

data class CrossSettingsCell (
    val cscIs: Int,
    val cscName: String,
    var settingsDTO: SettingsDTO? = null
): Serializable, Comparable<CrossSettingsCell> {
    override fun compareTo(other: CrossSettingsCell): Int {
        return cscIs.compareTo(other.cscIs)
    }
}

//fun main() {
//    APP_WORK_IN_CONTAINER = false
//    val listOfSettings = Settings.loadListFromDb(mapOf(Pair("song_author", "Ундервуд")), WORKING_DATABASE)
//    CrossSettings.publications(
//        listOfSettings = listOfSettings
//    )
//
//    CrossSettings.unpublications(
//        listOfSettings = listOfSettings
//    )
//
//}
class CrossSettings {
    companion object {

        fun publications(
            listOfSettings: List<Settings>,
            rowField: SettingField = SettingField.DATE,
            columnField: SettingField = SettingField.TIME
        ): List<CrossSettingsRow> {

            val columns = listOfSettings.map { sett ->
                val fields = sett.javaClass.getDeclaredField("fields")
                fields.isAccessible = true
                (fields.get(sett) as Map<*, *>)[columnField] as String
            }.distinct()
            .sortedBy {
                if (columnField == SettingField.DATE) {
                    it.split(".").reversed().joinToString("")
                } else {
                    it
                }
            }

            val rows = listOfSettings.map { sett ->
                val fields = sett.javaClass.getDeclaredField("fields")
                fields.isAccessible = true
                (fields.get(sett) as Map<*, *>)[rowField] as String
            }.distinct()
//                .sortedBy {
//                if (rowField == SettingField.DATE) {
//                    it.split(".").reversed().joinToString("")
//                } else {
//                    it
//                }
//            }

            val listCSR = rows.mapIndexed { rowIndex, rowName ->
                CrossSettingsRow(
                    csrId = rowIndex,
                    csrName = rowName,
                    csrCells = columns.mapIndexed { columnIndex, columnName ->
                        CrossSettingsCell(cscIs = columnIndex, cscName = columnName)
                    }
                )
            }

            listOfSettings.forEach { sett ->
                val fields = sett.javaClass.getDeclaredField("fields")
                fields.isAccessible = true
                val fldRow = (fields.get(sett) as Map<*, *>)[rowField] as String
                val fldCol = (fields.get(sett) as Map<*, *>)[columnField] as String
                listCSR.first { it.csrName == fldRow }.csrCells.first { it.cscName == fldCol }.settingsDTO = sett.toDTO()
            }

            println(listCSR)

            return listCSR
        }


        fun unpublications(
            listOfSettings: List<Settings>,
            columnField: SettingField = SettingField.AUTHOR
        ): List<CrossSettingsRow> {

            val columns = listOfSettings.map { sett ->
                val fields = sett.javaClass.getDeclaredField("fields")
                fields.isAccessible = true
                (fields.get(sett) as Map<*, *>)[columnField] as String
            }.distinct().sortedBy {
                if (columnField == SettingField.DATE) {
                    it.split(".").reversed().joinToString("")
                } else {
                    it
                }
            }

            val countRows = listOfSettings.groupBy {
                val fields = it.javaClass.getDeclaredField("fields")
                fields.isAccessible = true
                (fields.get(it) as Map<*, *>)[columnField] as String
            }.map {it.value.size}.max()

            val rows = (1 .. countRows).map { it.toString() }

            val listCSR = rows.mapIndexed { rowIndex, rowName ->
                CrossSettingsRow(
                    csrId = rowIndex,
                    csrName = rowName,
                    csrCells = columns.mapIndexed { columnIndex, columnName ->
                        CrossSettingsCell(cscIs = columnIndex, cscName = columnName)
                    }
                )
            }

            columns.forEach { col ->
                listOfSettings.filter { sett ->
                    val fields = sett.javaClass.getDeclaredField("fields")
                    fields.isAccessible = true
                    val fldCol = (fields.get(sett) as Map<*, *>)[columnField] as String
                    fldCol == col
                }.forEachIndexed { index, settings ->
                    listCSR.first { it.csrId == index }.csrCells.first { it.cscName == col }.settingsDTO = settings.toDTO()
                }

            }

            println(listCSR)

            return listCSR
        }
    }
}