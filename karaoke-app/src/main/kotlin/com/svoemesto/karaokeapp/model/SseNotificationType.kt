package com.svoemesto.karaokeapp.model

enum class SseNotificationType(val value: String) {
    RECORD_CHANGE("recordChange"),
    RECORD_ADD("recordAdd"),
    RECORD_DELETE("recordDelete"),
    PROCESS_WORKER_STATE("processWorkerState"),
    MESSAGE("message"),
    DUMMY("dummy"),
    LOG("log"),
    CRUD("crud")
}