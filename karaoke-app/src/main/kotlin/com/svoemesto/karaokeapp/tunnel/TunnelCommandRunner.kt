package com.svoemesto.karaokeapp.tunnel

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Результат запуска команды туннеля.
 *
 * @property exitCode код возврата (`0` — успех).
 * @property stdout stdout команды (обрезается, чтобы не тащить в лог мусор).
 * @property stderr stderr команды.
 */
data class TunnelCommandResult(
    val exitCode: Int,
    val stdout: String = "",
    val stderr: String = "",
) {
    val ok: Boolean get() = exitCode == 0

    /** Текст для диагностики: stderr при наличии, иначе stdout. */
    fun diagnostic(): String = stderr.trim().ifEmpty { stdout.trim() }
}

/**
 * Абстракция запуска системных команд туннеля (`ip`, `wg`).
 *
 * Существует ради тестируемости: в unit-тестах подменяется на подставной исполнитель,
 * поэтому логика [WireGuardProdTunnel] проверяется без root и без сети.
 *
 * @see WireGuardProdTunnel
 */
interface TunnelCommandRunner {
    /**
     * Выполняет команду.
     *
     * @param args аргументы команды (первый — имя программы).
     */
    fun run(vararg args: String): TunnelCommandResult
}

/**
 * Исполнитель, запускающий настоящие команды.
 *
 * Таймаут обязателен: зависшая команда (`wg`, `ip`) не должна держать поток
 * супервизора туннеля (FR-002 — туннель восстанавливается автоматически).
 */
class ProcessTunnelCommandRunner(
    private val timeoutSeconds: Long = 20,
    private val workingDir: File? = null,
) : TunnelCommandRunner {
    override fun run(vararg args: String): TunnelCommandResult {
        if (args.isEmpty()) return TunnelCommandResult(-1, stderr = "empty command")
        return try {
            val process =
                ProcessBuilder(*args)
                    .apply {
                        workingDir?.let { directory(it) }
                        redirectErrorStream(false)
                    }.start()
            // stdout и stderr читаются разными потоками: иначе process буферизует
            // один из потоков и процесс не завершится (классический дедлок pipe).
            val out = StringBuilder()
            val err = StringBuilder()
            val outThread =
                Thread {
                    runCatching { process.inputStream.bufferedReader().forEachLine { out.append(it).append('\n') } }
                }
            val errThread =
                Thread {
                    runCatching { process.errorStream.bufferedReader().forEachLine { err.append(it).append('\n') } }
                }
            outThread.isDaemon = true
            errThread.isDaemon = true
            outThread.start()
            errThread.start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                TunnelCommandResult(-1, out.toString(), "timeout ${timeoutSeconds}s")
            } else {
                outThread.join(1000)
                errThread.join(1000)
                TunnelCommandResult(process.exitValue(), out.toString(), err.toString())
            }
        } catch (e: Exception) {
            TunnelCommandResult(-1, stderr = "${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
