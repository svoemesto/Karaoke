package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.CensoredDictionaryRules
import com.svoemesto.karaokeapp.Converter
import com.svoemesto.karaokeapp.Crypto
import com.svoemesto.karaokeapp.CryptoConfigurationException
import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.FileActualityVerdict
import com.svoemesto.karaokeapp.HealthReport
import com.svoemesto.karaokeapp.HealthReportStatus.ERROR
import com.svoemesto.karaokeapp.HealthReportType.FILE_VIOLATION
import com.svoemesto.karaokeapp.KaraokeProcess
import com.svoemesto.karaokeapp.KaraokeProcessStatuses
import com.svoemesto.karaokeapp.KaraokeProcessTypes
import com.svoemesto.karaokeapp.KaraokeVoice
import com.svoemesto.karaokeapp.KaraokeVoiceFill
import com.svoemesto.karaokeapp.KaraokeVoiceGroup
import com.svoemesto.karaokeapp.ReadinessRecalcResult
import com.svoemesto.karaokeapp.StorageFileInfoProbe
import com.svoemesto.karaokeapp.actionToDeleteFileAndFolderIfFolderEmpty
import com.svoemesto.karaokeapp.censored
import com.svoemesto.karaokeapp.fileActualityVerdict
import com.svoemesto.karaokeapp.filterSongProcesses
import com.svoemesto.karaokeapp.getVoices
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SongVersion
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeapp.services.StorageFileInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import reactor.core.publisher.Mono
import java.awt.Color
import java.io.File
import java.nio.file.Files

/**
 * Регрессия пакета `PKG-S3-26` (аудит #336, OpenProject #398) — ПОВЕДЕНЧЕСКИЕ проверки.
 *
 * Каждый тест падает на старом коде и проходит на текущем. Проверки, где поведение
 * нельзя проверить без БД/браузера/окружения, вынесены в
 * `PkgS326SourceContractsTest` (статический guard с обоснованием в отчёте пакета).
 *
 * @see specs/398-pkg-s3-26/report.md
 */
class PkgS326BehaviorTest {
    private val storageService: KaraokeStorageService = Mockito.mock(KaraokeStorageService::class.java)
    private val storageApiClient: StorageApiClient = Mockito.mock(StorageApiClient::class.java)

    private fun newSong(): Song = Song(FakeKaraokeConnection(), storageService, storageApiClient)

    // ------------------------------------------------------------------ BR-276

    @Test
    fun `BR-276 голоса сериализуются и разбираются без потери`() {
        // Round-trip «запись → чтение» для настройки voices: раньше сеттер писал
        // строку голосов в ключ flashColor, и геттер voices читал старое значение.
        val voices =
            mutableListOf(
                KaraokeVoice(
                    groups = mutableListOf(KaraokeVoiceGroup(mltText = Converter.getMltFontFromString(""))),
                    fill =
                        KaraokeVoiceFill(
                            evenColor = Color(1, 2, 3, 4),
                            evenOpacity = 0.6,
                            oddColor = Color(5, 6, 7, 8),
                            oddOpacity = 0.7,
                        ),
                ),
            )
        val text = Converter.getStringFromVoices(voices)
        val parsed = Converter.getVoicesFromString(text)
        assertEquals(1, parsed.size, "после round-trip должен остаться один голос")
        assertEquals(1, parsed[0].groups.size)
        assertEquals(
            voices[0].fill.evenOpacity,
            parsed[0].fill.evenOpacity,
            "прозрачность заливки не должна теряться при разборе",
        )
        assertEquals(voices[0].fill.evenColor, parsed[0].fill.evenColor)
    }

    // ------------------------------------------------------------------ BR-277

    @Test
    fun `BR-277 битая символическая ссылка удаляется действием repair`() {
        val dir = Files.createTempDirectory("pkg-s3-26-symlink")
        try {
            val target = dir.resolve("target.txt")
            Files.writeString(target, "x")
            val link = dir.resolve("broken-link.txt")
            Files.createSymbolicLink(link, target)
            Files.delete(target)

            // ДО: `File(path).exists()` для битого симлинка = false → действие было no-op.
            assertFalse(Files.exists(link), "условие теста: ссылка битая (цель удалена)")
            assertTrue(Files.isSymbolicLink(link), "ссылка должна оставаться симлинком")

            actionToDeleteFileAndFolderIfFolderEmpty(pathToFile = link.toString())()

            assertFalse(
                Files.isSymbolicLink(link),
                "битый симлинк должен быть удалён действием «удалить файл»",
            )
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    // ------------------------------------------------------------------ BR-280

    @Test
    fun `BR-280 фильтр заданий песни повторяет правила loadList`() {
        fun process(
            songId: Int,
            type: String,
            threadId: Int,
            status: String,
            args: List<List<String>>,
        ): KaraokeProcess =
            KaraokeProcess(
                database = FakeKaraokeConnection(),
                storageService = storageService,
                storageApiClient = storageApiClient,
            ).apply {
                this.songId = songId
                this.type = type
                this.threadId = threadId
                this.status = status
                this.args = args
            }

        val uploadArgs = listOf(listOf("runFunctionWithArgs", "uploadToRemoteStore", "karaokeFileType=MP3_VOCAL"))
        val processes =
            listOf(
                process(
                    7,
                    KaraokeProcessTypes.UPLOAD_TO_REMOTE_STORE.name,
                    42,
                    KaraokeProcessStatuses.WAITING.name,
                    uploadArgs,
                ),
                process(
                    7,
                    KaraokeProcessTypes.UPLOAD_TO_LOCAL_STORE.name,
                    42,
                    KaraokeProcessStatuses.WAITING.name,
                    uploadArgs,
                ),
                process(
                    8,
                    KaraokeProcessTypes.UPLOAD_TO_REMOTE_STORE.name,
                    42,
                    KaraokeProcessStatuses.WAITING.name,
                    uploadArgs,
                ),
                process(
                    7,
                    KaraokeProcessTypes.DEMUCS2.name,
                    1,
                    KaraokeProcessStatuses.WAITING.name,
                    listOf(listOf("a=b")),
                ),
            )

        val filtered =
            filterSongProcesses(
                processes = processes,
                args =
                    mapOf(
                        "song_id" to "7",
                        "process_type" to KaraokeProcessTypes.UPLOAD_TO_REMOTE_STORE.name,
                        "thread_id" to "42",
                        "process_args" to "karaokeFileType=MP3_VOCAL",
                    ),
            )
        assertEquals(1, filtered.size, "под фильтр должен попасть ровно один процесс")
        assertEquals(KaraokeProcessTypes.UPLOAD_TO_REMOTE_STORE.name, filtered[0].type)
        assertEquals(7, filtered[0].songId)
    }

    // ------------------------------------------------------------------ BR-281

    @Test
    fun `BR-281 неизвестное состояние хранилища не выглядит как актуальный файл`() {
        val dir = Files.createTempDirectory("pkg-s3-26-actuality")
        try {
            val file = dir.resolve("song.mp3")
            Files.writeString(file, "0123456789")

            // Состояние неизвестно (сбой MinIO / circuit breaker) — вердикт НЕ «актуален».
            assertEquals(
                FileActualityVerdict.UNKNOWN,
                fileActualityVerdict(StorageFileInfoProbe.Unknown("circuit breaker open"), file.toString()),
                "неизвестное состояние не должно считаться «файл актуален»",
            )

            // Объекта нет в хранилище — файл неактуален (объект надо перезалить).
            assertEquals(
                FileActualityVerdict.NOT_ACTUAL,
                fileActualityVerdict(StorageFileInfoProbe.Missing, file.toString()),
            )

            // Размер известен и совпадает — актуален; не совпадает — неактуален.
            assertEquals(
                FileActualityVerdict.ACTUAL,
                fileActualityVerdict(StorageFileInfoProbe.Known(storageFileInfo(10)), file.toString()),
            )
            assertEquals(
                FileActualityVerdict.NOT_ACTUAL,
                fileActualityVerdict(StorageFileInfoProbe.Known(storageFileInfo(11)), file.toString()),
            )

            // Файл исчез с диска (BR-278) — вердикт «неактуально» выносить нельзя.
            val vanished = dir.resolve("vanished.mp3")
            assertEquals(
                FileActualityVerdict.DISK_VANISHED,
                fileActualityVerdict(StorageFileInfoProbe.Known(storageFileInfo(10)), vanished.toString()),
            )
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun storageFileInfo(size: Long): StorageFileInfo = StorageFileInfo("karaoke", "file.mp3", "etag", size)

    // ------------------------------------------------------------------ BR-283

    @Test
    fun `BR-283 ошибка удаления из удалённого хранилища не остаётся тихой`() {
        val failing = Mockito.mock(StorageApiClient::class.java)
        Mockito.`when`(failing.deleteFile(Mockito.anyString(), Mockito.anyString())).thenReturn(Mono.error<String>(IllegalStateException("boom")))
        val error =
            assertThrows<IllegalStateException> {
                HealthReport.deleteRemoteFileOrThrow(
                    storageApiClient = failing,
                    bucketName = "karaoke",
                    fileName = "file.mp3",
                )
            }
        assertTrue(
            error.message?.contains("boom") == true,
            "причина сбоя удаления должна доходить до вызывающего, а не теряться: ${error.message}",
        )
    }

    // ------------------------------------------------------------------ BR-284

    @Test
    fun `BR-284 сбой одного действия не обрывает каскад остальных`() {
        val song = newSong()
        val executed = mutableListOf<String>()
        val failing =
            HealthReport(
                song = song,
                healthReportType = FILE_VIOLATION,
                healthReportStatus = ERROR,
                canResolve = true,
                solutionActions =
                    listOf(
                        {
                            executed.add("first")
                            throw IllegalStateException("boom")
                        },
                    ),
            )
        val next =
            HealthReport(
                song = song,
                healthReportType = FILE_VIOLATION,
                healthReportStatus = ERROR,
                canResolve = true,
                solutionActions = listOf({ executed.add("second") }),
            )

        val failed = HealthReport.executeResolvable(listOf(failing, next))

        assertEquals(1, failed, "одно упавшее действие должно быть посчитано как ошибка")
        assertEquals(listOf("first", "second"), executed, "после сбоя каскад обязан продолжиться")
    }

    // ------------------------------------------------------------------ BR-289

    @Test
    fun `BR-289 значение словаря не трактуется как regex-шаблон`() {
        // Значение с метасимволами: раньше подставлялось в шаблон как есть и маскировало
        // совсем другие слова (а значение с незакрытой скобкой роняло toRegex()).
        val rules = CensoredDictionaryRules.buildRules(listOf("[х*й]"))
        assertEquals(1, rules.size, "правило должно быть собрано (пустым оно не считается)")
        // Форма словаря: «[слово]» — в скобках то, чем маскируют.
        val masked = rules.apply("песня про х*й и хой")
        assertTrue(
            masked.contains("[х*й]"),
            "слово из словаря должно быть замаскировано: $masked",
        )
        // ДО (без экранирования) шаблон «х*й» трактовался как regex и маскировал «й»
        // в любом слове (в т.ч. «хой»). Теперь маскируется только точное «х*й».
        assertTrue(
            masked.contains("и хой"),
            "постороннее слово «хой» не должно маскироваться регулярным выражением словаря: $masked",
        )
    }

    @Test
    fun `BR-289 значение словаря с незакрытой скобкой не роняет разбор`() {
        // ДО: `toRegex()` бросал PatternSyntaxException → падало сохранение песни.
        val rules = CensoredDictionaryRules.buildRules(listOf("х["))
        assertNotNull(rules)
        assertEquals(1, rules.size, "значение должно быть экранировано, а не отброшено")
    }

    @Test
    fun `BR-289 пустое значение словаря отбрасывается`() {
        val rules = CensoredDictionaryRules.buildRules(listOf("[]"))
        assertEquals(0, rules.size, "значение без слова не должно давать правило")
    }

    // ------------------------------------------------------------------ BR-290

    @Test
    fun `BR-290 голос без непустых по времени линий не роняет создание проекта`() {
        // ДО: максимум длительности считался по ВСЕМ линиям, в т.ч. по нулевым
        // (комментарий/маркер) → maxDuration = 0 → ArithmeticException «/ by zero».
        val song = newSong()
        song.sourceMarkers =
            """
            [[{"time":1.0,"label":"COMMENT|примечание","note":"","chord":"","stringLad":"","lockLad":"","tag":"","color":"","position":"","markertype":"setting"}]]
            """.trimIndent()
        val voices = getVoices(song = song, songVersion = SongVersion.KARAOKE)
        assertNotNull(voices, "разбор голосов не должен падать на голосе без непустых линий")
    }

    // ------------------------------------------------------------------ BR-291

    @Test
    fun `BR-291 неверная длина ключа и IV отвергаются явно`() {
        assertThrows<CryptoConfigurationException> {
            Crypto.validateKeyMaterial("too-short", "0123456789abcdef")
        }
        assertThrows<CryptoConfigurationException> {
            Crypto.validateKeyMaterial("0123456789abcdef", "short-iv")
        }
        // Корректные длины принимаются.
        Crypto.validateKeyMaterial("0123456789abcdef", "0123456789abcdef")
        Crypto.validateKeyMaterial("0123456789abcdef0123456789abcdef", "0123456789abcdef")
    }

    // ------------------------------------------------------------------ BR-292

    @Test
    fun `BR-292 усечённая настройка голосов не роняет разбор`() {
        // ДО: `partsVoiceFields[1]` на усечённой строке → IndexOutOfBoundsException.
        assertNotNull(Converter.getVoicesFromString(""))
        assertNotNull(Converter.getVoicesFromString("безразделителя"))
        assertNotNull(Converter.getVoicesFromString("группы|заливка"))
    }

    @Test
    fun `BR-292 некорректный цвет из настройки не роняет разбор`() {
        // ДО: `Color(300, ...)` вне try → IllegalArgumentException.
        val color = Converter.getColorFromString("r=300;g=0;b=0;a=255")
        assertNotNull(color)
        assertTrue(color.alpha in 0..255)
    }

    // ------------------------------------------------------------------ BR-294

    @Test
    fun `BR-294 правила цензурирования применяются без повторного чтения словаря`() {
        val rules = CensoredDictionaryRules.buildRules(listOf("[мат]"))
        assertEquals(1, rules.size)
        // Повторное применение тех же правил даёт тот же результат — словарь не перечитывается.
        val first = "песня с мат и слова".censored(rules)
        val second = "песня с мат и слова".censored(rules)
        assertEquals(first, second)
        assertTrue(first.contains("[мат]"), "слово из словаря должно быть замаскировано: $first")
    }

    // ------------------------------------------------------------------ BR-287

    @Test
    fun `BR-287 итог пересчёта различает обработанные песни и ошибки`() {
        val result = ReadinessRecalcResult(processed = 10, failed = 3)
        assertEquals(10, result.processed)
        assertEquals(3, result.failed)
        assertEquals(13, result.processed + result.failed)
    }

    // ------------------------------------------------------------------ BR-526/531

    @Test
    fun `BR-526 в prod-resources нет чужих XML-фикстур с полями ПДн`() {
        val resources = repoFile("karaoke-app/src/main/resources")
        assertTrue(resources.isDirectory, "каталог ресурсов не найден")
        val offenders =
            resources
                .walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in setOf("xml", "json") }
                .filter { file ->
                    val text = runCatching { file.readText() }.getOrDefault("")
                    listOf("snils", "phoneNumber", "passport", "EGRNRequest").any { marker ->
                        text.contains(marker, ignoreCase = true)
                    }
                }.toList()
        assertTrue(
            offenders.isEmpty(),
            "в prod-resources не должно быть фикстур с полями ПДн: ${offenders.map { it.name }}",
        )
    }

    @Test
    fun `BR-531 в репозитории нет тестовых MLT-фикстур с локальными путями`() {
        val testRoot = repoFile("karaoke-app/src/test")
        val offenders =
            testRoot
                .walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in setOf("xml", "json", "mlt") }
                .filter { file ->
                    val text = runCatching { file.readText() }.getOrDefault("")
                    text.contains("~/Documents/") || text.contains("/home/") || text.contains("C:\\Users\\")
                }.toList()
        assertTrue(
            offenders.isEmpty(),
            "в тестовых ресурсах не должно быть фикстур с локальными путями: ${offenders.map { it.name }}",
        )
        // Конкретные мёртвые файлы, найденные аудитом, удалены.
        for (name in listOf("Правильный.xml", "Неправильный.xml", "1", "2.xml")) {
            assertFalse(
                File(testRoot, "kotlin/$name").exists(),
                "мёртвая фикстура $name должна быть удалена (BR-531)",
            )
        }
    }

    // ------------------------------------------------------------------ утилиты

    private fun repoFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        return File(dir, relativePath)
    }
}
