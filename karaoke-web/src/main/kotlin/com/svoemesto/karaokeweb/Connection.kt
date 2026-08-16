package com.svoemesto.karaokeweb

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeweb.services.DB_LOCAL_POSTGRES_PASSWORD
import com.svoemesto.karaokeweb.services.DB_LOCAL_POSTGRES_USER
import com.svoemesto.karaokeweb.services.DB_REMOTE_HOST
import com.svoemesto.karaokeweb.services.DB_REMOTE_PORT
import com.svoemesto.karaokeweb.services.DB_SERVER_POSTGRES_PASSWORD
import com.svoemesto.karaokeweb.services.DB_SERVER_POSTGRES_USER
import com.svoemesto.karaokeweb.services.WEB_WORK_IN_CONTAINER
import com.svoemesto.karaokeweb.services.WEB_WORK_ON_SERVER

/**
 * Класс Connection.
 *
 * **Singleton-семантика фабрик (specs/234-db-sync-connection-leak):** начиная с этой спеки,
 * `local()`/`remote()`/`virtual()` возвращают **тот же самый инстанс** `Connection` на повторные
 * вызовы (Kotlin `by lazy(SYNCHRONIZED)`), а не `new Connection(...)` каждый раз — симметричный
 * фикс `karaoke-app/.../Connection.kt`. Решает ту же утечку JDBC-соединений в `webvue3`-эндпоинтах
 * (новости, шаблоны, словари через `withDb { ... }`).
 *
 * @see archive/docs/features/dual-db-sync.md
 * @see specs/234-db-sync-connection-leak фикс утечки JDBC-соединений
 */
class Connection(
    override val url: String,
    override val username: String,
    override val password: String,
    override val name: String,
) : KaraokeConnection(url, username, password, name) {
    //    private var connection: java.sql.Connection? = null
//    override fun getConnection(): java.sql.Connection {
//        if (connection == null) {
//            Class.forName("org.postgresql.Driver")
//            connection = DriverManager.getConnection(url, username, password)
//        }
//        return connection!!
//    }

    companion object {
        private val USERNAME = if (WEB_WORK_ON_SERVER) DB_SERVER_POSTGRES_USER else DB_LOCAL_POSTGRES_USER
        private val PASSWORD = if (WEB_WORK_ON_SERVER) DB_SERVER_POSTGRES_PASSWORD else DB_LOCAL_POSTGRES_PASSWORD

        // Singleton-фабрики (specs/234-db-sync-connection-leak) — симметрично karaoke-app/.../Connection.kt.
        // Один инстанс Connection на процесс karaoke-web, ленивая инициализация, thread-safe.
        private val LOCAL_INSTANCE: Connection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Connection(name = "LOCAL", url = connectionLocalUrl(), username = USERNAME, password = PASSWORD)
        }

        @Suppress("unused")
        private val REMOTE_INSTANCE: Connection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Connection(
                name = "SERVER",
                url = connectionRemoteUrl(),
                username = DB_SERVER_POSTGRES_USER,
                password = DB_SERVER_POSTGRES_PASSWORD,
            )
        }

        @Suppress("unused")
        private val VIRTUAL_INSTANCE: Connection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Connection(name = "VIRTUAL", url = connectionVirtualUrl(), username = USERNAME, password = PASSWORD)
        }

        /** Singleton-фабрика (specs/234-db-sync-connection-leak) — возвращает тот же инстанс на повторные вызовы. */
        fun local(): KaraokeConnection = LOCAL_INSTANCE

        @Suppress("unused")
        fun remote(): KaraokeConnection = REMOTE_INSTANCE

        @Suppress("unused")
        fun virtual(): KaraokeConnection = VIRTUAL_INSTANCE

        private fun connectionLocalUrl(): String =
            if (WEB_WORK_IN_CONTAINER) {
                "jdbc:postgresql://karaoke-db:5432/karaoke?currentSchema=public"
            } else {
                "jdbc:postgresql://localhost:8832/karaoke?currentSchema=public"
            }

        private fun connectionRemoteUrl(): String = "jdbc:postgresql://$DB_REMOTE_HOST:$DB_REMOTE_PORT/karaoke?currentSchema=public"

        private fun connectionVirtualUrl(): String = "jdbc:postgresql://localhost:2230/karaoke?currentSchema=public"
    }
}
