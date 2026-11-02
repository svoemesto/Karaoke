package com.svoemesto.karaokeapp

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * KARAOKE-387: логи ошибок — одна человекочитаемая строка с машинным кодом, без стектрейсов.
 *
 * ## Проблема
 *
 * Инцидент 2026-11-02: в логе приложения сорок строк стека Spring/Tomcat вокруг одной ошибки
 * `PSQLException: Ошибка ввода/вывода при отправке бэкенду`, а рядом — технические тексты
 * исключений, которые доходили и до интерфейса. Полезное (что случилось, где, с чем) тонуло в
 * фреймах; грепать по `at org.springframework...` бессмысленно.
 *
 * ## Формат
 *
 * Одна строка, машиночитаемые пары `ключ=значение` и стабильный `code`:
 *
 * ```
 * event=db.connect.failed code=DB-CONNECT-001 target=SERVER thread=http-nio-1
 *   host=188.127.240.124:5433 cause=PSQLException: Закончилось время ожидания
 * ```
 *
 * (в реальном логе — без переносов: одна строка целиком).
 *
 * ## Стек
 *
 * По умолчанию стек **не печатается**: в строку попадает только тип и сообщение исключения и
 * цепочка `cause` (`A: msg <- B: msg`). Полный стек включается переменной окружения
 * `LOG_STACKTRACES=true` — тогда после строки SLF4J печатает и throwable. Для разбора «что
 * именно упало» одной строки с кодом, хостом и операцией обычно достаточно; стек нужен, когда
 * непонятно место в коде — его включают на время.
 *
 * ## Реестр кодов
 *
 * Коды — часть контракта разбора логов: они живут в
 * `knowledge/guidelines/runbooks/log-correlation.md` (таблица «код → что значит → что делать»).
 * Новый код добавляется туда же, иначе он бесполезен для дежурного.
 */
object AppLog {
    /** Включает печать стека исключения (по умолчанию выключено). */
    private val stackTracesEnabled: Boolean =
        System
            .getenv("LOG_STACKTRACES")
            ?.trim()
            ?.equals("true", ignoreCase = true)
            ?: false

    private val loggers = ConcurrentHashMap<String, Logger>()

    private fun logger(category: String): Logger = loggers.computeIfAbsent(category) { LoggerFactory.getLogger(it) }

    /** true — стеки печатаются (для тестов и для диагностических проверок). */
    fun isStackTracesEnabled(): Boolean = stackTracesEnabled

    /**
     * Формирует одну строку события: `event=… code=… key=value … [cause=…]`.
     *
     * Значения не экранируются, но нормализуются: переводы строк заменяются пробелом, чтобы
     * событие гарантированно занимало ровно одну строку лога.
     */
    fun format(
        event: String,
        code: String,
        fields: Map<String, Any?> = emptyMap(),
        error: Throwable? = null,
    ): String {
        val parts = mutableListOf("event=${oneLine(event)}", "code=${oneLine(code)}")
        fields.forEach { (key, value) ->
            if (value != null) parts += "$key=${oneLine(value.toString())}"
        }
        if (error != null) parts += "cause=${causeChain(error)}"
        return parts.joinToString(" ")
    }

    /** Одна строка из цепочки cause: `Тип: сообщение <- Тип: сообщение`. */
    fun causeChain(error: Throwable, limit: Int = 4): String {
        val parts = mutableListOf<String>()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < limit) {
            val message = current.message?.takeIf { it.isNotBlank() }
            parts.add(if (message != null) "${current.javaClass.simpleName}: $message" else current.javaClass.simpleName)
            current = current.cause
            depth++
        }
        return parts.joinToString(" <- ")
    }

    fun warn(
        category: String,
        code: String,
        event: String,
        fields: Map<String, Any?> = emptyMap(),
        error: Throwable? = null,
    ) {
        emit(level = Level.WARN, category = category, code = code, event = event, fields = fields, error = error)
    }

    fun error(
        category: String,
        code: String,
        event: String,
        fields: Map<String, Any?> = emptyMap(),
        error: Throwable? = null,
    ) {
        emit(level = Level.ERROR, category = category, code = code, event = event, fields = fields, error = error)
    }

    private enum class Level { WARN, ERROR }

    private fun emit(
        level: Level,
        category: String,
        code: String,
        event: String,
        fields: Map<String, Any?>,
        error: Throwable?,
    ) {
        val log = logger(category)
        val line = format(event, code, fields, error)
        if (stackTracesEnabled && error != null) {
            // Стек — только по явному требованию (LOG_STACKTRACES=true).
            when (level) {
                Level.WARN -> log.warn("{}", line, error)
                Level.ERROR -> log.error("{}", line, error)
            }
            return
        }
        when (level) {
            Level.WARN -> log.warn(line)
            Level.ERROR -> log.error(line)
        }
    }

    private fun oneLine(value: String): String =
        value
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("\t", " ")
            .trim()
}
