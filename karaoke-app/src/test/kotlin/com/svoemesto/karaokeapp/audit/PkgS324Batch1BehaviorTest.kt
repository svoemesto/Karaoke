package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.model.ImageParams
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.Picture
import com.svoemesto.karaokeapp.model.TextParams
import com.svoemesto.karaokeapp.services.cleanup.ArtifactCleanupClass
import com.svoemesto.karaokeapp.services.cleanup.ArtifactCleanupPlanner
import com.svoemesto.karaokeapp.services.cleanup.ArtifactCleanupService
import com.svoemesto.karaokeapp.services.cleanup.CleanupJournal
import com.svoemesto.karaokeapp.services.cleanup.CleanupPlan
import java.awt.Color
import java.awt.Font
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.xml.sax.InputSource

/**
 * Поведенческая регрессия пакета `PKG-S3-24` (аудит #336, OpenProject #396), партия 1 —
 * BR-174, BR-179, BR-180, BR-183.
 *
 * Эти проверки падают на старом коде и проходят после правки:
 * - BR-183: XML с `&` в названии песни был невалидным → теперь разбирается парсером;
 * - BR-179: пустой/битый путь к картинке давал `IIOException`/NPE → теперь явная ошибка;
 * - BR-180: перебор `2^(n-1)` разбиений на длинном названии занимал минуты → теперь быстро;
 * - BR-174: отчёт чистки строится в фоновом потоке → состояние `READY` приходит без
 *   ожидания в HTTP-потоке.
 *
 * @see specs/396-pkg-s3-24/report.md
 */
class PkgS324Batch1BehaviorTest {
    // ---------------------------------------------------------------- BR-183

    @Test
    fun `BR-183 XML с амперсандом в атрибуте и тексте остаётся валидным`() {
        val node =
            MltNode(
                name = "producer",
                fields = mutableMapOf("id" to "p0", "title" to "Sex & Violence <live>"),
                body = "Глеб Самойлоff & The MatriXX",
            )

        val xml = node.toString()
        assertTrue(xml.contains("&amp;"), "амперсанд должен быть экранирован: $xml")
        assertTrue(xml.contains("&lt;live&gt;"), "угловые скобки в атрибуте должны быть экранированы: $xml")

        // Документ должен разбираться стандартным XML-парсером (на старом коде — нет).
        val document =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(InputSource(StringReader(xml)))
        val element = document.documentElement
        assertEquals("p0", element.getAttribute("id"))
        assertEquals("Sex & Violence <live>", element.getAttribute("title"))
        assertEquals("Глеб Самойлоff & The MatriXX", element.textContent)
    }

    @Test
    fun `BR-183 вложенный xmldata не ломает документ`() {
        val inner =
            MltNode(
                name = "playlist",
                fields = mutableMapOf("id" to "pl0"),
                body = "Текст & амперсанд",
            ).toString()
        val node =
            MltNode(
                name = "property",
                fields = mutableMapOf("name" to "xmldata"),
                body = "<?xml version=\"1.0\"?>$inner",
            )

        val xml = node.toString()
        val document =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(InputSource(StringReader(xml)))
        assertEquals("xmldata", document.documentElement.getAttribute("name"))
    }

    // ---------------------------------------------------------------- BR-179

    @Test
    fun `BR-179 пустой путь к картинке даёт понятную ошибку`() {
        val picture = Picture(params = ImageParams(w = 32, h = 32, pathToFile = ""))
        val e = assertThrows(IllegalStateException::class.java) { picture.bi() }
        assertTrue(
            e.message!!.contains("picture.image_path_blank"),
            "ожидалась ошибка о пустом пути, а было: ${e.message}",
        )
    }

    @Test
    fun `BR-179 отсутствующий файл картинки даёт понятную ошибку`() {
        val picture = Picture(params = ImageParams(w = 32, h = 32, pathToFile = "/tmp/karaoke-br179-no-such-file.png"))
        val e = assertThrows(IllegalStateException::class.java) { picture.bi() }
        assertTrue(
            e.message!!.contains("picture.image_file_missing"),
            "ожидалась ошибка об отсутствующем файле, а было: ${e.message}",
        )
    }

    // ---------------------------------------------------------------- BR-180

    @Test
    @Timeout(60)
    fun `BR-180 длинное название разбивается на строки быстро`() {
        val text = List(20) { "Слово$it" }.joinToString(" ")
        val picture =
            Picture(
                params =
                    TextParams(
                        w = 800,
                        h = 200,
                        color = Color.WHITE,
                        text = text,
                        fontName = "SansSerif",
                        fontStyle = Font.PLAIN,
                        fontSize = 40,
                        isCalculatedSize = true,
                        isLineBreak = true,
                    ),
            )

        // На старом коде здесь перебирались 2^19 = 524 288 разбиений (минуты CPU).
        val image = picture.bi()
        assertTrue(image.width > 0 && image.height > 0)
    }

    // ---------------------------------------------------------------- BR-174

    @Test
    fun `BR-174 отчёт чистки строится в фоновом потоке`() {
        val tempDir: Path = Files.createTempDirectory("karaoke-br174")
        try {
            val service =
                ArtifactCleanupService(
                    planner = ArtifactCleanupPlanner(roots = listOf(tempDir.toString())),
                    journal = CleanupJournal(tempDir.resolve("logs").toString()),
                )

            // Рефлексия — чтобы тест КОМПИЛИРОВАЛСЯ и на старом коде: тогда «до»
            // — это падение (метода `planAsync` нет), а не ошибка компиляции.
            val method =
                ArtifactCleanupService::class.java.methods.firstOrNull { it.name == "planAsync" }
            assertNotNull(method, "BR-174: нужен асинхронный построитель отчёта (planAsync)")

            val job = method!!.invoke(service, ArtifactCleanupClass.DEMO_MP4.name)
            val planId = job.javaClass.getMethod("getPlanId").invoke(job) as String

            // Отчёт появляется по planId — обход каталогов идёт в фоновом потоке.
            var plan: CleanupPlan? = null
            val deadline = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < deadline && plan == null) {
                plan = runCatching { service.planById(planId) }.getOrNull()
                if (plan == null) Thread.sleep(50)
            }
            assertNotNull(plan, "BR-174: отчёт должен быть построен и доступен по planId")
            assertNotNull(plan!!.fingerprint)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
