package com.svoemesto.karaokeapp.textfilehistory

import com.svoemesto.karaokeapp.COUNT_HISTORY_LINES
import com.svoemesto.karaokeapp.setPublishedFilePermissions
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * Класс History Map.
 *
 * @see AGENTS.md
 */
@Serializable
data class HistoryMap(
    val args: Map<String, String>,
)

/**
 * Интерфейс для text file history.
 *
 * BR-056 (аудит #336, пакет PKG-S3-22): файл истории — общий ресурс, а обращение к нему
 * раньше было «read-modify-write без блокировки»:
 * - два параллельных запроса (`/api/songs`, `/api/songsdigests` — автообновление таблицы,
 *   две вкладки) читали одну историю и писали её наперегонки, последний писатель затирал
 *   запись другого;
 * - `save()` писал `writeText` напрямую, поэтому параллельное чтение могло увидеть
 *   обрезанный файл, а `loadList()` на **любом** исключении возвращал пустой список —
 *   следующее сохранение записывало историю из одной строки.
 *
 * Теперь: общий `ReentrantLock` на путь файла (внутрипроцессная сериализация),
 * `load-modify-save` под этим же локом, атомарная запись через временный файл
 * (`Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`), а ошибка разбора не «обнуляет» историю
 * молча — повреждённый файл копируется рядом как `*.corrupt-<timestamp>`, в лог идёт WARN.
 *
 * @see AGENTS.md
 * @see specs/394-pkg-s3-22/report.md
 */
interface TextFileHistory {
    fun pathToFile(): String

    var history: MutableList<Map<String, String>>

    fun save() {
        val path = pathToFile()
        withFileLock(path) { writeAtomically(path, encode(history)) }
        // BR-115 (аудит #336, #407): файл истории — опубликованный, права 644.
        // Раньше здесь стоял `chmod 666` — запись для всех: любой локальный
        // пользователь или контейнер мог подменить историю.
        setPublishedFilePermissions(path)
    }

    fun add(element: Map<String, String>) {
        // BR-056: перечитываем историю под локом — объект мог быть создан до того, как
        // другой поток дописал свою запись.
        val path = pathToFile()
        withFileLock(path) {
            val current = readFrom(path)
            if (current.contains(element)) current.remove(element)
            current.add(0, element)
            while (current.size > COUNT_HISTORY_LINES) {
                current.removeAt(current.size - 1)
            }
            history = current
            writeAtomically(path, encode(current))
        }
        // BR-115 (аудит #336, #407): см. save() — права 644 вместо 666.
        setPublishedFilePermissions(path)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun loadList(): MutableList<Map<String, String>> = withFileLock(pathToFile()) { readFrom(pathToFile()) }

    companion object {
        private val log = LoggerFactory.getLogger(TextFileHistory::class.java)

        /** Один лок на путь файла: разные истории (songs/processes) не блокируют друг друга. */
        private val locks = ConcurrentHashMap<String, ReentrantLock>()

        internal fun lockFor(path: String): ReentrantLock = locks.computeIfAbsent(path) { ReentrantLock() }

        internal fun <T> withFileLock(
            path: String,
            block: () -> T,
        ): T {
            val lock = lockFor(path)
            lock.lock()
            try {
                return block()
            } finally {
                lock.unlock()
            }
        }

        internal fun encode(history: List<Map<String, String>>): String =
            history.joinToString("\n") {
                Base64.getEncoder().encodeToString(Json.encodeToString(HistoryMap.serializer(), HistoryMap(it)).toByteArray())
            }

        /**
         * Атомарная запись: временный файл в том же каталоге + `Files.move(ATOMIC_MOVE)`.
         * Читатель видит либо старую, либо новую версию целиком — не обрезанный файл.
         */
        internal fun writeAtomically(
            path: String,
            content: String,
        ) {
            val target = File(path).toPath()
            target.parent?.let { Files.createDirectories(it) }
            val tmp = Files.createTempFile(target.parent ?: Path.of("."), "history-", ".tmp")
            try {
                Files.writeString(tmp, content)
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    // некоторые ФС (сетевые/FUSE) не умеют ATOMIC_MOVE — деградируем до replace
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(tmp)
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        internal fun readFrom(path: String): MutableList<Map<String, String>> {
            val file = File(path)
            if (!file.exists()) return mutableListOf()
            return try {
                file
                    .readText()
                    .split("\n")
                    .filter { it != "" }
                    .map {
                        Json
                            .decodeFromStream(HistoryMap.serializer(), ByteArrayInputStream(Base64.getDecoder().decode(it)))
                            .args
                    }.toMutableList()
            } catch (e: Exception) {
                // Было: `catch (_: Exception) { return mutableListOf() }` — повреждённый файл
                // молча превращался в «пустую историю» и затирался следующим сохранением.
                val backup = "${file.absolutePath}.corrupt-${System.currentTimeMillis()}"
                val moved =
                    try {
                        Files.copy(file.toPath(), File(backup).toPath(), StandardCopyOption.REPLACE_EXISTING)
                        true
                    } catch (_: Exception) {
                        false
                    }
                log.warn(
                    "История '{}' не разобрана ({}); копия {}",
                    path,
                    "${e::class.simpleName}: ${e.message}",
                    if (moved) backup else "не сохранена",
                )
                mutableListOf()
            }
        }
    }
}
