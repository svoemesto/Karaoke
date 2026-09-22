package com.svoemesto.karaokeapp.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pass 434 (#158): тесты построения [StorageFileInfo] из строки кеша.
 *
 * Критичный кейс — строка, созданная через `fileExists` (size/etag = NULL): она
 * НЕ должна давать `StorageFileInfo` с `size=0` (иначе ложное «файл неактуальный»).
 */
class StorageMetadataCacheFileInfoTest {
    @Test
    fun `builds info for complete row`() {
        val info = buildFileInfoFromRow("karaoke", "a.mp3", exists = true, etag = "\"abc\"", size = 1234L, sizeIsNull = false)
        assertEquals("karaoke", info?.bucketName)
        assertEquals("a.mp3", info?.fileName)
        assertEquals("\"abc\"", info?.etag)
        assertEquals(1234L, info?.size)
    }

    @Test
    fun `returns null when size is null (row created by fileExists)`() {
        val info = buildFileInfoFromRow("karaoke", "a.mp3", exists = true, etag = "", size = 0L, sizeIsNull = true)
        assertNull(info, "row without size must be a cache miss, not size=0")
    }

    @Test
    fun `returns null when exists is false`() {
        val info = buildFileInfoFromRow("karaoke", "a.mp3", exists = false, etag = "\"abc\"", size = 1234L, sizeIsNull = false)
        assertNull(info)
    }

    @Test
    fun `accepts zero-size real file when size is present`() {
        // Реальный пустой файл: size=0, но sizeIsNull=false — это валидный info.
        val info = buildFileInfoFromRow("karaoke", "empty.mp3", exists = true, etag = "\"e\"", size = 0L, sizeIsNull = false)
        assertEquals(0L, info?.size)
    }
}
