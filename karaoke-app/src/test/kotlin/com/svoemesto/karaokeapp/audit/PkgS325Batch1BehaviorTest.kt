package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.UuidCache
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.storedUuidId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Регрессия пакета `PKG-S3-25` (аудит #336, OpenProject #397), партия 1 —
 * ПОВЕДЕНЧЕСКИЕ проверки кеша UUID структурных узлов MLT (BR-230).
 *
 * На коде «до» правки класс [UuidCache] не существует, поэтому тест не
 * компилируется — сам по себе это доказательство «до/после».
 *
 * @see specs/397-pkg-s3-25/report.md
 */
class PkgS325Batch1BehaviorTest {
    @Test
    fun `BR-230 кеш загружает таблицу UUID один раз и больше не ходит в БД`() {
        var loadAllCalls = 0
        var loadOneCalls = 0
        var createCalls = 0
        val cache =
            UuidCache(
                loadAll = {
                    loadAllCalls++
                    mapOf(1 to "uuid-1", 2 to "uuid-2")
                },
                loadOne = {
                    loadOneCalls++
                    null
                },
                create = {
                    createCalls++
                    "uuid-$it"
                },
            )

        // Первый обращающийся подтягивает всю таблицу одним запросом.
        assertEquals("uuid-1", cache.get(1))
        assertEquals(1, loadAllCalls, "BR-230: таблица UUID должна загружаться ОДНИМ запросом")

        // Второй и третий вызовы — уже из памяти.
        assertEquals("uuid-2", cache.get(2))
        assertEquals("uuid-1", cache.get(1))
        assertEquals(1, loadAllCalls, "BR-230: повторный запрос не должен перечитывать таблицу")
        assertEquals(0, loadOneCalls, "BR-230: для найденного id точечный SELECT не нужен")
        assertEquals(0, createCalls, "BR-230: существующий id не должен пересоздаваться")
    }

    @Test
    fun `BR-230 новый id создаётся один раз и кэшируется`() {
        var createCalls = 0
        val cache =
            UuidCache(
                loadAll = { emptyMap() },
                loadOne = { null },
                create = {
                    createCalls++
                    "uuid-$it"
                },
            )

        assertEquals("uuid-42", cache.get(42))
        assertEquals("uuid-42", cache.get(42))
        assertEquals(1, createCalls, "BR-230: один и тот же новый id должен создаваться один раз")
    }

    @Test
    fun `BR-230 reset сбрасывает состояние загрузки`() {
        var loadAllCalls = 0
        val cache =
            UuidCache(
                loadAll = {
                    loadAllCalls++
                    mapOf(1 to "uuid-1")
                },
                loadOne = { null },
                create = { "uuid-new" },
            )

        cache.get(1)
        cache.get(2)
        assertEquals(1, loadAllCalls)
        assertEquals(2, cache.size())

        cache.reset()
        assertEquals(0, cache.size())
        cache.get(1)
        assertEquals(2, loadAllCalls, "BR-230: после reset таблица должна загружаться заново")
    }

    @Test
    fun `BR-230 идентификатор узла считается одинаково и различает узлы`() {
        val key = listOf(ProducerType.ELEMENT, 0, 3, 7)
        assertEquals(storedUuidId(key), storedUuidId(listOf(ProducerType.ELEMENT, 0, 3, 7)))
        assertNotEquals(
            storedUuidId(key),
            storedUuidId(listOf(ProducerType.ELEMENT, 0, 3, 8)),
            "BR-230: разные элементы должны давать разные id",
        )
        assertNotEquals(
            storedUuidId(key),
            storedUuidId(listOf(ProducerType.FILL, 0, 3, 7)),
            "BR-230: разные типы должны давать разные id",
        )
        assertTrue(storedUuidId(key) != 0)
    }
}
