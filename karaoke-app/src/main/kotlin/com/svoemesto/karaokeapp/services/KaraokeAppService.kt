package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.controllers.SseNotificationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import kotlin.properties.Delegates

lateinit var SNS: SseNotificationService
var APP_WORK_IN_CONTAINER by Delegates.notNull<Boolean>()

@Service
//@Component
class KaraokeAppService(
    sseNotificationService: SseNotificationService,
    @Value("\${work-in-container}") val wic: Long
) {

    init {
        APP_WORK_IN_CONTAINER = (wic != 0L)
        SNS = sseNotificationService
    }

}