package com.svoemesto.karaokeapp.model


data class SseNotification(
    val type: SseNotificationType,
    val data: Any
) {
    companion object {
        fun recordChange(recordChangeMessage: RecordChangeMessage): SseNotification {
            return SseNotification(SseNotificationType.RECORD_CHANGE, recordChangeMessage)
        }
        fun recordAdd(recordAddMessage: RecordAddMessage): SseNotification {
            return SseNotification(SseNotificationType.RECORD_ADD, recordAddMessage)
        }
        fun recordDelete(recordDeleteMessage: RecordDeleteMessage): SseNotification {
            return SseNotification(SseNotificationType.RECORD_DELETE, recordDeleteMessage)
        }
        fun processWorkerState(processWorkerStateMessage: ProcessWorkerStateMessage): SseNotification {
            return SseNotification(SseNotificationType.PROCESS_WORKER_STATE, processWorkerStateMessage)
        }
        fun message(message: Message): SseNotification {
            return SseNotification(SseNotificationType.MESSAGE, message)
        }
        fun dummy(): SseNotification {
            return SseNotification(SseNotificationType.DUMMY, "dummy")
        }
        fun log(text: String): SseNotification {
            return SseNotification(SseNotificationType.LOG, text)
        }
        fun crud(crudMessage: List<List<String>>): SseNotification {
            return SseNotification(SseNotificationType.CRUD, crudMessage)
        }
    }
}