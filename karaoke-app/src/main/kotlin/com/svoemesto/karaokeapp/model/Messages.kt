package com.svoemesto.karaokeapp.model

data class Message(
    val type: String = "info",
    val head: String = "",
    val body: String = ""
) {
    companion object {
        @Suppress("unused")
        fun getRecordChangeMessage(type: String = "info", head: String = "", body: String = ""): RecordChangeMessage {
            val message = Message(type = type, head = head, body = body)
            return RecordChangeMessage(
                recordId = 0,
                tableName = "message",
                diffs = emptyList(),
                databaseName = "",
                record = message
            )
        }
    }
}

data class ProcessWorkerStateMessage(
    val isWork: Boolean,
    val stopAfterThreadIsDone: Boolean
)

data class ProcessCountWaitingMessage(
        val countWaiting: Long
)

data class RecordDeleteMessage(
    val recordId: Long,
    val tableName: String,
    val databaseName: String
)

data class RecordAddMessage(
    val recordId: Long,
    val tableName: String,
    val databaseName: String,
    val record: Any?
)

data class RecordChangeMessage(
    val recordId: Long,
    val tableName: String,
    val diffs: List<RecordDiff>,
    val databaseName: String,
    val record: Any?
) {

    fun getSetString(): String {
        val result: MutableList<String> = mutableListOf()
        diffs.filter{it.recordDiffRealField}.forEach { recordDiff ->
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
        stringBuilder.append("Таблица: $tableName\n")
        stringBuilder.append("ID: $recordId\n")
        diffs.forEach { diff ->
            stringBuilder.append("Поле: ${diff.recordDiffName}\n")
            stringBuilder.append("Было: ${diff.recordDiffValueOld}\n")
            stringBuilder.append("Стало: ${diff.recordDiffValueNew}\n")
        }
        stringBuilder.append("--------------------------------\n")
        return stringBuilder.toString()
    }
}