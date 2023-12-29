package com.svoemesto.karaokeweb

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.services.WEB_WORK_IN_CONTAINER
import java.sql.DriverManager

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

        private val USERNAME = "postgres"
        private val PASSWORD = "postgres"
        fun local(): KaraokeConnection {
            return Connection(name = "LOCAL", url = connectionLocalUrl(), username = USERNAME, password = PASSWORD)
        }
        fun remote(): KaraokeConnection {
            return Connection(name = "SERVER", url = connectionRemoteUrl(), username = USERNAME, password = PASSWORD)
        }

        fun virtual(): KaraokeConnection {
            return Connection(name = "VIRTUAL", url = connectionVirtualUrl(), username = USERNAME, password = PASSWORD)
        }

        private fun connectionLocalUrl(): String {

            return if (WEB_WORK_IN_CONTAINER) {
                "jdbc:postgresql://karaoke-db:5432/karaoke?currentSchema=public"
            } else {
                "jdbc:postgresql://localhost:8832/karaoke?currentSchema=public"
            }

        }

        private fun connectionRemoteUrl(): String {
            return "jdbc:postgresql://79.174.95.69:8832/karaoke?currentSchema=public"
        }

        private fun connectionVirtualUrl(): String {
            return "jdbc:postgresql://localhost:2230/karaoke?currentSchema=public"
        }

    }
}

