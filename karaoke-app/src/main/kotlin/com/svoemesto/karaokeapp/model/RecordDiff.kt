package com.svoemesto.karaokeapp.model

data class RecordDiff(
    val recordDiffName: String,
    val recordDiffValueNew: Any?,
    val recordDiffValueOld: Any?,
    val recordDiffRealField: Boolean = true
)

data class RecordChangeMessage(
    val recordChangeId: Long,
    val recordChangeTableName: String,
    val recordChangeDiffs: List<RecordDiff>
) {
    override fun toString(): String {
        val stringBuilder = StringBuilder()
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