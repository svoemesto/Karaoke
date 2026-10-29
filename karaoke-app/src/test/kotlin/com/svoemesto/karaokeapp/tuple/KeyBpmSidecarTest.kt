package com.svoemesto.karaokeapp.tuple

import com.svoemesto.karaokeapp.audit.TestRepoRoot
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * 674 (FR-012/FR-013): sidecar-отпечаток источника для key/bpm.
 *
 * @see specs/674-kortege-execution/tasks.md T026
 */
class KeyBpmSidecarTest {
    @TempDir
    lateinit var dir: Path

    private fun writeFile(
        name: String,
        size: Int,
    ): Path {
        val path = dir.resolve(name)
        Files.write(path, ByteArray(size) { (it % 251).toByte() })
        return path
    }

    @Test
    fun `sidecar совпадает с источником и переживает roundtrip`() {
        val source = writeFile("song.flac", 200_000)
        val keyJson = dir.resolve("song [key].json")
        val sidecar = KeyBpmSidecar.forSource(source.toString(), 275_044L, 42L)

        assertTrue(sidecar.matches(source.toString(), 275_044L))
        assertTrue(KeyBpmSidecar.write(keyJson.toString(), sidecar))
        val read = KeyBpmSidecar.read(keyJson.toString())
        assertNotNull(read)
        assertTrue(read!!.matches(source.toString(), 275_044L))
    }

    @Test
    fun `изменённый источник или другая длительность не проходят проверку`() {
        val source = writeFile("song.flac", 200_000)
        val sidecar = KeyBpmSidecar.forSource(source.toString(), 275_044L, 42L)

        assertFalse(sidecar.matches(source.toString(), 214_949L), "другая длительность — чужой файл")

        Files.write(source, ByteArray(10_000) { 7 })
        assertFalse(sidecar.matches(source.toString(), 275_044L), "изменённый размер — не тот источник")
    }

    @Test
    fun `нет sidecar — значение не применяется`() {
        val keyJson = dir.resolve("song [key].json")
        Files.writeString(keyJson, """{"bpm":103,"key":"D minor"}""")
        assertTrue(KeyBpmSidecar.read(keyJson.toString()) == null)
    }

    @Test
    fun `реестр синхронизации не содержит tbl_processes`() {
        val registry = TestRepoRoot.file("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt").readText()
        assertFalse(registry.contains("tbl_processes"), "FR-017: очередь не синхронизируется между БД")
        assertTrue(registry.contains("object SyncRegistry"))
        assertTrue(TestRepoRoot.file("deploy/recordhash_processes.sql").exists())
    }
}
