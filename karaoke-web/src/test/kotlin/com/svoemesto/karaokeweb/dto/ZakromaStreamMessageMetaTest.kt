package com.svoemesto.karaokeweb.dto

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * BR-354 (аудит #336, пакет PKG-S3-28, OpenProject #400): авторские поля в NDJSON-сообщении `meta`.
 *
 * ЧТО БЫЛО. Страница песен автора (`/zakroma/{id}`) перешла на NDJSON-стрим
 * (`/api/public/zakroma/stream`), а сообщение `meta` не содержало авторских полей
 * (`authorDescription`, `authorShortDescription`, `authorWarning`, `authorSkip`) — они есть
 * только в REST-версии (`ZakromaPublicDto`). Итог: блок автора на публичной странице
 * показывал пустое описание/предупреждение и никогда — бейдж SKIP.
 *
 * ЧТО СТАЛО. `meta` несёт авторские поля; они НЕОБЯЗАТЕЛЬНЫ (старые клиенты их не читают).
 */
class ZakromaStreamMessageMetaTest {
    private val mapper: ObjectMapper = ObjectMapper()

    @Test
    fun `meta carries author fields`() {
        val msg =
            ZakromaStreamMessageDto.meta(
                author = "Кино",
                expectedCount = 2485L,
                authorDescription = "Описание автора",
                authorShortDescription = "Remastered 2018",
                authorWarning = "Иноагент",
                authorSkip = true,
            )
        val json = mapper.writeValueAsString(msg)
        assertTrue(json.contains("\"authorDescription\":\"Описание автора\""))
        assertTrue(json.contains("\"authorShortDescription\":\"Remastered 2018\""))
        assertTrue(json.contains("\"authorWarning\":\"Иноагент\""))
        assertTrue(json.contains("\"authorSkip\":true"))
        assertTrue(json.contains("\"expectedCount\":2485"))
    }

    @Test
    fun `meta without author fields stays backward compatible`() {
        // Старый бэкенд/клиент: полей нет — в JSON они НЕ попадают (NON_NULL),
        // поэтому старый фронт продолжает разбирать сообщение как раньше.
        val json = mapper.writeValueAsString(ZakromaStreamMessageDto.meta("Кино", 10L))
        assertFalse(json.contains("authorDescription"))
        assertFalse(json.contains("authorSkip"))
        assertTrue(json.contains("\"type\":\"meta\""))
    }

    @Test
    fun `empty author fields are not dropped`() {
        // Пустая строка — это «поле есть, но пустое»: клиент должен уметь отличить
        // «нет данных» от «автор без описания». Значение "" НЕ null — попадает в JSON.
        val msg =
            ZakromaStreamMessageDto.meta(
                author = "Кино",
                expectedCount = 1L,
                authorDescription = "",
                authorShortDescription = "",
                authorWarning = "",
                authorSkip = false,
            )
        val json = mapper.writeValueAsString(msg)
        assertTrue(json.contains("\"authorDescription\":\"\""))
        assertTrue(json.contains("\"authorSkip\":false"))
    }

    @Test
    fun `author skip is omitted when viewer has no rights`() {
        // Для посетителя без права видеть SKIP-контент флаг не отдаётся (null → нет в JSON).
        val msg =
            ZakromaStreamMessageDto.meta(
                author = "Кино",
                expectedCount = 1L,
                authorDescription = "d",
                authorShortDescription = "s",
                authorWarning = "w",
                authorSkip = null,
            )
        assertFalse(mapper.writeValueAsString(msg).contains("authorSkip"))
    }

    @Test
    fun `expected count is unchanged`() {
        assertEquals(2485L, ZakromaStreamMessageDto.meta("Кино", 2485L).expectedCount)
    }
}
