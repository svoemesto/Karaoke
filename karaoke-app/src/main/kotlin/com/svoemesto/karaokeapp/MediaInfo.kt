package com.svoemesto.karaokeapp

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.InputStreamReader

const val TEST_AUDIO_FILE_PATH = "/sm-karaoke/system/(01) [Агата Кристи] Инспектор ПО.flac"

/**
 * Таймаут ожидания `mediainfo`, секунды (BR-035, аудит #336, пакет PKG-S3-22).
 *
 * Настраивается переменной окружения `KARAOKE_MEDIAINFO_TIMEOUT_SECONDS`; по умолчанию
 * [DEFAULT_MEDIAINFO_TIMEOUT_SECONDS]. Значение намеренно скромное: на нормальном файле
 * `mediainfo` отвечает за десятки миллисекунд, а зависание (битый/сетевой файл, залипший
 * FUSE-маунт) раньше блокировало поток очереди навсегда.
 */
fun mediaInfoTimeoutSeconds(): Long =
    System
        .getenv("KARAOKE_MEDIAINFO_TIMEOUT_SECONDS")
        ?.toLongOrNull()
        ?.takeIf { it > 0 }
        ?: DEFAULT_MEDIAINFO_TIMEOUT_SECONDS

const val DEFAULT_MEDIAINFO_TIMEOUT_SECONDS: Long = 30

/**
 * Ошибка внешнего CLI `mediainfo`: не запустился, не ответил за таймаут или вернул
 * ненулевой код. Раньше такие сбои превращались в `null` без следа, а вызывающий
 * (`Song.durationInMilliseconds`) подставлял `0.0` — и MLT генерировался с
 * `out=00:00:00.000`, оставаясь при этом «успешным» заданием.
 */
class MediaInfoException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Suppress("unused")
fun mainMediaInfo() {
//    println(MediaInfo.executeMediaInfo(TEST_AUDIO_FILE_PATH, "--Output=JSON"))
//    println(MediaInfo.getInfoBySectionAndParameter(TEST_AUDIO_FILE_PATH, "Audio","Duration"))
    println(MediaInfo.getInfoBySectionAndParameter(TEST_AUDIO_FILE_PATH, "Audio", "Duration"))
}

/**
 * Класс Media Info.
 *
 * BR-035 (аудит #336, пакет PKG-S3-22): запуск `mediainfo` больше не «вечный».
 * - stdout читается отдельным демон-потоком, поэтому зависший процесс не блокирует
 *   вызывающего в `read()` навсегда;
 * - ожидание завершения — `waitFor(timeout)` с `destroyForcibly()` по истечении
 *   ([awaitProcessExit], общий helper проекта);
 * - ненулевой код возврата — [MediaInfoException], а не «пустой вывод»;
 * - сбой логируется `WARN`/`ERROR` с путём файла и хвостом вывода, а не только `println`.
 *
 * @see archive/docs/features/dual-db-sync.md
 * @see specs/394-pkg-s3-22/report.md
 */
class MediaInfo {
    companion object {
        private val logger = LoggerFactory.getLogger(MediaInfo::class.java)

        /**
         * Запускает `mediainfo` и возвращает его слитый stdout/stderr.
         *
         * @param param полная командная строка (exe + аргументы)
         * @param timeoutSeconds таймаут ожидания завершения
         * @throws MediaInfoException процесс не запустился, не завершился за таймаут
         *   или завершился с ненулевым кодом
         */
        internal fun runMediaInfo(
            param: List<String>,
            timeoutSeconds: Long = mediaInfoTimeoutSeconds(),
        ): String {
            val process =
                try {
                    ProcessBuilder(param).redirectErrorStream(true).start()
                } catch (e: Exception) {
                    throw MediaInfoException(
                        "mediainfo не запустился [${describeCommand(param)}]: ${e::class.simpleName}: ${e.message}",
                        e,
                    )
                }
            val buffer = StringBuilder()
            // Читаем stdout в отдельном потоке: EOF наступает только после выхода процесса,
            // поэтому синхронное чтение в вызывающем потоке зависало бы вместе с процессом.
            val readerThread =
                Thread({
                    try {
                        InputStreamReader(process.inputStream, Charsets.UTF_8).use { reader ->
                            val chunk = CharArray(8192)
                            var read: Int
                            while (reader.read(chunk).also { read = it } != -1) {
                                buffer.append(chunk, 0, read)
                            }
                        }
                    } catch (_: Exception) {
                        // Поток-читатель не должен падать наружу: причина уже видна по
                        // exit code/таймауту, а частичный вывод остаётся в buffer для лога.
                    }
                }, "mediainfo-reader").apply {
                    isDaemon = true
                }
            readerThread.start()
            val exitCode =
                try {
                    awaitProcessExit(process, param, timeoutSeconds) { buffer.toString() }
                } catch (e: ProcessExitException) {
                    throw MediaInfoException(e.message ?: "mediainfo не завершился за $timeoutSeconds с", e)
                } finally {
                    // destroyForcibly() для уже завершившегося процесса — no-op; для
                    // зависшего — гарантированное освобождение дескрипторов (BR-035).
                    process.destroyForcibly()
                    readerThread.join(1000)
                }
            val out = buffer.toString()
            if (exitCode != 0) {
                throw MediaInfoException(
                    "mediainfo завершился с кодом $exitCode [${describeCommand(param)}]: ${outputTail(out)}",
                )
            }
            return out
        }

        fun getInfo(
            media: String,
            exePath: String = PATH_TO_MEDIAINFO,
        ): Map<String, Any> {
            val param = mutableListOf<String>()
            param.add(exePath)
            param.add(media)
            param.add("--Output=JSON")
            val out = runMediaInfo(param)
            val objectMapper = ObjectMapper()
            val result: Map<String, Any> =
                try {
                    objectMapper.readValue(out, object : TypeReference<HashMap<String, Any>>() {})
                } catch (e: Exception) {
                    throw MediaInfoException(
                        "mediainfo вернул не JSON для '$media': ${e::class.simpleName}: ${e.message}; вывод: ${outputTail(out)}",
                        e,
                    )
                }
            return result
        }

        @Suppress("unused")
        fun getInfoByParameter(
            media: String,
            parameter: String,
        ): String = executeMediaInfo(media, parameter)

        /**
         * Значение параметра секции (`Audio`/`Video`) для файла [media].
         *
         * Возвращает `null`, если `mediainfo` не смог ответить (нет файла, таймаут,
         * ненулевой код, параметра нет в ответе) — но каждый такой случай логируется
         * `WARN` с путём файла и причиной, а не молча подменяется нулём.
         */
        fun getInfoBySectionAndParameter(
            media: String,
            section: String,
            parameter: String,
        ): String? {
            try {
                val mediaInfo = getInfo(media)
                val mediaSection = mediaInfo["media"] as? Map<*, *> ?: return null
                val tracks = mediaSection["track"] as? List<*> ?: return null
                tracks.forEach { trackRaw ->
                    val track = trackRaw as? Map<*, *> ?: return@forEach
                    if (track["@type"] == section) {
                        return track[parameter] as? String
                    }
                }
                logger.warn(
                    "mediainfo: секция '{}' не найдена в ответе для '{}' (параметр '{}')",
                    section,
                    media,
                    parameter,
                )
            } catch (e: MediaInfoException) {
                logger.warn("mediainfo: не удалось получить '{}' для '{}': {}", parameter, media, e.message)
            } catch (e: Exception) {
                logger.warn("mediainfo: неожиданная ошибка при разборе '{}' для '{}'", parameter, media, e)
            }
            return null
        }

        @Suppress("unused")
        fun executeMediaInfo(media: String): String {
            val param = mutableListOf<String>()
            param.add(media)
            return executeMediaInfo(param)
        }

        fun executeMediaInfo(
            media: String,
            parameter: String,
        ): String {
            val param = mutableListOf<String>()
            param.add(media)
            param.add(parameter)
            return executeMediaInfo(param)
        }

        /**
         * Сырой вывод `mediainfo` по произвольным [parameters] (без разбора JSON).
         *
         * BR-035: раньше здесь стояла жёстко зашитая команда (`mediainfo --Inform="%%Duration%%"
         * <тестовый файл>`) — переданные аргументы игнорировались. Теперь параметры
         * используются, а запуск идёт через общий [runMediaInfo] (таймаут + exit code).
         */
        fun executeMediaInfo(parameters: List<String>): String {
            val param = mutableListOf<String>()
            param.add(PATH_TO_MEDIAINFO)
            param.addAll(parameters)
            return runMediaInfo(param)
        }
    }
}
