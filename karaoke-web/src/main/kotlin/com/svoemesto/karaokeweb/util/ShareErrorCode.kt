package com.svoemesto.karaokeweb.util

// Коды ошибок share-флоу «Временный полный доступ к песне» (add-song-share-link).
// Используются в JSON-ответах эндпоинтов `/api/public/share/*` и
// `/api/siteusers/share/*` под ключом `errorCode`. На фронте модалки владельца
// и ShareView.vue показывают пользовательские тексты по этим кодам.
// См. archive/docs/features/guest-share-link.md.

enum class ShareErrorCode(
    val dbValue: String,
) {
    // Секрет не найден / отозван / просрочен — единый ответ для всех негативных кейсов.
    NOT_FOUND("share.notFound"),

    // Срок ссылки истёк (expires_at < now()).
    EXPIRED("share.expired"),

    // Ссылка отозвана владельцем / админом / автоматически (premium_lost, song_unavailable).
    REVOKED("share.revoked"),

    // Песня помечена тегом SKIP или ещё не опубликована.
    SONG_UNAVAILABLE("share.songUnavailable"),

    // specs/293-skip-author-toggle: песня помечена тегом SKIP — share-link запрещён
    // независимо от canWorkWithSkipped инициатора (compliance: SKIP-контент скрыт по
    // требованию правообладателя). Отдельный errorCode, чтобы UI мог показать
    // специфичное сообщение «Невозможно создать share-link для SKIP-контента».
    SONG_SKIPPED("share.songSkipped"),

    // Уже 2 активных playback-сессии на эту ссылку.
    CONCURRENT_LIMIT("share.concurrentLimit"),

    // Heartbeat перестал приходить (>90 сек) — сессия закрыта.
    LEASE_EXPIRED("share.leaseExpired"),

    // Превышен rate-limit claim (>10/мин с одного IP).
    RATE_LIMITED("share.rateLimited"),

    // Не используется на текущий момент; оставлено для будущих расширений.
    NOT_OWNER("share.notOwner"),

    // Превышен лимит генераций / перевыпусков / живых ссылок.
    LINK_ALREADY_ACTIVE("share.linkAlreadyActive"),

    // Запрос без обязательного параметра (token, shareSecret, songId).
    TOKEN_MISSING("share.tokenMissing"),

    // Системная (не доменная) ошибка: БД недоступна, неожиданное исключение,
    // программная ошибка. Используется как обёртка для catch-all в контроллере —
    // гарантирует, что share.notFound не маскирует сбои инфраструктуры.
    // См. spec 167-fix-share-claim-500 (FR-010, FR-014).
    INTERNAL("share.internal"),
}

// Утилита маскирования секрета для логов и tbl_events.referer. Секрет
// показывается как abcd...1234 (первые 4 + последние 4 символа), середина
// заменяется на ***. Используется во всех местах, где потенциально может
// появиться URL/секрет share-ссылки.

object ShareSecretMask {
    fun mask(secret: String?): String {
        if (secret.isNullOrBlank()) return ""
        if (secret.length <= 8) return "***"
        return "${secret.substring(0, 4)}***${secret.substring(secret.length - 4)}"
    }
}
