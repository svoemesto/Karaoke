package com.svoemesto.karaokeapp

import java.util.concurrent.TimeUnit

/**
 * Проверка кода возврата внешнего процесса (аудит #336, BR-029/BR-161).
 *
 * Контракт:
 * - stdout и stderr **слиты** (`redirectErrorStream(true)`, ADR-0006) — поэтому
 *   хвост вывода, который передаётся в сообщение об ошибке, содержит и stderr;
 * - после EOF stdout процесс обязан завершиться; ждём ограниченное время и при
 *   превышении убиваем его `destroyForcibly()`;
 * - `exitValue() != 0` — это ошибка задания, а не успех: вызывающий обязан
 *   бросить [ProcessExitException], а не помечать задание `DONE`.
 *
 * @see ProcessExitException
 * @see archive/docs/features/async-process-queue.md
 */
class ProcessExitException(
    val command: List<String>,
    val exitCode: Int?,
    val outputTail: String,
    val reason: String,
) : RuntimeException(buildMessage(command, exitCode, outputTail, reason))

private fun buildMessage(
    command: List<String>,
    exitCode: Int?,
    outputTail: String,
    reason: String,
): String =
    buildString {
        append("Внешний процесс завершился неуспешно")
        append(if (exitCode != null) " (код возврата $exitCode)" else " (код возврата не получен)")
        append(": ").append(describeCommand(command))
        append(". ").append(reason)
        if (outputTail.isNotBlank()) {
            append("\nХвост stdout/stderr:\n").append(outputTail.trimEnd())
        }
    }

/** Команда для сообщения об ошибке: ограниченная длина, чтобы не раздувать лог ffmpeg-фильтрами. */
fun describeCommand(
    command: List<String>,
    maxLength: Int = 800,
): String {
    val line = command.joinToString(" ")
    return if (line.length <= maxLength) line else line.take(maxLength) + "… (обрезано, всего ${line.length} симв.)"
}

/** Последние [maxLines] строк слитого stdout/stderr — хвост для диагностики. */
fun outputTail(
    text: String,
    maxLines: Int = 20,
): String {
    if (text.isBlank()) return ""
    val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
    return lines.takeLast(maxLines).joinToString("\n")
}

/**
 * Таймаут ожидания завершения процесса **после** EOF stdout, секунды.
 *
 * Переопределяется переменной окружения `KARAOKE_PROCESS_EXIT_TIMEOUT_SECONDS`.
 * Значение небольшое: EOF по слитому потоку наступает, когда процесс уже
 * закрывает вывод, то есть завершается; длинные рендеры сюда не упираются.
 */
fun processExitTimeoutSeconds(): Long =
    System
        .getenv("KARAOKE_PROCESS_EXIT_TIMEOUT_SECONDS")
        ?.toLongOrNull()
        ?.takeIf { it > 0 }
        ?: DEFAULT_PROCESS_EXIT_TIMEOUT_SECONDS

const val DEFAULT_PROCESS_EXIT_TIMEOUT_SECONDS: Long = 60

/**
 * Дожидается завершения процесса и возвращает его код возврата.
 *
 * Бросает [ProcessExitException] только если процесс не завершился за
 * [timeoutSeconds] (тогда он убивается `destroyForcibly()`). Ненулевой код
 * **возвращается** вызывающему — решение «это ошибка» принимает он
 * ([processExitFailure]).
 */
fun awaitProcessExit(
    process: Process,
    command: List<String>,
    timeoutSeconds: Long = processExitTimeoutSeconds(),
    outputTail: () -> String = { "" },
): Int {
    val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
    if (!finished) {
        process.destroyForcibly()
        process.waitFor(DESTROY_GRACE_SECONDS, TimeUnit.SECONDS)
        throw ProcessExitException(
            command = command,
            exitCode = null,
            outputTail = outputTail(),
            reason = "процесс не завершился за $timeoutSeconds с после закрытия stdout; убит destroyForcibly()",
        )
    }
    return process.exitValue()
}

/** Ошибка задания по ненулевому коду возврата: команда + код + распознавание + stderr-хвост. */
fun processExitFailure(
    command: List<String>,
    exitCode: Int,
    outputTail: String,
): ProcessExitException =
    ProcessExitException(
        command = command,
        exitCode = exitCode,
        outputTail = outputTail,
        reason = describeExternalExitCode(command, exitCode),
    )

/** Распознавание типовых кодов docker/ffmpeg/melt — чтобы причина была видна без чтения лога. */
fun describeExternalExitCode(
    command: List<String>,
    exitCode: Int,
): String {
    val binary =
        command
            .firstOrNull()
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.lowercase()
    return when (binary) {
        "docker" ->
            when (exitCode) {
                125 -> "docker: ошибка самого docker (неверная команда или конфигурация compose)"
                126 -> "docker: команда внутри контейнера не исполняема"
                127 -> "docker: команда внутри контейнера не найдена"
                137 -> "docker: процесс убит SIGKILL (нехватка памяти или force-stop)"
                143 -> "docker: процесс убит SIGTERM"
                else -> "docker завершился с ненулевым кодом"
            }
        "ffmpeg" ->
            when (exitCode) {
                1 -> "ffmpeg: ошибка кодека/параметров/входного файла"
                69 -> "ffmpeg: недоступен сетевой входной ресурс"
                234 -> "ffmpeg: сбой внутренней обработки"
                else -> "ffmpeg завершился с ненулевым кодом"
            }
        "melt" -> "melt: сбой рендера MLT (проверьте project.mlt и входные файлы)"
        "mediainfo" -> "mediainfo: не удалось прочитать файл"
        else -> "ненулевой код возврата"
    }
}

private const val DESTROY_GRACE_SECONDS = 5L
