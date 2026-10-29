package com.svoemesto.karaokeapp.tuple

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 674 (FR-004/FR-005): scopeId, подстановка `{scope}`, изоляция каталогов и контейнеров.
 *
 * @see specs/674-kortege-execution/tasks.md T011
 */
class TupleScopeTest {
    @Test
    fun `scopeId — chainId, у одиночного — id`() {
        assertEquals(100L, TupleScope.scopeId(100L, 1L))
        assertEquals(7L, TupleScope.scopeId(null, 7L))
    }

    @Test
    fun `подстановка токена во всех аргументах`() {
        val argv =
            listOf(
                "cp",
                "/song.flac",
                "/tmp/{scope}/file.flac",
                "runFunctionWithArgs",
                "finalizeDemucs",
                "scopeId={scope}",
            )
        val expanded = TupleScope.expand(argv, 1650737L)

        assertTrue(expanded.contains("/tmp/1650737/file.flac"))
        assertTrue(expanded.contains("scopeId=1650737"))
        assertFalse(expanded.any { it.contains(TupleScope.TOKEN) })
    }

    @Test
    fun `каталоги и имена контейнеров двух кортежей не совпадают`() {
        val root = "/tmp/demucs"
        val a = TupleScope.scopeDir(root, 100L)
        val b = TupleScope.scopeDir(root, 200L)

        assertTrue(a != b)
        assertTrue(a.name != b.name)
        // имена контейнеров строятся из scopeId в Song.argsDemucs*, проверяем контракт имени
        assertTrue("demucs-${a.name}" != "demucs-${b.name}")
    }

    @Test
    fun `каталог scope лежит внутри корня`() {
        val dir = TupleScope.scopeDir("/tmp/demucs", 42L)
        assertEquals("/tmp/demucs/42", dir.path)
    }
}
