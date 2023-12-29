package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection

data class RecordDiff(
    val recordDiffName: String,
    val recordDiffValueNew: Any?,
    val recordDiffValueOld: Any?,
    val recordDiffRealField: Boolean = true
)

data class RecordChangeMessage(
    val recordChangeId: Long,
    val recordChangeTableName: String,
    val recordChangeDiffs: List<RecordDiff>,
    val databaseName: String
) {

    fun getSetString(): String {
        val result: MutableList<String> = mutableListOf()
        recordChangeDiffs.filter{it.recordDiffRealField}.forEach { recordDiff ->
            val txt = StringBuilder()
            txt.append("${recordDiff.recordDiffName} = ")
            if (recordDiff.recordDiffValueNew is Long) {
                txt.append("${recordDiff.recordDiffValueNew}")
            } else {
                txt.append("'${recordDiff.recordDiffValueNew.toString().replace("'","''")}'")
            }
            result.add(txt.toString())
        }
        return result.joinToString(", ")
    }

    override fun toString(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("База данных: ${databaseName}\n")
        stringBuilder.append("Таблица: $recordChangeTableName\n")
        stringBuilder.append("ID: $recordChangeId\n")
        recordChangeDiffs.forEach { diff ->
            stringBuilder.append("Поле: ${diff.recordDiffName}\n")
            stringBuilder.append("Было: ${diff.recordDiffValueOld}\n")
            stringBuilder.append("Стало: ${diff.recordDiffValueNew}\n")
        }
        stringBuilder.append("--------------------------------\n")
        return stringBuilder.toString()
    }
}