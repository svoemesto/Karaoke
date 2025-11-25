package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import kotlin.properties.Delegates

lateinit var WEBSOCKET: SimpMessagingTemplate
//lateinit var KSS_WEB: KaraokeStorageService
var WEB_WORK_IN_CONTAINER by Delegates.notNull<Boolean>()
var WEB_WORK_ON_SERVER by Delegates.notNull<Boolean>()
lateinit var DB_LOCAL_POSTGRES_USER: String
lateinit var DB_LOCAL_POSTGRES_PASSWORD: String
lateinit var DB_SERVER_POSTGRES_USER: String
lateinit var DB_SERVER_POSTGRES_PASSWORD: String

@Service
class KaraokeWebService(
    val webSocket: SimpMessagingTemplate,
    val karaokeStorageService: KaraokeStorageService,
    val storageApiClient: StorageApiClient,
    @Value($$"${work-in-container}") val wic: Long,
    @Value($$"${work-on-server}") val wos: Long,
    @Value($$"${db-local-postgres-user}") val dbLocalPostrgesUser: String,
    @Value($$"${db-local-postgres-password}") val dbLocalPostrgesPassword: String,
    @Value($$"${db-remote-postgres-user}") val dbRemotePostrgesUser: String,
    @Value($$"${db-remote-postgres-password}") val dbRemotePostrgesPassword: String,
) {

    init {
        WEB_WORK_IN_CONTAINER = (wic != 0L)
        WEB_WORK_ON_SERVER = (wos != 0L)
        DB_LOCAL_POSTGRES_USER = dbLocalPostrgesUser
        DB_LOCAL_POSTGRES_PASSWORD = dbLocalPostrgesPassword
        DB_SERVER_POSTGRES_USER = dbRemotePostrgesUser
        DB_SERVER_POSTGRES_PASSWORD = dbRemotePostrgesPassword
        WEBSOCKET = webSocket
//        KSS_WEB = karaokeStorageService
        karaokeStorageService.deleteAllEmptyBuckets()
        println("WEB_WORK_ON_SERVER = $WEB_WORK_ON_SERVER")
    }

}