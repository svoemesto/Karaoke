package com.svoemesto.karaokeapp.tunnel

/**
 * Причина отказа по сертификату.
 *
 * FR-016: отказ по сертификату MUST попадать в наблюдаемость как событие с
 * указанием причины (просрочен / недоверенный / отозван), а не как безымянная
 * ошибка соединения.
 *
 * @see CertFailureClassifier
 */
enum class CertFailureReason(
    val code: String,
) {
    /** Сертификат просрочен. */
    EXPIRED("expired"),

    /** Сертификат отозван. */
    REVOKED("revoked"),

    /** Сертификат не доверенный (нет в цепочке доверия / не совпал корневой). */
    UNTRUSTED("untrusted"),

    /** Причина не распознана. */
    UNKNOWN("unknown"),
}

/**
 * Классификатор отказов по сертификату.
 *
 * Тексты ошибок PostgreSQL и JDK зависят от версий, поэтому причина ищется по
 * нескольким устойчивым маркерам в цепочке причин исключения.
 *
 * @see knowledge/guidelines/runbooks/log-correlation.md — реестр кодов (`DB-CERT-001`).
 */
object CertFailureClassifier {
    private val EXPIRED_MARKERS =
        listOf(
            "certificate has expired",
            "certificate expired",
            "certificateexpired",
            "not yet valid",
            "certificate is not yet valid",
        )

    private val REVOKED_MARKERS =
        listOf(
            "certificate revoked",
            "certificate has been revoked",
            "certificate revoked:",
            "revoked by the issuer",
        )

    private val UNTRUSTED_MARKERS =
        listOf(
            "certificate verify failed",
            "unable to find valid certification path",
            "unable to verify the first certificate",
            "self-signed certificate",
            "self signed certificate",
            "certificate is not trusted",
            "pkix path building failed",
            "ssl error",
            "certificate_unknown",
        )

    /**
     * Определяет причину по цепочке исключений.
     *
     * @param error исключение (или его причина).
     * @return причина; [CertFailureReason.UNKNOWN], если отказа по сертификату нет.
     */
    fun classify(error: Throwable?): CertFailureReason {
        val text = collectText(error).lowercase()
        if (text.isBlank()) return CertFailureReason.UNKNOWN
        if (REVOKED_MARKERS.any { text.contains(it) }) return CertFailureReason.REVOKED
        if (EXPIRED_MARKERS.any { text.contains(it) }) return CertFailureReason.EXPIRED
        if (UNTRUSTED_MARKERS.any { text.contains(it) }) return CertFailureReason.UNTRUSTED
        return CertFailureReason.UNKNOWN
    }

    /** `true`, если отказ похож на отказ по сертификату. */
    fun isCertificateFailure(error: Throwable?): Boolean {
        val reason = classify(error)
        return reason != CertFailureReason.UNKNOWN
    }

    /**
     * Собирает текст цепочки причин (сообщения + SQLState).
     *
     * Текст ограничен по длине — он попадает в лог.
     */
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
