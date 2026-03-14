package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.services.APP_WORK_ON_SERVER
import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_USER
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_PASSWORD
import com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_USER
import com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_PASSWORD
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SearchLastAlbumVkTest {
    @Test
    fun searchLastAlbumVk() {
        APP_WORK_ON_SERVER = false
        APP_WORK_IN_CONTAINER = false
        DB_LOCAL_POSTGRES_USER = ""
        DB_LOCAL_POSTGRES_PASSWORD = ""
        DB_SERVER_POSTGRES_USER = ""
        DB_SERVER_POSTGRES_PASSWORD = ""

        val vkId = "piknik"
        val result = searchLastAlbumVk(vkId)
        println(result)
    }

}