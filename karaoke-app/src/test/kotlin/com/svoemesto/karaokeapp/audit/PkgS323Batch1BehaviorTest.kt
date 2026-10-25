package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.PictureWriteException
import com.svoemesto.karaokeapp.controllers.parsePromoBoundary
import com.svoemesto.karaokeapp.controllers.shareLinksDateFilter
import com.svoemesto.karaokeapp.writePictureOrThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.awt.image.BufferedImage
import java.nio.file.Path

/**
 * Регрессия пакета `PKG-S3-23` (аудит #336, OpenProject #395), партия 1 —
 * ПОВЕДЕНЧЕСКИЕ проверки (на новых API, введённых правкой).
 *
 * На коде «до» правки эти API не существуют, поэтому класс не компилируется — сам по
 * себе это доказательство «до/после». Контрактные (компилируемые на старом коде)
 * проверки — в [PkgS323Batch1SourceContractsTest].
 *
 * @see specs/395-pkg-s3-23/report.md
 */
class PkgS323Batch1BehaviorTest {
    @Test
    fun `BR-117 ошибка записи картинки пробрасывается наружу, а не глотается`(
        @org.junit.jupiter.api.io.TempDir tmp: Path,
    ) {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        // Каталога не существует → ImageIO.write падает с IOException.
        val target = tmp.resolve("missing-dir").resolve("picture.png").toString()

        // ДО: catch (e: Exception) { println(e.message) } — тест падал бы на
        // «Expected PictureWriteException to be thrown, but nothing was thrown».
        val error =
            assertThrows<PictureWriteException> {
                writePictureOrThrow(image, target, "LYRICS")
            }
        assertTrue(
            error.message.orEmpty().contains(target),
            "в тексте ошибки должен быть путь к файлу: ${error.message}",
        )
        assertTrue(
            error.cause != null,
            "исходное исключение должно сохраниться в cause (иначе потеряна диагностика)",
        )
    }

    @Test
    fun `BR-117 успешная запись картинки файла не создаёт и исключения не бросает`(
        @org.junit.jupiter.api.io.TempDir tmp: Path,
    ) {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        val target = tmp.resolve("picture.png").toString()

        writePictureOrThrow(image, target, "LYRICS")

        assertTrue(java.io.File(target).exists(), "файл картинки должен быть создан")
    }

    @Test
    fun `BR-118 корректная дата фильтра нормализуется, пустая — игнорируется`() {
        assertNull(shareLinksDateFilter(null, "filterCreatedFrom"))
        assertNull(shareLinksDateFilter("   ", "filterCreatedFrom"))
        assertEquals(
            "2026-01-02 03:04:00.0",
            shareLinksDateFilter("2026-01-02T03:04", "filterCreatedFrom"),
        )
        assertEquals(
            "2026-01-02 03:04:05.0",
            shareLinksDateFilter("2026-01-02 03:04:05", "filterCreatedFrom"),
        )
    }

    @Test
    fun `BR-118 некорректная дата фильтра даёт 400 с указанием поля, а не SQLException`() {
        // ДО: строка подставлялась в SQL «как есть», падала SQLException, проглатывалась —
        // админ получал 200 с пустым списком.
        val error =
            assertThrows<ResponseStatusException> {
                shareLinksDateFilter("не дата", "filterCreatedFrom")
            }
        assertEquals(400, error.statusCode.value())
        assertTrue(
            error.message.orEmpty().contains("filterCreatedFrom"),
            "в ошибке должно быть имя поля: ${error.message}",
        )
    }

    @Test
    fun `BR-120 границы акции парсятся, негодная дата даёт 400 с описанием поля`() {
        val ok = parsePromoBoundary("2026-03-04T05:06:07", "validFrom")
        assertEquals("2026-03-04 05:06:07.0", ok.toString())

        // Пустая строка — «границу не трогать», а не ошибка.
        assertNull(parsePromoBoundary("", "validFrom"))
        assertNull(parsePromoBoundary(null, "validTo"))

        // ДО: Timestamp.valueOf бросал IllegalArgumentException → 500 без описания поля.
        val error =
            assertThrows<ResponseStatusException> {
                parsePromoBoundary("2026-13-40", "validFrom")
            }
        assertEquals(400, error.statusCode.value())
        assertTrue(error.message.orEmpty().contains("validFrom"))
    }
}
