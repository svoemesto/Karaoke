package com.svoemesto.karaokeweb.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.services.SNS
import com.svoemesto.karaokeapp.services.SseNotificationService
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
    val objectMapper: ObjectMapper,
    @Value($$"${work-in-container}") val wic: Long,
    @Value($$"${work-on-server}") val wos: Long,
    @Value($$"${db-local-postgres-user}") val dbLocalPostgresUser: String,
    @Value($$"${db-local-postgres-password}") val dbLocalPostgresPassword: String,
    @Value($$"${db-remote-postgres-user}") val dbRemotePostgresUser: String,
    @Value($$"${db-remote-postgres-password}") val dbRemotePostgresPassword: String,
) {

    init {
        WEB_WORK_IN_CONTAINER = (wic != 0L)
        WEB_WORK_ON_SERVER = (wos != 0L)
        DB_LOCAL_POSTGRES_USER = dbLocalPostgresUser
        DB_LOCAL_POSTGRES_PASSWORD = dbLocalPostgresPassword
        DB_SERVER_POSTGRES_USER = dbRemotePostgresUser
        DB_SERVER_POSTGRES_PASSWORD = dbRemotePostgresPassword
        WEBSOCKET = webSocket
//        KSS_WEB = karaokeStorageService
        // karaoke-web не сканирует com.svoemesto.karaokeapp.services (нет @ComponentScan туда), поэтому
        // SNS (lateinit, обычно инициализируется в KaraokeAppService.init{}) здесь никогда бы не был выставлен.
        // KaraokeDbTable.createDbInstance() вызывает SNS.send(...) без try/catch — без этой инициализации
        // первый же INSERT через переиспользованные модели karaoke-app (например SiteUser при регистрации)
        // уронил бы процесс karaoke-web с UninitializedPropertyAccessException. У karaoke-web нет /subscribe
        // эндпоинта, поэтому emitters всегда пуст и send() безопасно не делает ничего.
        SNS = SseNotificationService(objectMapper)
        // Уборка пустых бакетов (deleteAllEmptyBuckets) намеренно НЕ вызывается: karaoke-web не обращается
        // к MinIO напрямую (см. DEVELOPMENT.md, MTU black-hole; бин KaraokeStorageService здесь — заглушка,
        // KaraokeStorageServiceImplWeb.kt). Прежний вызов ходил к недоступному karaoke-storage:9000 и ронял
        // старт в crash-loop. Это забота admin/karaoke-app.
        println("WEB_WORK_ON_SERVER = $WEB_WORK_ON_SERVER")
    }

}