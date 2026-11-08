package com.svoemesto.karaokeapp.logging

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy

/**
 * Конвертер Logback: исключение печатается ОДНОЙ строкой — цепочкой причин без
 * кадров стека.
 *
 * Зачем (KARAOKE-387, спека 732). Правило проекта: в логах — нормальные
 * сообщения, а не простыни `at com.svoemesto…`. `AppLog` даёт такую строку там,
 * где код написан по правилу, но вызовы `logger.error("…", e)` печатали полный
 * стек — и на проде 2026-11-08 лог был полон стектрейсов (`SSLHandshakeException`,
 * `HttpTimeoutException`, `EOF reached while reading`,
 * `chat.unread.count.unavailable`).
 *
 * Конвертер подключён глобально (`logback-spring.xml`, `<conversionRule>`), поэтому
 * «пол» работает для ВСЕХ логгеров — своих, Spring, Tomcat, — без правки каждого
 * места вызова.
 *
 * Режимы:
 *  * по умолчанию — одна строка `Тип: сообщение <- Тип: сообщение` (до 6 звеньев);
 *    переводы строк в сообщениях заменяются пробелом;
 *  * `LOG_STACKTRACES=true` — полный стек (тот же флаг, что у `AppLog`).
 *
 * Флаг читается один раз при старте — как и в `AppLog`.
 *
 * @see com.svoemesto.karaokeapp.AppLog — структурированная строка события
 * @see knowledge/system/error-handling.md — правило сообщения об ошибке
 */
open class CauseChainConverter : ClassicConverter() {
    override fun convert(event: ILoggingEvent): String {
        val proxy = event.throwableProxy ?: return ""
        if (stackTracesEnabled()) {
            return fullStack(proxy)
        }
        val parts = mutableListOf<String>()
        var current: IThrowableProxy? = proxy
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            val message = current.message?.takeIf { it.isNotBlank() }
            parts +=
                if (message != null) {
                    "${current.className}: ${oneLine(message)}"
                } else {
                    current.className
                }
            current = current.cause
            depth++
        }
        return parts.joinToString(" <- ")
    }

    /** Полный стек (режим LOG_STACKTRACES=true): класс, сообщение и кадры. */
    private fun fullStack(proxy: IThrowableProxy): String {
        val sb = StringBuilder()
        var current: IThrowableProxy? = proxy
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (depth > 0) {
                sb.append("Caused by: ")
            }
            sb.append(current.className)
            current.message?.takeIf { it.isNotBlank() }?.let { sb.append(": ").append(it) }
            sb.append('\n')
            current.stackTraceElementProxyArray?.forEach { element ->
                sb.append("\tat ").append(element.steAsString).append('\n')
            }
            current = current.cause
            depth++
        }
        return sb.toString().trimEnd('\n')
    }

    /** Режим полного стека — только по явному флагу ( overridable для тестов). */
    protected open fun stackTracesEnabled(): Boolean = STACK_TRACES_ENABLED

    /** Сообщение исключения — всегда одна строка: переводы строк ломают grep по событиям. */
    private fun oneLine(value: String): String =
        value
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("\t", " ")
            .trim()

    companion object {
        /** Глубина цепочки причин в одной строке. */
        const val MAX_CAUSE_DEPTH: Int = 6

        /** Полный стек печатается только по явному требованию (как в `AppLog`). */
        private val STACK_TRACES_ENABLED: Boolean =
            System
                .getenv("LOG_STACKTRACES")
                ?.trim()
                ?.equals("true", ignoreCase = true)
                ?: false
    }
}
