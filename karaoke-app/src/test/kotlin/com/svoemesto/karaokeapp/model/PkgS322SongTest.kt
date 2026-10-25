package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * Регрессии пакета `PKG-S3-22` (аудит #336, OpenProject #394), часть 3 — `model/Song.kt`
 * (порции P-18a/P-18b).
 *
 * Каждая проверка — «до/после»: рядом лежит реплика старого поведения, которая на старом
 * коде действительно падала/портила данные.
 *
 * @see specs/336-code-audit/findings/P-18a.md
 * @see specs/336-code-audit/findings/P-18b.md
 * @see specs/394-pkg-s3-22/report.md
 */
class PkgS322SongTest {
    private val storageService: KaraokeStorageService = mock(KaraokeStorageService::class.java)
    private val storageApiClient: StorageApiClient = mock(StorageApiClient::class.java)

    private fun song(database: FakeKaraokeConnection = FakeKaraokeConnection()): Song =
        Song(
            database = database,
            storageService = storageService,
            storageApiClient = storageApiClient,
        )

    private fun marker(
        type: Markertype,
        time: Double = 0.0,
        label: String = "",
        note: String = "",
        chord: String = "",
        stringLad: String = "",
        lockLad: String = "",
    ) = SourceMarker(
        time = time,
        label = label,
        note = note,
        chord = chord,
        stringLad = stringLad,
        lockLad = lockLad,
        color = "",
        position = "",
        markertype = type.value,
    )

    // ------------------------------------------------------------------ BR-072
    @Test
    fun `BR-072 неразбираемые source_markers не перезаписываются чтением`() {
        val database = FakeKaraokeConnection()
        val s = song(database)
        s.sourceMarkers = "это-не-json"
        val rawBefore = s.sourceMarkers

        // Старое поведение: геттер при неудаче разбора делал `sourceMarkers = "[]"` + saveToDb(),
        // то есть обычное чтение уничтожало разметку и открывало соединение с БД.
        assertEquals("это-не-json", rawBefore, "предусловие: значение в поле")

        assertTrue(s.sourceMarkersList.isEmpty(), "неразбираемое значение читается как пустой список")
        assertEquals("это-не-json", s.sourceMarkers, "геттер НЕ должен менять source_markers")
        assertEquals(0, database.getConnectionCalls.get(), "геттер НЕ должен ходить в БД")
    }

    @Test
    fun `BR-072 одиночный формат разбирается без записи в БД`() {
        val database = FakeKaraokeConnection()
        val s = song(database)
        s.sourceMarkers =
            """[{"time":1.5,"label":"","note":"","chord":"Am","stringLad":"","lockLad":"","tag":"","color":"","position":"","markertype":"chord"}]"""

        val raw = s.sourceMarkers
        val parsed = s.sourceMarkersList
        assertEquals(1, parsed.size)
        assertEquals(1, parsed[0].size)
        assertEquals("Am", parsed[0][0].chord)
        assertEquals(0, database.getConnectionCalls.get(), "разбор без побочных эффектов")
        assertEquals(raw, s.sourceMarkers, "значение поля не переписывается чтением")
    }

    // ------------------------------------------------------------------ BR-076
    @Test
    fun `BR-076 несуществующий голос не роняет расчёт аппликатур`() {
        val s = song()
        s.sourceMarkers = "[[]]"

        // Старое поведение: `sourceMarkersList[voice]` → IndexOutOfBoundsException.
        assertThrowsIndexOutOfBounds { s.sourceMarkersList[5] }

        assertTrue(s.getStringsForAllNotesInSong(5).isEmpty(), "несуществующий голос — пустой результат")
    }

    private fun assertThrowsIndexOutOfBounds(block: () -> Unit) {
        val thrown = runCatching(block).exceptionOrNull()
        assertTrue(thrown is IndexOutOfBoundsException, "ожидался IndexOutOfBoundsException, был $thrown")
    }

    // ------------------------------------------------------------------ BR-077
    @Test
    fun `BR-077 индекс варианта строя не выходит за границу списка`() {
        // Старое поведение: `size.coerceAtMost(index)` при index >= size давал size.
        assertEquals(3, 3.coerceAtMost(7), "реплика старого бага: индекс == size")
        // Новое: индекс всегда валиден.
        val s = song()
        assertEquals(2, s.clampTabsVariantIndex(7, 3))
        assertEquals(0, s.clampTabsVariantIndex(0, 3))
        assertEquals(0, s.clampTabsVariantIndex(-5, 3))
        assertEquals(0, s.clampTabsVariantIndex(2, 0), "пустой список — индекс 0, без исключения")
    }

    // ------------------------------------------------------------------ BR-080
    @Test
    fun `BR-080 пустые маркеры не роняют длительность песни`() {
        val s = song()
        s.sourceMarkers = "[]"

        // Старое поведение: `sourceMarkersList.maxOf { … }` на пустом списке — NoSuchElementException.
        assertEquals(0L, s.songLengthMs)
    }

    @Test
    fun `BR-080 длительность берётся по END-маркеру`() {
        val s = song()
        s.sourceMarkers =
            """[[{"time":61.5,"label":"END","note":"","chord":"","stringLad":"","lockLad":"","tag":"","color":"","position":"","markertype":"setting"}]]"""
        assertEquals(61_500L, s.songLengthMs)
    }

    // ------------------------------------------------------------------ BR-081
    @Test
    fun `BR-081 невалидный stringLad не роняет рендер табов`() {
        val s = song()
        // Невалидные варианты: нет разделителя, не число, струна вне 0..5, отрицательный лад.
        assertNull(s.parseLockedStringLad("5"))
        assertNull(s.parseLockedStringLad("a|b"))
        assertNull(s.parseLockedStringLad("7|2"))
        assertNull(s.parseLockedStringLad("2|-1"))
        // Валидный — разбирается.
        val ok = s.parseLockedStringLad("5|3")
        assertEquals(5, ok?.string)
        assertEquals(3, ok?.lad)
        // Старое поведение: `marker.stringLad.split("|")` на "5" → IndexOutOfBoundsException.
        assertThrowsIndexOutOfBounds {
            val (a, b) = "5".split("|")
            a + b
        }
    }

    // ------------------------------------------------------------------ BR-082
    @Test
    fun `BR-082 тишина перед первым слогом считается по непустым голосам`() {
        val s = song()
        // Голос 0 — без SYLLABLES/NOTE (раньше это давало NoSuchElementException → 5000 мс),
        // голос 1 — первый слог на 2.0 с.
        s.sourceMarkers =
            """
            [
                [{"time":0.0,"label":"","note":"","chord":"Am","stringLad":"","lockLad":"","tag":"","color":"","position":"","markertype":"chord"}],
                [{"time":2.0,"label":"","note":"","chord":"","stringLad":"","lockLad":"","tag":"","color":"","position":"","markertype":"syllables"}]
            ]
            """.trimIndent()

        assertEquals(3000L, s.getStartSilentOffsetMs(), "5000 - 2000 по непустому голосу")
    }

    @Test
    fun `BR-082 пустые маркеры дают полные 5 секунд тишины`() {
        val s = song()
        s.sourceMarkers = "[]"
        assertEquals(5000L, s.getStartSilentOffsetMs())
    }

    // ------------------------------------------------------------------ BR-083
    @Test
    fun `BR-083 чтение длительности не запускает subprocess и не пишет в БД`() {
        val database = FakeKaraokeConnection()
        val s = song(database)
        s.ms = 123_000L

        assertEquals(123_000L, s.ms, "читается сохранённое значение")
        assertEquals(0, database.getConnectionCalls.get(), "геттер не должен открывать соединение")

        // Значение отсутствует — 0, без ffprobe (файл песни в тесте не существует).
        val empty = song(FakeKaraokeConnection())
        assertEquals(0L, empty.ms)
        assertEquals(0L, empty.refreshDurationMsFromAudioFile(), "явный шаг без файла возвращает 0, не падая")
    }

    // ------------------------------------------------------------------ BR-085
    @Test
    fun `BR-085 битый файл key-bpm не роняет шаг, а даёт пустой результат`() {
        // `getKeyBpmFromFile` использует существующий безопасный разбор: недописанный JSON
        // даёт `null` (см. `parseKeyBpmFileOrNull`), а не исключение наружу.
        val broken =
            kotlin.io.path
                .createTempFile("key-bpm", ".json")
                .toFile()
        broken.writeText("{ \"key\": ")
        try {
            assertNull(Song.parseKeyBpmFileOrNull(broken.absolutePath), "битый JSON — null, не исключение")
        } finally {
            broken.delete()
        }
        // Валидный файл разбирается (ключи key+bpm обязательны).
        val good =
            kotlin.io.path
                .createTempFile("key-bpm", ".json")
                .toFile()
        good.writeText("""{"key":"Am","bpm":120}""")
        try {
            assertEquals("Am", Song.parseKeyBpmFileOrNull(good.absolutePath)?.key)
        } finally {
            good.delete()
        }
    }

    // ------------------------------------------------------------------ BR-086
    @Test
    fun `BR-086 все интерполируемые значения экранируются при сборке HTML`() {
        val src = java.io.File("src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt").readText()

        // Старое поведение: `originalKey`/`newKey`/`capo` (блок каподастра) и `noteOctave`
        // вставлялись в HTML конкатенацией как есть → stored XSS в v-html публичной страницы
        // (после «мягкой» починки парсера нот, см. P-18-01/P-18-06).
        assertTrue(src.contains("val originalKeySafe = originalKey.escapeHtml()"), "originalKey должен экранироваться")
        assertTrue(src.contains("val newKeySafe = newKey.escapeHtml()"), "newKey должен экранироваться")
        assertTrue(src.contains("val capoSafe = capo.toString().escapeHtml()"), "capo должен экранироваться")
        assertFalse(
            src.contains("Оригинальная тональность: " + "\$originalKey" + "\""),
            "в HTML не должно остаться неэкранированного originalKey",
        )
        assertEquals(2, Regex("""noteOctave\.escapeHtml\(\)""").findAll(src).count(), "октава экранируется в обоих местах")
    }

    @Test
    fun `guard — в Song нет записи в БД из геттера sourceMarkersList`() {
        val src = java.io.File("src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt").readText()
        val getter = src.substringAfter("val sourceMarkersList: List<List<SourceMarker>> get()").substringBefore("fun migrateSourceMarkersFormat")
        assertFalse(getter.contains("saveToDb()"), "геттер не должен сохранять сущность: $getter")
    }
}
