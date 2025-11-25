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
var APP_WORK_ON_SERVER by Delegates.notNull<Boolean>()
lateinit var DB_LOCAL_POSTGRES_USER: String
lateinit var DB_LOCAL_POSTGRES_PASSWORD: String
lateinit var DB_SERVER_POSTGRES_USER: String
lateinit var DB_SERVER_POSTGRES_PASSWORD: String

@Service
//@Component
class KaraokeAppService(
    sseNotificationService: SseNotificationService,
    karaokeStorageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
    @Value($$"${work-in-container}") val wic: Long,
    @Value($$"${work-on-server}") val wos: Long,
    @Value($$"${db-local-postgres-user}") val dbLocalPostgresUser: String,
    @Value($$"${db-local-postgres-password}") val dbLocalPostgresPassword: String,
    @Value($$"${db-remote-postgres-user}") val dbRemotePostgresUser: String,
    @Value($$"${db-remote-postgres-password}") val dbRemotePostgresPassword: String,
) {

    init {
        APP_WORK_IN_CONTAINER = (wic != 0L)
        APP_WORK_ON_SERVER = (wos != 0L)
        DB_LOCAL_POSTGRES_USER = dbLocalPostgresUser
        DB_LOCAL_POSTGRES_PASSWORD = dbLocalPostgresPassword
        DB_SERVER_POSTGRES_USER = dbRemotePostgresUser
        DB_SERVER_POSTGRES_PASSWORD = dbRemotePostgresPassword
        SNS = sseNotificationService
        KSS_APP = karaokeStorageService
        SAC_APP = storageApiClient
        WVP = WebvueProperties()
        System.setOut(DualStream(System.out))
        System.setErr(DualStream(System.err))
        KSS_APP.deleteAllEmptyBuckets()
    }

}