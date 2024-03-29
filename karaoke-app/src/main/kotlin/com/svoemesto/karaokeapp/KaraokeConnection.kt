package com.svoemesto.karaokeapp

import java.sql.DriverManager

abstract class KaraokeConnection(
    open val url: String,
    open val username: String,
    open val password: String,
    open val name: String
) {
    private var connection: java.sql.Connection? = null
    fun getConnection(): java.sql.Connection {
        if (connection == null) {
            Class.forName("org.postgresql.Driver")
            connection = DriverManager.getConnection(url, username, password)
        }
        return connection!!
    }
//    abstract fun getConnection(): java.sql.Connection
}