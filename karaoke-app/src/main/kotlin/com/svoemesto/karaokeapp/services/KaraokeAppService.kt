package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.DualStream
import com.svoemesto.karaokeapp.propertiesfiledictionary.PropertiesFileDictionary
import com.svoemesto.karaokeapp.propertiesfiledictionary.WebvueProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.properties.Delegates


lateinit var SNS: SseNotificationService
lateinit var WVP: PropertiesFileDictionary
lateinit var KSS_APP: KaraokeStorageService
lateinit var SAC_APP: StorageApiClient
var APP_WORK_IN_CONTAINER by Delegates.notNull<Boolean>()

@Service
//@Component
class KaraokeAppService(
    sseNotificationService: SseNotificationService,
    karaokeStorageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
    @Value($$"${work-in-container}") val wic: Long
) {

    init {
        APP_WORK_IN_CONTAINER = (wic != 0L)
        SNS = sseNotificationService
        KSS_APP = karaokeStorageService
        SAC_APP = storageApiClient
        WVP = WebvueProperties()
        System.setOut(DualStream(System.out))
        System.setErr(DualStream(System.err))
        KSS_APP.deleteAllEmptyBuckets()
    }

}