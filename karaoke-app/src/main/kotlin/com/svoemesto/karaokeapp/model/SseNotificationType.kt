package com.svoemesto.karaokeapp.model

enum class SseNotificationType(val value: String) {
    RECORD_CHANGE("recordChange"),
    RECORD_ADD("recordAdd"),
    RECORD_DELETE("recordDelete"),
    PROCESS_WORKER_STATE("processWorkerState"),
    PROCESS_COUNT_WAITING("processCountWaiting"),
    MESSAGE("message"),
    DUMMY("dummy"),
    LOG("log"),
    CRUD("crud")
}