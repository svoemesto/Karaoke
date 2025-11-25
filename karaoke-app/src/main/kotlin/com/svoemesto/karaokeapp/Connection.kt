package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import com.svoemesto.karaokeapp.services.APP_WORK_ON_SERVER
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_PASSWORD
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_USER
import com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_PASSWORD
import com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_USER

class Connection(
    override val url: String,
    override val username: String,
    override val password: String,
    override val name: String
): KaraokeConnection(url, username, password, name) {

//    private var connection: java.sql.Connection? = null
//    override fun getConnection(): java.sql.Connection {
//        if (connection == null) {
//            Class.forName("org.postgresql.Driver")
//            connection = DriverManager.getConnection(url, username, password)
//        }
//        return connection!!
//    }
    companion object {

    private val USERNAME = if(APP_WORK_ON_SERVER) DB_SERVER_POSTGRES_USER else DB_LOCAL_POSTGRES_USER
    private val PASSWORD = if(APP_WORK_ON_SERVER) DB_SERVER_POSTGRES_PASSWORD else DB_LOCAL_POSTGRES_PASSWORD
        fun local(): KaraokeConnection {
            return Connection(name = "LOCAL", url = connectionLocalUrl(), username = USERNAME, password = PASSWORD)
        }
        fun remote(): KaraokeConnection {
            return Connection(name = "SERVER", url = connectionRemoteUrl(), username = DB_SERVER_POSTGRES_USER, password = DB_SERVER_POSTGRES_PASSWORD)
        }

        @Suppress("unused")
        fun virtual(): KaraokeConnection {
            return Connection(name = "VIRTUAL", url = connectionVirtualUrl(), username = USERNAME, password = PASSWORD)
        }

        private fun connectionLocalUrl(): String {

            return if (APP_WORK_IN_CONTAINER) {
                "jdbc:postgresql://karaoke-db:5432/karaoke?currentSchema=public" //&socketTimeout=30&loginTimeout=10"
            } else {
                "jdbc:postgresql://localhost:8832/karaoke?currentSchema=public" //&socketTimeout=30&loginTimeout=10"
            }

        }

        private fun connectionRemoteUrl(): String {
            return "jdbc:postgresql://79.174.95.69:8832/karaoke?currentSchema=public" //&socketTimeout=30&loginTimeout=10"
        }

        private fun connectionVirtualUrl(): String {
            return "jdbc:postgresql://localhost:2230/karaoke?currentSchema=public" //&socketTimeout=30&loginTimeout=10"
        }

    }

}

