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
 * Класс Cache Filler Metrics Message (Pass 92/98).
 *
 * Метрики [com.svoemesto.karaokeapp.services.StorageMetadataCache.CacheFillerMetrics],
 * отправляемые через SSE (`SseNotificationType.CACHE_FILLER_METRICS`).
 *
 * Используется в webvue3 `CacheQueueBadge.vue` для отображения бейджа счётчика
 * количества задач в пуле проверки кеша обращения к хранилищу.
 *
 * @see research/92-backend-queue-size/REPORT.md
 * @see specs/_wayfinder-92-hrqueue-priority/97-grilling-resolution.md
 */
data class CacheFillerMetricsMessage(
    val corePoolSize: Int,
    val maximumPoolSize: Int,
    val activeCount: Int,
    val poolSize: Int,
    val queueSize: Int,
    val completedTaskCount: Long,
    val pendingTotal: Int,
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
