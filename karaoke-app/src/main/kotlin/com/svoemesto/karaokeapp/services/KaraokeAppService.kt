package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.Crypto
import com.svoemesto.karaokeapp.DualStream
import com.svoemesto.karaokeapp.propertiesfiledictionary.PropertiesFileDictionary
import com.svoemesto.karaokeapp.propertiesfiledictionary.WebvueProperties
import org.slf4j.LoggerFactory
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
lateinit var DB_REMOTE_HOST: String
lateinit var DB_REMOTE_PORT: String

/**
 * Режим TLS для подключения к удалённой (SERVER) БД — env `DB_REMOTE_SSL_MODE`.
 *
 * BR-545 / BR-557 (аудит #336, OpenProject #371): L4-`stream`-прокси прод-хоста
 * (порт 5433) по умолчанию несёт трафик БЕЗ TLS, а драйвер PostgreSQL при
 * `sslmode=prefer` молча деградирует в открытый текст. Значение по умолчанию
 * здесь — `prefer` (поведение как было); целевые состояния:
 *   * `require`      — TLS есть, сертификат не проверяется;
 *   * `verify-full`  — TLS + проверка сертификата и имени хоста.
 * Включать только после того, как на сервере БД включён TLS
 * (`deploy/prod-single-host/docker-compose-database.yml`, DB_SSL_ON).
 */
lateinit var DB_REMOTE_SSL_MODE: String

// @Component

/** BR-291: логгер проверки конфигурации крипты при старте приложения. */
private val cryptoLog = LoggerFactory.getLogger("infra.crypto.config")

/**
 * Сервис для karaoke app .
 *
 * @see archive/docs/features/async-process-queue.md
 */
@Service
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
    @Value($$"${db-remote-host}") val dbRemoteHost: String,
    @Value($$"${db-remote-port}") val dbRemotePort: String,
    @Value($$"${db-remote-ssl-mode:prefer}") val dbRemoteSslMode: String,
) {
    init {
        APP_WORK_IN_CONTAINER = (wic != 0L)
        APP_WORK_ON_SERVER = (wos != 0L)
        DB_LOCAL_POSTGRES_USER = dbLocalPostgresUser
        DB_LOCAL_POSTGRES_PASSWORD = dbLocalPostgresPassword
        DB_SERVER_POSTGRES_USER = dbRemotePostgresUser
        DB_SERVER_POSTGRES_PASSWORD = dbRemotePostgresPassword
        DB_REMOTE_HOST = dbRemoteHost
        DB_REMOTE_PORT = dbRemotePort
        DB_REMOTE_SSL_MODE = dbRemoteSslMode
        SNS = sseNotificationService
        KSS_APP = karaokeStorageService
        SAC_APP = storageApiClient
        WVP = WebvueProperties()
        System.setOut(DualStream(System.out))
        System.setErr(DualStream(System.err))
        // BR-291 (аудит #336, пакет PKG-S3-26): конфигурация крипты проверяется на старте.
        // Неверная длина ключа/IV — ошибка конфигурации, а не «не удалось зашифровать»:
        // логируем явно (ERROR), а сами операции шифрования бросают
        // CryptoConfigurationException (fail-closed: пустая строка вместо шифротекста
        // в удалённую БД больше не уходит). Полный отказ приложения на старте —
        // за владельцем (см. specs/398-pkg-s3-26/report.md).
        try {
            Crypto.validateCurrentKeyMaterial()
        } catch (e: Exception) {
            cryptoLog.error(
                "Конфигурация крипты некорректна: {} — операции шифрования будут отклонены",
                e.message,
                e,
            )
        }
        cleanupEmptyBucketsOnStartup(KSS_APP)
    }
}

/**
 * Уборка пустых бакетов при старте — необязательная операция (BR-162, аудит #336).
 *
 * Раньше `deleteAllEmptyBuckets()` вызывался из `init` без `try/catch`: сетевой
 * сбой MinIO (`ConnectException`/`SocketTimeoutException` — это `IOException`, не
 * `MinioException`, который внутри ловится) валил создание бина и весь Spring-контекст,
 * то есть `karaoke-app` не стартовал вовсе. Теперь сбой уборки логируется WARN и не
 * мешает старту; сама уборка ограничена бакетами Karaoke (см. `deleteAllEmptyBuckets`).
 */
internal fun cleanupEmptyBucketsOnStartup(
    storage: KaraokeStorageService,
    warn: (String, Throwable) -> Unit = { message, error -> println("WARN $message: ${error.message}") },
) {
    runCatching { storage.deleteAllEmptyBuckets() }
        .onFailure { warn("startup: не удалось убрать пустые бакеты (продолжаем запуск)", it) }
}
