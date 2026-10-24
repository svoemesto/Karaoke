package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import com.svoemesto.karaokeapp.services.APP_WORK_ON_SERVER

/**
 * Инициализация глобалов `karaoke-web` для юнит-тестов.
 *
 * `WORKING_DATABASE` (= `Connection.local()`) инициализируется лениво и читает эти значения при
 * первом обращении; без них — `IllegalStateException: Property WEB_WORK_ON_SERVER should be
 * initialized before get`. В тестах доступа достаточно, чтобы объект соединения СОЗДАЛСЯ: само
 * подключение к БД ленивое (`KaraokeConnection.getConnection()`), поэтому тесты БД не касаются.
 */
object KaraokeWebTestEnv {
    /** Идемпотентно проставляет значения глобалов. */
    fun init() {
        WEB_WORK_ON_SERVER = false
        WEB_WORK_IN_CONTAINER = false
        DB_LOCAL_POSTGRES_USER = "test"
        DB_LOCAL_POSTGRES_PASSWORD = "test"
        DB_SERVER_POSTGRES_USER = "test"
        DB_SERVER_POSTGRES_PASSWORD = "test"
        DB_REMOTE_HOST = "localhost"
        DB_REMOTE_PORT = "5432"
        APP_WORK_ON_SERVER = false
        APP_WORK_IN_CONTAINER = false
    }
}
