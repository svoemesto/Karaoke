package com.svoemesto.karaokeapp

import java.sql.DriverManager

abstract class KaraokeConnection(
    open val url: String,
    open val username: String,
    open val password: String,
    open val name: String,
) {
    @Volatile private var connection: java.sql.Connection? = null

    @Synchronized
    fun getConnection(): java.sql.Connection? {
        val conn = connection
        if (conn == null || conn.isClosed || !conn.isValid(3)) {
            Class.forName("org.postgresql.Driver")
            try {
                connection = DriverManager.getConnection(url, username, password)
            } catch (e: Exception) {
                println("KaraokeConnection getConnection Exception: ${e.message}")
            }
        }
        return connection
    }
}
