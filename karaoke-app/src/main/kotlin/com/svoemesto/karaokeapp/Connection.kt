package com.svoemesto.karaokeapp

enum class Connection(
    val url: String,
    val username: String,
    val password: String
) {
    LOCAL(url = "jdbc:postgresql://localhost:5430/karaoke?currentSchema=public", username = "postgres", password = "postgres"),
    REMOTE(url = "jdbc:postgresql://localhost:2230/karaoke?currentSchema=public", username = "postgres", password = "postgres")
}