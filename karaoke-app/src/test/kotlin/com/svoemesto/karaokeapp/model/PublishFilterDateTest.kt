package com.svoemesto.karaokeapp.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat

/**
 * BR-411 (аудит #336, пакет PKG-S2-17, OpenProject #380): разбор клиентской даты
 * `dd.MM.yy` в фильтре публикаций.
 *
 * ДО ПРАВКИ фильтр вызывал `SimpleDateFormat("dd.MM.yy").parse(filterDateTo)` напрямую.
 * Фронт после клика по штатной кнопке `EXCLUSIVE` присылал `NaN.NaN.N` (эндпоинт
 * `/api/publications/date` штатно отдаёт пустую строку, а `stringDDMMYYaddDays('')`
 * давала `NaN.NaN.N`), `parse` бросал `ParseException`, глобального
 * `@ControllerAdvice` нет — клиент получал HTTP 500, а испорченный фильтр сохранялся
 * в webvueProp и переживал F5.
 *
 * Тест фиксирует: разбор НЕ бросает и возвращает `null` на любой невалидный вход —
 * «границы окна нет» вместо падения запроса.
 *
 * Офлайн: Spring-контекст не поднимается, БД не нужна.
 */
class PublishFilterDateTest {
    private fun fmt(value: String): String = SimpleDateFormat("dd.MM.yy").format(parsePublishFilterDate(value))

    @Test
    fun `валидная дата разбирается`() {
        assertNotNull(parsePublishFilterDate("01.02.26"))
        assertEquals("01.02.26", fmt("01.02.26"))
    }

    @Test
    fun `строка NaN из старого фронта не роняет разбор`() {
        assertNull(parsePublishFilterDate("NaN.NaN.N"))
    }

    @Test
    fun `пустая и отсутствующая граница означают отсутствие фильтра`() {
        assertNull(parsePublishFilterDate(""))
        assertNull(parsePublishFilterDate("   "))
        assertNull(parsePublishFilterDate(null))
    }

    @Test
    fun `несуществующая календарная дата отвергается`() {
        assertNull(parsePublishFilterDate("31.02.26"))
        assertNull(parsePublishFilterDate("32.13.26"))
    }

    @Test
    fun `мусор и ISO-формат отвергаются`() {
        assertNull(parsePublishFilterDate("2026-02-01"))
        assertNull(parsePublishFilterDate("01/02/26"))
        assertNull(parsePublishFilterDate("завтра"))
    }
}
