package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.services.StorageFileInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pass 435 (#159) + Pass 448 (#181): тесты чистого решения backfill etag/size.
 *
 * `null` (info неизвестен — circuit open/ошибка) → SKIP;
 * `size < 0` (файл не найден) → MARK_MISSING;
 * иначе → UPDATE.
 *
 * Pass 448: выборка расширена на `exists=false` — при проверке такой записи
 * найденный файл даёт UPDATE (exists=true + etag/size), отсутствующий — MARK_MISSING
 * (остаётся false). Решение по `StorageFileInfo` от `exists` строки не зависит,
 * поэтому тесты фиксируют это явно.
 */
class CacheEtagSizeBackfillTest {
    private fun info(size: Long) = StorageFileInfo("karaoke", "a.mp3", "\"etag\"", size)

    @Test
    fun `null info is skipped`() {
        assertEquals(BackfillAction.SKIP, decideBackfillAction(null))
    }

    @Test
    fun `missing file for previously-absent row stays missing`() {
        // Pass 448: строка была exists=false, файла по-прежнему нет → MARK_MISSING.
        assertEquals(BackfillAction.MARK_MISSING, decideBackfillAction(info(-1L)))
    }

    @Test
    fun `existing file for previously-absent row updates`() {
        // Pass 448: строка была exists=false, но файл появился мимо Karaoke → UPDATE
        // (exists=true + etag/size).
        assertEquals(BackfillAction.UPDATE, decideBackfillAction(info(4321L)))
    }
}
