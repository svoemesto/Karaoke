package com.svoemesto.karaokeapp.model

/**
 * Класс Message.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
data class Message(
    val type: String = "info",
    val head: String = "",
    val body: String = "",
) {
    companion object {
        @Suppress("unused")
        fun getRecordChangeMessage(
            type: String = "info",
            head: String = "",
            body: String = "",
        ): RecordChangeMessage {
            val message = Message(type = type, head = head, body = body)
            return RecordChangeMessage(
                recordId = 0,
                tableName = "message",
                diffs = emptyList(),
                databaseName = "",
                record = message,
            )
        }
    }
}

/**
 * Класс Process Worker State Message.
 *
 * @see archive/docs/features/async-process-queue.md
 */
data class ProcessWorkerStateMessage(
    val isWork: Boolean,
    val stopAfterThreadIsDone: Boolean,
)

/**
 * Класс Process Count Waiting Message.
 *
 * @see archive/docs/features/async-process-queue.md
 */
data class ProcessCountWaitingMessage(
    val countWaiting: Long,
)

/**
 * Класс Health Report Pool Count Message.
 *
 * SSE-payload для [com.svoemesto.karaokeapp.model.SseNotificationType.HEALTH_REPORT_POOL_COUNT]
 * (OpenProject #129, specs/129-hrpool-badge). Размер приоритетной очереди
 * `HealthReportBatchPool` — рассылается при каждом изменении (с подавлением
 * дублей через `lastSentQueueSize` в pool).
 *
 * Фронт: `webvue3/src/components/Common/ProcessWorker.vue` рисует зелёный
 * бейдж с этим числом в хедере у кнопки Старт/Стоп.
 */
data class HealthReportPoolCountMessage(
    val count: Long,
)

/**
 * Класс Health Report Waiting Count Message.
 *
 * SSE-payload для [com.svoemesto.karaokeapp.model.SseNotificationType.HEALTH_REPORT_WAITING_COUNT]
 * (OpenProject #130, specs/130-hrwaiting-badge). Сумма количества WAITING-записей
 * по всем песням, для которых получен HR (на момент recomputeAndBroadcast).
 *
 * Фронт: `webvue3/src/components/Common/ProcessWorker.vue` рисует голубой
 * бейдж с этим числом в правом верхнем углу кнопки Старт/Стоп.
 */
data class HealthReportWaitingCountMessage(
    val count: Long,
)

/**
 * Класс Record Delete Message.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
data class RecordDeleteMessage(
    val recordId: Long,
    val tableName: String,
    val databaseName: String,
)

/**
 * Класс Record Add Message.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
data class RecordAddMessage(
    val recordId: Long,
    val tableName: String,
    val databaseName: String,
    val record: Any?,
)

/**
 * Класс Record Change Message.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
data class RecordChangeMessage(
    val recordId: Long,
    val tableName: String,
    val diffs: List<RecordDiff>,
    val databaseName: String,
    val record: Any?,
) {
    fun getSetString(): String {
        val result: MutableList<String> = mutableListOf()
        diffs.filter { it.recordDiffRealField }.forEach { recordDiff ->
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
