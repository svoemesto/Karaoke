package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.services.KaraokeStorageService
import org.springframework.beans.factory.annotation.Value
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import kotlin.properties.Delegates

lateinit var WEBSOCKET: SimpMessagingTemplate
//lateinit var KSS_WEB: KaraokeStorageService
var WEB_WORK_IN_CONTAINER by Delegates.notNull<Boolean>()

@Service
class KaraokeWebService(
    val webSocket: SimpMessagingTemplate,
    val karaokeStorageService: KaraokeStorageService,
    @Value($$"${work-in-container}") val wic: Long
) {

    init {
        WEB_WORK_IN_CONTAINER = (wic != 0L)
        WEBSOCKET = webSocket
//        KSS_WEB = karaokeStorageService
        karaokeStorageService.deleteAllEmptyBuckets()
        println("karaokeStorageService initialize in KaraokeWebService init")
    }

}