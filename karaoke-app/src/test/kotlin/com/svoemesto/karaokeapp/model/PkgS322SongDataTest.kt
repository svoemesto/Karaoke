package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File
import java.nio.file.Files
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException

/**
 * Регрессии пакета `PKG-S3-22` (аудит #336, OpenProject #394), часть 4 — `model/Song.kt`,
 * записи BR-073, BR-074, BR-075, BR-078, BR-079, BR-084, BR-103, BR-105, BR-106, BR-107.
 *
 * Каждая проверка — «до/после»: рядом лежит реплика старого поведения, которая на старом
 * коде действительно падала / портила данные / писала в БД.
 *
 * @see specs/336-code-audit/findings/P-18a.md
 * @see specs/336-code-audit/findings/P-18b.md
 * @see specs/394-pkg-s3-22/report.md
 */
class PkgS322SongDataTest {
    private val storageService: KaraokeStorageService = mock(KaraokeStorageService::class.java)
    private val storageApiClient: StorageApiClient = mock(StorageApiClient::class.java)

    private fun song(database: FakeKaraokeConnection = FakeKaraokeConnection()): Song =
        Song(
            database = database,
            storageService = storageService,
            storageApiClient = storageApiClient,
        )

    private fun songSource(): String = File("src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt").readText()

    /** Поддельное JDBC-соединение: `prepareStatement` отдаёт заданный mock-статмент. */
    private fun mockConnection(ps: PreparedStatement): Connection {
        val connection = mock(Connection::class.java)
        `when`(connection.prepareStatement(anyString())).thenReturn(ps)
        return connection
    }

    /** JSON одного маркера (голоса) с заданным временем. */
    private fun voiceMarker(time: Double): String =
        "{\"time\":$time,\"label\":\"\",\"note\":\"\",\"chord\":\"C\",\"stringLad\":\"\",\"lockLad\":\"\"," +
            "\"tag\":\"\",\"color\":\"\",\"position\":\"\",\"markertype\":\"chord\"}"

    /** JSON набора голосов: [[маркер], [маркер]] — по одному маркеру на голос. */
    private fun voicesJson(vararg times: Double): String =
        "[" + times.joinToString(",") { "[${voiceMarker(it)}]" } + "]"

    // ================================================================== BR-073
    @Test
    fun `BR-073 чтение rootFolder не пишет в БД и не меняет значение`() {
        val database = FakeKaraokeConnection(name = "LOCAL")
        val s = song(database)
        s.rootFolder = "/sm-karaoke/work/Автор/2020 - Альбом"

        // Старое поведение: геттер при отсутствующем каталоге перебирал PROJECT_ROOT_FOLDERS,
        // подставлял хвост пути и при первом совпадении (1) менял `_rootFolder` и (2) делал
        // `UPDATE tbl_songs SET root_folder = ?` — то есть ЧТЕНИЕ свойства писало в БД.
        val root = s.rootFolder
        assertEquals("/sm-karaoke/work/Автор/2020 - Альбом", root, "геттер возвращает сохранённое значение")
        assertEquals("/sm-karaoke/work/Автор/2020 - Альбом", s.rootFolder, "геттер не подменяет значение")
        assertEquals(0, database.getConnectionCalls.get(), "геттер rootFolder НЕ должен ходить в БД")

        val src = songSource()
        val getter = src.substringAfter("var rootFolder: String").substringBefore("internal fun rootFolderRepairCandidates")
        assertFalse(getter.contains("UPDATE tbl_songs"), "геттер rootFolder не должен писать в БД: $getter")
        assertFalse(getter.contains("getConnection"), "геттер rootFolder не должен открывать соединение: $getter")
    }

    @Test
    fun `BR-073 починка rootFolder принимает только единственный подходящий кандидат`() {
        val tmp = Files.createTempDirectory("s3-22-root-a").toFile()
        val tmp2 = Files.createTempDirectory("s3-22-root-b").toFile()
        try {
            val songDir = File(tmp, "Author/2020 - Album").apply { mkdirs() }
            File(songDir, "Test Song.flac").writeText("audio")
            val songDir2 = File(tmp2, "Author/2020 - Album").apply { mkdirs() }
            File(songDir2, "Test Song.flac").writeText("audio")

            val ps = mock(PreparedStatement::class.java)
            `when`(ps.executeUpdate()).thenReturn(1)
            val database = FakeKaraokeConnection(jdbcConnection = mockConnection(ps), name = "LOCAL")
            val s = song(database)
            s.id = 42L
            s.fileName = "Test Song"
            // Каталог песни «переехал»: в БД старый корень, на диске — новый.
            s.rootFolder = "/sm-karaoke/work/Author/2020 - Album"

            // Единственный кандидат — починить можно.
            assertEquals(
                listOf(File(tmp, "Author/2020 - Album").absolutePath),
                s.rootFolderRepairCandidates(listOf(tmp.absolutePath)),
            )
            assertTrue(s.repairRootFolder(listOf(tmp.absolutePath)), "единственный кандидат должен приниматься")
            assertEquals(File(tmp, "Author/2020 - Album").absolutePath, s.rootFolder)
            verify(ps).executeUpdate()

            // Два кандианта (одинаковый хвост под двумя корнями) — починка запрещена (fail-closed):
            // старый код взял ПЕРВЫЙ и записал его в БД, то есть указывал на чужой каталог.
            val ps2 = mock(PreparedStatement::class.java)
            `when`(ps2.executeUpdate()).thenReturn(1)
            val database2 = FakeKaraokeConnection(jdbcConnection = mockConnection(ps2), name = "LOCAL")
            val s2 = song(database2)
            s2.id = 43L
            s2.fileName = "Test Song"
            s2.rootFolder = "/sm-karaoke/work/Author/2020 - Album"
            assertEquals(2, s2.rootFolderRepairCandidates(listOf(tmp.absolutePath, tmp2.absolutePath)).size)
            assertFalse(
                s2.repairRootFolder(listOf(tmp.absolutePath, tmp2.absolutePath)),
                "неоднозначный кандидат не должен приниматься",
            )
            assertEquals("/sm-karaoke/work/Author/2020 - Album", s2.rootFolder, "корень не изменён")
        } finally {
            tmp.deleteRecursively()
            tmp2.deleteRecursively()
        }
    }

    @Test
    fun `BR-073 кандидат без файла песни не принимается`() {
        val tmp = Files.createTempDirectory("s3-22-root-c").toFile()
        try {
            File(tmp, "Author/2020 - Album").apply { mkdirs() }
            val ps = mock(PreparedStatement::class.java)
            `when`(ps.executeUpdate()).thenReturn(1)
            val database = FakeKaraokeConnection(jdbcConnection = mockConnection(ps), name = "LOCAL")
            val s = song(database)
            s.id = 44L
            s.fileName = "Test Song"
            s.rootFolder = "/sm-karaoke/work/Author/2020 - Album"

            // Каталог существует, но файла песни в нём нет — это каталог другой песни.
            assertFalse(s.repairRootFolder(listOf(tmp.absolutePath)), "каталог без файла песни не должен приниматься")
            assertEquals("/sm-karaoke/work/Author/2020 - Album", s.rootFolder)
        } finally {
            tmp.deleteRecursively()
        }
    }

    // ================================================================== BR-074
    @Test
    fun `BR-074 геттеры картинок только читают, создание — отдельная операция`() {
        val src = songSource()
        val getters = src.substringAfter("val pictureAuthor: Pictures? get() =").substringBefore("fun ensurePictureAuthor()")
        assertFalse(getters.contains("createNewPicture"), "геттер картинки не должен создавать запись Pictures")
        assertFalse(getters.contains("saveToDb"), "геттер картинки не должен сохранять песню")
        assertFalse(getters.contains("Ready = true"), "геттер картинки не должен выставлять флаг готовности")
        assertTrue(src.contains("fun ensurePictureAuthor()"), "явная операция создания картинки обязана существовать")
        assertTrue(src.contains("fun ensurePictureAlbum()"), "явная операция создания обложки обязана существовать")
    }

    @Test
    fun `BR-074 нечитаемая картинка не роняет операцию и не ставит флаг готовности`() {
        val tmp = Files.createTempDirectory("s3-22-pic").toFile()
        try {
            File(tmp, "LogoAlbum.png").writeText("это не картинка")
            val database = FakeKaraokeConnection()
            val s = song(database)
            s.rootFolder = tmp.absolutePath
            s.fileName = "Test Song"

            // Старое поведение: `ImageIO.read(...)` возвращает null → `bi.width` → NPE.
            val bi = javax.imageio.ImageIO.read(File(tmp, "LogoAlbum.png").inputStream())
            assertNull(bi, "предусловие: файл не является изображением")
            assertThrows<NullPointerException> { bi.width }

            // Новое: операция возвращает null и НЕ выставляет флаг готовности.
            assertNull(s.ensurePictureAlbum(), "нечитаемая картинка — null, без исключения")
            assertFalse(s.pictureAlbumReady, "флаг готовности не выставляется, если объекта в хранилище нет")
        } finally {
            tmp.deleteRecursively()
        }
    }

    // ================================================================== BR-075
    @Test
    fun `BR-075 флаг готовности дописывается, а не перезаписывает чужой JSON`() {
        val s = song()
        val merged =
            s.mergeReadinessFlag(
                """{"stemVocalReady":true,"telegramAutoPublishState":"RUNNING"}""",
                "pictureAlbumReady",
                JsonPrimitive(true),
            )
        val parsed = Json.parseToJsonElement(merged).jsonObject
        assertEquals(true, parsed["stemVocalReady"]?.jsonPrimitive?.boolean)
        assertEquals("RUNNING", parsed["telegramAutoPublishState"]?.jsonPrimitive?.content)
        assertEquals(true, parsed["pictureAlbumReady"]?.jsonPrimitive?.boolean)

        // Формат хранения совпадает с прежним (компактный JSON без пробелов) — от него зависят
        // проверки в karaoke-web (`"pictureAlbumReady":true`).
        assertTrue(merged.contains("\"pictureAlbumReady\":true"), "формат флага не изменился: $merged")
    }

    @Test
    fun `BR-075 запись флага — compare-and-swap, diff поле не перезаписывает`() {
        val src = songSource()
        assertTrue(
            src.contains("AND COALESCE(player_readiness_flags, '{}') = ?"),
            "запись флага обязана быть compare-and-swap по значению колонки",
        )
        assertTrue(
            src.contains(
                """RecordDiff("player_readiness_flags", settA.playerReadinessFlags, settB.playerReadinessFlags, false)""",
            ),
            "diff-обновление не должно писать поле целиком",
        )
    }

    @Test
    fun `BR-075 флаг несохранённой песни остаётся в памяти и не ходит в БД`() {
        val database = FakeKaraokeConnection()
        val s = song(database)
        s.stemVocalReady = true
        assertTrue(s.stemVocalReady)
        assertEquals(0, database.getConnectionCalls.get(), "у несохранённой песни флаг не пишется в БД")
    }

    // ================================================================== BR-078
    @Test
    fun `BR-078 разбор beat-times не падает на нештатном файле`() {
        val s = song()

        // Старое поведение: `bodyBeattimes.substring(1, bodyBeattimes.length - 2)` на пустом
        // файле → StringIndexOutOfBoundsException; `it.toDouble()` → NumberFormatException.
        assertThrows<StringIndexOutOfBoundsException> { "".substring(1, "".length - 2) }
        assertThrows<NumberFormatException> { "abc".toDouble() }

        assertTrue(s.parseSheetsageBeattimes("").isEmpty(), "пустой файл — пустой список, без исключения")
        assertTrue(s.parseSheetsageBeattimes("[]").isEmpty(), "пустой список — пустой результат")
        val partial = s.parseSheetsageBeattimes("[0.0, мусор, 1.0]")
        assertTrue(partial.isNotEmpty(), "числовая часть разбирается даже при мусоре")
        assertEquals(0.0, partial.first(), 0.0001)
    }

    @Test
    fun `BR-078 разбор sheetsageInfo устойчив к неполным файлам`() {
        val tmp = Files.createTempDirectory("s3-22-sheetsage").toFile()
        try {
            val dir = File(tmp, "sheetsage").apply { mkdirs() }
            File(dir, "Test Song [beattimes].txt").writeText("[0.0, 0.5, 1.0]")
            File(dir, "Test Song [sheetsage].ly").writeText("\\key c \\major\n\\tempo = 120\n\\chordmode { C4*1 G4*1 }\n")

            val s = song(FakeKaraokeConnection())
            s.rootFolder = tmp.absolutePath
            s.fileName = "Test Song"

            val info = s.sheetsageInfo
            assertEquals("120", info.tempo, "темп разобран")
            assertTrue(info.key.startsWith("C"), "тональность разобрана: ${info.key}")
            assertTrue(info.beattimes.isNotEmpty(), "beat-times разобраны")
            assertTrue(info.chords.isNotEmpty(), "аккорды разобраны")

            // Битый .ly — пустой результат, без исключения.
            File(dir, "Test Song [sheetsage].ly").writeText("\\key\n\\tempo\n")
            val broken = s.sheetsageInfo
            assertEquals("", broken.tempo, "битый файл не роняет разбор")
            assertTrue(broken.chords.isEmpty())
        } finally {
            tmp.deleteRecursively()
        }
    }

    // ================================================================== BR-079
    @Test
    fun `BR-079 sheetsageInfo типизирован, а не Map с неявными кастами`() {
        val s = song()
        val empty: SheetsageInfo = s.sheetsageInfo
        assertTrue(empty.isEmpty, "пустой разбор — пустой результат")
        assertTrue(empty.toMap().isEmpty(), "пустой разбор — пустая карта для JSON-эндпоинта")
        assertEquals("", empty.tempo)
        assertEquals(emptyList<String>(), empty.chords)
        assertEquals(emptyList<Double>(), empty.beattimes)

        // Старое поведение: `map["tempo"] as String` при отсутствующем ключе → NPE
        // (элвис `?: ""` применяется к результату приведения, а не к значению в карте).
        val legacy: Map<String, Any> = emptyMap()
        assertThrows<NullPointerException> { legacy["tempo"] as String }

        val filled = SheetsageInfo(key = "C major", tempo = "120", chords = listOf("C"), beattimes = listOf(0.0))
        assertEquals("120", filled.toMap()["tempo"])
        assertEquals(listOf("C"), filled.toMap()["chords"])
    }

    @Test
    fun `BR-079 потребители читают типизированные поля без unchecked-кастов`() {
        val src = File("src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt").readText()
        assertFalse(
            src.contains("sheetsageInfo[\"tempo\"]") || src.contains("sheetsageInfo[\"key\"]"),
            "в ApiController не должно быть unchecked-кастов к sheetsageInfo",
        )
        val utils = File("src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt").readText()
        assertFalse(
            utils.contains("sheetsageInfo[\"tempo\"] as String"),
            "в Utils не должно быть unchecked-каста `as String` к sheetsageInfo",
        )
    }

    // ================================================================== BR-084
    @Test
    fun `BR-084 разбор флагов кэшируется и сбрасывается при записи поля`() {
        val s = song()
        s.playerReadinessFlags = """{"stemVocalReady":true}"""
        assertTrue(s.stemVocalReady)
        assertEquals(1, s.playerReadinessFlagsMap.size)

        // Новое значение поля — кэш обязан быть сброшен, иначе остались бы старые значения.
        s.playerReadinessFlags = """{"pictureAlbumReady":true}"""
        assertFalse(s.stemVocalReady, "старое значение не должно оставаться в кэше")
        assertTrue(s.pictureAlbumReady)
        assertEquals(1, s.playerReadinessFlagsMap.size)

        val src = songSource()
        assertTrue(src.contains("private fun readinessFlagsCacheOf(raw: String)"), "разбор флагов должен кэшироваться")
        assertTrue(
            src.contains("readinessFlagsCache = null"),
            "запись поля должна сбрасывать кэш разбора",
        )
    }

    @Test
    fun `BR-084 string-флаги читаются из того же кэша`() {
        val s = song()
        s.playerReadinessFlags = """{"telegramAutoPublishState":"RUNNING","telegramAutoPublishLastError":""}"""
        assertEquals("RUNNING", s.telegramAutoPublishState)
        assertEquals("", s.telegramAutoPublishLastError)
        s.playerReadinessFlags = """{"telegramAutoPublishState":"COMPLETE"}"""
        assertEquals("COMPLETE", s.telegramAutoPublishState, "кэш сброшен при записи поля")
    }

    // ================================================================== BR-103
    @Test
    fun `BR-103 голос добавленный редактором не исчезает при сохранении фонового объекта`() {
        val s = song()
        val base = """["text0"]""" to voicesJson(0.0)
        // Фоновый объект ничего не менял — mine == base.
        val mine = base
        // Редактор добавил второй голос.
        val theirs = """["text0","text1"]""" to voicesJson(0.0, 1.0)

        val merged = s.mergeVoiceFields(base = base, mine = mine, theirs = theirs)
        val texts = parseVoiceTexts(merged.first)
        val markers = parseVoiceMarkers(merged.second)
        assertNotNull(texts)
        assertNotNull(markers)
        assertEquals(2, texts!!.size, "голос, добавленный редактором, должен сохраниться")
        assertEquals(2, markers!!.size)
        assertEquals("text1", texts[1])
    }

    @Test
    fun `BR-103 изменённый этим объектом голос приоритетнее версии из БД`() {
        val s = song()
        val base = """["text0","text1"]""" to voicesJson(0.0, 1.0)
        // Фоновый объект изменил ВТОРОЙ голос.
        val mine = """["text0","text1-исправлено"]""" to voicesJson(0.0, 1.0)
        val theirs = """["text0","text1"]""" to voicesJson(0.0, 1.0)

        val merged = s.mergeVoiceFields(base = base, mine = mine, theirs = theirs)
        val texts = parseVoiceTexts(merged.first)
        assertNotNull(texts)
        assertEquals("text1-исправлено", texts!![1], "намерение сохраняющего объекта приоритетно")
        assertEquals("text0", texts[0], "не тронутый голос остаётся")
    }

    @Test
    fun `BR-103 удалённый этим объектом голос не восстанавливается из БД`() {
        val s = song()
        val base = """["text0","text1"]""" to voicesJson(0.0, 1.0)
        val mine = """["text0"]""" to voicesJson(0.0)
        val theirs = """["text0","text1"]""" to voicesJson(0.0, 1.0)

        val merged = s.mergeVoiceFields(base = base, mine = mine, theirs = theirs)
        val texts = parseVoiceTexts(merged.first)
        assertNotNull(texts)
        assertEquals(1, texts!!.size, "удаление голоса сохраняющим объектом — приоритетно")
    }

    @Test
    fun `BR-103 слияние голосов выполняется до вычисления diff`() {
        val src = songSource()
        val saveToDb = src.substringAfter("fun saveToDb(): Boolean {").substringBefore("fun saveToDbLocked()")
        val mergeAt = saveToDb.indexOf("applyVoiceMerge(savedSong, voiceFieldsBeforeSave)")
        val diffAt = saveToDb.indexOf("getDiff(this, savedSong)")
        assertTrue(mergeAt > 0, "saveToDb обязан вызывать applyVoiceMerge")
        assertTrue(diffAt > 0, "saveToDb обязан считать diff")
        assertTrue(mergeAt < diffAt, "слияние голосов должно выполняться ДО вычисления diff")
    }

    // ================================================================== BR-105
    @Test
    fun `BR-105 число голосов не подменяется заглушкой`() {
        val s = song()
        // Старое поведение: поле маркеров не загружено → заглушка "[[]]" → countVoices == 1
        // у ВСЕХ песен списка (фильтр «Количество голосов» показывал всё при 1 и пусто при 2+).
        assertEquals(1, s.countVoices, "реплика старого поведения: заглушка даёт один голос")
        assertNull(s.countVoicesLoaded, "незагруженные маркеры — число голосов НЕИЗВЕСТНО, а не 1")

        s.sourceMarkers = voicesJson(0.0, 1.0)
        assertEquals(2, s.countVoicesLoaded, "число голосов считается по реальным маркерам")
    }

    @Test
    fun `BR-105 фильтр по числу голосов грузит маркеры`() {
        val src = songSource()
        assertTrue(
            src.contains("""args.containsKey("filter_count_voices")"""),
            "фильтр по числу голосов должен принудительно грузить source_markers",
        )
        assertTrue(
            src.contains("song.countVoicesLoaded?.toString()"),
            "фильтр должен сравнивать с реальным счётчиком голосов",
        )
    }

    // ================================================================== BR-106
    @Test
    fun `BR-106 при ошибке DELETE файлы песни не удаляются`() {
        val tmp = Files.createTempDirectory("s3-22-del").toFile()
        try {
            val audio = File(tmp, "Test Song.flac").apply { writeText("audio") }
            val ps = mock(PreparedStatement::class.java)
            `when`(ps.executeUpdate()).thenThrow(SQLException("boom"))
            val database = FakeKaraokeConnection(jdbcConnection = mockConnection(ps))
            val s = song(database)
            s.rootFolder = tmp.absolutePath
            s.fileName = "Test Song"

            // Старое поведение: файлы удалялись ДО строки БД — при ошибке DELETE строка оставалась,
            // а аудиофайл уже исчезал (песня с «битым» звуком).
            s.deleteFromDb(withFiles = true)

            assertTrue(audio.exists(), "при ошибке DELETE файл песни НЕ должен удаляться")
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `BR-106 удаление файлов идёт после удаления строки и без deleteOnExit`() {
        val src = songSource()
        val body = src.substringAfter("fun deleteFromDb(\n        withFiles: Boolean = true,").substringBefore("val messageRecordDelete =")
        assertFalse(body.contains("deleteOnExit"), "deleteOnExit запрещён: файлы остаются на диске до перезапуска JVM")
        val deleteAt = body.indexOf("DELETE FROM tbl_songs")
        val fileAt = body.indexOf("file.delete()")
        assertTrue(deleteAt > 0 && fileAt > 0, "в deleteFromDb должны быть и DELETE, и удаление файлов")
        assertTrue(deleteAt < fileAt, "сначала строка БД, потом файлы")
    }

    // ================================================================== BR-107
    @Test
    fun `BR-107 ошибка INSERT не проглатывается`() {
        val ps = mock(PreparedStatement::class.java)
        `when`(ps.executeUpdate(anyString(), anyInt())).thenThrow(SQLException("null value in column violates not-null constraint"))
        val database = FakeKaraokeConnection(jdbcConnection = mockConnection(ps))
        val s = song(database)
        s.fields[SongField.NAME] = "Test Song"

        // Старое поведение: `catch (_: Exception)` → «починка» сиквенса → null → вызывающий
        // продолжал работать с несохранённой песней (id = 0) и ставил задания с songId = 0.
        val thrown =
            assertThrows<SQLException> {
                Song.createDbInstance(s, database)
            }
        assertTrue(thrown.message!!.contains("not-null"), "исходная причина ошибки INSERT не потеряна: ${thrown.message}")
    }

    @Test
    fun `BR-107 saveToDb сообщает о неудачной вставке`() {
        val src = songSource()
        val saveToDb = src.substringAfter("fun saveToDb(): Boolean {").substringBefore("fun saveToDbLocked()")
        assertTrue(
            saveToDb.contains("song.insert.failed"),
            "saveToDb обязан реагировать на неудачный INSERT, а не возвращать true",
        )
        assertTrue(
            saveToDb.contains("val created ="),
            "результат createDbInstance не должен игнорироваться",
        )
    }
}
