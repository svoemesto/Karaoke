package com.svoemesto.karaokeapp.tuple

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/**
 * Sidecar-отпечаток источника для `<file> [key].json`.
 *
 * Позволяет отличить «key/bpm посчитаны из файла этой песни» от чужого/старого файла без
 * повторного запуска keybpmfinder: дешёвый хэш первых/последних 64 КиБ + размер + длительность.
 *
 * @see specs/674-kortege-execution/contracts/artifact-verification.md §5
 */
@Serializable
data class KeyBpmSidecar(
    val schema: Int = 1,
    val scopeId: Long = 0,
    val sourcePath: String = "",
    val sourceSize: Long = 0,
    val sourceDurationMs: Long = 0,
    val sourceSha256HeadTail: String = "",
    val computedAt: String = "",
) {
    fun matches(
        sourcePath: String,
        sourceDurationMs: Long?,
    ): Boolean {
        val file = File(sourcePath)
        if (!file.exists() || file.length() != sourceSize) return false
        if (sourceDurationMs == null || sourceDurationMs <= 0) return false
        if (sourceDurationMs != this.sourceDurationMs) return false
        return sourceSha256HeadTail(sourcePath) == sourceSha256HeadTail
    }

    companion object {
        private const val CHUNK = 64 * 1024
        private val json = Json { ignoreUnknownKeys = true }

        /** Имя sidecar рядом с `[key].json`. */
        fun sidecarPath(keyJsonPath: String): File = File("$keyJsonPath.meta")

        fun forSource(
            sourcePath: String,
            sourceDurationMs: Long,
            scopeId: Long,
        ): KeyBpmSidecar {
            val file = File(sourcePath)
            return KeyBpmSidecar(
                scopeId = scopeId,
                sourcePath = sourcePath,
                sourceSize = if (file.exists()) file.length() else 0,
                sourceDurationMs = sourceDurationMs,
                sourceSha256HeadTail = sourceSha256HeadTail(sourcePath),
                computedAt = Instant.now().toString(),
            )
        }

        fun write(
            keyJsonPath: String,
            sidecar: KeyBpmSidecar,
        ): Boolean =
            try {
                sidecarPath(keyJsonPath).writeText(json.encodeToString(KeyBpmSidecar.serializer(), sidecar))
                true
            } catch (e: Exception) {
                false
            }

        fun read(keyJsonPath: String): KeyBpmSidecar? =
            try {
                val file = sidecarPath(keyJsonPath)
                if (!file.exists()) null else json.decodeFromString(KeyBpmSidecar.serializer(), file.readText())
            } catch (e: Exception) {
                null
            }

        /** Хэш конкатенации первых и последних [CHUNK] байт файла (не полный хэш — дешёвая проверка). */
        fun sourceSha256HeadTail(sourcePath: String): String {
            val file = File(sourcePath)
            if (!file.exists()) return ""
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { stream ->
                    val head = ByteArray(CHUNK)
                    val read = stream.read(head)
                    if (read > 0) digest.update(head, 0, read)
                }
                val length = file.length()
                if (length > CHUNK) {
                    java.io.RandomAccessFile(file, "r").use { raf ->
                        val start = (length - CHUNK).coerceAtLeast(0)
                        raf.seek(start)
                        val tail = ByteArray((length - start).toInt())
                        raf.readFully(tail)
                        digest.update(tail)
                    }
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                ""
            }
        }
    }
}
