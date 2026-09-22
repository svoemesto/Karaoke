package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.services.StorageFileInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pass 435 (#159): тесты чистого решения backfill etag/size.
 *
 * `null` (info неизвестен — circuit open/ошибка) → SKIP;
 * `size < 0` (файл не найден) → MARK_MISSING;
 * иначе → UPDATE.
 */
class CacheEtagSizeBackfillTest {
    private fun info(size: Long) = StorageFileInfo("karaoke", "a.mp3", "\"etag\"", size)

    @Test
    fun `null info is skipped`() {
        assertEquals(BackfillAction.SKIP, decideBackfillAction(null))
    }

    @Test
    fun `negative size marks missing`() {
        assertEquals(BackfillAction.MARK_MISSING, decideBackfillAction(info(-1L)))
    }

    @Test
    fun `zero size updates`() {
        assertEquals(BackfillAction.UPDATE, decideBackfillAction(info(0L)))
    }

    @Test
    fun `positive size updates`() {
        assertEquals(BackfillAction.UPDATE, decideBackfillAction(info(1234L)))
    }
}
