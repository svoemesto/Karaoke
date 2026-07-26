package com.svoemesto.karaokeapp.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Офлайн-проверка парсинга и реестра спецтегов (без сети/БД) — контракт синтаксиса и реестра
 * зафиксирован в `specs/010-lyrics-spec-tags/contracts/tag-registry.md`.
 */
class SpecTagsTest {
    @Test
    fun `parseLine распознаёт тег без значения`() {
        val (stripped, tags) = SpecTags.parseLine("~newline~")
        assertEquals("", stripped)
        assertEquals(listOf(SpecTags.SpecTag("newline", null)), tags)
    }

    @Test
    fun `parseLine распознаёт тег со значением`() {
        val (stripped, tags) = SpecTags.parseLine("~group:2~")
        assertEquals("", stripped)
        assertEquals(listOf(SpecTags.SpecTag("group", "2")), tags)
    }

    @Test
    fun `parseLine регистронезависима к имени тега`() {
        val (_, tags) = SpecTags.parseLine("~Куплет~")
        assertEquals(listOf(SpecTags.SpecTag("куплет", null)), tags)
    }

    @Test
    fun `parseLine оставляет обычную строку лирики без изменений`() {
        val (stripped, tags) = SpecTags.parseLine("Обычная строка текста песни")
        assertEquals("Обычная строка текста песни", stripped)
        assertTrue(tags.isEmpty())
    }

    @Test
    fun `resolve newline не принимает значение`() {
        assertEquals(Markertype.NEWLINE to "", SpecTags.resolve(SpecTags.SpecTag("newline", null)))
        assertNull(SpecTags.resolve(SpecTags.SpecTag("newline", "x")))
    }

    @Test
    fun `resolve group строит маркер SETTING с label GROUP`() {
        assertEquals(Markertype.SETTING to "GROUP|2", SpecTags.resolve(SpecTags.SpecTag("group", "2")))
    }

    @Test
    fun `resolve group отклоняет нечисловое и вне-диапазонное значение`() {
        assertNull(SpecTags.resolve(SpecTags.SpecTag("group", "abc")))
        assertNull(SpecTags.resolve(SpecTags.SpecTag("group", "5")))
        assertNull(SpecTags.resolve(SpecTags.SpecTag("group", null)))
    }

    @Test
    fun `resolve comment строит маркер SETTING с label COMMENT`() {
        assertEquals(Markertype.SETTING to "COMMENT|текст", SpecTags.resolve(SpecTags.SpecTag("comment", "текст")))
    }

    @Test
    fun `resolve comment отклоняет пустое значение`() {
        assertNull(SpecTags.resolve(SpecTags.SpecTag("comment", null)))
        assertNull(SpecTags.resolve(SpecTags.SpecTag("comment", "   ")))
    }

    @Test
    fun `resolve неизвестного тега возвращает null`() {
        assertNull(SpecTags.resolve(SpecTags.SpecTag("foobar", null)))
    }

    @Test
    fun `алиасы группы резолвятся идентично своей канонической параметризованной форме`() {
        assertEquals(SpecTags.resolve(SpecTags.SpecTag("group", "0")), SpecTags.resolve(SpecTags.SpecTag("куплет", null)))
        assertEquals(SpecTags.resolve(SpecTags.SpecTag("group", "1")), SpecTags.resolve(SpecTags.SpecTag("припев", null)))
        assertEquals(SpecTags.resolve(SpecTags.SpecTag("group", "2")), SpecTags.resolve(SpecTags.SpecTag("бридж", null)))
        assertEquals(SpecTags.resolve(SpecTags.SpecTag("group", "3")), SpecTags.resolve(SpecTags.SpecTag("приговор", null)))
    }

    @Test
    fun `для group 4 алиаса нет в v1`() {
        assertNull(SpecTags.resolve(SpecTags.SpecTag("group4", null)))
        assertEquals(Markertype.SETTING to "GROUP|4", SpecTags.resolve(SpecTags.SpecTag("group", "4")))
    }

    @Test
    fun `алиас со значением не распознаётся`() {
        assertNull(SpecTags.resolve(SpecTags.SpecTag("куплет", "2")))
    }
}
