package com.svoemesto.karaokeapp.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Контракт `POST /api/authors/updateauthor` — частичное обновление автора.
 *
 * ИНЦИДЕНТ (2026-11-01): модалка «Изменение автора» шлёт только ИЗМЕНЁННЫЕ поля
 * (BR-377, аудит #336, пакет PKG-S3-31) — так устроено специально, чтобы не затирать
 * правки, сделанные другим админом или синхронизацией, пока модалка была открыта
 * (инвариант § 9 в knowledge/system/frontend/webvue3-audit-invariants.md).
 *
 * Но бэкенд требовал полный набор параметров (`@RequestParam(required = true)` на
 * `author`, `ymId`, `vkId`, `lastAlbumYm`, `lastAlbumVk`, `lastAlbumProcessed`,
 * `watched`, `skip`), поэтому КАЖДОЕ такое сохранение падало с HTTP 400
 * (`MissingServletRequestParameterException: Required request parameter 'author' …`)
 * — изменить что-либо у автора через модалку было невозможно.
 *
 * Контракт: обязателен только `id`; переданные параметры применяются, неприсланные
 * поля сохраняют прежние значения. Поведенчески проверить метод в юнит-тесте нельзя
 * (глобальный `WORKING_DATABASE` — `Connection.local()`, подменить его нельзя),
 * поэтому проверки статические — по исходнику, как в PkgS322ApiControllerTest.
 */
class AuthorPartialUpdateContractTest {
    private fun source(name: String = "ApiController.kt"): String =
        File("src/main/kotlin/com/svoemesto/karaokeapp/controllers/$name").readText()

    private fun methodBody(): String =
        source().substringAfter("fun apisUpdateAuthor(").substringBefore("\n    @PostMapping")

    @Test
    fun `обязателен только id - остальные параметры опциональны`() {
        val body = methodBody()
        val required =
            Regex("""@RequestParam\(required = true\)\s+(\w+)""")
                .findAll(body)
                .map { it.groupValues[1] }
                .toList()
        assertEquals(
            listOf("id"),
            required,
            "обязательным должен быть только id: частичное обновление требует опциональных полей, $body",
        )
    }

    @Test
    fun `неприсланные поля не затираются - присваивание только через null-guard`() {
        val body = methodBody()
        val fields =
            listOf(
                "author",
                "ymId",
                "vkId",
                "lastAlbumYm",
                "lastAlbumVk",
                "lastAlbumProcessed",
                "watched",
                "skip",
            )
        for (field in fields) {
            assertTrue(
                body.contains("$field?.let { v -> it.$field = v }"),
                "поле $field должно применяться только когда передано: $body",
            )
            assertFalse(
                Regex("""it\.$field\s*=\s*$field\b""").containsMatchIn(body),
                "поле $field не должно затираться неприсланным значением: $body",
            )
        }
    }

    @Test
    fun `пустой lastAlbumVk больше не затирает прежнее значение пустой строкой`() {
        val body = methodBody()
        assertFalse(
            body.contains("""it.lastAlbumVk = lastAlbumVk ?: """"""),
            "неприсланный lastAlbumVk не должен писать пустую строку: $body",
        )
    }
}
