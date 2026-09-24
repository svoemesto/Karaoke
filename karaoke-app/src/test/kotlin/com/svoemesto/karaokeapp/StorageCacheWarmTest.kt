package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.services.StorageFileInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Спека #449 (#182): тесты чистых частей полного прогрева кеша.
 *
 * `listToMap` — индекс листинга бакета по `fileName` (по нему warm решает
 * exists/etag/size без обращения к MinIO на каждый файл).
 */
class StorageCacheWarmTest {
    @Test
    fun `listToMap indexes by fileName`() {
        val list =
            listOf(
                StorageFileInfo("karaoke", "a/one.mp3", "\"e1\"", 111L),
                StorageFileInfo("karaoke", "b/two.mp3", "\"e2\"", 222L),
            )
        val map = listToMap(list)
        assertEquals(2, map.size)
        assertEquals(111L, map["a/one.mp3"]?.size)
        assertEquals("\"e2\"", map["b/two.mp3"]?.etag)
        assertNull(map["missing.mp3"], "absent file must be null → warm writes exists=false")
    }

    @Test
    fun `empty listing yields empty map`() {
        assertEquals(0, listToMap(emptyList()).size)
    }
}
