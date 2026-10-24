package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.AlbumCoverUrlPolicy
import com.svoemesto.karaokeapp.LibraryRootPolicy
import com.svoemesto.karaokeapp.LyricsSearchPacing
import com.svoemesto.karaokeapp.ProcessArgsPolicy
import com.svoemesto.karaokeapp.SmartCopyPathPolicy
import com.svoemesto.karaokeapp.SongCopyFieldsPolicy
import com.svoemesto.karaokeapp.SongPublicationFlagsPolicy
import com.svoemesto.karaokeapp.model.Markertype
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SongField
import com.svoemesto.karaokeapp.model.SourceMarker
import com.svoemesto.karaokeapp.getTransposingChord
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeapp.FakeKaraokeConnection
import org.mockito.Mockito
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path

/**
 * Регрессии пакета `PKG-S2-10` (аудит #336, OpenProject #373): чистые политики.
 *
 * Каждый блок содержит два вида проверок:
 *  - «старое поведение» — реплика прежней логики, показывающая дефект;
 *  - «после правки» — проверка production-шва, который теперь используется кодом.
 *
 * @see specs/373-pkg-s2-10/report.md
 */
class PkgS210PoliciesTest {
    private val storageService: KaraokeStorageService = Mockito.mock(KaraokeStorageService::class.java)
    private val storageApiClient: StorageApiClient = Mockito.mock(StorageApiClient::class.java)

    /** Song без обращения к глобальным lateinit-зависимостям (WORKING_DATABASE/KSS_APP). */
    private fun newSong(): Song = Song(FakeKaraokeConnection(), storageService, storageApiClient)

    // ------------------------------------------------------------------ BR-033
    @Test
    fun `BR-033 пустой args отклоняется, валидный принимается`() {
        // Старое поведение: args[0] на пустом списке — IndexOutOfBounds в главном цикле.
        assertThrows<IndexOutOfBoundsException> {
            val oldArgs: List<List<String>> = emptyList()
            oldArgs[0]
        }
        assertNotNull(ProcessArgsPolicy.validationError(emptyList()))
        assertNotNull(ProcessArgsPolicy.validationError(listOf(emptyList())))
        assertNotNull(ProcessArgsPolicy.validationError(listOf(listOf("docker"), emptyList())))
        assertNull(ProcessArgsPolicy.validationError(listOf(listOf("ffmpeg", "-i", "input.bin"))))
    }

    // ------------------------------------------------------------------ BR-048
    @Test
    fun `BR-048 ID в списке полей отклоняется целиком`() {
        // Старое поведение: любой SongField принимался, включая ID.
        assertEquals(SongField.ID, SongField.valueOf("ID"))

        val bad = SongCopyFieldsPolicy.decide("ID;SOURCE_TEXT")
        assertFalse(bad.isValid)
        assertTrue(bad.rejected.contains("ID"))

        val unknown = SongCopyFieldsPolicy.decide("SOURCE_TEXT;NOT_A_FIELD")
        assertFalse(unknown.isValid)

        val good = SongCopyFieldsPolicy.decide("SOURCE_TEXT;RESULT_TEXT;SOURCE_MARKERS")
        assertTrue(good.isValid)
        assertEquals(3, good.accepted.size)
        assertTrue(good.accepted.none { it == SongField.ID })
    }

    // ------------------------------------------------------------------ BR-049
    @Test
    fun `BR-049 SSRF-политика блокирует loopback, приватные диапазоны и нестандартные порты`() {
        val publicResolver: (String) -> Array<InetAddress> = {
            arrayOf(InetAddress.getByName("93.158.134.1"))
        }
        // Старое поведение: url уходил в HttpClient без проверок — URI валиден.
        val oldUrl = "http://127.0.0.1:8899/api/diagnostics"
        val oldUri = java.net.URI.create(oldUrl)
        assertEquals(oldUrl, oldUri.toString())

        listOf(
            "http://127.0.0.1:8899/api/diagnostics",
            "http://localhost/admin",
            "http://[::1]/x",
            "http://169.254.169.254/latest/meta-data",
            "http://10.0.0.5/",
            "http://192.168.1.10/",
            "http://example.com:8080/x",
            "ftp://example.com/x",
            "http://user@example.com/x",
        ).forEach { url ->
            assertNotNull(AlbumCoverUrlPolicy.rejectionReason(url, publicResolver), "должен быть отклонён: $url")
        }

        assertNull(
            AlbumCoverUrlPolicy.rejectionReason("https://avatars.yandex.net/get-cover/1.jpg", publicResolver),
        )
        assertNull(
            AlbumCoverUrlPolicy.rejectionReason("http://example.com/a.png", publicResolver),
        )
        // Резолв в приватный адрес тоже блокируется (DNS-rebinding на имя).
        assertNotNull(
            AlbumCoverUrlPolicy.rejectionReason("https://evil.example.com/a.png") { arrayOf(InetAddress.getByName("10.1.2.3")) },
        )
    }

    @Test
    fun `BR-049 сигнатура ответа проверяется до возврата`() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val html = "<html>internal</html>".toByteArray()
        assertTrue(AlbumCoverUrlPolicy.looksLikeImage(png))
        assertTrue(AlbumCoverUrlPolicy.looksLikeImage(jpeg))
        assertFalse(AlbumCoverUrlPolicy.looksLikeImage(html))
        assertFalse(AlbumCoverUrlPolicy.looksLikeImage("short".toByteArray()))
    }

    // ------------------------------------------------------------------ BR-050
    @Test
    fun `BR-050 rootFolder принимается только под корнем библиотеки и без изменений`(
        @TempDir temp: Path,
    ) {
        val root = Files.createDirectories(temp.resolve("library"))
        val album = Files.createDirectories(root.resolve("author/album"))
        val roots = listOf(root.toString())

        // Старое поведение: прямое присваивание любого пути.
        val song = newSong()
        song.rootFolder = "/etc"
        assertEquals("/etc", song.rootFolder)

        val same = LibraryRootPolicy.validateRequestedRoot(album.toString(), album.toString(), roots)
        assertNotNull(same)

        assertNull(LibraryRootPolicy.validateRequestedRoot("/etc", album.toString(), roots))
        assertNull(LibraryRootPolicy.validateRequestedRoot("$root/../outside", album.toString(), roots))
        assertNull(LibraryRootPolicy.validateRequestedRoot("relative/path", album.toString(), roots))
        // Другой каталог под корнем — тоже отклоняем: rootFolder read-only.
        val other = Files.createDirectories(root.resolve("author/other"))
        assertNull(LibraryRootPolicy.validateRequestedRoot(other.toString(), album.toString(), roots))
    }

    // ------------------------------------------------------------------ BR-063
    @Test
    fun `BR-063 после сетевого вызова пишутся только флаги публикации`() {
        val snapshot = newSong()
        snapshot.id = 10
        snapshot.fields[SongField.ID_TELEGRAM_DEMO] = "air-777"

        val stale = newSong()
        stale.id = 10
        stale.newsPremiumTelegramSent = true

        // Старое поведение: diff устаревшего объекта содержит чужой id — он и затирался.
        val oldDiff = Song.getDiff(stale, snapshot).map { it.recordDiffName }
        assertTrue(oldDiff.contains("id_telegram_demo"), "реплика старого бага: $oldDiff")

        val fresh = newSong()
        fresh.id = 10
        fresh.fields[SongField.ID_TELEGRAM_DEMO] = "air-777"
        SongPublicationFlagsPolicy.copyFlags(from = stale, to = fresh)
        val newDiff = Song.getDiff(fresh, snapshot).map { it.recordDiffName }
        assertFalse(newDiff.contains("id_telegram_demo"), "id air-публикации не должен попадать в diff: $newDiff")
        assertTrue(fresh.newsPremiumTelegramSent)
    }

    // ------------------------------------------------------------------ BR-064
    @Test
    fun `BR-064 пауза не берётся для песни без запроса`() {
        // Старое поведение: sleep на каждой итерации, кроме последней, независимо от запроса.
        fun oldSleep(index: Int, size: Int, intervalMs: Long): Long = if (index < size - 1) intervalMs else 0
        assertEquals(10_000L, oldSleep(0, 100, 10_000L))

        assertTrue(LyricsSearchPacing.shouldRequest(haveSourceText = false, idsCount = 100))
        assertTrue(LyricsSearchPacing.shouldRequest(haveSourceText = true, idsCount = 1))
        assertFalse(LyricsSearchPacing.shouldRequest(haveSourceText = true, idsCount = 100))
        // Для песни без запроса новой паузы нет вовсе (решение о запросе = false).
        assertEquals(0L, LyricsSearchPacing.delayBeforeRequest(lastRequestAtMs = null, nowMs = 0L, intervalMs = 10_000L))

        assertEquals(10_000L, LyricsSearchPacing.delayBeforeRequest(lastRequestAtMs = 1_000L, nowMs = 1_000L, intervalMs = 10_000L))
        assertEquals(0L, LyricsSearchPacing.delayBeforeRequest(lastRequestAtMs = 1_000L, nowMs = 11_000L, intervalMs = 10_000L))
        assertEquals(0L, LyricsSearchPacing.delayBeforeRequest(lastRequestAtMs = null, nowMs = 5_000L, intervalMs = 10_000L))
    }

    // ------------------------------------------------------------------ BR-071
    @Test
    fun `BR-071 неразбираемый аккорд не роняет рендер`() {
        // Старое поведение: getTransposingChord делал note!! и падал.
        assertThrows<NullPointerException> {
            // реплика старой строки: MusicNote.entries.indexOf(note!!)
            val note: com.svoemesto.karaokeapp.model.MusicNote? = null
            MusicNoteEntries.indexOf(note!!)
        }
        assertEquals("N.C.", getTransposingChord("N.C.", 2))
        assertEquals("C/H", getTransposingChord("C/H", 3))
        assertEquals("Em", getTransposingChord("Em", 0))
        // Известный аккорд по-прежнему транспонируется.
        assertTrue(getTransposingChord("Am", 2).isNotEmpty())
    }

    private object MusicNoteEntries {
        val entries = com.svoemesto.karaokeapp.model.MusicNote.entries

        fun indexOf(note: com.svoemesto.karaokeapp.model.MusicNote): Int = entries.indexOf(note)
    }

    @Test
    fun `BR-071 getFormattedChords сохраняет исходный неизвестный аккорд`() {
        val song = newSong()
        val markers =
            listOf(
                listOf(
                    SourceMarker(
                        time = 0.0,
                        label = "",
                        chord = "N.C.",
                        color = "",
                        position = "",
                        markertype = Markertype.CHORD.value,
                    ),
                    SourceMarker(
                        time = 1.0,
                        label = "",
                        chord = "",
                        color = "",
                        position = "",
                        markertype = Markertype.ENDOFLINE.value,
                    ),
                ),
            )
        song.fields[SongField.SOURCE_MARKERS] = Json.encodeToString(ListSerializer(ListSerializer(SourceMarker.serializer())), markers)
        val html = song.getFormattedChords()
        assertTrue(html.contains("N.C."), "неизвестный аккорд должен остаться в тексте: $html")
    }

    // ------------------------------------------------------------------ BR-108
    @Test
    fun `BR-108 INSERT содержит поля, которые терялись при вставке`() {
        val song = newSong()
        song.id = 0
        song.albumId = 42
        song.rootId = 7
        song.free = true
        song.freeAfterOnAir = true
        song.audioParentId = 13
        song.audioSimilarityPercent = 95
        song.audioDeltaMs = 120
        song.audioCompareHistory = """[{"id":1}]"""
        song.playerReadinessFlags = """{"stemAccompanimentReady":true}"""

        val sql = song.getSqlToInsert()
        listOf(
            "album_id",
            "root_id",
            "free",
            "free_after_on_air",
            "audio_parent_id",
            "audio_similarity_percent",
            "audio_delta_ms",
            "audio_compare_history",
            "player_readiness_flags",
        ).forEach { column ->
            assertTrue(sql.contains(column), "в INSERT нет колонки $column: $sql")
        }
        assertTrue(sql.contains("42"), "album_id должен попасть в VALUES: $sql")
        assertTrue(sql.contains("7"), "root_id должен попасть в VALUES: $sql")
    }

    // ------------------------------------------------------------------ BR-109
    @Test
    fun `BR-109 путь экспорта и шаблон имени валидируются`(
        @TempDir temp: Path,
    ) {
        val export = Files.createDirectories(temp.resolve("export"))
        assertNotNull(SmartCopyPathPolicy.validateExportRoot(export.toString(), allowedRoots = emptyList(), protectedRoots = emptyList()))

        assertNull(SmartCopyPathPolicy.validateExportRoot("/etc", allowedRoots = emptyList(), protectedRoots = emptyList()))
        assertNull(SmartCopyPathPolicy.validateExportRoot("relative/export", allowedRoots = emptyList(), protectedRoots = emptyList()))
        assertNull(SmartCopyPathPolicy.validateExportRoot("$export/../etc", allowedRoots = emptyList(), protectedRoots = emptyList()))
        assertNull(SmartCopyPathPolicy.validateExportRoot("/sm-karaoke/work", allowedRoots = emptyList(), protectedRoots = listOf("/sm-karaoke/work")))

        // Явный allowlist: всё вне него запрещено.
        val other = Files.createDirectories(temp.resolve("other"))
        assertNull(SmartCopyPathPolicy.validateExportRoot(other.toString(), allowedRoots = listOf(export.toString()), protectedRoots = emptyList()))

        // Шаблон: `..` и разделители недопустимы, обычный шаблон из UI — допустим.
        assertNull(SmartCopyPathPolicy.sanitizeDestinationTemplate("../evil"))
        // Абсолютный путь не проходит как «имя файла»: после санитайзинга остаётся
        // один сегмент без разделителей (в каталог по нему не уйти).
        val sanitizedAbsolute = SmartCopyPathPolicy.sanitizeDestinationTemplate("/etc/passwd")
        assertNotNull(sanitizedAbsolute)
        assertFalse(sanitizedAbsolute!!.contains("/"))
        assertNull(SmartCopyPathPolicy.sanitizeDestinationFileName(".."))
        assertEquals(
            "[{author}] ({year}) {name} [{key}]",
            SmartCopyPathPolicy.sanitizeDestinationTemplate("[{author}] ({year}) {name} [{key}]"),
        )
        // Реплика старого поведения: сырой путь с `..` выходил за пределы export.
        assertFalse(Path.of(export.toString(), "../etc").normalize().startsWith(export))
    }
}
