package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Base64
import java.nio.file.Files

/**
 * Офлайн-тесты сохранения/чтения файла настроек (Pass 468): кодирование формата,
 * атомарная запись с резервной копией и поведение на битом файле.
 *
 * Файл настроек — единственное место, где живут ~150 параметров, включая
 * выставленные оператором, поэтому потеря или молчаливый откат к дефолтам здесь
 * дороже, чем в большинстве мест проекта.
 *
 * Реальный файл (`/sm-karaoke/system/Karaoke.properties`) тесты НЕ трогают:
 * работают во временном каталоге, а формат сверяется на настоящей строке из прода,
 * вшитой в тест.
 */
internal class KaraokePropertiesPersistenceTest {
    private fun tempDir(): File = Files.createTempDirectory("karaoke-props-test").toFile()

    /** Base64-строка файла настроек → исходный JSON-текст. */
    private fun base64Decoded(line: String): String {
        val decoder = Base64.getDecoder()
        val bytes = decoder.decode(line)
        return String(bytes)
    }

    private fun decoded(line: String): KaraokePropertySerializable =
        kotlinx.serialization.json.Json.decodeFromString(
            KaraokePropertySerializable.serializer(),
            base64Decoded(line),
        )

    @Test
    fun `encode и decode — обратимый round-trip по всем поддерживаемым типам`() {
        val original =
            mapOf<String, Any>(
                "checkSearchAsync" to false, // Boolean
                "lyricsSearchMinResults" to 2, // Int
                "requestNewSongTimeoutMs" to 600_000L, // Long
                "lyricsSearchScrapers" to "google_cse;brave", // String
            )
        val restored = decodePropertiesMap(encodePropertiesMap(original))
        for ((key, value) in original) {
            assertEquals(value, restored[key], "значение $key не пережило round-trip")
        }
    }

    @Test
    fun `формат — одна строка на параметр, каждая строка это Base64 от JSON`() {
        val encoded = encodePropertiesMap(mapOf("lyricsSearchMinResults" to 2, "checkSearchAsync" to false))
        val lines = encoded.split("\n")
        assertEquals(2, lines.size, "на каждый параметр должна быть ровно одна строка")
        for (line in lines) {
            // Проверять «нет '='» нельзя: '=' — это паддинг Base64. Смысл проверки в
            // другом: строка должна быть Base64 от JSON, а не парой key=value.
            val json = base64Decoded(line)
            assertTrue(json.startsWith("{"), "строка должна быть Base64 от JSON-объекта: $json")
            assertTrue(json.contains("\"key\""), "в JSON должен быть ключ: $json")
            assertTrue(json.contains("\"serializableValue\""), "в JSON должно быть serializableValue: $json")
        }
    }

    @Test
    fun `реальная строка из прод-файла разбирается`() {
        // Первая строка живого /sm-karaoke/system/Karaoke.properties (значение 600000).
        // Тест фиксирует совместимость с форматом, который уже лежит на проде, а не
        // только сам с собой.
        val realLine = "eyJrZXkiOiJyZXF1ZXN0TmV3U29uZ1RpbWVvdXRNcyIsInNlcmlhbGl6YWJsZVZhbHVlIjoiTmpBd01EQXcifQ=="
        val parsed = decoded(realLine)
        assertEquals("requestNewSongTimeoutMs", parsed.key)
        assertEquals("NjAwMDAw", parsed.serializableValue)
        assertEquals(600_000L, parsed.value())
    }

    @Test
    fun `атомарная запись кладёт содержимое и снимает копию предыдущей версии`() {
        val dir = tempDir()
        val target = File(dir, "Karaoke.properties")

        writePropertiesFileAtomic(target, "первая версия")
        assertEquals("первая версия", target.readText())
        val bak = backupFileFor(target)
        assertFalse(bak.exists(), "копировать нечего, если файла ещё не было")

        writePropertiesFileAtomic(target, "вторая версия")
        assertEquals("вторая версия", target.readText())
        assertTrue(bak.exists(), "перед заменой должна появиться резервная копия")
        assertEquals("первая версия", bak.readText(), "в .bak должна лежать ПРЕДЫДУЩАЯ версия")
    }

    @Test
    fun `атомарная запись не оставляет временный файл`() {
        val dir = tempDir()
        val target = File(dir, "Karaoke.properties")
        writePropertiesFileAtomic(target, "раз")
        writePropertiesFileAtomic(target, "два")
        val leftovers = dir.listFiles { f -> f.name.endsWith(".tmp") } ?: emptyArray()
        assertEquals(0, leftovers.size, "временные файлы не должны накапливаться: ${leftovers.map { it.name }}")
    }

    @Test
    fun `readPropertiesFile возвращает null для отсутствующего и для битого файла`() {
        val dir = tempDir()
        assertNull(readPropertiesFile(File(dir, "нет-такого.properties")))

        val broken = File(dir, "broken.properties")
        broken.writeText("это не Base64 и не JSON")
        assertNull(readPropertiesFile(broken), "битый файл не должен бросать исключение наружу")
    }

    @Test
    fun `битый основной файл — значения восстанавливаются из резервной копии`() {
        // Ровно тот сценарий, ради которого добавлены .bak и громкая диагностика:
        // до Pass 468 исключение разбора молча проглатывалось, все настройки
        // откатывались к дефолтам и затем перезаписывались ими же.
        val dir = tempDir()
        val target = File(dir, "Karaoke.properties")
        val content = encodePropertiesMap(mapOf("lyricsSearchMinResults" to 7))
        writePropertiesFileAtomic(target, content) // первая запись — .bak ещё нет
        writePropertiesFileAtomic(target, content) // вторая — теперь .bak = предыдущая версия
        target.writeText("мусор вместо настроек")

        assertNull(readPropertiesFile(target), "основной файл не разбирается")
        val fromBackup = readPropertiesFile(backupFileFor(target))
        assertEquals(7, fromBackup?.get("lyricsSearchMinResults"), "значения должны подняться из .bak")
    }

    @Test
    fun `backupFileFor — сосед основного файла с суффиксом bak`() {
        assertEquals(
            File("/tmp/dir/Karaoke.properties.bak"),
            backupFileFor(File("/tmp/dir/Karaoke.properties")),
        )
    }
}
