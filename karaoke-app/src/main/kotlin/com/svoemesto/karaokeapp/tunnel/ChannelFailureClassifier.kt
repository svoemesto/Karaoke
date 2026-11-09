package com.svoemesto.karaokeapp.tunnel

/**
 * Класс отказа задания.
 *
 * FR-033: кратковременный (до ~60 с) обрыв внешней сети не должен приводить к
 * потере задания — оно возвращается в очередь и завершается повторной попыткой.
 * Логический отказ (ошибка пайплайна, «данные не найдены») остаётся `ERROR`,
 * как раньше.
 *
 * @see ChannelFailureClassifier
 */
enum class FailureKind(
    val code: String,
) {
    /** Сетевой/канальный отказ: обрыв WAN, таймаут, недоступность БД или хранилища. */
    CHANNEL("channel"),

    /** Отказ по сертификату (см. [CertFailureClassifier]). */
    CERTIFICATE("certificate"),

    /** Логический отказ — задание падает по существу. */
    LOGICAL("logical"),

    /** Не классифицировано. */
    UNKNOWN("unknown"),
}

/**
 * Классификатор отказов заданий.
 *
 * Различает «канал отвалился» (задание → в очередь) и «задание не прошло по
 * существу» (задание → ошибка). Отдельная проверка на отказ по сертификату:
 * сертификат может быть перевыпущен, значит задание тоже стоит вернуть в
 * очередь, но с явным событием `DB-CERT-001`.
 *
 * @see knowledge/guidelines/runbooks/log-correlation.md — реестр кодов.
 */
object ChannelFailureClassifier {
    private val CHANNEL_MARKERS =
        listOf(
            "communications link failure",
            "connection reset",
            "connection refused",
            "refused",
            "connection timed out",
            "socket timeout",
            "timed out",
            "timeout expired",
            "no route to host",
            "network is unreachable",
            "broken pipe",
            "connection closed",
            "eof reached",
            "sslhandshakeexception",
            "ssl handshake",
            "unable to connect",
            "could not connect",
            "temporarily unavailable",
            "host is unreachable",
        )

    private val LOGICAL_MARKERS =
        listOf(
            "data not found",
            "данные не найдены",
            "no such file or directory",
            "permission denied",
            "syntax error",
        )

    /**
     * Классифицирует отказ.
     *
     * @param error исключение задания.
     * @return класс отказа.
     */
    fun classify(error: Throwable?): FailureKind {
        if (error == null) return FailureKind.UNKNOWN
        if (CertFailureClassifier.isCertificateFailure(error)) return FailureKind.CERTIFICATE
        val text = collectText(error).lowercase()
        if (text.isBlank()) return FailureKind.UNKNOWN
        if (CHANNEL_MARKERS.any { text.contains(it) }) return FailureKind.CHANNEL
        if (LOGICAL_MARKERS.any { text.contains(it) }) return FailureKind.LOGICAL
        return FailureKind.UNKNOWN
    }

    /** `true`, если отказ канальный (задание возвращается в очередь). */
    fun isChannelFailure(error: Throwable?): Boolean {
        val kind = classify(error)
        return kind == FailureKind.CHANNEL || kind == FailureKind.CERTIFICATE
    }

    private fun collectText(error: Throwable?): String {
        if (error == null) return ""
        val parts = mutableListOf<String>()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 6) {
            parts += current.message ?: current.javaClass.simpleName
            parts += current.javaClass.simpleName
            current = current.cause
            depth++
        }
        return parts.joinToString(" ").take(2000)
    }
}
